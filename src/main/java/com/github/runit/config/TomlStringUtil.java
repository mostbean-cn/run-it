package com.github.runit.config;

import java.util.Locale;

final class TomlStringUtil {
    private TomlStringUtil() {
    }

    static String quote(String value) {
        return "\"" + escape(value) + "\"";
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); ) {
            int codePoint = value.codePointAt(i);
            i += Character.charCount(codePoint);

            switch (codePoint) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\b' -> sb.append("\\b");
                case '\t' -> sb.append("\\t");
                case '\n' -> sb.append("\\n");
                case '\f' -> sb.append("\\f");
                case '\r' -> sb.append("\\r");
                default -> {
                    if (Character.isISOControl(codePoint)) {
                        appendUnicodeEscape(sb, codePoint);
                    } else {
                        sb.appendCodePoint(codePoint);
                    }
                }
            }
        }
        return sb.toString();
    }

    private static void appendUnicodeEscape(StringBuilder sb, int codePoint) {
        if (codePoint <= 0xFFFF) {
            sb.append("\\u").append(toPaddedHex(codePoint, 4));
        } else {
            sb.append("\\U").append(toPaddedHex(codePoint, 8));
        }
    }

    private static String toPaddedHex(int value, int width) {
        String hex = Integer.toHexString(value).toUpperCase(Locale.ROOT);
        if (hex.length() >= width) {
            return hex;
        }

        StringBuilder sb = new StringBuilder(width);
        for (int i = hex.length(); i < width; i++) {
            sb.append('0');
        }
        sb.append(hex);
        return sb.toString();
    }
}
