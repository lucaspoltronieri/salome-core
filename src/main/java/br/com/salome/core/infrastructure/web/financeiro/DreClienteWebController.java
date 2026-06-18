package br.com.salome.core.infrastructure.web.financeiro;

import br.com.salome.core.application.financeiro.DreClienteService;
import br.com.salome.core.domain.financeiro.DreClienteDetalhe;
import br.com.salome.core.domain.financeiro.DreClienteSnapshot;
import br.com.salome.core.domain.financeiro.FinanceiroFiltro;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class DreClienteWebController {

    private final DreClienteService service;

    public DreClienteWebController(DreClienteService service) {
        this.service = service;
    }

    @GetMapping("/financeiro/dre-cliente")
    public String dreCliente() {
        return "redirect:/financeiro/dre-cliente/";
    }

    @GetMapping("/financeiro/dre-cliente/")
    public String dreClienteIndex() {
        return "forward:/financeiro/dre-cliente/index.html";
    }

    @GetMapping("/api/financeiro/dre-cliente")
    @ResponseBody
    public DreClienteSnapshot dashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) Integer filialId,
            @RequestParam(required = false) String filial,
            @RequestParam(required = false) String busca,
            @RequestParam(required = false, defaultValue = "PESO") String driver,
            @RequestParam(required = false, defaultValue = "COMPETENCIA") String regime) {
        return service.dashboard(new FinanceiroFiltro(inicio, fim, busca, statusDoRegime(regime), "TODAS"), filialId,
                filial, driver, regime);
    }

    @GetMapping("/api/financeiro/dre-cliente/{idCliente}")
    @ResponseBody
    public DreClienteDetalhe dreDoCliente(
            @PathVariable int idCliente,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) Integer filialId,
            @RequestParam(required = false) String filial,
            @RequestParam(required = false, defaultValue = "PESO") String driver,
            @RequestParam(required = false, defaultValue = "COMPETENCIA") String regime) {
        return service.dreDoCliente(idCliente, new FinanceiroFiltro(inicio, fim, null, statusDoRegime(regime), "TODAS"),
                filialId, filial, driver, regime);
    }

    private String statusDoRegime(String regime) {
        return "CAIXA".equalsIgnoreCase(regime) ? "REALIZADO" : "TODOS";
    }
}
