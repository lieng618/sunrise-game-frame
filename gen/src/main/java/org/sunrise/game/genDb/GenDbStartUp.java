package org.sunrise.game.genDb;

import org.sunrise.game.config.ConfigReader;
import org.sunrise.game.db.DbService;

import java.io.BufferedWriter;
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

        // 获取所有表名
        List<String> tableNames = getAllTableNames();

        // 为每个表生成实体类
        for (String tableName : tableNames) {
            String classCode = generateEntityClass(tableName);
            System.out.println("Generated: " + classCode);
        }
    }

    // 获取数据库中的所有表名
    private static List<String> getAllTableNames() {
        String query = "SHOW TABLES";
        return dbService.queryAllSingleColumn(query);
    }

    // 生成实体类代码并保存为文件
    public static String generateEntityClass(String tableName) {
        String className = "Entity" + capitalize(tableName);
        String fileName = className + ".java";

        StringBuilder classBuilder = new StringBuilder();
        classBuilder.append("package org.sunrise.game.genDb.gen;\n\n")
                .append("import lombok.Data;\n")
                .append("import java.time.LocalDateTime;\n")
                .append("import java.util.Map;\n")
                .append("\n@Data\n")
                .append("public class ").append(className).append(" {\n");

        // 查询表结构
        String query = "DESCRIBE " + tableName;
        List<Map<String, Object>> maps = dbService.queryAll(query);

        for (Map<String, Object> map : maps) {
            // 获取字段名和类型，兼容MySQL 8.0和5.7
            String field = map.containsKey("Field") ? (String) map.get("Field") : (String) map.get("COLUMN_NAME");
            String type = map.containsKey("Type") ? (String) map.get("Type") : (String) map.get("COLUMN_TYPE");
            type = mapSQLTypeToJava(type);

            classBuilder.append("    private final ").append(type)
                    .append(" ").append(field).append(";\n");
        }

        // 添加构造函数
        classBuilder.append("\n    public ").append(className)
                .append("(Map<String, Object> dataMap) {\n");

        for (Map<String, Object> map : maps) {
            String field = map.containsKey("Field") ? (String) map.get("Field") : (String) map.get("COLUMN_NAME");
            String type = map.containsKey("Type") ? (String) map.get("Type") : (String) map.get("COLUMN_TYPE");
            type = mapSQLTypeToJava(type);

            classBuilder.append("        this.").append(field)
                    .append(" = (").append(type).append(") dataMap.get(\"")
                    .append(field).append("\");\n");
        }

        classBuilder.append("    }\n");
        classBuilder.append("}\n");

        String outputFile = System.getProperty("user.dir") + "/gen/src/main/java/org/sunrise/game/genDb/gen/" + fileName;
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            writer.write(classBuilder.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }

        return tableName;
    }

    // 映射SQL类型到Java类型
    private static String mapSQLTypeToJava(String sqlType) {
        sqlType = sqlType.toLowerCase();

        // 去掉类型中的括号和长度（如int(11) -> int）
        sqlType = sqlType.replaceAll("\\(.*\\)", "");  // 正则去除括号及其中内容

        if (sqlType.startsWith("varchar") || sqlType.startsWith("char") || sqlType.startsWith("text") || sqlType.startsWith("longtext")) {
            return "String";
        } else if (sqlType.startsWith("int")) {
            return "int";
        } else if (sqlType.startsWith("bigint")) {
            return "long";
        } else if (sqlType.startsWith("double") || sqlType.startsWith("float") || sqlType.startsWith("real")) {
            return "double";
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
        } else if (sqlType.startsWith("mediumblob")) {
            return "byte[]";
        } else {
            return "Object";
        }
    }

    // 将表名转换为类名
    private static String capitalize(String str) {
        StringBuilder result = new StringBuilder();
        String[] words = str.split("_");
        for (String word : words) {
            result.append(word.substring(0, 1).toUpperCase()).append(word.substring(1).toLowerCase());
        }
        return result.toString();
    }
}
