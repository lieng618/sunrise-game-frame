package org.sunrise.game.core;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 进程级业务代码热更基线。
 *
 * <p>基线不是“启动参数里那个 jar”，而是当前 JVM 中允许热更的类实际来自哪些
 * CodeSource。单进程、多进程和组合部署因此可以使用同一份全量补丁包。</p>
 */
public final class HotswapScanner {
    private static final String DEFAULT_INCLUDE_PACKAGES = "org.sunrise.game";
    private static final String DEFAULT_EXCLUDE_CLASSES = String.join(",",
            "org.sunrise.game.core.Hotswap",
            "org.sunrise.game.core.HotswapScanner",
            "org.sunrise.game.core.AgentBootstrap",
            "org.sunrise.game.rpc.hotswap",
            "org.sunrise.game.genRpc.gen.CallEnum");

    private static volatile HotswapScanner instance;

    private final List<String> includePrefixes;
    private final List<String> excludePrefixes;
    private final Map<String, ClassInfo> currentClasses = new HashMap<>();
    private final Set<String> baselineJars = new TreeSet<>();

    private boolean initialized;
    private String activeTargetPath;
    private String previousTargetPath;
    private String activeVersion;
    private volatile Report lastReport;

    public static final class ClassInfo {
        private final String className;
        private final String entryName;
        private final String digest;
        private final long size;
        private final String source;

        private ClassInfo(String className, String entryName, String digest, long size, String source) {
            this.className = className;
            this.entryName = entryName;
            this.digest = digest;
            this.size = size;
            this.source = source;
        }

        public String getClassName() {
            return className;
        }

        public String getEntryName() {
            return entryName;
        }

        public String getDigest() {
            return digest;
        }

        public long getSize() {
            return size;
        }

        public String getSource() {
            return source;
        }
    }

    /** 可直接由 RPC/GM 序列化的热更结果。 */
    public static final class Report {
        private String status;
        private String message;
        private boolean initialized;
        private boolean agentLoaded;
        private String targetPath;
        private String targetVersion;
        private String activeVersion;
        private String previousTargetPath;
        private int targetClassCount;
        private int unchangedClassCount;
        private List<String> baselineJars = List.of();
        private List<String> requestedClasses = List.of();
        private List<String> redefinedClasses = List.of();
        private List<String> skippedNotLoadedClasses = List.of();
        private List<String> skippedUnsupportedClasses = List.of();
        private List<String> failedClasses = List.of();

        public String getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }

        public boolean isInitialized() {
            return initialized;
        }

        public boolean isAgentLoaded() {
            return agentLoaded;
        }

        public String getTargetPath() {
            return targetPath;
        }

        public String getTargetVersion() {
            return targetVersion;
        }

        public String getActiveVersion() {
            return activeVersion;
        }

        public String getPreviousTargetPath() {
            return previousTargetPath;
        }

        public int getTargetClassCount() {
            return targetClassCount;
        }

        public int getUnchangedClassCount() {
            return unchangedClassCount;
        }

        public List<String> getBaselineJars() {
            return baselineJars;
        }

        public List<String> getRequestedClasses() {
            return requestedClasses;
        }

        public List<String> getRedefinedClasses() {
            return redefinedClasses;
        }

        public List<String> getSkippedNotLoadedClasses() {
            return skippedNotLoadedClasses;
        }

        public List<String> getSkippedUnsupportedClasses() {
            return skippedUnsupportedClasses;
        }

