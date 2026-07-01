package br.com.salome.core.domain.torre;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Caminhão (viagem de transferência) que está sendo descarregado ou já foi
 * descarregado hoje — base para abrir a separação por caminhão. {@code descargaAberta}
 * indica que a descarga ainda está em andamento (dá pra separar o que já saiu do caminhão).
 *
 * <p>Os campos além de {@code idViagem}/{@code placa}/{@code descargaAberta} vêm do legado
 * (enriquecidos por {@code MovimentacaoService.caminhoesParaSeparar} via
 * {@code ViagemLegadoRepository.buscarResumoPorViagens}) e podem vir nulos/zerados quando a
 * viagem não tem manifesto no legado (ex. atividade aberta sem idViagem).
 */
public record CaminhaoEmDescarga(
        Long idViagem,
        String placa,
        boolean descargaAberta,
        String origem,
        String motorista,
        int qtdCtes,
        BigDecimal volumes,
        BigDecimal peso,
        LocalDate dataBaixa,
        String horaBaixa,
        int qtdManifestos
) {
    /** Construtor de conveniência pra quando ainda não há resumo do legado (recém-listado). */
    public CaminhaoEmDescarga(Long idViagem, String placa, boolean descargaAberta) {
        this(idViagem, placa, descargaAberta, null, null, 0, BigDecimal.ZERO, BigDecimal.ZERO, null, null, 1);
    }
}
