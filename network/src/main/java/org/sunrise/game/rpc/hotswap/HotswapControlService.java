package org.sunrise.game.rpc.hotswap;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.sunrise.game.config.ConfigReader;
import org.sunrise.game.core.HotswapScanner;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.rpc.annotation.RpcMethod;
import org.sunrise.game.rpc.annotation.RpcService;
import org.sunrise.game.rpc.function.RpcFunction;
import org.sunrise.game.rpc.node.RpcNodeManager;
import org.sunrise.game.rpc.service.BaseService;
import org.sunrise.game.utils.Utils;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 所有 RPC 服务进程共用的热更控制入口，避免热更命令只注册在 GameServer。
 */
@RpcService
public class HotswapControlService extends BaseService {
    private static final String[] SELF_REPORTED_TYPES = {"global", "http", "gmback"};
    private static final String CALL_ENUM_CLASS = "org.sunrise.game.genRpc.gen.CallEnum";
    private static final int UNAVAILABLE_CALL_ID = -1;

    public HotswapControlService(String nodeId) {
        super(nodeId);
    }

    @RpcMethod
    public void command(String requestJson) {
        JSONObject request = requestJson == null || requestJson.isBlank()
                ? new JSONObject()
                : JSON.parseObject(requestJson);
        String operation = request.getString("operation");
        if (operation == null || operation.isBlank()) {
            operation = "status";
        }

        HotswapScanner.Report report;
        try {
            HotswapScanner scanner = HotswapScanner.getInstance();
            if (scanner == null) {
                throw new IllegalStateException("热更基线尚未初始化");
            }
            switch (operation) {
                case "reload" -> report = scanner.reload(request.getString("jarPath"));
                case "rollback" -> report = scanner.rollback();
                case "status" -> report = scanner.status(null);
                default -> throw new IllegalArgumentException("未知热更操作: " + operation);
            }
        } catch (Exception e) {
            LogCore.RpcUtils.warn("Hotswap operation failed on node {}: {}",
                    RpcNodeManager.getRpcServerNodeId(), e.getMessage(), e);
            Map<String, Object> error = nodeResult();
            error.put("status", "FAILED");
            error.put("message", e.getClass().getSimpleName() + ": " + e.getMessage());
            returns("report", JSON.toJSONString(error));
            return;
        }

        Map<String, Object> result = nodeResult();
        result.put("status", report.getStatus());
        result.put("message", report.getMessage());
        result.put("initialized", report.isInitialized());
        result.put("agentLoaded", report.isAgentLoaded());
        result.put("targetPath", report.getTargetPath());
        result.put("targetVersion", report.getTargetVersion());
        result.put("activeVersion", report.getActiveVersion());
        result.put("previousTargetPath", report.getPreviousTargetPath());
        result.put("targetClassCount", report.getTargetClassCount());
        result.put("unchangedClassCount", report.getUnchangedClassCount());
        result.put("baselineJars", report.getBaselineJars());
        result.put("requestedClasses", report.getRequestedClasses());
        result.put("redefinedClasses", report.getRedefinedClasses());
        result.put("skippedNotLoadedClasses", report.getSkippedNotLoadedClasses());
        result.put("skippedUnsupportedClasses", report.getSkippedUnsupportedClasses());
        result.put("failedClasses", report.getFailedClasses());
        returns("report", JSON.toJSONString(result));
    }

    @Override
    public void pulsePer5Sec() {
        super.pulsePer5Sec();
        if (!shouldSelfReport()) {
            return;
        }
        Map<String, Object> dataMap = nodeResult();
        dataMap.put("online", 0);
        dataMap.put("port", RpcNodeManager.getRpcNode().getPort());
        dataMap.put("processId", Utils.getProcessId());
        dataMap.put("type", reportType());
        int reportCallId = resolveCallId("GmBackRecvMessageService_recvMessage");
        if (reportCallId == UNAVAILABLE_CALL_ID) {
            return;
        }
        RpcFunction.newInstance().call(reportCallId,
                "reportNodeData", JSON.toJSONString(dataMap));
    }

    private Map<String, Object> nodeResult() {
        Map<String, Object> result = new HashMap<>();
        result.put("nodeId", RpcNodeManager.getRpcServerNodeId());
        String nodeType = ConfigReader.getString("rpc.node.type", "unknown");
        result.put("nodeType", reportType());
        result.put("rawNodeType", nodeType);
        return result;
    }

    private boolean shouldSelfReport() {
        String nodeType = ConfigReader.getString("rpc.node.type", "");
        return SELF_REPORTED_TYPES[0].equalsIgnoreCase(nodeType)
                || SELF_REPORTED_TYPES[1].equalsIgnoreCase(nodeType)
                || SELF_REPORTED_TYPES[2].equalsIgnoreCase(nodeType);
    }

    private String reportType() {
        String nodeType = ConfigReader.getString("rpc.node.type", "unknown");
        return switch (nodeType.toLowerCase(Locale.ROOT)) {
            case "game", "runallone" -> "GameServer";
            case "external" -> "ExternalServer";
            case "global" -> "GlobalServer";
            case "http" -> "HttpServer";
            case "gmback" -> "GmBackServer";
            case "center" -> "CenterServer";
            default -> nodeType;
        };
    }

    private static int resolveCallId(String fieldName) {
        try {
            Class<?> callEnum = Class.forName(CALL_ENUM_CLASS);
            return callEnum.getField(fieldName).getInt(null);
        } catch (ReflectiveOperationException e) {
            LogCore.RpcUtils.warn("Hotswap control call id unavailable: {}", fieldName);
            return UNAVAILABLE_CALL_ID;
        }
    }
}
