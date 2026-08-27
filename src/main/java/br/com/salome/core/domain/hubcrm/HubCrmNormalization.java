package br.com.salome.core.domain.hubcrm;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class HubCrmNormalization {
    private static final Set<String> LEGAL_SUFFIXES = Set.of(
            "LTDA", "LIMITADA", "SA", "S/A", "EIRELI", "ME", "EPP", "MEI");
    private static final Pattern LEADING_REGISTRATION = Pattern.compile(
            "^([0-9][0-9\\s.\\-/]*)\\s+(.+)$", Pattern.UNICODE_CHARACTER_CLASS);

    private HubCrmNormalization() {}

    public static String digits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    public static String normalizedText(String value) {
        String text = value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return text.toUpperCase(Locale.ROOT);
    }

    public static String shortName(String legalName) {
        String prepared = businessName(legalName)
                .replaceAll("(?i)\\bS\\.?\\s*/?\\s*A\\.?\\b", " ");
        String[] words = normalizedText(prepared).split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isBlank() || LEGAL_SUFFIXES.contains(word)) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(word);
            if (result.toString().length() >= 28 || result.toString().split(" ").length == 3) break;
        }
        return result.isEmpty() ? "CLIENTE" : result.toString();
    }

    public static String businessName(String legalName) {
        if (valueIsBlank(legalName)) return "";
        String prepared = legalName.trim().replaceAll("\\s+", " ");
        var matcher = LEADING_REGISTRATION.matcher(prepared);
        if (!matcher.matches()) return prepared;
        int identifierLength = digits(matcher.group(1)).length();
        if (identifierLength != 8 && identifierLength != 11 && identifierLength != 14) return prepared;
        String name = matcher.group(2).trim();
        return name.matches(".*\\p{L}.*") ? name : prepared;
    }

    public static boolean validContactName(String value) {
        String name = contactName(value);
        String digitsOnly = name.replaceAll("[^0-9]", "");
        boolean numericOnly = !valueIsBlank(name) && digitsOnly.length() >= 7
                && name.replaceAll("[0-9 .()\\-]", "").isBlank();
        return !valueIsBlank(name) && !numericOnly && !normalizedText(name).equals("ERICK");
    }

    /**
     * The legacy contact field may be stored as "phone || name".  The phone
     * belongs in the phone field and must never become part of the Arpa person
     * name.
     */
    public static String contactName(String value) {
        if (valueIsBlank(value)) return "";
        String candidate = value.trim();
        int separator = candidate.indexOf("||");
        if (separator >= 0 && digits(candidate.substring(0, separator)).length() >= 8) {
            candidate = candidate.substring(separator + 2).trim();
        }
        return candidate;
    }

    public static boolean validEmail(String value) {
        if (valueIsBlank(value)) return false;
        String email = value.trim().toLowerCase(Locale.ROOT);
        return email.contains("@") && !Set.of(
                "erick@salome.com.br", "erickcardozo@salome.com.br", "ti@ti.com.br").contains(email);
    }

    public static String sha256(Object... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Object value : values) {
                digest.update(String.valueOf(value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception exception) {
            throw new IllegalStateException("Não foi possível gerar o hash", exception);
        }
    }

    private static boolean valueIsBlank(String value) {
        return value == null || value.isBlank();
    }
}
