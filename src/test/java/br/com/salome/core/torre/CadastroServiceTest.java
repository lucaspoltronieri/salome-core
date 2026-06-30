package br.com.salome.core.torre;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.salome.core.application.torre.AuditoriaRepository;
import br.com.salome.core.application.torre.AuditoriaService;
import br.com.salome.core.application.torre.CadastroService;
import br.com.salome.core.application.torre.FilialTorreRepository;
import br.com.salome.core.application.torre.LocalArmazemRepository;
import br.com.salome.core.application.torre.auth.UsuarioRepository;
import br.com.salome.core.domain.torre.FilialTorre;
import br.com.salome.core.domain.torre.LocalArmazem;
import br.com.salome.core.domain.torre.PerfilCodigo;
import br.com.salome.core.domain.torre.SalvarFilialRequest;
import br.com.salome.core.domain.torre.auth.CriarUsuarioRequest;
import br.com.salome.core.domain.torre.auth.UsuarioAutenticado;
import br.com.salome.core.domain.torre.auth.UsuarioCredencial;
import br.com.salome.core.domain.torre.auth.UsuarioResumo;
import br.com.salome.core.domain.torre.erro.RegraViolada;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class CadastroServiceTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final UsuarioAutenticado admin =
            new UsuarioAutenticado(1L, "Admin", "admin", 2, PerfilCodigo.ADMIN);

    private final AtomicReference<String> acaoAuditada = new AtomicReference<>();

    private AuditoriaService auditoria() {
        AuditoriaRepository repo = new AuditoriaRepository() {
            @Override
            public long registrar(br.com.salome.core.domain.torre.EventoAuditoria e) {
                acaoAuditada.set(e.acao());
                return 1L;
            }

            @Override
            public List<br.com.salome.core.domain.torre.EventoAuditoria> listarPorFilial(int idFilial, int limite) {
                throw new UnsupportedOperationException();
            }
        };
        return new AuditoriaService(repo, Clock.systemUTC());
    }

    @Test
    void criarUsuario_filialExiste_hasheiaSenhaEAudita() {
        AtomicReference<String> hashGravado = new AtomicReference<>();
        UsuarioRepository usuarios = usuarioRepoCriando(hashGravado, 99L);
        FilialTorreRepository filiais = filialComBusca(Optional.of(
                new FilialTorre(2, "Rio Preto", LocalDate.of(2026, 6, 1), true)));

        CadastroService service = new CadastroService(usuarios, localStub(), filiais, encoder, auditoria());
        UsuarioResumo resumo = service.criarUsuario(
                new CriarUsuarioRequest("joao", "João", "senha123", 2, PerfilCodigo.OPERADOR), admin);

        assertThat(resumo.id()).isEqualTo(99L);
        assertThat(resumo.ativo()).isTrue();
        assertThat(resumo.perfil()).isEqualTo(PerfilCodigo.OPERADOR);
        // senha foi persistida como hash bcrypt, não em claro
        assertThat(hashGravado.get()).isNotEqualTo("senha123");
        assertThat(encoder.matches("senha123", hashGravado.get())).isTrue();
        assertThat(acaoAuditada.get()).isEqualTo("CRIAR_USUARIO");
    }

    @Test
    void criarUsuario_filialInexistente_lancaRegraViolada() {
        CadastroService service = new CadastroService(
                usuarioRepoCriando(new AtomicReference<>(), 1L), localStub(),
                filialComBusca(Optional.empty()), encoder, auditoria());

        assertThatThrownBy(() -> service.criarUsuario(
                new CriarUsuarioRequest("joao", "João", "senha123", 77, PerfilCodigo.OPERADOR), admin))
                .isInstanceOf(RegraViolada.class);
        assertThat(acaoAuditada.get()).isNull();
    }

    @Test
    void criarChapa_geraLoginDoNomeEPerfilChapa() {
        AtomicReference<String> loginGravado = new AtomicReference<>();
        AtomicReference<PerfilCodigo> perfilGravado = new AtomicReference<>();
        UsuarioRepository usuarios = new UsuarioRepository() {
            @Override
            public long criar(String login, String nome, String senhaHash, int idFilial, PerfilCodigo perfil) {
                loginGravado.set(login);
                perfilGravado.set(perfil);
                return 42L;
            }

            @Override
            public Optional<UsuarioCredencial> buscarPorLogin(String login) {
                return Optional.empty(); // nenhum login ocupado
            }

            @Override
            public List<UsuarioResumo> listar(int idFilial) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean definirAtivo(long id, boolean ativo) {
                throw new UnsupportedOperationException();
            }
        };
        FilialTorreRepository filiais = filialComBusca(Optional.of(
                new FilialTorre(2, "Rio Preto", LocalDate.of(2026, 6, 1), true)));
        CadastroService service = new CadastroService(usuarios, localStub(), filiais, encoder, auditoria());

        UsuarioResumo chapa = service.criarChapa(2, "José da Silva", admin);

        assertThat(chapa.id()).isEqualTo(42L);
        assertThat(chapa.nome()).isEqualTo("José da Silva");
        assertThat(chapa.perfil()).isEqualTo(PerfilCodigo.CHAPA);
        assertThat(perfilGravado.get()).isEqualTo(PerfilCodigo.CHAPA);
        assertThat(loginGravado.get()).isEqualTo("chapa.jos.da.silva");
        assertThat(acaoAuditada.get()).isEqualTo("CRIAR_CHAPA");
    }

    @Test
    void criarChapa_nomeVazio_lancaRegraViolada() {
        CadastroService service = new CadastroService(
                usuarioRepoCriando(new AtomicReference<>(), 1L), localStub(),
                filialComBusca(Optional.of(new FilialTorre(2, "Rio Preto", LocalDate.of(2026, 6, 1), true))),
                encoder, auditoria());

        assertThatThrownBy(() -> service.criarChapa(2, "   ", admin))
                .isInstanceOf(RegraViolada.class);
        assertThat(acaoAuditada.get()).isNull();
    }

    @Test
    void salvarFilial_criaOsTresBoxesPadrao() {
        List<LocalArmazem> criados = new ArrayList<>();
        LocalArmazemRepository locais = localCapturando(criados);
        CadastroService service = new CadastroService(
                usuarioRepoCriando(new AtomicReference<>(), 1L), locais, filialSalvavel(), encoder, auditoria());

        service.salvarFilial(new SalvarFilialRequest(2, "Rio Preto", LocalDate.of(2026, 6, 1), true), admin);

        assertThat(criados).extracting(LocalArmazem::codigo)
                .containsExactlyInAnyOrder("SEP", "DIST", "TRANSF");
        assertThat(criados).allMatch(l -> "BOX".equals(l.tipo()));
    }

    @Test
    void salvarFilial_naoDuplicaBoxesJaExistentes() {
        List<LocalArmazem> criados = new ArrayList<>();
        LocalArmazemRepository locais = localCapturando(criados,
                new LocalArmazem(1L, 2, "SEP", "Box Separação", "BOX", true));
        CadastroService service = new CadastroService(
                usuarioRepoCriando(new AtomicReference<>(), 1L), locais, filialSalvavel(), encoder, auditoria());

        service.salvarFilial(new SalvarFilialRequest(2, "Rio Preto", LocalDate.of(2026, 6, 1), true), admin);

        assertThat(criados).extracting(LocalArmazem::codigo)
                .containsExactlyInAnyOrder("DIST", "TRANSF");
    }

    // ---- stubs ----------------------------------------------------------

    private UsuarioRepository usuarioRepoCriando(AtomicReference<String> hashGravado, long idGerado) {
        return new UsuarioRepository() {
            @Override
            public long criar(String login, String nome, String senhaHash, int idFilial, PerfilCodigo perfil) {
                hashGravado.set(senhaHash);
                return idGerado;
            }

            @Override
            public Optional<UsuarioCredencial> buscarPorLogin(String login) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<UsuarioResumo> listar(int idFilial) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean definirAtivo(long id, boolean ativo) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private FilialTorreRepository filialComBusca(Optional<FilialTorre> resultado) {
        return new FilialTorreRepository() {
            @Override
            public Optional<FilialTorre> buscar(int idFilial) {
                return resultado;
            }

            @Override
            public List<FilialTorre> listarAtivas() {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<FilialTorre> listarTodas() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void salvar(FilialTorre filial) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private FilialTorreRepository filialSalvavel() {
        return new FilialTorreRepository() {
            @Override
            public Optional<FilialTorre> buscar(int idFilial) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<FilialTorre> listarAtivas() {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<FilialTorre> listarTodas() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void salvar(FilialTorre filial) {
                // no-op
            }
        };
    }

    /** Fake stateful: já tem {@code existentes} e acumula os boxes criados em {@code criados}. */
    private LocalArmazemRepository localCapturando(List<LocalArmazem> criados, LocalArmazem... existentes) {
        List<LocalArmazem> base = new ArrayList<>(List.of(existentes));
        return new LocalArmazemRepository() {
            @Override
            public List<LocalArmazem> listarAtivos(int idFilial) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<LocalArmazem> listarTodos(int idFilial) {
                List<LocalArmazem> todos = new ArrayList<>(base);
                todos.addAll(criados);
                return todos;
            }

            @Override
            public Optional<LocalArmazem> buscar(long id, int idFilial) {
                throw new UnsupportedOperationException();
            }

            @Override
            public long criar(LocalArmazem local) {
                criados.add(local);
                return criados.size();
            }

            @Override
            public boolean definirAtivo(long id, int idFilial, boolean ativo) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private LocalArmazemRepository localStub() {
        return new LocalArmazemRepository() {
            @Override
            public List<LocalArmazem> listarAtivos(int idFilial) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<LocalArmazem> listarTodos(int idFilial) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<LocalArmazem> buscar(long id, int idFilial) {
                throw new UnsupportedOperationException();
            }

            @Override
            public long criar(LocalArmazem local) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean definirAtivo(long id, int idFilial, boolean ativo) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
