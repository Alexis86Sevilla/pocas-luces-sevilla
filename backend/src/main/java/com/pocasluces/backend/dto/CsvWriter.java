package com.pocasluces.backend.dto;

/**
 * Minimal CSV field encoder shared by the admin export and the public open-data export.
 * Applies RFC 4180 quoting plus OWASP CSV/formula-injection neutralization to text values.
 */
public final class CsvWriter {

    private CsvWriter() {
    }

    /** Joins values into one CSV line (no trailing newline). */
    public static String row(Object... values) {
        return rowWith(',', '.', values);
    }

    /**
     * Joins values with the given delimiter. Non-integer numbers use {@code decimalSeparator}
     * (e.g. {@code ';'} and {@code ','} for a Spanish-locale Excel variant).
     */
    public static String rowWith(char delimiter, char decimalSeparator, Object... values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(delimiter);
            }
            sb.append(field(values[i], delimiter, decimalSeparator));
        }
        return sb.toString();
    }

    public static String field(Object value) {
        return field(value, ',', '.');
    }

    public static String field(Object value, char delimiter, char decimalSeparator) {
        if (value == null) {
            return "";
        }
        if (value instanceof Number || value instanceof Boolean) {
            // Numbers and booleans can't start with a formula-injection character; skip sanitization.
            String plain = value.toString();
            return decimalSeparator == '.' ? plain : plain.replace('.', decimalSeparator);
        }
        String text = neutralizeFormulaInjection(value.toString());
        if (text.indexOf(delimiter) >= 0 || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    /**
     * OWASP CSV/formula injection mitigation: if a value starts with a character that
     * spreadsheet software interprets as the start of a formula (=, +, -, @) or with a
     * tab/carriage return, prefix it with a single quote so it is opened as plain text.
     */
    static String neutralizeFormulaInjection(String text) {
        if (text.isEmpty()) {
            return text;
        }
        char first = text.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            return "'" + text;
        }
        return text;
    }
}
