package br.com.salome.core.domain.manifesto;

public record ManifestoBaixaSituacaoAtual(
        Integer idConhecimento,
        String situacaoCte,
        Integer idViagemEntrega,
        String placaVeiculo,
        String motorista
) {
}
