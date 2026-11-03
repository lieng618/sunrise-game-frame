package org.sunrise.game.dashboard;

import ch.qos.logback.classic.Level;
import com.alibaba.fastjson2.JSON;
import io.javalin.Javalin;
import org.sunrise.game.core.HotswapScanner;
import org.sunrise.game.log.LogCore;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class CenterDashboardServer {
    private final int port;
    private Javalin app;

    public CenterDashboardServer(int port) {
        this.port = port;
    }

    public void start() {
        app = Javalin.create(config -> config.showJavalinBanner = false);

        app.get("/", ctx -> {
            ctx.contentType("text/html; charset=utf-8");
            ctx.result(loadResource("/index.html"));
        });

        app.get("/api/topology", ctx -> {
            ctx.contentType("application/json; charset=utf-8");
            ctx.result(NodeTopologyBuilder.buildSnapshot().toJSONString());
        });

        app.post("/api/hotswap/reload", ctx -> {
            Map<?, ?> body = ctx.bodyAsClass(Map.class);
            Object jarPath = body.get("jarPath");
            Object operation = body.get("operation");
            HotswapScanner scanner = HotswapScanner.getInstance();
            HotswapScanner.Report report = null;
            if (scanner != null) {
                report = "rollback".equals(operation)
                        ? scanner.rollback()
                        : scanner.reload(jarPath == null ? null : String.valueOf(jarPath));
            }
            Map<String, Object> result = new HashMap<>();
            result.put("nodeId", "center-" + port);
            result.put("nodeType", "CenterServer");
            if (report == null) {
                result.put("status", "FAILED");
                result.put("message", "热更基线尚未初始化");
            } else {
                result.put("status", report.getStatus());
                result.put("message", report.getMessage());
                result.put("redefinedClasses", report.getRedefinedClasses());
                result.put("skippedNotLoadedClasses", report.getSkippedNotLoadedClasses());
                result.put("skippedUnsupportedClasses", report.getSkippedUnsupportedClasses());
                result.put("failedClasses", report.getFailedClasses());
            }
            ctx.contentType("application/json; charset=utf-8");
            ctx.result(JSON.toJSONString(result));
        });

        app.get("/api/hotswap/status", ctx -> {
            HotswapScanner scanner = HotswapScanner.getInstance();
            ctx.contentType("application/json; charset=utf-8");
            ctx.result(JSON.toJSONString(scanner == null
                    ? Map.of("status", "UNINITIALIZED")
                    : scanner.status(null)));
        });

        LogCore.setLogLevel("io.javalin", Level.WARN);
        try {
            app.start(port);
            LogCore.CenterServer.info("Center dashboard started on http://127.0.0.1:{}", port);
        } catch (Exception e) {
            LogCore.CenterServer.error("Center dashboard start failed, port = {}", port, e);
        }
    }

    public void stop() {
        if (app != null) {
            app.stop();
        }
    }

    private static String loadResource(String path) throws IOException {
        try (InputStream inputStream = CenterDashboardServer.class.getResourceAsStream(path)) {
            if (inputStream == null) {
                throw new IOException("resource not found: " + path);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
