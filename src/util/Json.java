package util;

import java.util.*;

/** Encodes responses; all user-provided strings are escaped. */
public final class Json {
    private Json() {}
    public static String encode(Object value) {
        if (value == null) return "null";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?,?> map) {
            StringJoiner parts = new StringJoiner(",", "{", "}");
            map.forEach((key, item) -> parts.add(encode(key.toString()) + ":" + encode(item)));
            return parts.toString();
        }
        if (value instanceof Iterable<?> items) {
            StringJoiner parts = new StringJoiner(",", "[", "]");
            items.forEach(item -> parts.add(encode(item)));
            return parts.toString();
        }
        StringBuilder result = new StringBuilder("\"");
        for (char ch : value.toString().toCharArray()) {
            if (ch == '"' || ch == '\\') result.append('\\').append(ch);
            else if (ch < 32) result.append(String.format("\\u%04x", (int) ch));
            else result.append(ch);
        }
        return result.append('"').toString();
    }
}
