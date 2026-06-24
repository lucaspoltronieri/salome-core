package br.com.salome.core.infrastructure.web.torre;

import br.com.salome.core.application.torre.OcorrenciaService;
import br.com.salome.core.domain.torre.Ocorrencia;
import br.com.salome.core.domain.torre.RegistrarOcorrenciaRequest;
import br.com.salome.core.domain.torre.auth.UsuarioAutenticado;
import br.com.salome.core.infrastructure.torre.auth.AutenticacaoContexto;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@ConditionalOnProperty(prefix = "salome.torre", name = "enabled", havingValue = "true")
@RequestMapping("/api/torre/ocorrencias")
public class OcorrenciaWebController {

    private final OcorrenciaService ocorrenciaService;

    public OcorrenciaWebController(OcorrenciaService ocorrenciaService) {
        this.ocorrenciaService = ocorrenciaService;
    }

    /** Registro simples (sem foto), corpo JSON. */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public Ocorrencia registrar(@Valid @RequestBody RegistrarOcorrenciaRequest req,
                                @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return ocorrenciaService.registrar(req, usuario);
    }

    /** Registro com foto opcional, corpo multipart (campos em {@code dados} + arquivo {@code foto}). */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Ocorrencia registrarComFoto(@Valid @RequestPart("dados") RegistrarOcorrenciaRequest req,
                                       @RequestPart(value = "foto", required = false) MultipartFile foto,
                                       @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return ocorrenciaService.registrar(req, foto, usuario);
    }

    @GetMapping("/{id}/foto")
    public ResponseEntity<Resource> foto(@PathVariable long id,
                                         @RequestParam(required = false) Integer filial) {
        Path caminho = ocorrenciaService.caminhoFoto(id, AutenticacaoContexto.filialAtiva(filial));
        return ResponseEntity.ok()
                .contentType(tipo(caminho))
                .header(HttpHeaders.CACHE_CONTROL, "max-age=86400")
                .body(new FileSystemResource(caminho));
    }

    @GetMapping
    public List<Ocorrencia> listar(@RequestParam(required = false) Integer filial) {
        return ocorrenciaService.listar(AutenticacaoContexto.filialAtiva(filial));
    }

    private static MediaType tipo(Path caminho) {
        try {
            String mime = Files.probeContentType(caminho);
            return mime == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(mime);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
