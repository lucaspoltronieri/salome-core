package br.com.salome.core.infrastructure.hubcrm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ArpaSuiteHttpGatewayTest {

    @Test
    void aceitaErroDeDigitacaoExistenteNoCatalogoDeCargaEspecial() {
        HubCrmProperties properties = new HubCrmProperties(true, false, 30000, 32001, 10,
                "https://core.example.com", "12345678901234567890123456789012", 60,
                new HubCrmProperties.Datasource("jdbc:h2:mem:test", "sa", ""),
                new HubCrmProperties.Arpa("https://suite.arpacore.com.br", "key", 1, 2, 3,
                        4, 5, 6, 7, 8, 9, 10, 11, 12, 13));
        ArpaSuiteHttpGateway gateway = new ArpaSuiteHttpGateway(properties);

        assertThat(gateway.normalized("Carga epecial/dedicada"))
                .isEqualTo(gateway.normalized("Carga especial/dedicada"));
    }
}
