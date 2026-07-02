package br.com.salome.core.application.torre;

import br.com.salome.core.domain.torre.Atividade;
import br.com.salome.core.domain.torre.DocumentoOperacional;
import br.com.salome.core.domain.torre.Ocorrencia;
import br.com.salome.core.domain.torre.OcorrenciaCte;
import br.com.salome.core.domain.torre.OcorrenciaDetalhe;
import br.com.salome.core.domain.torre.OcorrenciaFoto;
import br.com.salome.core.domain.torre.OcorrenciaItem;
import br.com.salome.core.domain.torre.RegistrarAvariaRequest;
import br.com.salome.core.domain.torre.RegistrarOcorrenciaRequest;
import br.com.salome.core.domain.torre.StatusOcorrencia;
import br.com.salome.core.domain.torre.TratarOcorrenciaRequest;
import br.com.salome.core.domain.torre.auth.UsuarioAutenticado;
import br.com.salome.core.domain.torre.erro.RecursoNaoEncontrado;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@ConditionalOnProperty(prefix = "salome.torre", name = "enabled", havingValue = "true")
@Transactional("torreTransactionManager")
public class OcorrenciaService {

    private static final int LIMITE = 200;
    private static final String CAT_AVARIA = "AVARIA";
    private static final String CAT_NF = "NOTA_FISCAL";

    private final OcorrenciaRepository ocorrenciaRepository;
    private final AtividadeRepository atividadeRepository;
    private final DocumentoRepository documentoRepository;
    private final FotoStorageService fotoStorageService;
    private final AuditoriaService auditoriaService;
    private final Clock clock;

    public OcorrenciaService(OcorrenciaRepository ocorrenciaRepository,
                             AtividadeRepository atividadeRepository,
                             DocumentoRepository documentoRepository,
                             FotoStorageService fotoStorageService,
                             AuditoriaService auditoriaService,
                             Clock clock) {
        this.ocorrenciaRepository = ocorrenciaRepository;
        this.atividadeRepository = atividadeRepository;
        this.documentoRepository = documentoRepository;
        this.fotoStorageService = fotoStorageService;
        this.auditoriaService = auditoriaService;
        this.clock = clock;
    }

    // ---- Registro simples (compatível com o app antigo) --------------------

    public Ocorrencia registrar(RegistrarOcorrenciaRequest req, UsuarioAutenticado usuario) {
        return registrar(req, null, usuario);
    }

    /**
     * Registra a ocorrência simples; se {@code foto} vier, salva no disco e o servidor
     * define o {@code foto_path} (o path informado no corpo é ignorado).
     */
    public Ocorrencia registrar(RegistrarOcorrenciaRequest req, MultipartFile foto, UsuarioAutenticado usuario) {
        String fotoPath = foto != null && !foto.isEmpty()
                ? fotoStorageService.salvar(foto, usuario.idFilial())
                : null;
        Ocorrencia o = new Ocorrencia(null, usuario.idFilial(), req.tipo(), StatusOcorrencia.REGISTRADA.name(),
                req.idDocumento(), req.idAtividade(), req.placa(), null, null, req.descricao(),
                null, null, null, null, null, null, fotoPath, usuario.id(), clock.instant(), null, null, null);
        long id = ocorrenciaRepository.inserir(o);
        return ocorrenciaRepository.buscar(id, usuario.idFilial())
                .orElseThrow(() -> new RecursoNaoEncontrado("Ocorrência não encontrada."));
    }

    // ---- Registro de avaria (fluxo completo do app) ------------------------

