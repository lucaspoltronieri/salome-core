package br.com.salome.core.application.hubcrm;

import br.com.salome.core.domain.hubcrm.HubCrmNormalization;
import br.com.salome.core.domain.hubcrm.LegacyQuote;
import br.com.salome.core.domain.hubcrm.LegacyQuotePrint;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * PDF da cotação no mesmo formato da impressão do legado (cabeçalho, blocos de
 * remetente/destinatário/consignatário e grade de valores), com os dados da cotação
 * e o contato da responsável comercial logo abaixo do cabeçalho.
 */
@Service
@ConditionalOnProperty(prefix = "salome.hub-crm", name = "enabled", havingValue = "true")
public class HubCrmQuotePdfService {
    private static final String LOGO_RESOURCE = "/hub-crm/logo-salome.png";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String COMMERCIAL_PHONE = "(17) 2139-4866";
    private static final Map<String, String> RESPONSIBLE_EMAILS = Map.of(
            "FERNANDA", "fernanda.silva@salome.com.br",
            "JACI", "jaci.queiroz@salome.com.br",
            "JACI QUEIROZ", "jaci.queiroz@salome.com.br",
            "QUEIROZ", "jaci.queiroz@salome.com.br");

    private static final float LEFT = 36;
    private static final float RIGHT = 559;
    private static final float ROW = 15;
    private static final float SIZE = 8;
    // Colunas dos blocos de endereço: rótulo à esquerda; rótulos do meio e da direita
    // alinhados pelo fim, como na impressão do legado.
    private static final float VALUE_X = 100;
    private static final float NAME_X = 196;
    private static final float MID_LABEL_END = 318;
    private static final float RIGHT_LABEL_END = 470;

    private final HubCrmLegacyRepository legacy;
    private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    public HubCrmQuotePdfService(HubCrmLegacyRepository legacy) {
        this.legacy = legacy;
    }

    public byte[] generate(long quoteId) {
        return generate(legacy.findQuotePrint(quoteId)
                .orElseThrow(() -> new IllegalArgumentException("Cotação não encontrada: " + quoteId)));
    }

