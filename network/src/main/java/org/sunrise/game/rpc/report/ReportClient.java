package org.sunrise.game.rpc.report;

import lombok.Getter;
import lombok.Setter;
import org.sunrise.game.core.client.BaseClient;
import org.sunrise.game.core.client.BaseClientManager;
import org.sunrise.game.utils.Utils;

@Getter
@Setter
public class ReportClient {
    private final BaseClient connectToCenter;
    private String clientIp; //当前节点上报ip
    private int clientPort; //当前节点上报port
    private int serverId; //服务id
    private String nodeType; //节点类型

    private String masterIp;
    private int masterPort;

    public ReportClient(String nodeId) {
        this.connectToCenter = new BaseClient(nodeId) {
            @Override
            public void onFail() {
                super.onFail();
                Utils.sleep(5000);
                connectMaster(masterIp, masterPort);
            }
        };
        ReportClientMessageManager messageManager = new ReportClientMessageManager(connectToCenter.getNodeId());
        this.connectToCenter.setClientHandler(r -> new ReportClientHandler(nodeId));
        this.connectToCenter.setMessageManager(messageManager);
        BaseClientManager.register(connectToCenter);
    }

    public ReportClient(String nodeId, int serverId, String clientIp, int clientPort, String nodeType) {
        this.clientIp = clientIp;
        this.clientPort = clientPort;
        this.serverId = serverId;
        this.nodeType = nodeType;
        this.connectToCenter = new BaseClient(nodeId) {
            @Override
            public void onFail() {
                super.onFail();
                Utils.sleep(5000);
                connectMaster(masterIp, masterPort);
            }
        };
        ReportClientMessageManager messageManager = new ReportClientMessageManager(connectToCenter.getNodeId());
        this.connectToCenter.setClientHandler(r -> new ReportClientHandler(nodeId));
        this.connectToCenter.setMessageManager(messageManager);
        BaseClientManager.register(connectToCenter);
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
