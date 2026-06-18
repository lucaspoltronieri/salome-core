package br.com.salome.core.infrastructure.web.financeiro;

import br.com.salome.core.application.financeiro.CteSemFaturaExportService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class CteSemFaturaExportWebController {

    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final CteSemFaturaExportService service;

    public CteSemFaturaExportWebController(CteSemFaturaExportService service) {
        this.service = service;
    }

    @GetMapping("/api/financeiro/ctes-sem-fatura/export")
    public ResponseEntity<byte[]> exportar(
            @RequestParam(defaultValue = "2026-05-31")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        byte[] arquivo = service.exportarXlsx(ate);
        String filename = "ctes-sem-fatura-ate-" + ate + ".xlsx";
        return ResponseEntity.ok()
                .contentType(XLSX_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(arquivo);
    }
}
