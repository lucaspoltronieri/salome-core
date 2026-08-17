package br.com.salome.core.infrastructure.hubcrm;

import br.com.salome.core.application.hubcrm.HubCrmClientSyncService;
import br.com.salome.core.application.hubcrm.HubCrmQuoteSyncService;
import java.time.Instant;
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
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicReference<Instant> lastStarted = new AtomicReference<>();
    private final AtomicReference<Instant> lastFinished = new AtomicReference<>();
    private final AtomicReference<String> lastError = new AtomicReference<>();

    public HubCrmScheduler(HubCrmClientSyncService clients, HubCrmQuoteSyncService quotes) {
        this.clients = clients;
        this.quotes = quotes;
    }

    @Scheduled(fixedDelayString = "${salome.hub-crm.polling-delay-ms:30000}")
    public void poll() {
        if (!running.compareAndSet(false, true)) return;
        lastStarted.set(Instant.now());
        try {
            clients.syncNewClients();
            quotes.syncQuotes();
            lastError.set(null);
        } catch (Exception exception) {
            lastError.set(exception.getMessage());
            log.error("Falha no polling do Hub CRM", exception);
        } finally {
            lastFinished.set(Instant.now());
            running.set(false);
        }
    }

    public Status status() {
        return new Status(running.get(), lastStarted.get(), lastFinished.get(), lastError.get());
    }

    public record Status(boolean running, Instant lastStarted, Instant lastFinished, String lastError) {}
}
