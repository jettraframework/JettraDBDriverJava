package com.jettra.driver.java;

import io.jettra.json.JettraJson;
import io.jettra.json.JsonArray;
import io.jettra.json.JsonObject;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * High-performance, reflection-based Java 25 Record mapper for
 * JettraDBDriverJava.
 * Supports direct transformation of JSON strings, JsonObjects, raw maps, and
 * AggregationRows into strongly-typed Java Record classes.
 *
 * Features:
 * - Tolerant field naming (camelCase, snake_case, case-insensitive)
 * - Automatic unwrapping of JettraDB `components` blocks in RECORD engine
 * payloads
 * - Smart accumulator alias matching for AggregationRows (e.g. sum_totalAmount
 * -> totalAmount)
 * - Automatic type coercion for primitives, Wrappers, Enums, LocalDate,
 * Instant, BigDecimal, UUID, and nested Records
 */
public final class JettraRecordMapper {

    private static final JettraJson JSON_PARSER = new JettraJson();

    private JettraRecordMapper() {
    }

    /**
     * Converts a JSON string into an instance of the specified Java Record class.
     */
    public static <R extends Record> R toRecord(String json, Class<R> recordClass) {
        if (json == null || json.isBlank() || recordClass == null) {
            return null;
        }
        try {
            JsonObject jo = JSON_PARSER.fromJson(json, JsonObject.class);
            if (jo != null) {
                return toRecord(jo, recordClass);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Converts a JsonObject into an instance of the specified Java Record class.
     */
    public static <R extends Record> R toRecord(JsonObject jsonObject, Class<R> recordClass) {
        if (jsonObject == null || recordClass == null) {
            return null;
        }

        // If the object contains a "components" object (JettraDB Records Engine
        // format), merge them
        Map<String, Object> sourceMap = new LinkedHashMap<>();
        if (jsonObject.has("components")) {
            Object comp = jsonObject.get("components");
            if (comp instanceof JsonObject compObj) {
                for (String key : compObj.keySet()) {
                    sourceMap.put(key, compObj.get(key));
                }
            }
        }
        for (String key : jsonObject.keySet()) {
            if (!"components".equals(key) || !sourceMap.containsKey(key)) {
                sourceMap.putIfAbsent(key, jsonObject.get(key));
            }
        }

        return fromMap(sourceMap, recordClass);
    }

    /**
     * Converts an AggregationRow into an instance of the specified Java Record
     * class.
     */
    public static <R extends Record> R toRecord(AggregationRow row, Class<R> recordClass) {
        if (row == null || recordClass == null) {
            return null;
        }
        Map<String, Object> sourceMap = new LinkedHashMap<>();

        // 1. Group keys
        sourceMap.putAll(row.getGroupKeys());

        // 2. Accumulated values
        sourceMap.putAll(row.getValues());

        // 3. Enriched objects
        sourceMap.putAll(row.getEnriched());

        return fromMap(sourceMap, recordClass);
    }

    /**
     * Converts a Map of field names to values into an instance of the specified
     * Java Record class.
     */
    public static <R extends Record> R toRecord(Map<String, Object> map, Class<R> recordClass) {
        return fromMap(map, recordClass);
    }

    /**
     * Converts a list of items (JSON strings, JsonObjects, Maps, or
     * AggregationRows) into a List of Records.
     */
    public static <R extends Record> List<R> toRecordList(List<?> items, Class<R> recordClass) {
        if (items == null || items.isEmpty() || recordClass == null) {
            return Collections.emptyList();
        }
        List<R> list = new ArrayList<>(items.size());
        for (Object item : items) {
            if (item == null)
                continue;
            R rec = null;
            if (recordClass.isInstance(item)) {
                rec = recordClass.cast(item);
            } else if (item instanceof AggregationRow row) {
                rec = toRecord(row, recordClass);
            } else if (item instanceof JsonObject jo) {
                rec = toRecord(jo, recordClass);
            } else if (item instanceof String s) {
                rec = toRecord(s, recordClass);
            } else if (item instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked")
                Map<String, Object> casted = (Map<String, Object>) m;
                rec = toRecord(casted, recordClass);
            }
            if (rec != null) {
                list.add(rec);
            }
        }
        return list;
    }

    // --- Internal Implementation ---

    @SuppressWarnings("unchecked")
    private static <R extends Record> R fromMap(Map<String, Object> map, Class<R> recordClass) {
        if (map == null || recordClass == null) {
            return null;
        }

        RecordComponent[] components = recordClass.getRecordComponents();
        if (components == null || components.length == 0) {
            return null;
        }

        Class<?>[] paramTypes = new Class<?>[components.length];
        Object[] args = new Object[components.length];

        for (int i = 0; i < components.length; i++) {
            RecordComponent rc = components[i];
            String name = rc.getName();
            Class<?> type = rc.getType();
            paramTypes[i] = type;

            Object rawVal = findValueInMap(map, name);
            args[i] = coerceValue(rawVal, type);
        }

        try {
            Constructor<R> canonical = recordClass.getDeclaredConstructor(paramTypes);
            canonical.setAccessible(true);
            return canonical.newInstance(args);
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate record " + recordClass.getName() + ": " + e.getMessage(),
                    e);
        }
    }

    /**
     * Resolves a field name in the map with fuzzy/tolerant strategies.
     */
    private static Object findValueInMap(Map<String, Object> map, String name) {
        if (map == null || name == null)
            return null;

        // 1. Exact match
        if (map.containsKey(name))
            return map.get(name);

        // 2. Case-insensitive match
        for (var entry : map.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }

        // 3. Snake_case equivalent
        String snake = camelToSnake(name);
        if (map.containsKey(snake))
            return map.get(snake);
        for (var entry : map.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(snake)) {
                return entry.getValue();
            }
        }

        // 4. CamelCase equivalent (if name is snake_case)
        String camel = snakeToCamel(name);
        if (map.containsKey(camel))
            return map.get(camel);

        // 5. Common aggregation prefixes: sum_*, avg_*, min_*, max_*, count_*
        String[] prefixes = { "sum_", "avg_", "min_", "max_", "count_" };
        for (String pfx : prefixes) {
            String candidate = pfx + name;
            if (map.containsKey(candidate))
                return map.get(candidate);
            String candidateSnake = pfx + snake;
            if (map.containsKey(candidateSnake))
                return map.get(candidateSnake);
        }

        return null;
    }

    /**
     * Coerces a raw object into the target component type.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static Object coerceValue(Object raw, Class<?> targetType) {
        if (raw == null) {
            return getDefaultPrimitiveValue(targetType);
        }

        // If already compatible
        if (targetType.isInstance(raw)) {
            return raw;
        }

        String str = raw.toString().trim();

        // 1. Strings
        if (targetType == String.class) {
            return str;
        }

        // 2. Integers
        if (targetType == int.class || targetType == Integer.class) {
            if (raw instanceof Number n)
                return n.intValue();
            try {
                return (int) Math.round(Double.parseDouble(str));
            } catch (Exception e) {
                return targetType == int.class ? 0 : null;
            }
        }

        // 3. Longs
        if (targetType == long.class || targetType == Long.class) {
            if (raw instanceof Number n)
                return n.longValue();
            try {
                return Math.round(Double.parseDouble(str));
            } catch (Exception e) {
                return targetType == long.class ? 0L : null;
            }
        }

        // 4. Doubles
        if (targetType == double.class || targetType == Double.class) {
            if (raw instanceof Number n)
                return n.doubleValue();
            try {
                return Double.parseDouble(str);
            } catch (Exception e) {
                return targetType == double.class ? 0.0 : null;
            }
        }

        // 5. Floats
        if (targetType == float.class || targetType == Float.class) {
            if (raw instanceof Number n)
                return n.floatValue();
            try {
                return Float.parseFloat(str);
            } catch (Exception e) {
                return targetType == float.class ? 0.0f : null;
            }
        }

        // 6. Booleans
        if (targetType == boolean.class || targetType == Boolean.class) {
            if (raw instanceof Boolean b)
                return b;
            return "true".equalsIgnoreCase(str) || "1".equals(str);
        }

        // 7. Enums
        if (targetType.isEnum()) {
            Class<? extends Enum> enumClass = (Class<? extends Enum>) targetType;
            for (Enum e : enumClass.getEnumConstants()) {
                if (e.name().equalsIgnoreCase(str)) {
                    return e;
                }
            }
            return null;
        }

        // 8. BigDecimal
        if (targetType == BigDecimal.class) {
            try {
                return new BigDecimal(str);
            } catch (Exception e) {
                return null;
            }
        }

        // 9. LocalDate
        if (targetType == LocalDate.class) {
            try {
                return LocalDate.parse(str);
            } catch (Exception e) {
                return null;
            }
        }

        // 10. Instant
        if (targetType == Instant.class) {
            try {
                if (raw instanceof Number n) {
                    return Instant.ofEpochMilli(n.longValue());
                }
                return Instant.parse(str);
            } catch (Exception e) {
                return null;
            }
        }

        // 11. UUID
        if (targetType == UUID.class) {
            try {
                return UUID.fromString(str);
            } catch (Exception e) {
                return null;
            }
        }

        // 12. Nested Record
        if (Record.class.isAssignableFrom(targetType)) {
            Class<? extends Record> recType = (Class<? extends Record>) targetType;
            if (raw instanceof JsonObject jo) {
                return toRecord(jo, recType);
            } else if (raw instanceof Map<?, ?> m) {
                return fromMap((Map<String, Object>) m, recType);
            } else if (raw instanceof String s && s.startsWith("{")) {
                return toRecord(s, recType);
            }
        }

        // 13. JsonObject
        if (targetType == JsonObject.class) {
            if (raw instanceof JsonObject jo)
                return jo;
            try {
                return JSON_PARSER.fromJson(str, JsonObject.class);
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    private static Object getDefaultPrimitiveValue(Class<?> type) {
        if (type == int.class)
            return 0;
        if (type == long.class)
            return 0L;
        if (type == double.class)
            return 0.0;
        if (type == float.class)
            return 0.0f;
        if (type == boolean.class)
            return false;
        if (type == byte.class)
            return (byte) 0;
        if (type == short.class)
            return (short) 0;
        if (type == char.class)
            return '\0';
        return null;
    }

    private static String camelToSnake(String str) {
        if (str == null)
            return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0)
                    sb.append('_');
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String snakeToCamel(String str) {
        if (str == null)
            return null;
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = false;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '_') {
                nextUpper = true;
            } else {
                if (nextUpper) {
                    sb.append(Character.toUpperCase(c));
                    nextUpper = false;
                } else {
                    sb.append(Character.toLowerCase(c));
                }
            }
        }
        return sb.toString();
    }
}
