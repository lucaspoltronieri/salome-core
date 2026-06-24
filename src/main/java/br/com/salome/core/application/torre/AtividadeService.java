package br.com.salome.core.application.torre;

import br.com.salome.core.domain.torre.AbrirAtividadeRequest;
import br.com.salome.core.domain.torre.Atividade;
import br.com.salome.core.domain.torre.AtividadeFinalizada;
import br.com.salome.core.domain.torre.AtividadeResumo;
import br.com.salome.core.domain.torre.EntrarAtividadeRequest;
import br.com.salome.core.domain.torre.Participante;
import br.com.salome.core.domain.torre.StatusAtividade;
import br.com.salome.core.domain.torre.auth.UsuarioAutenticado;
import br.com.salome.core.domain.torre.erro.RecursoNaoEncontrado;
import br.com.salome.core.domain.torre.erro.RegraViolada;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Núcleo de atividade: abrir, entrar, sair e finalizar — com a regra de
 * "uma participação ativa por usuário" e cronometragem server-side.
 */
@Service
@ConditionalOnProperty(prefix = "salome.torre", name = "enabled", havingValue = "true")
@Transactional("torreTransactionManager")
public class AtividadeService {

    private final AtividadeRepository atividadeRepository;
    private final ParticipanteRepository participanteRepository;
    private final AuditoriaService auditoriaService;
    private final Clock clock;

    public AtividadeService(AtividadeRepository atividadeRepository,
                            ParticipanteRepository participanteRepository,
                            AuditoriaService auditoriaService,
                            Clock clock) {
        this.atividadeRepository = atividadeRepository;
        this.participanteRepository = participanteRepository;
        this.auditoriaService = auditoriaService;
        this.clock = clock;
    }

    public AtividadeResumo abrir(AbrirAtividadeRequest request, UsuarioAutenticado usuario) {
        if (request.tipo() == br.com.salome.core.domain.torre.TipoAtividade.OUTRAS
                && (request.subtipo() == null || request.subtipo().isBlank())) {
            throw new br.com.salome.core.domain.torre.erro.RegraViolada(
                    "Atividade 'Outras' exige um subtipo (atendimento, geração de viagem, limpeza, etc.).");
        }
        Instant agora = clock.instant();
        Atividade nova = new Atividade(
                null, usuario.idFilial(), request.tipo(), request.subtipo(),
                StatusAtividade.ABERTA, request.idViagem(), request.placa(),
                usuario.id(), null, agora, null);
        long id = atividadeRepository.inserir(nova);

        // Quem abre já entra automaticamente e começa a contar tempo.
        entrarInterno(id, usuario.id(), request.funcao(), agora);
        return montarResumo(id, usuario.idFilial());
    }

    public AtividadeResumo entrar(long idAtividade, EntrarAtividadeRequest request, UsuarioAutenticado usuario) {
        Atividade atividade = exigirAtividade(idAtividade, usuario.idFilial());
        if (atividade.status() != StatusAtividade.ABERTA) {
            throw new RegraViolada("Atividade não está aberta.");
        }
        entrarInterno(idAtividade, usuario.id(), request == null ? null : request.funcao(), clock.instant());
        return montarResumo(idAtividade, usuario.idFilial());
    }

    public AtividadeResumo sair(long idAtividade, UsuarioAutenticado usuario) {
        exigirAtividade(idAtividade, usuario.idFilial());
        participanteRepository.encerrarNaAtividade(idAtividade, usuario.id(), clock.instant());
        return montarResumo(idAtividade, usuario.idFilial());
    }

    public AtividadeFinalizada finalizar(long idAtividade, UsuarioAutenticado usuario) {
        Atividade atividade = exigirAtividade(idAtividade, usuario.idFilial());
        if (atividade.status() == StatusAtividade.FINALIZADA) {
            throw new RegraViolada("Atividade já finalizada.");
        }
        Instant agora = clock.instant();
        participanteRepository.encerrarTodasDaAtividade(idAtividade, agora);
        atividadeRepository.finalizar(idAtividade, agora);

        List<Participante> participantes = participanteRepository.listarPorAtividade(idAtividade);
        long horasHomem = participantes.stream()
                .mapToLong(p -> Duration.between(p.entradaEm(), p.saidaEm() == null ? agora : p.saidaEm()).getSeconds())
                .sum();
        long duracao = Duration.between(atividade.iniciadaEm(), agora).getSeconds();
        auditoriaService.registrar(usuario, "FINALIZAR_ATIVIDADE", "atividade_armazem", idAtividade,
                "duração " + duracao + "s, " + participantes.size() + " participante(s)");
        return new AtividadeFinalizada(idAtividade, atividade.iniciadaEm(), agora, duracao, horasHomem, participantes.size());
    }

    public AtividadeResumo cancelar(long idAtividade, String motivo, UsuarioAutenticado usuario) {
        Atividade atividade = exigirAtividade(idAtividade, usuario.idFilial());
        if (atividade.status() != StatusAtividade.ABERTA) {
            throw new RegraViolada("Só é possível cancelar atividade aberta.");
        }
        Instant agora = clock.instant();
        participanteRepository.encerrarTodasDaAtividade(idAtividade, agora);
        atividadeRepository.cancelar(idAtividade, agora, motivo);
        auditoriaService.registrar(usuario, "CANCELAR_ATIVIDADE", "atividade_armazem", idAtividade, motivo);
        return montarResumo(idAtividade, usuario.idFilial());
    }

    @Transactional(value = "torreTransactionManager", readOnly = true)
    public List<AtividadeResumo> listarAbertas(int idFilial) {
        return atividadeRepository.listarAbertas(idFilial).stream()
                .map(a -> montarResumo(a.id(), idFilial))
                .toList();
    }

    private void entrarInterno(long idAtividade, long idUsuario, String funcao, Instant em) {
        // Trocar de atividade encerra a participação anterior automaticamente.
        participanteRepository.encerrarAtivasDoUsuario(idUsuario, em);
        participanteRepository.abrir(idAtividade, idUsuario, funcao, "APP", em);
    }

    private Atividade exigirAtividade(long idAtividade, int idFilial) {
        return atividadeRepository.buscar(idAtividade, idFilial)
                .orElseThrow(() -> new RecursoNaoEncontrado("Atividade não encontrada."));
    }

    private AtividadeResumo montarResumo(long idAtividade, int idFilial) {
        Atividade a = exigirAtividade(idAtividade, idFilial);
        List<Participante> participantes = participanteRepository.listarPorAtividade(idAtividade);
        return new AtividadeResumo(a.id(), a.idFilial(), a.tipo(), a.subtipo(), a.status(),
                a.idViagemLegado(), a.placaVeiculo(), a.iniciadaEm(), a.finalizadaEm(), participantes);
    }
}
