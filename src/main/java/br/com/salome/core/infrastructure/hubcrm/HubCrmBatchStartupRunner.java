package br.com.salome.core.infrastructure.hubcrm;

import br.com.salome.core.application.hubcrm.HubCrmBatchService;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Executa o lote de limpeza uma única vez, sem precisar do clique na tela: basta definir
 * {@code SALOME_HUB_CRM_BATCH_TOKEN} (qualquer marca, ex. a data) e reiniciar. O token
 * executado fica em {@code hub_crm_checkpoint('lote_token')}; o mesmo token nunca roda de novo.
 */
@Component
@ConditionalOnExpression("${salome.hub-crm.enabled:false} and ${salome.hub-crm.auto-approval.enabled:false}")
public class HubCrmBatchStartupRunner {
    private static final Logger log = LoggerFactory.getLogger(HubCrmBatchStartupRunner.class);
    private static final String CHECKPOINT = "lote_token";

    private final HubCrmBatchService batch;
    private final HubCrmStore store;
    private final String token;
    private final String cutoff;

    public HubCrmBatchStartupRunner(HubCrmBatchService batch, HubCrmStore store,
            @Value("${salome.hub-crm.batch.execute-token:}") String token,
            @Value("${salome.hub-crm.batch.cutoff:2026-08-31}") String cutoff) {
        this.batch = batch;
        this.store = store;
        this.token = token;
        this.cutoff = cutoff;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (token == null || token.isBlank()) return;
        if (token.equals(store.textCheckpoint(CHECKPOINT).orElse(null))) {
            log.info("Lote do Hub CRM com token {} já executado; ignorado", token);
            return;
        }
        store.setTextCheckpoint(CHECKPOINT, token);
        log.info("Iniciando lote do Hub CRM (token {}, corte {})", token, cutoff);
        batch.start(true, LocalDate.parse(cutoff));
    }
}
