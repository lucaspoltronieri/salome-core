package br.com.salome.core.application.torre;

import br.com.salome.core.application.torre.auth.UsuarioRepository;
import br.com.salome.core.domain.torre.CriarLocalRequest;
import br.com.salome.core.domain.torre.FilialTorre;
import br.com.salome.core.domain.torre.LocalArmazem;
import br.com.salome.core.domain.torre.SalvarFilialRequest;
import br.com.salome.core.domain.torre.auth.CriarUsuarioRequest;
import br.com.salome.core.domain.torre.auth.UsuarioAutenticado;
import br.com.salome.core.domain.torre.auth.UsuarioResumo;
import br.com.salome.core.domain.torre.erro.RecursoNaoEncontrado;
import br.com.salome.core.domain.torre.erro.RegraViolada;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cadastros administrativos da Torre (usuários, locais, filiais). Toda escrita
 * é auditada. A verificação de perfil ADMIN fica no controller; aqui assume-se
 * que o chamador já é admin.
 */
@Service
@ConditionalOnProperty(prefix = "salome.torre", name = "enabled", havingValue = "true")
@Transactional("torreTransactionManager")
public class CadastroService {

    private final UsuarioRepository usuarioRepository;
    private final LocalArmazemRepository localRepository;
    private final FilialTorreRepository filialRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;

    public CadastroService(UsuarioRepository usuarioRepository,
                           LocalArmazemRepository localRepository,
                           FilialTorreRepository filialRepository,
                           PasswordEncoder passwordEncoder,
                           AuditoriaService auditoriaService) {
        this.usuarioRepository = usuarioRepository;
        this.localRepository = localRepository;
        this.filialRepository = filialRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
    }

    // ---- Usuários -------------------------------------------------------

    public UsuarioResumo criarUsuario(CriarUsuarioRequest req, UsuarioAutenticado admin) {
        filialRepository.buscar(req.idFilial())
                .orElseThrow(() -> new RegraViolada("Filial " + req.idFilial() + " não cadastrada na Torre."));
        String hash = passwordEncoder.encode(req.senha());
        long id = usuarioRepository.criar(req.login().trim(), req.nome().trim(), hash, req.idFilial(), req.perfil());
        auditoriaService.registrar(admin, "CRIAR_USUARIO", "usuario", id,
                req.login() + " (" + req.perfil() + ", filial " + req.idFilial() + ")");
        return new UsuarioResumo(id, req.nome().trim(), req.login().trim(), req.idFilial(), req.perfil(), true);
    }

    public void definirUsuarioAtivo(long id, boolean ativo, UsuarioAutenticado admin) {
        if (!usuarioRepository.definirAtivo(id, ativo)) {
            throw new RecursoNaoEncontrado("Usuário não encontrado.");
        }
        auditoriaService.registrar(admin, ativo ? "ATIVAR_USUARIO" : "DESATIVAR_USUARIO", "usuario", id, null);
    }

    @Transactional(value = "torreTransactionManager", readOnly = true)
    public List<UsuarioResumo> listarUsuarios(int idFilial) {
        return usuarioRepository.listar(idFilial);
    }

    // ---- Locais ---------------------------------------------------------

    public LocalArmazem criarLocal(int idFilial, CriarLocalRequest req, UsuarioAutenticado admin) {
        long id = localRepository.criar(new LocalArmazem(
                null, idFilial, req.codigo().trim(), req.nome().trim(), req.tipo().trim().toUpperCase(), true));
        auditoriaService.registrar(admin, "CRIAR_LOCAL", "local_armazem", id,
                req.codigo() + " (" + req.tipo() + ", filial " + idFilial + ")");
        return new LocalArmazem(id, idFilial, req.codigo().trim(), req.nome().trim(),
                req.tipo().trim().toUpperCase(), true);
    }

    public void definirLocalAtivo(long id, int idFilial, boolean ativo, UsuarioAutenticado admin) {
        if (!localRepository.definirAtivo(id, idFilial, ativo)) {
            throw new RecursoNaoEncontrado("Local não encontrado.");
        }
        auditoriaService.registrar(admin, ativo ? "ATIVAR_LOCAL" : "DESATIVAR_LOCAL", "local_armazem", id, null);
    }

    @Transactional(value = "torreTransactionManager", readOnly = true)
    public List<LocalArmazem> listarLocais(int idFilial) {
        return localRepository.listarTodos(idFilial);
    }

    // ---- Filiais --------------------------------------------------------

    public FilialTorre salvarFilial(SalvarFilialRequest req, UsuarioAutenticado admin) {
        FilialTorre filial = new FilialTorre(req.idFilial(), req.nome().trim(), req.dataCorteViagem(), req.ativa());
        filialRepository.salvar(filial);
        auditoriaService.registrar(admin, "SALVAR_FILIAL", "filial_torre", (long) req.idFilial(),
                req.nome() + " (corte " + req.dataCorteViagem() + ", ativa=" + req.ativa() + ")");
        return filial;
    }

    @Transactional(value = "torreTransactionManager", readOnly = true)
    public List<FilialTorre> listarFiliais() {
        return filialRepository.listarTodas();
    }
}
