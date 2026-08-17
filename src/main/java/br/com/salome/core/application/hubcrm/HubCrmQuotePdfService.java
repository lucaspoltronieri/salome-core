package br.com.salome.core.application.hubcrm;

import br.com.salome.core.domain.hubcrm.LegacyQuote;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "salome.hub-crm", name = "enabled", havingValue = "true")
public class HubCrmQuotePdfService {
    private static final float MARGIN = 46;
    private final HubCrmLegacyRepository legacy;

    public HubCrmQuotePdfService(HubCrmLegacyRepository legacy) {
        this.legacy = legacy;
    }

    public byte[] generate(long quoteId) {
        LegacyQuote quote = legacy.findQuotesByIds(List.of(quoteId)).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Cotação não encontrada: " + quoteId));
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            List<String> lines = content(quote);
            int cursor = 0;
            while (cursor < lines.size()) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    drawHeader(stream, quote.id());
                    float y = 755;
                    while (cursor < lines.size() && y > 55) {
                        String line = lines.get(cursor++);
                        boolean section = line.endsWith(":") && !line.contains("R$");
                        drawText(stream, line, MARGIN, y, section ? 11 : 9,
                                section ? Standard14Fonts.FontName.HELVETICA_BOLD
                                        : Standard14Fonts.FontName.HELVETICA);
                        y -= section ? 20 : 15;
                    }
                    drawText(stream, "Expresso Salomé • Cotação gerada pelo Hub CRM", MARGIN, 30, 8,
                            Standard14Fonts.FontName.HELVETICA_OBLIQUE);
                }
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Não foi possível gerar o PDF da cotação " + quoteId, exception);
        }
    }

    private List<String> content(LegacyQuote q) {
        List<String> lines = new ArrayList<>();
        lines.add("DADOS DA COTAÇÃO:");
        lines.add("Data: " + q.createdDate() + "   Responsável: " + value(q.responsible()));
        lines.add("Tipo de pagamento: " + value(q.paymentType()));
        lines.add("");
        lines.add("REMETENTE:");
        lines.addAll(wrap(value(q.senderName()) + " • CNPJ " + value(q.senderCnpj()) + " • " + value(q.senderCity()), 92));
        lines.add("");
        lines.add("DESTINATÁRIO:");
        lines.addAll(wrap(value(q.recipientName()) + " • CNPJ " + value(q.recipientCnpj()) + " • " + value(q.recipientCity()), 92));
        lines.add("");
        lines.add("CARGA:");
        lines.add("Natureza: " + value(q.cargoType()));
        lines.add("Volumes: " + q.volumes() + "   Peso: " + decimal(q.weight()) + " kg   Cubagem: " + decimal(q.cubage()) + " m³");
        lines.add("Valor da nota fiscal: " + money(q.invoiceValue()));
        lines.add("");
        lines.add("COMPOSIÇÃO DO FRETE:");
        lines.add("Frete peso: " + money(q.freightWeight()) + "   Frete valor: " + money(q.freightValue()));
        lines.add("Pedágio: " + money(q.toll()) + "   Coleta: " + money(q.pickup()) + "   Entrega: " + money(q.delivery()));
        lines.add("Despacho: " + money(q.dispatch()) + "   GRIS: " + money(q.gris()) + "   Redespacho: " + money(q.redelivery()));
        lines.add("ICMS: " + money(q.icms()) + "   Desconto: " + money(q.discount()) + "   Acréscimo: " + money(q.addition()));
        lines.add("");
        lines.add("TOTAL DO FRETE: " + money(q.totalFreight()));
        return lines;
    }

    private void drawHeader(PDPageContentStream stream, long quoteId) throws IOException {
        stream.setNonStrokingColor(155, 18, 37);
        stream.addRect(0, 792, PDRectangle.A4.getWidth(), 50);
        stream.fill();
        stream.setNonStrokingColor(255, 255, 255);
        drawText(stream, "EXPRESSO SALOMÉ", MARGIN, 815, 16, Standard14Fonts.FontName.HELVETICA_BOLD);
        drawText(stream, "COTAÇÃO DE FRETE Nº " + quoteId, 350, 815, 11, Standard14Fonts.FontName.HELVETICA_BOLD);
        stream.setNonStrokingColor(30, 41, 59);
    }

    private void drawText(PDPageContentStream stream, String text, float x, float y, float size,
            Standard14Fonts.FontName fontName) throws IOException {
        stream.beginText();
        stream.setFont(new PDType1Font(fontName), size);
        stream.newLineAtOffset(x, y);
        stream.showText(sanitize(text));
        stream.endText();
    }

    private List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (!line.isEmpty() && line.length() + word.length() + 1 > width) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (!line.isEmpty()) line.append(' ');
            line.append(word);
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    private String sanitize(String value) {
        return value == null ? "" : value.replace('→', '-').replace('•', '-');
    }

    private String money(BigDecimal value) {
        return NumberFormat.getCurrencyInstance(Locale.of("pt", "BR"))
                .format(value == null ? BigDecimal.ZERO : value);
    }

    private String decimal(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private String value(String value) {
        return value == null || value.isBlank() ? "Não informado" : value.trim();
    }
}
