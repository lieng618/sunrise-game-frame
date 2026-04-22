package core.client;

import core.message.MessageHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import lombok.Getter;
import lombok.Setter;
import org.sunrise.game.core.coder.SocketMessageDecoder;
import org.sunrise.game.core.coder.SocketMessageEncoder;
import org.sunrise.game.core.message.MessageType;
import org.sunrise.game.core.message.SocketMessage;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.utils.Utils;

import java.nio.charset.StandardCharsets;

@Getter
@Setter
public class TcpClient extends SocketClient {

    public void connect(String host, int port) {
        EventLoopGroup group = Utils.createEventLoopGroup(1);
        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(Utils.getServerChannelClass())
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();

                            // 添加解码器和编码器
                            pipeline.addLast(new SocketMessageEncoder());
                            pipeline.addLast(new SocketMessageDecoder());

                            // 添加自定义的业务处理器
                            pipeline.addLast(new SimpleChannelInboundHandler<SocketMessage>() {
                                @Override
                                public void channelActive(ChannelHandlerContext ctx) {
                                    String connectMessage = Utils.CLIENT_CONNECT;
                                    ctx.writeAndFlush(new SocketMessage(MessageType.biz, connectMessage.getBytes(StandardCharsets.UTF_8)));
                                }

                                @Override
                                public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                                    super.channelInactive(ctx);
                                }

                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, SocketMessage socketMessage) throws Exception {
                                    byte[] data = socketMessage.getData();
                                    MessageHandler.handler(uid, data);
                                }

                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                    LogCore.Client.error("Exception caught in TcpClient", cause);
                                }
                            });
                        }
                    });

            b.connect(host, port).addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    this.channel = future.channel();
                    connectSuccess = true;
                    // 异步监听连接关闭事件
                    future.channel().closeFuture().addListener((ChannelFutureListener) closeFuture -> group.shutdownGracefully());
                } else {
                    LogCore.Client.error("server connection failed: ip = {}, port = {}", host, port);
                    group.shutdownGracefully();
                }
            });
        } catch (Exception e) {
            LogCore.Client.error("connect failed, reason : ", e);
            group.shutdownGracefully();
        }
    }
}