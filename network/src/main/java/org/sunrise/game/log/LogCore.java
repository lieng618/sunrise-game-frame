package org.sunrise.game.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogCore {
    public static final Logger ServerStartUp = LoggerFactory.getLogger("ServerStartUp");

    public static final Logger BaseClient = LoggerFactory.getLogger("BaseClient");
    public static final Logger BaseServer = LoggerFactory.getLogger("BaseServer");
    public static final Logger BasePulse = LoggerFactory.getLogger("BasePulse");

    public static final Logger RpcClient = LoggerFactory.getLogger("RpcClient");
    public static final Logger RpcServer = LoggerFactory.getLogger("RpcServer");
    public static final Logger RpcUtils = LoggerFactory.getLogger("RpcUtils");

    public static final Logger CenterServer = LoggerFactory.getLogger("CenterServer");
    public static final Logger ReportClient = LoggerFactory.getLogger("ReportClient");
    public static final Logger ExternalServer = LoggerFactory.getLogger("ExternalServer");
    public static final Logger GameServer = LoggerFactory.getLogger("GameServer");
    public static final Logger GmBackServer = LoggerFactory.getLogger("GmBackServer");
    public static final Logger HttpServer = LoggerFactory.getLogger("HttpServer");
    public static final Logger GlobalServer = LoggerFactory.getLogger("GlobalServer");

    public static final Logger Bot = LoggerFactory.getLogger("Bot");
    public static final Logger Stress = LoggerFactory.getLogger("Stress");
    public static final Logger Client = LoggerFactory.getLogger("Client");

    public static void setLogLevel(String loggerKey, Level level) {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger rootLogger = loggerContext.getLogger(loggerKey);
        rootLogger.setLevel(level);
    }
}
