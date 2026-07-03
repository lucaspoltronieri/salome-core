package br.com.salome.core.application.torre;

import br.com.salome.core.domain.torre.RelatorioOperadores;
import br.com.salome.core.domain.torre.RelatorioProdutividadeCargas;
import java.time.LocalDate;

/**
 * Relatórios de leitura sobre o banco da Torre.
 */
public interface RelatorioRepository {

    /** O que cada operador fez no período [de, ate] na filial: atividades, tempo e CT-es. */
    RelatorioOperadores operadores(int idFilial, LocalDate de, LocalDate ate);

    /** Produtividade por carga (viagem descarregada) no período [de, ate]: tempos, pessoas e volumes. */
    RelatorioProdutividadeCargas produtividadeCargas(int idFilial, LocalDate de, LocalDate ate);
}
