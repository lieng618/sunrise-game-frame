package org.sunrise.game.core.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.Data;
import org.sunrise.game.core.coder.SocketMessageDecoder;
import org.sunrise.game.core.coder.SocketMessageEncoder;
import org.sunrise.game.core.message.BaseMessage;
import org.sunrise.game.core.message.BaseMessageManager;
import org.sunrise.game.core.message.ClientMessageManager;
import org.sunrise.game.core.server.Function;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.utils.Utils;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Data
public class BaseClient {
    private String nodeId = this.getClass().getSimpleName() + "@" + System.currentTimeMillis();
    private Channel serverChannel;
    private String serverNodeId;
    private volatile Boolean connectStatus = false;
    private BaseMessageManager messageManager;
    private Function<ChannelHandler, String> clientHandler;
    private Function<ChannelHandler, String> pulseHandler;
    private EventLoopGroup group;
    private Bootstrap b;
    private volatile boolean isShutdown = false;
    private AtomicBoolean connectFinish = new AtomicBoolean(false);
    /** 是否自己创建了 group（需要在 onStop 时关闭），共享 group 不关闭 */
    private boolean groupOwned = true;

    public BaseClient() {
        this.messageManager = new ClientMessageManager(nodeId);
        this.clientHandler = r -> new BaseClientHandler(nodeId);
        this.pulseHandler = r -> new BaseClientPulseHandler(nodeId);
        this.group = Utils.createEventLoopGroup(1);
        this.groupOwned = true;
        this.b = new Bootstrap();
        this.b.group(this.group);
        init();
    }

    public BaseClient(String nodeId) {
        this.nodeId = nodeId;
        this.messageManager = new ClientMessageManager(nodeId);
        this.clientHandler = r -> new BaseClientHandler(nodeId);
        this.pulseHandler = r -> new BaseClientPulseHandler(nodeId);
        this.group = Utils.createEventLoopGroup(1);
        this.groupOwned = true;
        this.b = new Bootstrap();
        this.b.group(this.group);
        init();
    }

    /**
     * 多个BaseClient可共用同一个group（传入外部 group 时不会在 onStop 中关闭）。
     */
    public BaseClient(String nodeId, EventLoopGroup group, Bootstrap b) {
        this.nodeId = nodeId;
        this.messageManager = new ClientMessageManager(nodeId);
        this.clientHandler = r -> new BaseClientHandler(nodeId);
        this.pulseHandler = r -> new BaseClientPulseHandler(nodeId);
        if (group == null) {
            group = Utils.createEventLoopGroup(1);
            this.groupOwned = true;
        } else {
            this.groupOwned = false; // 外部传入，不负责关闭
        }
        this.group = group;
        if (b == null) {
            b = new Bootstrap();
        }
        this.b = b;
        this.b.group(this.group);
        init();
    }

    public void init() {
        b.channel(Utils.getClientChannelClass())
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();

                        // 添加解码器和编码器
                        pipeline.addLast(new SocketMessageEncoder());
                        pipeline.addLast(new SocketMessageDecoder());
                        // 添加心跳机制
                        pipeline.addLast(new IdleStateHandler(0, 10, 0, TimeUnit.SECONDS));
                        pipeline.addLast(pulseHandler.apply(nodeId));
                        // 添加自定义的业务处理器
                        pipeline.addLast(clientHandler.apply(nodeId));
                    }
                });
    }

    public void connect(String ip, int port) {
        // 异步连接
        if (ip == null) {
            LogCore.BaseClient.error("server connection failed: ip = {}, port = {}", null, port);
            onFail();
            return;
        }
        b.connect(ip, port).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                this.serverChannel = future.channel();
                onStart();
            } else {
                LogCore.BaseClient.error("server connection failed: ip = {}, port = {}", ip, port);
                onFail();
            }
        });
    }

    public void connectBlock(String ip, int port) {
        connect(ip, port);
        long timeout = 5000;
        long start = System.currentTimeMillis();

        while (!connectStatus) {
            if (System.currentTimeMillis() - start > timeout) {
                LogCore.BaseClient.error("connectBlock timeout: ip = {}, port = {}", ip, port);
                return;
            }
            Utils.sleep(30);
        }
    }

    public void sendToServer(BaseMessage message) {
        message.setNodeId(nodeId);
        messageManager.sendMsg(message);
    }

    public void onStart() {
        LogCore.BaseClient.info("BaseClient start, nodeId = { {} }, messageManager = { {} }, baseClientHandler = { {} }, remoteAddress = { {} }", nodeId, messageManager.getClass().getSimpleName(), clientHandler.apply(nodeId).getClass().getSimpleName(), serverChannel.remoteAddress());
        connectStatus = true;
        messageManager.run();
    }

    public void onFail() {
    }

    /**
     * 优雅停机：关闭 channel → 排空队列 → 关闭 DispatchThread → 关闭自有 EventLoopGroup。
     * 由 {@code GracefulShutdown} 统一编排调用。
     */
    public void onStop() {
        isShutdown = true;
        if (serverChannel != null) {
            connectStatus = false;
            LogCore.BaseClient.info("BaseClient close, nodeId = { {} }, remoteAddress = { {} }",
                    nodeId, serverChannel.remoteAddress());
            serverChannel.close();
            serverChannel = null;
        }
        if (messageManager != null) {
            // 停机前先排空队列
            messageManager.drainRecvQueue(2000);
            messageManager.drainSendQueue(3000);
            messageManager.getDispatchThread().shutdown();
            messageManager.getDispatchThread().awaitTermination(5000);
            messageManager = null;
        }
        // 仅关闭自身创建的 EventLoopGroup
        if (groupOwned && group != null) {
            group.shutdownGracefully().awaitUninterruptibly(5000);
            group = null;
        }
    }
}
