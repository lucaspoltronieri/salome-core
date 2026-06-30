package br.com.salome.core.domain.torre;

/**
 * Veículo (placa) de saída da filial, usado no carregamento de Entrega/Transferência.
 */
public record Veiculo(
        Long id,
        int idFilial,
        String placa,
        TipoVeiculo tipo,
        boolean ativo
) {
}
