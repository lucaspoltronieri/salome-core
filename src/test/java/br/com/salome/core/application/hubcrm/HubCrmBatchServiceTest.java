package br.com.salome.core.application.hubcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

class HubCrmBatchServiceTest {
    private static final LocalDate CUTOFF = LocalDate.of(2026, 8, 31);
    private HubCrmLegacyRepository legacy;
    private HubCrmStore store;
    private LegacyQuoteApprovalWriter writer;
    private HubCrmBatchService service;

    // Com CT-e (pagador A): aprova. Sem CT-e antes do corte: não aprova por preço.
    // Sem CT-e depois do corte: fica. NÃO APROVADA sem CT-e: fica.
    private final LegacyQuote comCte = quote(1, "ABERTA", "FERNANDA", LocalDate.of(2026, 5, 10), "A");
    private final LegacyQuote antigaSemCte = quote(2, "ABERTA", "CARLOS", LocalDate.of(2023, 3, 1), "B");
    private final LegacyQuote recenteSemCte = quote(3, "ABERTA", "JACI", LocalDate.of(2026, 9, 2), "C");
    private final LegacyQuote jaPerdida = quote(4, "NÃO APROVADA", "CARLOS", LocalDate.of(2025, 1, 1), "D");
    private final LegacyCte cte = new LegacyCte(900, "5000", "1", "k", LocalDate.of(2026, 5, 12), "10:00", "CIF",
            "A", "X", "A", new BigDecimal("50"), new BigDecimal("1000"), 2, new BigDecimal("150.00"));

    @BeforeEach
    void setUp() {
        legacy = mock(HubCrmLegacyRepository.class);
        store = mock(HubCrmStore.class);
        writer = mock(LegacyQuoteApprovalWriter.class);
        when(store.cteMatchIdsSince(any())).thenReturn(Set.of());
        when(store.quotesApprovedByCte()).thenReturn(Set.of());
        when(legacy.findApprovableQuotes(HubCrmBatchService.QUOTES_SINCE))
                .thenReturn(List.of(comCte, antigaSemCte, recenteSemCte, jaPerdida));
        when(legacy.findRecentCtes(HubCrmBatchService.CTE_SINCE)).thenReturn(List.of(cte));
        Clock clock = Clock.fixed(LocalDate.of(2026, 9, 11).atTime(15, 0).atZone(ZoneId.systemDefault())
                .toInstant(), ZoneId.systemDefault());
        service = new HubCrmBatchService(legacy, store, writer,
                new HubCrmAutoApprovalProperties(true, 30, 3, "jdbc", "crm_api", "", "Outro"), clock);
    }

    @Test
    void simulacaoNaoGravaNadaEContaAsAcoes() {
        service.run(false, CUTOFF);

        var state = service.status();
        assertThat(state.running()).isFalse();
        assertThat(state.totals()).containsEntry("APROVAR:SIMULADO", 1).containsEntry("NAO_APROVAR:SIMULADO", 1);
        assertThat(state.items()).extracting(HubCrmBatchService.BatchItem::cotacao).containsExactly(1L, 2L);
        verify(writer, never()).approve(any(), any(), any());
        verify(writer, never()).rejectForPrice(any(), anyString(), any());
        verify(store, never()).recordCteMatch(any(), any(), anyString(), any(), any());
    }

    @Test
    void execucaoAprovaComCteENaoAprovaAntigaSemCte() {
        when(writer.approve(eq(comCte), eq(cte), any())).thenReturn(true);
        when(writer.rejectForPrice(eq(antigaSemCte), anyString(), any())).thenReturn(true);

        service.run(true, CUTOFF);

        verify(writer).approve(eq(comCte), eq(cte), any());
        verify(store).recordCteMatch(eq(cte), eq(comCte), eq("APROVADA_AUTO"), anyString(), any());
        verify(store).recordEvent(eq("quote:1:lote:aprovada"), eq("COTACAO"), eq(1L), eq("LOTE_APROVADA"),
                eq("PROCESSADO"), anyString(), any());
        verify(store).recordEvent(eq("quote:2:lote:nao-aprovada"), eq("COTACAO"), eq(2L), eq("LOTE_NAO_APROVADA"),
                eq("PROCESSADO"), anyString(), any());
        verify(writer).rejectForPrice(eq(antigaSemCte), anyString(), any());
        verify(writer, never()).rejectForPrice(eq(recenteSemCte), anyString(), any());
        verify(writer, never()).rejectForPrice(eq(jaPerdida), anyString(), any());
        verify(writer, never()).rejectForPrice(eq(comCte), anyString(), any());
        assertThat(service.status().totals())
                .containsEntry("APROVAR:APROVADA", 1).containsEntry("NAO_APROVAR:NAO_APROVADA", 1);
    }

    @Test
    void cotacoesEmpatadasNoMesmoCteNaoViramPerdidas() {
        LegacyQuote gemeaA = quote(10, "ABERTA", "CARLOS", LocalDate.of(2026, 5, 10), "A");
        LegacyQuote gemeaB = quote(11, "ABERTA", "CARLOS", LocalDate.of(2026, 5, 10), "A");
        when(legacy.findApprovableQuotes(HubCrmBatchService.QUOTES_SINCE)).thenReturn(List.of(gemeaA, gemeaB));

        service.run(true, CUTOFF);

        verify(writer, never()).approve(any(), any(), any());
        verify(writer, never()).rejectForPrice(any(), anyString(), any());
        assertThat(service.status().totals()).containsEntry("AMBIGUO:REVISAO", 1);
    }

    @Test
    void falhaAoGravarUmaCotacaoNaoParaOLote() {
        LegacyQuote outraAntiga = quote(5, "ABERTA", "MOISES", LocalDate.of(2022, 1, 1), "E");
        when(legacy.findApprovableQuotes(HubCrmBatchService.QUOTES_SINCE))
                .thenReturn(List.of(antigaSemCte, outraAntiga));
        when(legacy.findRecentCtes(HubCrmBatchService.CTE_SINCE)).thenReturn(List.of());
        when(writer.rejectForPrice(eq(antigaSemCte), anyString(), any()))
                .thenThrow(new IllegalStateException("lock wait timeout"));
        when(writer.rejectForPrice(eq(outraAntiga), anyString(), any())).thenReturn(true);

        service.run(true, CUTOFF);

        assertThat(service.status().error()).isNull();
        assertThat(service.status().totals())
                .containsEntry("NAO_APROVAR:ERRO", 1).containsEntry("NAO_APROVAR:NAO_APROVADA", 1);
    }

    private static LegacyQuote quote(long id, String status, String responsible, LocalDate created, String payer) {
        BigDecimal zero = BigDecimal.ZERO;
        return new LegacyQuote(id, created, "10:30", responsible, status,
                LocalDateTime.of(created, LocalTime.of(11, 0)), "Emitente (CIF)", payer, "REMETENTE", "SP",
                "X", "DESTINATARIO", "CAMPINAS", payer, "PAGADOR", "", "", "DIVERSOS", 2,
                new BigDecimal("50"), new BigDecimal("1000"), zero, zero, zero, zero, zero, zero, zero, zero, zero,
                zero, zero, zero, new BigDecimal("150.00"), "", Map.of());
    }
}
