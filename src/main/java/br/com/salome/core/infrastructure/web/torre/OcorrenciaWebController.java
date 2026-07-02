package br.com.salome.core.infrastructure.web.torre;

import br.com.salome.core.application.torre.OcorrenciaService;
import br.com.salome.core.domain.torre.Ocorrencia;
import br.com.salome.core.domain.torre.OcorrenciaDetalhe;
import br.com.salome.core.domain.torre.RegistrarAvariaRequest;
import br.com.salome.core.domain.torre.RegistrarOcorrenciaRequest;
import br.com.salome.core.domain.torre.TratarOcorrenciaRequest;
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
import org.springframework.web.bind.annotation.PatchMapping;
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

    /** Registro simples com foto opcional, corpo multipart (campos em {@code dados} + arquivo {@code foto}). */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Ocorrencia registrarComFoto(@Valid @RequestPart("dados") RegistrarOcorrenciaRequest req,
                                       @RequestPart(value = "foto", required = false) MultipartFile foto,
                                       @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return ocorrenciaService.registrar(req, foto, usuario);
    }

    /**
     * Registro de avaria (fluxo completo): CT-es, várias fotos ({@code fotos}) e fotos da
     * nota fiscal ({@code fotosNf}), itens avariados — tudo amarrado a uma atividade/viagem.
     */
    @PostMapping(path = "/avaria", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public OcorrenciaDetalhe registrarAvaria(
            @Valid @RequestPart("dados") RegistrarAvariaRequest req,
            @RequestPart(value = "fotos", required = false) List<MultipartFile> fotos,
            @RequestPart(value = "fotosNf", required = false) List<MultipartFile> fotosNf,
            @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return ocorrenciaService.registrarAvaria(req, fotos, fotosNf, usuario);
    }

    @GetMapping
    public List<Ocorrencia> listar(@RequestParam(required = false) Integer filial,
                                   @RequestParam(required = false) String status,
                                   @RequestParam(required = false) String tipo) {
        return ocorrenciaService.listar(AutenticacaoContexto.filialAtiva(filial), status, tipo);
    }

    @GetMapping("/{id}")
    public OcorrenciaDetalhe detalhe(@PathVariable long id,
                                     @RequestParam(required = false) Integer filial) {
        return ocorrenciaService.buscarDetalhe(id, AutenticacaoContexto.filialAtiva(filial));
    }

    /** Tratamento/atualização parcial da ocorrência (ciclo de status). */
    @PatchMapping("/{id}")
    public OcorrenciaDetalhe tratar(@PathVariable long id,
                                    @RequestBody TratarOcorrenciaRequest req,
                                    @RequestParam(required = false) Integer filial,
                                    @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return ocorrenciaService.tratar(id, AutenticacaoContexto.filialAtiva(filial), req, usuario);
    }

    /** Foto principal (legado, uma foto por ocorrência). */
    @GetMapping("/{id}/foto")
    public ResponseEntity<Resource> foto(@PathVariable long id,
                                         @RequestParam(required = false) Integer filial) {
        Path caminho = ocorrenciaService.caminhoFoto(id, AutenticacaoContexto.filialAtiva(filial));
        return servir(caminho);
    }

    /** Uma foto específica da galeria da ocorrência. */
    @GetMapping("/{id}/fotos/{fotoId}")
    public ResponseEntity<Resource> fotoDe(@PathVariable long id,
                                           @PathVariable long fotoId,
                                           @RequestParam(required = false) Integer filial) {
        Path caminho = ocorrenciaService.caminhoFotoDe(id, fotoId, AutenticacaoContexto.filialAtiva(filial));
        return servir(caminho);
    }

    private static ResponseEntity<Resource> servir(Path caminho) {
        return ResponseEntity.ok()
                .contentType(tipo(caminho))
                .header(HttpHeaders.CACHE_CONTROL, "max-age=86400")
                .body(new FileSystemResource(caminho));
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
