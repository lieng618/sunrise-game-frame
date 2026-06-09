package org.sunrise.game.rpc.report;

import lombok.Getter;
import lombok.Setter;
import org.sunrise.game.core.client.BaseClient;
import org.sunrise.game.core.client.BaseClientManager;

import java.util.concurrent.TimeUnit;

@Getter
@Setter
public class ReportClient {
    private static final int RECONNECT_DELAY_SECONDS = 5;

    private final BaseClient connectToCenter;
    private String clientIp; //当前节点上报ip
    private int clientPort; //当前节点上报port
    private int serverId; //服务id
    private String nodeType; //节点类型

    private String masterIp;
    private int masterPort;

    public ReportClient(String nodeId) {
        this.connectToCenter = createConnectClient(nodeId);
        initConnectClient(nodeId);
    }

    public ReportClient(String nodeId, int serverId, String clientIp, int clientPort, String nodeType) {
        this.clientIp = clientIp;
        this.clientPort = clientPort;
        this.serverId = serverId;
        this.nodeType = nodeType;
        this.connectToCenter = createConnectClient(nodeId);
        initConnectClient(nodeId);
    }

    private BaseClient createConnectClient(String nodeId) {
        return new BaseClient(nodeId) {
            @Override
            public void onFail() {
                super.onFail();
                scheduleReconnect();
            }
        };
    }

    private void initConnectClient(String nodeId) {
        ReportClientMessageManager messageManager = new ReportClientMessageManager(connectToCenter.getNodeId());
        connectToCenter.setClientHandler(r -> new ReportClientHandler(nodeId));
        connectToCenter.setMessageManager(messageManager);
        BaseClientManager.register(connectToCenter);
    }

    private void scheduleReconnect() {
        if (connectToCenter.isShutdown() || masterIp == null) {
            return;
        }
        connectToCenter.getGroup().schedule(
                () -> connectMaster(masterIp, masterPort),
                RECONNECT_DELAY_SECONDS,
                TimeUnit.SECONDS);
    }

    public void connectMaster(String ip, int port) {
        masterIp = ip;
        masterPort = port;
        connectToCenter.connect(ip, port);
    }

    public void reConnectMaster() {
        connectToCenter.connect(masterIp, masterPort);
    }
}
