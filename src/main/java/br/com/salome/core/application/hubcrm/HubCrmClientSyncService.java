package br.com.salome.core.application.hubcrm;

import br.com.salome.core.domain.hubcrm.ClientIntegration;
import br.com.salome.core.domain.hubcrm.HubCrmNormalization;
import br.com.salome.core.domain.hubcrm.LegacyCrmClient;
import br.com.salome.core.infrastructure.hubcrm.HubCrmProperties;
import br.com.salome.core.infrastructure.hubcrm.HubCrmStore;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "salome.hub-crm", name = "enabled", havingValue = "true")
public class HubCrmClientSyncService {
    private final HubCrmLegacyRepository legacy;
    private final HubCrmStore store;
    private final ArpaSuiteGateway arpa;
    private final HubCrmProperties properties;

    public HubCrmClientSyncService(HubCrmLegacyRepository legacy, HubCrmStore store,
            ArpaSuiteGateway arpa, HubCrmProperties properties) {
        this.legacy = legacy;
        this.store = store;
        this.arpa = arpa;
        this.properties = properties;
    }

    public SyncResult initialPilotAndBatch() {
        SyncResult pilot = syncEligible(properties.pilotSize());
        if (pilot.failed() > 0) return pilot;
        return pilot.plus(syncEligible(Integer.MAX_VALUE));
    }

    public SyncResult syncNewClients() {
        return syncEligible(100);
    }

    SyncResult syncEligible(int maximum) {
        List<LegacyCrmClient> source = legacy.findEligibleClients(properties.initialCutoffClientId());
        Set<String> cnpjs = new HashSet<>();
        Set<String> legalNames = new HashSet<>();
        int integrated = 0;
        int updated = 0;
        int skipped = 0;
        int failed = 0;
        int attempted = 0;
        for (LegacyCrmClient client : source) {
            String cnpj = HubCrmNormalization.digits(client.cnpj());
            String normalizedName = HubCrmNormalization.normalizedText(client.legalName());
            if (cnpj.length() != 14 || !cnpjs.add(cnpj) || !legalNames.add(normalizedName)) {
                skipped++;
                continue;
            }
            String hash = clientHash(client);
            store.discoverClient(client, normalizedName, hash);
            ClientIntegration current = store.findClient(cnpj).orElseThrow();
            if ("INTEGRADO".equals(current.status()) && hash.equals(current.snapshotHash())) {
                skipped++;
                continue;
            }
            if (attempted >= maximum) continue;
            attempted++;
            try {
                boolean wasIntegrated = current.dealId() != null;
                integrate(client, current, hash);
                if (wasIntegrated) updated++; else integrated++;
            } catch (Exception exception) {
                failed++;
                store.markClientError(cnpj, exception);
                store.recordEvent("client:" + cnpj + ":" + hash, "CLIENTE", client.legacyClientId(),
                        "SINCRONIZAR", "ERRO", null, exception.getMessage());
            }
        }
        return new SyncResult(integrated, updated, skipped, failed);
    }

    private void integrate(LegacyCrmClient client, ClientIntegration current, String hash) {
        long userId = current.assignedUserId() != null ? current.assignedUserId()
                : store.nextRoundRobinUser(properties.arpa().fernandaUserId(), properties.arpa().jaciUserId());
        long organizationId;
        long peopleId;
        long dealId;
        String personName = HubCrmNormalization.validContactName(client.contactName())
                ? client.contactName().trim() : HubCrmNormalization.shortName(client.legalName());
        String phone = preferredPhone(client.contactPhone(), client.phone());

        if (current.dealId() != null && current.organizationId() != null && current.peopleId() != null) {
            organizationId = current.organizationId();
            peopleId = current.peopleId();
            dealId = current.dealId();
            arpa.updateOrganization(organizationId, client.legalName());
            arpa.updatePerson(peopleId, personName, phone, organizationId);
            arpa.updatePortfolioDeal(dealId, client, organizationId, peopleId, userId);
        } else {
            var external = arpa.findLatestOpenDealByCnpj(client.cnpj());
            if (external.isPresent() && external.get().organizationId() != null && external.get().peopleId() != null) {
                organizationId = external.get().organizationId();
                peopleId = external.get().peopleId();
                dealId = external.get().id();
                arpa.updateOrganization(organizationId, client.legalName());
                arpa.updatePerson(peopleId, personName, phone, organizationId);
                arpa.updatePortfolioDeal(dealId, client, organizationId, peopleId, userId);
            } else {
                organizationId = arpa.createOrganization(client.legalName());
                peopleId = arpa.createPerson(personName, phone, organizationId);
                if (external.isPresent()) {
                    dealId = external.get().id();
                    arpa.linkDeal(dealId, organizationId, peopleId, userId);
                    arpa.updatePortfolioDeal(dealId, client, organizationId, peopleId, userId);
                } else {
                    dealId = arpa.createPortfolioDeal(client, organizationId, peopleId, userId);
                }
            }
        }

        String dateEventKey = "client:" + client.cnpj() + ":first-cte:" + client.firstCteWithoutFreight();
        if (!store.eventProcessed(dateEventKey)) {
            arpa.addAnnotation(dealId, "Data de entrada pelo primeiro CT-e sem frete: "
                    + client.firstCteWithoutFreight());
            store.recordEvent(dateEventKey, "CLIENTE", client.legacyClientId(),
                    "DATA_PRIMEIRO_CTE", "PROCESSADO", "Anotação criada", null);
        }
        store.markClientIntegrated(client.cnpj(), organizationId, peopleId, dealId, userId, hash);
        store.recordEvent("client:" + client.cnpj() + ":" + hash, "CLIENTE", client.legacyClientId(),
                "SINCRONIZAR", "PROCESSADO", "Card " + dealId, null);
    }

    private String clientHash(LegacyCrmClient client) {
        return HubCrmNormalization.sha256(client.legalName(), client.cnpj(), client.city(), client.state(),
                client.email(), client.phone(), client.segment(), client.contactName(),
                client.contactDepartment(), client.contactEmail(), client.contactPhone(),
                client.firstCteWithoutFreight());
    }

    private String preferredPhone(String contact, String company) {
        String contactDigits = HubCrmNormalization.digits(contact);
        if (contactDigits.length() == 10 || contactDigits.length() == 11) return contactDigits;
        return company;
    }

    public record SyncResult(int integrated, int updated, int skipped, int failed) {
        SyncResult plus(SyncResult other) {
            return new SyncResult(integrated + other.integrated, updated + other.updated,
                    skipped + other.skipped, failed + other.failed);
        }
    }
}
