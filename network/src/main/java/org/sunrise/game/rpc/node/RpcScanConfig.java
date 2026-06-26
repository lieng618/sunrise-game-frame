package org.sunrise.game.rpc.node;

import org.sunrise.game.config.ConfigReader;
import org.sunrise.game.startup.FatalStartupException;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * 从配置文件解析 RPC 服务扫描参数。
 * <p>
 * 配置项：
 * <ul>
 *   <li>{@code rpc.scan.packages} — 必填，逗号分隔的 @RpcService 扫描包名</li>
 *   <li>{@code rpc.call-enum-class} — 生成的 CallEnum 全类名，用于 rpcId 与 @RpcMethod 绑定</li>
 * </ul>
 * 单进程 runallone 需配置更宽的 {@code rpc.scan.packages=org.sunrise.game}，以覆盖多子服务包。
 */
public final class RpcScanConfig {
    private static final String DEFAULT_CALL_ENUM_CLASS = "org.sunrise.game.genRpc.gen.CallEnum";

    private RpcScanConfig() {
    }

    /** RPC 扫描注册所需的参数，由 {@link #resolve} 一次性解析 */
    public record Settings(List<String> scanPackages, Class<?> callEnumClass) {
    }

    /** 读取当前已加载的配置并解析 RPC 注册参数 */
    public static Settings resolve() {
        Properties properties = ConfigReader.getProp();
        return new Settings(
                resolveScanPackages(properties),
                resolveCallEnumClass(properties));
    }

    /** 从 {@code rpc.scan.packages} 解析逗号分隔的包列表 */
    private static List<String> resolveScanPackages(Properties properties) {
        if (properties == null) {
            throw new FatalStartupException("Config not loaded; cannot resolve rpc.scan.packages");
        }
        String configured = properties.getProperty("rpc.scan.packages");
        if (configured == null || configured.isBlank()) {
            throw new FatalStartupException("rpc.scan.packages is required");
        }
        return parsePackageList(configured);
    }

    /** 加载 CallEnum 类；类不存在时快速失败，避免启动后 RPC 调用全部找不到方法 */
    private static Class<?> resolveCallEnumClass(Properties properties) {
        String className = DEFAULT_CALL_ENUM_CLASS;
        if (properties != null) {
            className = properties.getProperty("rpc.call-enum-class", DEFAULT_CALL_ENUM_CLASS).trim();
        }
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new FatalStartupException("rpc.call-enum-class not found: " + className, e);
        }
    }

    /** 解析逗号分隔的包列表，过滤空白项 */
    private static List<String> parsePackageList(String configured) {
        String[] parts = configured.split(",");
        List<String> packages = new ArrayList<>(parts.length);
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                packages.add(trimmed);
            }
        }
        if (packages.isEmpty()) {
            throw new FatalStartupException("rpc.scan.packages is empty");
        }
        return packages;
    }
}
