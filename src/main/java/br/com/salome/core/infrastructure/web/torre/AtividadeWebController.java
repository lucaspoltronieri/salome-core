package br.com.salome.core.infrastructure.web.torre;

import br.com.salome.core.application.torre.AtividadeService;
import br.com.salome.core.domain.torre.AbrirAtividadeRequest;
import br.com.salome.core.domain.torre.AtividadeFinalizada;
import br.com.salome.core.domain.torre.AtividadeResumo;
import br.com.salome.core.domain.torre.CancelarAtividadeRequest;
import br.com.salome.core.domain.torre.EntrarAtividadeRequest;
import br.com.salome.core.domain.torre.auth.UsuarioAutenticado;
import br.com.salome.core.infrastructure.torre.auth.AutenticacaoContexto;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(prefix = "salome.torre", name = "enabled", havingValue = "true")
@RequestMapping("/api/torre/atividades")
public class AtividadeWebController {

    private final AtividadeService atividadeService;

    public AtividadeWebController(AtividadeService atividadeService) {
        this.atividadeService = atividadeService;
    }

    @PostMapping
    public AtividadeResumo abrir(@Valid @RequestBody AbrirAtividadeRequest request,
                                 @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return atividadeService.abrir(request, usuario);
    }

    @PostMapping("/{id}/entrar")
    public AtividadeResumo entrar(@PathVariable long id,
                                  @RequestBody(required = false) EntrarAtividadeRequest request,
                                  @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return atividadeService.entrar(id, request, usuario);
    }

    @PostMapping("/{id}/sair")
    public AtividadeResumo sair(@PathVariable long id,
                                @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return atividadeService.sair(id, usuario);
    }

    @PostMapping("/{id}/finalizar")
    public AtividadeFinalizada finalizar(@PathVariable long id,
                                         @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return atividadeService.finalizar(id, usuario);
    }

    @PostMapping("/{id}/cancelar")
    public AtividadeResumo cancelar(@PathVariable long id,
                                    @Valid @RequestBody CancelarAtividadeRequest request,
                                    @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return atividadeService.cancelar(id, request.motivo(), usuario);
    }

    @GetMapping("/abertas")
    public List<AtividadeResumo> listarAbertas(@RequestParam(required = false) Integer filial) {
        return atividadeService.listarAbertas(AutenticacaoContexto.filialAtiva(filial));
    }
}
