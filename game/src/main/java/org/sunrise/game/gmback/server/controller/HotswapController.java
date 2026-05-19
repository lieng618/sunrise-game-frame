package org.sunrise.game.gmback.server.controller;

import io.javalin.http.Context;

import java.util.HashMap;
import java.util.Map;

public class HotswapController extends BaseController {

    public void hotswapJar(Context ctx) {
        String jarPath = getBodyParam(ctx, "jarPath", String.class);
        String nodeId = getBodyParam(ctx, "nodeId", String.class);

        if (jarPath == null || jarPath.isBlank()) {
            fail(ctx, 400, "jarPath is required");
            return;
        }

        Map<String, Object> extraData = new HashMap<>();
        extraData.put("jarPath", jarPath.trim());

        String logNodeId;
        if (nodeId == null || nodeId.isEmpty()) {
            logNodeId = "所有游戏服节点";
            sendMessageToAllGame("hotswapJar", extraData);
        } else {
            logNodeId = nodeId;
            sendMessageToDesignatedGame("hotswapJar", extraData, nodeId);
        }

        success(ctx, null, "Hotswap jar message sent");

        ControllerManager.getController(OperationLogController.class).recordLog(
                ctx, OperationLogController.OperationType.HOTSWAP_JAR,
                "代码热更(JAR:" + jarPath.trim() + ", 节点:" + logNodeId + ")");
    }
}
