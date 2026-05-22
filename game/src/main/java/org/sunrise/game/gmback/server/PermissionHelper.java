package org.sunrise.game.gmback.server;

import org.sunrise.game.config.ConfigReader;
import org.sunrise.game.gmback.server.controller.ControllerManager;
import org.sunrise.game.gmback.server.controller.UserController;
import org.sunrise.game.jwt.JwtUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * 页面权限与 API 路径映射
 */
public class PermissionHelper {

    /** 可分配给普通用户的页面 */
    public static final List<String> ASSIGNABLE_PAGE_KEYS = Arrays.asList(
            "monitor",
            "server_status",
            "online_player",
            "config_update",
            "hotswap_jar",
            "send_mail",
            "kick_human",
            "ban_player",
            "mute_player",
            "whitelist",
            "announcement"
    );

    private static final Map<String, String> PAGE_LABELS = new LinkedHashMap<>();

    static {
        PAGE_LABELS.put("monitor", "节点监控");
        PAGE_LABELS.put("server_status", "服务器关闭");
        PAGE_LABELS.put("online_player", "在线玩家");
        PAGE_LABELS.put("config_update", "配置更新");
        PAGE_LABELS.put("hotswap_jar", "代码热更");
        PAGE_LABELS.put("send_mail", "发送邮件");
        PAGE_LABELS.put("kick_human", "玩家下线");
        PAGE_LABELS.put("ban_player", "玩家封禁");
        PAGE_LABELS.put("mute_player", "玩家禁言");
        PAGE_LABELS.put("whitelist", "白名单");
        PAGE_LABELS.put("announcement", "全服公告");
    }

    /** API 路径 -> 所需权限（任一匹配即可，用于共享接口如 /api/nodes） */
    private static final Map<String, Set<String>> API_PERMISSIONS = new HashMap<>();

    static {
        addApiPermission("GET", "/api/nodes", "monitor", "config_update", "hotswap_jar");
        addApiPermission("POST", "/api/config/reload", "config_update");
        addApiPermission("POST", "/api/hotswap/jar", "hotswap_jar");
        addApiPermission("POST", "/api/gm/send-mail", "send_mail");
        addApiPermission("POST", "/api/gm/kick", "kick_human");
        addApiPermission("GET", "/api/logs", "operation_log");
        addApiPermission("GET", "/api/ban/list", "ban_player");
        addApiPermission("POST", "/api/ban", "ban_player");
        addApiPermission("POST", "/api/unban", "ban_player");
        addApiPermission("GET", "/api/mute/list", "mute_player");
        addApiPermission("POST", "/api/mute", "mute_player");
        addApiPermission("POST", "/api/unmute", "mute_player");
        addApiPermission("GET", "/api/online-players", "online_player");
        addApiPermission("GET", "/api/server-status", "server_status");
        addApiPermission("POST", "/api/server-status", "server_status");
        addApiPermission("GET", "/api/whitelist", "whitelist");
        addApiPermission("POST", "/api/whitelist", "whitelist");
        addApiPermission("POST", "/api/whitelist/remove", "whitelist");
        addApiPermission("GET", "/api/announcements", "announcement");
        addApiPermission("POST", "/api/announcements", "announcement");
        addApiPermission("POST", "/api/announcements/update", "announcement");
        addApiPermission("POST", "/api/announcements/remove", "announcement");
    }

    private static void addApiPermission(String method, String path, String... permissions) {
        API_PERMISSIONS.put(method + " " + path, new HashSet<>(Arrays.asList(permissions)));
    }

    public static boolean isConfigAdmin(String username) {
        Properties properties = ConfigReader.getProp();
        if (properties == null || username == null) {
            return false;
        }
        String adminUsername = properties.getProperty("admin.user");
        return adminUsername != null && adminUsername.equals(username);
    }

    /**
     * 获取用户拥有的页面权限 key 列表
     */
    public static List<String> getUserPermissions(String username) {
        if (isConfigAdmin(username)) {
            List<String> all = new ArrayList<>(ASSIGNABLE_PAGE_KEYS);
            all.add("user_manager");
            return all;
        }
        UserController userController = ControllerManager.getController(UserController.class);
        if (userController == null) {
            return Collections.emptyList();
        }
        UserController.User user = userController.findUser(username);
        if (user == null) {
            return Collections.emptyList();
        }
        List<String> permissions = user.getPermissions();
        if (permissions == null || permissions.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(permissions);
    }

    /**
     * 检查当前请求是否允许访问
     */
    public static boolean checkApiAccess(String token, String method, String path) {
        String username = JwtUtil.verifyToken(token);
        if (username == null) {
            return false;
        }
        if (isConfigAdmin(username)) {
            return true;
        }
        String key = method + " " + path;
        Set<String> required = API_PERMISSIONS.get(key);
        if (required == null) {
            // 未映射的 API（如用户管理）由各自 Controller 的 isAdmin 校验
            return true;
        }
        Set<String> userPerms = new HashSet<>(getUserPermissions(username));
        for (String perm : required) {
            if (userPerms.contains(perm)) {
                return true;
            }
        }
        return false;
    }

    public static List<Map<String, String>> getAssignablePages() {
        List<Map<String, String>> pages = new ArrayList<>();
        for (String key : ASSIGNABLE_PAGE_KEYS) {
            Map<String, String> page = new HashMap<>();
            page.put("key", key);
            page.put("label", PAGE_LABELS.get(key));
            pages.add(page);
        }
        return pages;
    }
}
