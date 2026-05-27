package org.sunrise.game.external.server;

import ch.qos.logback.classic.Level;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import kcp.ChannelConfig;
import kcp.KcpServer;
import lombok.Data;
import org.sunrise.game.config.ConfigReader;
import org.sunrise.game.core.coder.SocketMessageDecoder;
import org.sunrise.game.core.coder.SocketMessageEncoder;
import org.sunrise.game.core.coder.WebSocketMessageCodec;
import org.sunrise.game.db.DbManager;
import org.sunrise.game.db.entity.EntityExternalSystem;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.rpc.node.RpcNodeManager;
import org.sunrise.game.utils.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Data
public class ExternalServer {
    private String externalHost;
    private int externalPort;
    private KcpServer kcpServer;
    private boolean tcpEnabled;
    private boolean wsEnabled;
    private boolean kcpEnabled;

    private volatile boolean statusUpdated;

    public void start() {
        int serverId = RpcNodeManager.getRpcServerId();
        int port = 0;
        String ip = null;
        int maxPort = 0;
        List<EntityExternalSystem> externalSystems = new ArrayList<>();
        try {
            var resultSet = DbManager.getDbService().queryAll("select * from `external_system`");
            for (Map<String, Object> objectMap : resultSet) {
                externalSystems.add(new EntityExternalSystem(objectMap));
            }

            for (var externalSystem : externalSystems) {
                if (externalSystem.getId() == serverId) {
                    if (externalSystem.getStatus() == 1) {
                        LogCore.ExternalServer.error("Server StartUp Failed, name = { ExternalServer }, serverId = {}, reason = {}", serverId, "server running");
                        System.exit(-1);
                    } else {
                        ip = Utils.getListenIpAddress();
                        port = externalSystem.getPort();
                    }
                }
                maxPort = Math.max(maxPort, externalSystem.getPort());
            }
            if (ip == null) {
                ip = Utils.getListenIpAddress();
                port = maxPort == 0 ? 10000 : maxPort + 3;
                DbManager.getDbService().execute("insert into `external_system` (id,ip,port) values (?,?,?)", serverId, Utils.getLocalIpAddress(), port);
            }
            externalPort = port;
            Properties properties = ConfigReader.getProp();
            if ("true".equalsIgnoreCase(properties.getProperty("external.listen.tcp", "true"))) {
                tcpEnabled = true;
                startTcpListen(ip, port);
            }
            if ("true".equalsIgnoreCase(properties.getProperty("external.listen.ws", "false"))) {
                wsEnabled = true;
                startWsListen(ip, port + 1);
            }
            if ("true".equalsIgnoreCase(properties.getProperty("external.listen.kcp", "false"))) {
                kcpEnabled = true;
                startKcpListen(ip, port + 2);
            }
        } catch (Exception e) {
            LogCore.ExternalServer.error("Server StartUp Failed, name = { ExternalServer }, serverId = {}, reason = {}", serverId, e.getLocalizedMessage());
            System.exit(-1);
        }
        Utils.setShutdownHook(() -> DbManager.getDbService().execute("update `external_system` set `status` = ? where `id` = ?", 0, serverId));
    }

    private void startTcpListen(String ip, int port) {
        EventLoopGroup bossGroup = Utils.createEventLoopGroup(1);
        EventLoopGroup workerGroup = Utils.createEventLoopGroup(0);
        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
                .channel(Utils.getServerChannelClass())
                .option(ChannelOption.SO_BACKLOG, 10240) //内核为这个套接字排队的最大连接数
                .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT) //使用内存池
                .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT) //使用内存池
                .childOption(ChannelOption.TCP_NODELAY, true) //禁用 Nagle 算法
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(128 * 1024, 256 * 1024)) // 控制输出流量
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new SocketMessageEncoder());
                        ch.pipeline().addLast(new SocketMessageDecoder());
                        ch.pipeline().addLast(new ExternalServerHandler());
                    }
                });

        b.bind(ip, port).addListener((ChannelFuture future) -> {
            if (future.isSuccess()) {
                LogCore.ExternalServer.info("External Tcp Server start, port = { {} }", port);
                markServerRunning();
            } else {
                LogCore.ExternalServer.error("Failed to bind server on ip: {} and port: {}", ip, port, future.cause());
                bossGroup.shutdownGracefully().syncUninterruptibly();
                workerGroup.shutdownGracefully().syncUninterruptibly();
                System.exit(-1);
            }
        });
    }

    public void startWsListen(String ip, int port) {
        if (ip == null || port == 0) {
            return;
        }
        EventLoopGroup bossGroup = Utils.createEventLoopGroup(1);
        EventLoopGroup workerGroup = Utils.createEventLoopGroup(0);
        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
                .channel(Utils.getServerChannelClass())
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new HttpServerCodec()); // HTTP 协议解析，用于握手阶段
                        ch.pipeline().addLast(new HttpObjectAggregator(65536)); // HTTP 协议解析，用于握手阶段
                        ch.pipeline().addLast(new WebSocketServerCompressionHandler(8 * 1024 * 1024)); // WebSocket 数据压缩扩展
                        ch.pipeline().addLast(new WebSocketMessageCodec());
                        ch.pipeline().addLast(new WebSocketServerProtocolHandler("/", null, true, 128 * 1024 * 1024)); // WebSocket 握手、控制帧处理
                        ch.pipeline().addLast(new ExternalServerHandler());
                    }
                });

        b.bind(ip, port).addListener((ChannelFuture future) -> {
            if (future.isSuccess()) {
                LogCore.ExternalServer.info("External Ws Server start, port = { {} }", port);
                markServerRunning();
            } else {
                LogCore.ExternalServer.error("Failed to bind server on ip: {} and port: {}", ip, port, future.cause());
                bossGroup.shutdownGracefully();
                workerGroup.shutdownGracefully();
                System.exit(-1);
            }
        });
    }

    public void startKcpListen(String ip, int port) {
        LogCore.setLogLevel("kcp", Level.WARN);
        ChannelConfig channelConfig = new ChannelConfig();
        channelConfig.nodelay(true, 10, 2, true);
        channelConfig.setSndwnd(256);
        channelConfig.setRcvwnd(256);
        channelConfig.setMtu(1400);
        channelConfig.setTimeoutMillis(30000);
        channelConfig.setUseConvChannel(true);
        channelConfig.setCrc32Check(true);

        KcpServerHandler kcpServerHandler = new KcpServerHandler();
        kcpServer = new KcpServer();
        kcpServer.init(kcpServerHandler, channelConfig, port);

        LogCore.ExternalServer.info("External Kcp Server start, port = { {} }", port);
        markServerRunning();
    }

    private void markServerRunning() {
        if (statusUpdated) {
            return;
        }
        statusUpdated = true;
        DbManager.getDbService().execute("update `external_system` set `status` = ?, `ip` = ? where `id` = ?", 1, Utils.getLocalIpAddress(), RpcNodeManager.getRpcServerId());
    }
}