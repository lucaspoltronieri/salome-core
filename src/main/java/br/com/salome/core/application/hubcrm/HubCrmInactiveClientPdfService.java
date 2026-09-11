package br.com.salome.core.application.hubcrm;

import br.com.salome.core.domain.hubcrm.HubCrmNormalization;
import br.com.salome.core.domain.hubcrm.InactiveClientReport;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
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
 * PDF (A4 paisagem) com os CT-es do ano em que o cliente inativo pagou o frete: remetente,
 * destinatário, notas, peso, valor da NF e frete, com totais. Vai por link na observação do
 * card de prospecção no ArpaSuite.
 */
@Service
@ConditionalOnProperty(prefix = "salome.hub-crm", name = "enabled", havingValue = "true")
public class HubCrmInactiveClientPdfService {
    private static final String LOGO_RESOURCE = "/hub-crm/logo-salome.png";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String COMMERCIAL_PHONE = "(17) 2139-4866";
    private static final PDRectangle PAGE = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
    private static final float LEFT = 30;
    private static final float RIGHT = 812;
    private static final float BOTTOM = 40;
    private static final float ROW = 12;
    private static final float SIZE = 7;
    // Cada CT-e ocupa duas linhas: a segunda traz o CNPJ da outra parte (quem não é o cliente).
    private static final float CNPJ_LINE = 8;
    private static final float CTE_ROW = ROW + CNPJ_LINE;
    private static final int SENDER_COLUMN = 3;
    private static final int RECIPIENT_COLUMN = 5;
    // Colunas: CT-e, Emissão, Pagamento, Remetente e cidade, Destinatário e cidade, NF, Volumes,
    // Peso, Valor NF, Frete (larguras somam RIGHT - LEFT).
    private static final String[] HEADERS = {"Nº CT-e", "Emissão", "Pagto", "Remetente", "Cidade remetente",
            "Destinatário", "Cidade destinatário", "NF", "Volumes", "Peso (kg)", "Valor NF", "Frete"};
    private static final float[] WIDTHS = {40, 46, 30, 120, 91, 120, 91, 58, 34, 48, 54, 50};
    private static final boolean[] NUMERIC =
            {true, false, false, false, false, false, false, false, true, true, true, true};
    // Cidades e notas usam a segunda linha da célula em vez de serem cortadas.
    private static final boolean[] WRAP =
            {false, false, false, false, true, false, true, true, false, false, false, false};

    private final InactiveClientRepository repository;
    private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    public HubCrmInactiveClientPdfService(InactiveClientRepository repository) {
        this.repository = repository;
    }

    public byte[] generate(long clientId, int year) {
        return generate(repository.findReport(clientId, year)
                .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado: " + clientId)));
    }

    public byte[] generate(InactiveClientReport report) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDImageXObject logo = loadLogo(document);
            PDPage page = new PDPage(PAGE);
            document.addPage(page);
            PDPageContentStream stream = new PDPageContentStream(document, page);
            try {
                float y = header(stream, logo, report);
                y = tableHeader(stream, y);
                for (InactiveClientReport.Cte cte : report.ctes()) {
                    if (y < BOTTOM + CTE_ROW) {
                        stream.close();
                        page = new PDPage(PAGE);
                        document.addPage(page);
                        stream = new PDPageContentStream(document, page);
                        text(stream, trim(report.name()) + " — transportes " + report.year() + " (continuação)",
                                LEFT, PAGE.getHeight() - 36, 9, bold);
                        y = tableHeader(stream, PAGE.getHeight() - 52);
                    }
                    row(stream, y, regular, new String[] {
                            String.valueOf(cte.number()),
                            cte.issued() == null ? "" : DATE_FORMAT.format(cte.issued()),
                            paymentLabel(cte.paymentType()),
                            trim(cte.sender()), trim(cte.senderCity()),
                            trim(cte.recipient()), trim(cte.recipientCity()),
                            trim(cte.invoices()), String.valueOf(cte.volumes()),
                            decimal3(cte.weight()), money(cte.invoiceValue()), money(cte.freight())});
                    counterpartCnpj(stream, y - CNPJ_LINE, report, cte);
                    y -= CTE_ROW;
                }
                line(stream, y + ROW - 3, 0.8f);
                row(stream, y - 2, bold, new String[] {"", "", "", "TOTAL (" + report.ctes().size() + " CT-es)", "", "",
                        "", "", String.valueOf(report.totalVolumes()), decimal3(report.totalWeight()),
                        money(report.totalInvoiceValue()), money(report.totalFreight())});
            } finally {
                stream.close();
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Não foi possível gerar o PDF do cliente " + report.clientId(), exception);
        }
    }

