package core.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.sunrise.game.log.LogCore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * 玩家 HTTP 认证：注册、验证码、登录、重置密码。
 */
public class PlayerAuthManager {

    private static String httpUrl = "127.0.0.1:8090";
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public static class AuthSession {
        public final String uid;
        public final String token;

        public AuthSession(String uid, String token) {
            this.uid = uid;
            this.token = token;
        }
    }

    public static void initialize(String url) {
        httpUrl = url;
    }

    public static AuthSession login(String email, String password) {
        JSONObject res = postJson("/auth/login", Map.of(
                "email", email,
                "password", password));
        return parseSession(res);
    }

    public static AuthSession register(String email, String password, String code) {
        JSONObject res = postJson("/auth/register", Map.of(
                "email", email,
                "password", password,
                "code", code));
        return parseSession(res);
    }

    public static void sendCode(String email, String type) {
        JSONObject res = postJson("/auth/send_code", Map.of(
                "email", email,
                "type", type));
        if (res == null || res.getIntValue("code") != 200) {
            throw new RuntimeException(res != null ? res.getString("msg") : "send_code failed");
        }
    }

    public static void forgotPassword(String email, String code, String newPassword) {
        JSONObject res = postJson("/auth/forgot_password", Map.of(
                "email", email,
                "code", code,
                "newPassword", newPassword));
        if (res == null || res.getIntValue("code") != 200) {
            throw new RuntimeException(res != null ? res.getString("msg") : "forgot_password failed");
        }
    }

    private static AuthSession parseSession(JSONObject res) {
        if (res == null || res.getIntValue("code") != 200) {
            throw new RuntimeException(res != null ? res.getString("msg") : "auth failed");
        }
        JSONObject data = res.getJSONObject("data");
        if (data == null) {
            throw new RuntimeException("auth response missing data");
        }
        return new AuthSession(data.getString("uid"), data.getString("token"));
    }

    private static JSONObject postJson(String path, Map<String, String> body) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + httpUrl + path))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 && response.statusCode() != 400) {
                LogCore.Client.error("Auth HTTP failed: path={}, code={}", path, response.statusCode());
                return null;
            }
            return JSON.parseObject(response.body());
        } catch (Exception e) {
            LogCore.Client.error("Auth HTTP exception: path={}", path, e);
            return null;
        }
    }
}
