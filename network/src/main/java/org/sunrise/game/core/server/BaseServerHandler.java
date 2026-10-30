package org.sunrise.game.core.server;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.Getter;
import lombok.Setter;
import org.sunrise.game.core.auth.RpcAuth;
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
            String[] parts = Utils.parseClientConnect(message);
            String connectNodeId = parts[0];
            if (RpcAuth.enabled() && !RpcAuth.verify(parts[1])) {
                LogCore.BaseServer.error("rpc auth failed, connectNode = { {} }, remoteAddress = { {} }", connectNodeId, channel.remoteAddress());
                channel.close();
                return;
            }
            boolean registered = ConnectionManager.createConnect(connectNodeId, channel);
            if (registered) {
                connectNode = connectNodeId;
                String sendMsg = Utils.CLIENT_CONNECT_RESPONSE + Utils.SUCCESS + nodeId;
                channel.writeAndFlush(new SocketMessage(MessageType.biz, sendMsg.getBytes(StandardCharsets.UTF_8)));

                LogCore.BaseServer.info("recv connection success, connectNode = { {} }, remoteAddress = { {} }", connectNode, channel.remoteAddress());
                onRecvConnect();
            } else {
                String sendMsg = Utils.CLIENT_CONNECT_RESPONSE + Utils.FAILED;
                channel.writeAndFlush(new SocketMessage(MessageType.biz, sendMsg.getBytes(StandardCharsets.UTF_8)));
                channel.close();

                LogCore.BaseServer.info("recv connection failed, connectNode = { {} }, remoteAddress = { {} }", connectNodeId, channel.remoteAddress());
            }

        } else {
            channel.close();
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent state && state.state() == IdleState.READER_IDLE) {
            // 对端每 10s 必有心跳，读空闲即无声死亡（断电/拔线/网络分区），
            // close 走 channelInactive 释放 ConnectionManager 中的 nodeId，允许对端重连
            LogCore.BaseServer.error("reader idle timeout, close zombie connection, connectNode = { {} }, remoteAddress = { {} }", connectNode, ctx.channel().remoteAddress());
            ctx.close();
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    public void onRecvConnect() {
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (connectNode != null) {
            ConnectionManager.removeConnect(connectNode, ctx.channel());
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
