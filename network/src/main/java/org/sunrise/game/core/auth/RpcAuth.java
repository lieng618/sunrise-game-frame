package org.sunrise.game.core.auth;

import org.sunrise.game.config.ConfigReader;
import org.sunrise.game.log.LogCore;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 内部 RPC 链路握手认证：节点与中心服共享 rpc.auth-token。
 * 未配置时降级为不认证并告警；配置后握手必须携带相同 token，否则连接被拒绝。
 */
public final class RpcAuth {
    private static volatile boolean initDone = false;
    /** null 表示未启用认证 */
    private static String token = null;

    private RpcAuth() {
    }

    private static void init() {
        if (initDone) {
            return;
        }
        synchronized (RpcAuth.class) {
            if (initDone) {
                return;
            }
            var prop = ConfigReader.getProp();
            String configured = prop == null ? null : prop.getProperty("rpc.auth-token");
            if (configured != null && !configured.isBlank()) {
                token = configured.trim();
            } else {
                LogCore.BaseServer.warn("rpc.auth-token 未配置，内部 RPC 链路不启用认证，所有能连上端口的对端都将被信任");
            }
            initDone = true;
        }
    }

    public static boolean enabled() {
        init();
        return token != null;
    }

    /** 客户端握手消息追加的 token；未启用认证时返回 null */
    public static String getToken() {
        init();
        return token;
    }

    /** 常量时间比较，避免时序侧信道 */
    public static boolean verify(String candidate) {
        init();
        return token != null && candidate != null
                && MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), candidate.getBytes(StandardCharsets.UTF_8));
    }
}
