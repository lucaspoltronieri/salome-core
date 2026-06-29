package br.com.salome.core.infrastructure.web.torre;

import br.com.salome.core.application.torre.auth.UsuarioRepository;
import br.com.salome.core.domain.torre.auth.UsuarioResumo;
import br.com.salome.core.infrastructure.torre.auth.AutenticacaoContexto;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lista simples de usuários da filial — para o app escolher quem adicionar à
 * atividade (colegas e chapas). Diferente de {@code /admin/usuarios}, é acessível
 * a qualquer usuário autenticado (não exige ADMIN).
 */
@RestController
@ConditionalOnProperty(prefix = "salome.torre", name = "enabled", havingValue = "true")
@RequestMapping("/api/torre/usuarios")
public class UsuarioWebController {

    private final UsuarioRepository usuarioRepository;

    public UsuarioWebController(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping
    public List<UsuarioResumo> listar(@RequestParam(required = false) Integer filial) {
        return usuarioRepository.listar(AutenticacaoContexto.filialAtiva(filial));
    }
}
