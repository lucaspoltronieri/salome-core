package br.com.salome.core.application.torre;

import br.com.salome.core.domain.torre.Ocorrencia;
import br.com.salome.core.domain.torre.RegistrarOcorrenciaRequest;
import br.com.salome.core.domain.torre.auth.UsuarioAutenticado;
import br.com.salome.core.domain.torre.erro.RecursoNaoEncontrado;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@ConditionalOnProperty(prefix = "salome.torre", name = "enabled", havingValue = "true")
@Transactional("torreTransactionManager")
public class OcorrenciaService {

    private static final int LIMITE = 200;

    private final OcorrenciaRepository ocorrenciaRepository;
    private final FotoStorageService fotoStorageService;
    private final Clock clock;

    public OcorrenciaService(OcorrenciaRepository ocorrenciaRepository,
                             FotoStorageService fotoStorageService,
                             Clock clock) {
        this.ocorrenciaRepository = ocorrenciaRepository;
        this.fotoStorageService = fotoStorageService;
        this.clock = clock;
    }

    public Ocorrencia registrar(RegistrarOcorrenciaRequest req, UsuarioAutenticado usuario) {
        return registrar(req, null, usuario);
    }

    /**
     * Registra a ocorrência; se {@code foto} vier, salva no disco e o servidor
     * define o {@code foto_path} (o path informado no corpo é ignorado).
     */
    public Ocorrencia registrar(RegistrarOcorrenciaRequest req, MultipartFile foto, UsuarioAutenticado usuario) {
        String fotoPath = foto != null && !foto.isEmpty()
                ? fotoStorageService.salvar(foto, usuario.idFilial())
                : null;
        Ocorrencia o = new Ocorrencia(null, usuario.idFilial(), req.tipo(), req.idDocumento(),
                req.idAtividade(), req.placa(), req.descricao(), fotoPath, usuario.id(), clock.instant());
        long id = ocorrenciaRepository.inserir(o);
        return new Ocorrencia(id, o.idFilial(), o.tipo(), o.idDocumento(), o.idAtividade(),
                o.placaVeiculo(), o.descricao(), o.fotoPath(), o.idUsuario(), o.criadoEm());
    }

    /** Caminho absoluto da foto de uma ocorrência da filial (para servir o binário). */
    @Transactional(value = "torreTransactionManager", readOnly = true)
    public Path caminhoFoto(long idOcorrencia, int idFilial) {
        Ocorrencia o = ocorrenciaRepository.buscar(idOcorrencia, idFilial)
                .orElseThrow(() -> new RecursoNaoEncontrado("Ocorrência não encontrada."));
        return fotoStorageService.resolver(o.fotoPath());
    }

    @Transactional(value = "torreTransactionManager", readOnly = true)
    public List<Ocorrencia> listar(int idFilial) {
        return ocorrenciaRepository.listarPorFilial(idFilial, LIMITE);
    }
}
