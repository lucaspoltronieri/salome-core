package br.com.salome.core.application.hubcrm;

import br.com.salome.core.infrastructure.hubcrm.HubCrmProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "salome.hub-crm", name = "enabled", havingValue = "true")
public class HubCrmMediaSigner {
    private final HubCrmProperties properties;
    private final Clock clock;

    @Autowired
    public HubCrmMediaSigner(HubCrmProperties properties) {
        this(properties, Clock.systemUTC());
    }

    HubCrmMediaSigner(HubCrmProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public SignedUrl quoteUrl(long quoteId) {
        requireConfigured();
        long expires = clock.instant().plus(properties.mediaUrlTtlMinutes(), ChronoUnit.MINUTES).getEpochSecond();
        String signature = sign(quotePayload(quoteId, expires));
        return new SignedUrl(base() + "/api/hub-crm/public/cotacoes/" + quoteId
                + "/pdf?expires=" + expires + "&signature=" + signature, expires, signature);
    }

    public boolean valid(long quoteId, long expires, String candidate) {
        return matches(quotePayload(quoteId, expires), expires, candidate);
    }

    // Link do PDF de transportes do cliente inativo: vai no texto da observação do card, então
    // precisa durar enquanto o card estiver em prospecção (validade em dias, não minutos).
    public SignedUrl inactiveClientUrl(long clientId, int year, long ttlDays) {
        requireConfigured();
        long expires = clock.instant().plus(ttlDays, ChronoUnit.DAYS).getEpochSecond();
        String signature = sign(inactivePayload(clientId, year, expires));
        return new SignedUrl(base() + "/api/hub-crm/public/inativos/" + clientId + "/pdf?ano=" + year
                + "&expires=" + expires + "&signature=" + signature, expires, signature);
    }

    public boolean validInactiveClient(long clientId, int year, long expires, String candidate) {
        return matches(inactivePayload(clientId, year, expires), expires, candidate);
    }

    private static String quotePayload(long quoteId, long expires) {
        return quoteId + ":" + expires;
    }

    // Prefixo próprio para uma assinatura de cotação nunca valer como link de cliente inativo.
    private static String inactivePayload(long clientId, int year, long expires) {
        return "inativo:" + clientId + ":" + year + ":" + expires;
    }

    private boolean matches(String payload, long expires, String candidate) {
        if (candidate == null || expires < clock.instant().getEpochSecond()) return false;
        byte[] expected = sign(payload).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = candidate.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, actual);
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.mediaSigningKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Não foi possível assinar a URL do PDF", exception);
        }
    }

    private String base() {
        return properties.publicBaseUrl().replaceAll("/+$", "");
    }

    private void requireConfigured() {
        if (properties.publicBaseUrl() == null || properties.publicBaseUrl().isBlank()
                || properties.mediaSigningKey() == null || properties.mediaSigningKey().length() < 32) {
            throw new IllegalStateException("URL pública ou chave de assinatura do Hub CRM não configurada");
        }
    }

    public record SignedUrl(String url, long expires, String signature) {}
}
