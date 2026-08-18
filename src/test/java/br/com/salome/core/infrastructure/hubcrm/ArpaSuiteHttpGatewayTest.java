package br.com.salome.core.infrastructure.hubcrm;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ArpaSuiteHttpGatewayTest {

    @Test
    void aceitaErroDeDigitacaoExistenteNoCatalogoDeCargaEspecial() {
        HubCrmProperties properties = properties("https://suite.arpacore.com.br");
        ArpaSuiteHttpGateway gateway = new ArpaSuiteHttpGateway(properties);

        assertThat(gateway.normalized("Carga epecial/dedicada"))
                .isEqualTo(gateway.normalized("Carga especial/dedicada"));
    }

    @Test
    void leJsonDeEndpointDeEscritaQueRespondeComoTexto() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/organizations", exchange -> {
            byte[] response = "{\"data\":{\"id\":987}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain;charset=utf-8");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var gateway = new ArpaSuiteHttpGateway(
                    properties("http://127.0.0.1:" + server.getAddress().getPort()));

            assertThat(gateway.createOrganization("CLIENTE TESTE")).isEqualTo(987L);
        } finally {
            server.stop(0);
        }
    }

    private HubCrmProperties properties(String arpaUrl) {
        return new HubCrmProperties(true, false, 30000, 32001, 10,
                "https://core.example.com", "12345678901234567890123456789012", 60,
                new HubCrmProperties.Datasource("jdbc:h2:mem:test", "sa", ""),
                new HubCrmProperties.Arpa(arpaUrl, "key", 1, 2, 3,
                        4, 5, 6, 7, 8, 9, 10, 11, 12, 13));
    }
}
