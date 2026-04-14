package org.sunrise.game.external.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import lombok.Data;
import org.sunrise.game.core.coder.SocketMessageDecoder;
import org.sunrise.game.core.coder.SocketMessageEncoder;
import org.sunrise.game.core.coder.WebSocketMessageCodec;
import org.sunrise.game.db.DbService;
import org.sunrise.game.db.entity.EntityExternalSystem;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.rpc.node.RpcNodeManager;
import org.sunrise.game.utils.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class ExternalServer {
    private final DbService dbService = new DbService();
    private String externalHost;
    private int externalPort;

    /**
     * 保证当前服务的唯一性
     */
    public void start() {
        int serverId = RpcNodeManager.getRpcServerId();
        int port = 0;
        String ip = null;
        int maxPort = 0;
        List<EntityExternalSystem> externalSystems = new ArrayList<>();
        try {
            var resultSet = dbService.queryAll("select * from `external_system`");
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
                port = maxPort == 0 ? 10000 : maxPort + 2;
                dbService.execute("insert into `external_system` (id,ip,port) values (?,?,?)", serverId, Utils.getLocalIpAddress(), port);
            }
            externalPort = port;
            startTcpListen(ip, port);
            startWsListen(ip, port + 1);
        } catch (Exception e) {
            LogCore.ExternalServer.error("Server StartUp Failed, name = { ExternalServer }, serverId = {}, reason = {}", serverId, e.getLocalizedMessage());
            System.exit(-1);
        }
        Utils.setShutdownHook(() -> {
            dbService.execute("update `external_system` set `status` = ? where `id` = ?", 0, serverId);
        });
    }

    private void startTcpListen(String ip, int port) {
        EventLoopGroup bossGroup = Utils.isLinux() ? new EpollEventLoopGroup(1) : new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = Utils.isLinux() ? new EpollEventLoopGroup() : new NioEventLoopGroup();
        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
                .channel(Utils.isLinux() ? EpollServerSocketChannel.class : NioServerSocketChannel.class)
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
                LogCore.ExternalServer.info("External Tcp Server start, localAddress = { {} }", future.channel().localAddress());
                dbService.execute("update `external_system` set `status` = ?, `ip` = ? where `id` = ?", 1, Utils.getLocalIpAddress(), RpcNodeManager.getRpcServerId());
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
        EventLoopGroup bossGroup = Utils.isLinux() ? new EpollEventLoopGroup(1) : new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = Utils.isLinux() ? new EpollEventLoopGroup() : new NioEventLoopGroup();
        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
                .channel(Utils.isLinux() ? EpollServerSocketChannel.class : NioServerSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new HttpServerCodec()); // HTTP 协议解析，用于握手阶段
                        ch.pipeline().addLast(new HttpObjectAggregator(65536)); // HTTP 协议解析，用于握手阶段
                        ch.pipeline().addLast(new WebSocketServerCompressionHandler()); // WebSocket 数据压缩扩展
                        ch.pipeline().addLast(new WebSocketMessageCodec());
                        ch.pipeline().addLast(new WebSocketServerProtocolHandler("/", null, true, 128 * 1024 * 1024)); // WebSocket 握手、控制帧处理
                        ch.pipeline().addLast(new ExternalServerHandler());
                    }
                });

        b.bind(ip, port).addListener((ChannelFuture future) -> {
            if (future.isSuccess()) {
                LogCore.ExternalServer.info("External Ws Server start, localAddress = { {} }", future.channel().localAddress());
            } else {
                LogCore.ExternalServer.error("Failed to bind server on ip: {} and port: {}", ip, port, future.cause());
                bossGroup.shutdownGracefully();
                workerGroup.shutdownGracefully();
                System.exit(-1);
            }
        });
    }
}
