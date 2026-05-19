package org.sunrise.game.core;

import java.io.File;
import java.io.IOException;
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
    private final String jarPath;
    private String jarVersion = null;
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

    public HotswapScanner(String watchJarPath) {
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

    public boolean isInitialized() {
        return jarVersion != null && !clazzInfos.isEmpty();
    }

    protected void log(String str) {
        this.recentReloadLogs.add(new Date() + "===" + str);
    }

    public synchronized void reloadClasses() {
        this.recentReloadLogs.clear();

        if (!isInitialized()) {
            log("scanner未初始化.");
            return;
        }
        // 保存旧的
        Map<String, JarEntryInfo> savedClazzInfos = new HashMap<>(this.clazzInfos);
        try {
            List<String> changes = this.scanChanges();
            if (changes == null || changes.isEmpty()) {
                log("reloadClass 代码未改变.");
                return;
            }
            Map<String, JarEntryInfo> changedJarEntries = new HashMap<>();
            for (String entry : changes) {
                JarEntryInfo entryInfo = this.clazzInfos.get(entry);
                changedJarEntries.put(entry, entryInfo);
                log("--changed class:" + entryInfo.clazzName);
            }
            log("reloadClass begin");
            Hotswap.reloadClasses(changedJarEntries, jarPath);
            log("reloadClass OK");
        } catch (Exception ex) {
            // 如果失败, 恢复
            this.clazzInfos = savedClazzInfos;
            log("reloadClassError " + ex);
        }
    }

    private static String jarEntryNameToClassName(String entryName) {
        return entryName.replaceAll(".class", "").replaceAll("/", ".");
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

    private List<String> scanChanges() throws IOException {
        String key = getJarFileVersion(new File(jarPath));
        this.log(String.format("scanChanges jar=%s curVersion=%s,newVersion=%s", jarPath, key, jarVersion));
        if (key.equals(jarVersion)) return List.of();

        List<String> changedClassFiles = new ArrayList<>(32);

        // 改变了则赋值
        jarVersion = key;

        try (JarFile jarFile = new JarFile(jarPath)) {
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
