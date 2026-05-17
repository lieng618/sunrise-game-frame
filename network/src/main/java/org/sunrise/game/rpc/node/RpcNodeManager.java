package org.sunrise.game.rpc.node;

import lombok.Getter;
import org.sunrise.game.core.client.BaseClient;
import org.sunrise.game.log.LogCore;

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
            LogCore.RpcUtils.warn("RpcNode getServerIdByClientNodeId fail, use NodeId = { {} }, cur have Others ServerId = {{}}", curNodeId, rpcNode.getConnectToOthers().keySet());
        } else {
            LogCore.RpcUtils.warn("RpcNode getServerIdByClientNodeId fail, rpcNode is null, use NodeId = { {} }", curNodeId);
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
            LogCore.RpcUtils.warn("RpcNode getServerNodeIdByClientNodeId fail, use NodeId = { {} }, cur have Others ServerId = {{}}", curNodeId, rpcNode.getConnectToOthers().keySet());
        } else {
            LogCore.RpcUtils.warn("RpcNode getServerNodeIdByClientNodeId fail, rpcNode is null, use NodeId = { {} }", curNodeId);
        }
        return "";
    }

    /**
     * 判断服务器节点是否有效
     */
    public static boolean isServerNodeActive(String serverNodeId) {
        if (serverNodeId == null || serverNodeId.isEmpty()) {
            return false;
        }
        if (rpcNode != null) {
            if (rpcNode.getNodeId().equals(serverNodeId)) {
                return true;
            }
            for (Map.Entry<Integer, BaseClient> entry : rpcNode.getConnectToOthers().entrySet()) {
                if (Objects.equals(entry.getValue().getServerNodeId(), serverNodeId)) {
                    return true;
                }
            }
        }
        return false;
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
            LogCore.RpcUtils.warn("RpcNode getClientNodeIdByServerNodeId fail, use NodeId = { {} }, cur have Others ServerId = {{}}", serverNodeId, rpcNode.getConnectToOthers().keySet());
        } else {
            LogCore.RpcUtils.warn("RpcNode getClientNodeIdByServerNodeId fail, rpcNode is null, use NodeId = { {} }", serverNodeId);
        }
        return "";
    }

    /**
     * 获取rpc节点服务id
     */
    public static int getRpcServerId() {
        if (rpcNode != null) {
            return rpcNode.getServerId();
        } else {
            LogCore.RpcUtils.warn("RpcNode getRpcServerId fail, rpcNode is null");
        }
        return 0;
    }

    /**
     * 获取rpc节点服务节点id
     */
    public static String getRpcServerNodeId() {
        if (rpcNode != null) {
            return rpcNode.getRpcServer().getNodeId();
        } else {
            LogCore.RpcUtils.warn("RpcNode getRpcServerNodeId fail, rpcNode is null");
        }
        return "";
    }
}