    private float header(PDPageContentStream stream, PDImageXObject logo, InactiveClientReport report)
            throws IOException {
        float top = PAGE.getHeight() - 24;
        float logoHeight = 36;
        float logoWidth = logoHeight * logo.getWidth() / logo.getHeight();
        stream.drawImage(logo, LEFT, top - logoHeight, logoWidth, logoHeight);
        text(stream, "TRANSPORTES DO CLIENTE — " + report.year(), LEFT + logoWidth + 18, top - 24, 16, bold);
        text(stream, "Comercial Salomé: " + COMMERCIAL_PHONE, RIGHT - width("Comercial Salomé: " + COMMERCIAL_PHONE,
                regular, 8), top - 24, 8, regular);
        float y = top - logoHeight - 10;
        line(stream, y, 1.2f);
        y -= 14;
        String name = trim(report.name());
        if (!trim(report.tradeName()).isEmpty() && !trim(report.tradeName()).equalsIgnoreCase(name)) {
            name += " (" + trim(report.tradeName()) + ")";
        }
        label(stream, "Cliente:", LEFT, y);
        fit(stream, name, LEFT + 50, y, 700, regular, 9);
        y -= 14;
        label(stream, "CNPJ:", LEFT, y);
        text(stream, cnpj(report.cnpj()), LEFT + 50, y, 9, regular);
        label(stream, "Código:", 610, y);
        text(stream, String.valueOf(report.clientId()), 648, y, 9, regular);
        y -= 14;
        label(stream, "Cidade:", LEFT, y);
        text(stream, trim(report.city()) + (trim(report.state()).isEmpty() ? "" : "-" + trim(report.state())),
                LEFT + 50, y, 9, regular);
        y -= 14;
        label(stream, "Resumo " + report.year() + ":", LEFT, y);
        text(stream, report.ctes().size() + " CT-es  |  " + report.totalVolumes() + " volumes  |  Peso "
                + decimal3(report.totalWeight()) + " kg  |  Valor NF R$ "
                + money(report.totalInvoiceValue()) + "  |  Frete R$ " + money(report.totalFreight()),
                LEFT + 70, y, 9, regular);
        y -= 10;
        line(stream, y, 0.8f);
        return y - 16;
    }

    private float tableHeader(PDPageContentStream stream, float y) throws IOException {
        stream.setNonStrokingColor(new Color(0x1F, 0x4E, 0x78));
        stream.addRect(LEFT, y - 3, RIGHT - LEFT, ROW + 2);
        stream.fill();
        float x = LEFT;
        for (int i = 0; i < HEADERS.length; i++) {
            float textX = NUMERIC[i] ? x + WIDTHS[i] - 3 - width(HEADERS[i], bold, SIZE) : x + 3;
            text(stream, HEADERS[i], textX, y, SIZE, bold, Color.WHITE);
            x += WIDTHS[i];
        }
        return y - ROW - 2;
    }

    private void row(PDPageContentStream stream, float y, PDType1Font font, String[] cells) throws IOException {
        float x = LEFT;
        for (int i = 0; i < cells.length; i++) {
            float room = WIDTHS[i] - 6;
            String value = sanitize(trim(cells[i]), font);
            if (NUMERIC[i]) {
                // Número nunca é cortado: diminui a fonte até caber.
                float size = SIZE;
                while (size > 5 && width(value, font, size) > room) size -= 0.25f;
                text(stream, value, x + WIDTHS[i] - 3 - width(value, font, size), y, size, font);
            } else if (WRAP[i] && width(value, font, SIZE) > room) {
                String[] lines = twoLines(value, font, room);
                text(stream, lines[0], x + 3, y, SIZE, font);
                text(stream, shorten(lines[1], font, SIZE, room), x + 3, y - CNPJ_LINE, SIZE, font);
            } else {
                text(stream, shorten(value, font, SIZE, room), x + 3, y, SIZE, font);
            }
            x += WIDTHS[i];
        }
    }

