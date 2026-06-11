package org.sunrise.game.utils;

import ch.qos.logback.classic.Level;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollIoHandler;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueIoHandler;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.thread.DispatchThread;

import java.io.File;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class Utils {

    public static final String CLIENT_CONNECT = "CLIENT_CONNECT_";
    public static final String CLIENT_CONNECT_RESPONSE = "CLIENT_CONNECT_RESPONSE_";

    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";

    public static final int MSG_BYTE_LEN_MAX = 64 * 1024;// 从客户端接收的消息最大长度
    public static final int MAX_BODY_SIZE = 1024 * 1024; // 默认数据包大小

    public static final int ID_BASE_NUM = 1000000; //连接id基数

    public static String getLocalIpAddress() {
        try {
            InetAddress inetAddress = InetAddress.getLocalHost();
            return inetAddress.getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    public static long getProcessId() {
        return ProcessHandle.current().pid();
    }

    /**
     * 获取监听ip，所有节点默认都绑定在此ip上
     */
    public static String getListenIpAddress() {
        return "0.0.0.0";
    }

    /**
     * 根据当前系统环境创建最优的 EventLoopGroup
     * @param nThreads 线程数 (传入 0 则使用 Netty 默认值：CPU 核心数 * 2)
     */
    public static EventLoopGroup createEventLoopGroup(int nThreads) {
        if (Epoll.isAvailable()) {
            // 注入 Epoll 的 IoHandler
            return new MultiThreadIoEventLoopGroup(nThreads, EpollIoHandler.newFactory());

        } else if (KQueue.isAvailable()) {
            // 注入 KQueue 的 IoHandler
            return new MultiThreadIoEventLoopGroup(nThreads, KQueueIoHandler.newFactory());

        } else {
            // 注入默认 NIO 的 IoHandler
            return new MultiThreadIoEventLoopGroup(nThreads, NioIoHandler.newFactory());
        }
    }

    /**
     * 获取与当前系统环境匹配的 ServerSocketChannel 类
     */
    public static Class<? extends ServerChannel> getServerChannelClass() {
        if (Epoll.isAvailable()) {
            return EpollServerSocketChannel.class;
        } else if (KQueue.isAvailable()) {
            return KQueueServerSocketChannel.class;
        } else {
            return NioServerSocketChannel.class;
        }
    }

    /**
     * 获取与当前系统环境匹配的 SocketChannel 类 (客户端使用)
     */
    public static Class<? extends Channel> getClientChannelClass() {
        if (Epoll.isAvailable()) {
            return EpollSocketChannel.class;
        } else if (KQueue.isAvailable()) {
            return KQueueSocketChannel.class;
        } else {
            return NioSocketChannel.class;
        }
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
        sb.append(" total memory: ").append(totalMemory / 1024 / 1024).append(" MB ");
        sb.append(" free memory: ").append(freeMemory / 1024 / 1024).append(" MB ");
        sb.append(" used memory: ").append(usedMemory / 1024 / 1024).append(" MB ").append(";");

        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            long count = gcBean.getCollectionCount();
            long timeMs = gcBean.getCollectionTime();
            if (count > 0) {
                sb.append(String.format(" %s: %d次耗时%dms ", gcBean.getName(), count, timeMs));
            }
        }

        LogCore.ServerStartUp.info("ServerMemory : { {} }", sb);
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

    /**
     * 睡眠指定毫秒。被中断时恢复中断标志并返回 false。
     *
     * @return true 如果正常睡眠结束；false 如果被中断
     */
    public static boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 恢复中断标志
            LogCore.ServerStartUp.debug("sleep interrupted: {}", e.getMessage());
            return false;
        }
    }
}
