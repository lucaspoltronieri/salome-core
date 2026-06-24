package br.com.salome.core.infrastructure.web.torre;

import br.com.salome.core.application.torre.MapaArmazemExportService;
import br.com.salome.core.application.torre.MapaArmazemService;
import br.com.salome.core.domain.torre.MapaArmazemSnapshot;
import br.com.salome.core.domain.torre.MapaFiltro;
import java.time.LocalDate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import br.com.salome.core.infrastructure.torre.auth.AutenticacaoContexto;

/**
 * Torre operacional — "mapa do armazém". Página (shell estático) pública; o
 * snapshot e o export exigem login (JWT). A filial sai do token (ADMIN pode
 * trocar via {@code filial}), mesmo critério do painel TV.
 */
@Controller
@ConditionalOnProperty(prefix = "salome.torre", name = "enabled", havingValue = "true")
public class MapaWebController {

    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final MapaArmazemService mapaService;
    private final MapaArmazemExportService exportService;

    public MapaWebController(MapaArmazemService mapaService, MapaArmazemExportService exportService) {
        this.mapaService = mapaService;
        this.exportService = exportService;
    }

    @GetMapping("/torre/mapa/")
    public String pagina() {
        return "forward:/torre/mapa/index.html";
    }

    @GetMapping("/api/torre/mapa/snapshot")
    @ResponseBody
    public MapaArmazemSnapshot snapshot(@RequestParam(required = false) Integer filial) {
        return mapaService.snapshot(AutenticacaoContexto.filialAtiva(filial));
    }

    @GetMapping("/api/torre/mapa/export")
    public ResponseEntity<byte[]> exportar(@RequestParam(required = false) Integer filial,
                                           @RequestParam(required = false) String texto,
                                           @RequestParam(required = false) String cidade,
                                           @RequestParam(required = false) String situacao) {
        int idFilial = AutenticacaoContexto.filialAtiva(filial);
        MapaArmazemSnapshot snapshot = mapaService.snapshot(idFilial);
        byte[] arquivo = exportService.exportarXlsx(snapshot, new MapaFiltro(texto, cidade, situacao));
        String filename = "mapa-armazem-filial-" + idFilial + "-" + LocalDate.now() + ".xlsx";
        return ResponseEntity.ok()
                .contentType(XLSX_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(arquivo);
    }
}
