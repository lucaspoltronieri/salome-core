package br.com.salome.core.application.hubcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.salome.core.domain.hubcrm.InactiveClientReport;
import br.com.salome.core.infrastructure.hubcrm.HubCrmProperties;
import br.com.salome.core.infrastructure.hubcrm.HubCrmStore;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class HubCrmClientTransportNoteServiceTest {
    private static final String CNPJ = "48108565000113";
    private HubCrmStore store;
    private InactiveClientRepository reports;
    private ArpaSuiteGateway arpa;
    private HubCrmMediaSigner signer;
    private HubCrmClientTransportNoteService service;

    @BeforeEach
    void setUp() {
        store = mock(HubCrmStore.class);
        reports = mock(InactiveClientRepository.class);
        arpa = mock(ArpaSuiteGateway.class);
        signer = mock(HubCrmMediaSigner.class);
        // Carteira / Não Pagantes = 2 (segundo id do Arpa abaixo).
        HubCrmProperties properties = new HubCrmProperties(true, false, 30000, 32001, 10,
                "https://core.example.com", "12345678901234567890123456789012", 60,
                new HubCrmProperties.Datasource("jdbc:h2:mem:test", "sa", ""),
                new HubCrmProperties.Arpa("https://suite.arpacore.com.br", "key", 1, 2, 3,
                        4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16));
        service = new HubCrmClientTransportNoteService(store, reports, arpa, signer, properties);
        when(arpa.findDealStage(2290001)).thenReturn(Optional.of(2L));
        when(store.clientCardsWithoutTransportNote(HubCrmClientTransportNoteService.PER_CYCLE))
                .thenReturn(List.of(new HubCrmStore.ClientCard(32500, CNPJ, 2290001)));
        when(signer.receivedClientUrl(32500, HubCrmClientTransportNoteService.LINK_TTL_DAYS))
                .thenReturn(new HubCrmMediaSigner.SignedUrl("https://hub/api/hub-crm/public/nao-pagantes/32500/pdf?x=1&y=2",
                        1L, "s"));
    }

    @Test
    void gravaUltimoTransporteELinkDaRelacaoDeCtes() {
        when(reports.findReceivedReport(32500)).thenReturn(Optional.of(report(List.of(
                cte(301000, LocalDate.of(2025, 11, 3), "10", "500", "80.00"),
                cte(318500, LocalDate.of(2026, 9, 1), "25.5", "1200", "150.40")))));
        when(arpa.addAnnotation(eq(2290001L), anyString())).thenReturn(77L);

        var result = service.annotatePending();

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(arpa).addAnnotation(eq(2290001L), text.capture());
        assertThat(text.getValue())
                .contains("Cliente Não Pagante", "Recebeu 2 CT-es desde 03/11/2025", "Frete (pago pelo remetente): R$ 230,40")
                .contains("Último transporte: 01/09/2026 | CT-e 318500 | Origem: FORNECEDOR SA - SÃO PAULO")
                .contains("Destino: TINTAS DO CLIENTE - CEDRAL", "Peso: 25,500 kg", "Valor NF: R$ 1.200,00",
                        "Frete: R$ 150,40", "Pagto: CIF")
                .contains("href=\"https://hub/api/hub-crm/public/nao-pagantes/32500/pdf?x=1&amp;y=2\"",
                        "Abrir PDF dos CT-es recebidos");
        verify(store).recordEvent(eq("client:" + CNPJ + ":transportes"), eq("CLIENTE"), eq(32500L),
                eq("OBS_TRANSPORTES"), eq("PROCESSADO"), anyString(), isNull());
        assertThat(result).isEqualTo(new HubCrmClientTransportNoteService.NoteResult(1, 0, 0));
    }

    @Test
    void cardApagadoNoArpaNaoRecebeObservacaoEViraRemovido() {
        when(reports.findReceivedReport(32500)).thenReturn(Optional.of(report(List.of(
                cte(318500, LocalDate.of(2026, 9, 1), "1", "1", "1")))));
        when(arpa.findDealStage(2290001)).thenReturn(Optional.empty());

        service.annotatePending();

        verify(arpa, never()).addAnnotation(anyLong(), anyString());
        verify(store).markClientRemoved(CNPJ);
    }

    @Test
    void cardForaDoEstagioNaoPagantesNaoRecebeObservacao() {
        when(reports.findReceivedReport(32500)).thenReturn(Optional.of(report(List.of(
                cte(318500, LocalDate.of(2026, 9, 1), "1", "1", "1")))));
        when(arpa.findDealStage(2290001)).thenReturn(Optional.of(3L));

        var result = service.annotatePending();

        verify(arpa, never()).addAnnotation(anyLong(), anyString());
        verify(store).recordEvent(eq("client:" + CNPJ + ":transportes"), eq("CLIENTE"), eq(32500L),
                eq("OBS_TRANSPORTES"), eq("FORA_DO_ESTAGIO"), anyString(), isNull());
        assertThat(result.skipped()).isEqualTo(1);
    }

    @Test
    void semCteRecebidoNaoGravaObservacao() {
        when(reports.findReceivedReport(32500)).thenReturn(Optional.of(report(List.of())));

        var result = service.annotatePending();

        verify(arpa, never()).addAnnotation(anyLong(), anyString());
        verify(store).recordEvent(eq("client:" + CNPJ + ":transportes"), eq("CLIENTE"), eq(32500L),
                eq("OBS_TRANSPORTES"), eq("SEM_CTE"), anyString(), isNull());
        assertThat(result.skipped()).isEqualTo(1);
    }

    @Test
    void falhaNaApiFicaRegistradaComoErro() {
        when(reports.findReceivedReport(32500)).thenReturn(Optional.of(report(List.of(
                cte(318500, LocalDate.of(2026, 9, 1), "1", "1", "1")))));
        when(arpa.addAnnotation(eq(2290001L), anyString())).thenThrow(new IllegalStateException("429"));

        var result = service.annotatePending();

        verify(store).recordEvent(eq("client:" + CNPJ + ":transportes"), eq("CLIENTE"), eq(32500L),
                eq("OBS_TRANSPORTES"), eq("ERRO"), isNull(), eq("429"));
        assertThat(result.failed()).isEqualTo(1);
    }

    private static InactiveClientReport report(List<InactiveClientReport.Cte> ctes) {
        return new InactiveClientReport(32500, 2026, "recebidos", "TINTAS DO CLIENTE LTDA", "", CNPJ, "CEDRAL", "SP",
                ctes);
    }

    private static InactiveClientReport.Cte cte(long number, LocalDate issued, String weight, String invoice,
            String freight) {
        return new InactiveClientReport.Cte(number, issued, "Emitente (CIF)", "FORNECEDOR SA", "11222333000181",
                "SÃO PAULO", "TINTAS DO CLIENTE", CNPJ, "CEDRAL", "1001", 3, new BigDecimal(weight),
                new BigDecimal(invoice), new BigDecimal(freight));
    }
}
