package org.sunrise.game.genRpc;

import org.sunrise.game.rpc.annotation.RpcMethod;
import org.sunrise.game.rpc.annotation.RpcService;
import org.sunrise.game.utils.Utils;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class GenRpcStartUp {

    public static void main(String[] args) throws Exception {
        String[] packageNames = {
                "org.sunrise.game.genRpc.service",
        };

        String[] outputFiles = {
                System.getProperty("user.dir") + "/gen/src/main/java/org/sunrise/game/genRpc/gen/CallEnum.java"
        };

        List<Class<?>> classes = new ArrayList<>();
        for (String packageName : packageNames) {
            classes.addAll(Utils.findClasses(packageName));
        }
        for (String output : outputFiles) {
            gen(classes, output);
        }
        System.out.println("success");
    }

    private static void gen(List<Class<?>> classes, String outputFile) {
        AtomicInteger counter = new AtomicInteger(1);
        Set<String> enumNamesSet = new HashSet<>();  // 用于存储已生成的enumName
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            String packageName = outputFile.substring(outputFile.indexOf("org"), outputFile.lastIndexOf("/"))
                    .replace("/", ".");

            writer.write("package " + packageName + ";\n\n");
            writer.write("public class CallEnum {\n");

            for (Class<?> clazz : classes) {
//                if (clazz.isAnnotationPresent(RpcService.class)) {
                    for (Method method : clazz.getDeclaredMethods()) {
//                        if (method.isAnnotationPresent(RpcMethod.class)) {
                            String enumName = clazz.getSimpleName() + "_" + method.getName();

                            // 检查是否已经存在相同的enumName
                            if (!enumNamesSet.add(enumName)) {
                                throw new RuntimeException("生成的enumName重复: " + enumName + "，请检查代码。");
                            }

                            writer.write("    public static final int " + enumName + " = " + counter.getAndIncrement() + ";\n");
//                        }
                    }
//                }
            }

            writer.write("}\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
