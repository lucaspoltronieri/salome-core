package br.com.salome.core.infrastructure.web.torre;

import br.com.salome.core.application.torre.CadastroService;
import br.com.salome.core.domain.torre.CriarLocalRequest;
import br.com.salome.core.domain.torre.FilialTorre;
import br.com.salome.core.domain.torre.LocalArmazem;
import br.com.salome.core.domain.torre.SalvarFilialRequest;
import br.com.salome.core.domain.torre.auth.CriarUsuarioRequest;
import br.com.salome.core.domain.torre.auth.UsuarioAutenticado;
import br.com.salome.core.domain.torre.auth.UsuarioResumo;
import br.com.salome.core.domain.torre.erro.AcessoNegado;
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

/**
 * Cadastros administrativos da Torre. Todas as rotas exigem perfil ADMIN.
 */
@RestController
@ConditionalOnProperty(prefix = "salome.torre", name = "enabled", havingValue = "true")
@RequestMapping("/api/torre/admin")
public class AdminWebController {

    private final CadastroService cadastroService;

    public AdminWebController(CadastroService cadastroService) {
        this.cadastroService = cadastroService;
    }

    // ---- Usuários -------------------------------------------------------

    @PostMapping("/usuarios")
    public UsuarioResumo criarUsuario(@Valid @RequestBody CriarUsuarioRequest req,
                                      @AuthenticationPrincipal UsuarioAutenticado admin) {
        exigirAdmin(admin);
        return cadastroService.criarUsuario(req, admin);
    }

    @GetMapping("/usuarios")
    public List<UsuarioResumo> listarUsuarios(@RequestParam(required = false) Integer filial,
                                              @AuthenticationPrincipal UsuarioAutenticado admin) {
        exigirAdmin(admin);
        return cadastroService.listarUsuarios(AutenticacaoContexto.filialAtiva(filial));
    }

    @PostMapping("/usuarios/{id}/ativo")
    public void definirUsuarioAtivo(@PathVariable long id, @RequestParam boolean ativo,
                                    @AuthenticationPrincipal UsuarioAutenticado admin) {
        exigirAdmin(admin);
        cadastroService.definirUsuarioAtivo(id, ativo, admin);
    }

    // ---- Locais ---------------------------------------------------------

    @PostMapping("/locais")
    public LocalArmazem criarLocal(@Valid @RequestBody CriarLocalRequest req,
                                   @RequestParam(required = false) Integer filial,
                                   @AuthenticationPrincipal UsuarioAutenticado admin) {
        exigirAdmin(admin);
        return cadastroService.criarLocal(AutenticacaoContexto.filialAtiva(filial), req, admin);
    }

    @GetMapping("/locais")
    public List<LocalArmazem> listarLocais(@RequestParam(required = false) Integer filial,
                                           @AuthenticationPrincipal UsuarioAutenticado admin) {
        exigirAdmin(admin);
        return cadastroService.listarLocais(AutenticacaoContexto.filialAtiva(filial));
    }

    @PostMapping("/locais/{id}/ativo")
    public void definirLocalAtivo(@PathVariable long id, @RequestParam boolean ativo,
                                  @RequestParam(required = false) Integer filial,
                                  @AuthenticationPrincipal UsuarioAutenticado admin) {
        exigirAdmin(admin);
        cadastroService.definirLocalAtivo(id, AutenticacaoContexto.filialAtiva(filial), ativo, admin);
    }

    // ---- Filiais --------------------------------------------------------

    @PostMapping("/filiais")
    public FilialTorre salvarFilial(@Valid @RequestBody SalvarFilialRequest req,
                                    @AuthenticationPrincipal UsuarioAutenticado admin) {
        exigirAdmin(admin);
        return cadastroService.salvarFilial(req, admin);
    }

    @GetMapping("/filiais")
    public List<FilialTorre> listarFiliais(@AuthenticationPrincipal UsuarioAutenticado admin) {
        exigirAdmin(admin);
        return cadastroService.listarFiliais();
    }

    private void exigirAdmin(UsuarioAutenticado usuario) {
        if (usuario == null || !usuario.isAdmin()) {
            throw new AcessoNegado("Apenas administradores acessam os cadastros.");
        }
    }
}
