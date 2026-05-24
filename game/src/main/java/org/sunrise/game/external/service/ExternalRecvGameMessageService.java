package org.sunrise.game.external.service;

import com.alibaba.fastjson2.JSON;
import org.sunrise.game.config.ConfigReader;
import org.sunrise.game.external.server.ClientConnection;
import org.sunrise.game.external.server.ExternalConnectionManger;
import org.sunrise.game.external.server.ExternalServer;
import org.sunrise.game.genRpc.gen.CallEnum;
import org.sunrise.game.rpc.annotation.RpcMethod;
import org.sunrise.game.rpc.annotation.RpcService;
import org.sunrise.game.rpc.function.RpcFunction;
import org.sunrise.game.rpc.node.RpcNodeManager;
import org.sunrise.game.rpc.service.BaseService;
import org.sunrise.game.utils.Utils;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 对外服从游戏服接收要发给客户端的数据，心跳中发给客户端
 * 每五秒在心跳中向http服务上报自身数据，把地址提供给客户端进行连接
 */
@RpcService
public class ExternalRecvGameMessageService extends BaseService {
    private ExternalServer externalServer;

    public ExternalRecvGameMessageService(String nodeId) {
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
        RpcFunction.newInstance().call(CallEnum.HttpRecvMessageService_updateExternalRemoteData, "serverId", RpcNodeManager.getRpcServerId(), "host", externalServer.getExternalHost(), "port", externalServer.getExternalPort());
    }

    @Override
    public void pulsePerMin() {
        super.pulsePerMin();
        pulseReportExternalData();
    }

    @RpcMethod
    public void recvMessage(long connectionId, byte[] data, String gameNodeId) {
        if (connectionId > 0) {
            var connection = ExternalConnectionManger.getClientConnect(connectionId);
            if (connection != null) {
                if (gameNodeId != null && !gameNodeId.isEmpty()) {
                    connection.setGameNodeId(gameNodeId);
                }
                connection.sendMessage(data);
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
                RpcFunction.newInstance(connection.getGameNodeId()).call(CallEnum.GameRecvExternalMessageService_recvMessage, "id", connection.getId(), "data", data, "nodeId", connection.isFirstSend() ? "" : RpcNodeManager.getRpcServerNodeId());
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
            if (!RpcNodeManager.isServerNodeActive(connection.getGameNodeId())) {
                if (connection.isActive()) {
                    connection.close();
                }
            }
        }
    }

    /**
     * 定时上报external自身数据
     */
    private void pulseReportExternalData() {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("nodeId", RpcNodeManager.getRpcServerNodeId());
        dataMap.put("serverId", RpcNodeManager.getRpcServerId());
        dataMap.put("ip", Utils.getLocalIpAddress());
        dataMap.put("port", externalServer.getExternalPort());
        dataMap.put("online", ExternalConnectionManger.getOnlineCount());
        dataMap.put("processId", Utils.getProcessId());
        dataMap.put("type", "ExternalServer");
        RpcFunction.newInstance().call(CallEnum.GmBackRecvMessageService_recvMessage, "operation", "reportNodeData", "data", JSON.toJSONString(dataMap));
    }
}