    /**
     * Registra uma avaria amarrada a uma atividade (viagem): herda placa/motorista/viagem
     * da atividade, resolve os CT-es informados contra os documentos dela, grava as fotos
     * (avaria e nota fiscal) e os itens avariados.
     */
    public OcorrenciaDetalhe registrarAvaria(RegistrarAvariaRequest req,
                                             List<MultipartFile> fotos,
                                             List<MultipartFile> fotosNf,
                                             UsuarioAutenticado usuario) {
        int idFilial = usuario.idFilial();
        Atividade atividade = atividadeRepository.buscar(req.idAtividade(), idFilial)
                .orElseThrow(() -> new RecursoNaoEncontrado("Atividade não encontrada para a filial."));

        String placa = primeiroNaoVazio(atividade.placaVeiculo(), req.placa());
        String motorista = req.motorista();
        String tipo = (req.tipo() == null || req.tipo().isBlank()) ? "AVARIA" : req.tipo();
        Instant identificacao = req.dataIdentificacao() != null ? req.dataIdentificacao() : clock.instant();

        List<OcorrenciaCte> ctes = resolverCtes(req.numerosCte(), req.idAtividade());
        List<OcorrenciaFoto> fotosAvaria = salvarFotos(fotos, CAT_AVARIA, idFilial, 0);
        List<OcorrenciaFoto> todasFotos = new ArrayList<>(fotosAvaria);
        todasFotos.addAll(salvarFotos(fotosNf, CAT_NF, idFilial, 0));
        List<OcorrenciaItem> itens = mapearItens(req.itens());

        String fotoPath = fotosAvaria.isEmpty() ? null : fotosAvaria.get(0).fotoPath();

        Ocorrencia cabecalho = new Ocorrencia(null, idFilial, tipo, StatusOcorrencia.REGISTRADA.name(),
                null, req.idAtividade(), placa, motorista, atividade.idViagemLegado(), req.descricao(),
                req.culpa(), req.quemCausou(), req.responsavelPagamento(), req.valorTotal(),
                identificacao, req.duracaoSegundos(), fotoPath, usuario.id(), clock.instant(), null, null, null);

        long id = ocorrenciaRepository.inserirDetalhe(cabecalho, ctes, todasFotos, itens);
        auditoriaService.registrar(usuario, "REGISTRAR_AVARIA", "ocorrencia", id,
                "atividade=" + req.idAtividade() + " ctes=" + ctes.size() + " fotos=" + todasFotos.size());
        return ocorrenciaRepository.buscarDetalhe(id, idFilial)
                .orElseThrow(() -> new RecursoNaoEncontrado("Ocorrência não encontrada."));
    }

    private List<OcorrenciaCte> resolverCtes(List<Long> numerosCte, long idAtividade) {
        List<OcorrenciaCte> resultado = new ArrayList<>();
        if (numerosCte == null || numerosCte.isEmpty()) {
            return resultado;
        }
        List<DocumentoOperacional> docs = documentoRepository.listarPorAtividade(idAtividade);
        for (Long numero : numerosCte) {
            if (numero == null) {
                continue;
            }
            Optional<DocumentoOperacional> doc = docs.stream()
                    .filter(d -> d.numeroCte() != null && d.numeroCte().longValue() == numero)
                    .findFirst();
            resultado.add(new OcorrenciaCte(null, numero,
                    doc.map(DocumentoOperacional::id).orElse(null),
                    doc.map(DocumentoOperacional::remetente).orElse(null),
                    doc.map(DocumentoOperacional::destinatario).orElse(null)));
        }
        return resultado;
    }

    private List<OcorrenciaFoto> salvarFotos(List<MultipartFile> arquivos, String categoria, int idFilial, int base) {
        List<OcorrenciaFoto> fotos = new ArrayList<>();
        if (arquivos == null) {
            return fotos;
        }
        int ordem = base;
        for (MultipartFile f : arquivos) {
            if (f == null || f.isEmpty()) {
                continue;
            }
            String path = fotoStorageService.salvar(f, idFilial);
            fotos.add(new OcorrenciaFoto(null, categoria, path, ordem++));
        }
        return fotos;
    }

    private List<OcorrenciaItem> mapearItens(List<RegistrarAvariaRequest.ItemAvariaRequest> itens) {
        List<OcorrenciaItem> resultado = new ArrayList<>();
        if (itens == null) {
            return resultado;
        }
        for (RegistrarAvariaRequest.ItemAvariaRequest it : itens) {
            if (it == null || it.nome() == null || it.nome().isBlank()) {
                continue;
            }
            resultado.add(new OcorrenciaItem(null, it.codigo(), it.nome().trim(), it.quantidade()));
        }
        return resultado;
    }

