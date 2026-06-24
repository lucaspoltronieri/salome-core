package br.com.salome.core.application.torre;

import br.com.salome.core.domain.torre.IndicadoresDia;
import java.time.Instant;

public interface IndicadoresRepository {

    /**
     * Calcula os indicadores do dia da filial. {@code inicioDia} é o instante
     * 00:00 local do dia corrente (limite inferior para os recortes "hoje").
     */
    IndicadoresDia calcular(int idFilial, Instant inicioDia);
}
