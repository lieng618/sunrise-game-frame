package core.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import lombok.Getter;
import lombok.Setter;
import core.message.MessageHandler;
import org.sunrise.game.core.coder.WebSocketMessageCodec;
import org.sunrise.game.core.message.MessageType;
import org.sunrise.game.core.message.SocketMessage;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.utils.Utils;

import java.net.URI;
import java.nio.charset.StandardCharsets;

@Getter
@Setter
public class WsClient extends SocketClient {
    public void connect(String host, int port) {
        EventLoopGroup group = Utils.createEventLoopGroup(1);
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(Utils.getServerChannelClass())
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new HttpClientCodec());
                            ch.pipeline().addLast(new HttpObjectAggregator(65536));
                            ch.pipeline().addLast(WebSocketClientCompressionHandler.INSTANCE);
                            ch.pipeline().addLast(new WebSocketMessageCodec());
                            ch.pipeline().addLast(new SimpleChannelInboundHandler<Object>() {
                                private final WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                                        URI.create("ws://" + host + ":" + port),
                                        WebSocketVersion.V13,
                                        null,
                                        true,
                                        HttpHeaders.EMPTY_HEADERS,
                                        128 * 1024 * 1024
                                );

                                @Override
                                public void channelActive(ChannelHandlerContext ctx) {
                                    LogCore.Client.info("Starting WebSocket handshake...");
                                    handshaker.handshake(ctx.channel());
                                }

                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
                                    if (!handshaker.isHandshakeComplete()) {
                                        handleHandshake(ctx, msg);
                                    } else if (msg instanceof SocketMessage socketMessage) {
                                        handleSocketMessage(ctx, socketMessage);
                                    } else {
                                        LogCore.Client.warn("Received unexpected message: {}", msg);
                                    }
                                }

                                private void handleHandshake(ChannelHandlerContext ctx, Object msg) throws Exception {
                                    if (msg instanceof FullHttpResponse response) {
                                        handshaker.finishHandshake(ctx.channel(), response);
                                        LogCore.Client.info("WebSocket handshake complete");
                                        sendInitialMessage(ctx);
                                        connectSuccess = true;
                                    } else {
                                        LogCore.Client.error("Unexpected handshake response: {}", msg);
                                        ctx.close();
                                    }
                                }

                                private void sendInitialMessage(ChannelHandlerContext ctx) {
                                    String connectMessage = Utils.CLIENT_CONNECT;
                                    SocketMessage initialMessage = new SocketMessage(MessageType.biz, connectMessage.getBytes(StandardCharsets.UTF_8));
                                    ctx.writeAndFlush(initialMessage);
                                    LogCore.Client.info("Sent initial message: {}", connectMessage);
                                }

                                private void handleSocketMessage(ChannelHandlerContext ctx, SocketMessage message) throws Exception {
                                    byte[] data = message.getData();
                                    MessageHandler.handler(uid, data);
                                }

                                @Override
                                public void channelInactive(ChannelHandlerContext ctx) {
                                    LogCore.Client.warn("WebSocket connection closed");
                                    group.shutdownGracefully();
                                }

                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                    LogCore.Client.error("Exception caught in WebSocket client", cause);
                                }
                            });
                        }
                    });

            bootstrap.connect(host, port).addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    this.channel = future.channel();
                    LogCore.Client.info("WebSocket connected to {}:{}", host, port);
                } else {
                    LogCore.Client.error("Failed to connect WebSocket to {}:{}", host, port);
                    group.shutdownGracefully();
                }
            });
        } catch (Exception e) {
            LogCore.Client.error("WebSocket connection failed", e);
            group.shutdownGracefully();
        }
    }
}