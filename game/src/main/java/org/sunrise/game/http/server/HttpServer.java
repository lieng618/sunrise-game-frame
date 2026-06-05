package org.sunrise.game.http.server;

import ch.qos.logback.classic.Level;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import io.javalin.Javalin;
import io.javalin.http.Context;
import lombok.Data;
import lombok.Getter;
import org.sunrise.game.config.ConfigReader;
import org.sunrise.game.jwt.JwtUtil;
import org.sunrise.game.jwt.MailUtil;
import org.sunrise.game.jwt.PasswordUtil;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.utils.IdGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * http服务 管理所有对外服地址
 * client通过curl请求，获取对外服地址进行连接
 * 同一个client的uid，多次请求尽量分给同一个地址
 */
@Data
public class HttpServer {
    @Data
    public static class AuthUser {
        private String uid;
        private String email;
        private String passwordHash;
    }

    private final int port;
    @Getter
    private Javalin app;
    private final Random random = new Random();
    private final AtomicInteger convAllocator = new AtomicInteger(1);
    private volatile boolean serverOpen = true;
    private volatile List<String> whitelist = new ArrayList<>();
    private volatile List<Map<String, Object>> announcements = new ArrayList<>();
    // uid-对外服id
    private final ConcurrentHashMap<String, Integer> uidExternals = new ConcurrentHashMap<>();
    // 对外服类型-<对外服id-地址>
    // "tcp" - <1001, 127.0.0.1:10000>
    // "websocket" - <1001, 127.0.0.1:10001>
    // "kcp" - <1001, 127.0.0.1:10002>
    private final ConcurrentHashMap<String, ConcurrentHashMap<Integer, String>> externalAddress = new ConcurrentHashMap<>();
    private Map<String, AuthUser> authUsers = new ConcurrentHashMap<>();

