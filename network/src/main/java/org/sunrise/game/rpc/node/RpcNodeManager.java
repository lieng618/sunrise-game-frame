package org.sunrise.game.rpc.node;

import lombok.Getter;
import org.sunrise.game.core.client.BaseClient;

import java.util.Map;
import java.util.Objects;

public class RpcNodeManager {
    @Getter
    private static RpcNode rpcNode;

    public static RpcNode createRpcNode(int serverId) {
        rpcNode = new RpcNode(serverId);
        return rpcNode;
    }

    /**
     * 通过客户端节点id获取远端的服务器id
     */
    public static int getServerIdByClientNodeId(String curNodeId) {
        if (rpcNode != null) {
            for (Map.Entry<Integer, BaseClient> entry : rpcNode.getConnectToOthers().entrySet()) {
                if (Objects.equals(entry.getValue().getNodeId(), curNodeId)) {
                    return entry.getKey();
                }
            }
        }
        return 0;
    }

    /**
     * 通过客户端节点id获取远端的服务器节点id
     */
    public static String getServerNodeIdByClientNodeId(String curNodeId) {
        if (rpcNode != null) {
            for (Map.Entry<Integer, BaseClient> entry : rpcNode.getConnectToOthers().entrySet()) {
                if (Objects.equals(entry.getValue().getNodeId(), curNodeId)) {
                    return entry.getValue().getServerNodeId();
                }
            }
        }
        return "";
    }

    /**
     * 通过服务器节点id获取客户端节点id
     */
    public static String getClientNodeIdByServerNodeId(String serverNodeId) {
        if (rpcNode != null) {
            for (Map.Entry<Integer, BaseClient> entry : rpcNode.getConnectToOthers().entrySet()) {
                if (Objects.equals(entry.getValue().getServerNodeId(), serverNodeId)) {
                    return entry.getValue().getNodeId();
                }
            }
        }
        return "";
    }

    /**
     * 获取rpc节点服务id
     */
    public static int getRpcServerId() {
        if (rpcNode != null) {
            return rpcNode.getServerId();
        }
        return 0;
    }

    /**
     * 获取rpc节点服务节点id
     */
    public static String getRpcServerNodeId() {
        if (rpcNode != null) {
            return rpcNode.getRpcServer().getNodeId();
        }
        return "";
    }
}
