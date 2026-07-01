package br.com.salome.core.application.torre;

import br.com.salome.core.domain.torre.AgregadoOperacional;
import br.com.salome.core.domain.torre.IndicadoresDia;
import java.time.Instant;

public interface IndicadoresRepository {

    /**
     * Calcula os indicadores do dia da filial. {@code inicioDia} é o instante
     * 00:00 local do dia corrente (limite inferior para os recortes "hoje").
     */
    IndicadoresDia calcular(int idFilial, Instant inicioDia);

    /**
     * Veículos/volume/peso das descargas (transferência ou coleta) finalizadas hoje —
     * agregado pro painel TV. Default vazio para fakes de teste.
     */
    default AgregadoOperacional descargasFinalizadasHoje(int idFilial, Instant inicioDia) {
        return AgregadoOperacional.vazio();
    }
}
