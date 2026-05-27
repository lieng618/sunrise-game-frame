package org.sunrise.game.rpc.policy;

import lombok.Getter;
import org.sunrise.game.config.ConfigReader;
import org.sunrise.game.log.LogCore;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * RPC 节点连接策略，由中心服启动时从配置文件加载。
 * 配置格式：rpc.connect.&lt;本节点类型&gt;=&lt;目标类型1&gt;,&lt;目标类型2&gt;
 * 未配置任何 rpc.connect.* 时，保持全量互连（兼容旧行为）。
 */
public class RpcConnectPolicy {
    private static final String CONNECT_PREFIX = "rpc.connect.";

    @Getter
    private static boolean enabled = false;
    @Getter
    private static Map<String, Set<String>> rules = Collections.emptyMap();

    private RpcConnectPolicy() {
    }

    public static void init() {
        Properties properties = ConfigReader.getProp();
        if (properties == null) {
            enabled = false;
            rules = Collections.emptyMap();
            return;
        }

        Map<String, Set<String>> parsed = new HashMap<>();
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(CONNECT_PREFIX)) {
                continue;
            }
            String fromType = normalizeType(key.substring(CONNECT_PREFIX.length()));
            if (fromType.isEmpty()) {
                continue;
            }
            parsed.put(fromType, parseTargets(properties.getProperty(key)));
        }

        if (parsed.isEmpty()) {
            enabled = false;
            rules = Collections.emptyMap();
            LogCore.CenterServer.info("rpc connect policy disabled, use full mesh");
            return;
        }

        enabled = true;
        rules = Collections.unmodifiableMap(parsed);
        LogCore.CenterServer.info("rpc connect policy loaded, rules = {}", rules);
    }

    /**
     * receiver 是否应对 target 建立出站连接
     */
    public static boolean shouldConnect(String receiverType, String targetType) {
        if (!enabled) {
            return true;
        }
        if (receiverType == null || receiverType.isBlank() || targetType == null || targetType.isBlank()) {
            return true;
        }
        String from = normalizeType(receiverType);
        String to = normalizeType(targetType);
        Set<String> targets = rules.get(from);
        if (targets == null) {
            return false;
        }
        return targets.contains(to);
    }

    private static Set<String> parseTargets(String value) {
        Set<String> targets = new HashSet<>();
        if (value == null || value.isBlank()) {
            return targets;
        }
        for (String part : value.split(",")) {
            String type = normalizeType(part);
            if (!type.isEmpty()) {
                targets.add(type);
            }
        }
        return targets;
    }

    public static String normalizeType(String type) {
        if (type == null) {
            return "";
        }
        return type.trim().toLowerCase();
    }
}
