package br.com.salome.core.application.hubcrm;

import br.com.salome.core.domain.hubcrm.ClientIntegration;
import br.com.salome.core.domain.hubcrm.HubCrmNormalization;
import br.com.salome.core.domain.hubcrm.LegacyCrmClient;
import br.com.salome.core.infrastructure.hubcrm.HubCrmProperties;
import br.com.salome.core.infrastructure.hubcrm.HubCrmStore;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "salome.hub-crm", name = "enabled", havingValue = "true")
public class HubCrmClientSyncService {
    private static final int BATCH_CONCURRENCY = 4;
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

    public SyncResult initialPilot() {
        return syncEligible(properties.pilotSize());
    }

    public SyncResult initialBatch() {
        return syncEligible(Integer.MAX_VALUE);
    }

    public SyncResult syncNewClients() {
        return syncEligible(100);
    }

    public SyncResult syncClient(long legacyClientId) {
        List<LegacyCrmClient> source = legacy.findEligibleClients(properties.initialCutoffClientId()).stream()
                .filter(client -> client.legacyClientId() == legacyClientId)
                .toList();
        return syncSource(source, 1);
    }

    SyncResult syncEligible(int maximum) {
        List<LegacyCrmClient> source = legacy.findEligibleClients(properties.initialCutoffClientId());
        return syncSource(source, maximum);
    }

