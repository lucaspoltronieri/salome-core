package br.com.salome.core.application.manifesto;

import br.com.salome.core.domain.legacy.LegacyOrigin;

public final class ManifestoRuleOrigins {

    public static final LegacyOrigin BAIXA_MANIFESTO = LegacyOrigin.of(
            "salome-legacy/view/BaixaManifesto.java",
            "btnSalvarActionPerformed",
            "ViagemTransferenciaData + ConhecimentoData",
            "viagemtransferencia, viagemtransferenciaconhecimento, conhecimento"
    );

    public static final LegacyOrigin SETOR_REGIAO = LegacyOrigin.of(
            "salome-legacy/view/BaixaManifesto.java",
            "propriedades[Setor/Regiao]",
            "query inline por CEP do destinatario/redespacho",
            "clienteregiao, clienteregiaosetor, clienteregiaosetorlocal, cliente, conhecimento"
    );

    public static final LegacyOrigin CTE_ARMAZEM = LegacyOrigin.of(
            "salome-legacy/view/CteArmazem.java",
            "atualizaTabela",
            "query por conhecimento.situacao e idClienteRegiaoSetorAtual",
            "conhecimento, clienteregiao, clienteregiaosetor, filial"
    );

    public static final LegacyOrigin VIAGEM_TRANSFERENCIA_SELECAO = LegacyOrigin.of(
            "salome-legacy/view/ViagemTransferenciaConhecimentoSelecao.java",
            "atualizaTabela",
            "filtro de filial destino pelo CEP da cidade do destinatario",
            "conhecimento, cliente destinatario, cidade, clienteregiao, clienteregiaosetor, clienteregiaosetorlocal, filial"
    );

    public static final LegacyOrigin VIAGEM_TRANSFERENCIA = LegacyOrigin.of(
            "salome-legacy/view/ViagemTransferencia.java",
            "btnSalvarActionPerformed",
            "manifesto de transferencia em viagem para filial destino",
            "viagemtransferencia, viagemtransferenciaconhecimento, viagem, veiculo, motorista, fornecedor"
    );

    public static final LegacyOrigin VIAGEM_ENTREGA = LegacyOrigin.of(
            "salome-legacy/view/ViagemEntrega.java",
            "btnSalvarActionPerformed",
            "viagem de entrega do CT-e a partir da ultima viagementrega",
            "viagementrega, viagem, veiculo, motorista, fornecedor"
    );

    private ManifestoRuleOrigins() {
    }
}
