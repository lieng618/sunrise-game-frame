package org.sunrise.game.gmback.server.controller;

import io.javalin.http.Context;
import lombok.Data;
import org.sunrise.game.rpc.center.NodeData;
import org.sunrise.game.rpc.center.NodeManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NodeController extends BaseController {
    @Data
    private class GameRemoteData {
        private int online;
        private String ip;
        private int port;
        private int serverId;
        private String nodeId;
    }
    private ConcurrentHashMap<String, GameRemoteData> games = new ConcurrentHashMap<>();
    public void updateGameData(String nodeId, int serverId, String ip, int port, int online) {
        GameRemoteData gameRemoteData = games.computeIfAbsent(nodeId, k -> new GameRemoteData());
        gameRemoteData.nodeId = nodeId;
        gameRemoteData.serverId = serverId;
        gameRemoteData.ip = ip;
        gameRemoteData.port = port;
        gameRemoteData.online = online;
    }

    public void list(Context ctx) {
        // 构建返回消息
        List<GameRemoteData> nodes = new ArrayList<>(games.values());

        List<Map<String, Object>> voList = new ArrayList<>();
        for (GameRemoteData node : nodes) {
            Map<String, Object> map = new HashMap<>();
            map.put("nodeId", node.getNodeId());
            map.put("ip", node.getIp());
            map.put("port", node.getPort());
            map.put("serverId", node.getServerId());
            map.put("status", 1);
            map.put("online", node.getOnline());
            voList.add(map);
        }
        success(ctx, voList);
    }

    public void toggle(Context ctx) {
        String nodeId = getBodyParam(ctx, "nodeId", String.class);
        Boolean stop = getBodyParam(ctx, "stop", Boolean.class);

//        if (nodeId != null && stop != null) {
//            NodeManager.updateNodeStatus(nodeId, stop);
//            success(ctx);
//
//            ControllerManager.getController(OperationLogController.class).recordLog(ctx, OperationLogController.OperationType.TOGGLE_NODE, (stop ? "停止节点" : "启动节点") + "(节点ID:" + nodeId + ")");
//        } else {
//            fail(ctx, 400, "Missing params");
//        }
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