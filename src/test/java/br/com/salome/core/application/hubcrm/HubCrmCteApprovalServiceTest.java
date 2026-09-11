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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.salome.core.domain.hubcrm.LegacyCte;
import br.com.salome.core.domain.hubcrm.LegacyQuote;
import br.com.salome.core.infrastructure.hubcrm.HubCrmAutoApprovalProperties;
import br.com.salome.core.infrastructure.hubcrm.HubCrmStore;
import br.com.salome.core.infrastructure.legacy.hubcrm.LegacyQuoteApprovalWriter;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HubCrmCteApprovalServiceTest {
    private static final String PAGADOR = "12345678000190";
    private HubCrmLegacyRepository legacy;
    private HubCrmStore store;
    private LegacyQuoteApprovalWriter writer;
    private HubCrmCteApprovalService service;

    @BeforeEach
    void setUp() {
        legacy = mock(HubCrmLegacyRepository.class);
        store = mock(HubCrmStore.class);
        writer = mock(LegacyQuoteApprovalWriter.class);
        when(store.cteMatchIdsSince(any())).thenReturn(Set.of());
        when(store.quotesApprovedByCte()).thenReturn(Set.of());
        Clock clock = Clock.fixed(LocalDate.of(2026, 9, 11).atTime(12, 0).atZone(ZoneId.systemDefault())
                .toInstant(), ZoneId.systemDefault());
        service = new HubCrmCteApprovalService(legacy, store, writer,
                new HubCrmAutoApprovalProperties(true, 30, 3, "jdbc", "crm_api", "", ""), clock);
    }

    @Test
    void aprovaCotacaoAbertaQuandoCteCasa() {
        LegacyQuote quote = quote(1, "ABERTA", "FERNANDA");
        LegacyCte cte = cte(100);
        when(legacy.findRecentCtes(LocalDate.of(2026, 9, 8))).thenReturn(List.of(cte));
        when(legacy.findApprovableQuotes(LocalDate.of(2026, 8, 9))).thenReturn(List.of(quote));
        when(writer.approve(eq(quote), eq(cte), any())).thenReturn(true);

        var result = service.approveFromCtes();

        assertThat(result.approved()).isEqualTo(1);
        verify(store).recordCteMatch(eq(cte), eq(quote), eq("APROVADA_AUTO"), anyString(), any());
        verify(store).recordEvent(eq("cte:100:aprovacao"), eq("CTE"), eq(100L), eq("APROVACAO_CTE"),
                eq("PROCESSADO"), contains("Cotação 1"), isNull());
    }

    @Test
    void carlosAprovaSoNoLegadoERegistraIsso() {
        LegacyQuote quote = quote(2, "ABERTA", "CARLOS");
        LegacyCte cte = cte(101);
        when(legacy.findRecentCtes(any())).thenReturn(List.of(cte));
        when(legacy.findApprovableQuotes(any())).thenReturn(List.of(quote));
        when(writer.approve(eq(quote), eq(cte), any())).thenReturn(true);

        service.approveFromCtes();

        verify(store).recordEvent(anyString(), anyString(), anyLong(), anyString(), eq("PROCESSADO"),
                contains("fora do ArpaSuite"), isNull());
    }

    @Test
    void statusMudadoNoMeioViraConcorrencia() {
        LegacyQuote quote = quote(3, "ABERTA", "JACI");
        LegacyCte cte = cte(102);
        when(legacy.findRecentCtes(any())).thenReturn(List.of(cte));
        when(legacy.findApprovableQuotes(any())).thenReturn(List.of(quote));
        when(writer.approve(eq(quote), eq(cte), any())).thenReturn(false);

        var result = service.approveFromCtes();

        assertThat(result.conflicts()).isEqualTo(1);
        verify(store).recordCteMatch(eq(cte), eq(quote), eq("CONCORRENCIA"), anyString(), anyString());
    }

    @Test
    void cteJaTratadoECotacaoJaUsadaSaoIgnorados() {
        LegacyQuote quote = quote(4, "ABERTA", "FERNANDA");
        when(store.cteMatchIdsSince(any())).thenReturn(Set.of(103L));
        when(store.quotesApprovedByCte()).thenReturn(Set.of(4L));
        when(legacy.findRecentCtes(any())).thenReturn(List.of(cte(103), cte(104)));
        when(legacy.findApprovableQuotes(any())).thenReturn(List.of(quote));

        var result = service.approveFromCtes();

        assertThat(result.evaluated()).isEqualTo(1);
        verify(writer, never()).approve(any(), any(), any());
    }

    @Test
    void mesmaCotacaoNaoEAprovadaPorDoisCtesNoMesmoCiclo() {
        LegacyQuote quote = quote(5, "ABERTA", "FERNANDA");
        when(legacy.findRecentCtes(any())).thenReturn(List.of(cte(105), cte(106)));
        when(legacy.findApprovableQuotes(any())).thenReturn(List.of(quote));
        when(writer.approve(any(), any(), any())).thenReturn(true);

        var result = service.approveFromCtes();

        assertThat(result.approved()).isEqualTo(1);
    }

    @Test
    void falhaEmUmCteNaoInterrompeOsDemais() {
        LegacyQuote q1 = quote(6, "ABERTA", "FERNANDA");
        LegacyQuote q2 = new LegacyQuote(7, q1.createdDate(), q1.createdTime(), "JACI", "ABERTA", q1.statusAt(),
                q1.paymentType(), "55555555000155", q1.senderName(), q1.senderCity(), q1.recipientCnpj(),
                q1.recipientName(), q1.recipientCity(), "55555555000155", q1.payerName(), q1.payerPhone(),
                q1.payerEmail(), q1.cargoType(), q1.volumes(), q1.weight(), q1.invoiceValue(), q1.cubage(),
                q1.freightWeight(), q1.freightValue(), q1.toll(), q1.pickup(), q1.delivery(), q1.dispatch(),
                q1.gris(), q1.redelivery(), q1.icms(), q1.discount(), q1.addition(), q1.totalFreight(),
                q1.approvalContact(), Map.of());
        LegacyCte c1 = cte(107);
        LegacyCte c2 = new LegacyCte(108, "6000", "1", "k", LocalDate.of(2026, 9, 10), "09:00", "CIF",
                "55555555000155", "98765432000110", "55555555000155", new BigDecimal("50"),
                new BigDecimal("1000"), 2, new BigDecimal("150.00"));
        when(legacy.findRecentCtes(any())).thenReturn(List.of(c1, c2));
        when(legacy.findApprovableQuotes(any())).thenReturn(List.of(q1, q2));
        when(writer.approve(eq(q1), eq(c1), any())).thenThrow(new IllegalStateException("lock wait timeout"));
        when(writer.approve(eq(q2), eq(c2), any())).thenReturn(true);

        var result = service.approveFromCtes();

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.approved()).isEqualTo(1);
        verify(store, never()).recordCteMatch(eq(c1), any(), anyString(), any(), any());
    }

    private static LegacyCte cte(long id) {
        return new LegacyCte(id, String.valueOf(5000 + id), "1", "chave" + id, LocalDate.of(2026, 9, 10),
                "09:00", "CIF", PAGADOR, "98765432000110", PAGADOR, new BigDecimal("50"),
                new BigDecimal("1000"), 2, new BigDecimal("150.00"));
    }

    private static LegacyQuote quote(long id, String status, String responsible) {
        BigDecimal zero = BigDecimal.ZERO;
        LocalDate created = LocalDate.of(2026, 9, 9);
        return new LegacyQuote(id, created, "10:30", responsible, status,
                LocalDateTime.of(created, LocalTime.of(11, 0)), "Emitente (CIF)",
                PAGADOR, "REMETENTE LTDA", "SÃO PAULO-SP", "98765432000110", "DESTINATÁRIO LTDA",
                "CAMPINAS-SP", PAGADOR, "REMETENTE LTDA", "11999999999", "vendas@remetente.com",
                "DIVERSOS", 2, new BigDecimal("50"), new BigDecimal("1000"), zero, zero, zero, zero, zero,
                zero, zero, zero, zero, zero, zero, zero, new BigDecimal("150.00"), "Maria", Map.of());
    }
}
