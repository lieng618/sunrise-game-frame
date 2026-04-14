package org.sunrise.game.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogCore {
    public static final Logger ServerStartUp = LoggerFactory.getLogger("ServerStartUp");

    public static final Logger BaseClient = LoggerFactory.getLogger("BaseClient");
    public static final Logger BaseServer = LoggerFactory.getLogger("BaseServer");

    public static final Logger RpcClient = LoggerFactory.getLogger("RpcClient");
    public static final Logger RpcServer = LoggerFactory.getLogger("RpcServer");
    public static final Logger RpcUtils = LoggerFactory.getLogger("RpcUtils");

    public static final Logger CenterServer = LoggerFactory.getLogger("CenterServer");
    public static final Logger ReportClient = LoggerFactory.getLogger("ReportClient");
    public static final Logger ExternalServer = LoggerFactory.getLogger("ExternalServer");
    public static final Logger GameServer = LoggerFactory.getLogger("GameServer");
    public static final Logger GmBackServer = LoggerFactory.getLogger("GmBackServer");

    public static final Logger Bot = LoggerFactory.getLogger("Bot");
    public static final Logger Client = LoggerFactory.getLogger("Client");
}
