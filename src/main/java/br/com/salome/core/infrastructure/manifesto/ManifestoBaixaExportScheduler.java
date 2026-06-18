package br.com.salome.core.infrastructure.manifesto;

import br.com.salome.core.application.manifesto.ManifestoBaixaExportService;
import br.com.salome.core.domain.manifesto.ManifestoBaixaExportRequest;
import br.com.salome.core.domain.manifesto.ManifestoBaixaExportResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "salome.manifesto.export", name = "enabled", havingValue = "true")
public class ManifestoBaixaExportScheduler {

    private static final Logger logger = LoggerFactory.getLogger(ManifestoBaixaExportScheduler.class);

    private final ManifestoBaixaExportService service;
    private final ManifestoBaixaExportProperties properties;

    public ManifestoBaixaExportScheduler(ManifestoBaixaExportService service, ManifestoBaixaExportProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @Scheduled(cron = "${salome.manifesto.export.cron:0 */15 * * * *}")
    public void exportarBaixasPendentes() {
        executarExportacao();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void exportarAoIniciar() {
        executarExportacao();
    }

    private void executarExportacao() {
        try {
            ManifestoBaixaExportResult result = service.exportarPendentes(new ManifestoBaixaExportRequest(
                    properties.filialDestinoId(), properties.effectiveBatchSize(), properties.effectiveDataCorte()));
            logger.info("Mapa atual de CT-es SJP exportado: consultados={}, exportados={}, duplicados={}",
                    result.consultados(), result.exportados(), result.duplicadosIgnorados());
        } catch (Exception ex) {
            logger.error("Falha ao exportar mapa atual de CT-es SJP.", ex);
        }
    }
}
