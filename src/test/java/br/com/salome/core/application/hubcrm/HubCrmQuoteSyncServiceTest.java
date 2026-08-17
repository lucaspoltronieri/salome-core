package br.com.salome.core.application.hubcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    void aprovadaMarcaCardComoGanho() {
        LegacyQuote quote = quote("APROVADA", Map.of());
        prepare(quote);

        service.syncQuotes();

        verify(arpa).markWon(99, quote.statusAt());
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

    private void prepare(LegacyQuote quote) {
        when(legacy.findQuotesAfter(0)).thenReturn(List.of(quote));
        when(legacy.findQuotesByIds(any())).thenReturn(List.of());
        when(store.findQuote(quote.id())).thenReturn(Optional.of(new QuoteIntegration(
                quote.id(), quote.payerCnpj(), quote.status(), null, null, null, null,
                quote.totalFreight(), "old", "PENDENTE", "AGUARDANDO_CANAL")));
    }

    private LegacyQuote quote(String status, Map<LossReason, String> reasons) {
        BigDecimal zero = BigDecimal.ZERO;
        return new LegacyQuote(15580, LocalDate.of(2026, 8, 17), "10:30", "FERNANDA", status,
                LocalDateTime.of(2026, 8, 17, 11, 0), "Emitente (CIF)",
                "12345678000190", "REMETENTE LTDA", "SÃO PAULO-SP",
                "98765432000110", "DESTINATÁRIO LTDA", "CAMPINAS-SP",
                "12345678000190", "REMETENTE LTDA", "11999999999", "vendas@remetente.com",
                "DIVERSOS", 2, new BigDecimal("50"), new BigDecimal("1000"), new BigDecimal("0.5"),
                new BigDecimal("100"), new BigDecimal("20"), new BigDecimal("10"), zero, zero,
                zero, zero, zero, new BigDecimal("20"), zero, zero, new BigDecimal("150"),
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
