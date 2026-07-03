package br.com.salome.core.infrastructure.legacy.financeiro;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracao da previsao "A receber (faturado)" do Fluxo de Caixa.
 *
 * <p>A empresa opera dois tipos de carteira de fatura: <b>simples</b> (a empresa ainda vai receber
 * do cliente) e <b>descontada</b> (a fatura foi antecipada no banco — o dinheiro JA entrou). Faturas
 * descontadas nao devem entrar na previsao de "a receber", senao inflam o caixa projetado.
 *
 * <p>O nome exato da coluna de carteira na tabela {@code fatura} do legado e os valores que
 * significam "descontada" sao configuraveis, pois variam por instalacao. Enquanto
 * {@code carteira-coluna} estiver vazio OU {@code carteira-descontada-valores} estiver vazio, o
 * filtro fica DESLIGADO e a previsao continua contando todas as faturas em aberto (comportamento
 * historico). Exemplo de configuracao (application.yml):
 *
 * <pre>
 * salome:
 *   financeiro:
 *     fluxo-caixa:
 *       carteira-coluna: carteira
 *       carteira-descontada-valores:
 *         - Descontada
 * </pre>
 */
@ConfigurationProperties(prefix = "salome.financeiro.fluxo-caixa")
public record FinanceiroFluxoCaixaProperties(
        String carteiraColuna,
        List<String> carteiraDescontadaValores
) {

    public FinanceiroFluxoCaixaProperties {
        carteiraColuna = carteiraColuna == null ? "" : carteiraColuna.trim();
        carteiraDescontadaValores = carteiraDescontadaValores == null ? List.of()
                : carteiraDescontadaValores.stream()
                        .filter(valor -> valor != null && !valor.isBlank())
                        .map(valor -> valor.trim().toUpperCase())
                        .toList();
    }

    /**
     * Nome da coluna de carteira sanitizado para uso seguro em SQL (apenas letras, digitos e "_").
     * Retorna vazio quando nao configurado ou invalido — nesse caso o filtro fica desligado.
     */
    public String colunaSegura() {
        if (carteiraColuna.isEmpty() || !carteiraColuna.matches("[A-Za-z0-9_]+")) {
            return "";
        }
        return carteiraColuna;
    }

    /** {@code true} quando ha coluna valida e ao menos um valor de "descontada" configurado. */
    public boolean filtroAtivo() {
        return !colunaSegura().isEmpty() && !carteiraDescontadaValores.isEmpty();
    }
}
