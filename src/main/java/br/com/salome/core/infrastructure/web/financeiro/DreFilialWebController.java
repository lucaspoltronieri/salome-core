package br.com.salome.core.infrastructure.web.financeiro;

import br.com.salome.core.application.financeiro.DreFilialService;
import br.com.salome.core.domain.financeiro.DreFilialDetalhe;
import br.com.salome.core.domain.financeiro.DreFilialSnapshot;
import br.com.salome.core.domain.financeiro.FinanceiroFiltro;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class DreFilialWebController {

    private final DreFilialService service;

    public DreFilialWebController(DreFilialService service) {
        this.service = service;
    }

    @GetMapping("/financeiro/dre-filial")
    public String dreFilial() {
        return "redirect:/financeiro/dre-filial/";
    }

    @GetMapping("/financeiro/dre-filial/")
    public String dreFilialIndex() {
        return "forward:/financeiro/dre-filial/index.html";
    }

    @GetMapping("/api/financeiro/dre-filial")
    @ResponseBody
    public DreFilialSnapshot dashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) String busca,
            @RequestParam(required = false, defaultValue = "COMPETENCIA") String regime,
            @RequestParam(required = false) Double repasse) {
        return service.dashboard(new FinanceiroFiltro(inicio, fim, busca, statusDoRegime(regime), "TODAS"), regime,
                repasse);
    }

    @GetMapping("/api/financeiro/dre-filial/{idFilial}")
    @ResponseBody
    public DreFilialDetalhe dreDaFilial(
            @PathVariable int idFilial,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false, defaultValue = "COMPETENCIA") String regime,
            @RequestParam(required = false) Double repasse) {
        return service.dreDaFilial(idFilial, new FinanceiroFiltro(inicio, fim, null, statusDoRegime(regime), "TODAS"),
                regime, repasse);
    }

    private String statusDoRegime(String regime) {
        return "CAIXA".equalsIgnoreCase(regime) ? "REALIZADO" : "TODOS";
    }
}
