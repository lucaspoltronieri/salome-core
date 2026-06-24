package br.com.salome.core.infrastructure.web.torre;

import br.com.salome.core.application.torre.AuditoriaService;
import br.com.salome.core.domain.torre.EventoAuditoria;
import br.com.salome.core.domain.torre.auth.UsuarioAutenticado;
import br.com.salome.core.domain.torre.erro.AcessoNegado;
import br.com.salome.core.infrastructure.torre.auth.AutenticacaoContexto;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta do rastro de auditoria. Restrita a ADMIN.
 */
@RestController
@ConditionalOnProperty(prefix = "salome.torre", name = "enabled", havingValue = "true")
@RequestMapping("/api/torre/auditoria")
public class AuditoriaWebController {

    private final AuditoriaService auditoriaService;

    public AuditoriaWebController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping
    public List<EventoAuditoria> listar(@RequestParam(required = false) Integer filial,
                                        @AuthenticationPrincipal UsuarioAutenticado usuario) {
        if (usuario == null || !usuario.isAdmin()) {
            throw new AcessoNegado("Apenas administradores acessam a auditoria.");
        }
        return auditoriaService.listar(AutenticacaoContexto.filialAtiva(filial));
    }
}
