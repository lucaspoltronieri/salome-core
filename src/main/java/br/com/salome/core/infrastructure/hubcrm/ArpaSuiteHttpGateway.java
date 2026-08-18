package br.com.salome.core.infrastructure.hubcrm;

import br.com.salome.core.application.hubcrm.ArpaSuiteGateway;
import br.com.salome.core.domain.hubcrm.HubCrmNormalization;
import br.com.salome.core.domain.hubcrm.LegacyCrmClient;
import br.com.salome.core.domain.hubcrm.LegacyQuote;
import br.com.salome.core.domain.hubcrm.LossReason;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

@Component
@ConditionalOnProperty(prefix = "salome.hub-crm", name = "enabled", havingValue = "true")
public class ArpaSuiteHttpGateway implements ArpaSuiteGateway {
    private static final ZoneOffset SAO_PAULO_OFFSET = ZoneOffset.ofHours(-3);
    private final RestClient client;
    private final HubCrmProperties properties;
    private final Map<LossReason, Long> lostReasonIds = new EnumMap<>(LossReason.class);
    private final AtomicReference<Long> whatsappChannelId = new AtomicReference<>();

    public ArpaSuiteHttpGateway(HubCrmProperties properties) {
        this.properties = properties;
        String apiKey = properties.arpa().apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("SALOME_HUB_CRM_ARPA_API_KEY não configurada");
        }
        this.client = RestClient.builder()
                .baseUrl(stripTrailingSlash(properties.arpa().baseUrl()) + "/api")
                .defaultHeader("X-API-Key", apiKey)
                .build();
    }

    @Override
    public synchronized void validateCatalog() {
        JsonNode response = get("/lostreasons?perPage=100&status=active");
        List<JsonNode> entries = dataEntries(response);
        lostReasonIds.clear();
        for (LossReason reason : LossReason.values()) {
            entries.stream()
                    .filter(item -> normalized(item.path("reason").asText()).equals(normalized(reason.arpaName())))
                    .findFirst()
                    .ifPresent(item -> lostReasonIds.put(reason, item.path("id").asLong()));
        }
        if (lostReasonIds.size() != LossReason.values().length) {
            List<String> missing = java.util.Arrays.stream(LossReason.values())
                    .filter(reason -> !lostReasonIds.containsKey(reason))
                    .map(LossReason::arpaName)
                    .toList();
            throw new IllegalStateException("Motivos de perda ausentes no ArpaSuite: " + missing);
        }
    }

    @Override
    public Optional<ArpaDeal> findLatestOpenDealByCnpj(String cnpj) {
        long fieldId = properties.arpa().cnpjCustomfieldId();
        JsonNode response = get("/deals?perPage=100&pipe=" + properties.arpa().pipeId()
                + "&status=open&orderColumn=created_at&orderDirection=desc&customfields="
                + fieldId + "%3A" + cnpj);
        return dataEntries(response).stream()
                .filter(item -> item.path("status").asText().equalsIgnoreCase("open"))
                .max(Comparator.comparing(item -> item.path("createdAt").asText("")))
                .map(item -> new ArpaDeal(item.path("id").asLong(), nullableLong(item, "organizationId"),
                        nullableLong(item, "peopleId"), nullableLong(item, "userId")));
    }

    @Override
    public long createOrganization(String legalName) {
        return extractId(post("/organizations", Map.of("name", legalName)));
    }

    @Override
    public long createPerson(String name, String phone, long organizationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", name);
        String normalizedPhone = bestPhone(phone);
        if (!normalizedPhone.isBlank()) payload.put("phone", normalizedPhone);
        payload.put("organizationId", organizationId);
        return extractId(post("/peoples", payload));
    }

    @Override
    public void updateOrganization(long organizationId, String legalName) {
        put("/organizations/" + organizationId, Map.of("name", legalName));
    }

    @Override
    public void updatePerson(long peopleId, String name, String phone, long organizationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", name);
        String normalizedPhone = bestPhone(phone);
        if (!normalizedPhone.isBlank()) payload.put("phone", normalizedPhone);
        payload.put("organizationId", organizationId);
        put("/peoples/" + peopleId, payload);
    }

    @Override
    public void linkDeal(long dealId, long organizationId, long peopleId, long userId) {
        put("/deals/" + dealId, Map.of(
                "organizationId", organizationId, "peopleId", peopleId, "userId", userId));
    }

    @Override
    public long createPortfolioDeal(LegacyCrmClient item, long organizationId, long peopleId, long userId) {
        Map<String, Object> payload = baseDeal(
                HubCrmNormalization.shortName(item.legalName()), organizationId, peopleId, userId,
                properties.arpa().carteiraStageId(), BigDecimal.ZERO);
        payload.put("details", "Cliente destinatário que não paga frete no legado");
        payload.put("customfields", clientFields(item));
        return extractId(post("/deals", payload));
    }

    @Override
    public void updatePortfolioDeal(long dealId, LegacyCrmClient item, long organizationId,
            long peopleId, long userId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", HubCrmNormalization.shortName(item.legalName()));
        payload.put("userId", userId);
        payload.put("peopleId", peopleId);
        payload.put("organizationId", organizationId);
        payload.put("customfields", clientFields(item));
        put("/deals/" + dealId, payload);
    }

    @Override
    public long createQuoteDeal(LegacyQuote quote, long organizationId, long peopleId, long userId) {
        Map<String, Object> payload = baseDeal(HubCrmNormalization.shortName(quote.payerName()),
                organizationId, peopleId, userId, properties.arpa().propostaStageId(), quote.totalFreight());
        payload.put("details", "Cotação do legado #" + quote.id());
        payload.put("customfields", quoteFields(quote));
        return extractId(post("/deals", payload));
    }

    @Override
    public void updateDealFromQuote(long dealId, LegacyQuote quote, long userId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("pipeId", properties.arpa().pipeId());
        payload.put("stageId", properties.arpa().propostaStageId());
        payload.put("price", amount(quote.totalFreight()));
        payload.put("details", "Cotação do legado #" + quote.id());
        payload.put("customfields", quoteFields(quote));
        put("/deals/" + dealId, payload);
    }

    @Override
    public long addAnnotation(long dealId, String text) {
        return extractId(post("/annotations", Map.of("dealId", dealId, "text", text)));
    }

    @Override
    public void markWon(long dealId, LocalDateTime wonAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "won");
        if (wonAt != null) payload.put("winDate", wonAt.atOffset(SAO_PAULO_OFFSET).toString());
        put("/deals/" + dealId, payload);
    }

    @Override
    public void markLost(long dealId, LossReason reason, LocalDateTime lostAt) {
        if (lostReasonIds.isEmpty()) validateCatalog();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "lost");
        payload.put("lostReasonId", lostReasonIds.get(reason));
        if (lostAt != null) payload.put("lostDate", lostAt.atOffset(SAO_PAULO_OFFSET).toString());
        put("/deals/" + dealId, payload);
    }

    @Override
    public boolean hasWhatsappChannel() {
        JsonNode response = get("/channels?perPage=100&status=active");
        Optional<JsonNode> channel = dataEntries(response).stream().findFirst();
        whatsappChannelId.set(channel.map(item -> item.path("id").asLong()).orElse(null));
        return channel.isPresent();
    }

    @Override
    public void sendQuoteDocument(long peopleId, long dealId, String mediaUrl, String caption) {
        Long channelId = whatsappChannelId.get();
        if (channelId == null && !hasWhatsappChannel()) {
            throw new IllegalStateException("Nenhum canal WhatsApp ativo no ArpaSuite");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("channelId", whatsappChannelId.get());
        payload.put("peopleId", peopleId);
        payload.put("dealId", dealId);
        payload.put("type", "document");
        payload.put("mediaUrl", mediaUrl);
        payload.put("caption", caption);
        payload.put("sentBy", "bot");
        post("/messages/send", payload);
    }

    private Map<String, Object> baseDeal(String title, long organizationId, long peopleId,
            long userId, long stageId, BigDecimal price) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", title);
        payload.put("price", amount(price));
        payload.put("userId", userId);
        payload.put("peopleId", peopleId);
        payload.put("organizationId", organizationId);
        payload.put("pipeId", properties.arpa().pipeId());
        payload.put("stageId", stageId);
        return payload;
    }

    private List<Map<String, Object>> clientFields(LegacyCrmClient item) {
        List<Map<String, Object>> fields = new ArrayList<>();
        addField(fields, properties.arpa().cnpjCustomfieldId(), item.cnpj());
        addField(fields, properties.arpa().cidadeCustomfieldId(), item.city());
        addField(fields, properties.arpa().estadoCustomfieldId(), item.state());
        addField(fields, properties.arpa().segmentoCustomfieldId(), item.segment());
        return fields;
    }

    private List<Map<String, Object>> quoteFields(LegacyQuote quote) {
        List<Map<String, Object>> fields = new ArrayList<>();
        addField(fields, properties.arpa().cnpjCustomfieldId(), quote.payerCnpj());
        addField(fields, properties.arpa().baseCotacaoCustomfieldId(), String.valueOf(quote.id()));
        addField(fields, properties.arpa().rotaCustomfieldId(), quote.senderCity() + " → " + quote.recipientCity());
        addField(fields, properties.arpa().tipoCargaCustomfieldId(), quote.cargoType());
        addField(fields, properties.arpa().volumeCustomfieldId(), quote.volumes() + " volume(s)");
        return fields;
    }

    private void addField(List<Map<String, Object>> fields, long id, String value) {
        if (id > 0 && value != null && !value.isBlank()) {
            fields.add(Map.of("customfieldId", id, "value", value));
        }
    }

    private JsonNode get(String path) {
        return client.get().uri(path).retrieve().body(JsonNode.class);
    }

    private JsonNode post(String path, Object payload) {
        return client.post().uri(path).contentType(MediaType.APPLICATION_JSON)
                .body(payload).retrieve().body(JsonNode.class);
    }

    private JsonNode put(String path, Object payload) {
        return client.put().uri(path).contentType(MediaType.APPLICATION_JSON)
                .body(payload).retrieve().body(JsonNode.class);
    }

    private long extractId(JsonNode response) {
        if (response == null) throw new IllegalStateException("Resposta vazia do ArpaSuite");
        JsonNode data = response.path("data");
        long id = data.path("id").asLong(response.path("id").asLong(0));
        if (id <= 0) throw new IllegalStateException("ArpaSuite não retornou o ID criado");
        return id;
    }

    private List<JsonNode> dataEntries(JsonNode response) {
        if (response == null) return List.of();
        JsonNode data = response.path("data");
        if (!data.isArray() && response.isArray()) data = response;
        if (!data.isArray()) return List.of();
        List<JsonNode> result = new ArrayList<>();
        data.forEach(result::add);
        return result;
    }

    private Long nullableLong(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() || !value.canConvertToLong() ? null : value.asLong();
    }

    private double amount(BigDecimal value) {
        return value == null ? 0 : value.doubleValue();
    }

    private String bestPhone(String value) {
        if (value == null) return "";
        for (String part : value.split("[,;|/]")) {
            String digits = HubCrmNormalization.digits(part);
            if (digits.startsWith("55") && digits.length() > 11) digits = digits.substring(2);
            if (digits.length() == 10 || digits.length() == 11) return digits;
        }
        return "";
    }

    private String normalized(String value) {
        return HubCrmNormalization.normalizedText(value);
    }

    private String stripTrailingSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }
}
