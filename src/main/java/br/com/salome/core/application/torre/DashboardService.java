package br.com.salome.core.application.torre;

import br.com.salome.core.domain.torre.ArmazemSnapshot;
import br.com.salome.core.domain.torre.DashboardSnapshot;
import br.com.salome.core.domain.torre.IndicadoresDia;
import br.com.salome.core.domain.torre.MapaArmazemSnapshot;
import br.com.salome.core.domain.torre.MapaCaminhao;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Dashboard de entrada da Torre (web): números e percentuais ao abrir, não tabela
 * crua. Apenas compõe serviços que já existem — indicadores do dia, ocupação do
 * armazém (estados próprios da Torre) e situação do pátio. Os caminhões "em
 * trânsito" e a ocupação por box reaproveitam caches já montados (mapa/armazém),
 * então o dashboard não adiciona consulta pesada nova ao legado.
 */
@Service
@ConditionalOnProperty(prefix = "salome.torre", name = "enabled", havingValue = "true")
public class DashboardService {

    private final IndicadoresRepository indicadoresRepository;
    private final ArmazemService armazemService;
    private final ViagemAguardandoService viagemAguardandoService;
    private final AtividadeService atividadeService;
    private final MapaArmazemService mapaService;
    private final Clock clock;

    public DashboardService(IndicadoresRepository indicadoresRepository,
                            ArmazemService armazemService,
                            ViagemAguardandoService viagemAguardandoService,
                            AtividadeService atividadeService,
                            MapaArmazemService mapaService,
                            Clock clock) {
        this.indicadoresRepository = indicadoresRepository;
        this.armazemService = armazemService;
        this.viagemAguardandoService = viagemAguardandoService;
        this.atividadeService = atividadeService;
        this.mapaService = mapaService;
        this.clock = clock;
    }

    public DashboardSnapshot snapshot(int idFilial) {
        Instant agora = clock.instant();
        Instant inicioDia = LocalDate.now(clock).atStartOfDay(clock.getZone()).toInstant();
        IndicadoresDia indicadores = indicadoresRepository.calcular(idFilial, inicioDia);

        ArmazemSnapshot armazem = armazemService.snapshot(idFilial);
        int[] e = ArmazemService.contarPorEstagio(armazem.documentos());
        int aguardandoSeparacao = e[0], emSeparacao = e[1], prontos = e[2], emCarregamento = e[3], avarias = e[4];
        int totalNoArmazem = aguardandoSeparacao + emSeparacao + prontos + emCarregamento + avarias;

        int caminhoesAguardando = viagemAguardandoService.listar(idFilial).size();
        int coletasEmTransito = viagemAguardandoService.listarColetas(idFilial).size();
        int descargasEmAndamento = (int) atividadeService.listarAbertas(idFilial).stream()
                .filter(a -> MapaArmazemService.TIPOS_DESCARGA.contains(a.tipo())).count();

        // Em trânsito (caminhões vindo de outras bases) sai do mapa, que é cacheado.
        MapaArmazemSnapshot mapa = mapaService.snapshot(idFilial);
        List<MapaCaminhao> emTransito = mapa.vindoDeOutrasBases();
        MapaCaminhao prox = proximaChegada(emTransito);

        return new DashboardSnapshot(
                idFilial, agora, indicadores,
                totalNoArmazem, aguardandoSeparacao, emSeparacao, prontos, emCarregamento, avarias,
                armazem.boxes(),
                emTransito.size(), caminhoesAguardando, coletasEmTransito, descargasEmAndamento,
                prox == null || prox.dataPrevisaoChegada() == null ? null : prox.dataPrevisaoChegada().toString(),
                prox == null ? null : prox.horaPrevisaoChegada());
    }

    /** Caminhão com a previsão de chegada mais cedo (ignora os sem previsão). */
    private MapaCaminhao proximaChegada(List<MapaCaminhao> caminhoes) {
        MapaCaminhao melhor = null;
        String melhorChave = null;
        for (MapaCaminhao c : caminhoes) {
            if (c.dataPrevisaoChegada() == null) {
                continue;
            }
            String chave = c.dataPrevisaoChegada() + " " + (c.horaPrevisaoChegada() == null ? "" : c.horaPrevisaoChegada());
            if (melhorChave == null || chave.compareTo(melhorChave) < 0) {
                melhorChave = chave;
                melhor = c;
            }
        }
        return melhor;
    }
}
