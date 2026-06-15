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
 *   <li>{@code rpc.scan.packages} — 逗号分隔的包名；未配置时按 {@code org.sunrise.game.{nodeType}.service} 约定推导</li>
 *   <li>{@code rpc.call-enum-class} — 生成的 CallEnum 全类名，用于 rpcId 与 @RpcMethod 绑定</li>
 *   <li>{@code rpc.init.strict} — 为 true 时任一 @RpcService 实例化失败则终止启动（默认 true）</li>
 * </ul>
 * 单进程 runallone 需显式配置更宽的 {@code rpc.scan.packages=org.sunrise.game}，以覆盖多子服务包。
 */
public final class RpcScanConfig {
    private static final String DEFAULT_CALL_ENUM_CLASS = "org.sunrise.game.genRpc.gen.CallEnum";
    private static final String PACKAGE_PREFIX = "org.sunrise.game";

    private RpcScanConfig() {
    }

    /** RPC 扫描注册所需的三个参数，由 {@link #resolve} 一次性解析 */
    public record Settings(List<String> scanPackages, Class<?> callEnumClass, boolean strict) {
    }

    /**
     * 读取当前已加载的配置并解析 RPC 注册参数。
     *
     * @param nodeType {@code rpc.node.type}，用于推导默认扫描包
     */
    public static Settings resolve(String nodeType) {
        Properties properties = ConfigReader.getProp();
        return new Settings(
                resolveScanPackages(nodeType, properties),
                resolveCallEnumClass(properties),
                isStrictInit(properties));
    }

    /** 优先使用显式配置的 {@code rpc.scan.packages}，否则按 nodeType 推导单包 */
    private static List<String> resolveScanPackages(String nodeType, Properties properties) {
        if (properties != null) {
            String configured = properties.getProperty("rpc.scan.packages");
            if (configured != null && !configured.isBlank()) {
                return parsePackageList(configured);
            }
        }
        return List.of(defaultPackageForNodeType(nodeType));
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

    /** {@code rpc.init.strict}，缺省 true：部分服务注册失败时不应带病启动 */
    private static boolean isStrictInit(Properties properties) {
        if (properties == null) {
            return true;
        }
        return Boolean.parseBoolean(properties.getProperty("rpc.init.strict", "true"));
    }

    /** 约定：game 节点 → {@code org.sunrise.game.game.service}，external → {@code org.sunrise.game.external.service} */
    private static String defaultPackageForNodeType(String nodeType) {
        return PACKAGE_PREFIX + "." + nodeType.trim().toLowerCase() + ".service";
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