    public HttpServer(int port) {
        this.port = port;
        app = Javalin.create();
        app.post("/send_code", ctx -> {
            JSONObject jsonResponse = new JSONObject();
            String email = ctx.queryParam("email");
            String verifyCode = MailUtil.sendVerifyCode(email);
            if (verifyCode.isEmpty()) {
                jsonResponse.put("result", false);
            } else {
                jsonResponse.put("result", true);
            }
            ctx.result(jsonResponse.toJSONString());
        });
        app.post("/register", ctx -> {
            JSONObject jsonResponse = new JSONObject();
            String email = ctx.queryParam("email");
            String password = ctx.queryParam("password");
            String code = ctx.queryParam("code");
            if (authUsers.containsKey(email)) {
                jsonResponse.put("result", false);
                jsonResponse.put("msg", "email already registered");
                ctx.result(jsonResponse.toJSONString());
                return;
            }
            if (password == null || password.length() < 6) {
                jsonResponse.put("result", false);
                jsonResponse.put("msg", "password too short");
                ctx.result(jsonResponse.toJSONString());
                return;
            }
            if (!MailUtil.verifyCode(email, code)) {
                jsonResponse.put("result", false);
                jsonResponse.put("msg", "invalid or expired code");
                ctx.result(jsonResponse.toJSONString());
                return;
            }
            // 生成uid
            String uid = String.valueOf(IdGenerator.getId());
            // 创建账户
            AuthUser user = new AuthUser();
            user.setUid(uid);
            user.setEmail(email);
            user.setPasswordHash(PasswordUtil.encryptPassword(password));
            authUsers.put(email, user);
            MailUtil.removeCode(email);
            jsonResponse.put("result", true);
            ctx.result(jsonResponse.toJSONString());
        });
        app.post("/login", ctx -> {
            String email = ctx.queryParam("email");
            String password = ctx.queryParam("password");
            JSONObject jsonResponse = new JSONObject();
            AuthUser user = authUsers.get(email);
            if (user == null || !PasswordUtil.verifyPassword(password, user.getPasswordHash())) {
                jsonResponse.put("result", false);
                jsonResponse.put("msg", "invalid email or password");
                ctx.result(jsonResponse.toJSONString());
                return;
            }
            jsonResponse.put("result", true);
            jsonResponse.put("token", JwtUtil.createToken(user.uid));
            ctx.result(jsonResponse.toJSONString());
        });
        app.post("/forgot_password", ctx -> {
            String email = ctx.queryParam("email");
            String password = ctx.queryParam("password");
            String code = ctx.queryParam("code");
            JSONObject jsonResponse = new JSONObject();
            AuthUser user = authUsers.get(email);
            if (user == null) {
                jsonResponse.put("result", false);
                jsonResponse.put("msg", "email not registered");
                ctx.result(jsonResponse.toJSONString());
                return;
            }
            if (password == null || password.length() < 6) {
                jsonResponse.put("result", false);
                jsonResponse.put("msg", "password too short");
                ctx.result(jsonResponse.toJSONString());
                return;
            }
            if (!MailUtil.verifyCode(email, code)) {
                jsonResponse.put("result", false);
                jsonResponse.put("msg", "invalid or expired code");
                ctx.result(jsonResponse.toJSONString());
                return;
            }
            user.setPasswordHash(PasswordUtil.encryptPassword(password));
            MailUtil.removeCode(email);
            jsonResponse.put("result", true);
            JwtUtil.invalidateUserTokens(user.getUid());
            ctx.result(jsonResponse.toJSONString());
        });
        // 接口：/server_status
        // 功能：返回服务器开关状态，客户端通过此接口判断是否可以连接
        // 白名单用户始终返回开启
        app.get("/server_status", ctx -> {
            JSONObject jsonResponse = new JSONObject();
            String uid = resolveRequestUid(ctx);
            if (uid != null && whitelist.contains(uid)) {
                jsonResponse.put("open", true);
            } else {
                jsonResponse.put("open", serverOpen);
            }
            ctx.result(jsonResponse.toJSONString());
        });
        // 接口：/external_address
        // 功能：分配网关节点并返回 ip:port
        app.get("/external_address", ctx -> {
            JSONObject jsonResponse = new JSONObject();
            String uid = resolveRequestUid(ctx);
            if (!serverOpen && (uid == null || !whitelist.contains(uid))) {
                jsonResponse.put("error", "server_closed");
                ctx.result(jsonResponse.toJSONString());
                return;
            }
            String type = ctx.queryParam("type");
            if (type == null) {
                return;
            }
            ConcurrentHashMap<Integer, String> address = externalAddress.get(type);
            if (address == null) {
                return;
            }
            Integer oldExternalId = uidExternals.get(uid);
            if (oldExternalId != null) {
                // 有旧的地址
                String s = address.get(oldExternalId);
                if (s != null) {
                    jsonResponse.put("address", s);
                } else {
                    // 旧地址已失效
                    int randomId = random.nextInt(address.size());
                    int i = 0;
                    for (Integer key : address.keySet()) {
                        if (i == randomId) {
                            uidExternals.put(uid, key);
                            jsonResponse.put("address", address.get(key));
                        }
                        ++i;
                    }
                }
            } else {
                // 没有旧地址 随机一个新的
                int randomId = random.nextInt(address.size());
                int i = 0;
                for (Integer key : address.keySet()) {
                    if (i == randomId) {
                        uidExternals.put(uid, key);
                        jsonResponse.put("address", address.get(key));
                    }
                    ++i;
                }
            }
            ctx.result(jsonResponse.toJSONString());
        });
        // 接口：/kcp_conv
        // 功能：分配唯一的kcp conv id
        // 注意：重启后此id会重新开始计数
        app.get("/kcp_conv", ctx -> {
            JSONObject jsonResponse = new JSONObject();
            jsonResponse.put("conv", convAllocator.getAndIncrement());
            ctx.result(jsonResponse.toJSONString());
        });
        // 接口：/external_address_list
        // 功能：返回所有的对外服地址
        app.get("/external_address_list", ctx -> {
            JSONArray finalAddress = new JSONArray();
            for (Map.Entry<String, ConcurrentHashMap<Integer, String>> entry : externalAddress.entrySet()) {
                for (Map.Entry<Integer, String> addressEntry : entry.getValue().entrySet()) {
                    JSONObject oneResponse = new JSONObject();
                    oneResponse.put("address", addressEntry.getValue());
                    oneResponse.put("type", entry.getKey());
                    oneResponse.put("id", addressEntry.getKey());
                    finalAddress.add(oneResponse);
                }
            }
            ctx.result(finalAddress.toJSONString());
        });
        // 接口：/announcements
        // 功能：返回当前生效的公告列表，客户端通过curl请求获取
        app.get("/announcements", ctx -> {
            JSONArray result = new JSONArray();
            for (Map<String, Object> announcement : announcements) {
                JSONObject item = new JSONObject();
                item.put("id", announcement.get("id"));
                item.put("title", announcement.get("title"));
                item.put("content", announcement.get("content"));
                item.put("startTime", announcement.get("startTime"));
                item.put("endTime", announcement.get("endTime"));
                result.add(item);
            }
            ctx.contentType("application/json;charset=utf-8");
            ctx.result(result.toJSONString());
        });
    }

    /**
     * 若服务器开启校验，客户端必须发token（通过/login获取），根据token解析uid
     * 若服务器未开启校验，客户端的token有效则可以解析uid，无效也可以使用参数发来的uid
     */
    private String resolveRequestUid(Context ctx) {
        String token = ctx.header("Authorization");
        String queryUid = ctx.queryParam("uid");
        Properties properties = ConfigReader.getProp();
        if (Boolean.parseBoolean(properties.getProperty("player.auth.enabled", "false"))) {
            return JwtUtil.verifyToken(token);
        }
        if (token != null && !token.isBlank()) {
            String uid = JwtUtil.verifyToken(token);
            if (uid != null) {
                return uid;
            }
        }
        return queryUid;
    }

    public void start() {
        // 将 io.javalin 包下的日志级别设置为 WARN
        LogCore.setLogLevel("io.javalin", Level.WARN);
        try {
            app.start(port);
            LogCore.HttpServer.info("HttpServer started on : {}", app.jettyServer().server().getURI());
        } catch (Exception e) {
            LogCore.HttpServer.error("HttpServer start failed", e);
        }
    }
}
