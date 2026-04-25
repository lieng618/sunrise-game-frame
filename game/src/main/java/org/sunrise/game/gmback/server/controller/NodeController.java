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
    }

    private final ConcurrentHashMap<String, NodeRemoteData> nodes = new ConcurrentHashMap<>();
    public void updateNodeData(String nodeId, int serverId, String ip, int port, int online, String type) {
        NodeRemoteData nodeRemoteData = nodes.computeIfAbsent(type + nodeId, k -> new NodeRemoteData());
        nodeRemoteData.nodeId = nodeId;
        nodeRemoteData.serverId = serverId;
        nodeRemoteData.type = type;
        nodeRemoteData.ip = ip;
        nodeRemoteData.port = port;
        nodeRemoteData.online = online;
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
            map.put("serverId", node.getServerId());
            map.put("status", 1);
            map.put("online", node.getOnline());
            voList.add(map);
        }
        success(ctx, voList);
    }

    public void reloadConfig(Context ctx) {
        String nodeId = getBodyParam(ctx, "nodeId", String.class);
        if (nodeId == null || nodeId.isEmpty()) {
            fail(ctx, 400, "Missing nodeId");
            return;
        }

        sendMessageToAllGame("reloadConfig", null);
        success(ctx, null, "Reload config message sent");

        ControllerManager.getController(OperationLogController.class).recordLog(ctx, OperationLogController.OperationType.RELOAD_CONFIG, "热更配置(节点ID:" + nodeId + ")");
    }
}