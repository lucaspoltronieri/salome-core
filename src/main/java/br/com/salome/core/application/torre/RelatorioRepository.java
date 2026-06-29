package br.com.salome.core.application.torre;

import br.com.salome.core.domain.torre.RelatorioOperadores;
import java.time.LocalDate;

/**
 * Relatórios de leitura sobre o banco da Torre.
 */
public interface RelatorioRepository {

    /** O que cada operador fez no período [de, ate] na filial: atividades, tempo e CT-es. */
    RelatorioOperadores operadores(int idFilial, LocalDate de, LocalDate ate);
}
