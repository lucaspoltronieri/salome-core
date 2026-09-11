package br.com.salome.core.application.hubcrm;

import br.com.salome.core.domain.hubcrm.CteQuoteMatcher;
import br.com.salome.core.domain.hubcrm.LegacyCte;
import br.com.salome.core.domain.hubcrm.LegacyQuote;
import br.com.salome.core.infrastructure.hubcrm.HubCrmAutoApprovalProperties;
import br.com.salome.core.infrastructure.hubcrm.HubCrmStore;
import br.com.salome.core.infrastructure.legacy.hubcrm.LegacyQuoteApprovalWriter;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

/**
 * Lê os CT-es emitidos e aprova no legado a cotação correspondente
 * ({@link CteQuoteMatcher}). O ganho no ArpaSuite não sai daqui: a cotação aprovada é
 * percebida pelo {@link HubCrmQuoteSyncService} no mesmo ciclo (Fernanda/Jaci). Cotações de
 * quem não está no ArpaSuite (Carlos) só mudam no legado.
 */
@Service
@ConditionalOnExpression("${salome.hub-crm.enabled:false} and ${salome.hub-crm.auto-approval.enabled:false}")
public class HubCrmCteApprovalService {
    private final HubCrmLegacyRepository legacy;
    private final HubCrmStore store;
    private final LegacyQuoteApprovalWriter writer;
    private final HubCrmAutoApprovalProperties properties;
    private final Clock clock;

    @Autowired
    public HubCrmCteApprovalService(HubCrmLegacyRepository legacy, HubCrmStore store,
            LegacyQuoteApprovalWriter writer, HubCrmAutoApprovalProperties properties) {
        this(legacy, store, writer, properties, Clock.systemDefaultZone());
    }

    HubCrmCteApprovalService(HubCrmLegacyRepository legacy, HubCrmStore store,
            LegacyQuoteApprovalWriter writer, HubCrmAutoApprovalProperties properties, Clock clock) {
        this.legacy = legacy;
        this.store = store;
        this.writer = writer;
        this.properties = properties;
        this.clock = clock;
    }

    public synchronized CteApprovalResult approveFromCtes() {
        LocalDate today = LocalDate.now(clock);
        LocalDate since = today.minusDays(properties.lookbackDays());
        Set<Long> handled = store.cteMatchIdsSince(since);
        List<LegacyCte> ctes = legacy.findRecentCtes(since).stream()
                .filter(cte -> !handled.contains(cte.id()))
                .toList();
        if (ctes.isEmpty()) return new CteApprovalResult(0, 0, 0, 0, 0);

        Set<Long> used = store.quotesApprovedByCte();
        List<LegacyQuote> open = new ArrayList<>(legacy.findApprovableQuotes(
                since.minusDays(properties.windowDays())).stream()
                .filter(quote -> !used.contains(quote.id()))
                .toList());
        Map<String, List<LegacyQuote>> byPayer = open.stream()
                .filter(quote -> quote.payerCnpj() != null && !quote.payerCnpj().isBlank())
                .collect(Collectors.groupingBy(LegacyQuote::payerCnpj));

        int approved = 0;
        int ambiguous = 0;
        int conflicts = 0;
        int failed = 0;
        for (LegacyCte cte : ctes) {
            String eventKey = "cte:" + cte.id() + ":aprovacao";
            try {
                List<LegacyQuote> candidates = byPayer.getOrDefault(cte.payerCnpj(), List.of());
                var match = CteQuoteMatcher.evaluate(cte, candidates, properties.windowDays());
                switch (match.outcome()) {
                    case SEM_COTACAO -> { }
                    case AMBIGUO -> {
                        ambiguous++;
                        store.recordCteMatch(cte, match.quote(), "AMBIGUO", match.criteria(), null);
                        store.recordEvent(eventKey, "CTE", cte.id(), "APROVACAO_CTE", "REVISAO", null,
                                "CT-e " + cte.label() + ": " + match.criteria());
                    }
                    case APROVAR -> {
                        LegacyQuote quote = match.quote();
                        if (writer.approve(quote, cte, LocalDateTime.now(clock))) {
                            approved++;
                            store.recordCteMatch(cte, quote, "APROVADA_AUTO", match.criteria(), match.divergences());
                            store.recordEvent(eventKey, "CTE", cte.id(), "APROVACAO_CTE", "PROCESSADO",
                                    "Cotação " + quote.id() + " (" + quote.responsible() + ") "
                                            + quote.status() + " → APROVADA pelo CT-e " + cte.label()
                                            + (CteQuoteMatcher.inArpaSuite(quote.responsible())
                                                    ? "" : "; responsável fora do ArpaSuite, só legado"),
                                    null);
                        } else {
                            conflicts++;
                            store.recordCteMatch(cte, quote, "CONCORRENCIA", match.criteria(),
                                    "Status da cotação mudou antes da gravação");
                            store.recordEvent(eventKey, "CTE", cte.id(), "APROVACAO_CTE", "REVISAO", null,
                                    "Cotação " + quote.id() + " mudou de status antes da aprovação automática");
                        }
                        byPayer.computeIfPresent(cte.payerCnpj(),
                                (key, list) -> list.stream().filter(q -> q.id() != quote.id()).toList());
                    }
                }
            } catch (Exception exception) {
                // Isolado por CT-e: não grava match, então é tentado de novo no próximo ciclo.
                failed++;
                store.recordEvent(eventKey, "CTE", cte.id(), "APROVACAO_CTE", "ERRO", null,
                        exception.getMessage());
            }
        }
        return new CteApprovalResult(ctes.size(), approved, ambiguous, conflicts, failed);
    }

    public record CteApprovalResult(int evaluated, int approved, int ambiguous, int conflicts, int failed) {}
}
