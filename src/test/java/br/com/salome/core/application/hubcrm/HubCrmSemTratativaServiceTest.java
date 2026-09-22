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
import br.com.salome.core.infrastructure.hubcrm.HubCrmSemTratativaProperties;
import br.com.salome.core.infrastructure.hubcrm.HubCrmStore;
import br.com.salome.core.infrastructure.legacy.hubcrm.LegacyQuoteApprovalWriter;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HubCrmSemTratativaServiceTest {
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 22);
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
        when(arpa.findDealStatus(anyLong())).thenReturn(Optional.of("open"));
        when(writer.reject(any(), anyString(), anyString(), any())).thenReturn(true);
    }

    private void open(LegacyQuote... quotes) {
        Map<Long, Long> deals = new LinkedHashMap<>();
        for (LegacyQuote quote : quotes) deals.put(quote.id(), 9000 + quote.id());
        when(store.openQuoteDeals()).thenReturn(deals);
        when(legacy.findQuotesByIds(any())).thenReturn(List.of(quotes));
    }

    @Test
    void propostaComMaisDeDezDiasSemAtividadeEBaixadaPorPrecoNoLegado() {
        LegacyQuote antiga = quote(15769, "FERNANDA", TODAY.minusDays(14));
        open(antiga);

        assertThat(service.closeStaleQuotes()).isEqualTo(1);

        verify(writer).reject(eq(antiga), eq("naoAprovacaoPreco"),
                eq("Sem tratativa do comercial, baixado pelo legado"), any());
        verify(store).recordEvent(eq("quote:15769:sem-tratativa"), eq("COTACAO"), eq(15769L), eq("SEM_TRATATIVA"),
                eq("PROCESSADO"), contains("317833"), isNull());
    }

    @Test
    void atividadeNoCardSeguraABaixa() {
        LegacyQuote antiga = quote(15769, "FERNANDA", TODAY.minusDays(14));
        open(antiga);
        when(arpa.hasDealActivitySince(24769, antiga.createdDate())).thenReturn(true);

        service.closeStaleQuotes();

        verify(writer, never()).reject(any(), anyString(), anyString(), any());
    }

    @Test
    void menosDeDezDiasCardFechadoOuForaDoArpaNaoBaixam() {
        LegacyQuote nova = quote(15870, "JACI", TODAY.minusDays(9));
        LegacyQuote cardGanho = quote(15771, "JACI", TODAY.minusDays(20));
        LegacyQuote carlos = quote(15772, "CARLOS", TODAY.minusDays(20));
        open(nova, cardGanho, carlos);
        when(arpa.findDealStatus(24771)).thenReturn(Optional.of("won"));

        assertThat(service.closeStaleQuotes()).isZero();

        verify(writer, never()).reject(any(), anyString(), anyString(), any());
    }

    @Test
    void verificaNoMaximoUmaVezPorHora() {
        open(quote(15769, "FERNANDA", TODAY.minusDays(14)));

        service.closeStaleQuotes();
        service.closeStaleQuotes();

        verify(store, times(1)).openQuoteDeals();
    }

    private static LegacyQuote quote(long id, String responsible, LocalDate created) {
        BigDecimal zero = BigDecimal.ZERO;
        return new LegacyQuote(id, created, "10:00", responsible, "ABERTA", LocalDateTime.of(created, java.time.LocalTime.NOON),
                "Emitente (CIF)", "A", "A", "X", "B", "B", "Y", "A", "A", "", "", "DIVERSOS", 1, BigDecimal.TEN,
                BigDecimal.TEN, zero, zero, zero, zero, zero, zero, zero, zero, zero, zero, zero, zero,
                new BigDecimal("100"), "", Map.of());
    }
}
