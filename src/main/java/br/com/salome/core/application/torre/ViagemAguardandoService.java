package br.com.salome.core.application.torre;

import br.com.salome.core.domain.torre.FilialTorre;
import br.com.salome.core.domain.torre.ViagemAguardando;
import br.com.salome.core.domain.torre.erro.RecursoNaoEncontrado;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Lista as viagens de transferência aguardando descarga numa filial: lê do legado
 * e exclui as que já têm descarga na Torre.
 *
 * <p>Transferências usam um <b>piso fixo</b> (virada de produção): baixados a partir
 * dessa data ficam no painel até a descarga ser registrada na Torre; baixas anteriores
 * (backlog do legado) são ignoradas — quem tira da lista é a descarga, não a data.
 * Coletas mostram <b>apenas o dia</b> (corte = hoje).
 */
@Service
@ConditionalOnProperty(prefix = "salome.torre", name = "enabled", havingValue = "true")
public class ViagemAguardandoService {

    private static final int LIMITE = 500;

    private final ViagemLegadoRepository viagemLegadoRepository;
    private final FilialTorreRepository filialTorreRepository;
    private final AtividadeRepository atividadeRepository;
    private final Clock clock;
    private final LocalDate dataInicioAguardando;

    public ViagemAguardandoService(ViagemLegadoRepository viagemLegadoRepository,
                                   FilialTorreRepository filialTorreRepository,
                                   AtividadeRepository atividadeRepository,
                                   Clock clock,
                                   @Value("${salome.torre.aguardando.data-inicio:2026-07-01}") String dataInicio) {
        this.viagemLegadoRepository = viagemLegadoRepository;
        this.filialTorreRepository = filialTorreRepository;
        this.atividadeRepository = atividadeRepository;
        this.clock = clock;
        this.dataInicioAguardando = LocalDate.parse(dataInicio);
    }

    public List<ViagemAguardando> listar(int idFilial) {
        filialTorreRepository.buscar(idFilial)
                .filter(FilialTorre::ativa)
                .orElseThrow(() -> new RecursoNaoEncontrado("Filial não está ativa na Torre."));

        // Piso fixo da virada de produção: ignora o backlog antigo do legado. O caminhão
        // segue no painel até a descarga ser registrada (idsViagensComDescarga), mesmo
        // que a baixa seja de dias anteriores.
        LocalDate dataCorte = dataInicioAguardando;
        List<ViagemAguardando> viagens =
                viagemLegadoRepository.listarAguardandoDescarga(idFilial, dataCorte, LIMITE);
        // Descarga é por viagem (caminhão): abrir uma cobre todos os manifestos.
        // Exclui pelo idViagem; sem idViagem, cai no id do manifesto.
        Set<Long> jaAbertas = atividadeRepository.idsViagensComDescarga(idFilial);

        return viagens.stream()
                .filter(v -> {
                    Long chave = v.idViagem() != null ? v.idViagem() : v.idViagemTransferencia();
                    return !jaAbertas.contains(chave);
                })
                .toList();
    }

    /** Viagens trazendo coletas da própria filial ('Em Viagem'), exceto as já em descarga. */
    public List<ViagemAguardando> listarColetas(int idFilial) {
        filialTorreRepository.buscar(idFilial)
                .filter(FilialTorre::ativa)
                .orElseThrow(() -> new RecursoNaoEncontrado("Filial não está ativa na Torre."));

        // Coletas: só as do dia. Cada dia mostra apenas as viagens de coleta de hoje.
        LocalDate dataCorte = LocalDate.now(clock);
        List<ViagemAguardando> viagens = viagemLegadoRepository.listarColetasAguardando(idFilial, dataCorte, LIMITE);
        Set<Long> jaAbertas = atividadeRepository.idsViagensComDescarga(idFilial);

        return viagens.stream()
                .filter(v -> {
                    Long chave = v.idViagem() != null ? v.idViagem() : v.idViagemTransferencia();
                    return !jaAbertas.contains(chave);
                })
                .toList();
    }
}
