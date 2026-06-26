package org.sunrise.game.rpc.node;

import lombok.Getter;
import org.sunrise.game.config.ConfigReader;
import org.sunrise.game.core.client.BaseClient;
import org.sunrise.game.graceful.GracefulShutdown;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.rpc.function.CallUtils;
import org.sunrise.game.startup.FatalStartupException;
import org.sunrise.game.utils.Utils;

import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * RPC 节点生命周期入口：创建节点、统一 main 启动流程、节点 ID 查询。
 */
public class RpcNodeManager {
    /** 当前 JVM 内的唯一 RPC 节点实例 */
    @Getter
    private static RpcNode rpcNode;

    /**
     * 创建 RPC 节点并自动完成 RPC 服务扫描注册。
     * <p>
     * 扫描包与 CallEnum 类由 {@link RpcScanConfig} 从配置解析：
     * {@code rpc.scan.packages}、{@code rpc.call-enum-class}。
     */
    public static RpcNode createRpcNode(int serverId, String nodeType) {
        rpcNode = new RpcNode(serverId, nodeType);
        registerRpcServices(rpcNode);
        return rpcNode;
    }

    /**
     * RPC 进程通用启动：加载配置 → 创建并注册 RPC 节点 → 监听 → 优雅退出钩子。
     *
     * @param args               命令行参数，首参为配置文件路径；为空时使用 {@code defaultConfigPath}
     * @param defaultConfigPath  无命令行参数时的默认配置文件
     */
    public static void runMain(String[] args, String defaultConfigPath) {
        runMain(args, defaultConfigPath, null, null);
    }

    /**
     * RPC 进程通用启动，{@code beforeListen} 在 {@link RpcNode#start()} 之前执行。
     * <p>
     * 典型用途：GameServer 加载 Luban 表与协议处理器、Http/Game 初始化 JWT 等——
     * 此时 RPC 服务已注册，但尚未对外监听，适合加载仅本进程需要的资源。
     *
     * @param beforeListen 可为 null；抛 {@link FatalStartupException} 时进程以 exit 1 退出
     */
    public static void runMain(String[] args, String defaultConfigPath, Runnable beforeListen) {
        runMain(args, defaultConfigPath, null, beforeListen);
    }

