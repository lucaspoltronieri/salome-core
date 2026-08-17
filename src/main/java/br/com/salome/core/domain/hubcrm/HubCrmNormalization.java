package br.com.salome.core.domain.hubcrm;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

public final class HubCrmNormalization {
    private static final Set<String> LEGAL_SUFFIXES = Set.of(
            "LTDA", "LIMITADA", "SA", "S/A", "EIRELI", "ME", "EPP", "MEI");

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
        String prepared = legalName == null ? "" : legalName
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

    public static boolean validContactName(String value) {
        return !valueIsBlank(value) && !normalizedText(value).equals("ERICK");
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
