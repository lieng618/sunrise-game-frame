package org.sunrise.game.external.server;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.sunrise.game.core.message.MessageType;
import org.sunrise.game.core.message.SocketMessage;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.utils.Utils;

import java.nio.charset.StandardCharsets;

public class ExternalServerHandler extends SimpleChannelInboundHandler<SocketMessage> {
    private boolean isAuthMessage = true;// 是否为认证消息
    private ClientConnection clientConnection;

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
        if (!clientConnection.dataCheck(data)) {
            return;
        }
        clientConnection.getMsgQueue().add(data);
    }

    private void verify(Channel channel, byte[] data) {
        isAuthMessage = false;
        String message = new String(data, StandardCharsets.UTF_8);
        if (message.startsWith(Utils.CLIENT_CONNECT)) {
            clientConnection = ExternalConnectionManger.createClientConnect(channel);
            LogCore.ExternalServer.info("recv connection from client : connectionId = {}, remoteAddress = {}", clientConnection.getId(), channel.remoteAddress());
        } else {
            LogCore.ExternalServer.error("recv connection from client : check fail,  close, remoteAddress = {}", channel.remoteAddress());
            channel.close();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (clientConnection != null) {
            ExternalConnectionManger.removeClientConnect(clientConnection.getId());
            LogCore.ExternalServer.info("client disconnected, id = {}, remoteAddress = {}", clientConnection.getId(), clientConnection.getRemoteAddress());
            clientConnection = null;
        }
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LogCore.ExternalServer.error("Exception caught, reason = {}, remoteAddress = {}", cause.getMessage(), ctx.channel().remoteAddress());
    }
}