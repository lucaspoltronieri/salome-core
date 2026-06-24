package br.com.salome.core.application.torre;

import br.com.salome.core.domain.torre.AtividadeResumo;
import br.com.salome.core.domain.torre.IndicadoresDia;
import br.com.salome.core.domain.torre.PainelSnapshot;
import br.com.salome.core.domain.torre.StatusDocumento;
import br.com.salome.core.domain.torre.TipoAtividade;
import br.com.salome.core.domain.torre.ViagemAguardando;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Monta o snapshot do painel TV de uma filial (todos os blocos operacionais).
 */
@Service
@ConditionalOnProperty(prefix = "salome.torre", name = "enabled", havingValue = "true")
public class PainelService {

    private final ViagemAguardandoService viagemAguardandoService;
    private final AtividadeService atividadeService;
    private final DocumentoRepository documentoRepository;
    private final OcorrenciaService ocorrenciaService;
    private final IndicadoresRepository indicadoresRepository;
    private final Clock clock;

    public PainelService(ViagemAguardandoService viagemAguardandoService,
                         AtividadeService atividadeService,
                         DocumentoRepository documentoRepository,
                         OcorrenciaService ocorrenciaService,
                         IndicadoresRepository indicadoresRepository,
                         Clock clock) {
        this.viagemAguardandoService = viagemAguardandoService;
        this.atividadeService = atividadeService;
        this.documentoRepository = documentoRepository;
        this.ocorrenciaService = ocorrenciaService;
        this.indicadoresRepository = indicadoresRepository;
        this.clock = clock;
    }

    public PainelSnapshot snapshot(int idFilial) {
        List<ViagemAguardando> viagens = viagemAguardandoService.listar(idFilial);
        List<AtividadeResumo> abertas = atividadeService.listarAbertas(idFilial);
        Instant inicioDia = LocalDate.now(clock).atStartOfDay(clock.getZone()).toInstant();
        IndicadoresDia indicadores = indicadoresRepository.calcular(idFilial, inicioDia);

        return new PainelSnapshot(
                idFilial,
                clock.instant(),
                indicadores,
                viagens,
                porTipo(abertas, TipoAtividade.DESCARGA_TRANSFERENCIA, TipoAtividade.DESCARGA_COLETA),
                porTipo(abertas, TipoAtividade.SEPARACAO),
                porTipo(abertas, TipoAtividade.CARREGAMENTO),
                porTipo(abertas, TipoAtividade.OUTRAS),
                documentoRepository.listarPorStatus(idFilial, List.of(StatusDocumento.NO_ARMAZEM)),
                ocorrenciaService.listar(idFilial));
    }

    private List<AtividadeResumo> porTipo(List<AtividadeResumo> abertas, TipoAtividade... tipos) {
        var set = java.util.EnumSet.noneOf(TipoAtividade.class);
        for (TipoAtividade t : tipos) {
            set.add(t);
        }
        return abertas.stream().filter(a -> set.contains(a.tipo())).toList();
    }
}
