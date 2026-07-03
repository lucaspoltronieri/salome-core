package br.com.salome.core.domain.torre;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * União de intervalos de tempo. Usado para o "tempo efetivo" de uma carga: quanto
 * tempo, em relógio de parede, houve <b>ao menos uma</b> pessoa presente na descarga —
 * unindo os intervalos {@code [entrada, saída]} de todos os participantes e descontando
 * o buraco em que ninguém estava. Difere da soma de horas-homem (que dobra o tempo
 * quando há gente em paralelo) e da janela total (início→fim, que inclui a parada).
 */
public final class Intervalos {

    private Intervalos() {
    }

    /** Intervalo fechado no início; {@code fim} nulo é tratado por quem chama (usa um fallback). */
    public record Intervalo(Instant inicio, Instant fim) {
    }

    /**
     * Segundos cobertos pela união dos intervalos (merge dos sobrepostos/adjacentes).
     * Intervalos com início nulo, fim nulo ou fim ≤ início são ignorados.
     */
    public static long uniaoSegundos(List<Intervalo> intervalos) {
        List<Intervalo> validos = new ArrayList<>();
        for (Intervalo i : intervalos) {
            if (i != null && i.inicio() != null && i.fim() != null && i.fim().isAfter(i.inicio())) {
                validos.add(i);
            }
        }
        if (validos.isEmpty()) {
            return 0L;
        }
        validos.sort(Comparator.comparing(Intervalo::inicio));

        long total = 0L;
        Instant abertoInicio = validos.get(0).inicio();
        Instant abertoFim = validos.get(0).fim();
        for (int k = 1; k < validos.size(); k++) {
            Intervalo atual = validos.get(k);
            if (!atual.inicio().isAfter(abertoFim)) {
                // sobrepõe ou encosta: estende o bloco aberto
                if (atual.fim().isAfter(abertoFim)) {
                    abertoFim = atual.fim();
                }
            } else {
                // gap: fecha o bloco e abre outro
                total += abertoFim.getEpochSecond() - abertoInicio.getEpochSecond();
                abertoInicio = atual.inicio();
                abertoFim = atual.fim();
            }
        }
        total += abertoFim.getEpochSecond() - abertoInicio.getEpochSecond();
        return total;
    }
}
