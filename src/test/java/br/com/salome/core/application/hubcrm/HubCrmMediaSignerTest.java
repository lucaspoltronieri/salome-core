package br.com.salome.core.application.hubcrm;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.salome.core.infrastructure.hubcrm.HubCrmProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class HubCrmMediaSignerTest {
    @Test
    void assinaturaValidaAteExpirarENaoAceitaOutraCotacao() {
        HubCrmProperties properties = properties();
        Instant now = Instant.parse("2026-08-17T12:00:00Z");
        HubCrmMediaSigner signer = new HubCrmMediaSigner(properties, Clock.fixed(now, ZoneOffset.UTC));
        var signed = signer.quoteUrl(15580);

        assertThat(signer.valid(15580, signed.expires(), signed.signature())).isTrue();
        assertThat(signer.valid(15581, signed.expires(), signed.signature())).isFalse();

        HubCrmMediaSigner expired = new HubCrmMediaSigner(properties,
                Clock.fixed(now.plusSeconds(3601), ZoneOffset.UTC));
        assertThat(expired.valid(15580, signed.expires(), signed.signature())).isFalse();
    }

    private HubCrmProperties properties() {
        return new HubCrmProperties(true, false, 30000, 32001, 10,
                "https://core.example.com", "12345678901234567890123456789012", 60,
                new HubCrmProperties.Datasource("jdbc:h2:mem:test", "sa", ""), arpa());
    }

    private HubCrmProperties.Arpa arpa() {
        return new HubCrmProperties.Arpa("https://suite.arpacore.com.br", "key", 1, 2, 3,
                4, 5, 6, 7, 8, 9, 10, 11, 12, 13);
    }
}
