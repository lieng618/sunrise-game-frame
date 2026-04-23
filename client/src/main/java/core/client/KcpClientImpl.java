package core.client;

import core.message.MessageHandler;
import io.netty.buffer.ByteBuf;
import kcp.ChannelConfig;
import kcp.KcpClient;
import kcp.KcpListener;
import kcp.Ukcp;
import lombok.Getter;
import lombok.Setter;
import org.sunrise.game.core.message.MessageType;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.utils.Utils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

@Getter
@Setter
public class KcpClientImpl extends SocketClient {

    private int conv;
    private KcpClient kcpClient;

    public void connect(String host, int port) {
        ChannelConfig channelConfig = new ChannelConfig();
        channelConfig.nodelay(true, 10, 2, true);
        channelConfig.setSndwnd(256);
        channelConfig.setRcvwnd(256);
        channelConfig.setMtu(1400);
        channelConfig.setTimeoutMillis(30000);
        channelConfig.setUseConvChannel(true);
        channelConfig.setCrc32Check(true);
        channelConfig.setConv(conv);

        kcpClient = new KcpClient();
        kcpClient.init(channelConfig);

        KcpListener listener = new KcpListener() {
            @Override
            public void onConnected(Ukcp ukcp) {
                LogCore.Client.info("Kcp connected to {}:{}, conv={}", host, port, conv);
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
                    LogCore.Client.warn("kcp recv dataLength error: {}", dataLength);
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

                MessageHandler.handler(uid, data);
            }

            @Override
            public void handleException(Throwable ex, Ukcp ukcp) {
                LogCore.Client.error("Kcp exception", ex);
            }

            @Override
            public void handleClose(Ukcp ukcp) {
                LogCore.Client.info("Kcp connection closed, conv={}", conv);
                connectSuccess = false;
            }
        };

        try {
            InetSocketAddress serverAddress = new InetSocketAddress(host, port);
            Ukcp ukcp = kcpClient.connect(serverAddress, channelConfig, listener);
            this.ukcp = ukcp;
            connectSuccess = true;

            String connectMessage = Utils.CLIENT_CONNECT;
            ByteBuf buf = ukcpUserBuf(MessageType.biz, connectMessage.getBytes(StandardCharsets.UTF_8));
            ukcp.write(buf);
            buf.release();

            LogCore.Client.info("Kcp connecting to {}:{}, conv={}", host, port, conv);
        } catch (Exception e) {
            LogCore.Client.error("Kcp connect failed: {}:{}, conv={}", host, port, conv, e);
            connectSuccess = false;
        }
    }

    private ByteBuf ukcpUserBuf(int messageType, byte[] data) {
        ByteBuf buf = io.netty.buffer.Unpooled.buffer(4 + 4 + data.length);
        buf.writeInt(messageType);
        buf.writeInt(data.length);
        buf.writeBytes(data);
        return buf;
    }
}
