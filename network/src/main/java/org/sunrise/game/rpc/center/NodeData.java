package org.sunrise.game.rpc.center;

import lombok.Data;

@Data
public class NodeData {
    private String nodeId;
    private String ip;
    private int port;
    private volatile long reportTime;
    private int serverId;
    private String nodeType;

    public NodeData(String nodeId) {
        this.nodeId = nodeId;
    }
}
