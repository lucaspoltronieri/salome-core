package br.com.salome.core.application.hubcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.salome.core.domain.hubcrm.ClientIntegration;
import br.com.salome.core.domain.hubcrm.LegacyCrmClient;
import br.com.salome.core.infrastructure.hubcrm.HubCrmProperties;
import br.com.salome.core.infrastructure.hubcrm.HubCrmStore;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HubCrmClientSyncServiceTest {
    private HubCrmLegacyRepository legacy;
    private HubCrmStore store;
    private ArpaSuiteGateway arpa;
    private HubCrmClientSyncService service;

    @BeforeEach
    void setUp() {
        legacy = mock(HubCrmLegacyRepository.class);
        store = mock(HubCrmStore.class);
        arpa = mock(ArpaSuiteGateway.class);
        when(store.nextRoundRobinUser(anyLong(), anyLong())).thenReturn(4L);
        when(arpa.findLatestOpenDealByCnpj(anyString())).thenReturn(Optional.empty());
        when(arpa.createOrganization(anyString())).thenReturn(77L);
        when(arpa.createPerson(anyString(), anyString(), anyLong())).thenReturn(88L);
        when(arpa.createPortfolioDeal(any(), anyLong(), anyLong(), anyLong())).thenReturn(99L);
        when(arpa.createPortfolioDealWithoutPerson(any(), anyLong(), anyLong())).thenReturn(555L);
        service = new HubCrmClientSyncService(legacy, store, arpa, properties());
    }

    @Test
    void cadastroSemContatoNoLegadoViraCardNaCarteiraSemPessoa() {
        LegacyCrmClient client = client("");
        prepare(client, novo(client));

        var result = service.syncNewClients();

        assertThat(result.integrated()).isEqualTo(1);
        verify(arpa).createOrganization("ACME INDUSTRIA LTDA");
        verify(arpa).createPortfolioDealWithoutPerson(client, 77, 4);
        // Nunca cria uma pessoa artificial com o nome da empresa.
        verify(arpa, never()).createPerson(anyString(), anyString(), anyLong());
        verify(store).markClientIntegrated("12345678000190", 77, null, 555L, 4, hashOf(client));
    }

    @Test
    void cadastroComContatoValidoCriaPessoaECard() {
        LegacyCrmClient client = client("MARIA SILVA");
        prepare(client, novo(client));

        var result = service.syncNewClients();

        assertThat(result.integrated()).isEqualTo(1);
        verify(arpa).createPerson("MARIA SILVA", "11988887777", 77);
        verify(arpa).createPortfolioDeal(client, 77, 88, 4);
        verify(arpa, never()).createPortfolioDealWithoutPerson(any(), anyLong(), anyLong());
    }

    @Test
    void cadastroAntigoMarcadoSemContatoEReprocessadoEGanhaCard() {
        LegacyCrmClient client = client("ERICK");
        // Estado deixado pela regra antiga: SEM_CONTATO, sem card, com o hash já gravado.
        prepare(client, new ClientIntegration(client.legacyClientId(), client.cnpj(), null, null,
                null, null, client.firstCteWithoutFreight(), hashOf(client), "SEM_CONTATO"));

        var result = service.syncNewClients();

        assertThat(result.skipped()).isZero();
        assertThat(result.integrated()).isEqualTo(1);
        verify(arpa).createPortfolioDealWithoutPerson(client, 77, 4);
    }

    @Test
    void cardApagadoNoArpaSuiteNaoERecriado() {
        LegacyCrmClient client = client("MARIA SILVA");
        prepare(client, new ClientIntegration(client.legacyClientId(), client.cnpj(), 77L, 88L,
                2231912L, 4L, client.firstCteWithoutFreight(), "outro", "ERRO"));
        when(store.canRetryClient(client.cnpj())).thenReturn(true);
        // O card foi apagado no CRM: a API responde 404 e o gateway devolve vazio.
        when(arpa.findDeal(2231912L)).thenReturn(Optional.empty());

        var result = service.syncNewClients();

        assertThat(result.failed()).isZero();
        assertThat(result.integrated()).isZero();
        verify(store).markClientRemoved("12345678000190");
        // Quem apagou decidiu que o card não deve existir: nada é recriado.
        verify(arpa, never()).createPortfolioDeal(any(), anyLong(), anyLong(), anyLong());
        verify(arpa, never()).createPortfolioDealWithoutPerson(any(), anyLong(), anyLong());
        verify(arpa, never()).updatePortfolioDeal(anyLong(), any(), anyLong(), anyLong(), anyLong());
    }

    @Test
    void cadastroComCardRemovidoNaoEReprocessado() {
        LegacyCrmClient client = client("MARIA SILVA");
        prepare(client, new ClientIntegration(client.legacyClientId(), client.cnpj(), 77L, 88L,
                null, 4L, client.firstCteWithoutFreight(), "outro", "REMOVIDO"));

        var result = service.syncNewClients();

        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.integrated()).isZero();
        verify(arpa, never()).createPortfolioDeal(any(), anyLong(), anyLong(), anyLong());
    }

    @Test
    void cardExistenteEApenasAtualizado() {
        LegacyCrmClient client = client("MARIA SILVA");
        prepare(client, new ClientIntegration(client.legacyClientId(), client.cnpj(), 77L, 88L,
                4242L, 4L, client.firstCteWithoutFreight(), "outro", "INTEGRADO"));
        when(arpa.findDeal(4242L))
                .thenReturn(Optional.of(new ArpaSuiteGateway.ArpaDeal(4242, 77L, 88L, 4L)));

        var result = service.syncNewClients();

        assertThat(result.updated()).isEqualTo(1);
        verify(arpa).updatePortfolioDeal(4242, client, 77, 88, 4);
        verify(arpa, never()).createPortfolioDeal(any(), anyLong(), anyLong(), anyLong());
    }

    private void prepare(LegacyCrmClient client, ClientIntegration current) {
        when(legacy.findEligibleClients(32001)).thenReturn(List.of(client));
        when(store.findClient(client.cnpj())).thenReturn(Optional.of(current));
        when(store.eventProcessed(anyString())).thenReturn(true);
    }

    private ClientIntegration novo(LegacyCrmClient client) {
        return new ClientIntegration(client.legacyClientId(), client.cnpj(), null, null, null, null,
                client.firstCteWithoutFreight(), "outro", "PENDENTE");
    }

    private String hashOf(LegacyCrmClient client) {
        return br.com.salome.core.domain.hubcrm.HubCrmNormalization.sha256(
                br.com.salome.core.domain.hubcrm.HubCrmNormalization.businessName(client.legalName()),
                client.cnpj(), client.city(), client.state(), client.email(), client.phone(),
                client.segment(),
                br.com.salome.core.domain.hubcrm.HubCrmNormalization.contactName(client.contactName()),
                client.contactDepartment(), client.contactEmail(), client.contactPhone(),
                client.firstCteWithoutFreight());
    }

    private LegacyCrmClient client(String contactName) {
        return new LegacyCrmClient(33500, "ACME INDUSTRIA LTDA", "12345678000190", "BAURU", "SP",
                "contato@acme.com.br", "1133334444", "COMERCIO", contactName, "COMPRAS",
                "compras@acme.com.br", "11988887777", LocalDate.of(2026, 8, 12));
    }

    private HubCrmProperties properties() {
        return new HubCrmProperties(true, false, 30000, 32001, 10,
                "https://core.example.com", "12345678901234567890123456789012", 60,
                new HubCrmProperties.Datasource("jdbc:h2:mem:test", "sa", ""),
                new HubCrmProperties.Arpa("https://suite.arpacore.com.br", "key", 1, 2, 3,
                        4, 5, 6, 7, 8, 9, 10, 11, 12, 13));
    }
}
