package org.sunrise.game.rpc.function;

import java.util.List;

/**
 * 将白名单校验后的 RPC 参数转换为方法声明类型。
 */
final class RpcArgConverter {

    private RpcArgConverter() {
    }

    static Object convert(Object value, Class<?> targetType) {
        if (targetType == null) {
            throw new IllegalArgumentException("RPC parameter type is null");
        }
        if (value == null) {
            if (targetType.isPrimitive()) {
                return defaultPrimitive(targetType);
            }
            return null;
        }

        if (targetType.isInstance(value)) {
            return value;
        }

        if (value instanceof Number number) {
            return convertNumber(number, targetType);
        }

        if (List.class.isAssignableFrom(targetType) && value instanceof List<?> list) {
            return list;
        }

        if (targetType == boolean.class && value instanceof Boolean bool) {
            return bool;
        }
        if (targetType == Boolean.class && value instanceof Boolean bool) {
            return bool;
        }

        throw new IllegalArgumentException(
                "RPC argument type mismatch, expected " + targetType.getName() + " but got " + value.getClass().getName());
    }

    private static Object convertNumber(Number number, Class<?> targetType) {
        if (targetType == int.class || targetType == Integer.class) {
            return number.intValue();
        }
        if (targetType == long.class || targetType == Long.class) {
            return number.longValue();
        }
        if (targetType == double.class || targetType == Double.class) {
            return number.doubleValue();
        }
        if (targetType == float.class || targetType == Float.class) {
            return number.floatValue();
        }
        if (targetType == short.class || targetType == Short.class) {
            return number.shortValue();
        }
        if (targetType == byte.class || targetType == Byte.class) {
            return number.byteValue();
        }
        throw new IllegalArgumentException(
                "RPC argument type mismatch, expected " + targetType.getName() + " but got " + number.getClass().getName());
    }

    private static Object defaultPrimitive(Class<?> targetType) {
        if (targetType == boolean.class) {
            return false;
        }
        if (targetType == byte.class) {
            return (byte) 0;
        }
        if (targetType == short.class) {
            return (short) 0;
        }
        if (targetType == int.class) {
            return 0;
        }
        if (targetType == long.class) {
            return 0L;
        }
        if (targetType == float.class) {
            return 0f;
        }
        if (targetType == double.class) {
            return 0d;
        }
        throw new IllegalArgumentException("Unsupported primitive type: " + targetType.getName());
    }
}
