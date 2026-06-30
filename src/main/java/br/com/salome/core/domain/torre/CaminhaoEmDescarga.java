package br.com.salome.core.domain.torre;

/**
 * Caminhão (viagem de transferência) que está sendo descarregado ou já foi
 * descarregado hoje — base para abrir a separação por caminhão. {@code descargaAberta}
 * indica que a descarga ainda está em andamento (dá pra separar o que já saiu do caminhão).
 */
public record CaminhaoEmDescarga(
        Long idViagem,
        String placa,
        boolean descargaAberta
) {
}
