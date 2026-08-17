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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "salome.hub-crm", name = "enabled", havingValue = "true")
public class HubCrmMediaSigner {
    private final HubCrmProperties properties;
    private final Clock clock;

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
        String signature = signature(quoteId, expires);
        String base = properties.publicBaseUrl().replaceAll("/+$", "");
        return new SignedUrl(base + "/api/hub-crm/public/cotacoes/" + quoteId
                + "/pdf?expires=" + expires + "&signature=" + signature, expires, signature);
    }

    public boolean valid(long quoteId, long expires, String candidate) {
        if (candidate == null || expires < clock.instant().getEpochSecond()) return false;
        byte[] expected = signature(quoteId, expires).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = candidate.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, actual);
    }

    private String signature(long quoteId, long expires) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.mediaSigningKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((quoteId + ":" + expires).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Não foi possível assinar a URL do PDF", exception);
        }
    }

    private void requireConfigured() {
        if (properties.publicBaseUrl() == null || properties.publicBaseUrl().isBlank()
                || properties.mediaSigningKey() == null || properties.mediaSigningKey().length() < 32) {
            throw new IllegalStateException("URL pública ou chave de assinatura do Hub CRM não configurada");
        }
    }

    public record SignedUrl(String url, long expires, String signature) {}
}
