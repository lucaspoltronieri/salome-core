package br.com.salome.core.torre;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.salome.core.domain.torre.Intervalos;
import br.com.salome.core.domain.torre.Intervalos.Intervalo;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** União de intervalos = "tempo efetivo" de uma carga (desconta o buraco parado). */
class IntervalosTest {

    /** 08:00 na base; segundos a partir daí facilitam a leitura. */
    private static Instant t(long segundos) {
        return Instant.ofEpochSecond(1_000_000 + segundos);
    }

    private static Intervalo iv(long ini, long fim) {
        return new Intervalo(t(ini), t(fim));
    }

    @Test
    void vazio_ehZero() {
        assertThat(Intervalos.uniaoSegundos(List.of())).isZero();
    }

    @Test
    void umIntervalo() {
        assertThat(Intervalos.uniaoSegundos(List.of(iv(0, 100)))).isEqualTo(100);
    }

    @Test
    void disjuntos_somam() {
        assertThat(Intervalos.uniaoSegundos(List.of(iv(0, 100), iv(200, 300)))).isEqualTo(200);
    }

    @Test
    void sobrepostos_unem() {
        // uma pessoa 0-100, outra 50-200 → cobre 0-200 (não dobra a sobreposição).
        assertThat(Intervalos.uniaoSegundos(List.of(iv(0, 100), iv(50, 200)))).isEqualTo(200);
    }

    @Test
    void aninhados_valeOMaior() {
        assertThat(Intervalos.uniaoSegundos(List.of(iv(0, 300), iv(100, 200)))).isEqualTo(300);
    }

    @Test
    void adjacentes_encostam() {
        assertThat(Intervalos.uniaoSegundos(List.of(iv(0, 100), iv(100, 200)))).isEqualTo(200);
    }

    @Test
    void ordemDeEntradaNaoImporta() {
        assertThat(Intervalos.uniaoSegundos(List.of(iv(200, 300), iv(0, 100), iv(50, 120))))
                .isEqualTo(220); // 0-120 (120) + 200-300 (100)
    }

    @Test
    void cenarioLucas_gapNoMeio() {
        // Descarga 08:00, sai 09:00 (deu "Sair"), volta 13:00, conclui 14:00.
        // Janela = 6h; tempo efetivo (união) = 2h = 7200s.
        long h = 3600;
        long efetivo = Intervalos.uniaoSegundos(List.of(iv(0, h), iv(5 * h, 6 * h)));
        assertThat(efetivo).isEqualTo(2 * h);
    }

    @Test
    void ignoraInvalidos() {
        // fim nulo (não deu "Sair" e sem fallback), fim <= início e nulos são descartados.
        assertThat(Intervalos.uniaoSegundos(java.util.Arrays.asList(
                new Intervalo(t(0), null),
                new Intervalo(t(100), t(100)),
                new Intervalo(t(200), t(150)),
                null,
                iv(300, 400)))).isEqualTo(100);
    }
}
