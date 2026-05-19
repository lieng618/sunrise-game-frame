package org.sunrise.game.db.entity;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 实体类类型转换工具类
 * 提供从数据库返回的Object到Java类型的安全转换
 * 自动生成，请勿手动修改
 */
public final class EntityConverter {

    private EntityConverter() {
        // 私有构造函数，防止实例化
    }

    /**
     * 安全地将数据库返回的对象转换为指定类型
     * 修复了JDBC与Java类型转换的所有常见坑
     */
    @SuppressWarnings("unchecked")
    public static <T> T convertToType(Object value, Class<T> targetType) {
        if (value == null) {
            throw new NullPointerException("Database returned null for NOT NULL column: " 
                    + targetType.getSimpleName());
        }

        // 1. 直接匹配：目标类型就是值的类型
        if (targetType.isInstance(value)) {
            return (T) value;
        }

        // 2. 处理基本类型与包装类型的兼容
        if (targetType.isPrimitive()) {
            Class<?> wrapperType = getWrapperType(targetType);
            if (wrapperType.isInstance(value)) {
                return (T) value;
            }
        }

        // 3. 处理数字类型转换
        if (value instanceof Number) {
            Number num = (Number) value;
            
            if (targetType == int.class || targetType == Integer.class) {
                return (T) Integer.valueOf(num.intValue());
            } else if (targetType == long.class || targetType == Long.class) {
                return (T) Long.valueOf(num.longValue());
            } else if (targetType == double.class || targetType == Double.class) {
                return (T) Double.valueOf(num.doubleValue());
            } else if (targetType == float.class || targetType == Float.class) {
                return (T) Float.valueOf(num.floatValue());
            } else if (targetType == boolean.class || targetType == Boolean.class) {
                return (T) Boolean.valueOf(num.intValue() != 0);
            } else if (targetType == BigDecimal.class) {
                return (T) new BigDecimal(num.toString());
            }
        }

        // 处理 MySQL TINYINT(1) 驱动返回 Boolean，但实体类类型是整型的情况
        if (value instanceof Boolean) {
            int intVal = ((Boolean) value) ? 1 : 0;
            if (targetType == int.class || targetType == Integer.class) {
                return (T) Integer.valueOf(intVal);
            } else if (targetType == long.class || targetType == Long.class) {
                return (T) Long.valueOf(intVal);
            } else if (targetType == short.class || targetType == Short.class) {
                return (T) Short.valueOf((short) intVal);
            } else if (targetType == byte.class || targetType == Byte.class) {
                return (T) Byte.valueOf((byte) intVal);
            }
        }

        // 4. 处理JDBC时间类型到Java 8时间API的转换
        if (value instanceof Timestamp && targetType == LocalDateTime.class) {
            return (T) ((Timestamp) value).toLocalDateTime();
        } else if (value instanceof Date && targetType == LocalDate.class) {
            return (T) ((Date) value).toLocalDate();
        } else if (value instanceof Time && targetType == LocalTime.class) {
            return (T) ((Time) value).toLocalTime();
        }

        // 5. 处理字符串转换
        if (targetType == String.class) {
            return (T) value.toString();
        }

        // 无法转换时抛出详细异常
        throw new IllegalArgumentException(
                String.format("Cannot convert value of type %s to %s. Value: %s",
                        value.getClass().getName(),
                        targetType.getName(),
                        value));
    }

    /**
     * 获取基本类型对应的包装类型
     */
    private static Class<?> getWrapperType(Class<?> primitiveType) {
        if (primitiveType == boolean.class) return Boolean.class;
        if (primitiveType == byte.class) return Byte.class;
        if (primitiveType == char.class) return Character.class;
        if (primitiveType == short.class) return Short.class;
        if (primitiveType == int.class) return Integer.class;
        if (primitiveType == long.class) return Long.class;
        if (primitiveType == float.class) return Float.class;
        if (primitiveType == double.class) return Double.class;
        throw new IllegalArgumentException("Not a primitive type: " + primitiveType);
    }
}
