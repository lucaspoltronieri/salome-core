package br.com.salome.core.infrastructure.web.financeiro;

import br.com.salome.core.application.financeiro.DreGerencialCompetenciaService;
import br.com.salome.core.application.financeiro.DreGerencialService;
import br.com.salome.core.domain.financeiro.DreFaturaCte;
import br.com.salome.core.domain.financeiro.DreGerencialSnapshot;
import br.com.salome.core.domain.financeiro.FinanceiroFiltro;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class DreGerencialWebController {

    private final DreGerencialService service;
    private final DreGerencialCompetenciaService competenciaService;

    public DreGerencialWebController(DreGerencialService service,
            DreGerencialCompetenciaService competenciaService) {
        this.service = service;
        this.competenciaService = competenciaService;
    }

    @GetMapping("/financeiro/dre-gerencial")
    public String dreGerencial() {
        return "redirect:/financeiro/dre-gerencial/";
    }

    @GetMapping("/financeiro/dre-gerencial/")
    public String dreGerencialIndex() {
        return "forward:/financeiro/dre-gerencial/index.html";
    }

    @GetMapping("/api/financeiro/dre-gerencial")
    @ResponseBody
    public DreGerencialSnapshot dashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) Integer filialId,
            @RequestParam(required = false) String filial,
            @RequestParam(required = false) String busca) {
        return service.dashboard(new FinanceiroFiltro(inicio, fim, busca, "REALIZADO", "TODAS"), filialId, filial);
    }

    @GetMapping("/api/financeiro/dre-gerencial-competencia")
    @ResponseBody
    public DreGerencialSnapshot dashboardCompetencia(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) Integer filialId,
            @RequestParam(required = false) String filial,
            @RequestParam(required = false) String busca) {
        return competenciaService.dashboard(new FinanceiroFiltro(inicio, fim, busca, "TODOS", "TODAS"), filialId, filial);
    }

    @GetMapping("/api/financeiro/dre-gerencial/faturas/{idFatura}/ctes")
    @ResponseBody
    public List<DreFaturaCte> ctesDaFatura(@PathVariable int idFatura) {
        return service.ctesDaFatura(idFatura);
    }
}
