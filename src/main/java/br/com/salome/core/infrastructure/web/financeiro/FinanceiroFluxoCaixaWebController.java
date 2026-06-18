package br.com.salome.core.infrastructure.web.financeiro;

import br.com.salome.core.application.financeiro.FinanceiroFluxoCaixaService;
import br.com.salome.core.domain.financeiro.FinanceiroDashboardSnapshot;
import br.com.salome.core.domain.financeiro.FinanceiroDrillNode;
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
public class FinanceiroFluxoCaixaWebController {

    private final FinanceiroFluxoCaixaService service;

    public FinanceiroFluxoCaixaWebController(FinanceiroFluxoCaixaService service) {
        this.service = service;
    }

    @GetMapping("/financeiro/fluxo-caixa")
    public String fluxoCaixa() {
        return "redirect:/financeiro/fluxo-caixa/";
    }

    @GetMapping("/financeiro/fluxo-caixa/")
    public String fluxoCaixaIndex() {
        return "forward:/financeiro/fluxo-caixa/index.html";
    }

    @GetMapping("/api/financeiro/fluxo-caixa")
    @ResponseBody
    public FinanceiroDashboardSnapshot dashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) String busca,
            @RequestParam(defaultValue = "TODOS") String status,
            @RequestParam(defaultValue = "TODAS") String natureza) {
        return service.dashboard(new FinanceiroFiltro(inicio, fim, busca, status, natureza));
    }

    private FinanceiroFiltro periodo(LocalDate inicio, LocalDate fim) {
        return new FinanceiroFiltro(inicio, fim, null, "TODOS", "TODAS");
    }

    @GetMapping("/api/financeiro/fluxo-caixa/clientes")
    @ResponseBody
    public List<FinanceiroDrillNode> clientes(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return service.clientes(periodo(inicio, fim));
    }

    @GetMapping("/api/financeiro/fluxo-caixa/clientes/{idCliente}/faturas")
    @ResponseBody
    public List<FinanceiroDrillNode> faturasDoCliente(@PathVariable int idCliente,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return service.faturasDoCliente(idCliente, periodo(inicio, fim));
    }

    @GetMapping("/api/financeiro/fluxo-caixa/faturas/{idFatura}/ctes")
    @ResponseBody
    public List<FinanceiroDrillNode> ctesDaFatura(@PathVariable int idFatura) {
        return service.ctesDaFatura(idFatura);
    }

    @GetMapping("/api/financeiro/fluxo-caixa/fornecedores")
    @ResponseBody
    public List<FinanceiroDrillNode> fornecedores(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return service.fornecedores(periodo(inicio, fim));
    }

    @GetMapping("/api/financeiro/fluxo-caixa/fornecedores/{idFornecedor}/notas")
    @ResponseBody
    public List<FinanceiroDrillNode> notasDoFornecedor(@PathVariable int idFornecedor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return service.notasDoFornecedor(idFornecedor, periodo(inicio, fim));
    }

    @GetMapping("/api/financeiro/fluxo-caixa/notas/{idNotaCompra}/produtos")
    @ResponseBody
    public List<FinanceiroDrillNode> produtosDaNota(@PathVariable int idNotaCompra) {
        return service.produtosDaNota(idNotaCompra);
    }
}
