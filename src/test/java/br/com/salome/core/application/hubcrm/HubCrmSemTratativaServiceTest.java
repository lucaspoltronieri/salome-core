package br.com.salome.core.application.hubcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

import br.com.salome.core.domain.hubcrm.LegacyQuote;
import br.com.salome.core.domain.hubcrm.LossReason;
import br.com.salome.core.domain.hubcrm.QuoteIntegration;
import br.com.salome.core.infrastructure.hubcrm.HubCrmSemTratativaProperties;
import br.com.salome.core.infrastructure.hubcrm.HubCrmStore;
import br.com.salome.core.infrastructure.legacy.hubcrm.LegacyQuoteApprovalWriter;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HubCrmSemTratativaServiceTest {
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 23);
    private HubCrmLegacyRepository legacy;
    private HubCrmStore store;
    private ArpaSuiteGateway arpa;
    private LegacyQuoteApprovalWriter writer;
    private HubCrmSemTratativaService service;

    @BeforeEach
    void setUp() {
        legacy = mock(HubCrmLegacyRepository.class);
        store = mock(HubCrmStore.class);
        arpa = mock(ArpaSuiteGateway.class);
        writer = mock(LegacyQuoteApprovalWriter.class);
        service = new HubCrmSemTratativaService(legacy, store, arpa, writer,
                new HubCrmSemTratativaProperties(true, 10, 317833, null),
                Clock.fixed(TODAY.atTime(10, 0).atZone(ZONE).toInstant(), ZONE));
        when(writer.reject(any(), anyString(), anyString(), any())).thenReturn(true);
        when(store.findQuote(anyLong())).thenReturn(Optional.empty());
    }

    private void legado(LegacyQuote... quotes) {
        when(legacy.findApprovableQuotes(any())).thenReturn(List.of(quotes));
    }

    private void card(long quoteId, long dealId, String status) {
        when(store.findQuote(quoteId)).thenReturn(Optional.of(new QuoteIntegration(quoteId, "A", "ABERTA", dealId,
                1L, 2L, 3L, BigDecimal.TEN, "hash", "INTEGRADO", "PENDENTE")));
        when(arpa.findDealStatus(dealId)).thenReturn(status == null ? Optional.empty() : Optional.of(status));
    }

    @Test
    void cotacaoSemCardNoArpaTambemEBaixada() {
        LegacyQuote carlos = quote(15731, "CARLOS", TODAY.minusDays(22));
        legado(carlos);

        assertThat(service.closeStaleQuotes()).isEqualTo(1);

        verify(writer).reject(eq(carlos), eq("naoAprovacaoPreco"),
                eq("Sem tratativa do comercial, baixado pelo legado"), any());
        verify(store).recordEvent(eq("quote:15731:sem-tratativa"), eq("COTACAO"), eq(15731L), eq("SEM_TRATATIVA"),
                eq("PROCESSADO"), contains("sem card"), isNull());
    }

    @Test
    void cardApagadoNaoImpedeABaixa() {
        LegacyQuote quote = quote(15733, "FERNANDA", TODAY.minusDays(22));
        legado(quote);
        card(15733, 555, null);

        assertThat(service.closeStaleQuotes()).isEqualTo(1);

        verify(writer).reject(eq(quote), eq("naoAprovacaoPreco"), anyString(), any());
    }

    @Test
    void cardJaPerdidoGravaOMesmoMotivoNoLegadoENaoMexeNoArpa() {
        LegacyQuote quote = quote(15754, "FERNANDA", TODAY.minusDays(20));
        legado(quote);
        card(15754, 2267535, "lost");
        when(arpa.findDealLostReason(2267535)).thenReturn(Optional.of(LossReason.BAD_DEADLINE_WINDOW));

        assertThat(service.closeStaleQuotes()).isEqualTo(1);

        verify(writer).reject(eq(quote), eq("naoAprovacaoPrazo"), contains("Prazo e janela ruins"), any());
        verify(store).recordEvent(eq("quote:15754:sem-tratativa"), eq("COTACAO"), eq(15754L),
                eq("SEM_TRATATIVA_CARD_PERDIDO"), eq("PROCESSADO"), anyString(), isNull());
    }

    @Test
    void cardGanhoFicaParaConferenciaManual() {
        LegacyQuote quote = quote(15783, "FERNANDA", TODAY.minusDays(13));
        legado(quote);
        card(15783, 2292031, "won");

        assertThat(service.closeStaleQuotes()).isZero();

        verify(writer, never()).reject(any(), anyString(), anyString(), any());
        verify(store).recordEvent(eq("quote:15783:sem-tratativa-revisao"), eq("COTACAO"), eq(15783L),
                eq("SEM_TRATATIVA"), eq("REVISAO"), isNull(), contains("ganho"));
    }

    @Test
    void cardAbertoComAtividadeNaoEBaixadoEOPrazoCurtoTambemNao() {
        LegacyQuote comAtividade = quote(15800, "JACI", TODAY.minusDays(14));
        LegacyQuote recente = quote(15900, "JACI", TODAY.minusDays(9));
        legado(comAtividade, recente);
        card(15800, 777, "open");
        when(arpa.hasDealActivitySince(777, comAtividade.createdDate())).thenReturn(true);

        assertThat(service.closeStaleQuotes()).isZero();

        verify(writer, never()).reject(any(), anyString(), anyString(), any());
    }

    @Test
    void verificaNoMaximoUmaVezPorHora() {
        legado(quote(15731, "CARLOS", TODAY.minusDays(22)));

        service.closeStaleQuotes();
        service.closeStaleQuotes();

        verify(legacy, times(1)).findApprovableQuotes(any());
    }

    private static LegacyQuote quote(long id, String responsible, LocalDate created) {
        BigDecimal zero = BigDecimal.ZERO;
        return new LegacyQuote(id, created, "10:00", responsible, "ABERTA",
                LocalDateTime.of(created, java.time.LocalTime.NOON), "Emitente (CIF)", "A", "A", "X", "B", "B", "Y",
                "A", "A", "", "", "DIVERSOS", 1, BigDecimal.TEN, BigDecimal.TEN, zero, zero, zero, zero, zero, zero,
                zero, zero, zero, zero, zero, zero, new BigDecimal("100"), "", Map.of());
    }
}
