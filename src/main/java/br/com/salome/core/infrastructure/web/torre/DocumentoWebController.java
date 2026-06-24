package br.com.salome.core.infrastructure.web.torre;

import br.com.salome.core.application.torre.DocumentoService;
import br.com.salome.core.domain.torre.CteDescarga;
import br.com.salome.core.domain.torre.DocumentoOperacional;
import br.com.salome.core.domain.torre.auth.UsuarioAutenticado;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(prefix = "salome.torre", name = "enabled", havingValue = "true")
@RequestMapping("/api/torre/atividades/{id}")
public class DocumentoWebController {

    private final DocumentoService documentoService;

    public DocumentoWebController(DocumentoService documentoService) {
        this.documentoService = documentoService;
    }

    /** CT-es da viagem desta descarga (lidos do legado). */
    @GetMapping("/ctes-disponiveis")
    public List<CteDescarga> ctesDisponiveis(@PathVariable long id,
                                             @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return documentoService.listarCtesDaDescarga(id, usuario);
    }

    /** Bipa/registra um CT-e como descarregado. */
    @PostMapping("/documentos")
    public DocumentoOperacional registrar(@PathVariable long id,
                                          @RequestBody RegistrarDocumento corpo,
                                          @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return documentoService.registrarDescarga(id, corpo.idConhecimento(), usuario);
    }

    /** Documentos já registrados nesta atividade. */
    @GetMapping("/documentos")
    public List<DocumentoOperacional> listar(@PathVariable long id,
                                             @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return documentoService.listarDocumentos(id, usuario);
    }

    public record RegistrarDocumento(@NotNull Long idConhecimento) {
    }
}
