package br.com.salome.core.domain.financeiro;

import java.math.BigDecimal;
import java.util.List;

/**
 * No da arvore do plano de contas no DRE. Pode ser sintetico (rollup de filhos) ou analitico
 * (conta folha com lancamentos). Nos analiticos trazem a quebra por origem em {@link #origens}.
 */
public record DreContaNo(
        String classificacao,
        String descricao,
        boolean sintetica,
        int nivel,
        BigDecimal valor,
        BigDecimal percentualReceitaLiquida,
        int quantidade,
        List<DreOrigemResumo> origens,
        List<DreContaNo> filhos
) {
}
