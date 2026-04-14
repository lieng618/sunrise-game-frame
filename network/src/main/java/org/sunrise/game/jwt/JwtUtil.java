package org.sunrise.game.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.sunrise.game.log.LogCore;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JWT 工具类，用于生成和验证 Token
 */
public class JwtUtil {
    private static SecretKey key;
    private static long expiration;
    
    // Token 黑名单
    private static final Map<String, Boolean> tokenBlacklist = new ConcurrentHashMap<>();

    /**
     * 初始化 JWT 工具类
     * @param expirationMs token 过期时间（毫秒）
     */
    public static void init(long expirationMs) {
        key = Jwts.SIG.HS256.key().build();
        expiration = expirationMs;
    }

    /**
     * 为指定用户生成一个 JWT
     * @param username 用户名
     * @return JWT 字符串
     */
    public static String createToken(String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(username)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    /**
     * 验证 JWT 并返回用户名
     * @param token JWT 字符串
     * @return 如果 token 有效，返回用户名；否则返回 null
     */
    public static String verifyToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            
            String username = claims.getSubject();
            if (username == null) {
                return null;
            }
            
            // 检查用户是否在黑名单中
            if (tokenBlacklist.containsKey(username)) {
                LogCore.BaseServer.debug("Token rejected: user {} tokens have been invalidated", username);
                return null;
            }
            
            return username;
        } catch (JwtException | IllegalArgumentException e) {
            LogCore.BaseServer.debug("Invalid JWT token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 使指定用户的所有 token 失效
     * @param username 用户名
     */
    public static void invalidateUserTokens(String username) {
        tokenBlacklist.put(username, true);
        LogCore.BaseServer.debug("Invalidated all tokens for user: {}", username);
    }

    /**
     * 清除指定用户的 token 黑名单记录（用于重新启用用户）
     * @param username 用户名
     */
    public static void clearUserBlacklist(String username) {
        tokenBlacklist.remove(username);
        LogCore.BaseServer.debug("Cleared blacklist for user: {}", username);
    }
}