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
    /** 上报方的进程实例身份，进程重启即变化，中心服据此判断是否需要广播 */
    private String instanceId;

    public NodeData(String nodeId) {
        this.nodeId = nodeId;
    }
}
