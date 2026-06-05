package org.sunrise.game.jwt;

import org.sunrise.game.log.LogCore;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class PasswordUtil {

    private PasswordUtil() {
    }

    /**
     * 加密密码
     */
    public static String encryptPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            return toHex(hash);
        } catch (NoSuchAlgorithmException e) {
            LogCore.BaseServer.error("Password encryption failed", e);
            return password; // 如果加密失败，返回原密码（不推荐，但作为降级方案）
        }
    }

    /**
     * 验证密码
     */
    public static boolean verifyPassword(String password, String passwordHash) {
        return encryptPassword(password).equals(passwordHash);
    }

    public static String toHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
