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
    private Boolean connectStatus = false;
    private BaseMessageManager messageManager;
    private Function<ChannelHandler, String> clientHandler;
    private Function<ChannelHandler, String> pulseHandler;
    private EventLoopGroup group;
    private Bootstrap b;
    private volatile boolean isShutdown = false;
    private AtomicBoolean connectFinish = new AtomicBoolean(false);

    public BaseClient() {
        this.messageManager = new ClientMessageManager(nodeId);
        this.clientHandler = r -> new BaseClientHandler(nodeId);
        this.pulseHandler = r -> new BaseClientPulseHandler(nodeId);
        this.group = Utils.createEventLoopGroup(1);
        this.b = new Bootstrap();
        this.b.group(this.group);
        init();
        Utils.setShutdownHook(this::onStop);
    }

    public BaseClient(String nodeId) {
        this.nodeId = nodeId;
        this.messageManager = new ClientMessageManager(nodeId);
        this.clientHandler = r -> new BaseClientHandler(nodeId);
        this.pulseHandler = r -> new BaseClientPulseHandler(nodeId);
        this.group = Utils.createEventLoopGroup(1);
        this.b = new Bootstrap();
        this.b.group(this.group);
        init();
        Utils.setShutdownHook(this::onStop);
    }

    /**
     * 多个BaseClient可共用同一个group
     */
    public BaseClient(String nodeId, EventLoopGroup group, Bootstrap b) {
        this.nodeId = nodeId;
        this.messageManager = new ClientMessageManager(nodeId);
        this.clientHandler = r -> new BaseClientHandler(nodeId);
        this.pulseHandler = r -> new BaseClientPulseHandler(nodeId);
        if (group == null) {
            group = Utils.createEventLoopGroup(1);
        }
        this.group = group;
        if (b == null) {
            b = new Bootstrap();
        }
        this.b = b;
        this.b.group(this.group);
        init();
        Utils.setShutdownHook(this::onStop);
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

    public void connectBlock(String ip, int port) throws InterruptedException {
        connect(ip, port);
        while (true) {
            if (connectStatus) {
                return;
            }
            Thread.sleep(100);
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

    public void onStop() {
        isShutdown = true;
        if (serverChannel != null) {
            connectStatus = false;
            serverChannel.close();
            serverChannel = null;
        }
        if (messageManager != null) {
            messageManager.getDispatchThread().shutdown();
            messageManager = null;
        }
    }
}
