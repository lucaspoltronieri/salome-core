package br.com.salome.core.application.torre;

import br.com.salome.core.domain.torre.AgregadoOperacional;
import br.com.salome.core.domain.torre.AtividadeResumo;
import br.com.salome.core.domain.torre.IndicadoresDia;
import br.com.salome.core.domain.torre.MapaArmazemSnapshot;
import br.com.salome.core.domain.torre.MapaCaminhao;
import br.com.salome.core.domain.torre.PainelSnapshot;
import br.com.salome.core.domain.torre.SaldoArmazem;
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

    private static final List<StatusDocumento> STATUS_ARMAZEM_ATUAL = List.of(
            StatusDocumento.NO_ARMAZEM, StatusDocumento.EM_SEPARACAO,
            StatusDocumento.SEPARADO_BOX, StatusDocumento.EM_CARREGAMENTO);

    private final ViagemAguardandoService viagemAguardandoService;
    private final AtividadeService atividadeService;
    private final DocumentoRepository documentoRepository;
    private final OcorrenciaService ocorrenciaService;
    private final IndicadoresRepository indicadoresRepository;
    private final MapaArmazemService mapaArmazemService;
    private final Clock clock;

    public PainelService(ViagemAguardandoService viagemAguardandoService,
                         AtividadeService atividadeService,
                         DocumentoRepository documentoRepository,
                         OcorrenciaService ocorrenciaService,
                         IndicadoresRepository indicadoresRepository,
                         MapaArmazemService mapaArmazemService,
                         Clock clock) {
        this.viagemAguardandoService = viagemAguardandoService;
        this.atividadeService = atividadeService;
        this.documentoRepository = documentoRepository;
        this.ocorrenciaService = ocorrenciaService;
        this.indicadoresRepository = indicadoresRepository;
        this.mapaArmazemService = mapaArmazemService;
        this.clock = clock;
    }

    public PainelSnapshot snapshot(int idFilial) {
        List<ViagemAguardando> viagens = viagemAguardandoService.listar(idFilial);
        List<AtividadeResumo> abertas = atividadeService.listarAbertas(idFilial);
        Instant inicioDia = LocalDate.now(clock).atStartOfDay(clock.getZone()).toInstant();
        IndicadoresDia indicadores = indicadoresRepository.calcular(idFilial, inicioDia);

        AgregadoOperacional aguardandoSeparacao =
                documentoRepository.agregarPorStatus(idFilial, StatusDocumento.NO_ARMAZEM);
        AgregadoOperacional descargasFinalizadas =
                indicadoresRepository.descargasFinalizadasHoje(idFilial, inicioDia);
        AgregadoOperacional armazemAtual = documentoRepository.agregarPorStatus(idFilial, STATUS_ARMAZEM_ATUAL);
        // Quebra por etapa em CT-es (COUNT(*), sobrecarga de lista) para o total bater com
        // a soma das partes — a sobrecarga de status único conta viagens, não documentos.
        SaldoArmazem saldoArmazem = new SaldoArmazem(
                armazemAtual,
                documentoRepository.agregarPorStatus(idFilial, List.of(StatusDocumento.NO_ARMAZEM)),
                documentoRepository.agregarPorStatus(idFilial, List.of(StatusDocumento.EM_SEPARACAO)),
                documentoRepository.agregarPorStatus(idFilial, List.of(StatusDocumento.SEPARADO_BOX)),
                documentoRepository.agregarPorStatus(idFilial, List.of(StatusDocumento.EM_CARREGAMENTO)));

        // O mapa é cacheado por filial (~25s), então uma chamada alimenta chegando + pra rua.
        MapaArmazemSnapshot mapa = mapaArmazemService.snapshot(idFilial);
        List<MapaCaminhao> emTransito = mapa.vindoDeOutrasBases();
        List<MapaCaminhao> emRotaEntrega = mapa.emRotaEntrega();

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
                documentoRepository.listarPorStatus(idFilial, List.of(StatusDocumento.SEPARADO_BOX)),
                ocorrenciaService.listar(idFilial),
                aguardandoSeparacao,
                descargasFinalizadas,
                armazemAtual,
                saldoArmazem,
                emTransito,
                emRotaEntrega);
    }

    private List<AtividadeResumo> porTipo(List<AtividadeResumo> abertas, TipoAtividade... tipos) {
        var set = java.util.EnumSet.noneOf(TipoAtividade.class);
        for (TipoAtividade t : tipos) {
            set.add(t);
        }
        return abertas.stream().filter(a -> set.contains(a.tipo())).toList();
    }
}
