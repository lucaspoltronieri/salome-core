package br.com.salome.core.infrastructure.web.torre;

import br.com.salome.core.application.torre.DashboardService;
import br.com.salome.core.domain.torre.DashboardSnapshot;
import br.com.salome.core.infrastructure.torre.auth.AutenticacaoContexto;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dashboard de entrada da Torre. Snapshot exige login (JWT); a filial sai do
 * token e ADMIN pode trocar via {@code filial}, como o resto da Torre.
 */
@RestController
@ConditionalOnProperty(prefix = "salome.torre", name = "enabled", havingValue = "true")
public class DashboardWebController {

    private final DashboardService dashboardService;

    public DashboardWebController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/api/torre/dashboard/snapshot")
    public DashboardSnapshot snapshot(@RequestParam(required = false) Integer filial) {
        return dashboardService.snapshot(AutenticacaoContexto.filialAtiva(filial));
    }
}
