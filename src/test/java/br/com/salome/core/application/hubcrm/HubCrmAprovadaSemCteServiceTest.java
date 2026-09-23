package br.com.salome.core.application.hubcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import br.com.salome.core.domain.hubcrm.LegacyQuote;
import br.com.salome.core.domain.hubcrm.LegacyQuoteChain;
import br.com.salome.core.infrastructure.hubcrm.HubCrmPosAprovacaoProperties;
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
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HubCrmAprovadaSemCteServiceTest {
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 23);
    private static final LocalDate ATIVACAO = LocalDate.of(2026, 9, 1);
    private HubCrmLegacyRepository legacy;
    private HubCrmStore store;
    private LegacyQuoteApprovalWriter writer;
    private HubCrmAprovadaSemCteService service;

    @BeforeEach
    void setUp() {
        legacy = mock(HubCrmLegacyRepository.class);
        store = mock(HubCrmStore.class);
        writer = mock(LegacyQuoteApprovalWriter.class);
        service = new HubCrmAprovadaSemCteService(legacy, store, writer,
                new HubCrmPosAprovacaoProperties(true, true, 10, null, null),
                Clock.fixed(TODAY.atTime(10, 0).atZone(ZONE).toInstant(), ZONE));
        when(store.textCheckpoint(HubCrmPosAprovacaoProperties.ACTIVATION_CHECKPOINT))
                .thenReturn(Optional.of(ATIVACAO.toString()));
        when(writer.reject(any(), anyString(), anyString(), any())).thenReturn(true);
    }

    @Test
    void aprovadaHaDezDiasSemCteVoltaParaNaoAprovadaPorArrependimento() {
        LegacyQuote parada = quote(15900, "FERNANDA", TODAY.minusDays(12));
        approved(parada);

        assertThat(service.rejectApprovedWithoutCte()).isEqualTo(1);

        verify(writer).reject(eq(parada), eq("naoAprovacaoArrependimentoFrete"),
                contains("emitindo o CT-e, reaprove"), any());
        verify(store).recordEvent(eq("quote:15900:aprovada-sem-cte"), eq("COTACAO"), eq(15900L),
                eq("APROVADA_SEM_CTE"), eq("PROCESSADO"), contains("Arrependimento do frete"), isNull());
        verify(store).markQuoteApprovalStatus(eq(15900L), eq("REPROVADA_SEM_CTE"), anyString());
    }

    @Test
    void reprovaQualquerResponsavel() {
        LegacyQuote carlos = quote(15901, "CARLOS", TODAY.minusDays(30));
        approved(carlos);

        assertThat(service.rejectApprovedWithoutCte()).isEqualTo(1);
    }

    @Test
    void coletaEmAndamentoSeguraATriagem() {
        LegacyQuote comColeta = quote(15902, "JACI", TODAY.minusDays(15));
        approved(comColeta);
        chains(chain(15902, 4801, "EM VIAGEM", null));

        assertThat(service.rejectApprovedWithoutCte()).isZero();

        verify(writer, never()).reject(any(), anyString(), anyString(), any());
    }

    @Test
    void coletaComCteNaoReprova() {
        LegacyQuote comCte = quote(15903, "JACI", TODAY.minusDays(15));
        approved(comCte);
        chains(chain(15903, 4802, "REALIZADA", 710816L));

        assertThat(service.rejectApprovedWithoutCte()).isZero();
    }

    @Test
    void cteJaAmarradoOuEmRevisaoNaoReprovam() {
        LegacyQuote amarrada = quote(15904, "FERNANDA", TODAY.minusDays(15));
        LegacyQuote revisao = quote(15905, "FERNANDA", TODAY.minusDays(15));
        approved(amarrada, revisao);
        when(store.quotesApprovedByCte()).thenReturn(Set.of(15904L));
        when(store.quotesInReview()).thenReturn(Set.of(15905L));

        assertThat(service.rejectApprovedWithoutCte()).isZero();
    }

    @Test
    void aprovadaHaMenosDeDezDiasNaoEntra() {
        approved(quote(15906, "FERNANDA", TODAY.minusDays(9)));

        assertThat(service.rejectApprovedWithoutCte()).isZero();
    }

    @Test
    void aprovadaAntesDaAtivacaoNaoEntra() {
        // A leitura já corta pela ativação; aqui a cotação antiga chega mesmo assim e é ignorada.
        approved(quote(15907, "FERNANDA", ATIVACAO.minusDays(5)));

        assertThat(service.rejectApprovedWithoutCte()).isEqualTo(1);
        verify(legacy).findApprovedQuotesSince(ATIVACAO);
    }

    @Test
    void ativacaoEGravadaNaPrimeiraExecucao() {
        when(store.textCheckpoint(HubCrmPosAprovacaoProperties.ACTIVATION_CHECKPOINT)).thenReturn(Optional.empty());
        approved();

        service.rejectApprovedWithoutCte();

        verify(store).setTextCheckpoint(HubCrmPosAprovacaoProperties.ACTIVATION_CHECKPOINT, TODAY.toString());
    }

    @Test
    void statusMudouAntesDaGravacaoVaiParaRevisao() {
        LegacyQuote parada = quote(15908, "FERNANDA", TODAY.minusDays(12));
        approved(parada);
        when(writer.reject(any(), anyString(), anyString(), any())).thenReturn(false);

        assertThat(service.rejectApprovedWithoutCte()).isZero();

        verify(store).recordEvent(eq("quote:15908:aprovada-sem-cte-falha"), eq("COTACAO"), eq(15908L),
                eq("APROVADA_SEM_CTE"), eq("REVISAO"), isNull(), contains("mudou antes da gravação"));
    }

    @Test
    void verificaNoMaximoUmaVezPorHora() {
        approved(quote(15909, "FERNANDA", TODAY.minusDays(12)));

        service.rejectApprovedWithoutCte();
        service.rejectApprovedWithoutCte();

        verify(legacy, times(1)).findApprovedQuotesSince(any());
    }

    private void approved(LegacyQuote... quotes) {
        when(legacy.findApprovedQuotesSince(any())).thenReturn(List.of(quotes));
        when(legacy.findQuoteChains(any())).thenReturn(List.of());
    }

    private void chains(LegacyQuoteChain... chains) {
        when(legacy.findQuoteChains(any())).thenReturn(List.of(chains));
    }

    private static LegacyQuoteChain chain(long quoteId, long coletaId, String status, Long cteId) {
        return new LegacyQuoteChain(quoteId, new LegacyColeta(coletaId, quoteId, status, TODAY.minusDays(14),
                null, BigDecimal.ZERO, BigDecimal.TEN), cteId);
    }

    /** Cotação APROVADA, com a data da aprovação em {@code approvedAt}. */
    private static LegacyQuote quote(long id, String responsible, LocalDate approvedAt) {
        BigDecimal zero = BigDecimal.ZERO;
        return new LegacyQuote(id, approvedAt.minusDays(1), "10:00", responsible, "APROVADA",
                LocalDateTime.of(approvedAt, java.time.LocalTime.NOON), "Emitente (CIF)", "A", "A", "X", "B", "B",
                "Y", "A", "A", "", "", "DIVERSOS", 1, BigDecimal.TEN, BigDecimal.TEN, zero, zero, zero, zero,
                zero, zero, zero, zero, zero, zero, zero, zero, new BigDecimal("100"), "", Map.of());
    }
}
