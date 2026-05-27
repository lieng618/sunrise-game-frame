package org.sunrise.game.dashboard;

import ch.qos.logback.classic.Level;
import io.javalin.Javalin;
import org.sunrise.game.log.LogCore;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

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
