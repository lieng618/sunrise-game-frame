package org.sunrise.game.gmback.server.controller;

import com.alibaba.fastjson2.TypeReference;
import io.javalin.http.Context;
import lombok.Data;
import org.sunrise.game.config.ConfigReader;
import org.sunrise.game.jwt.JwtUtil;
import org.sunrise.game.log.LogCore;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.sunrise.game.gmback.server.PermissionHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 用户管理控制器
 */
public class UserController extends BaseController {

    @Data
    public static class User {
        private String username;      // 用户名
        private String password;      // 加密后的密码
        private Long createTime;      // 创建时间
        private List<String> permissions; // 页面权限 key 列表

        public User() {
        }

        public User(String username, String password) {
            this.username = username;
            this.password = password;
            this.createTime = System.currentTimeMillis();
        }
    }

    private final List<User> users = new CopyOnWriteArrayList<>();

    @Override
    public void load() {
        getDbData("users", new TypeReference<List<User>>() {
        }, value -> {
            if (value != null) {
                users.clear();
                users.addAll(value);
            }
        });
    }

    @Override
    public void save() {
        List<User> usersCopy = new ArrayList<>(users);
        putDbData("users", usersCopy);
    }

    /**
     * 检查当前用户是否为 admin
     */
    private boolean isAdmin(Context ctx) {
        try {
            String token = ctx.header("Authorization");
            if (token == null || token.isEmpty()) {
                return false;
            }
            String username = JwtUtil.verifyToken(token);
            if (username == null) {
                return false;
            }
            Properties properties = ConfigReader.getProp();
            if (properties == null) {
                return false;
            }
            String adminUsername = properties.getProperty("admin.user");
            return adminUsername != null && adminUsername.equals(username);
        } catch (Exception e) {
            LogCore.GmBackServer.error("Check admin failed", e);
            return false;
        }
    }

