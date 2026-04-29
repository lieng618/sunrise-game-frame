package org.sunrise.game.gmback.server;

import ch.qos.logback.classic.Level;
import com.alibaba.fastjson2.JSON;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import io.javalin.http.staticfiles.Location;
import io.javalin.json.JsonMapper;
import org.jetbrains.annotations.NotNull;
import org.sunrise.game.config.ConfigReader;
import org.sunrise.game.gmback.server.controller.AuthController;
import org.sunrise.game.gmback.server.controller.AnnouncementController;
import org.sunrise.game.gmback.server.controller.BanHumanController;
import org.sunrise.game.gmback.server.controller.ControllerManager;
import org.sunrise.game.gmback.server.controller.GmController;
import org.sunrise.game.gmback.server.controller.MuteHumanController;
import org.sunrise.game.gmback.server.controller.NodeController;
import org.sunrise.game.gmback.server.controller.OnlinePlayerController;
import org.sunrise.game.gmback.server.controller.OperationLogController;
import org.sunrise.game.gmback.server.controller.ServerStatusController;
import org.sunrise.game.gmback.server.controller.UserController;
import org.sunrise.game.gmback.server.controller.WhitelistController;
import org.sunrise.game.jwt.JwtUtil;
import org.sunrise.game.log.LogCore;

import java.io.File;
import java.lang.reflect.Type;
import java.util.Properties;

public class AdminServer {

    public void start() {
        Properties properties = ConfigReader.getProp();
        if (properties == null) {
            LogCore.GmBackServer.error("AdminServer start failed: config not found.");
            return;
        }

        int port = Integer.parseInt(properties.getProperty("admin.port"));
        String uiPath = properties.getProperty("admin.uipath");

        // 确保 UI 目录存在
        File uiDir = new File(uiPath);
        if (!uiDir.exists() || !uiDir.isDirectory()) {
            LogCore.GmBackServer.error("Admin UI path not found: " + uiPath);
            return;
        }

        // 初始化 JWT Key
        long jwtExpiration = Long.parseLong(properties.getProperty("admin.jwt.expiration", "3600000")); // 默认1小时
        JwtUtil.init(jwtExpiration);

        Javalin app = Javalin.create(config -> {
            // 配置静态资源 (前端页面)
            if (uiDir.exists()) {
                config.staticFiles.add(uiPath, Location.EXTERNAL);
            }

            // 配置 FastJSON 作为默认 JSON 处理引擎
            config.jsonMapper(new JsonMapper() {
                @NotNull
                @Override
                public String toJsonString(@NotNull Object obj, @NotNull Type type) {
                    return JSON.toJSONString(obj);
                }

                @NotNull
                @Override
                public <T> T fromJsonString(@NotNull String json, @NotNull Type targetType) {
                    return JSON.parseObject(json, targetType);
                }
            });
        });

        // 初始化控制器
        ControllerManager.initController();

        // 统一鉴权
        app.before("/api/*", ctx -> {
            // 登录接口无需鉴权
            if (ctx.path().equals("/api/login")) {
                return;
            }

            String token = ctx.header("Authorization");
            // Token 校验
            if (token == null || token.isEmpty() || JwtUtil.verifyToken(token) == null) {
                // 401 未授权，拦截请求
                ctx.status(HttpStatus.UNAUTHORIZED).result("Unauthorized");
                ctx.skipRemainingHandlers(); // 停止后续处理
            }
        });

        app.post("/api/login", ControllerManager.getController(AuthController.class)::login);
        app.get("/api/nodes", ControllerManager.getController(NodeController.class)::list);
        app.post("/api/config/reload", ControllerManager.getController(NodeController.class)::reloadConfig);
        app.post("/api/gm/send-mail", ControllerManager.getController(GmController.class)::sendMail);
        app.post("/api/gm/kick", ControllerManager.getController(GmController.class)::kick);
        app.get("/api/logs", ControllerManager.getController(OperationLogController.class)::list);
        
        // 用户管理路由
        app.get("/api/users", ControllerManager.getController(UserController.class)::list);
        app.post("/api/users", ControllerManager.getController(UserController.class)::add);
        app.delete("/api/users/{username}", ControllerManager.getController(UserController.class)::delete);
        app.put("/api/users/{username}/password", ControllerManager.getController(UserController.class)::updatePassword);

        // 封禁玩家路由
        app.get("/api/ban/list", ControllerManager.getController(BanHumanController.class)::list);
        app.post("/api/ban", ControllerManager.getController(BanHumanController.class)::ban);
        app.post("/api/unban", ControllerManager.getController(BanHumanController.class)::unban);

        // 禁言玩家路由
        app.get("/api/mute/list", ControllerManager.getController(MuteHumanController.class)::list);
        app.post("/api/mute", ControllerManager.getController(MuteHumanController.class)::mute);
        app.post("/api/unmute", ControllerManager.getController(MuteHumanController.class)::unmute);

        app.get("/api/online-players", ControllerManager.getController(OnlinePlayerController.class)::list);

        // 服务器状态路由
        app.get("/api/server-status", ControllerManager.getController(ServerStatusController.class)::getStatus);
        app.post("/api/server-status", ControllerManager.getController(ServerStatusController.class)::setStatus);

        // 白名单路由
        app.get("/api/whitelist", ControllerManager.getController(WhitelistController.class)::list);
        app.post("/api/whitelist", ControllerManager.getController(WhitelistController.class)::add);
        app.post("/api/whitelist/remove", ControllerManager.getController(WhitelistController.class)::remove);

        // 公告路由
        app.get("/api/announcements", ControllerManager.getController(AnnouncementController.class)::list);
        app.post("/api/announcements", ControllerManager.getController(AnnouncementController.class)::add);
        app.post("/api/announcements/update", ControllerManager.getController(AnnouncementController.class)::update);
        app.post("/api/announcements/remove", ControllerManager.getController(AnnouncementController.class)::remove);
        // 将 io.javalin 包下的日志级别设置为 WARN
        LogCore.setLogLevel("io.javalin", Level.WARN);
        try {
            app.start(port);
            LogCore.GmBackServer.info("AdminServer started on : {}", app.jettyServer().server().getURI());
        } catch (Exception e) {
            LogCore.GmBackServer.error("AdminServer start failed", e);
        }
    }
}