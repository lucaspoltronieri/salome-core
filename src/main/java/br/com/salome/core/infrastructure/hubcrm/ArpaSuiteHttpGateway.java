package br.com.salome.core.infrastructure.hubcrm;

import br.com.salome.core.application.hubcrm.ArpaSuiteGateway;
import br.com.salome.core.domain.hubcrm.HubCrmNormalization;
import br.com.salome.core.domain.hubcrm.LegacyCrmClient;
import br.com.salome.core.domain.hubcrm.LegacyQuote;
import br.com.salome.core.domain.hubcrm.LossReason;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
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
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
@ConditionalOnProperty(prefix = "salome.hub-crm", name = "enabled", havingValue = "true")
public class ArpaSuiteHttpGateway implements ArpaSuiteGateway {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    // Sem estes limites o RestClient herda o timeout infinito do JDK: em 09/2026 uma
    // resposta que nunca chegou pendurou a thread do polling por dias, sem erro no log.
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);
    // O canal de WhatsApp muda raramente; sem cache era uma chamada por cotação a cada polling.
    private static final Duration CHANNEL_CACHE_TTL = Duration.ofMinutes(5);
    private final RestClient client;
    private final HubCrmProperties properties;
    private final Map<LossReason, Long> lostReasonIds = new EnumMap<>(LossReason.class);
    private final AtomicReference<Long> whatsappChannelId = new AtomicReference<>();
    private final AtomicReference<Instant> whatsappChannelCheckedAt = new AtomicReference<>();

    public ArpaSuiteHttpGateway(HubCrmProperties properties) {
        this.properties = properties;
        String apiKey = properties.arpa().apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("SALOME_HUB_CRM_ARPA_API_KEY não configurada");
        }
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.client = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(stripTrailingSlash(properties.arpa().baseUrl()) + "/api")
                .defaultHeader("X-API-Key", apiKey)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
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
    public Optional<ArpaDeal> findDeal(long dealId) {
        if (dealId <= 0) return Optional.empty();
        try {
            JsonNode item = get("/deals/" + dealId);
            JsonNode data = item.path("data").isObject() ? item.path("data") : item;
            if (data.path("id").asLong(0) <= 0) return Optional.empty();
            return Optional.of(new ArpaDeal(data.path("id").asLong(), nullableLong(data, "organizationId"),
                    nullableLong(data, "peopleId"), nullableLong(data, "userId")));
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) return Optional.empty();
            throw exception;
        }
    }

    @Override
    public Optional<ArpaDeal> findLatestOpenDealByCnpj(String cnpj) {
        long fieldId = properties.arpa().cnpjCustomfieldId();
        JsonNode response = get("/deals?perPage=100&pipe=" + properties.arpa().pipeId()
                + "&status=open&customfields="
                + fieldId + ":" + cnpj);
        return dataEntries(response).stream()
                .filter(item -> item.path("status").asText().equalsIgnoreCase("open"))
                .max(Comparator.comparing(this::createdAtValue))
                .map(item -> new ArpaDeal(item.path("id").asLong(), nullableLong(item, "organizationId"),
                        nullableLong(item, "peopleId"), nullableLong(item, "userId")));
    }

    @Override
    public Optional<ArpaDeal> findDealByLegacyQuoteId(long legacyQuoteId) {
        long fieldId = properties.arpa().baseCotacaoCustomfieldId();
        JsonNode response = get("/deals?perPage=100&pipe=" + properties.arpa().pipeId()
                + "&customfields=" + fieldId + ":" + legacyQuoteId);
        return dataEntries(response).stream()
                .max(Comparator.comparing(this::createdAtValue))
                .map(this::dealFrom);
    }

    @Override
    public long createOrganization(String legalName) {
        Optional<Long> existing = findOrganizationId(legalName);
        if (existing.isPresent()) return existing.get();
        try {
            return extractId(post("/organizations", Map.of("name", legalName)));
        } catch (InvalidArpaResponseException exception) {
            return findOrganizationId(legalName).orElseThrow(() -> exception);
        }
    }

    @Override
    public long createPerson(String name, String phone, long organizationId) {
        Optional<Long> existing = findPersonId(name, organizationId);
        if (existing.isPresent()) return existing.get();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", name);
        String normalizedPhone = bestPhone(phone);
        if (!normalizedPhone.isBlank()) payload.put("phone", normalizedPhone);
        payload.put("organizationId", organizationId);
        try {
            return extractId(post("/peoples", payload));
        } catch (InvalidArpaResponseException exception) {
            return findPersonId(name, organizationId).orElseThrow(() -> exception);
        }
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
        try {
            return extractId(post("/deals", payload));
        } catch (InvalidArpaResponseException exception) {
            return findLatestOpenDealByCnpj(item.cnpj()).map(ArpaDeal::id).orElseThrow(() -> exception);
        }
    }

    @Override
    public long createPortfolioDealWithoutPerson(LegacyCrmClient item, long organizationId, long userId) {
        Map<String, Object> payload = baseDealWithoutPerson(
                HubCrmNormalization.shortName(item.legalName()), organizationId, userId,
                properties.arpa().carteiraStageId(), BigDecimal.ZERO);
        payload.put("details", "Cliente destinatário que não paga frete no legado");
        payload.put("customfields", clientFields(item));
        try {
            return extractId(post("/deals", payload));
        } catch (InvalidArpaResponseException exception) {
            return findLatestOpenDealByCnpj(item.cnpj()).map(ArpaDeal::id).orElseThrow(() -> exception);
        }
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
    public void updatePortfolioDealWithoutPerson(long dealId, LegacyCrmClient item,
            long organizationId, long userId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", HubCrmNormalization.shortName(item.legalName()));
        payload.put("userId", userId);
        payload.put("peopleId", null);
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
        try {
            return extractId(post("/deals", payload));
        } catch (InvalidArpaResponseException exception) {
            return findDealByLegacyQuoteId(quote.id()).map(ArpaDeal::id).orElseThrow(() -> exception);
        }
    }

    @Override
    public void updateDealFromQuote(long dealId, LegacyQuote quote, long userId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", HubCrmNormalization.shortName(quote.payerName()));
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
        try {
            return extractId(post("/annotations", Map.of(
                    "type", "observation", "dealId", dealId, "text", text)));
        } catch (InvalidArpaResponseException exception) {
            return 0L;
        }
    }

    @Override
    public void markWon(long dealId, LocalDateTime wonAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "won");
        put("/deals/" + dealId, payload);
    }

    @Override
    public void markLost(long dealId, LossReason reason, LocalDateTime lostAt) {
        if (lostReasonIds.isEmpty()) validateCatalog();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "lost");
        payload.put("lostReasonId", lostReasonIds.get(reason));
        put("/deals/" + dealId, payload);
    }

    @Override
    public boolean hasWhatsappChannel() {
        Instant checkedAt = whatsappChannelCheckedAt.get();
        if (checkedAt != null && checkedAt.isAfter(Instant.now().minus(CHANNEL_CACHE_TTL))) {
            return whatsappChannelId.get() != null;
        }
        JsonNode response = get("/channels?perPage=100&status=active");
        Optional<JsonNode> channel = dataEntries(response).stream().findFirst();
        whatsappChannelId.set(channel.map(item -> item.path("id").asLong()).orElse(null));
        whatsappChannelCheckedAt.set(Instant.now());
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

    private Map<String, Object> baseDealWithoutPerson(String title, long organizationId, long userId,
            long stageId, BigDecimal price) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", title);
        payload.put("price", amount(price));
        payload.put("userId", userId);
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
        String response = client.get().uri(path).retrieve().body(String.class);
        return parse(response);
    }

    private JsonNode post(String path, Object payload) {
        byte[] body = jsonBytes(payload);
        String response = client.post().uri(path).contentType(MediaType.APPLICATION_JSON)
                .contentLength(body.length).body(body).retrieve().body(String.class);
        return parse(response);
    }

    private JsonNode put(String path, Object payload) {
        byte[] body = jsonBytes(payload);
        String response = client.put().uri(path).contentType(MediaType.APPLICATION_JSON)
                .contentLength(body.length).body(body).retrieve().body(String.class);
        return parse(response);
    }

    private byte[] jsonBytes(Object payload) {
        try {
            return JSON.writeValueAsBytes(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("Não foi possível serializar a requisição do ArpaSuite", exception);
        }
    }

    private JsonNode parse(String response) {
        if (response == null || response.isBlank()) {
            throw new IllegalStateException("Resposta vazia do ArpaSuite");
        }
        try {
            return JSON.readTree(response);
        } catch (Exception exception) {
            throw new InvalidArpaResponseException(response, exception);
        }
    }

    private Optional<Long> findOrganizationId(String legalName) {
        JsonNode response = get("/organizations?perPage=100&name={name}", legalName);
        String expected = HubCrmNormalization.normalizedText(legalName);
        return dataEntries(response).stream()
                .filter(item -> HubCrmNormalization.normalizedText(item.path("name").asText()).equals(expected))
                .map(item -> item.path("id").asLong())
                .filter(id -> id > 0)
                .max(Long::compareTo);
    }

    private Optional<Long> findPersonId(String name, long organizationId) {
        JsonNode response = get("/peoples?perPage=100&name={name}&organizations={organizationId}",
                name, organizationId);
        String expected = HubCrmNormalization.normalizedText(name);
        return dataEntries(response).stream()
                .filter(item -> HubCrmNormalization.normalizedText(item.path("name").asText()).equals(expected))
                .filter(item -> item.path("organizationId").asLong(0) == organizationId)
                .map(item -> item.path("id").asLong())
                .filter(id -> id > 0)
                .max(Long::compareTo);
    }

    private JsonNode get(String template, Object... variables) {
        String response = client.get().uri(template, variables).retrieve().body(String.class);
        return parse(response);
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

    private String createdAtValue(JsonNode item) {
        String value = item.path("createdAt").asText("");
        return value.isBlank() ? item.path("created_at").asText("") : value;
    }

    private ArpaDeal dealFrom(JsonNode item) {
        return new ArpaDeal(item.path("id").asLong(), nullableLong(item, "organizationId"),
                nullableLong(item, "peopleId"), nullableLong(item, "userId"));
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

    String normalized(String value) {
        return HubCrmNormalization.normalizedText(value)
                .replace("CARGA EPECIAL DEDICADA", "CARGA ESPECIAL DEDICADA");
    }

    private String stripTrailingSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }

    private static final class InvalidArpaResponseException extends IllegalStateException {
        private InvalidArpaResponseException(String response, Throwable cause) {
            super("Resposta inválida do ArpaSuite", cause);
        }
    }
}