    // Quebra por palavras: o que couber na primeira linha fica nela e o resto vai para a segunda.
    private String[] twoLines(String value, PDType1Font font, float room) throws IOException {
        String[] words = value.split(" ");
        StringBuilder first = new StringBuilder();
        int index = 0;
        while (index < words.length) {
            String candidate = first.isEmpty() ? words[index] : first + " " + words[index];
            if (width(candidate, font, SIZE) > room) break;
            first.setLength(0);
            first.append(candidate);
            index++;
        }
        if (first.isEmpty()) return new String[] {shorten(value, font, SIZE, room), ""};
        return new String[] {first.toString(), String.join(" ", java.util.Arrays.copyOfRange(words, index, words.length))};
    }

    // O cliente é o remetente ou o destinatário do CT-e; o CNPJ vai embaixo do nome da outra parte.
    private void counterpartCnpj(PDPageContentStream stream, float y, InactiveClientReport report,
            InactiveClientReport.Cte cte) throws IOException {
        String client = HubCrmNormalization.digits(report.cnpj());
        boolean clientIsSender = !client.isEmpty() && client.equals(HubCrmNormalization.digits(cte.senderCnpj()));
        String other = clientIsSender ? cte.recipientCnpj() : cte.senderCnpj();
        if (HubCrmNormalization.digits(other).isEmpty()) return;
        int column = clientIsSender ? RECIPIENT_COLUMN : SENDER_COLUMN;
        float x = LEFT;
        for (int i = 0; i < column; i++) x += WIDTHS[i];
        text(stream, "CNPJ " + cnpj(other), x + 3, y, 6, regular, Color.DARK_GRAY);
    }

    private String paymentLabel(String paymentType) {
        String normalized = HubCrmNormalization.normalizedText(paymentType);
        if (normalized.contains("FOB")) return "FOB";
        if (normalized.contains("CIF")) return "CIF";
        return trim(paymentType);
    }

    private PDImageXObject loadLogo(PDDocument document) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(LOGO_RESOURCE)) {
            if (input == null) throw new IOException("Logotipo não encontrado no classpath: " + LOGO_RESOURCE);
            return PDImageXObject.createFromByteArray(document, input.readAllBytes(), "logo-salome");
        }
    }

    private void label(PDPageContentStream stream, String label, float x, float y) throws IOException {
        text(stream, label, x, y, 9, bold);
    }

    private void fit(PDPageContentStream stream, String value, float x, float y, float room, PDType1Font font,
            float size) throws IOException {
        text(stream, shorten(value, font, size, room), x, y, size, font);
    }

    private String shorten(String value, PDType1Font font, float size, float room) throws IOException {
        String text = sanitize(trim(value), font);
        if (width(text, font, size) <= room) return text;
        while (!text.isEmpty() && width(text + "...", font, size) > room) text = text.substring(0, text.length() - 1);
        return text + "...";
    }

    private void text(PDPageContentStream stream, String text, float x, float y, float size, PDType1Font font)
            throws IOException {
        text(stream, text, x, y, size, font, Color.BLACK);
    }

    private void text(PDPageContentStream stream, String text, float x, float y, float size, PDType1Font font,
            Color color) throws IOException {
        stream.setNonStrokingColor(color);
        stream.beginText();
        stream.setFont(font, size);
        stream.newLineAtOffset(x, y);
        stream.showText(sanitize(text, font));
        stream.endText();
    }

    private void line(PDPageContentStream stream, float y, float width) throws IOException {
        stream.setLineWidth(width);
        stream.setStrokingColor(Color.BLACK);
        stream.moveTo(LEFT, y);
        stream.lineTo(RIGHT, y);
        stream.stroke();
    }

    private float width(String text, PDType1Font font, float size) throws IOException {
        return font.getStringWidth(sanitize(text, font)) / 1000 * size;
    }

    // Fontes Standard 14 só codificam WinAnsi: caractere fora dele vira espaço.
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

    private String cnpj(String value) {
        String d = HubCrmNormalization.digits(value);
        if (d.length() != 14) return trim(value);
        return d.substring(0, 2) + "." + d.substring(2, 5) + "." + d.substring(5, 8) + "/" + d.substring(8, 12)
                + "-" + d.substring(12);
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
