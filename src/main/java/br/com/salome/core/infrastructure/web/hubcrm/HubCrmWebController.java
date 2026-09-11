package br.com.salome.core.infrastructure.web.hubcrm;

import br.com.salome.core.application.hubcrm.ArpaSuiteGateway;
import br.com.salome.core.application.hubcrm.HubCrmBatchService;
import br.com.salome.core.application.hubcrm.HubCrmClientSyncService;
import br.com.salome.core.application.hubcrm.HubCrmCteApprovalService;
import br.com.salome.core.application.hubcrm.HubCrmQuoteSyncService;
import br.com.salome.core.infrastructure.hubcrm.HubCrmProperties;
import br.com.salome.core.infrastructure.hubcrm.HubCrmScheduler;
import br.com.salome.core.infrastructure.hubcrm.HubCrmStore;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@ConditionalOnProperty(prefix = "salome.hub-crm", name = "enabled", havingValue = "true")
public class HubCrmWebController {
    private final HubCrmStore store;
    private final HubCrmClientSyncService clients;
    private final HubCrmQuoteSyncService quotes;
    private final ArpaSuiteGateway arpa;
    private final HubCrmProperties properties;
    private final Optional<HubCrmScheduler> scheduler;
    private final Optional<HubCrmCteApprovalService> cteApproval;
    private final Optional<HubCrmBatchService> batch;

    public HubCrmWebController(HubCrmStore store, HubCrmClientSyncService clients,
            HubCrmQuoteSyncService quotes, ArpaSuiteGateway arpa, HubCrmProperties properties,
            Optional<HubCrmScheduler> scheduler, Optional<HubCrmCteApprovalService> cteApproval,
            Optional<HubCrmBatchService> batch) {
        this.store = store;
        this.clients = clients;
        this.quotes = quotes;
        this.arpa = arpa;
        this.properties = properties;
        this.scheduler = scheduler;
        this.cteApproval = cteApproval;
        this.batch = batch;
    }

    @PostMapping("/api/hub-crm/acoes/lote")
    @ResponseBody
    public Object startBatch(@RequestParam(defaultValue = "false") boolean executar,
            @RequestParam(defaultValue = "2026-08-31") String ate) {
        return batch.<Object>map(service -> service.start(executar, java.time.LocalDate.parse(ate)).summary())
                .orElse(Map.of("ok", false, "message", "Aprovação automática por CT-e desligada"));
    }

    @GetMapping("/api/hub-crm/lote/status")
    @ResponseBody
    public Object batchStatus() {
        return batch.<Object>map(service -> service.status().summary())
                .orElse(Map.of("running", false, "phase", "Aprovação automática por CT-e desligada"));
    }

    @GetMapping("/api/hub-crm/lote")
    @ResponseBody
    public Object batchItems() {
        return batch.<Object>map(service -> service.status().items()).orElse(java.util.List.of());
    }

    @GetMapping({"/hub-crm", "/hub-crm/"})
    public String app() {
        return "redirect:/hub-crm/index.html";
    }

    @GetMapping("/api/hub-crm/status")
    @ResponseBody
    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", properties.enabled());
        result.put("pollingEnabled", properties.pollingEnabled());
        result.put("pollingDelayMs", properties.pollingDelayMs());
        result.put("autoApprovalEnabled", cteApproval.isPresent());
        result.put("summary", store.summary());
        result.put("scheduler", scheduler.<Object>map(HubCrmScheduler::status).orElse(Map.of(
                "running", false, "lastStarted", "", "lastFinished", "", "lastError", "polling desativado")));
        result.put("checkedAt", Instant.now());
        return result;
    }

    @GetMapping("/api/hub-crm/clientes")
    @ResponseBody
    public Object clients(@RequestParam(defaultValue = "100") int limit) {
        return store.clients(limit);
    }

    @GetMapping("/api/hub-crm/cotacoes")
    @ResponseBody
    public Object quotes(@RequestParam(defaultValue = "100") int limit) {
        return store.quotes(limit);
    }

    @GetMapping("/api/hub-crm/eventos")
    @ResponseBody
    public Object events(@RequestParam(defaultValue = "100") int limit) {
        return store.events(limit);
    }

    @GetMapping("/api/hub-crm/aprovacoes-cte")
    @ResponseBody
    public Object cteMatches(@RequestParam(defaultValue = "100") int limit) {
        return store.cteMatches(limit);
    }

    @GetMapping("/api/hub-crm/logs")
    @ResponseBody
    public Object logs(@RequestParam(defaultValue = "200") int limit,
            @RequestParam(required = false) String tipo,
            @RequestParam(defaultValue = "false") boolean erros) {
        return store.logs(limit, tipo, erros);
    }

    @PostMapping("/api/hub-crm/acoes/aprovar-por-cte")
    @ResponseBody
    public Object approveFromCtes() {
        return cteApproval.<Object>map(HubCrmCteApprovalService::approveFromCtes)
                .orElse(Map.of("ok", false, "message", "Aprovação automática por CT-e desligada"));
    }

    @PostMapping("/api/hub-crm/acoes/carga-inicial")
    @ResponseBody
    public Object initialLoad() {
        return clients.initialPilot();
    }

    @PostMapping("/api/hub-crm/acoes/carga-completa")
    @ResponseBody
    public Object fullLoad() {
        return clients.initialBatch();
    }

    @PostMapping("/api/hub-crm/acoes/sincronizar")
    @ResponseBody
    public Map<String, Object> synchronize() {
        return Map.of("clientes", clients.syncNewClients(), "cotacoes", quotes.syncQuotes());
    }

    @PostMapping("/api/hub-crm/acoes/validar-arpa")
    @ResponseBody
    public Map<String, Object> validateArpa() {
        arpa.validateCatalog();
        return Map.of("ok", true, "message", "Catálogo e acesso ao ArpaSuite validados");
    }

    @PostMapping("/api/hub-crm/acoes/reprocessar")
    @ResponseBody
    public Map<String, Object> reprocess(@RequestParam String tipo, @RequestParam long id) {
        store.reprocess(tipo, id);
        Object result = "CLIENTE".equalsIgnoreCase(tipo)
                ? clients.syncClient(id)
                : Map.of("status", "PENDENTE");
        return Map.of("ok", true, "tipo", tipo, "id", id, "resultado", result);
    }
}