        public List<String> getFailedClasses() {
            return failedClasses;
        }
    }

    private HotswapScanner(String includePackages, String excludeClasses) {
        this.includePrefixes = parsePrefixes(includePackages, DEFAULT_INCLUDE_PACKAGES);
        this.excludePrefixes = parsePrefixes(excludeClasses, DEFAULT_EXCLUDE_CLASSES);
    }

    /**
     * 在 RPC 服务类和启动类加载后建立基线。没有 Agent 时也会生成状态报告，
     * 由 GM 明确提示节点需要重启并加载 -javaagent。
     */
    public static synchronized HotswapScanner initialize(String includePackages, String excludeClasses) {
        if (instance == null) {
            instance = new HotswapScanner(includePackages, excludeClasses);
        }
        instance.captureRuntimeBaseline();
        return instance;
    }

    public static HotswapScanner getInstance() {
        return instance;
    }

    public synchronized Report status(String message) {
        if (lastReport == null) {
            return buildBaseReport("UNINITIALIZED",
                    message == null ? "热更基线尚未初始化" : message);
        }
        Report report = copyForResponse(lastReport);
        if (message != null && !message.isBlank()) {
            report.message = message;
        }
        return report;
    }

    /**
     * 将全量业务补丁与当前进程的已加载类求交集，并返回实际重定义结果。
     */
    public synchronized Report reload(String targetJarPath) {
        if (targetJarPath == null || targetJarPath.isBlank()) {
            return rememberReport(buildBaseReport("FAILED", "目标 jar 路径为空"));
        }

        Instrumentation instrumentation = Hotswap.getInstrumentation();
        if (instrumentation == null) {
            return rememberReport(buildBaseReport("FAILED",
                    "hotswap agent 未加载，请使用 -javaagent:hotswap-agent.jar 启动该进程"));
        }
        if (!initialized) {
            captureRuntimeBaseline();
        }
        if (!initialized) {
            return rememberReport(buildBaseReport("FAILED", "热更基线初始化失败"));
        }

        Path target = Path.of(targetJarPath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(target)) {
            return rememberReport(buildBaseReport("FAILED", "目标 jar 不存在: " + target));
        }

        final Map<String, ClassInfo> targetClasses;
        final String targetVersion;
        try {
            targetClasses = scanClassSource(target);
            targetVersion = sha256(target);
        } catch (Exception e) {
            return rememberReport(buildBaseReport("FAILED", "读取目标 jar 失败: " + e.getMessage()));
        }
        if (targetClasses.isEmpty()) {
            return rememberReport(buildBaseReport("FAILED",
                    "目标 jar 中没有可热更的业务 class: " + target));
        }

        Map<String, List<Class<?>>> loadedClasses = collectLoadedClasses(instrumentation);
        Map<String, ClassInfo> refreshedCurrent = new HashMap<>(currentClasses);
        refreshMissingLoadedOrigins(instrumentation, loadedClasses, refreshedCurrent);

        List<String> requested = new ArrayList<>();
        List<String> notLoaded = new ArrayList<>();
        List<String> unsupported = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        Map<Class<?>, ClassInfo> pending = new HashMap<>();
        int unchanged = 0;

        for (ClassInfo targetClass : targetClasses.values().stream()
                .sorted(Comparator.comparing(ClassInfo::getClassName))
                .toList()) {
            ClassInfo current = refreshedCurrent.get(targetClass.className);
            if (current != null && current.digest.equals(targetClass.digest)) {
                unchanged++;
                continue;
            }

            requested.add(targetClass.className);
            List<Class<?>> classes = loadedClasses.get(targetClass.className);
            if (classes == null || classes.isEmpty()) {
                if (current == null) {
                    unsupported.add(targetClass.className + " (target-only class cannot be defined by redefineClasses)");
                } else {
                    notLoaded.add(targetClass.className);
                }
                continue;
            }
            if (current == null) {
                unsupported.add(targetClass.className + " (no baseline class found)");
                continue;
            }

            for (Class<?> clazz : classes) {
                if (clazz.getClassLoader() == null) {
                    unsupported.add(clazz.getName() + " (bootstrap class loader)");
                } else if (!instrumentation.isModifiableClass(clazz)) {
                    unsupported.add(clazz.getName() + " (class is not modifiable)");
                } else {
                    pending.put(clazz, targetClass);
                }
            }
        }

        List<String> redefined = new ArrayList<>();
        if (!pending.isEmpty()) {
            try (JarFile jarFile = new JarFile(target.toFile())) {
                ClassDefinition[] definitions = new ClassDefinition[pending.size()];
                int index = 0;
                for (Map.Entry<Class<?>, ClassInfo> entry : pending.entrySet()) {
                    byte[] bytes = readJarEntry(jarFile, entry.getValue().entryName);
                    definitions[index++] = new ClassDefinition(entry.getKey(), bytes);
                }
                Hotswap.redefine(definitions);
                for (Map.Entry<Class<?>, ClassInfo> entry : pending.entrySet()) {
                    redefined.add(entry.getKey().getName());
                    refreshedCurrent.put(entry.getValue().className, entry.getValue());
                }
            } catch (Exception e) {
                failed.add("redefineClasses: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }

        boolean reloadSucceeded = failed.isEmpty();
        if (reloadSucceeded && !redefined.isEmpty()) {
            currentClasses.clear();
            currentClasses.putAll(refreshedCurrent);
            if (activeTargetPath != null && !activeTargetPath.equals(target.toString())) {
                previousTargetPath = activeTargetPath;
            }
            activeTargetPath = target.toString();
            activeVersion = targetVersion;
        } else if (reloadSucceeded && requested.isEmpty()) {
            activeVersion = targetVersion;
        }

        String status;
        String message;
        if (!failed.isEmpty()) {
            status = "FAILED";
            message = "类重定义失败，本批修改未生效";
        } else if (!redefined.isEmpty()) {
            status = !notLoaded.isEmpty() || !unsupported.isEmpty() ? "PARTIAL" : "SUCCESS";
            message = "实际重定义 " + redefined.size() + " 个类";
        } else if (requested.isEmpty()) {
            status = "NO_CHANGE";
            message = "目标补丁与当前已应用代码一致";
        } else {
            status = "NO_APPLICABLE";
            message = "补丁中有代码变化，但当前进程没有可应用的已加载类";
        }

        Report report = baseReport(status, message);
        report.targetPath = target.toString();
        report.targetVersion = targetVersion;
        report.targetClassCount = targetClasses.size();
        report.unchangedClassCount = unchanged;
        report.requestedClasses = List.copyOf(new LinkedHashSet<>(requested));
        report.redefinedClasses = List.copyOf(new LinkedHashSet<>(redefined));
        report.skippedNotLoadedClasses = List.copyOf(new LinkedHashSet<>(notLoaded));
        report.skippedUnsupportedClasses = List.copyOf(new LinkedHashSet<>(unsupported));
        report.failedClasses = List.copyOf(new LinkedHashSet<>(failed));
        return rememberReport(report);
    }

    public synchronized Report rollback() {
        if (previousTargetPath == null || previousTargetPath.isBlank()) {
            return rememberReport(buildBaseReport("FAILED", "没有可回滚的上一版本补丁"));
        }
        return reload(previousTargetPath);
    }

    private void captureRuntimeBaseline() {
        currentClasses.clear();
        baselineJars.clear();
        activeTargetPath = null;
        previousTargetPath = null;
        activeVersion = null;

        Instrumentation instrumentation = Hotswap.getInstrumentation();
        if (instrumentation == null) {
            initialized = false;
            lastReport = buildBaseReport("FAILED",
                    "hotswap agent 未加载，无法读取已加载类");
            return;
        }

        Map<String, String> loadedOrigins = new HashMap<>();
        for (Class<?> clazz : instrumentation.getAllLoadedClasses()) {
            if (clazz.isArray() || clazz.isPrimitive() || !isAllowed(clazz.getName())) {
                continue;
            }
            String source = codeSourcePath(clazz);
            if (source != null) {
                baselineJars.add(source);
                loadedOrigins.put(clazz.getName(), source);
            }
        }

        try {
            for (String source : baselineJars) {
                Map<String, ClassInfo> entries = scanClassSource(Path.of(source));
                for (ClassInfo info : entries.values()) {
                    ClassInfo existing = currentClasses.get(info.className);
                    String actualSource = loadedOrigins.get(info.className);
                    if (existing == null || (actualSource != null && actualSource.equals(info.source))) {
                        currentClasses.put(info.className, info);
                    }
                }
            }
            initialized = !currentClasses.isEmpty();
            if (initialized) {
                String message = "热更基线已建立，来源 JAR 数=" + baselineJars.size()
                        + "，业务类数=" + currentClasses.size();
                lastReport = buildBaseReport("READY", message);
                System.err.println("[hotswap] " + message + "，来源=" + baselineJars);
            } else {
                lastReport = buildBaseReport("FAILED", "没有从运行中 ClassLoader 发现可热更类");
            }
        } catch (Exception e) {
            initialized = false;
            lastReport = buildBaseReport("FAILED", "建立热更基线失败: " + e.getMessage());
            System.err.println("[hotswap] " + lastReport.message);
        }
    }

    private void refreshMissingLoadedOrigins(
            Instrumentation instrumentation,
            Map<String, List<Class<?>>> loadedClasses,
            Map<String, ClassInfo> refreshedCurrent) {
        Set<String> additionalSources = new TreeSet<>();
        for (Map.Entry<String, List<Class<?>>> entry : loadedClasses.entrySet()) {
            if (refreshedCurrent.containsKey(entry.getKey())) {
                continue;
            }
            for (Class<?> clazz : entry.getValue()) {
                String source = codeSourcePath(clazz);
                if (source != null) {
                    additionalSources.add(source);
                }
            }
        }
        for (String source : additionalSources) {
            try {
                refreshedCurrent.putAll(scanClassSource(Path.of(source)));
                baselineJars.add(source);
            } catch (Exception e) {
                System.err.println("[hotswap] 补充扫描类来源失败: " + source + ", " + e.getMessage());
            }
        }
    }

    private Map<String, List<Class<?>>> collectLoadedClasses(Instrumentation instrumentation) {
        Map<String, List<Class<?>>> result = new HashMap<>();
        for (Class<?> clazz : instrumentation.getAllLoadedClasses()) {
            if (clazz.isArray() || clazz.isPrimitive() || !isAllowed(clazz.getName())) {
                continue;
            }
            result.computeIfAbsent(clazz.getName(), ignored -> new ArrayList<>()).add(clazz);
        }
        return result;
    }

    private Map<String, ClassInfo> scanClassSource(Path source) throws IOException {
        Map<String, ClassInfo> result = new HashMap<>();
        if (Files.isDirectory(source)) {
            try (var paths = Files.walk(source)) {
                for (Path file : paths.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".class"))
                        .toList()) {
                    String entryName = source.relativize(file).toString().replace('\\', '/');
                    String className = entryNameToClassName(entryName);
                    if (!isAllowed(className)) {
                        continue;
                    }
                    result.put(className, new ClassInfo(className, entryName,
                            sha256(Files.readAllBytes(file)), Files.size(file), source.toString()));
                }
            }
            return result;
        }

        try (JarFile jarFile = new JarFile(source.toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                    continue;
                }
                String className = entryNameToClassName(entry.getName());
                if (!isAllowed(className)) {
                    continue;
                }
                byte[] bytes = readJarEntry(jarFile, entry.getName());
                result.put(className, new ClassInfo(className, entry.getName(),
                        sha256(bytes), bytes.length, source.toString()));
            }
        }
        return result;
    }

    private String codeSourcePath(Class<?> clazz) {
        CodeSource codeSource = clazz.getProtectionDomain().getCodeSource();
        if (codeSource == null || codeSource.getLocation() == null) {
            return null;
        }
        try {
            URI uri = codeSource.getLocation().toURI();
            Path path = Path.of(uri).toAbsolutePath().normalize();
            return Files.exists(path) ? path.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isAllowed(String className) {
        boolean included = includePrefixes.stream().anyMatch(className::startsWith);
        if (!included) {
            return false;
        }
        return excludePrefixes.stream().noneMatch(className::startsWith);
    }

    private static List<String> parsePrefixes(String configured, String defaults) {
        String value = configured == null || configured.isBlank() ? defaults : configured;
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(prefix -> !prefix.isEmpty())
                .toList();
    }

    private static String entryNameToClassName(String entryName) {
        String name = entryName.endsWith(".class")
                ? entryName.substring(0, entryName.length() - ".class".length())
                : entryName;
        return name.replace('/', '.');
    }

    private static byte[] readJarEntry(JarFile jarFile, String entryName) throws IOException {
        JarEntry entry = jarFile.getJarEntry(entryName);
        if (entry == null) {
            throw new IOException("jar entry not found: " + entryName);
        }
        try (InputStream inputStream = jarFile.getInputStream(entry)) {
            return inputStream.readAllBytes();
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static String sha256(Path path) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
        try (InputStream inputStream = Files.newInputStream(path)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = inputStream.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private Report buildBaseReport(String status, String message) {
        return baseReport(status, message);
    }

    private Report baseReport(String status, String message) {
        Report report = new Report();
        report.status = status;
        report.message = message;
        report.initialized = initialized;
        report.agentLoaded = Hotswap.isAgentLoaded();
        report.targetPath = activeTargetPath;
        report.targetVersion = activeVersion;
        report.activeVersion = activeVersion;
        report.previousTargetPath = previousTargetPath;
        report.baselineJars = List.copyOf(baselineJars);
        return report;
    }

    private Report rememberReport(Report report) {
        this.lastReport = copyForResponse(report);
        System.err.println("[hotswap] " + report.status + ": " + report.message);
        return copyForResponse(report);
    }

    private Report copyForResponse(Report source) {
        Report copy = new Report();
        copy.status = source.status;
        copy.message = source.message;
        copy.initialized = source.initialized;
        copy.agentLoaded = source.agentLoaded;
        copy.targetPath = source.targetPath;
        copy.targetVersion = source.targetVersion;
        copy.activeVersion = source.activeVersion;
        copy.previousTargetPath = source.previousTargetPath;
        copy.targetClassCount = source.targetClassCount;
        copy.unchangedClassCount = source.unchangedClassCount;
        copy.baselineJars = List.copyOf(source.baselineJars);
        copy.requestedClasses = List.copyOf(source.requestedClasses);
        copy.redefinedClasses = List.copyOf(source.redefinedClasses);
        copy.skippedNotLoadedClasses = List.copyOf(source.skippedNotLoadedClasses);
        copy.skippedUnsupportedClasses = List.copyOf(source.skippedUnsupportedClasses);
        copy.failedClasses = List.copyOf(source.failedClasses);
        return copy;
    }
}