    public byte[] generate(LegacyQuotePrint print) {
        LegacyQuote quote = print.quote();
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                float y = header(stream, loadLogo(document), quote.id());
                y = quoteData(stream, quote, y);
                y = party(stream, "Remetente:", print.sender(), y, true);
                y = party(stream, "Destinatário:", print.recipient(), y, true);
                y = party(stream, "Consignatário:", print.consignee(), y, false);
                y = cargo(stream, quote, print, y);
                y = values(stream, quote, y);
                information(stream, print.additionalInfo(), y);
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Não foi possível gerar o PDF da cotação " + quote.id(), exception);
        }
    }

    private float header(PDPageContentStream stream, PDImageXObject logo, long quoteId) throws IOException {
        line(stream, 812, 0.8f, false);
        // O logo é dimensionado pela altura da faixa do cabeçalho (entre as duas linhas),
        // para nunca invadir a primeira linha de dados.
        float logoHeight = 40;
        float logoWidth = logoHeight * logo.getWidth() / logo.getHeight();
        stream.drawImage(logo, LEFT + 10, 768, logoWidth, logoHeight);
        text(stream, "COTAÇÃO DE FRETE: " + quoteId, LEFT + 24 + logoWidth, 781, 20, bold);
        line(stream, 762, 1.4f, false);
        return 746;
    }

    // Mantém os dados da própria cotação e o contato comercial para o cliente responder.
    private float quoteData(PDPageContentStream stream, LegacyQuote quote, float y) throws IOException {
        String date = quote.createdDate() == null ? "" : DATE_FORMAT.format(quote.createdDate());
        if (quote.createdTime() != null && !quote.createdTime().isBlank()) date += " " + quote.createdTime().trim();
        label(stream, "Data:", LEFT, y);
        value(stream, date, VALUE_X, y, NAME_X + 100);
        rightLabel(stream, "Responsável:", MID_LABEL_END + 60, y);
        value(stream, trim(quote.responsible()), MID_LABEL_END + 64, y, RIGHT);
        y -= ROW;
        label(stream, "Telefone:", LEFT, y);
        value(stream, COMMERCIAL_PHONE, VALUE_X, y, NAME_X + 100);
        rightLabel(stream, "E-mail:", MID_LABEL_END + 60, y);
        value(stream, responsibleEmail(quote.responsible()), MID_LABEL_END + 64, y, RIGHT);
        y -= 8;
        line(stream, y, 0.8f, false);
        return y - 13;
    }

    private float party(PDPageContentStream stream, String title, LegacyQuotePrint.Party party, float y,
            boolean dashedAfter) throws IOException {
        LegacyQuotePrint.Party p = party == null
                ? new LegacyQuotePrint.Party("", "", "", "", "", "", "", "", "") : party;
        label(stream, title, LEFT, y);
        value(stream, p.cnpj(), VALUE_X, y, NAME_X - 6);
        value(stream, p.name(), NAME_X, y, RIGHT);
        y -= ROW;
        label(stream, "Endereço:", LEFT, y);
        value(stream, p.address(), VALUE_X, y, RIGHT_LABEL_END - 60);
        rightLabel(stream, "Complemento:", RIGHT_LABEL_END, y);
        value(stream, p.complement(), RIGHT_LABEL_END + 4, y, RIGHT);
        y -= ROW;
        label(stream, "Bairro:", LEFT, y);
        value(stream, p.district(), VALUE_X, y, MID_LABEL_END - 34);
        rightLabel(stream, "Cidade:", MID_LABEL_END, y);
        value(stream, p.city(), MID_LABEL_END + 4, y, RIGHT_LABEL_END - 24);
        rightLabel(stream, "CEP:", RIGHT_LABEL_END, y);
        value(stream, p.zipCode(), RIGHT_LABEL_END + 4, y, RIGHT);
        y -= ROW;
        label(stream, "Telefone:", LEFT, y);
        value(stream, phone(p.phone()), VALUE_X, y, MID_LABEL_END - 34);
        rightLabel(stream, "E-mail:", MID_LABEL_END, y);
        value(stream, trim(p.email()).replace(",", ", "), MID_LABEL_END + 4, y, RIGHT);
        y -= 7;
        line(stream, y, dashedAfter ? 0.5f : 1.1f, dashedAfter);
        return y - 13;
    }

    private float cargo(PDPageContentStream stream, LegacyQuote quote, LegacyQuotePrint print, float y)
            throws IOException {
        label(stream, "Tipo de Pagamento:", LEFT, y);
        value(stream, trim(quote.paymentType()), LEFT + 88, y, RIGHT);
        y -= ROW;
        label(stream, "Natureza de Carga:", LEFT, y);
        value(stream, trim(quote.cargoType()), LEFT + 88, y, RIGHT);
        y -= ROW;
        label(stream, "Previsão de Entrega:", LEFT, y);
        value(stream, print.expectedDelivery() == null ? "" : DATE_FORMAT.format(print.expectedDelivery()),
                LEFT + 88, y, RIGHT);
        return y - 26;
    }

    private float values(PDPageContentStream stream, LegacyQuote q, float y) throws IOException {
        float[][] columns = {{LEFT, 190}, {202, 350}, {362, 470}, {482, RIGHT}};
        String[][] labels = {
                {"Volumes:", "Peso:", "Valor NF:", "Cubagem (M3):"},
                {"Frete Peso:", "Frete Valor:", "Pedágio:", "Coleta:"},
                {"Entrega:", "Despacho:", "Redespacho:", "ICMS:"},
                {"GRIS:", "Total Frete:", "Desconto:", "Acréscimo:"}};
        String[][] values = {
                {String.valueOf(q.volumes()), decimal3(q.weight()), money(q.invoiceValue()), decimal3(q.cubage())},
                {money(q.freightWeight()), money(q.freightValue()), money(q.toll()), money(q.pickup())},
                {money(q.delivery()), money(q.dispatch()), money(q.redelivery()), money(q.icms())},
                {money(q.gris()), money(q.totalFreight()), money(q.discount()), money(q.addition())}};
        float top = y + 11;
        for (int column = 0; column < columns.length; column++) {
            float rowY = y;
            for (int row = 0; row < 4; row++) {
                label(stream, labels[column][row], columns[column][0], rowY);
                boolean total = column == 3 && row == 1;
                String value = values[column][row];
                PDType1Font font = total ? bold : regular;
                float size = total ? 9 : SIZE;
                text(stream, value, columns[column][1] - width(value, font, size), rowY, size, font);
                rowY -= ROW;
            }
        }
        float bottom = y - (3 * ROW) - 5;
        stream.setLineWidth(1f);
        for (float x : new float[] {196, 356, 476}) {
            stream.moveTo(x, top);
            stream.lineTo(x, bottom);
        }
        stream.stroke();
        return bottom - 20;
    }

    private void information(PDPageContentStream stream, String info, float y) throws IOException {
        label(stream, "Informações:", LEFT, y);
        y -= ROW;
        for (String line : wrap(trim(info), RIGHT - LEFT)) {
            if (y < 40) break;
            text(stream, line, LEFT, y, SIZE, regular);
            y -= 12;
        }
    }

    private PDImageXObject loadLogo(PDDocument document) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(LOGO_RESOURCE)) {
            if (input == null) {
                throw new IOException("Logotipo não encontrado no classpath: " + LOGO_RESOURCE);
            }
            return PDImageXObject.createFromByteArray(document, input.readAllBytes(), "logo-salome");
        }
    }

    private void label(PDPageContentStream stream, String label, float x, float y) throws IOException {
        text(stream, label, x, y, SIZE, bold);
    }

    private void rightLabel(PDPageContentStream stream, String label, float endX, float y) throws IOException {
        text(stream, label, endX - width(label, bold, SIZE), y, SIZE, bold);
    }

    // Valor que não cabe no espaço da coluna diminui a fonte e, no limite, é cortado com "...".
    private void value(PDPageContentStream stream, String value, float x, float y, float maxX) throws IOException {
        String text = sanitize(trim(value), regular);
        float size = SIZE;
        float room = maxX - x;
        while (size > 6.5f && width(text, regular, size) > room) size -= 0.5f;
        if (width(text, regular, size) > room) {
            while (!text.isEmpty() && width(text + "...", regular, size) > room) {
                text = text.substring(0, text.length() - 1);
            }
            text = text + "...";
        }
        text(stream, text, x, y, size, regular);
    }

    private void text(PDPageContentStream stream, String text, float x, float y, float size, PDType1Font font)
            throws IOException {
        stream.setNonStrokingColor(Color.BLACK);
        stream.beginText();
        stream.setFont(font, size);
        stream.newLineAtOffset(x, y);
        stream.showText(sanitize(text, font));
        stream.endText();
    }

    private void line(PDPageContentStream stream, float y, float width, boolean dashed) throws IOException {
        stream.setLineWidth(width);
        stream.setStrokingColor(Color.BLACK);
        if (dashed) stream.setLineDashPattern(new float[] {2, 2}, 0);
        stream.moveTo(LEFT, y);
        stream.lineTo(RIGHT, y);
        stream.stroke();
        if (dashed) stream.setLineDashPattern(new float[] {}, 0);
    }

    private float width(String text, PDType1Font font, float size) throws IOException {
        return font.getStringWidth(sanitize(text, font)) / 1000 * size;
    }

    private List<String> wrap(String text, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\\R")) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (!line.isEmpty() && width(candidate, regular, SIZE) > maxWidth) {
                    lines.add(line.toString());
                    line.setLength(0);
                    line.append(word);
                } else {
                    line.setLength(0);
                    line.append(candidate);
                }
            }
            lines.add(line.toString());
        }
        return lines;
    }

    // Fontes Standard 14 só codificam WinAnsi: caractere fora dele vira espaço em vez de
    // derrubar a geração do PDF.
    private String sanitize(String value, PDType1Font font) {
        if (value == null) return "";
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            try {
                font.encode(String.valueOf(current));
                result.append(current);
            } catch (IllegalArgumentException | IOException exception) {
                result.append(' ');
            }
        }
        return result.toString();
    }

    private String responsibleEmail(String responsible) {
        return RESPONSIBLE_EMAILS.getOrDefault(HubCrmNormalization.normalizedText(responsible), "");
    }

    private String phone(String value) {
        String digits = HubCrmNormalization.digits(value);
        if (digits.length() == 10) {
            return "(" + digits.substring(0, 2) + ") " + digits.substring(2, 6) + "-" + digits.substring(6);
        }
        if (digits.length() == 11) {
            return "(" + digits.substring(0, 2) + ") " + digits.substring(2, 7) + "-" + digits.substring(7);
        }
        return trim(value);
    }

    private String money(BigDecimal value) {
        return format("#,##0.00", value);
    }

    private String decimal3(BigDecimal value) {
        return format("#,##0.000", value);
    }

    private String format(String pattern, BigDecimal value) {
        DecimalFormat format = new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.of("pt", "BR")));
        return format.format(value == null ? BigDecimal.ZERO : value);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
