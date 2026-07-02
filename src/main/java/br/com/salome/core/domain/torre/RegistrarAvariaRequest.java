package br.com.salome.core.domain.torre;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Registro de avaria vindo do app. A avaria está sempre amarrada a uma atividade
 * (viagem) — placa/motorista/viagem são herdados dela no servidor. As fotos
 * viajam como partes multipart separadas (não neste JSON).
 */
public record RegistrarAvariaRequest(
        String tipo,                        // default AVARIA se vazio
        @NotNull Long idAtividade,
        String placa,                       // contexto do app; a atividade prevalece se tiver
        String motorista,                   // contexto do app (viagem)
        List<Long> numerosCte,
        String descricao,
        String culpa,                       // CARREGAMENTO|VIAGEM|DESCARREGAMENTO
        String quemCausou,
        String responsavelPagamento,
        BigDecimal valorTotal,
        Instant dataIdentificacao,
        Integer duracaoSegundos,
        List<ItemAvariaRequest> itens
) {
    public record ItemAvariaRequest(
            String codigo,
            String nome,
            BigDecimal quantidade
    ) {
    }
}
