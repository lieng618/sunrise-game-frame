package org.sunrise.game.rpc.center;

import lombok.Data;

@Data
public class NodeData {
    private String nodeId;
    private String ip;
    private int port;
    private long reportTime;
    private int serverId;

    public NodeData(String nodeId) {
        this.nodeId = nodeId;
    }
}
