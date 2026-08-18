package br.com.salome.core.infrastructure.hubcrm;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.salome.core.application.hubcrm.ArpaSuiteGateway;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
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
        AtomicReference<byte[]> requestBody = new AtomicReference<>();
        AtomicReference<Long> contentLength = new AtomicReference<>();
        AtomicReference<String> accept = new AtomicReference<>();
        server.createContext("/api/organizations", exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                byte[] response = "{\"data\":[]}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/plain;charset=utf-8");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
                return;
            }
            requestBody.set(exchange.getRequestBody().readAllBytes());
            contentLength.set(Long.parseLong(exchange.getRequestHeaders().getFirst("Content-Length")));
            accept.set(exchange.getRequestHeaders().getFirst("Accept"));
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

            assertThat(gateway.createOrganization("CLIENTE SALOMÉ")).isEqualTo(987L);
            assertThat(requestBody.get()).hasSize(contentLength.get().intValue());
            assertThat(new String(requestBody.get(), StandardCharsets.UTF_8)).contains("CLIENTE SALOMÉ");
            assertThat(accept.get()).contains("application/json");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void leJsonDeBuscaDeCardsQueRespondeComoTexto() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> query = new AtomicReference<>();
        server.createContext("/api/deals", exchange -> {
            query.set(exchange.getRequestURI().getRawQuery());
            byte[] response = "{\"data\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain;charset=utf-8");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var gateway = new ArpaSuiteHttpGateway(
                    properties("http://127.0.0.1:" + server.getAddress().getPort()));

            assertThat(gateway.findLatestOpenDealByCnpj("19076738000160")).isEmpty();
            assertThat(query.get())
                    .contains("pipe=1", "status=open", "customfields=6:19076738000160")
                    .doesNotContain("orderColumn", "orderDirection");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void buscaCardPeloIdDaCotacaoNoCampoBaseCotacao() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> query = new AtomicReference<>();
        server.createContext("/api/deals", exchange -> {
            query.set(exchange.getRequestURI().getRawQuery());
            byte[] response = "{\"data\":[{\"id\":555,\"organizationId\":77,\"peopleId\":88}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var gateway = new ArpaSuiteHttpGateway(
                    properties("http://127.0.0.1:" + server.getAddress().getPort()));

            assertThat(gateway.findDealByLegacyQuoteId(15591)).get()
                    .extracting(ArpaSuiteGateway.ArpaDeal::id).isEqualTo(555L);
            assertThat(query.get()).contains("pipe=1", "customfields=10:15591");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void recuperaIdDaOrganizacaoQuandoPostRetornaCorpoNaoJson() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger gets = new AtomicInteger();
        AtomicReference<String> query = new AtomicReference<>();
        server.createContext("/api/organizations", exchange -> {
            byte[] response;
            if ("GET".equals(exchange.getRequestMethod())) {
                query.set(exchange.getRequestURI().getRawQuery());
                response = (gets.getAndIncrement() == 0 ? "{\"data\":[]}" :
                        "{\"data\":[{\"id\":321,\"name\":\"CLIENTE, TESTE\"}]}")
                        .getBytes(StandardCharsets.UTF_8);
            } else {
                exchange.getRequestBody().readAllBytes();
                response = "created".getBytes(StandardCharsets.UTF_8);
            }
            exchange.getResponseHeaders().set("Content-Type", "text/plain;charset=utf-8");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var gateway = new ArpaSuiteHttpGateway(
                    properties("http://127.0.0.1:" + server.getAddress().getPort()));

            assertThat(gateway.createOrganization("CLIENTE, TESTE")).isEqualTo(321L);
            assertThat(gets).hasValue(2);
            assertThat(query.get()).contains("name=CLIENTE%2C%20TESTE");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void criaAnotacaoComoObservacao() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> request = new AtomicReference<>();
        server.createContext("/api/annotations", exchange -> {
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"id\":456}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(201, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var gateway = new ArpaSuiteHttpGateway(
                    properties("http://127.0.0.1:" + server.getAddress().getPort()));

            assertThat(gateway.addAnnotation(123L, "Data do primeiro CT-e")).isEqualTo(456L);
            assertThat(request.get()).contains("\"type\":\"observation\"", "\"dealId\":123");
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
