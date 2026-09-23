package com.umlcollab.backend.codegen;

import java.util.regex.Pattern;

/** Conversion de nombres del diagrama (como los escribio el usuario o la IA) a identificadores Java validos. */
public final class NameUtils {

    private static final Pattern NON_ALNUM = Pattern.compile("[^A-Za-z0-9]+");

    private NameUtils() {
    }

    public static String pascalCase(String raw) {
        String[] parts = splitWords(raw);
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase());
        }
        String result = sb.toString();
        return result.isEmpty() ? "Entidad" : ensureValidStart(result);
    }

    public static String camelCase(String raw) {
        String pascal = pascalCase(raw);
        return Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);
    }

    public static String snakeCase(String raw) {
        String[] parts = splitWords(raw);
        return String.join("_", parts).toLowerCase();
    }

    /** Pluralizacion simple en ingles (los identificadores del backend generado se escriben en ingles/camelCase tecnico). */
    public static String pluralize(String word) {
        if (word.isEmpty()) return word;
        String lower = word.toLowerCase();
        if (lower.endsWith("s") || lower.endsWith("x") || lower.endsWith("z") || lower.endsWith("ch") || lower.endsWith("sh")) {
            return word + "es";
        }
        if (lower.endsWith("y") && word.length() > 1 && !isVowel(lower.charAt(lower.length() - 2))) {
            return word.substring(0, word.length() - 1) + "ies";
        }
        return word + "s";
    }

    private static boolean isVowel(char c) {
        return "aeiou".indexOf(c) >= 0;
    }

    private static String[] splitWords(String raw) {
        if (raw == null || raw.isBlank()) return new String[]{"Entidad"};
        // Normaliza acentos y enyes (ej. "AÑO" -> "ANO", "dirección" -> "direccion")
        String normalized = java.text.Normalizer.normalize(raw.trim(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        // separa por espacios/guiones/underscores y tambien en los cambios minuscula->mayuscula (camelCase de entrada)
        String spaced = normalized.replaceAll("([a-z0-9])([A-Z])", "$1 $2");
        String[] rawParts = NON_ALNUM.matcher(spaced).replaceAll(" ").trim().split("\\s+");
        java.util.List<String> parts = new java.util.ArrayList<>();
        for (String p : rawParts) {
            if (!p.isBlank()) parts.add(p);
        }
        return parts.isEmpty() ? new String[]{"Entidad"} : parts.toArray(new String[0]);
    }

    public static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String ensureValidStart(String identifier) {
        if (Character.isDigit(identifier.charAt(0))) {
            return "E" + identifier;
        }
        return identifier;
    }
}
