package org.sunrise.game.gmback.server.controller;

import io.javalin.http.Context;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NodeController extends BaseController {
    @Data
    private class NodeRemoteData {
        private int online;
        private String ip;
        private int port;
        private int serverId;
        private String nodeId;
        private String type;
        private long processId;
        private boolean tcpEnabled;
        private boolean wsEnabled;
        private boolean kcpEnabled;
    }

    private final ConcurrentHashMap<String, NodeRemoteData> nodes = new ConcurrentHashMap<>();

    public void updateNodeData(String nodeId, int serverId, String ip, int port, int online, String type, long processId) {
        updateNodeData(nodeId, serverId, ip, port, online, type, processId, false, false, false);
    }

    public void updateNodeData(String nodeId, int serverId, String ip, int port, int online, String type, long processId,
                               boolean tcpEnabled, boolean wsEnabled, boolean kcpEnabled) {
        NodeRemoteData nodeRemoteData = nodes.computeIfAbsent(type + nodeId, k -> new NodeRemoteData());
        nodeRemoteData.nodeId = nodeId;
        nodeRemoteData.serverId = serverId;
        nodeRemoteData.type = type;
        nodeRemoteData.ip = ip;
        nodeRemoteData.port = port;
        nodeRemoteData.online = online;
        nodeRemoteData.processId = processId;
        nodeRemoteData.tcpEnabled = tcpEnabled;
        nodeRemoteData.wsEnabled = wsEnabled;
        nodeRemoteData.kcpEnabled = kcpEnabled;
    }

    public void list(Context ctx) {
        // 构建返回消息
        List<NodeRemoteData> nodes = new ArrayList<>(this.nodes.values());

        List<Map<String, Object>> voList = new ArrayList<>();
        for (NodeRemoteData node : nodes) {
            Map<String, Object> map = new HashMap<>();
            map.put("nodeId", node.getNodeId());
            map.put("ip", node.getIp());
            map.put("type", node.getType());
            map.put("port", node.getPort());
            if ("ExternalServer".equals(node.getType())) {
                map.put("tcpEnabled", node.isTcpEnabled());
                map.put("wsEnabled", node.isWsEnabled());
                map.put("kcpEnabled", node.isKcpEnabled());
            }
            map.put("serverId", node.getServerId());
            map.put("processId", node.getProcessId());
            map.put("status", 1);
            map.put("online", node.getOnline());
            voList.add(map);
        }
        success(ctx, voList);
    }

    public void reloadConfig(Context ctx) {
        String nodeId = getBodyParam(ctx, "nodeId", String.class);
        String logNodeId = nodeId;
        if (nodeId == null || nodeId.isEmpty()) {
            logNodeId = "所有游戏服节点";
            sendMessageToAllGame("reloadConfig", null);
        } else {
            sendMessageToDesignatedGame("reloadConfig", null, nodeId);
        }

        success(ctx, null, "Reload config message sent");

        ControllerManager.getController(OperationLogController.class).recordLog(ctx, OperationLogController.OperationType.RELOAD_CONFIG, "热更配置(节点ID:" + logNodeId + ")");
    }
}