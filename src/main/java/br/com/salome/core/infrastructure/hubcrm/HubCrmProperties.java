package br.com.salome.core.infrastructure.hubcrm;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "salome.hub-crm")
public record HubCrmProperties(
        boolean enabled,
        boolean pollingEnabled,
        long pollingDelayMs,
        long initialCutoffClientId,
        int pilotSize,
        String publicBaseUrl,
        String mediaSigningKey,
        long mediaUrlTtlMinutes,
        Datasource datasource,
        Arpa arpa
) {
    public record Datasource(String url, String username, String password) {}

    public record Arpa(
            String baseUrl,
            String apiKey,
            long pipeId,
            long carteiraStageId,
            long propostaStageId,
            long fernandaUserId,
            long jaciUserId,
            long cnpjCustomfieldId,
            long cidadeCustomfieldId,
            long estadoCustomfieldId,
            long segmentoCustomfieldId,
            long baseCotacaoCustomfieldId,
            long rotaCustomfieldId,
            long tipoCargaCustomfieldId,
            long volumeCustomfieldId
    ) {}
}