    /**
     * 加密密码
     */
    private String encryptPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            LogCore.GmBackServer.error("Password encryption failed", e);
            return password; // 如果加密失败，返回原密码（不推荐，但作为降级方案）
        }
    }

    /**
     * 验证密码
     */
    public boolean verifyPassword(String password, String encryptedPassword) {
        return encryptPassword(password).equals(encryptedPassword);
    }

    /**
     * 根据用户名查找用户
     */
    public User findUser(String username) {
        for (User user : users) {
            if (user.getUsername().equals(username)) {
                return user;
            }
        }
        return null;
    }

    /**
     * 获取用户列表（仅 admin 可见）
     */
    public void list(Context ctx) {
        if (!isAdmin(ctx)) {
            fail(ctx, 403, "Forbidden: Only admin can access user management");
            return;
        }

        List<User> usersCopy = new ArrayList<>(users);
        success(ctx, usersCopy);
    }

    /**
     * 添加新用户
     */
    public void add(Context ctx) {
        if (!isAdmin(ctx)) {
            fail(ctx, 403, "Forbidden: Only admin can add users");
            return;
        }

        String username = getBodyParam(ctx, "username", String.class);
        String password = getBodyParam(ctx, "password", String.class);

        if (username == null || username.trim().isEmpty()) {
            fail(ctx, 400, "Username cannot be empty");
            return;
        }

        if (password == null || password.trim().isEmpty()) {
            fail(ctx, 400, "Password cannot be empty");
            return;
        }

        // 检查用户名是否已存在
        if (findUser(username) != null) {
            fail(ctx, 400, "Username already exists");
            return;
        }

        // 检查是否是 admin 用户名
        Properties properties = ConfigReader.getProp();
        if (properties != null) {
            String adminUsername = properties.getProperty("admin.user");
            if (adminUsername != null && adminUsername.equals(username)) {
                fail(ctx, 400, "Cannot create user with admin username");
                return;
            }
        }

        // 加密密码并添加用户，默认无页面权限，需管理员在权限管理中分配
        String encryptedPassword = encryptPassword(password);
        User newUser = new User(username, encryptedPassword);
        newUser.setPermissions(new ArrayList<>());
        users.add(newUser);
        save();

        // 记录操作日志
        ControllerManager.getController(OperationLogController.class).recordLog(
                ctx, OperationLogController.OperationType.USER_MANAGER, "添加用户: " + username);

        success(ctx, null, "User added successfully");
    }

    /**
     * 删除用户
     */
    public void delete(Context ctx) {
        if (!isAdmin(ctx)) {
            fail(ctx, 403, "Forbidden: Only admin can delete users");
            return;
        }

        String username = ctx.pathParam("username");
        if (username.trim().isEmpty()) {
            fail(ctx, 400, "Username cannot be empty");
            return;
        }

        // 检查是否是 admin 用户名
        Properties properties = ConfigReader.getProp();
        if (properties != null) {
            String adminUsername = properties.getProperty("admin.user");
            if (adminUsername != null && adminUsername.equals(username)) {
                fail(ctx, 400, "Cannot delete admin user");
                return;
            }
        }

        // 查找并删除用户
        User user = findUser(username);
        if (user == null) {
            fail(ctx, 404, "User not found");
            return;
        }

        users.remove(user);
        save();

        // 使该用户的所有 token 失效
        JwtUtil.invalidateUserTokens(username);

        // 记录操作日志
        ControllerManager.getController(OperationLogController.class).recordLog(
                ctx, OperationLogController.OperationType.USER_MANAGER, "删除用户: " + username);

        success(ctx, null, "User deleted successfully");
    }

    /**
     * 修改用户密码
     */
    public void updatePassword(Context ctx) {
        if (!isAdmin(ctx)) {
            fail(ctx, 403, "Forbidden: Only admin can update user passwords");
            return;
        }

        String username = ctx.pathParam("username");
        String newPassword = getBodyParam(ctx, "password", String.class);

        if (username.trim().isEmpty()) {
            fail(ctx, 400, "Username cannot be empty");
            return;
        }

        if (newPassword == null || newPassword.trim().isEmpty()) {
            fail(ctx, 400, "Password cannot be empty");
            return;
        }

        // 检查是否是 admin 用户名
        Properties properties = ConfigReader.getProp();
        if (properties != null) {
            String adminUsername = properties.getProperty("admin.user");
            if (adminUsername != null && adminUsername.equals(username)) {
                fail(ctx, 400, "Cannot update admin password through this interface");
                return;
            }
        }

        // 查找并更新用户密码
        User user = findUser(username);
        if (user == null) {
            fail(ctx, 404, "User not found");
            return;
        }

        // 加密新密码并更新
        String encryptedPassword = encryptPassword(newPassword);
        user.setPassword(encryptedPassword);
        save();

        // 使该用户的所有 token 失效（用户需要用新密码重新登录）
        JwtUtil.invalidateUserTokens(username);

        // 记录操作日志
        ControllerManager.getController(OperationLogController.class).recordLog(
                ctx, OperationLogController.OperationType.USER_MANAGER, "修改用户密码: " + username);

        success(ctx, null, "Password updated successfully");
    }

    /**
     * 获取可分配的页面列表（仅 admin）
     */
    public void listPages(Context ctx) {
        if (!isAdmin(ctx)) {
            fail(ctx, 403, "Forbidden: Only admin can access permission management");
            return;
        }
        success(ctx, PermissionHelper.getAssignablePages());
    }

    /**
     * 获取指定用户的页面权限（仅 admin）
     */
    public void getPermissions(Context ctx) {
        if (!isAdmin(ctx)) {
            fail(ctx, 403, "Forbidden: Only admin can access permission management");
            return;
        }

        String username = ctx.pathParam("username");
        if (username.trim().isEmpty()) {
            fail(ctx, 400, "Username cannot be empty");
            return;
        }

        Properties properties = ConfigReader.getProp();
        if (properties != null) {
            String adminUsername = properties.getProperty("admin.user");
            if (adminUsername != null && adminUsername.equals(username)) {
                fail(ctx, 400, "Admin user permissions cannot be modified");
                return;
            }
        }

        User user = findUser(username);
        if (user == null) {
            fail(ctx, 404, "User not found");
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("username", username);
        data.put("permissions", PermissionHelper.getUserPermissions(username));
        data.put("pages", PermissionHelper.getAssignablePages());
        success(ctx, data);
    }

    /**
     * 更新用户页面权限（仅 admin），修改后强制该用户重新登录
     */
    @SuppressWarnings("unchecked")
    public void updatePermissions(Context ctx) {
        if (!isAdmin(ctx)) {
            fail(ctx, 403, "Forbidden: Only admin can update permissions");
            return;
        }

        String username = ctx.pathParam("username");
        if (username.trim().isEmpty()) {
            fail(ctx, 400, "Username cannot be empty");
            return;
        }

        User user = findUser(username);
        if (user == null) {
            fail(ctx, 404, "User not found");
            return;
        }

        List<String> permissions = getBodyParam(ctx, "permissions", List.class);
        if (permissions == null) {
            fail(ctx, 400, "Permissions cannot be empty");
            return;
        }

        Set<String> validKeys = new HashSet<>(PermissionHelper.ASSIGNABLE_PAGE_KEYS);
        List<String> sanitized = new ArrayList<>();
        for (Object item : permissions) {
            if (item == null) {
                continue;
            }
            String key = item.toString();
            if (validKeys.contains(key) && !sanitized.contains(key)) {
                sanitized.add(key);
            }
        }

        user.setPermissions(sanitized);
        save();

        JwtUtil.invalidateUserTokens(username);

        ControllerManager.getController(OperationLogController.class).recordLog(
                ctx, OperationLogController.OperationType.USER_MANAGER,
                "修改用户权限: " + username + " -> " + sanitized);

        success(ctx, null, "Permissions updated successfully");
    }
}
