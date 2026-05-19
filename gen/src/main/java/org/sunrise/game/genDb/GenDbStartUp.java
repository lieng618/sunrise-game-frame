package org.sunrise.game.genDb;

import org.sunrise.game.config.ConfigReader;
import org.sunrise.game.db.DbService;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class GenDbStartUp {
    private static DbService dbService;

    public static void main(String[] args) {
        // 加载配置文件
        ConfigReader.loadConfig(System.getProperty("user.dir") + "/gen/src/main/java/org/sunrise/game/genDb/db-config.properties");
        dbService = new DbService();

        // 首先生成工具类
        generateEntityConverter();
        System.out.println("Generated: EntityConverter.java");

        // 获取所有表名
        List<String> tableNames = getAllTableNames();

        // 为每个表生成实体类
        for (String tableName : tableNames) {
            generateEntityClass(tableName);
            System.out.println("Generated immutable entity for table: " + tableName);
        }
    }

    // 生成类型转换工具类
    private static void generateEntityConverter() {
        String converterCode = "package org.sunrise.game.genDb.gen;\n\n" +
                "import java.math.BigDecimal;\n" +
                "import java.sql.Date;\n" +
                "import java.sql.Time;\n" +
                "import java.sql.Timestamp;\n" +
                "import java.time.LocalDate;\n" +
                "import java.time.LocalDateTime;\n" +
                "import java.time.LocalTime;\n\n" +
                "/**\n" +
                " * 实体类类型转换工具类\n" +
                " * 提供从数据库返回的Object到Java类型的安全转换\n" +
                " * 自动生成，请勿手动修改\n" +
                " */\n" +
                "public final class EntityConverter {\n\n" +
                "    private EntityConverter() {\n" +
                "        // 私有构造函数，防止实例化\n" +
                "    }\n\n" +
                "    /**\n" +
                "     * 安全地将数据库返回的对象转换为指定类型\n" +
                "     * 修复了JDBC与Java类型转换的所有常见坑\n" +
                "     */\n" +
                "    @SuppressWarnings(\"unchecked\")\n" +
                "    public static <T> T convertToType(Object value, Class<T> targetType) {\n" +
                "        if (value == null) {\n" +
                "            throw new NullPointerException(\"Database returned null for NOT NULL column: \" \n" +
                "                    + targetType.getSimpleName());\n" +
                "        }\n\n" +
                "        // 1. 直接匹配：目标类型就是值的类型\n" +
                "        if (targetType.isInstance(value)) {\n" +
                "            return (T) value;\n" +
                "        }\n\n" +
                "        // 2. 处理基本类型与包装类型的兼容\n" +
                "        if (targetType.isPrimitive()) {\n" +
                "            Class<?> wrapperType = getWrapperType(targetType);\n" +
                "            if (wrapperType.isInstance(value)) {\n" +
                "                return (T) value;\n" +
                "            }\n" +
                "        }\n\n" +
                "        // 3. 处理数字类型转换\n" +
                "        if (value instanceof Number) {\n" +
                "            Number num = (Number) value;\n" +
                "            \n" +
                "            if (targetType == int.class || targetType == Integer.class) {\n" +
                "                return (T) Integer.valueOf(num.intValue());\n" +
                "            } else if (targetType == long.class || targetType == Long.class) {\n" +
                "                return (T) Long.valueOf(num.longValue());\n" +
                "            } else if (targetType == double.class || targetType == Double.class) {\n" +
                "                return (T) Double.valueOf(num.doubleValue());\n" +
                "            } else if (targetType == float.class || targetType == Float.class) {\n" +
                "                return (T) Float.valueOf(num.floatValue());\n" +
                "            } else if (targetType == boolean.class || targetType == Boolean.class) {\n" +
                "                return (T) Boolean.valueOf(num.intValue() != 0);\n" +
                "            } else if (targetType == BigDecimal.class) {\n" +
                "                return (T) new BigDecimal(num.toString());\n" +
                "            }\n" +
                "        }\n\n" +
                "        // 处理 MySQL TINYINT(1) 驱动返回 Boolean，但实体类类型是整型的情况\n" +
                "        if (value instanceof Boolean) {\n" +
                "            int intVal = ((Boolean) value) ? 1 : 0;\n" +
                "            if (targetType == int.class || targetType == Integer.class) {\n" +
                "                return (T) Integer.valueOf(intVal);\n" +
                "            } else if (targetType == long.class || targetType == Long.class) {\n" +
                "                return (T) Long.valueOf(intVal);\n" +
                "            } else if (targetType == short.class || targetType == Short.class) {\n" +
                "                return (T) Short.valueOf((short) intVal);\n" +
                "            } else if (targetType == byte.class || targetType == Byte.class) {\n" +
                "                return (T) Byte.valueOf((byte) intVal);\n" +
                "            }\n" +
                "        }\n\n" +
                "        // 4. 处理JDBC时间类型到Java 8时间API的转换\n" +
                "        if (value instanceof Timestamp && targetType == LocalDateTime.class) {\n" +
                "            return (T) ((Timestamp) value).toLocalDateTime();\n" +
                "        } else if (value instanceof Date && targetType == LocalDate.class) {\n" +
                "            return (T) ((Date) value).toLocalDate();\n" +
                "        } else if (value instanceof Time && targetType == LocalTime.class) {\n" +
                "            return (T) ((Time) value).toLocalTime();\n" +
                "        }\n\n" +
                "        // 5. 处理字符串转换\n" +
                "        if (targetType == String.class) {\n" +
                "            return (T) value.toString();\n" +
                "        }\n\n" +
                "        // 无法转换时抛出详细异常\n" +
                "        throw new IllegalArgumentException(\n" +
                "                String.format(\"Cannot convert value of type %s to %s. Value: %s\",\n" +
                "                        value.getClass().getName(),\n" +
                "                        targetType.getName(),\n" +
                "                        value));\n" +
                "    }\n\n" +
                "    /**\n" +
                "     * 获取基本类型对应的包装类型\n" +
                "     */\n" +
                "    private static Class<?> getWrapperType(Class<?> primitiveType) {\n" +
                "        if (primitiveType == boolean.class) return Boolean.class;\n" +
                "        if (primitiveType == byte.class) return Byte.class;\n" +
                "        if (primitiveType == char.class) return Character.class;\n" +
                "        if (primitiveType == short.class) return Short.class;\n" +
                "        if (primitiveType == int.class) return Integer.class;\n" +
                "        if (primitiveType == long.class) return Long.class;\n" +
                "        if (primitiveType == float.class) return Float.class;\n" +
                "        if (primitiveType == double.class) return Double.class;\n" +
                "        throw new IllegalArgumentException(\"Not a primitive type: \" + primitiveType);\n" +
                "    }\n" +
                "}\n";

        String outputFile = System.getProperty("user.dir") + "/gen/src/main/java/org/sunrise/game/genDb/gen/EntityConverter.java";
        File file = new File(outputFile);
        file.getParentFile().mkdirs();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            writer.write(converterCode);
        } catch (IOException e) {
            System.err.println("Failed to write EntityConverter.java");
            e.printStackTrace();
        }
    }

    // 获取数据库中的所有表名
    private static List<String> getAllTableNames() {
        String query = "SHOW TABLES";
        return dbService.queryAllSingleColumn(query);
    }

    // 生成实体类代码并保存为文件
    public static void generateEntityClass(String tableName) {
        String className = "Entity" + toCamelCase(tableName, true);
        String fileName = className + ".java";

        StringBuilder classBuilder = new StringBuilder();
        classBuilder.append("package org.sunrise.game.genDb.gen;\n\n")
                .append("import lombok.Value;\n")
                .append("import java.math.BigDecimal;\n")
                .append("import java.time.LocalDate;\n")
                .append("import java.time.LocalDateTime;\n")
                .append("import java.time.LocalTime;\n")
                .append("import java.util.Map;\n")
                .append("\n/**\n")
                .append(" * 数据库表 ").append(tableName).append(" 的不可变实体类\n")
                .append(" * 自动生成，请勿手动修改\n")
                .append(" */\n")
                .append("@Value\n")
                .append("public class ").append(className).append(" {\n");

        // 查询表结构
        String query = "DESCRIBE " + tableName;
        List<Map<String, Object>> columns = dbService.queryAll(query);

        // 生成字段定义（@Value会自动添加private final）
        for (Map<String, Object> column : columns) {
            String fieldName = getFieldName(column);
            String javaType = getJavaType(column);
            String comment = getColumnComment(column);

            if (comment != null && !comment.isEmpty()) {
                classBuilder.append("\n    /** ").append(comment).append(" */\n");
            }
            classBuilder.append("    ").append(javaType)
                    .append(" ").append(fieldName).append(";\n");
        }

        // 添加Map参数构造函数（调用工具类进行类型转换）
        classBuilder.append("\n    public ").append(className)
                .append("(Map<String, Object> dataMap) {\n");

        for (Map<String, Object> column : columns) {
            String columnName = getColumnName(column);
            String fieldName = getFieldName(column);
            String javaType = getJavaType(column);

            classBuilder.append("        this.").append(fieldName)
                    .append(" = EntityConverter.convertToType(dataMap.get(\"").append(columnName)
                    .append("\"), ").append(javaType).append(".class);\n");
        }

        classBuilder.append("    }\n");
        classBuilder.append("}\n");

        // 写入文件
        String outputFile = System.getProperty("user.dir") + "/gen/src/main/java/org/sunrise/game/genDb/gen/" + fileName;
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            writer.write(classBuilder.toString());
        } catch (IOException e) {
            System.err.println("Failed to write file: " + outputFile);
            e.printStackTrace();
        }
    }

    // 获取列名（兼容MySQL 5.7和8.0）
    private static String getColumnName(Map<String, Object> column) {
        return column.containsKey("Field") ? (String) column.get("Field") : (String) column.get("COLUMN_NAME");
    }

    // 获取字段名（下划线转驼峰）
    private static String getFieldName(Map<String, Object> column) {
        return toCamelCase(getColumnName(column), false);
    }

    // 获取Java类型
    private static String getJavaType(Map<String, Object> column) {
        String sqlType = column.containsKey("Type") ? (String) column.get("Type") : (String) column.get("COLUMN_TYPE");
        return mapSQLTypeToJava(sqlType);
    }

    // 获取列注释
    private static String getColumnComment(Map<String, Object> column) {
        if (column.containsKey("Comment")) {
            return (String) column.get("Comment");
        }
        return null;
    }

    // 映射SQL类型到Java类型（全部使用基本类型，因为数据库字段都是NOT NULL）
    private static String mapSQLTypeToJava(String sqlType) {
        String originalType = sqlType.toLowerCase();

        // 拦截 MySQL 的 boolean (tinyint(1))
        if (originalType.startsWith("tinyint(1)")) {
            return "boolean";
        }

        sqlType = sqlType.toLowerCase().replaceAll("\\(.*\\)", "");

        if (sqlType.startsWith("varchar") || sqlType.startsWith("char") ||
                sqlType.startsWith("text") || sqlType.startsWith("longtext") ||
                sqlType.startsWith("mediumtext") || sqlType.startsWith("tinytext")) {
            return "String";
        } else if (sqlType.startsWith("tinyint") || sqlType.startsWith("smallint") ||
                sqlType.startsWith("int") || sqlType.startsWith("integer")) {
            return "int";
        } else if (sqlType.startsWith("bigint")) {
            return "long";
        } else if (sqlType.startsWith("double")) {
            return "double";
        } else if (sqlType.startsWith("float")) {
            return "float";
        } else if (sqlType.startsWith("decimal") || sqlType.startsWith("numeric")) {
            return "BigDecimal";
        } else if (sqlType.startsWith("boolean") || sqlType.startsWith("bit")) {
            return "boolean";
        } else if (sqlType.startsWith("datetime") || sqlType.startsWith("timestamp")) {
            return "LocalDateTime";
        } else if (sqlType.startsWith("date")) {
            return "LocalDate";
        } else if (sqlType.startsWith("time")) {
            return "LocalTime";
        } else if (sqlType.startsWith("blob") || sqlType.startsWith("mediumblob") ||
                sqlType.startsWith("longblob") || sqlType.startsWith("tinyblob")) {
            return "byte[]";
        } else {
            return "Object";
        }
    }

    // 下划线转驼峰命名
    private static String toCamelCase(String str, boolean capitalizeFirst) {
        if (str == null || str.isEmpty()) {
            return str;
        }

        StringBuilder result = new StringBuilder();
        String[] words = str.split("_");

        for (int i = 0; i < words.length; i++) {
            String word = words[i].toLowerCase();
            if (word.isEmpty()) {
                continue;
            }

            if (i == 0 && !capitalizeFirst) {
                result.append(word);
            } else {
                result.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1));
            }
        }

        return result.toString();
    }
}