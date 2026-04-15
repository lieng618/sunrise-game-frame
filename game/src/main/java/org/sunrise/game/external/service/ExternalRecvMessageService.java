package org.sunrise.game.external.service;

import org.sunrise.game.config.ConfigReader;
import org.sunrise.game.core.message.MessageType;
import org.sunrise.game.core.message.SocketMessage;
import org.sunrise.game.external.server.ClientConnection;
import org.sunrise.game.external.server.ExternalConnectionManger;
import org.sunrise.game.external.server.ExternalServer;
import org.sunrise.game.genRpc.gen.CallEnum;
import org.sunrise.game.rpc.annotation.RpcMethod;
import org.sunrise.game.rpc.annotation.RpcService;
import org.sunrise.game.rpc.function.RpcFunction;
import org.sunrise.game.rpc.node.RpcNodeManager;
import org.sunrise.game.rpc.service.BaseService;

import java.util.Properties;

/**
 * 对外服从游戏服接收要发给客户端的数据，心跳中发给客户端
 * 每五秒在心跳中向http服务上报自身数据，把地址提供给客户端进行连接
 */
@RpcService
public class ExternalRecvMessageService extends BaseService {
    private ExternalServer externalServer;

    public ExternalRecvMessageService(String nodeId) {
        super(nodeId);
    }

    @Override
    public void init() {
        super.init();
        externalServer = new ExternalServer();
        externalServer.start();
        Properties properties = ConfigReader.getProp();
        if (properties != null) {
            externalServer.setExternalHost(properties.getProperty("external.address"));
        }
    }

    @Override
    public void pulse() {
        super.pulse();
        pulseHandlerConnectionMsg();
    }

    @Override
    public void pulsePer5Sec() {
        super.pulsePer5Sec();
        pulseRemoveClients();
        RpcFunction.newInstance().call(CallEnum.HttpRecvMessageService_recvMessage, "serverId", RpcNodeManager.getRpcServerId(), "host", externalServer.getExternalHost(), "port", externalServer.getExternalPort());
    }

    @RpcMethod
    public void recvMessage(long connectionId, byte[] data, String gameNodeId) {
        if (connectionId > 0) {
            var connection = ExternalConnectionManger.getClientConnect(connectionId);
            if (connection != null) {
                if (gameNodeId != null && !gameNodeId.isEmpty()) {
                    connection.setGameNodeId(gameNodeId);
                }
                connection.getChannel().writeAndFlush(new SocketMessage(MessageType.biz, data));
            }
        }
    }

    /**
     * 心跳将客户端消息发送给游戏服
     */
    private void pulseHandlerConnectionMsg() {
        for (ClientConnection connection : ExternalConnectionManger.getClientConnections()) {
            while (!connection.getMsgQueue().isEmpty()) {
                byte[] data = connection.getMsgQueue().poll();
                if (data == null) {
                    continue;
                }
                RpcFunction.newInstance(connection.getGameNodeId()).call(CallEnum.GameRecvMessageService_recvMessage, "id", connection.getId(), "data", data, "nodeId", connection.isFirstSend() ? "" : RpcNodeManager.getRpcServerNodeId());
                if (!connection.isFirstSend()) {
                    connection.setFirstSend(true);
                }
            }
        }
    }

    /**
     * 心跳清理失效客户端
     */
    private void pulseRemoveClients() {
        for (ClientConnection connection : ExternalConnectionManger.getClientConnections()) {
            if (connection.getGameNodeId() == null || connection.getGameNodeId().isEmpty()) {
                continue;
            }
            // 此客户断连接的游戏服已经断开连接 需要清理客户端
            if (RpcNodeManager.getClientNodeIdByServerNodeId(connection.getGameNodeId()).isEmpty()) {
                if (connection.getChannel() != null && connection.getChannel().isActive()) {
                    connection.getChannel().close();
                }
            }
        }
    }
}
