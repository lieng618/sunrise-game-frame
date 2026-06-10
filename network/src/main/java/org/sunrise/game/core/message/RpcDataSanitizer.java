package org.sunrise.game.core.message;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * RPC MessagePack 载荷白名单校验：仅允许基本类型、String、byte[] 及嵌套的 List/Map。
 */
public final class RpcDataSanitizer {

    private static final int MAX_DEPTH = 16;
    private static final int MAX_COLLECTION_SIZE = 10_000;

    private RpcDataSanitizer() {
    }

    public static Object[] sanitizeArray(Object[] values) {
        if (values == null) {
            return null;
        }
        Object[] sanitized = new Object[values.length];
        for (int i = 0; i < values.length; i++) {
            sanitized[i] = sanitize(values[i], 0);
        }
        return sanitized;
    }

    public static Object sanitize(Object value) {
        return sanitize(value, 0);
    }

    private static Object sanitize(Object value, int depth) {
        if (value == null) {
            return null;
        }
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("RPC data exceeds max nesting depth");
        }

        if (value instanceof Boolean || value instanceof String || value instanceof byte[]) {
            return value;
        }
        if (value instanceof Number) {
            return normalizeNumber((Number) value);
        }
        if (value instanceof List<?> list) {
            return sanitizeList(list, depth);
        }
        if (value instanceof Map<?, ?> map) {
            return sanitizeMap(map, depth);
        }
        if (value instanceof Set<?> set) {
            return sanitizeCollection(set, depth);
        }
        if (value instanceof Collection<?> collection) {
            return sanitizeCollection(collection, depth);
        }

        throw new IllegalArgumentException("Disallowed RPC data type: " + value.getClass().getName());
    }

    private static List<Object> sanitizeList(List<?> list, int depth) {
        if (list.size() > MAX_COLLECTION_SIZE) {
            throw new IllegalArgumentException("RPC data list exceeds max size");
        }
        List<Object> sanitized = new ArrayList<>(list.size());
        for (Object item : list) {
            sanitized.add(sanitize(item, depth + 1));
        }
        return sanitized;
    }

    private static List<Object> sanitizeCollection(Collection<?> collection, int depth) {
        if (collection.size() > MAX_COLLECTION_SIZE) {
            throw new IllegalArgumentException("RPC data collection exceeds max size");
        }
        List<Object> sanitized = new ArrayList<>(collection.size());
        for (Object item : collection) {
            sanitized.add(sanitize(item, depth + 1));
        }
        return sanitized;
    }

    private static Map<String, Object> sanitizeMap(Map<?, ?> map, int depth) {
        if (map.size() > MAX_COLLECTION_SIZE) {
            throw new IllegalArgumentException("RPC data map exceeds max size");
        }
        rejectPolymorphicTypeMetadata(map);
        Map<String, Object> sanitized = new LinkedHashMap<>(map.size());
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException("RPC data map key must be String");
            }
            sanitized.put(key, sanitize(entry.getValue(), depth + 1));
        }
        return sanitized;
    }

    private static void rejectPolymorphicTypeMetadata(Map<?, ?> map) {
        if (map.containsKey("@class") || map.containsKey("@type")) {
            throw new IllegalArgumentException("Polymorphic type metadata is not allowed in RPC data");
        }
    }

    private static Number normalizeNumber(Number value) {
        if (value instanceof Integer || value instanceof Long || value instanceof Double || value instanceof Float) {
            return value;
        }
        if (value instanceof Byte || value instanceof Short) {
            return value.intValue();
        }
        throw new IllegalArgumentException("Disallowed RPC number type: " + value.getClass().getName());
    }
}
