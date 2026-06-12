package org.sunrise.game.core.server;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.Getter;
import lombok.Setter;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.core.message.MessageType;
import org.sunrise.game.core.message.SocketMessage;
import org.sunrise.game.utils.Utils;

import java.nio.charset.StandardCharsets;

@Getter
@Setter
public class BaseServerHandler extends SimpleChannelInboundHandler<SocketMessage> {
    private final String nodeId;
    private boolean isAuthMessage = true;// 是否为认证消息
    private String connectNode;

    public BaseServerHandler(String nodeId) {
        this.nodeId = nodeId;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, SocketMessage socketMessage) throws Exception {
        if (socketMessage.getMessageType() != MessageType.biz) {
            return;
        }
        byte[] data = socketMessage.getData();
        if (isAuthMessage) {
            verify(ctx.channel(), data);
            return;
        }

        // 来自Client的消息
        BaseServerManager.recvFromClient(nodeId, data);
    }

    // 首次收到消息
    private void verify(Channel channel, byte[] data) {
        isAuthMessage = false;
        String message = new String(data, StandardCharsets.UTF_8);
        if (message.startsWith(Utils.CLIENT_CONNECT)) {
            connectNode = message.substring(Utils.CLIENT_CONNECT.length());

            boolean exist = ConnectionManager.isConnectExist(connectNode);
            if (!exist) {
                String sendMsg = Utils.CLIENT_CONNECT_RESPONSE + Utils.SUCCESS + nodeId;
                ConnectionManager.createConnect(connectNode, channel);
                channel.writeAndFlush(new SocketMessage(MessageType.biz, sendMsg.getBytes(StandardCharsets.UTF_8)));

                LogCore.BaseServer.info("recv connection success, connectNode = { {} }, remoteAddress = { {} }", connectNode, channel.remoteAddress());
                onRecvConnect();
            } else {
                String sendMsg = Utils.CLIENT_CONNECT_RESPONSE + Utils.FAILED;
                channel.writeAndFlush(new SocketMessage(MessageType.biz, sendMsg.getBytes(StandardCharsets.UTF_8)));
                channel.close();

                LogCore.BaseServer.info("recv connection failed, connectNode = { {} }, remoteAddress = { {} }", connectNode, channel.remoteAddress());
            }

        } else {
            channel.close();
        }
    }

    public void onRecvConnect() {
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (connectNode != null) {
            ConnectionManager.removeConnect(connectNode);
        }
        LogCore.BaseServer.error("disconnected, connectNode = { {} }, remoteAddress = {}", connectNode, ctx.channel().remoteAddress());
        super.channelInactive(ctx);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LogCore.BaseServer.error("Exception caught, reason = {}, remoteAddress = {}", cause.getMessage(), ctx.channel().remoteAddress());
    }
}
