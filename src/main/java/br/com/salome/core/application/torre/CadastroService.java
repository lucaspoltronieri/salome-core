package br.com.salome.core.application.torre;

import br.com.salome.core.application.torre.auth.UsuarioRepository;
import br.com.salome.core.domain.torre.BoxPadrao;
import br.com.salome.core.domain.torre.CriarLocalRequest;
import br.com.salome.core.domain.torre.FilialTorre;
import br.com.salome.core.domain.torre.LocalArmazem;
import br.com.salome.core.domain.torre.PerfilCodigo;
import br.com.salome.core.domain.torre.SalvarFilialRequest;
import br.com.salome.core.domain.torre.auth.CriarUsuarioRequest;
import br.com.salome.core.domain.torre.auth.UsuarioAutenticado;
import br.com.salome.core.domain.torre.auth.UsuarioResumo;
import br.com.salome.core.domain.torre.erro.RecursoNaoEncontrado;
import br.com.salome.core.domain.torre.erro.RegraViolada;
import java.util.List;
import java.util.UUID;
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

    /**
     * Cadastra uma chapa avulsa (mão de obra esporádica) só pelo nome. Diferente de
     * {@link #criarUsuario}, NÃO exige admin nem senha: a chapa não acessa o app — entra
     * na atividade pelo líder. O login é gerado a partir do nome (único) e a senha é
     * aleatória/descartável só pra satisfazer o NOT NULL.
     */
    public UsuarioResumo criarChapa(int idFilial, String nome, UsuarioAutenticado autor) {
        String nomeLimpo = nome == null ? "" : nome.trim();
        if (nomeLimpo.isBlank()) {
            throw new RegraViolada("Informe o nome da chapa.");
        }
        filialRepository.buscar(idFilial)
                .orElseThrow(() -> new RegraViolada("Filial " + idFilial + " não cadastrada na Torre."));
        String login = loginChapaDisponivel(nomeLimpo);
        String hash = passwordEncoder.encode(UUID.randomUUID().toString());
        long id = usuarioRepository.criar(login, nomeLimpo, hash, idFilial, PerfilCodigo.CHAPA);
        auditoriaService.registrar(autor, "CRIAR_CHAPA", "usuario", id,
                nomeLimpo + " (chapa avulsa, filial " + idFilial + ")");
        return new UsuarioResumo(id, nomeLimpo, login, idFilial, PerfilCodigo.CHAPA, true);
    }

    /** Gera um login livre derivado do nome (chapa.<slug>, com sufixo numérico se já existir). */
    private String loginChapaDisponivel(String nome) {
        String slug = nome.toLowerCase()
                .replaceAll("[^a-z0-9]+", ".")
                .replaceAll("(^\\.+)|(\\.+$)", "");
        if (slug.isBlank()) {
            slug = "avulsa";
        }
        String base = "chapa." + slug;
        String login = base;
        int sufixo = 1;
        while (usuarioRepository.buscarPorLogin(login).isPresent()) {
            login = base + "." + (++sufixo);
        }
        return login;
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
        garantirBoxesPadrao(req.idFilial(), admin);
        return filial;
    }

    /**
     * Garante que a filial tenha os 3 boxes-destino padrão (Separação, Distribuição,
     * Transferência). Idempotente: cria só os que faltam — chamado a cada upsert de filial,
     * eliminando o seed manual via SQL.
     */
    private void garantirBoxesPadrao(int idFilial, UsuarioAutenticado admin) {
        List<String> existentes = localRepository.listarTodos(idFilial).stream()
                .map(l -> l.codigo() == null ? "" : l.codigo().trim().toUpperCase())
                .toList();
        for (BoxPadrao box : BoxPadrao.values()) {
            if (existentes.contains(box.codigo())) {
                continue;
            }
            long id = localRepository.criar(new LocalArmazem(
                    null, idFilial, box.codigo(), box.nome(), BoxPadrao.TIPO, true));
            auditoriaService.registrar(admin, "CRIAR_LOCAL", "local_armazem", id,
                    box.codigo() + " (box padrão, filial " + idFilial + ")");
        }
    }

    @Transactional(value = "torreTransactionManager", readOnly = true)
    public List<FilialTorre> listarFiliais() {
        return filialRepository.listarTodas();
    }
}
