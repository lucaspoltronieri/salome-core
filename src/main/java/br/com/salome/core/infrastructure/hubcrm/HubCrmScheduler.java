package br.com.salome.core.infrastructure.hubcrm;

import br.com.salome.core.application.hubcrm.HubCrmClientSyncService;
import br.com.salome.core.application.hubcrm.HubCrmCteApprovalService;
import br.com.salome.core.application.hubcrm.HubCrmQuoteSyncService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "salome.hub-crm", name = "polling-enabled", havingValue = "true")
public class HubCrmScheduler {
    private static final Logger log = LoggerFactory.getLogger(HubCrmScheduler.class);
    private final HubCrmClientSyncService clients;
    private final HubCrmQuoteSyncService quotes;
    private final Optional<HubCrmCteApprovalService> cteApproval;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicReference<Instant> lastStarted = new AtomicReference<>();
    private final AtomicReference<Instant> lastFinished = new AtomicReference<>();
    private final AtomicReference<String> lastError = new AtomicReference<>();

    public HubCrmScheduler(HubCrmClientSyncService clients, HubCrmQuoteSyncService quotes,
            Optional<HubCrmCteApprovalService> cteApproval) {
        this.clients = clients;
        this.quotes = quotes;
        this.cteApproval = cteApproval;
    }

    @Scheduled(fixedDelayString = "${salome.hub-crm.polling-delay-ms:30000}")
    public void poll() {
        if (!running.compareAndSet(false, true)) return;
        lastStarted.set(Instant.now());
        try {
            // As etapas são independentes: uma falha não impede as outras de rodar. A
            // aprovação por CT-e vem antes das cotações para o ganho no ArpaSuite sair no
            // mesmo ciclo em que a cotação é aprovada no legado.
            List<String> errors = new ArrayList<>();
            addError(errors, run("clientes", clients::syncNewClients));
            cteApproval.ifPresent(service -> addError(errors, run("aprovação por CT-e", service::approveFromCtes)));
            addError(errors, run("cotações", quotes::syncQuotes));
            lastError.set(errors.isEmpty() ? null : String.join(" | ", errors));
        } finally {
            lastFinished.set(Instant.now());
            running.set(false);
        }
    }

    private static void addError(List<String> errors, String error) {
        if (error != null) errors.add(error);
    }

    private String run(String etapa, Runnable step) {
        try {
            step.run();
            return null;
        } catch (Exception exception) {
            log.error("Falha no polling do Hub CRM ({})", etapa, exception);
            return etapa + ": " + exception.getMessage();
        }
    }

    public Status status() {
        return new Status(running.get(), lastStarted.get(), lastFinished.get(), lastError.get());
    }

    public record Status(boolean running, Instant lastStarted, Instant lastFinished, String lastError) {}
}