    // ---- Consulta ----------------------------------------------------------

    @Transactional(value = "torreTransactionManager", readOnly = true)
    public List<Ocorrencia> listar(int idFilial) {
        return ocorrenciaRepository.listar(idFilial, null, null, LIMITE);
    }

    @Transactional(value = "torreTransactionManager", readOnly = true)
    public List<Ocorrencia> listar(int idFilial, String status, String tipo) {
        return ocorrenciaRepository.listar(idFilial, status, tipo, LIMITE);
    }

    @Transactional(value = "torreTransactionManager", readOnly = true)
    public OcorrenciaDetalhe buscarDetalhe(long id, int idFilial) {
        return ocorrenciaRepository.buscarDetalhe(id, idFilial)
                .orElseThrow(() -> new RecursoNaoEncontrado("Ocorrência não encontrada."));
    }

    /** Caminho absoluto da foto principal (legado, uma foto por ocorrência). */
    @Transactional(value = "torreTransactionManager", readOnly = true)
    public Path caminhoFoto(long idOcorrencia, int idFilial) {
        Ocorrencia o = ocorrenciaRepository.buscar(idOcorrencia, idFilial)
                .orElseThrow(() -> new RecursoNaoEncontrado("Ocorrência não encontrada."));
        return fotoStorageService.resolver(o.fotoPath());
    }

    /** Caminho absoluto de uma foto específica da ocorrência (galeria). */
    @Transactional(value = "torreTransactionManager", readOnly = true)
    public Path caminhoFotoDe(long idOcorrencia, long idFoto, int idFilial) {
        ocorrenciaRepository.buscar(idOcorrencia, idFilial)
                .orElseThrow(() -> new RecursoNaoEncontrado("Ocorrência não encontrada."));
        OcorrenciaFoto foto = ocorrenciaRepository.fotos(idOcorrencia).stream()
                .filter(f -> f.id() != null && f.id() == idFoto)
                .findFirst()
                .orElseThrow(() -> new RecursoNaoEncontrado("Foto não encontrada."));
        return fotoStorageService.resolver(foto.fotoPath());
    }

    // ---- Tratamento (web) --------------------------------------------------

    /** Aplica o tratamento parcial e move o ciclo de status quando informado. */
    public OcorrenciaDetalhe tratar(long id, int idFilial, TratarOcorrenciaRequest req, UsuarioAutenticado usuario) {
        Ocorrencia atual = ocorrenciaRepository.buscar(id, idFilial)
                .orElseThrow(() -> new RecursoNaoEncontrado("Ocorrência não encontrada."));

        String novoStatus = atual.status();
        if (req.status() != null && !req.status().isBlank()) {
            StatusOcorrencia de = StatusOcorrencia.de(atual.status());
            StatusOcorrencia para = StatusOcorrencia.de(req.status());
            de.validarTransicao(para);
            novoStatus = para.name();
        }

        Ocorrencia atualizado = new Ocorrencia(atual.id(), atual.idFilial(), atual.tipo(), novoStatus,
                atual.idDocumento(), atual.idAtividade(), atual.placaVeiculo(), atual.motorista(),
                atual.idViagemLegado(), atual.descricao(),
                req.culpa() != null ? req.culpa() : atual.culpa(),
                req.quemCausou() != null ? req.quemCausou() : atual.quemCausou(),
                req.responsavelPagamento() != null ? req.responsavelPagamento() : atual.responsavelPagamento(),
                req.valorTotal() != null ? req.valorTotal() : atual.valorTotal(),
                atual.dataIdentificacao(), atual.duracaoSegundos(), atual.fotoPath(), atual.idUsuario(),
                atual.criadoEm(), usuario.id(), clock.instant(),
                req.resolucao() != null ? req.resolucao() : atual.resolucao());

        ocorrenciaRepository.atualizarTratamento(atualizado);
        auditoriaService.registrar(usuario, "TRATAR_OCORRENCIA", "ocorrencia", id,
                "status=" + novoStatus);
        return buscarDetalhe(id, idFilial);
    }

    private static String primeiroNaoVazio(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return (b != null && !b.isBlank()) ? b : null;
    }
}
