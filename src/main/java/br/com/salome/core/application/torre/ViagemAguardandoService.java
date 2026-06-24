package br.com.salome.core.application.torre;

import br.com.salome.core.domain.torre.FilialTorre;
import br.com.salome.core.domain.torre.ViagemAguardando;
import br.com.salome.core.domain.torre.erro.RecursoNaoEncontrado;
import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Lista as viagens de transferência aguardando descarga numa filial: lê do legado
 * (a partir da data de corte da filial) e exclui as que já têm descarga na Torre.
 */
@Service
@ConditionalOnProperty(prefix = "salome.torre", name = "enabled", havingValue = "true")
public class ViagemAguardandoService {

    private static final int LIMITE = 500;

    private final ViagemLegadoRepository viagemLegadoRepository;
    private final FilialTorreRepository filialTorreRepository;
    private final AtividadeRepository atividadeRepository;

    public ViagemAguardandoService(ViagemLegadoRepository viagemLegadoRepository,
                                   FilialTorreRepository filialTorreRepository,
                                   AtividadeRepository atividadeRepository) {
        this.viagemLegadoRepository = viagemLegadoRepository;
        this.filialTorreRepository = filialTorreRepository;
        this.atividadeRepository = atividadeRepository;
    }

    public List<ViagemAguardando> listar(int idFilial) {
        FilialTorre filial = filialTorreRepository.buscar(idFilial)
                .filter(FilialTorre::ativa)
                .orElseThrow(() -> new RecursoNaoEncontrado("Filial não está ativa na Torre."));

        List<ViagemAguardando> viagens =
                viagemLegadoRepository.listarAguardandoDescarga(idFilial, filial.dataCorteViagem(), LIMITE);
        Set<Long> jaAbertas = atividadeRepository.idsViagensComDescarga(idFilial);

        return viagens.stream()
                .filter(v -> !jaAbertas.contains(v.idViagemTransferencia()))
                .toList();
    }
}
