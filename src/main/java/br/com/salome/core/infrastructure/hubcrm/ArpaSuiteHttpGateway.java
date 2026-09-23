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
    private static final Duration CONVERSATION_CACHE_TTL = Duration.ofSeconds(60);
    private final AtomicReference<List<JsonNode>> openConversations = new AtomicReference<>(List.of());
    private final AtomicReference<Instant> openConversationsAt = new AtomicReference<>();

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
            return Optional.of(dealFrom(data));
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) return Optional.empty();
            throw exception;
        }
    }

    @Override
    public Optional<Long> findDealStage(long dealId) {
        if (dealId <= 0) return Optional.empty();
        try {
            JsonNode item = get("/deals/" + dealId);
            JsonNode data = item.path("data").isObject() ? item.path("data") : item;
            if (data.path("id").asLong(0) <= 0) return Optional.empty();
            return Optional.ofNullable(nullableLong(data, "stageId"));
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
                HubCrmNormalization.businessName(item.legalName()), organizationId, peopleId, userId,
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
                HubCrmNormalization.businessName(item.legalName()), organizationId, userId,
                properties.arpa().carteiraStageId(), BigDecimal.ZERO);
        // A API exige `peopleName` na criação do card (422 "peopleName é obrigatório").
        // Sem contato pessoal no legado, entra a própria razão social — é o que permite
        // o cadastro qualificado existir na Carteira mesmo sem contato.
        payload.put("peopleName", HubCrmNormalization.shortName(item.legalName()));
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
        payload.put("title", HubCrmNormalization.businessName(item.legalName()));
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
        payload.put("title", HubCrmNormalization.businessName(item.legalName()));
        payload.put("userId", userId);
        payload.put("peopleId", null);
        payload.put("organizationId", organizationId);
        payload.put("customfields", clientFields(item));
        put("/deals/" + dealId, payload);
    }

    @Override
    public long createQuoteDeal(LegacyQuote quote, long organizationId, long peopleId, long userId) {
        Map<String, Object> payload = baseDeal(HubCrmNormalization.businessName(quote.payerName()),
                organizationId, peopleId, userId, properties.arpa().propostaStageId(), quote.totalFreight());
        payload.put("details", "Cotação do legado #" + quote.id());
        payload.put("customfields", quoteFields(quote));
        putClosingDate(payload, quote);
        try {
            return extractId(post("/deals", payload));
        } catch (InvalidArpaResponseException exception) {
            return findDealByLegacyQuoteId(quote.id()).map(ArpaDeal::id).orElseThrow(() -> exception);
        }
    }

    @Override
    public void updateDealFromQuote(long dealId, LegacyQuote quote, long userId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", HubCrmNormalization.businessName(quote.payerName()));
        payload.put("userId", userId);
        payload.put("pipeId", properties.arpa().pipeId());
        payload.put("stageId", properties.arpa().propostaStageId());
        payload.put("price", amount(quote.totalFreight()));
        payload.put("details", "Cotação do legado #" + quote.id());
        payload.put("customfields", quoteFields(quote));
        putClosingDate(payload, quote);
        put("/deals/" + dealId, payload);
    }

    private void putClosingDate(Map<String, Object> payload, LegacyQuote quote) {
        String date = closingDate(quote.payerProfile().closingForecast());
        if (date != null) payload.put("expectClosingDate", date);
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
        // O card de cotação vive em Proposta Enviada: criado, ganho ou perdido. Se alguém
        // arrastou o card para outro estágio, o ganho/perda o traz de volta.
        payload.put("stageId", properties.arpa().propostaStageId());
        put("/deals/" + dealId, payload);
    }

    @Override
    public void markLost(long dealId, LossReason reason, LocalDateTime lostAt) {
        if (lostReasonIds.isEmpty()) validateCatalog();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "lost");
        payload.put("lostReasonId", lostReasonIds.get(reason));
        payload.put("stageId", properties.arpa().propostaStageId());
        put("/deals/" + dealId, payload);
    }

    @Override
    public void markLostWithReasonId(long dealId, long lostReasonId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "lost");
        payload.put("lostReasonId", lostReasonId);
        payload.put("stageId", properties.arpa().propostaStageId());
        put("/deals/" + dealId, payload);
    }

    @Override
    public Optional<String> findDealStatus(long dealId) {
        if (dealId <= 0) return Optional.empty();
        try {
            JsonNode item = get("/deals/" + dealId);
            JsonNode data = item.path("data").isObject() ? item.path("data") : item;
            if (data.path("id").asLong(0) <= 0) return Optional.empty();
            String status = data.path("status").asText("");
            return status.isBlank() ? Optional.empty() : Optional.of(status);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) return Optional.empty();
            throw exception;
        }
    }

    @Override
    public Optional<LossReason> findDealLostReason(long dealId) {
        if (dealId <= 0) return Optional.empty();
        if (lostReasonIds.isEmpty()) validateCatalog();
        try {
            JsonNode item = get("/deals/" + dealId);
            JsonNode data = item.path("data").isObject() ? item.path("data") : item;
            Long reasonId = nullableLong(data, "lostReasonId");
            if (reasonId == null) return Optional.empty();
            return lostReasonIds.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(reasonId))
                    .map(Map.Entry::getKey)
                    .findFirst();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) return Optional.empty();
            throw exception;
        }
    }

    @Override
    public boolean hasDealActivitySince(long dealId, java.time.LocalDate since) {
        JsonNode response = get("/activities?deal=" + dealId + "&perPage=100");
        return dataEntries(response).stream().anyMatch(activity -> {
            if ("cancelled".equalsIgnoreCase(activity.path("status").asText(""))) return false;
            String created = activity.path("createdAt").asText("");
            // createdAt vem em ISO (2026-09-22T12:09:20.000+00:00); sem data, conta como atividade.
            return created.length() < 10 || !java.time.LocalDate.parse(created.substring(0, 10)).isBefore(since);
        });
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
    public Optional<OpenConversation> findOpenConversation(long peopleId, String phone, Long organizationId,
            String... names) {
        String quotePhone = bestPhone(phone);
        java.util.Set<String> quoteNames = new java.util.HashSet<>();
        for (String name : names) {
            String normalized = HubCrmNormalization.normalizedText(name);
            if (!normalized.isBlank()) quoteNames.add(normalized);
        }
        OpenConversation best = null;
        int bestRank = Integer.MAX_VALUE;
        Instant bestInbound = Instant.MIN;
        for (JsonNode item : openConversations()) {
            JsonNode people = item.path("people");
            int rank;
            String match;
            if (item.path("peopleId").asLong() == peopleId) {
                rank = 0;
                match = "mesma pessoa";
            } else if (samePhone(quotePhone, bestPhone(people.path("phone").asText()))) {
                rank = 1;
                match = "mesmo telefone";
            } else if (organizationId != null && people.path("organizationId").asLong(0) == organizationId) {
                rank = 2;
                match = "mesma organização";
            } else if (quoteNames.contains(HubCrmNormalization.normalizedText(people.path("name").asText()))) {
                rank = 3;
                match = "mesmo nome";
            } else {
                continue;
            }
            Instant inbound = java.time.OffsetDateTime.parse(item.path("lastInboundAt").asText()).toInstant();
            if (rank < bestRank || (rank == bestRank && inbound.isAfter(bestInbound))) {
                best = new OpenConversation(item.path("id").asLong(), match);
                bestRank = rank;
                bestInbound = inbound;
            }
        }
        return Optional.ofNullable(best);
    }

    @Override
    public void sendDocumentToConversation(long conversationId, long dealId, String mediaUrl, String caption) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("conversationId", conversationId);
        payload.put("dealId", dealId);
        payload.put("type", "document");
        payload.put("mediaUrl", mediaUrl);
        payload.put("caption", caption);
        payload.put("sentBy", "bot");
        JsonNode response = post("/messages/send", payload);
        JsonNode data = response.path("data").isObject() ? response.path("data") : response;
        if ("failed".equals(data.path("status").asText())) {
            String result = response.toString();
            throw new IllegalStateException("Meta recusou a mensagem: "
                    + result.substring(0, Math.min(500, result.length())));
        }
    }

    // Conversas do canal com mensagem do contato nas últimas 24h (margem de 10 minutos para não cair
    // no 422 window_closed no fim da janela). Cache curto: uma busca serve a todas as cotações do ciclo.
    private synchronized List<JsonNode> openConversations() {
        Instant cachedAt = openConversationsAt.get();
        if (cachedAt != null && cachedAt.isAfter(Instant.now().minus(CONVERSATION_CACHE_TTL))) {
            return openConversations.get();
        }
        List<JsonNode> open = new ArrayList<>();
        Long channelId = whatsappChannelId.get();
        if (channelId != null || hasWhatsappChannel()) {
            Instant limit = Instant.now().minus(Duration.ofHours(24)).plus(Duration.ofMinutes(10));
            for (int page = 1; page <= 20; page++) {
                List<JsonNode> entries = dataEntries(get("/conversations?channel=" + whatsappChannelId.get()
                        + "&status=all&perPage=100&page=" + page));
                for (JsonNode item : entries) {
                    String inbound = item.path("lastInboundAt").asText();
                    if (!inbound.isBlank() && java.time.OffsetDateTime.parse(inbound).toInstant().isAfter(limit)) {
                        open.add(item);
                    }
                }
                if (entries.size() < 100) break;
            }
        }
        openConversations.set(open);
        openConversationsAt.set(Instant.now());
        return open;
    }

    // Mesmo número com ou sem o nono dígito do celular: DDD e os 8 últimos dígitos iguais.
    static boolean samePhone(String a, String b) {
        if (a.isBlank() || b.isBlank()) return false;
        if (a.equals(b)) return true;
        return a.length() >= 10 && b.length() >= 10 && a.substring(0, 2).equals(b.substring(0, 2))
                && a.substring(a.length() - 8).equals(b.substring(b.length() - 8));
    }


    // O título do card é a razão social completa, sem a inscrição numérica do CNPJ/CPF
    // que alguns cadastros trazem na frente do nome (businessName). O nome curto continua
    // valendo só para a pessoa, não para o card.
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
        // Telefone da empresa; sem ele, o do contato (quando só há um número, ele vai no card e na pessoa).
        String companyPhone = bestPhone(item.phone());
        addField(fields, properties.arpa().telefoneCustomfieldId(),
                companyPhone.isEmpty() ? bestPhone(item.contactPhone()) : companyPhone);
        return fields;
    }

    private List<Map<String, Object>> quoteFields(LegacyQuote quote) {
        List<Map<String, Object>> fields = new ArrayList<>();
        addField(fields, properties.arpa().cnpjCustomfieldId(), quote.payerCnpj());
        addField(fields, properties.arpa().baseCotacaoCustomfieldId(), String.valueOf(quote.id()));
        addField(fields, properties.arpa().rotaCustomfieldId(), quote.senderCity() + " → " + quote.recipientCity());
        addField(fields, properties.arpa().tipoCargaCustomfieldId(), quote.cargoType());
        addField(fields, properties.arpa().volumeCustomfieldId(), quote.volumes() + " volume(s)");
        LegacyQuote.PayerProfile payer = quote.payerProfile();
        addField(fields, properties.arpa().segmentoCustomfieldId(), payer.segment());
        addField(fields, properties.arpa().cidadeCustomfieldId(), payer.city());
        addField(fields, properties.arpa().estadoCustomfieldId(), payer.state());
        addField(fields, properties.arpa().emailCustomfieldId(), firstEmail(payer.email()));
        addField(fields, properties.arpa().telefoneCustomfieldId(), bestPhone(payer.phone()));
        addField(fields, properties.arpa().previsaoFechamentoCustomfieldId(), closingDate(payer.closingForecast()));
        return fields;
    }

    // O legado junta vários e-mails no mesmo campo ("a@x.com / b@x.com"); o card leva o primeiro.
    private String firstEmail(String value) {
        if (value == null) return "";
        for (String part : value.split("[,;/\\s]+")) {
            if (part.contains("@")) return part.trim().toLowerCase(java.util.Locale.ROOT);
        }
        return "";
    }

    // Data sem hora no legado: meio-dia de Brasília, no formato ISO que a API do Arpa usa.
    private String closingDate(java.time.LocalDate date) {
        return date == null ? null : date + "T12:00:00.000-03:00";
    }

    private void addField(List<Map<String, Object>> fields, long id, String value) {
        if (id > 0 && value != null && !value.isBlank()) {
            // Ordem fixa das chaves (Map.of não garante ordem e deixava o JSON variar entre execuções).
            Map<String, Object> field = new LinkedHashMap<>();
            field.put("customfieldId", id);
            field.put("value", value);
            fields.add(field);
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
                nullableLong(item, "peopleId"), nullableLong(item, "userId"), dealCnpj(item));
    }

    private String dealCnpj(JsonNode item) {
        long fieldId = properties.arpa().cnpjCustomfieldId();
        for (String name : List.of("normalizedCustomfields", "customfields")) {
            for (JsonNode field : item.path(name)) {
                if (field.path("customfieldId").asLong() == fieldId) {
                    String digits = HubCrmNormalization.digits(field.path("value").asText(""));
                    if (!digits.isEmpty()) return digits;
                }
            }
        }
        return null;
    }

    private double amount(BigDecimal value) {
        return value == null ? 0 : value.doubleValue();
    }

    private String bestPhone(String value) {
        return HubCrmNormalization.phone(value);
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
