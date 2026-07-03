package br.com.salome.core.infrastructure.web.torre;

import br.com.salome.core.application.torre.RelatorioCargasExportService;
import br.com.salome.core.application.torre.RelatorioRepository;
import br.com.salome.core.domain.torre.RelatorioOperadores;
import br.com.salome.core.domain.torre.RelatorioProdutividadeCargas;
import br.com.salome.core.infrastructure.torre.auth.AutenticacaoContexto;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Relatórios operacionais da Torre. Escopo de filial (operador vê a própria;
 * ADMIN escolhe pelo seletor de filial). Período opcional — default é o dia atual.
 */
@RestController
@ConditionalOnProperty(prefix = "salome.torre", name = "enabled", havingValue = "true")
@RequestMapping("/api/torre/relatorios")
public class RelatorioWebController {

    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final RelatorioRepository relatorioRepository;
    private final RelatorioCargasExportService cargasExportService;
    private final Clock clock;

    public RelatorioWebController(RelatorioRepository relatorioRepository,
                                  RelatorioCargasExportService cargasExportService,
                                  Clock clock) {
        this.relatorioRepository = relatorioRepository;
        this.cargasExportService = cargasExportService;
        this.clock = clock;
    }

    @GetMapping("/operadores")
    public RelatorioOperadores operadores(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(required = false) Integer filial) {
        LocalDate hoje = LocalDate.now(clock);
        LocalDate inicio = de != null ? de : hoje;
        LocalDate fim = ate != null ? ate : hoje;
        return relatorioRepository.operadores(AutenticacaoContexto.filialAtiva(filial), inicio, fim);
    }

    @GetMapping("/cargas")
    public RelatorioProdutividadeCargas cargas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(required = false) Integer filial) {
        LocalDate hoje = LocalDate.now(clock);
        LocalDate inicio = de != null ? de : hoje;
        LocalDate fim = ate != null ? ate : hoje;
        return relatorioRepository.produtividadeCargas(AutenticacaoContexto.filialAtiva(filial), inicio, fim);
    }

    @GetMapping("/cargas/export")
    public ResponseEntity<byte[]> cargasExport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(required = false) Integer filial) {
        LocalDate hoje = LocalDate.now(clock);
        LocalDate inicio = de != null ? de : hoje;
        LocalDate fim = ate != null ? ate : hoje;
        int idFilial = AutenticacaoContexto.filialAtiva(filial);
        RelatorioProdutividadeCargas rel = relatorioRepository.produtividadeCargas(idFilial, inicio, fim);
        byte[] arquivo = cargasExportService.exportarXlsx(rel);
        String filename = "produtividade-cargas-filial-" + idFilial + "-" + inicio + "_a_" + fim + ".xlsx";
        return ResponseEntity.ok()
                .contentType(XLSX_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(arquivo);
    }
}
