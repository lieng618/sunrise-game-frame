package org.sunrise.game.core.client;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.Getter;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.core.message.MessageType;
import org.sunrise.game.core.message.SocketMessage;
import org.sunrise.game.utils.Utils;

import java.nio.charset.StandardCharsets;

public class BaseClientHandler extends SimpleChannelInboundHandler<SocketMessage> {
    @Getter
    private final String nodeId;
    private boolean isAuthMessage = true;

    public BaseClientHandler(String nodeId) {
        this.nodeId = nodeId;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        String connectMessage = Utils.CLIENT_CONNECT + nodeId;
        ctx.writeAndFlush(new SocketMessage(MessageType.biz, connectMessage.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, SocketMessage socketMessage) {
        byte[] data = socketMessage.getData();
        if (isAuthMessage) {
            String responseMessage = new String(data, StandardCharsets.UTF_8);
            if (responseMessage.startsWith(Utils.CLIENT_CONNECT_RESPONSE + Utils.SUCCESS)) {
                String serverNodeId = responseMessage.substring(Utils.CLIENT_CONNECT_RESPONSE.length() + Utils.SUCCESS.length());
                BaseClientManager.getBaseClient(nodeId).setServerNodeId(serverNodeId);
                LogCore.BaseClient.info("connected to server success, cur NodeId = { {} }, serverNodeId = { {} } remoteAddress = { {} }", nodeId, serverNodeId, ctx.channel().remoteAddress());
                onConnectSuccess();
                BaseClientManager.getBaseClient(nodeId).getConnectFinish().set(true);
            } else {
                LogCore.BaseClient.error("connected to server failed, cur NodeId = { {} }", nodeId);
            }
            isAuthMessage = false;
        } else {
            BaseClientManager.recvFromServer(nodeId, data);
        }
    }

    public void onConnectSuccess() {
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        BaseClientManager.getBaseClient(nodeId).setServerChannel(null);
        LogCore.BaseClient.error("server disconnected, cur NodeId = { {} }, remoteAddress = {}", nodeId, ctx.channel().remoteAddress());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LogCore.BaseClient.error("Exception caught: cur nodeId = { {} },  {}", nodeId, cause.getMessage());
    }
}