    private SyncResult syncSource(List<LegacyCrmClient> source, int maximum) {
        Set<String> cnpjs = new HashSet<>();
        Set<String> legalNames = new HashSet<>();
        int skipped = 0;
        List<ClientWork> work = new ArrayList<>();
        for (LegacyCrmClient client : source) {
            String cnpj = HubCrmNormalization.digits(client.cnpj());
            String normalizedName = HubCrmNormalization.normalizedText(
                    HubCrmNormalization.businessName(client.legalName()));
            if (cnpj.length() != 14 || !cnpjs.add(cnpj) || !legalNames.add(normalizedName)) {
                skipped++;
                continue;
            }
            String hash = clientHash(client);
            store.discoverClient(client, normalizedName, hash);
            ClientIntegration current = store.findClient(cnpj).orElseThrow();
            boolean unchangedIntegrated = "INTEGRADO".equals(current.status())
                    && hash.equals(current.snapshotHash());
            boolean unchangedWithoutContact = "SEM_CONTATO".equals(current.status())
                    && current.dealId() == null && hash.equals(current.snapshotHash());
            if (unchangedIntegrated || unchangedWithoutContact) {
                skipped++;
                continue;
            }
            if ("ERRO".equals(current.status()) && !store.canRetryClient(cnpj)) {
                skipped++;
                continue;
            }
            if (work.size() >= maximum) continue;
            long userId = current.assignedUserId() != null ? current.assignedUserId()
                    : store.nextRoundRobinUser(
                            properties.arpa().fernandaUserId(), properties.arpa().jaciUserId());
            work.add(new ClientWork(client, current, hash, cnpj, userId));
        }
        if (work.isEmpty()) return new SyncResult(0, 0, skipped, 0);

        int integrated = 0;
        int updated = 0;
        int failed = 0;
        int threads = Math.min(BATCH_CONCURRENCY, work.size());
        try (var executor = Executors.newFixedThreadPool(threads)) {
            List<Callable<ClientResult>> tasks = work.stream()
                    .<Callable<ClientResult>>map(item -> () -> process(item))
                    .toList();
            for (var future : executor.invokeAll(tasks)) {
                ClientResult result = future.get();
                integrated += result.integrated();
                updated += result.updated();
                failed += result.failed();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Carga de clientes interrompida", exception);
        } catch (java.util.concurrent.ExecutionException exception) {
            throw new IllegalStateException("Falha inesperada na carga de clientes", exception.getCause());
        }
        return new SyncResult(integrated, updated, skipped, failed);
    }

    private ClientResult process(ClientWork item) {
        try {
            boolean hasValidContact = HubCrmNormalization.validContactName(
                    HubCrmNormalization.contactName(item.client().contactName()));
            if (!hasValidContact && item.current().dealId() == null) {
                store.markClientWithoutContact(item.cnpj(), item.hash());
                return new ClientResult(0, 0, 0);
            }
            boolean wasIntegrated = item.current().dealId() != null;
            integrate(item.client(), item.current(), item.hash(), item.userId());
            return wasIntegrated ? new ClientResult(0, 1, 0) : new ClientResult(1, 0, 0);
        } catch (Exception exception) {
            store.markClientError(item.cnpj(), exception);
            store.recordEvent("client:" + item.cnpj() + ":" + item.hash(), "CLIENTE",
                    item.client().legacyClientId(), "SINCRONIZAR", "ERRO", null, exception.getMessage());
            return new ClientResult(0, 0, 1);
        }
    }

    private void integrate(LegacyCrmClient client, ClientIntegration current, String hash, long userId) {
        long organizationId;
        Long peopleId;
        long dealId;
        String contactName = HubCrmNormalization.contactName(client.contactName());
        // A company phone/email is not a personal contact.  Without a valid
        // contact name the card must remain linked only to the organization.
        String companyName = HubCrmNormalization.normalizedText(
                HubCrmNormalization.shortName(client.legalName()));
        boolean hasContact = HubCrmNormalization.validContactName(contactName)
                && !companyName.equals(HubCrmNormalization.normalizedText(contactName));
        String personName = HubCrmNormalization.validContactName(contactName)
                ? contactName : HubCrmNormalization.shortName(client.legalName());
        String phone = preferredPhone(client.contactPhone(), client.phone());
        String organizationName = HubCrmNormalization.businessName(client.legalName());

        if (current.dealId() != null && current.organizationId() != null) {
            organizationId = current.organizationId();
            peopleId = current.peopleId();
            dealId = current.dealId();
            arpa.updateOrganization(organizationId, organizationName);
            if (hasContact) {
                if (peopleId == null) peopleId = arpa.createPerson(personName, phone, organizationId);
                arpa.updatePerson(peopleId, personName, phone, organizationId);
                arpa.linkDeal(dealId, organizationId, peopleId, userId);
                arpa.updatePortfolioDeal(dealId, client, organizationId, peopleId, userId);
            } else {
                peopleId = null;
                arpa.updatePortfolioDealWithoutPerson(dealId, client, organizationId, userId);
            }
        } else {
            var external = arpa.findLatestOpenDealByCnpj(client.cnpj());
            if (external.isPresent() && external.get().organizationId() != null) {
                organizationId = external.get().organizationId();
                dealId = external.get().id();
                arpa.updateOrganization(organizationId, organizationName);
                peopleId = hasContact ? external.get().peopleId() : null;
                if (hasContact) {
                    if (peopleId == null) peopleId = arpa.createPerson(personName, phone, organizationId);
                    arpa.updatePerson(peopleId, personName, phone, organizationId);
                    arpa.linkDeal(dealId, organizationId, peopleId, userId);
                    arpa.updatePortfolioDeal(dealId, client, organizationId, peopleId, userId);
                } else {
                    arpa.updatePortfolioDealWithoutPerson(dealId, client, organizationId, userId);
                }
            } else {
                organizationId = arpa.createOrganization(organizationName);
                peopleId = hasContact ? arpa.createPerson(personName, phone, organizationId) : null;
                if (external.isPresent()) {
                    dealId = external.get().id();
                    if (peopleId != null) arpa.linkDeal(dealId, organizationId, peopleId, userId);
                    if (peopleId == null) {
                        arpa.updatePortfolioDealWithoutPerson(dealId, client, organizationId, userId);
                    } else {
                        arpa.updatePortfolioDeal(dealId, client, organizationId, peopleId, userId);
                    }
                } else {
                    dealId = peopleId == null
                            ? arpa.createPortfolioDealWithoutPerson(client, organizationId, userId)
                            : arpa.createPortfolioDeal(client, organizationId, peopleId, userId);
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
        return HubCrmNormalization.sha256(HubCrmNormalization.businessName(client.legalName()),
                client.cnpj(), client.city(), client.state(),
                client.email(), client.phone(), client.segment(), HubCrmNormalization.contactName(client.contactName()),
                client.contactDepartment(), client.contactEmail(), client.contactPhone(),
                client.firstCteWithoutFreight());
    }

    private String preferredPhone(String contact, String company) {
        String contactDigits = HubCrmNormalization.digits(contact);
        if (contactDigits.length() == 10 || contactDigits.length() == 11) return contactDigits;
        return company;
    }

    public record SyncResult(int integrated, int updated, int skipped, int failed) {
    }

    private record ClientWork(LegacyCrmClient client, ClientIntegration current, String hash,
            String cnpj, long userId) {
    }

    private record ClientResult(int integrated, int updated, int failed) {
    }
}
