package org.sunrise.game.gmback.server.controller;

import io.javalin.http.Context;
import org.sunrise.game.config.ConfigReader;
import org.sunrise.game.jwt.JwtUtil;
import org.sunrise.game.log.LogCore;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class AuthController extends BaseController {

    public void login(Context ctx) {
        Properties properties = ConfigReader.getProp();
        if (properties == null) {
            LogCore.GmBackServer.error("AdminServer login failed: config not found.");
            return;
        }
        String u = getBodyParam(ctx, "user", String.class);
        String p = getBodyParam(ctx, "pass", String.class);
        
        if (u == null || p == null) {
            fail(ctx, 401, "Username and password are required");
            return;
        }
        
        String USERNAME = properties.getProperty("admin.user");
        String PASSWORD = properties.getProperty("admin.password");
        
        boolean loginSuccess = false;
        
        // 首先尝试 admin 用户验证
        if (USERNAME != null && USERNAME.equals(u) && PASSWORD != null && PASSWORD.equals(p)) {
            loginSuccess = true;
        } else {
            // 如果不是 admin，尝试从 UserController 验证普通用户
            UserController userController = ControllerManager.getController(UserController.class);
            if (userController != null) {
                UserController.User user = userController.findUser(u);
                if (user != null && userController.verifyPassword(p, user.getPassword())) {
                    loginSuccess = true;
                }
            }
        }
        
        if (loginSuccess) {
            // 登录成功后，清除该用户的黑名单记录（允许新 token 使用）
            JwtUtil.clearUserBlacklist(u);
            
            String token = JwtUtil.createToken(u);
            Map<String, Object> res = new HashMap<>();
            res.put("code", 200);
            res.put("msg", "Login Success");
            res.put("token", token);
            ctx.json(res);
            
            ControllerManager.getController(OperationLogController.class).recordLoginLog(ctx, u, OperationLogController.OperationType.LOGIN, "登录系统");
        } else {
            fail(ctx, 401, "Auth Failed");
        }
    }
}