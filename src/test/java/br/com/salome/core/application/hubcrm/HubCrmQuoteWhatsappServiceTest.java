package br.com.salome.core.application.hubcrm;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.salome.core.domain.hubcrm.LegacyQuote;
import br.com.salome.core.infrastructure.hubcrm.HubCrmStore;
import br.com.salome.core.infrastructure.hubcrm.HubCrmWhatsappProperties;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpClientErrorException;

class HubCrmQuoteWhatsappServiceTest {
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 16);
    private HubCrmStore store;
    private ArpaSuiteGateway arpa;
    private HubCrmQuoteWhatsappService service;

    @BeforeEach
    void setUp() {
        store = mock(HubCrmStore.class);
        arpa = mock(ArpaSuiteGateway.class);
        HubCrmMediaSigner signer = mock(HubCrmMediaSigner.class);
        when(signer.quoteUrl(anyLong())).thenReturn(new HubCrmMediaSigner.SignedUrl("https://hub/pdf", 1, "s"));
        when(arpa.hasWhatsappChannel()).thenReturn(true);
        service = new HubCrmQuoteWhatsappService(store, arpa, signer,
                new HubCrmWhatsappProperties(302790, false, 15840, 3),
                Clock.fixed(TODAY.atTime(10, 0).atZone(ZONE).toInstant(), ZONE));
    }

    @Test
    void janelaAbertaEnviaOPdfDireto() {
        when(arpa.sendQuoteDocument(eq(88L), eq(99L), anyString(), anyString(), eq(302790L))).thenReturn("requested");

        service.process(quote(15900, TODAY), 88L, 99L);

        verify(store).markWhatsapp(15900, "ENVIADO", null);
        verify(store).recordEvent(eq("quote:15900:whatsapp"), eq("COTACAO"), eq(15900L), eq("WHATSAPP_PDF"),
                eq("PROCESSADO"), anyString(), isNull());
    }

    @Test
    void janelaFechadaEnviaTemplateEAguardaResposta() {
        when(arpa.sendQuoteDocument(eq(88L), eq(99L), anyString(), anyString(), eq(302790L)))
                .thenReturn("fallback_template");

        service.process(quote(15900, TODAY), 88L, 99L);

        verify(store).markWhatsapp(15900, "AGUARDANDO_RESPOSTA", null);
        verify(store).recordEvent(eq("quote:15900:whatsapp-template"), eq("COTACAO"), eq(15900L),
                eq("WHATSAPP_TEMPLATE"), eq("PROCESSADO"), anyString(), isNull());
        verify(store, never()).markWhatsapp(15900, "ENVIADO", null);
    }

    @Test
    void clienteRespondeuAoTemplateEnviaOPdfSemTemplate() {
        when(store.eventProcessed("quote:15900:whatsapp-template")).thenReturn(true);
        when(arpa.whatsappWindowOpen(88L)).thenReturn(true);
        when(arpa.sendQuoteDocument(eq(88L), eq(99L), anyString(), anyString(), isNull())).thenReturn("requested");

        service.process(quote(15900, TODAY.minusDays(1)), 88L, 99L);

        verify(arpa).sendQuoteDocument(eq(88L), eq(99L), anyString(), anyString(), isNull());
        verify(store).markWhatsapp(15900, "ENVIADO", null);
    }

    @Test
    void aguardandoRespostaComJanelaFechadaNaoEnviaNada() {
        when(store.eventProcessed("quote:15900:whatsapp-template")).thenReturn(true);
        when(arpa.whatsappWindowOpen(88L)).thenReturn(false);

        service.process(quote(15900, TODAY), 88L, 99L);

        verify(arpa, never()).sendQuoteDocument(anyLong(), anyLong(), anyString(), anyString(), any());
    }

    @Test
    void semRespostaDepoisDoPrazoEncerra() {
        when(store.eventProcessed("quote:15900:whatsapp-template")).thenReturn(true);

        service.process(quote(15900, TODAY.minusDays(4)), 88L, 99L);

        verify(arpa, never()).whatsappWindowOpen(anyLong());
        verify(store).recordEvent(eq("quote:15900:whatsapp-encerrado"), eq("COTACAO"), eq(15900L),
                eq("WHATSAPP_SEM_RESPOSTA"), eq("PROCESSADO"), anyString(), isNull());
    }

    @Test
    void cotacaoAnteriorAoCorteOuJaEnviadaNaoEReenviada() {
        service.process(quote(15839, TODAY), 88L, 99L);
        when(store.eventProcessed("quote:15901:whatsapp")).thenReturn(true);
        service.process(quote(15901, TODAY), 88L, 99L);

        verify(arpa, never()).sendQuoteDocument(anyLong(), anyLong(), anyString(), anyString(), any());
    }

    @Test
    void pessoaSemTelefoneEncerraSemRepetir() {
        when(arpa.sendQuoteDocument(anyLong(), anyLong(), anyString(), anyString(), any()))
                .thenThrow(HttpClientErrorException.create(org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT,
                        "Unprocessable", HttpHeaders.EMPTY,
                        "{\"code\":\"people_without_phone\"}".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

        service.process(quote(15900, TODAY), 88L, 99L);

        verify(store).markWhatsapp(eq(15900L), eq("SEM_TELEFONE"), anyString());
        verify(store).recordEvent(eq("quote:15900:whatsapp-encerrado"), eq("COTACAO"), eq(15900L),
                eq("WHATSAPP_SEM_TELEFONE"), eq("PROCESSADO"), anyString(), isNull());
    }

    private static LegacyQuote quote(long id, LocalDate created) {
        BigDecimal zero = BigDecimal.ZERO;
        return new LegacyQuote(id, created, "10:30", "FERNANDA", "ABERTA", LocalDateTime.of(created, java.time.LocalTime.NOON),
                "Emitente (CIF)", "A", "REMETENTE", "SP", "X", "DESTINATARIO", "CAMPINAS", "A", "PAGADOR",
                "17999990000", "", "DIVERSOS", 1, zero, zero, zero, zero, zero, zero, zero, zero, zero, zero, zero,
                zero, zero, zero, new BigDecimal("100"), "", Map.of());
    }
}
