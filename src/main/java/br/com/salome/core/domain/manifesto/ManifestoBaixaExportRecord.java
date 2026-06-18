package br.com.salome.core.domain.manifesto;

import br.com.salome.core.domain.legacy.LegacyOrigin;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ManifestoBaixaExportRecord(
        LocalDate dataBaixa,
        String horaBaixa,
        String responsavelBaixa,
        Integer idManifesto,
        Integer idViagem,
        Integer idConhecimento,
        Integer cte,
        String remetente,
        String destinatario,
        String cidadeDestinatario,
        String setorRegiao,
        String notasFiscais,
        BigDecimal peso,
        BigDecimal valorNf,
        BigDecimal frete,
        BigDecimal icms,
        String filialDestino,
        Integer armazemId,
        String situacaoCte,
        Integer mdfe,
        String observacao,
        LegacyOrigin origin
) {

    public String dedupeKey() {
        return idManifesto + ":" + idConhecimento;
    }

    public ManifestoBaixaCursor cursor() {
        return new ManifestoBaixaCursor(dataBaixa, horaBaixa, idManifesto);
    }
}
