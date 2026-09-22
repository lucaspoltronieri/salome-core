package br.com.salome.core.application.hubcrm;

import br.com.salome.core.domain.hubcrm.HubCrmNormalization;
import br.com.salome.core.domain.hubcrm.InactiveClientReport;
import br.com.salome.core.infrastructure.hubcrm.HubCrmProperties;
import br.com.salome.core.infrastructure.hubcrm.HubCrmStore;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Observação dos cards de cliente não pagante (estágio Carteira / Não Pagantes): último transporte
 * e link do PDF com a relação dos CT-es recebidos, na mesma ideia dos cards de Pagantes. Grava uma
 * vez por card (a API não edita anotação); o PDF é gerado na hora, então a relação fica atual.
 */
@Service
@ConditionalOnProperty(prefix = "salome.hub-crm", name = "enabled", havingValue = "true")
public class HubCrmClientTransportNoteService {
    static final int PER_CYCLE = 25;
    static final long LINK_TTL_DAYS = 365;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final HubCrmStore store;
    private final InactiveClientRepository reports;
    private final ArpaSuiteGateway arpa;
    private final HubCrmMediaSigner signer;
    private final HubCrmProperties properties;

    public HubCrmClientTransportNoteService(HubCrmStore store, InactiveClientRepository reports,
            ArpaSuiteGateway arpa, HubCrmMediaSigner signer, HubCrmProperties properties) {
        this.store = store;
        this.reports = reports;
        this.arpa = arpa;
        this.signer = signer;
        this.properties = properties;
    }

    public NoteResult annotatePending() {
        int written = 0;
        int skipped = 0;
        int failed = 0;
        for (HubCrmStore.ClientCard card : store.clientCardsWithoutTransportNote(PER_CYCLE)) {
            String key = "client:" + card.cnpj() + ":transportes";
            try {
                Optional<InactiveClientReport> report = reports.findReceivedReport(card.legacyClientId());
                if (report.isEmpty() || report.get().ctes().isEmpty()) {
                    store.recordEvent(key, "CLIENTE", card.legacyClientId(), "OBS_TRANSPORTES", "SEM_CTE",
                            "Nenhum CT-e recebido sem frete", null);
                    skipped++;
                    continue;
                }
                Optional<Long> stage = arpa.findDealStage(card.dealId());
                if (stage.isEmpty()) {
                    // Card apagado no ArpaSuite: mesma regra do sync, não recria.
                    store.markClientRemoved(card.cnpj());
                    store.recordEvent(key, "CLIENTE", card.legacyClientId(), "OBS_TRANSPORTES", "CARD_REMOVIDO",
                            "Card apagado no ArpaSuite", null);
                    skipped++;
                    continue;
                }
                if (stage.get() != properties.arpa().carteiraStageId()) {
                    // Pedido do Lucas: só os cards que estão no estágio Não Pagantes.
                    store.recordEvent(key, "CLIENTE", card.legacyClientId(), "OBS_TRANSPORTES", "FORA_DO_ESTAGIO",
                            "Card no estágio " + stage.get() + "; só Não Pagantes recebe", null);
                    skipped++;
                    continue;
                }
                String link = signer.receivedClientUrl(card.legacyClientId(), LINK_TTL_DAYS).url();
                long annotationId = arpa.addAnnotation(card.dealId(), observation(report.get(), link));
                store.recordEvent(key, "CLIENTE", card.legacyClientId(), "OBS_TRANSPORTES", "PROCESSADO",
                        "Card " + card.dealId() + " anotação " + annotationId, null);
                written++;
            } catch (Exception exception) {
                store.recordEvent(key, "CLIENTE", card.legacyClientId(), "OBS_TRANSPORTES", "ERRO", null,
                        exception.getMessage());
                failed++;
            }
        }
        return new NoteResult(written, skipped, failed);
    }

    static String observation(InactiveClientReport report, String link) {
        List<InactiveClientReport.Cte> ctes = report.ctes();
        InactiveClientReport.Cte first = ctes.get(0);
        InactiveClientReport.Cte last = ctes.get(ctes.size() - 1);
        List<String> lines = new ArrayList<>();
        lines.add("Cliente Não Pagante: recebe mercadoria com o frete pago pelo remetente");
        lines.add("Recebeu " + ctes.size() + (ctes.size() == 1 ? " CT-e" : " CT-es") + " desde "
                + date(first) + " | Peso: " + kg(report.totalWeight()) + " kg | Valor NF: R$ "
                + money(report.totalInvoiceValue()) + " | Frete (pago pelo remetente): R$ "
                + money(report.totalFreight()));
        lines.add("Último transporte: " + date(last) + " | CT-e " + last.number()
                + " | Origem: " + party(last.sender(), last.senderCity())
                + " | Destino: " + party(last.recipient(), last.recipientCity())
                + " | Volumes: " + last.volumes() + " | Peso: " + kg(last.weight()) + " kg"
                + " | Valor NF: R$ " + money(last.invoiceValue()) + " | Frete: R$ " + money(last.freight())
                + " | Pagto: " + payment(last.paymentType()));
        lines.add("");
        lines.add("PDF com todos os CT-es recebidos (CT-e, remetente, cidades, NF, volumes, peso, valor NF, frete):");
        // A timeline do ArpaSuite guarda e mostra a observação em HTML (<p>, <br>), como as escritas pela tela.
        StringBuilder html = new StringBuilder();
        for (String line : lines) html.append(line.isEmpty() ? "<p><br></p>" : "<p>" + escape(line) + "</p>");
        html.append("<p><a href=\"").append(escape(link))
                .append("\" target=\"_blank\" rel=\"noopener noreferrer\">Abrir PDF dos CT-es recebidos</a></p>");
        return html.toString();
    }

    private static String date(InactiveClientReport.Cte cte) {
        return cte.issued() == null ? "-" : DATE.format(cte.issued());
    }

    private static String party(String name, String city) {
        String shortName = HubCrmNormalization.businessName(name == null ? "" : name.trim());
        return city == null || city.isBlank() ? shortName : shortName + " - " + city.trim();
    }

    private static String payment(String paymentType) {
        String normalized = HubCrmNormalization.normalizedText(paymentType);
        if (normalized.contains("FOB")) return "FOB";
        if (normalized.contains("CIF")) return "CIF";
        return paymentType == null ? "" : paymentType.trim();
    }

    private static String money(BigDecimal value) {
        return format("#,##0.00", value);
    }

    private static String kg(BigDecimal value) {
        return format("#,##0.000", value);
    }

    private static String format(String pattern, BigDecimal value) {
        return new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.of("pt", "BR")))
                .format(value == null ? BigDecimal.ZERO : value);
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    public record NoteResult(int written, int skipped, int failed) {}
}
