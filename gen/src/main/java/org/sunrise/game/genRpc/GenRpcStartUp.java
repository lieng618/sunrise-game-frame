package org.sunrise.game.genRpc;

import org.sunrise.game.utils.Utils;

import java.io.*;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GenRpcStartUp {

    public static void main(String[] args) throws Exception {
        String[] packageNames = {
                "org.sunrise.game.genRpc.service",
        };

        String outputFile =
                System.getProperty("user.dir") + "/gen/src/main/java/org/sunrise/game/genRpc/gen/CallEnum.java";

        List<Class<?>> classes = new ArrayList<>();
        for (String packageName : packageNames) {
            classes.addAll(Utils.findClasses(packageName));
        }

        gen(classes, outputFile);

        System.out.println("success");
    }

    private static void gen(List<Class<?>> classes, String outputFile) {

        // 1. 读取旧映射
        Map<String, Integer> oldMap = loadOld(outputFile);

        // 2. 找最大ID
        int maxId = oldMap.values().stream().max(Integer::compareTo).orElse(0);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {

            String packageName = outputFile
                    .substring(outputFile.indexOf("org"), outputFile.lastIndexOf("/"))
                    .replace("/", ".");

            writer.write("package " + packageName + ";\n\n");
            writer.write("public class CallEnum {\n");

            // 3. 排序 class
            classes.sort(Comparator.comparing(Class::getName));

            for (Class<?> clazz : classes) {

                // 4. 排序 method
                Method[] methods = clazz.getDeclaredMethods();
                Arrays.sort(methods, Comparator.comparing(Method::getName));

                for (Method method : methods) {

                    String enumName = clazz.getSimpleName() + "_" + method.getName();

                    int id;

                    // 5. 复用 or 分配
                    if (oldMap.containsKey(enumName)) {
                        id = oldMap.get(enumName);
                    } else {
                        id = ++maxId;
                    }

                    writer.write("    public static final int " + enumName + " = " + id + ";\n");
                }
            }

            writer.write("}\n");

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<String, Integer> loadOld(String file) {
        Map<String, Integer> map = new HashMap<>();

        File f = new File(file);
        if (!f.exists()) return map;

        try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
            String line;

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                if (line.startsWith("public static final int")) {
                    // public static final int XXX = 123;
                    String[] parts = line.split("\\s+");
                    String name = parts[4];
                    int value = Integer.parseInt(parts[6].replace(";", ""));
                    map.put(name, value);
                }
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return map;
    }
}