    /**
     * RPC 进程通用启动，可指定 logback 日志文件名前缀 {@code programName}。
     *
     * @param programName  为 null 或空白时使用 {@code {rpc.node.type}-{rpc.node.server-id}}
     */
    public static void runMain(String[] args, String defaultConfigPath, String programName, Runnable beforeListen) {
        try {
            startFromMain(args, defaultConfigPath, programName, beforeListen);
        } catch (FatalStartupException e) {
            LogCore.ServerStartUp.error("Startup failed: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    /** 各 StartUp main 的实际启动顺序，FatalStartupException 向上抛给 {@link #runMain} 统一处理 */
    private static void startFromMain(String[] args, String defaultConfigPath, String programName, Runnable beforeListen) {
        if (args.length == 0) {
            args = new String[]{defaultConfigPath};
        }
        ConfigReader.loadConfig(args[0]);
        Properties properties = ConfigReader.getProp();
        if (properties == null) {
            return;
        }
        int serverId = Integer.parseInt(properties.getProperty("rpc.node.server-id"));
        String nodeType = properties.getProperty("rpc.node.type");
        configureProgramName(programName, nodeType, serverId);
        Utils.setLogLevel(properties.getProperty("log.level"));

        // 创建节点时会同步扫描 @RpcService 并注册到 ServiceManager
        RpcNode rpcNode = createRpcNode(serverId, nodeType);
        if (beforeListen != null) {
            beforeListen.run();
        }
        rpcNode.start();
        GracefulShutdown.scanAndInstall();
        Utils.startMemoryCheck();
    }

    /**
     * 根据 {@link RpcScanConfig} 解析结果调用 {@link CallUtils#init}。
     * 必须在 {@link ConfigReader#loadConfig} 之后调用。
     */
    private static void registerRpcServices(RpcNode rpcNode) {
        if (ConfigReader.getProp() == null) {
            throw new FatalStartupException("Config not loaded before RPC registration");
        }
        RpcScanConfig.Settings settings = RpcScanConfig.resolve();
        CallUtils.init(rpcNode.getNodeId(), settings.scanPackages(), settings.callEnumClass());
    }

    /** 设置 {@code programName} 系统属性，供 logback.xml 区分各进程日志文件 */
    private static void configureProgramName(String programName, String nodeType, int serverId) {
        if (programName != null && !programName.isBlank()) {
            System.setProperty("programName", programName.trim());
            return;
        }
        String type = nodeType == null || nodeType.isBlank() ? "rpc" : nodeType.trim().toLowerCase();
        System.setProperty("programName", type + "-" + serverId);
    }

    /**
     * 通过客户端节点id获取远端的服务器id
     */
    public static int getServerIdByClientNodeId(String curNodeId) {
        if (rpcNode != null) {
            for (Map.Entry<Integer, BaseClient> entry : rpcNode.getConnectToOthers().entrySet()) {
                if (Objects.equals(entry.getValue().getNodeId(), curNodeId)) {
                    return entry.getKey();
                }
            }
            LogCore.RpcUtils.warn("RpcNode getServerIdByClientNodeId fail, use NodeId = { {} }, cur have Others ServerId = {{}}", curNodeId, rpcNode.getConnectToOthers().keySet());
        } else {
            LogCore.RpcUtils.warn("RpcNode getServerIdByClientNodeId fail, rpcNode is null, use NodeId = { {} }", curNodeId);
        }
        return 0;
    }

    /**
     * 通过客户端节点id获取远端的服务器节点id
     */
    public static String getServerNodeIdByClientNodeId(String curNodeId) {
        if (rpcNode != null) {
            for (Map.Entry<Integer, BaseClient> entry : rpcNode.getConnectToOthers().entrySet()) {
                if (Objects.equals(entry.getValue().getNodeId(), curNodeId)) {
                    return entry.getValue().getServerNodeId();
                }
            }
            LogCore.RpcUtils.warn("RpcNode getServerNodeIdByClientNodeId fail, use NodeId = { {} }, cur have Others ServerId = {{}}", curNodeId, rpcNode.getConnectToOthers().keySet());
        } else {
            LogCore.RpcUtils.warn("RpcNode getServerNodeIdByClientNodeId fail, rpcNode is null, use NodeId = { {} }", curNodeId);
        }
        return "";
    }

    /**
     * 判断服务器节点是否有效
     */
    public static boolean isServerNodeActive(String serverNodeId) {
        if (serverNodeId == null || serverNodeId.isEmpty()) {
            return false;
        }
        if (rpcNode != null) {
            if (rpcNode.getNodeId().equals(serverNodeId)) {
                return true;
            }
            for (Map.Entry<Integer, BaseClient> entry : rpcNode.getConnectToOthers().entrySet()) {
                if (Objects.equals(entry.getValue().getServerNodeId(), serverNodeId)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 通过服务器节点id获取客户端节点id
     */
    public static String getClientNodeIdByServerNodeId(String serverNodeId) {
        if (rpcNode != null) {
            for (Map.Entry<Integer, BaseClient> entry : rpcNode.getConnectToOthers().entrySet()) {
                if (Objects.equals(entry.getValue().getServerNodeId(), serverNodeId)) {
                    return entry.getValue().getNodeId();
                }
            }
            LogCore.RpcUtils.warn("RpcNode getClientNodeIdByServerNodeId fail, use NodeId = { {} }, cur have Others ServerId = {{}}", serverNodeId, rpcNode.getConnectToOthers().keySet());
        } else {
            LogCore.RpcUtils.warn("RpcNode getClientNodeIdByServerNodeId fail, rpcNode is null, use NodeId = { {} }", serverNodeId);
        }
        return "";
    }

    /**
     * 获取rpc节点服务id
     */
    public static int getRpcServerId() {
        if (rpcNode != null) {
            return rpcNode.getServerId();
        } else {
            LogCore.RpcUtils.warn("RpcNode getRpcServerId fail, rpcNode is null");
        }
        return 0;
    }

    /**
     * 获取rpc节点服务节点id
     */
    public static String getRpcServerNodeId() {
        if (rpcNode != null) {
            return rpcNode.getRpcServer().getNodeId();
        } else {
            LogCore.RpcUtils.warn("RpcNode getRpcServerNodeId fail, rpcNode is null");
        }
        return "";
    }
}
