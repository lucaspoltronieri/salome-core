package br.com.salome.core.infrastructure.hubcrm;

import br.com.salome.core.application.hubcrm.HubCrmBatchService;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Executa uma única vez, na inicialização, sem precisar do clique na tela:
 * <ul>
 *   <li>o lote de limpeza, quando {@code SALOME_HUB_CRM_BATCH_TOKEN} é definido;</li>
 *   <li>o ajuste manual ({@code SALOME_HUB_CRM_ADJUST_APPROVE} = "cotação:CT-e,...",
 *       {@code SALOME_HUB_CRM_ADJUST_REJECT} = "cotação,..."), quando
 *       {@code SALOME_HUB_CRM_ADJUST_TOKEN} é definido.</li>
 * </ul>
 * Cada token executado fica em {@code hub_crm_checkpoint}; o mesmo token nunca roda de novo.
 */
@Component
@ConditionalOnExpression("${salome.hub-crm.enabled:false} and ${salome.hub-crm.auto-approval.enabled:false}")
public class HubCrmBatchStartupRunner {
    private static final Logger log = LoggerFactory.getLogger(HubCrmBatchStartupRunner.class);

    private final HubCrmBatchService batch;
    private final HubCrmStore store;
    private final String token;
    private final String cutoff;
    private final String adjustToken;
    private final String adjustApprove;
    private final String adjustReject;

    public HubCrmBatchStartupRunner(HubCrmBatchService batch, HubCrmStore store,
            @Value("${salome.hub-crm.batch.execute-token:}") String token,
            @Value("${salome.hub-crm.batch.cutoff:2026-08-31}") String cutoff,
            @Value("${salome.hub-crm.batch.adjust-token:}") String adjustToken,
            @Value("${salome.hub-crm.batch.adjust-approve:}") String adjustApprove,
            @Value("${salome.hub-crm.batch.adjust-reject:}") String adjustReject) {
        this.batch = batch;
        this.store = store;
        this.token = token;
        this.cutoff = cutoff;
        this.adjustToken = adjustToken;
        this.adjustApprove = adjustApprove;
        this.adjustReject = adjustReject;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (firstTime("lote_token", token)) {
            log.info("Iniciando lote do Hub CRM (token {}, corte {})", token, cutoff);
            batch.start(true, LocalDate.parse(cutoff));
        }
        if (firstTime("ajuste_token", adjustToken)) {
            Map<Long, String> approve = parseApprove(adjustApprove);
            Set<Long> reject = parseIds(adjustReject);
            log.info("Iniciando ajuste manual do Hub CRM (token {}): aprovar {}, não aprovar {}",
                    adjustToken, approve, reject);
            Thread.ofVirtual().name("hub-crm-ajuste").start(() -> batch.runManual(approve, reject));
        }
    }

    private boolean firstTime(String checkpoint, String value) {
        if (value == null || value.isBlank()) return false;
        if (value.equals(store.textCheckpoint(checkpoint).orElse(null))) {
            log.info("Token {} ({}) já executado; ignorado", value, checkpoint);
            return false;
        }
        store.setTextCheckpoint(checkpoint, value);
        return true;
    }

    static Map<Long, String> parseApprove(String spec) {
        Map<Long, String> result = new LinkedHashMap<>();
        if (spec == null) return result;
        for (String part : spec.split(",")) {
            String[] pair = part.trim().split(":");
            if (pair.length == 2 && !pair[0].isBlank() && !pair[1].isBlank()) {
                result.put(Long.parseLong(pair[0].trim()), pair[1].trim());
            }
        }
        return result;
    }

    static Set<Long> parseIds(String spec) {
        Set<Long> result = new LinkedHashSet<>();
        if (spec == null) return result;
        for (String part : spec.split(",")) {
            if (!part.isBlank()) result.add(Long.parseLong(part.trim()));
        }
        return result;
    }
}
