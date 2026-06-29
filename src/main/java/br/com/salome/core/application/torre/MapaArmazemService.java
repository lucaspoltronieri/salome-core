package br.com.salome.core.application.torre;

import br.com.salome.core.application.manifesto.ManifestoBaixaRepository;
import br.com.salome.core.domain.manifesto.CteMapaSjpRecord;
import br.com.salome.core.domain.torre.AtividadeResumo;
import br.com.salome.core.domain.torre.MapaArmazemSnapshot;
import br.com.salome.core.domain.torre.MapaCaminhao;
import br.com.salome.core.domain.torre.MapaCte;
import br.com.salome.core.domain.torre.TipoAtividade;
import br.com.salome.core.domain.torre.ViagemAguardando;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Mapa do armazém da Torre operacional: compõe, ao vivo e por filial, o ciclo de
 * vida da carga — chegando (transferência), no armazém (aguardando/descarregando/
 * armazenado) e saindo para entrega. Reaproveita as consultas do legado que já
 * alimentam o export de manifesto e os estados próprios da Torre.
 *
 * <p>As consultas do legado são pesadas (subselects por linha); por isso o
 * resultado é cacheado por filial por alguns segundos, para vários operadores não
 * multiplicarem a carga no banco legado.
 */
@Service
@ConditionalOnProperty(prefix = "salome.torre", name = "enabled", havingValue = "true")
public class MapaArmazemService {

    private static final int LIMITE = 500;

    private final ManifestoBaixaRepository manifestoRepository;
    private final ViagemAguardandoService viagemAguardandoService;
    private final AtividadeService atividadeService;
    private final Clock clock;
    private final int diasCorte;
    private final long cacheSegundos;

    private final Map<Integer, Cache> cachePorFilial = new ConcurrentHashMap<>();

    public MapaArmazemService(ManifestoBaixaRepository manifestoRepository,
                              ViagemAguardandoService viagemAguardandoService,
                              AtividadeService atividadeService,
                              Clock clock,
                              @Value("${salome.torre.mapa.dias-corte:30}") int diasCorte,
                              @Value("${salome.torre.mapa.cache-segundos:25}") long cacheSegundos) {
        this.manifestoRepository = manifestoRepository;
        this.viagemAguardandoService = viagemAguardandoService;
        this.atividadeService = atividadeService;
        this.clock = clock;
        this.diasCorte = diasCorte;
        this.cacheSegundos = cacheSegundos;
    }

    public MapaArmazemSnapshot snapshot(int idFilial) {
        Instant agora = clock.instant();
        Cache atual = cachePorFilial.get(idFilial);
        if (atual != null && Duration.between(atual.builtAt(), agora).getSeconds() < cacheSegundos) {
            return atual.snapshot();
        }
        MapaArmazemSnapshot novo = montar(idFilial, agora);
        cachePorFilial.put(idFilial, new Cache(agora, novo));
        return novo;
    }

    private MapaArmazemSnapshot montar(int idFilial, Instant agora) {
        LocalDate dataCorte = LocalDate.now(clock).minusDays(diasCorte);

        List<MapaCaminhao> vindoDeOutrasBases =
                agruparPorViagem(manifestoRepository.listarViagensParaSjp(idFilial, dataCorte, LIMITE));
        List<ViagemAguardando> aguardando = viagemAguardandoService.listar(idFilial);
        List<AtividadeResumo> descarregando = atividadeService.listarAbertas(idFilial).stream()
                .filter(a -> TIPOS_DESCARGA.contains(a.tipo()))
                .toList();
        List<MapaCte> armazenado = manifestoRepository.listarArmazemSjp(idFilial, dataCorte, LIMITE)
                .stream().map(this::toMapaCte).toList();
        List<MapaCaminhao> emRotaEntrega =
                agruparPorViagem(manifestoRepository.listarEmRotaEntrega(idFilial, dataCorte, LIMITE));
        List<MapaCte> outrosArmazens = manifestoRepository.listarOutrosArmazens(idFilial, dataCorte, LIMITE)
                .stream().map(this::toMapaCte).toList();

        return new MapaArmazemSnapshot(idFilial, agora, dataCorte,
                vindoDeOutrasBases, aguardando, descarregando, armazenado, emRotaEntrega, outrosArmazens);
    }

    /** Agrupa CT-es por viagem (caminhão), somando volumes/peso. Sem idViagem, vira grupo próprio. */
    private List<MapaCaminhao> agruparPorViagem(List<CteMapaSjpRecord> ctes) {
        Map<String, List<CteMapaSjpRecord>> grupos = new LinkedHashMap<>();
        int avulso = 0;
        for (CteMapaSjpRecord r : ctes) {
            String chave = r.idViagem() != null
                    ? "v" + r.idViagem()
                    : "m" + r.idManifestoTransferencia() + ":" + (++avulso);
            grupos.computeIfAbsent(chave, k -> new ArrayList<>()).add(r);
        }
        List<MapaCaminhao> caminhoes = new ArrayList<>(grupos.size());
        for (List<CteMapaSjpRecord> grupo : grupos.values()) {
            CteMapaSjpRecord cab = grupo.get(0);
            BigDecimal volumes = BigDecimal.ZERO;
            BigDecimal peso = BigDecimal.ZERO;
            List<MapaCte> linhas = new ArrayList<>(grupo.size());
            for (CteMapaSjpRecord r : grupo) {
                volumes = volumes.add(nz(r.quantidadeVolumes()));
                peso = peso.add(nz(r.peso()));
                linhas.add(toMapaCte(r));
            }
            caminhoes.add(new MapaCaminhao(
                    cab.idViagem() == null ? null : cab.idViagem().longValue(),
                    cab.placaVeiculo(), cab.filialOrigem(), cab.motorista(),
                    cab.dataPrevisaoSaida(), cab.horaPrevisaoSaida(),
                    cab.dataPrevisaoChegada(), cab.horaPrevisaoChegada(),
                    grupo.size(), volumes, peso, linhas));
        }
        return caminhoes;
    }

    private MapaCte toMapaCte(CteMapaSjpRecord r) {
        return new MapaCte(
                r.cte(), r.dataEmissao(), r.notasFiscais(), r.remetente(), r.destinatario(),
                r.cidadeDestinatario(), r.setorRegiao(), nz(r.quantidadeVolumes()), nz(r.peso()),
                r.situacaoCte(), r.dataEntradaArmazem(), r.horaEntradaArmazem(),
                r.dataPrevistaEntrega(), r.armazemAtual(),
                r.idViagem() == null ? null : r.idViagem().longValue(), r.placaVeiculo());
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private record Cache(Instant builtAt, MapaArmazemSnapshot snapshot) {
    }

    /** Conjunto de tipos considerados descarga (para reuso/documentação). */
    static final Set<TipoAtividade> TIPOS_DESCARGA =
            Set.of(TipoAtividade.DESCARGA_TRANSFERENCIA, TipoAtividade.DESCARGA_COLETA);
}
