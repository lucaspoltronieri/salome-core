package br.com.salome.core.application.torre;

import br.com.salome.core.domain.torre.FilialTorre;
import br.com.salome.core.domain.torre.ViagemAguardando;
import br.com.salome.core.domain.torre.erro.RecursoNaoEncontrado;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Lista as viagens de transferência aguardando descarga numa filial: lê do legado
 * (somente as baixadas de hoje em diante) e exclui as que já têm descarga na Torre.
 */
@Service
@ConditionalOnProperty(prefix = "salome.torre", name = "enabled", havingValue = "true")
public class ViagemAguardandoService {

    private static final int LIMITE = 500;

    private final ViagemLegadoRepository viagemLegadoRepository;
    private final FilialTorreRepository filialTorreRepository;
    private final AtividadeRepository atividadeRepository;
    private final Clock clock;

    public ViagemAguardandoService(ViagemLegadoRepository viagemLegadoRepository,
                                   FilialTorreRepository filialTorreRepository,
                                   AtividadeRepository atividadeRepository,
                                   Clock clock) {
        this.viagemLegadoRepository = viagemLegadoRepository;
        this.filialTorreRepository = filialTorreRepository;
        this.atividadeRepository = atividadeRepository;
        this.clock = clock;
    }

    public List<ViagemAguardando> listar(int idFilial) {
        filialTorreRepository.buscar(idFilial)
                .filter(FilialTorre::ativa)
                .orElseThrow(() -> new RecursoNaoEncontrado("Filial não está ativa na Torre."));

        // Painel carrega só o que foi baixado de hoje em diante.
        LocalDate dataCorte = LocalDate.now(clock);
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
}
