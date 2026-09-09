package br.com.salome.core.application.hubcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.salome.core.domain.hubcrm.LegacyQuote;
import br.com.salome.core.domain.hubcrm.LossReason;
import br.com.salome.core.domain.hubcrm.QuoteIntegration;
import br.com.salome.core.infrastructure.hubcrm.HubCrmProperties;
import br.com.salome.core.infrastructure.hubcrm.HubCrmStore;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HubCrmQuoteSyncServiceTest {
    private HubCrmLegacyRepository legacy;
    private HubCrmStore store;
    private ArpaSuiteGateway arpa;
    private HubCrmQuoteSyncService service;

    @BeforeEach
    void setUp() {
        legacy = mock(HubCrmLegacyRepository.class);
        store = mock(HubCrmStore.class);
        arpa = mock(ArpaSuiteGateway.class);
        when(store.checkpoint(anyString(), anyLong())).thenReturn(0L);
        when(store.trackedQuoteIds()).thenReturn(List.of());
        when(arpa.findLatestOpenDealByCnpj(anyString()))
                .thenReturn(Optional.of(new ArpaSuiteGateway.ArpaDeal(99, 77L, 88L, 4L)));
        when(arpa.addAnnotation(anyLong(), anyString())).thenReturn(123L);
        when(arpa.hasWhatsappChannel()).thenReturn(false);
        service = new HubCrmQuoteSyncService(legacy, store, arpa, properties(), mock(HubCrmMediaSigner.class));
    }

    @Test
    void usaRemetenteComoPagadorCifEMoveParaProposta() {
        LegacyQuote quote = quote("ABERTA", Map.of());
        prepare(quote);

        var result = service.syncQuotes();

        assertThat(result.integrated()).isEqualTo(1);
        verify(arpa).updateDealFromQuote(99, quote, 4);
        verify(arpa, never()).markWon(anyLong(), any());
        verify(arpa, never()).markLost(anyLong(), any(), any());
    }

    @Test
    void persisteAmarracaoAntesDeAtualizarCardETimeline() {
        LegacyQuote quote = quote("ABERTA", Map.of());
        prepare(quote);

        service.syncQuotes();

        var order = inOrder(store, arpa);
        order.verify(store).bindQuote(quote.id(), 77, 88, 99, 4);
        order.verify(arpa).updateDealFromQuote(99, quote, 4);
        order.verify(arpa).addAnnotation(anyLong(), anyString());
    }

    @Test
    void recuperaCardPeloIdDaCotacaoSemCriarOutro() {
        LegacyQuote quote = quote("ABERTA", Map.of());
        prepare(quote);
        when(arpa.findDealByLegacyQuoteId(quote.id()))
                .thenReturn(Optional.of(new ArpaSuiteGateway.ArpaDeal(123, 77L, 88L, 4L)));

        service.syncQuotes();

        verify(store).bindQuote(quote.id(), 77, 88, 123, 4);
        verify(arpa).updateDealFromQuote(123, quote, 4);
        verify(arpa, never()).createQuoteDeal(any(), anyLong(), anyLong(), anyLong());
        verify(arpa, never()).findLatestOpenDealByCnpj(anyString());
    }

    @Test
    void usaIdDoCardPersistidoAoAlterarMesmaCotacao() {
        LegacyQuote quote = quote("ABERTA", Map.of());
        when(legacy.findQuotesAfter(0)).thenReturn(List.of(quote));
        when(legacy.findQuotesByIds(any())).thenReturn(List.of());
        when(store.findQuote(quote.id())).thenReturn(Optional.of(new QuoteIntegration(
                quote.id(), quote.payerCnpj(), quote.status(), 321L, 77L, 88L, 4L,
                quote.totalFreight(), "old", "ATUALIZAR", "AGUARDANDO_CANAL")));
        when(arpa.findDeal(321)).thenReturn(Optional.of(new ArpaSuiteGateway.ArpaDeal(321, 77L, 88L, 4L)));

        service.syncQuotes();

        verify(store).bindQuote(quote.id(), 77, 88, 321, 4);
        verify(arpa).updateDealFromQuote(321, quote, 4);
        verify(arpa, never()).createQuoteDeal(any(), anyLong(), anyLong(), anyLong());
        verify(arpa, never()).findDealByLegacyQuoteId(anyLong());
    }

    @Test
    void naoReaproveitaCardAbertoAmarradoAOutraCotacao() {
        LegacyQuote quote = quote("ABERTA", Map.of());
        prepare(quote);
        when(store.dealBoundToOtherQuote(99, quote.id())).thenReturn(true);
        when(arpa.createOrganization(anyString())).thenReturn(77L);
        when(arpa.createPerson(anyString(), anyString(), anyLong())).thenReturn(88L);
        when(arpa.createQuoteDeal(quote, 77, 88, 4)).thenReturn(456L);

        service.syncQuotes();

        verify(store).bindQuote(quote.id(), 77, 88, 456, 4);
        verify(arpa).updateDealFromQuote(456, quote, 4);
    }

    @Test
    void aprovadaMarcaCardComoGanho() {
        LegacyQuote quote = quote("APROVADA", Map.of());
        prepare(quote);

        service.syncQuotes();

        verify(arpa).markWon(99, quote.statusAt());
    }

    @Test
    void ganhoJaProcessadoNaoEhReenviadoAoArpa() {
        LegacyQuote quote = quote("APROVADA", Map.of());
        String eventKey = "quote:" + quote.id() + ":status:APROVADA:" + quote.statusAt();
        when(store.eventProcessed(eventKey)).thenReturn(false, true);

        service.applyStatus(quote, 99);
        service.applyStatus(quote, 99);

        verify(arpa, times(1)).markWon(99, quote.statusAt());
        verify(arpa, times(1)).addAnnotation(99,
                "Cotação " + quote.id() + " aprovada no legado em " + quote.statusAt());
    }

    @Test
    void cotacaoSemFreteCalculadoNaoCriaTimeline() {
        LegacyQuote quote = quote("ABERTA", Map.of(), BigDecimal.ZERO);
        prepare(quote);

        service.syncQuotes();

        verify(arpa, never()).addAnnotation(anyLong(), anyString());
        verify(arpa).updateDealFromQuote(99, quote, 4);
    }

    @Test
    void mudancaSomenteDeStatusNaoDuplicaObservacaoDaCotacao() {
        LegacyQuote open = quote("ABERTA", Map.of());
        LegacyQuote approved = quote("APROVADA", Map.of());
        when(store.eventProcessed(anyString())).thenReturn(false, true);

        service.applyQuoteAnnotation(open, 99);
        service.applyQuoteAnnotation(approved, 99);

        verify(arpa, times(1)).addAnnotation(anyLong(), anyString());
    }

    @Test
    void primeiraLeituraDoNovoHashIndexaObservacaoAntigaSemDuplicar() {
        LegacyQuote quote = quote("APROVADA", Map.of());
        when(store.hasProcessedQuoteContentEvent(quote.id())).thenReturn(false);
        when(store.hasProcessedLegacyQuoteSnapshotEvent(quote.id())).thenReturn(true);

        service.applyQuoteAnnotation(quote, 99);

        verify(arpa, never()).addAnnotation(anyLong(), anyString());
        verify(store).recordEvent(anyString(), anyString(), anyLong(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.contains("indexado sem nova anotação"), any());
    }

    @Test
    void naoAprovadaComUmMotivoMarcaComoPerdida() {
        LegacyQuote quote = quote("NÃO APROVADA", Map.of(LossReason.HIGH_PRICE, "Cliente achou caro"));
        prepare(quote);

        service.syncQuotes();

        verify(arpa).markLost(99, LossReason.HIGH_PRICE, quote.statusAt());
    }

    @Test
    void naoAprovadaSemMotivoVaiParaRevisao() {
        LegacyQuote quote = quote("NÃO APROVADA", Map.of());
        prepare(quote);

        var result = service.syncQuotes();

        assertThat(result.review()).isEqualTo(1);
        verify(store).markQuoteReview(anyLong(), anyString());
        verify(arpa, never()).markLost(anyLong(), any(), any());
    }

    @Test
    void falhaNoReprocessoNaoAbortaOPollingNemSeguraOCheckpoint() {
        LegacyQuote integrada = quote("ABERTA", Map.of());
        LegacyQuote nova = quote("ABERTA", Map.of(), new BigDecimal("300"), 15781);
        when(legacy.findQuotesAfter(0)).thenReturn(List.of(integrada, nova));
        when(legacy.findQuotesByIds(any())).thenReturn(List.of());
        // A cotação já integrada volta com o hash do próprio snapshot, então cai no
        // ramo de reprocesso — onde o ArpaSuite falha (indisponível).
        java.util.concurrent.atomic.AtomicReference<String> hash = new java.util.concurrent.atomic.AtomicReference<>();
        org.mockito.Mockito.doAnswer(invocation -> {
            hash.set(invocation.getArgument(1));
            return null;
        }).when(store).discoverQuote(org.mockito.ArgumentMatchers.eq(integrada), anyString());
        when(store.findQuote(integrada.id())).thenAnswer(invocation -> Optional.of(new QuoteIntegration(
                integrada.id(), integrada.payerCnpj(), integrada.status(), 555L, 77L, 88L, 4L,
                integrada.totalFreight(), hash.get(), "INTEGRADO", "AGUARDANDO_CANAL")));
        when(store.findQuote(nova.id())).thenReturn(Optional.of(new QuoteIntegration(
                nova.id(), nova.payerCnpj(), nova.status(), null, null, null, null,
                nova.totalFreight(), "old", "PENDENTE", "AGUARDANDO_CANAL")));
        when(arpa.addAnnotation(org.mockito.ArgumentMatchers.eq(555L), anyString()))
                .thenThrow(new IllegalStateException("503 Service Unavailable"));

        var result = service.syncQuotes();

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.integrated()).isEqualTo(1);
        verify(arpa).updateDealFromQuote(99, nova, 4);
        verify(store).setCheckpoint("last_quote_id", nova.id());
        // A cotação que falhou continua INTEGRADO: o reprocesso é tentado no próximo polling.
        verify(store, never()).markQuoteError(anyLong(), any());
    }

    private void prepare(LegacyQuote quote) {
        when(legacy.findQuotesAfter(0)).thenReturn(List.of(quote));
        when(legacy.findQuotesByIds(any())).thenReturn(List.of());
        when(store.findQuote(quote.id())).thenReturn(Optional.of(new QuoteIntegration(
                quote.id(), quote.payerCnpj(), quote.status(), null, null, null, null,
                quote.totalFreight(), "old", "PENDENTE", "AGUARDANDO_CANAL")));
    }

    private LegacyQuote quote(String status, Map<LossReason, String> reasons) {
        return quote(status, reasons, new BigDecimal("150"));
    }

    private LegacyQuote quote(String status, Map<LossReason, String> reasons, BigDecimal totalFreight) {
        return quote(status, reasons, totalFreight, 15580);
    }

    private LegacyQuote quote(String status, Map<LossReason, String> reasons, BigDecimal totalFreight,
            long id) {
        BigDecimal zero = BigDecimal.ZERO;
        return new LegacyQuote(id, LocalDate.of(2026, 8, 17), "10:30", "FERNANDA", status,
                LocalDateTime.of(2026, 8, 17, 11, 0), "Emitente (CIF)",
                "12345678000190", "REMETENTE LTDA", "SÃO PAULO-SP",
                "98765432000110", "DESTINATÁRIO LTDA", "CAMPINAS-SP",
                "12345678000190", "REMETENTE LTDA", "11999999999", "vendas@remetente.com",
                "DIVERSOS", 2, new BigDecimal("50"), new BigDecimal("1000"), new BigDecimal("0.5"),
                new BigDecimal("100"), new BigDecimal("20"), new BigDecimal("10"), zero, zero,
                zero, zero, zero, new BigDecimal("20"), zero, zero, totalFreight,
                "Maria", reasons);
    }

    private HubCrmProperties properties() {
        return new HubCrmProperties(true, false, 30000, 32001, 10,
                "https://core.example.com", "12345678901234567890123456789012", 60,
                new HubCrmProperties.Datasource("jdbc:h2:mem:test", "sa", ""),
                new HubCrmProperties.Arpa("https://suite.arpacore.com.br", "key", 1, 2, 3,
                        4, 5, 6, 7, 8, 9, 10, 11, 12, 13));
    }
}
