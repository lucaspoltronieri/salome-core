package br.com.salome.core.domain.financeiro;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Card de horizonte do Fluxo de Caixa estrategico (a pagar ou a receber): amanha, esta semana,
 * este mes ou em atraso. Traz o total a vencer no intervalo e, para o drill, a arvore por plano
 * de contas em {@link #contas}.
 */
public record FinanceiroHorizonteCard(
        String codigo,
        String titulo,
        FinanceiroNatureza natureza,
        LocalDate de,
        LocalDate ate,
        BigDecimal valor,
        int quantidade,
        String tom,
        List<FinanceiroContaNo> contas
) {
}
