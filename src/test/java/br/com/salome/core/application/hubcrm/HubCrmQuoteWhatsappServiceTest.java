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
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

class HubCrmQuoteWhatsappServiceTest {
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 17);
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
        service = new HubCrmQuoteWhatsappService(store, arpa, signer, new HubCrmWhatsappProperties(15841, 3),
                Clock.fixed(TODAY.atTime(10, 0).atZone(ZONE).toInstant(), ZONE));
    }

    @Test
    void conversaAbertaRecebeOPdfSemTemplate() {
        when(arpa.findOpenConversation(eq(88L), eq("17999990000"), eq(77L), eq("PAGADOR SA"), anyString()))
                .thenReturn(Optional.of(new ArpaSuiteGateway.OpenConversation(51962, "mesma organização")));

        service.process(quote(15900, TODAY), 77L, 88L, 99L);

        verify(arpa).sendDocumentToConversation(51962, 99, "https://hub/pdf", "Cotação de frete nº 15900");
        verify(store).markWhatsapp(15900, "ENVIADO", null);
        verify(store).recordEvent(eq("quote:15900:whatsapp"), eq("COTACAO"), eq(15900L), eq("WHATSAPP_PDF"),
                eq("PROCESSADO"), eq("PDF enviado na conversa 51962 (mesma organização)"), isNull());
    }

    @Test
    void semConversaAbertaNaoEnviaNadaEProcuraDeNovo() {
        when(arpa.findOpenConversation(anyLong(), any(), any(), any(String[].class))).thenReturn(Optional.empty());

        service.process(quote(15900, TODAY), 77L, 88L, 99L);

        verify(arpa, never()).sendDocumentToConversation(anyLong(), anyLong(), anyString(), anyString());
        verify(store).markWhatsapp(15900, "AGUARDANDO_CONVERSA", null);
        verify(store, never()).recordEvent(eq("quote:15900:whatsapp-encerrado"), any(), anyLong(), any(), any(),
                any(), any());
    }

    @Test
    void semConversaDepoisDoPrazoEncerra() {
        service.process(quote(15900, TODAY.minusDays(4)), 77L, 88L, 99L);

        verify(arpa, never()).findOpenConversation(anyLong(), any(), any(), any(String[].class));
        verify(store).recordEvent(eq("quote:15900:whatsapp-encerrado"), eq("COTACAO"), eq(15900L),
                eq("WHATSAPP_SEM_CONVERSA"), eq("PROCESSADO"), anyString(), isNull());
    }

    @Test
    void cotacaoAnteriorAoCorteOuJaEnviadaNaoEReenviada() {
        service.process(quote(15839, TODAY), 77L, 88L, 99L);
        when(store.eventProcessed("quote:15901:whatsapp")).thenReturn(true);
        service.process(quote(15901, TODAY), 77L, 88L, 99L);

        verify(arpa, never()).findOpenConversation(anyLong(), any(), any(), any(String[].class));
        verify(arpa, never()).sendDocumentToConversation(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void janelaQueFechouNoEnvioTentaDeNovoNoProximoCiclo() {
        when(arpa.findOpenConversation(anyLong(), any(), any(), any(String[].class)))
                .thenReturn(Optional.of(new ArpaSuiteGateway.OpenConversation(51962, "mesmo telefone")));
        org.mockito.Mockito.doThrow(HttpClientErrorException.create(HttpStatus.UNPROCESSABLE_CONTENT, "Unprocessable",
                        HttpHeaders.EMPTY, "{\"code\":\"window_closed\"}".getBytes(StandardCharsets.UTF_8),
                        StandardCharsets.UTF_8))
                .when(arpa).sendDocumentToConversation(anyLong(), anyLong(), anyString(), anyString());

        service.process(quote(15900, TODAY), 77L, 88L, 99L);

        verify(store, never()).markWhatsapp(eq(15900L), eq("ERRO"), any());
        verify(store, never()).recordEvent(eq("quote:15900:whatsapp-encerrado"), any(), anyLong(), any(), any(),
                any(), any());
    }

    private static LegacyQuote quote(long id, LocalDate created) {
        BigDecimal zero = BigDecimal.ZERO;
        return new LegacyQuote(id, created, "10:30", "FERNANDA", "ABERTA", LocalDateTime.of(created, LocalTime.NOON),
                "Emitente (CIF)", "A", "REMETENTE", "SP", "X", "DESTINATARIO", "CAMPINAS", "A", "PAGADOR SA",
                "17999990000", "", "DIVERSOS", 1, zero, zero, zero, zero, zero, zero, zero, zero, zero, zero, zero,
                zero, zero, zero, new BigDecimal("100"), "", Map.of());
    }
}
