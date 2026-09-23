package br.com.salome.core.application.hubcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.salome.core.domain.hubcrm.LegacyColeta;
import br.com.salome.core.domain.hubcrm.LegacyCte;
import br.com.salome.core.domain.hubcrm.LegacyQuote;
import br.com.salome.core.domain.hubcrm.LegacyQuoteChain;
import br.com.salome.core.infrastructure.hubcrm.HubCrmAutoApprovalProperties;
import br.com.salome.core.infrastructure.hubcrm.HubCrmStore;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HubCrmCteBindingServiceTest {
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 23);
    private static final String PAGADOR = "46299584000149";
    private HubCrmLegacyRepository legacy;
    private HubCrmStore store;
    private ArpaSuiteGateway arpa;
    private HubCrmCteBindingService service;

    @BeforeEach
    void setUp() {
        legacy = mock(HubCrmLegacyRepository.class);
        store = mock(HubCrmStore.class);
        arpa = mock(ArpaSuiteGateway.class);
        service = new HubCrmCteBindingService(legacy, store, arpa,
                new HubCrmAutoApprovalProperties(true, 30, 3, null, null, null, "Outro"),
                Clock.fixed(TODAY.atTime(10, 0).atZone(ZONE).toInstant(), ZONE));
        when(store.recordCteBinding(any(), any(), anyString(), anyString(), isNull())).thenReturn(true);
        when(legacy.findQuoteChains(any())).thenReturn(List.of());
        when(legacy.findRecentCtes(any())).thenReturn(List.of());
    }

    @Test
    void amarraPelaColetaSemConferirValores() {
        LegacyQuote quote = quote(15910, TODAY.minusDays(3), new BigDecimal("500"));
        when(legacy.findApprovedQuotesSince(any())).thenReturn(List.of(quote));
        when(legacy.findQuoteChains(any())).thenReturn(List.of(new LegacyQuoteChain(15910,
                new LegacyColeta(4801, 15910, "REALIZADA", TODAY.minusDays(2), TODAY.minusDays(1),
                        BigDecimal.ZERO, new BigDecimal("500")), 710816L)));
        // O CT-e saiu com frete bem diferente: pela coleta isso não importa.
        when(legacy.findCtesByIds(List.of(710816L))).thenReturn(List.of(cte(710816, "320401", new BigDecimal("1632.40"))));

        assertThat(service.bindNow()).isEqualTo(1);

        verify(store).recordCteBinding(any(), eq(quote), eq("AMARRADA_COLETA"), contains("Coleta 4801"), isNull());
        verify(store).recordEvent(eq("quote:15910:cte-amarrada"), eq("COTACAO"), eq(15910L), eq("AMARRACAO_CTE"),
                eq("PROCESSADO"), contains("CT-e 320401"), isNull());
        verify(store).upsertQuoteApproval(eq(15910L), anyString(), any(), eq("MANUAL"), eq(4801L),
                eq("REALIZADA"), any(), eq("COLETA"), eq("AMARRADA"), eq(3), anyString());
    }

    @Test
    void coletaECteEntramNaTimelineDoCard() {
        LegacyQuote quote = quote(15919, TODAY.minusDays(3), new BigDecimal("500"));
        when(legacy.findApprovedQuotesSince(any())).thenReturn(List.of(quote));
        when(legacy.findQuoteChains(any())).thenReturn(List.of(new LegacyQuoteChain(15919,
                new LegacyColeta(4805, 15919, "REALIZADA", TODAY.minusDays(2), TODAY.minusDays(1),
                        BigDecimal.ZERO, new BigDecimal("500")), 710816L)));
        when(legacy.findCtesByIds(List.of(710816L)))
                .thenReturn(List.of(cte(710816, "320401", new BigDecimal("1632.40"))));
        when(store.findQuote(15919)).thenReturn(java.util.Optional.of(integration(15919, 2315185L)));

        service.bindNow();

        verify(arpa).addAnnotation(eq(2315185L), contains("CT-e 320401/1"));
        verify(arpa).addAnnotation(eq(2315185L), contains("pela coleta 4805"));
    }

    @Test
    void cotacaoSemCardNaoRecebeAnotacao() {
        LegacyQuote quote = quote(15920, TODAY.minusDays(3), new BigDecimal("100"));
        when(legacy.findApprovedQuotesSince(any())).thenReturn(List.of(quote));
        when(legacy.findRecentCtes(any())).thenReturn(List.of(cte(710905, "320505", new BigDecimal("100"))));
        when(store.findQuote(15920)).thenReturn(java.util.Optional.empty());

        service.bindNow();

        verify(arpa, never()).addAnnotation(anyLong(), anyString());
    }

    @Test
    void semColetaAmarraPelasRegrasDeSempre() {
        LegacyQuote quote = quote(15911, TODAY.minusDays(5), new BigDecimal("100"));
        when(legacy.findApprovedQuotesSince(any())).thenReturn(List.of(quote));
        when(legacy.findRecentCtes(any())).thenReturn(List.of(cte(710900, "320500", new BigDecimal("100"))));

        assertThat(service.bindNow()).isEqualTo(1);

        verify(store).recordCteBinding(any(), eq(quote), eq("AMARRADA_CTE"), anyString(), isNull());
    }

    @Test
    void cteQueNaoCasaDeixaACotacaoSemCte() {
        LegacyQuote quote = quote(15912, TODAY.minusDays(5), new BigDecimal("100"));
        when(legacy.findApprovedQuotesSince(any())).thenReturn(List.of(quote));
        // Frete e NF bem diferentes: não passa nas regras.
        when(legacy.findRecentCtes(any())).thenReturn(List.of(cte(710901, "320501", new BigDecimal("999"))));

        assertThat(service.bindNow()).isZero();

        verify(store, never()).recordCteBinding(any(), any(), anyString(), anyString(), any());
        verify(store).upsertQuoteApproval(eq(15912L), anyString(), any(), anyString(), isNull(), isNull(),
                isNull(), isNull(), eq("SEM_CTE"), anyInt(), isNull());
    }

    @Test
    void coletaEmAndamentoAparaceNoAcompanhamento() {
        LegacyQuote quote = quote(15913, TODAY.minusDays(4), new BigDecimal("100"));
        when(legacy.findApprovedQuotesSince(any())).thenReturn(List.of(quote));
        when(legacy.findQuoteChains(any())).thenReturn(List.of(new LegacyQuoteChain(15913,
                new LegacyColeta(4802, 15913, "EM VIAGEM", TODAY.minusDays(4), null, BigDecimal.ZERO,
                        new BigDecimal("100")), null)));

        assertThat(service.bindNow()).isZero();

        verify(store).upsertQuoteApproval(eq(15913L), anyString(), any(), anyString(), eq(4802L), eq("EM VIAGEM"),
                isNull(), isNull(), eq("SEM_CTE"), eq(4), contains("em andamento"));
    }

    @Test
    void cteJaAmarradoAOutraCotacaoVaiParaRevisao() {
        LegacyQuote quote = quote(15914, TODAY.minusDays(5), new BigDecimal("100"));
        when(legacy.findApprovedQuotesSince(any())).thenReturn(List.of(quote));
        when(legacy.findRecentCtes(any())).thenReturn(List.of(cte(710902, "320502", new BigDecimal("100"))));
        when(store.recordCteBinding(any(), any(), anyString(), anyString(), isNull())).thenReturn(false);

        assertThat(service.bindNow()).isZero();

        verify(store).upsertQuoteApproval(eq(15914L), anyString(), any(), anyString(), isNull(), isNull(),
                isNull(), isNull(), eq("REVISAO"), anyInt(), contains("já amarrado"));
        verify(store).recordEvent(eq("cte:710902:amarracao"), eq("CTE"), eq(710902L), eq("AMARRACAO_CTE"),
                eq("REVISAO"), isNull(), contains("já amarrado"));
    }

    @Test
    void cotacaoAprovadaPeloHubFicaComOrigemHub() {
        LegacyQuote quote = quote(15915, TODAY.minusDays(2), new BigDecimal("100"));
        when(legacy.findApprovedQuotesSince(any())).thenReturn(List.of(quote));
        when(store.changedByHub(15915)).thenReturn(true);
        when(store.quotesApprovedByCte()).thenReturn(new java.util.HashSet<>(Set.of(15915L)));
        when(store.boundCte(15915)).thenReturn(java.util.Optional.of(
                new HubCrmStore.CteRef(710903L, "320503", "1", TODAY.minusDays(1))));

        assertThat(service.bindNow()).isZero();

        verify(store).upsertQuoteApproval(eq(15915L), anyString(), any(), eq("HUB"), isNull(), isNull(),
                any(), eq("CTE"), eq("AMARRADA"), eq(2), isNull());
        verify(store, never()).recordCteBinding(any(), any(), anyString(), anyString(), any());
    }

    @Test
    void falhaEmUmaCotacaoNaoDerrubaAsOutras() {
        LegacyQuote ruim = quote(15916, TODAY.minusDays(2), new BigDecimal("100"));
        LegacyQuote boa = quote(15917, TODAY.minusDays(2), new BigDecimal("100"));
        when(legacy.findApprovedQuotesSince(any())).thenReturn(List.of(ruim, boa));
        when(store.changedByHub(15916)).thenThrow(new IllegalStateException("banco fora"));

        service.bindNow();

        verify(store).recordEvent(eq("quote:15916:amarracao-falha"), eq("COTACAO"), eq(15916L),
                eq("AMARRACAO_CTE"), eq("ERRO"), isNull(), contains("banco fora"));
        verify(store).upsertQuoteApproval(eq(15917L), anyString(), any(), anyString(), isNull(), isNull(),
                isNull(), isNull(), eq("SEM_CTE"), anyInt(), isNull());
    }

    @Test
    void amarraNoMaximoACadaQuinzeMinutos() {
        when(legacy.findApprovedQuotesSince(any())).thenReturn(List.of(quote(15918, TODAY.minusDays(2),
                new BigDecimal("100"))));

        service.bindApprovedQuotes();
        service.bindApprovedQuotes();

        verify(legacy, times(1)).findApprovedQuotesSince(any());
    }

    private static br.com.salome.core.domain.hubcrm.QuoteIntegration integration(long quoteId, Long dealId) {
        return new br.com.salome.core.domain.hubcrm.QuoteIntegration(quoteId, PAGADOR, "APROVADA", dealId,
                1L, 2L, 3L, BigDecimal.TEN, "hash", "INTEGRADO", "ENVIADO");
    }

    private static LegacyCte cte(long id, String number, BigDecimal freight) {
        BigDecimal zero = BigDecimal.ZERO;
        return new LegacyCte(id, number, "1", "chave" + id, TODAY.minusDays(1), "10:00", "Emitente (CIF)",
                PAGADOR, "99999999999999", PAGADOR, BigDecimal.TEN, BigDecimal.TEN, 1, freight,
                new LegacyCte.Charges(freight, zero, zero, zero, zero, zero, zero, zero, zero, zero, zero));
    }

    /** Cotação já APROVADA, com peso e NF iguais aos do CT-e do teste. */
    private static LegacyQuote quote(long id, LocalDate approvedAt, BigDecimal freight) {
        BigDecimal zero = BigDecimal.ZERO;
        return new LegacyQuote(id, approvedAt.minusDays(1), "10:00", "FERNANDA", "APROVADA",
                LocalDateTime.of(approvedAt, java.time.LocalTime.NOON), "Emitente (CIF)", PAGADOR, "REMETENTE",
                "X", "99999999999999", "DESTINATARIO", "Y", PAGADOR, "REMETENTE", "", "", "DIVERSOS", 1,
                BigDecimal.TEN, BigDecimal.TEN, zero, freight, zero, zero, zero, zero, zero, zero, zero, zero,
                zero, zero, freight, "", Map.of());
    }
}
