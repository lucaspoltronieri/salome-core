package br.com.salome.core.application.financeiro;

import br.com.salome.core.domain.financeiro.PedagioLancamento;
import java.util.List;

/**
 * Fonte dos lancamentos de pedagio (vale-pedagio/tag) por placa, vindos do extrato JSON da operadora.
 * Esse custo nao esta no bolo do legado, entao o DRE por filial o trata como ajuste off-book.
 */
public interface PedagioRepository {

    /** Todos os lancamentos de pedagio carregados (passagem positiva, estorno negativo). */
    List<PedagioLancamento> listar();
}
