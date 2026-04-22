package org.sunrise.game.core.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import lombok.Data;
import org.sunrise.game.core.coder.SocketMessageDecoder;
import org.sunrise.game.core.coder.SocketMessageEncoder;
import org.sunrise.game.core.message.BaseMessage;
import org.sunrise.game.core.message.BaseMessageManager;
import org.sunrise.game.core.message.ServerMessageManager;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.utils.Utils;

@Data
public class BaseServer {
    private String nodeId = this.getClass().getSimpleName() + "@" + System.currentTimeMillis();
    private Channel channel;
    private boolean startSuccess;
    private BaseMessageManager messageManager;
    private Function<ChannelHandler, String> serverHandler;
    private Function<ChannelHandler, String> pulseHandler;
    private volatile boolean isShutdown = false;

    public BaseServer() {
        this.messageManager = new ServerMessageManager(nodeId);
        this.serverHandler = r -> new BaseServerHandler(nodeId);
        this.pulseHandler = r -> new BaseServerPulseHandler(nodeId);
        Utils.setShutdownHook(this::onStop);
    }

    public BaseServer(String nodeId) {
        this.nodeId = nodeId;
        this.messageManager = new ServerMessageManager(nodeId);
        this.serverHandler = r -> new BaseServerHandler(nodeId);
        this.pulseHandler = r -> new BaseServerPulseHandler(nodeId);
        Utils.setShutdownHook(this::onStop);
    }

    public void startListen(String ip, int port) {
        if (ip == null || port == 0) {
            return;
        }
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
                        ch.pipeline().addLast(pulseHandler.apply(nodeId));
                        ch.pipeline().addLast(serverHandler.apply(nodeId));
                    }
                });

        b.bind(ip, port).addListener((ChannelFuture future) -> {
            if (future.isSuccess()) {
                this.channel = future.channel();
                onStart();
            } else {
                LogCore.BaseServer.error("Failed to bind server on ip: {} and port: {}", ip, port, future.cause());
                bossGroup.shutdownGracefully().syncUninterruptibly();
                workerGroup.shutdownGracefully().syncUninterruptibly();
                System.exit(-1);
            }
        });
    }

    public void sendToClient(BaseMessage message) {
        message.setNodeId(nodeId);
        messageManager.sendMsg(message);
    }

    public void onStart() {
        LogCore.BaseServer.info("BaseServer start, nodeId = { {} }, messageManager = { {} }, BaseServerHandler = { {} }, localAddress = { {} }",
                nodeId, messageManager.getClass().getSimpleName(), serverHandler.apply(nodeId).getClass().getSimpleName(), channel.localAddress());
        startSuccess = true;
        messageManager.run();
    }

    public void onStop() {
        isShutdown = true;
        if (!startSuccess) {
            return;
        }
        if (channel != null) {
            LogCore.BaseServer.info("BaseServer close, nodeId = { {} }, messageManager = { {} }, BaseServerHandler = { {} }, localAddress = { {} }",
                    nodeId, messageManager.getClass().getSimpleName(), serverHandler.apply(nodeId).getClass().getSimpleName(), channel.localAddress());
            channel.close();
            channel = null;
        }
        if (messageManager != null) {
            messageManager.getDispatchThread().shutdown();
            messageManager = null;
        }
    }
}
