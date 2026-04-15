package org.sunrise.game.utils;

import ch.qos.logback.classic.Level;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.thread.DispatchThread;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class Utils {

    public static String CLIENT_CONNECT = "CLIENT_CONNECT_";
    public static String CLIENT_CONNECT_RESPONSE = "CLIENT_CONNECT_RESPONSE_";

    public static String SUCCESS = "SUCCESS";
    public static String FAILED = "FAILED";

    public static int MSG_COUNT_MAX = 300;// 每分钟从客户端接收的最大消息数
    public static int MSG_BYTE_LEN_MAX = 64 * 1024;// 从客户端接收的消息最大长度
    public static int MAX_BODY_SIZE = 1024 * 1024; // 默认数据包大小

    public static int ID_BASE_NUM = 1000000; //连接id基数

    public static String getLocalIpAddress() {
        try {
            InetAddress inetAddress = InetAddress.getLocalHost();
            return inetAddress.getHostAddress();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取监听ip，所有节点默认都绑定在此ip上
     */
    public static String getListenIpAddress() {
        return "0.0.0.0";
    }

    public static void setShutdownHook(Runnable task) {
        Runtime.getRuntime().addShutdownHook(new Thread(task, "ServerShutdown"));
    }

    public static boolean isWin() {
        return System.getProperty("os.name").startsWith("Windows");
    }

    public static boolean isLinux() {
        return "Linux".equals(System.getProperty("os.name"));
    }

    public static List<Class<?>> findClasses(String packageName) throws ClassNotFoundException, IOException, URISyntaxException {
        List<Class<?>> classes = new ArrayList<>();
        String path = packageName.replace('.', '/');
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        URL resource = classLoader.getResource(path);

        if (resource == null) {
            throw new ClassNotFoundException("Package " + packageName + " not found.");
        }

        // 判断是否是在一个JAR包里
        if (resource.getProtocol().equals("jar")) {
            // 处理JAR文件内的情况
            String jarPath = resource.getPath().substring(5, resource.getPath().indexOf("!"));
            try (JarFile jarFile = new JarFile(jarPath)) {
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String entryName = entry.getName();
                    if (entryName.startsWith(path) && entryName.endsWith(".class")) {
                        String className = entryName.replace('/', '.').substring(0, entryName.length() - 6);
                        classes.add(Class.forName(className));
                    }
                }
            }
        } else {
            // 处理文件系统内的情况（IDEA等环境）
            File directory = new File(resource.toURI());
            if (directory.exists()) {
                classes.addAll(findClassesInDirectory(directory, packageName));
            }
        }
        return classes;
    }

    private static List<Class<?>> findClassesInDirectory(File directory, String packageName) throws ClassNotFoundException {
        List<Class<?>> classes = new ArrayList<>();
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    classes.addAll(findClassesInDirectory(file, packageName + "." + file.getName()));
                } else if (file.getName().endsWith(".class")) {
                    String className = packageName + '.' + file.getName().substring(0, file.getName().length() - 6);
                    classes.add(Class.forName(className));
                }
            }
        }
        return classes;
    }

    private static void check() {
        StringBuilder sb = new StringBuilder();

        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory(); // 当前JVM堆内存总量
        long freeMemory = runtime.freeMemory(); // 当前JVM堆内存空闲量
        long usedMemory = totalMemory - freeMemory; // 当前JVM堆内存已使用量
        sb.append(" total memory: ").append(totalMemory / 1024 / 1024).append(" MB").append(",");
        sb.append(" free memory: ").append(freeMemory / 1024 / 1024).append(" MB").append(",");
        sb.append(" used memory: ").append(usedMemory / 1024 / 1024).append(" MB");

        LogCore.ServerStartUp.info("Memory : { {} }", sb);
    }

    public static void startMemoryCheck() {
        DispatchThread checkThread = new DispatchThread(Utils::check, "CheckMemory");
        checkThread.setInterval(60000);
        checkThread.start();
    }

    public static void setLogLevel(String logLevel) {
        switch (logLevel.toUpperCase()) {
            case "TRACE":
                LogCore.setLogLevel("root", Level.TRACE);
                break;
            case "DEBUG":
                LogCore.setLogLevel("root", Level.DEBUG);
                break;
            case "WARN":
                LogCore.setLogLevel("root", Level.WARN);
                break;
            case "ERROR":
                LogCore.setLogLevel("root", Level.ERROR);
                break;
            case "INFO":
            default:
                LogCore.setLogLevel("root", Level.INFO);
                break;
        }
    }
}
