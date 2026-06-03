package org.pexserver.koukunn.bettersurvival.Modules.Feature.WebMap;

import com.google.gson.Gson;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

public final class WebMapJsonRecovery {
    private static final Gson GSON = new Gson();

    private WebMapJsonRecovery() {
    }

    public record RecoveryMap<T>(Map<String, T> values, int recoveredEntries, int skippedEntries) {
    }

    public static String readRaw(File file) throws IOException {
        return Files.readString(file.toPath(), StandardCharsets.UTF_8);
    }

    public static String recoverStringField(String raw, String fieldName) {
        String token = "\"" + fieldName + "\"";
        int index = raw.indexOf(token);
        while (index >= 0) {
            int valueStart = skipWhitespace(raw, index + token.length());
            if (valueStart < raw.length() && raw.charAt(valueStart) == ':') {
                String value = readStringValue(raw, skipWhitespace(raw, valueStart + 1));
                if (value != null) {
                    return value;
                }
            }
            index = raw.indexOf(token, index + 1);
        }
        return null;
    }

    public static Integer recoverIntField(String raw, String fieldName) {
        String token = "\"" + fieldName + "\"";
        int index = raw.indexOf(token);
        while (index >= 0) {
            int valueStart = skipWhitespace(raw, index + token.length());
            if (valueStart < raw.length() && raw.charAt(valueStart) == ':') {
                String value = readPrimitiveValue(raw, skipWhitespace(raw, valueStart + 1));
                if (value != null) {
                    try {
                        return Integer.parseInt(value);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            index = raw.indexOf(token, index + 1);
        }
        return null;
    }

    public static <T> RecoveryMap<T> recoverMapField(String raw, String fieldName, Class<T> valueType) {
        return recoverMapField(raw, fieldName, valueType, value -> true);
    }

    public static <T> RecoveryMap<T> recoverMapField(String raw, String fieldName, Class<T> valueType, java.util.function.Predicate<T> keepPredicate) {
        String token = "\"" + fieldName + "\"";
        int index = raw.indexOf(token);
        while (index >= 0) {
            int valueStart = skipWhitespace(raw, index + token.length());
            if (valueStart < raw.length() && raw.charAt(valueStart) == ':') {
                int objectStart = skipWhitespace(raw, valueStart + 1);
                if (objectStart < raw.length() && raw.charAt(objectStart) == '{') {
                    return recoverObjectMap(raw, objectStart, valueType, keepPredicate);
                }
            }
            index = raw.indexOf(token, index + 1);
        }
        return new RecoveryMap<>(new LinkedHashMap<>(), 0, 0);
    }

    public static int countMapEntries(String raw, String fieldName) {
        String token = "\"" + fieldName + "\"";
        int index = raw.indexOf(token);
        while (index >= 0) {
            int valueStart = skipWhitespace(raw, index + token.length());
            if (valueStart < raw.length() && raw.charAt(valueStart) == ':') {
                int objectStart = skipWhitespace(raw, valueStart + 1);
                if (objectStart < raw.length() && raw.charAt(objectStart) == '{') {
                    return countObjectEntries(raw, objectStart);
                }
            }
            index = raw.indexOf(token, index + 1);
        }
        return 0;
    }

    private static <T> RecoveryMap<T> recoverObjectMap(String raw, int objectStart, Class<T> valueType, java.util.function.Predicate<T> keepPredicate) {
        Map<String, T> values = new LinkedHashMap<>();
        int recoveredEntries = 0;
        int skippedEntries = 0;
        int index = objectStart + 1;
        while (index < raw.length()) {
            index = skipWhitespaceAndSeparators(raw, index);
            if (index >= raw.length()) {
                break;
            }
            char current = raw.charAt(index);
            if (current == '}') {
                break;
            }
            String key = readStringValue(raw, index);
            if (key == null) {
                break;
            }
            int keyEnd = findStringEnd(raw, index);
            if (keyEnd < 0) {
                break;
            }
            int valueStart = skipWhitespace(raw, keyEnd);
            if (valueStart >= raw.length() || raw.charAt(valueStart) != ':') {
                skippedEntries++;
                int nextEntry = findNextEntryStart(raw, keyEnd);
                if (nextEntry < 0) {
                    break;
                }
                index = nextEntry;
                continue;
            }
            valueStart = skipWhitespace(raw, valueStart + 1);
            int valueEnd = findValueEnd(raw, valueStart);
            if (valueEnd < 0) {
                break;
            }
            String valueJson = raw.substring(valueStart, valueEnd);
            try {
                T value = GSON.fromJson(valueJson, valueType);
                if (value != null && (keepPredicate == null || keepPredicate.test(value))) {
                    values.put(key, value);
                    recoveredEntries++;
                } else {
                    skippedEntries++;
                }
            } catch (RuntimeException ignored) {
                skippedEntries++;
            }
            index = valueEnd;
        }
        return new RecoveryMap<>(values, recoveredEntries, skippedEntries);
    }

    private static int countObjectEntries(String raw, int objectStart) {
        int count = 0;
        int index = objectStart + 1;
        while (index < raw.length()) {
            index = skipWhitespaceAndSeparators(raw, index);
            if (index >= raw.length()) {
                break;
            }
            char current = raw.charAt(index);
            if (current == '}') {
                break;
            }
            String key = readStringValue(raw, index);
            if (key == null) {
                break;
            }
            int keyEnd = findStringEnd(raw, index);
            if (keyEnd < 0) {
                break;
            }
            int valueStart = skipWhitespace(raw, keyEnd);
            if (valueStart >= raw.length() || raw.charAt(valueStart) != ':') {
                break;
            }
            valueStart = skipWhitespace(raw, valueStart + 1);
            int valueEnd = findValueEnd(raw, valueStart);
            if (valueEnd < 0) {
                break;
            }
            count++;
            index = valueEnd;
        }
        return count;
    }

    private static String readStringValue(String raw, int start) {
        if (start >= raw.length() || raw.charAt(start) != '"') {
            return null;
        }
        int end = findStringEnd(raw, start);
        if (end < 0) {
            return null;
        }
        try {
            return GSON.fromJson(raw.substring(start, end), String.class);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String readPrimitiveValue(String raw, int start) {
        if (start >= raw.length()) {
            return null;
        }
        int end = findPrimitiveEnd(raw, start);
        if (end < 0) {
            return null;
        }
        return raw.substring(start, end).trim();
    }

    private static int findValueEnd(String raw, int start) {
        if (start >= raw.length()) {
            return -1;
        }
        char current = raw.charAt(start);
        return switch (current) {
            case '{' -> findMatchingDelimiter(raw, start, '{', '}');
            case '[' -> findMatchingDelimiter(raw, start, '[', ']');
            case '"' -> findStringEnd(raw, start);
            default -> findPrimitiveEnd(raw, start);
        };
    }

    private static int findPrimitiveEnd(String raw, int start) {
        int index = start;
        while (index < raw.length()) {
            char current = raw.charAt(index);
            if (current == ',' || current == '}' || current == ']' || Character.isWhitespace(current)) {
                break;
            }
            index++;
        }
        return index > start ? index : -1;
    }

    private static int findNextEntryStart(String raw, int start) {
        int index = start;
        while (index < raw.length()) {
            int quote = raw.indexOf('"', index);
            if (quote < 0) {
                return -1;
            }
            int end = findStringEnd(raw, quote);
            if (end < 0) {
                return -1;
            }
            int separator = skipWhitespace(raw, end);
            if (separator < raw.length() && raw.charAt(separator) == ':') {
                return quote;
            }
            index = end;
        }
        return -1;
    }

    private static int findStringEnd(String raw, int start) {
        if (start < 0 || start >= raw.length() || raw.charAt(start) != '"') {
            return -1;
        }
        boolean escape = false;
        for (int index = start + 1; index < raw.length(); index++) {
            char current = raw.charAt(index);
            if (escape) {
                escape = false;
                continue;
            }
            if (current == '\\') {
                escape = true;
                continue;
            }
            if (current == '"') {
                return index + 1;
            }
        }
        return -1;
    }

    private static int findMatchingDelimiter(String raw, int start, char open, char close) {
        if (start < 0 || start >= raw.length() || raw.charAt(start) != open) {
            return -1;
        }
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int index = start; index < raw.length(); index++) {
            char current = raw.charAt(index);
            if (inString) {
                if (escape) {
                    escape = false;
                    continue;
                }
                if (current == '\\') {
                    escape = true;
                    continue;
                }
                if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (current == '"') {
                inString = true;
                continue;
            }
            if (current == open) {
                depth++;
                continue;
            }
            if (current == close) {
                depth--;
                if (depth == 0) {
                    return index + 1;
                }
            }
        }
        return -1;
    }

    private static int skipWhitespace(String raw, int start) {
        int index = start;
        while (index < raw.length() && Character.isWhitespace(raw.charAt(index))) {
            index++;
        }
        return index;
    }

    private static int skipWhitespaceAndSeparators(String raw, int start) {
        int index = start;
        while (index < raw.length()) {
            char current = raw.charAt(index);
            if (!Character.isWhitespace(current) && current != ',') {
                break;
            }
            index++;
        }
        return index;
    }
}
