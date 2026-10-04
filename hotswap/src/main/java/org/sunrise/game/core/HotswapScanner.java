package org.sunrise.game.core;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class HotswapScanner {
    /**
     * 进程级基线：启动时运行中 jar 的条目快照，代表 JVM 当前已加载类对应的旧代码。
     * 基线必须在 jar 被替换前记录，否则 diff 永远为空；成功重载后基线更新为本次重载来源 jar。
     */
    private static volatile HotswapScanner baseline;

    private final String jarPath;
    private String jarVersion = null;
    private boolean freshBaseline = false;
    private final List<String> recentReloadLogs = new ArrayList<>(8);
    // jarEntry=>EntryInfo
    private Map<String, JarEntryInfo> clazzInfos = new HashMap<>();

    static class JarEntryInfo {
        private String clazzName;
        private long crc32;
        private long size;

        public String getClazzName() {
            return clazzName;
        }

        public long getCrc32() {
            return crc32;
        }

        public long getSize() {
            return size;
        }

        public JarEntryInfo(String clazzName, long crc32, long size) {
            this.clazzName = clazzName;
            this.crc32 = crc32;
            this.size = size;
        }
    }

    private HotswapScanner(String watchJarPath) {
        try {
            this.jarPath = watchJarPath;
            File file = new File(jarPath);
            if (!file.isFile()) {
                throw new RuntimeException("定位文件错误" + watchJarPath);
            }
            this.scan();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 取进程级基线；尚不存在时以 jarPath 现建（首次 reload 会提示先替换 jar 再下发） */
    public static HotswapScanner of(String jarPath) {
        HotswapScanner b = baseline;
        if (b != null) {
            return b;
        }
        synchronized (HotswapScanner.class) {
            if (baseline == null) {
                HotswapScanner scanner = new HotswapScanner(jarPath);
                scanner.freshBaseline = true;
                baseline = scanner;
            }
            return baseline;
        }
    }

    /**
     * 启动时对进程运行中的 jar 建立热更基线，必须在 jar 被替换之前调用。
     */
    public static void registerRunningJar() {
        try {
            String location = HotswapScanner.class.getProtectionDomain().getCodeSource().getLocation().getPath();
            String path = URLDecoder.decode(location, StandardCharsets.UTF_8);
            if (path.startsWith("file:")) {
                path = path.substring("file:".length());
            }
            if (!path.endsWith(".jar")) {
                System.err.println("hotswap skip baseline, not running from a jar: " + path);
                return;
            }
            synchronized (HotswapScanner.class) {
                if (baseline == null) {
                    baseline = new HotswapScanner(path);
                }
            }
            System.err.println("hotswap baseline registered: " + path);
        } catch (Exception e) {
            System.err.println("hotswap register running jar failed: " + e);
        }
    }

    public boolean isInitialized() {
        return jarVersion != null && !clazzInfos.isEmpty();
    }

    protected void log(String str) {
        this.recentReloadLogs.add(new Date() + "===" + str);
    }

    /**
     * 将 targetJarPath 与基线 diff，重载有变化的类（新字节码取自 targetJarPath）。
     * targetJarPath 可以是基线 jar 本身（原地替换），也可以是放新 jar 的另一个路径；成功后基线更新为 targetJarPath。
     */
    public synchronized void reloadClasses(String targetJarPath) {
        this.recentReloadLogs.clear();
        boolean firstRun = freshBaseline;
        freshBaseline = false;

        if (!isInitialized()) {
            log("scanner未初始化.");
            return;
        }
        File targetFile = new File(targetJarPath);
        if (!targetFile.isFile()) {
            log("目标 jar 不存在: " + targetJarPath);
            return;
        }
        // 保存旧的
        Map<String, JarEntryInfo> savedClazzInfos = new HashMap<>(this.clazzInfos);
        String savedVersion = this.jarVersion;
        try {
            List<String> changes = this.scanChanges(targetJarPath);
            if (changes == null || changes.isEmpty()) {
                log(firstRun ? "已建立基线（当前 jar 记为基线），替换该 jar 或对新 jar 路径再次下发 hotswap 才会重载" : "reloadClass 代码未改变.");
                return;
            }
            Map<String, JarEntryInfo> changedJarEntries = new HashMap<>();
            for (String entry : changes) {
                JarEntryInfo entryInfo = this.clazzInfos.get(entry);
                changedJarEntries.put(entry, entryInfo);
                log("--changed class:" + entryInfo.clazzName);
            }
            log("reloadClass begin");
            Hotswap.reloadClasses(changedJarEntries, targetJarPath);
            log("reloadClass OK");
        } catch (Exception ex) {
            // 如果失败, 恢复
            this.clazzInfos = savedClazzInfos;
            this.jarVersion = savedVersion;
            log("reloadClassError " + ex);
        }
    }

    private static String jarEntryNameToClassName(String entryName) {
        String name = entryName.endsWith(".class") ? entryName.substring(0, entryName.length() - ".class".length()) : entryName;
        return name.replace('/', '.');
    }

    private void scan() throws IOException {
        jarVersion = getJarFileVersion(new File(jarPath));

        try (JarFile jarFile = new JarFile(jarPath)) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry jarEntry = entries.nextElement();
                if (jarEntry.isDirectory()) continue;

                String jarEntryName = jarEntry.getName();
                if (jarEntryName.endsWith(".class")) {
                    this.clazzInfos.put(jarEntry.getName(), new JarEntryInfo(jarEntryNameToClassName(jarEntryName), jarEntry.getCrc(), jarEntry.getSize()));
                }
            }
        }
    }

    private String getJarFileVersion(File source) {
        if (!source.exists()) return jarVersion;

        String contentLength = String.valueOf((source.length()));
        String lastModified = String.valueOf((source.lastModified()));
        return new StringBuilder(contentLength).append("-").append(lastModified).toString();
    }

    /** 对比 targetJarPath 与基线条目，把基线更新为 target 的内容，返回其中有变更且基线中已存在的条目（redefine 不能新增类） */
    private List<String> scanChanges(String targetJarPath) throws IOException {
        String key = getJarFileVersion(new File(targetJarPath));
        this.log(String.format("scanChanges target=%s curVersion=%s,newVersion=%s", targetJarPath, key, jarVersion));
        if (key.equals(jarVersion)) return List.of();

        List<String> changedClassFiles = new ArrayList<>(32);

        // 改变了则赋值
        jarVersion = key;

        try (JarFile jarFile = new JarFile(targetJarPath)) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry jarEntry = entries.nextElement();
                if (jarEntry.isDirectory()) continue;

                String jarEntryName = jarEntry.getName();
                if (jarEntryName.endsWith(".class")) {
                    JarEntryInfo exists = this.clazzInfos.get(jarEntryName);
                    if (exists == null || (exists.getCrc32() != jarEntry.getCrc() || exists.getSize() != jarEntry.getSize())) {
                        JarEntryInfo info = new JarEntryInfo(jarEntryNameToClassName(jarEntryName), jarEntry.getCrc(), jarEntry.getSize());
                        this.clazzInfos.put(jarEntry.getName(), info);
                        if (exists != null) changedClassFiles.add(jarEntryName);
                    }
                }
            }
        }
        return changedClassFiles;
    }

    public String getRecentLogs() {
        return this.recentReloadLogs.stream().collect(Collectors.joining("\n"));
    }
}
