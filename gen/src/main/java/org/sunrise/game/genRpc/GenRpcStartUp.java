package org.sunrise.game.genRpc;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class GenRpcStartUp {

    private static final Pattern PACKAGE_PATTERN = Pattern.compile("package\\s+([\\w.]+)\\s*;");
    private static final Pattern CLASS_PATTERN = Pattern.compile("public\\s+class\\s+(\\w+)");
    private static final Pattern INTERFACE_PATTERN = Pattern.compile("public\\s+interface\\s+(\\w+)");
    private static final Pattern STUB_METHOD_PATTERN = Pattern.compile("\\s+(\\w+)\\s*\\(\\s*\\)\\s*;");
    private static final Pattern RPC_METHOD_PATTERN = Pattern.compile(
            "@RpcMethod\\s+public\\s+[\\w.<>,\\[\\]\\s]+\\s+(\\w+)\\s*\\(([^)]*)\\)",
            Pattern.MULTILINE
    );

    public static void main(String[] args) throws Exception {
        String projectRoot = System.getProperty("user.dir");
        String outputFile = Path.of(projectRoot, "gen/src/main/java/org/sunrise/game/genRpc/gen/CallEnum.java")
                .toString();

        List<StubRpcMethod> stubMethods = loadStubMethods(
                Path.of(projectRoot, "gen/src/main/java/org/sunrise/game/genRpc/service"));
        Map<String, String> rpcImplRefs = buildRpcImplRefs(projectRoot);
        gen(stubMethods, outputFile, rpcImplRefs);

        System.out.println("success");
    }

    private static void gen(List<StubRpcMethod> stubMethods, String outputFile, Map<String, String> rpcImplRefs) {
        Map<String, Integer> oldMap = loadOld(outputFile);
        int maxId = oldMap.values().stream().max(Integer::compareTo).orElse(0);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            String packageName = outputFile
                    .substring(outputFile.indexOf("org"), outputFile.lastIndexOf(File.separatorChar))
                    .replace(File.separatorChar, '.');

            writer.write("package " + packageName + ";\n\n");
            writer.write("/**\n");
            writer.write(" * RPC 调用 ID，由 {@link org.sunrise.game.genRpc.GenRpcStartUp} 自动生成，不要手动修改。\n");
            writer.write(" */\n");
            writer.write("public class CallEnum {\n");

            stubMethods.sort(Comparator.comparing(StubRpcMethod::className).thenComparing(StubRpcMethod::methodName));

            for (StubRpcMethod stub : stubMethods) {
                String enumName = stub.className() + "_" + stub.methodName();
                int id = oldMap.containsKey(enumName) ? oldMap.get(enumName) : ++maxId;

                String implRef = rpcImplRefs.get(enumName);
                if (implRef != null) {
                    writer.write("    /** {@code " + implRef + "} */\n");
                }
                writer.write("    public static final int " + enumName + " = " + id + ";\n");
            }

            writer.write("}\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<StubRpcMethod> loadStubMethods(Path serviceDir) throws IOException {
        List<StubRpcMethod> methods = new ArrayList<>();
        if (!Files.isDirectory(serviceDir)) {
            throw new IOException("Stub service directory not found: " + serviceDir);
        }
        try (Stream<Path> paths = Files.list(serviceDir)) {
            List<Path> javaFiles = paths.filter(path -> path.toString().endsWith(".java"))
                    .sorted(Comparator.comparing(Path::getFileName))
                    .toList();
            for (Path javaFile : javaFiles) {
                methods.addAll(parseStubServiceFile(javaFile));
            }
        }
        return methods;
    }

    private static List<StubRpcMethod> parseStubServiceFile(Path javaFile) throws IOException {
        List<StubRpcMethod> methods = new ArrayList<>();
        String content = Files.readString(javaFile);

        Matcher interfaceMatcher = INTERFACE_PATTERN.matcher(content);
        if (!interfaceMatcher.find()) {
            return methods;
        }
        String className = interfaceMatcher.group(1);

        Matcher methodMatcher = STUB_METHOD_PATTERN.matcher(content);
        while (methodMatcher.find()) {
            methods.add(new StubRpcMethod(className, methodMatcher.group(1)));
        }
        return methods;
    }

    private record StubRpcMethod(String className, String methodName) {
    }

    private static Map<String, String> buildRpcImplRefs(String projectRoot) {
        Map<String, String> refs = new HashMap<>();
        Path sourceRoot = Path.of(projectRoot, "game/src/main/java/org/sunrise/game");
        if (!Files.isDirectory(sourceRoot)) {
            System.out.println("warn: game source root not found, CallEnum will be generated without {@code} comments.");
            return refs;
        }

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> indexRpcSourceFile(path, refs));
        } catch (IOException e) {
            throw new RuntimeException("Failed to index RPC source methods from game module", e);
        }
        return refs;
    }

    private static void indexRpcSourceFile(Path javaFile, Map<String, String> refs) {
        try {
            String content = Files.readString(javaFile);
            if (!content.contains("@RpcService")) {
                return;
            }

            Matcher packageMatcher = PACKAGE_PATTERN.matcher(content);
            if (!packageMatcher.find()) {
                return;
            }
            String packageName = packageMatcher.group(1);

            Matcher classMatcher = CLASS_PATTERN.matcher(content);
            if (!classMatcher.find()) {
                return;
            }
            String className = classMatcher.group(1);
            String fqn = packageName + "." + className;

            Matcher methodMatcher = RPC_METHOD_PATTERN.matcher(content);
            while (methodMatcher.find()) {
                String methodName = methodMatcher.group(1);
                String paramTypes = parseParamTypes(methodMatcher.group(2));
                String key = className + "_" + methodName;
                refs.put(key, buildMethodRef(fqn, methodName, paramTypes));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read RPC source file: " + javaFile, e);
        }
    }

    private static String parseParamTypes(String paramsPart) {
        if (paramsPart == null || paramsPart.isBlank()) {
            return "";
        }
        List<String> types = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (char c : paramsPart.trim().toCharArray()) {
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                depth--;
            }
            if (c == ',' && depth == 0) {
                types.add(extractParamType(current.toString()));
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            types.add(extractParamType(current.toString()));
        }
        return String.join(",", types);
    }

    private static String extractParamType(String param) {
        String trimmed = param.trim();
        int lastSpace = trimmed.lastIndexOf(' ');
        if (lastSpace > 0) {
            return trimmed.substring(0, lastSpace).trim();
        }
        return trimmed;
    }

    private static String buildMethodRef(String fqn, String methodName, String paramTypes) {
        StringBuilder sb = new StringBuilder();
        sb.append(fqn).append("#").append(methodName);
        if (!paramTypes.isEmpty()) {
            sb.append("(").append(paramTypes).append(")");
        }
        return sb.toString();
    }

    private static Map<String, Integer> loadOld(String file) {
        Map<String, Integer> map = new HashMap<>();

        File f = new File(file);
        if (!f.exists()) {
            return map;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("public static final int")) {
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
