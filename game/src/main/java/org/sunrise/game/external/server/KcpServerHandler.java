package org.sunrise.game.external.server;

import io.netty.buffer.ByteBuf;
import kcp.KcpListener;
import kcp.Ukcp;
import org.sunrise.game.core.message.MessageType;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.utils.Utils;

import java.nio.charset.StandardCharsets;

public class KcpServerHandler implements KcpListener {

    @Override
    public void onConnected(Ukcp ukcp) {
    }

    @Override
    public void handleReceive(ByteBuf buf, Ukcp ukcp) {
        if (buf.readableBytes() < 8) {
            return;
        }

        buf.markReaderIndex();
        int messageType = buf.readInt();
        int dataLength = buf.readInt();

        if (dataLength < 0 || dataLength > Utils.MAX_BODY_SIZE) {
            LogCore.BaseServer.warn("kcp recv dataLength error: {}, will close, conv = {}", dataLength, ukcp.getConv());
            ukcp.close();
            return;
        }

        if (buf.readableBytes() < dataLength) {
            buf.resetReaderIndex();
            return;
        }

        byte[] data = new byte[dataLength];
        buf.readBytes(data);

        if (messageType != MessageType.biz) {
            return;
        }

        ClientConnection clientConnection = getClientConnection(ukcp);
        if (clientConnection == null) {
            verify(ukcp, data);
            return;
        }

        if (!clientConnection.dataCheck(data)) {
            return;
        }
        clientConnection.getMsgQueue().add(data);
    }

    @Override
    public void handleException(Throwable ex, Ukcp ukcp) {
        LogCore.ExternalServer.error("Exception caught, reason = {}, remoteAddress = {}", ex.getMessage(), ukcp.user().getRemoteAddress());
    }

    @Override
    public void handleClose(Ukcp ukcp) {
        ClientConnection clientConnection = getClientConnection(ukcp);
        if (clientConnection != null) {
            ExternalConnectionManger.removeClientConnect(clientConnection.getId());
            LogCore.ExternalServer.info("client disconnected, id = {}, remoteAddress = {}", clientConnection.getId(), clientConnection.getRemoteAddress());
        }
    }

    private ClientConnection getClientConnection(Ukcp ukcp) {
        if (ukcp.user() != null && ukcp.user().getCache() instanceof ClientConnection) {
            return ukcp.user().getCache();
        }
        return null;
    }

    private void verify(Ukcp ukcp, byte[] data) {
        String message = new String(data, StandardCharsets.UTF_8);
        if (message.startsWith(Utils.CLIENT_CONNECT)) {
            ClientConnection clientConnection = ExternalConnectionManger.createClientConnect(ukcp);
            ukcp.user().setCache(clientConnection);
            LogCore.ExternalServer.info("recv connection from client : connectionId = {}, conv = {}, remoteAddress = {}",
                clientConnection.getId(), ukcp.getConv(), clientConnection.getRemoteAddress());
        } else {
            LogCore.ExternalServer.error("recv connection from client : check fail,  close, remoteAddress = {}", ukcp.user().getRemoteAddress());
            ukcp.close();
        }
    }
}
