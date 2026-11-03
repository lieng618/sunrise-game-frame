package org.sunrise.game.gmback.server.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.javalin.http.Context;
import io.javalin.http.UploadedFile;
import org.sunrise.game.config.ConfigReader;
import org.sunrise.game.genRpc.gen.CallEnum;
import org.sunrise.game.rpc.function.RpcFunction;
import org.sunrise.game.rpc.function.RpcResult;
import org.sunrise.game.rpc.node.RpcNodeManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class HotswapController extends BaseController {
    private static final long HOTSWAP_TIMEOUT_MS = 20_000L;

    public void hotswapJar(Context ctx) {
        String jarPath = getBodyParam(ctx, "jarPath", String.class);
        String nodeId = getBodyParam(ctx, "nodeId", String.class);
        String operation = getBodyParam(ctx, "operation", String.class);
        executeHotswap(ctx, jarPath, nodeId, operation);
    }

    /**
     * 保存 GM 上传的业务全量补丁，然后立即对同一批节点执行热更。
     * 多机部署时 hotswap.upload-dir 必须位于所有节点可访问的共享目录。
     */
    public void uploadJar(Context ctx) {
        var files = ctx.uploadedFiles("file");
        if (files.isEmpty()) {
            fail(ctx, 400, "请选择要上传的 jar 文件");
            return;
        }
        UploadedFile uploadedFile = files.get(0);
        String originalName = Path.of(uploadedFile.filename()).getFileName().toString();
        if (!originalName.toLowerCase().endsWith(".jar")) {
            fail(ctx, 400, "只允许上传 .jar 文件");
            return;
        }

        String uploadDirValue = ConfigReader.getString(
                "hotswap.upload-dir", "../../../hotfix/uploaded");
        Path uploadDir = Path.of(uploadDirValue).toAbsolutePath().normalize();
        Path target = uploadDir.resolve(System.currentTimeMillis() + "-" + originalName);
        try {
            Files.createDirectories(uploadDir);
            try (var content = uploadedFile.content()) {
                Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            fail(ctx, 500, "保存热更 JAR 失败: " + e.getMessage());
            return;
        }

        executeHotswap(ctx, target.toString(), ctx.formParam("nodeId"), "reload");
    }

    /**
     * 对所有在线服务节点执行热更，并等待每个节点返回真实重定义结果。
     * 节点类型不再限制为 GameServer，单进程和组合部署走同一条调用链。
     */
    private void executeHotswap(Context ctx, String jarPath, String nodeId, String operation) {
        if (operation == null || operation.isBlank()) {
            operation = "reload";
        }
        if ("reload".equals(operation) && (jarPath == null || jarPath.isBlank())) {
            fail(ctx, 400, "jarPath is required");
            return;
        }

        List<String> nodeIds;
        if (nodeId == null || nodeId.isBlank()) {
            nodeIds = new ArrayList<>(new LinkedHashSet<>(
                    ControllerManager.getController(NodeController.class).getActiveNodeIds()));
            for (String registeredNodeId : RpcFunction.getCallNodes(
                    CallEnum.HotswapControlService_command)) {
                if (!nodeIds.contains(registeredNodeId)) {
                    nodeIds.add(registeredNodeId);
                }
            }
        } else {
            nodeIds = List.of(nodeId.trim());
        }
        if (nodeIds.isEmpty()) {
            fail(ctx, 409, "没有可执行的在线服务节点");
            return;
        }

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("operation", operation);
        if (jarPath != null && !jarPath.isBlank()) {
            request.put("jarPath", jarPath.trim());
        }
        String requestJson = JSON.toJSONString(request);

        Map<String, JSONObject> results = new ConcurrentHashMap<>();
        CountDownLatch completed = new CountDownLatch(nodeIds.size());
        for (String targetNodeId : nodeIds) {
            if (!RpcNodeManager.isServerNodeActive(targetNodeId)) {
                results.put(targetNodeId, errorResult(targetNodeId, "目标节点当前不可达"));
                completed.countDown();
                continue;
            }
            RpcFunction rpc = RpcFunction.newInstance(targetNodeId);
            boolean sent = rpc.call(CallEnum.HotswapControlService_command, requestJson);
            if (!sent) {
                results.put(targetNodeId, errorResult(targetNodeId, "RPC 未注册热更控制服务"));
                completed.countDown();
                continue;
            }
            rpc.listenResult(result -> completeNodeResult(
                    targetNodeId, result, results, completed));
            rpc.setTimeOut(HOTSWAP_TIMEOUT_MS);
        }

        try {
            completed.await(HOTSWAP_TIMEOUT_MS + 2_000L, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail(ctx, 500, "等待热更结果时被中断");
            return;
        }
        for (String targetNodeId : nodeIds) {
            results.computeIfAbsent(targetNodeId,
                    ignored -> errorResult(targetNodeId, "等待节点热更结果超时"));
        }

        List<Map<String, Object>> orderedResults = new ArrayList<>();
        int succeeded = 0;
        int failed = 0;
        for (String targetNodeId : nodeIds) {
            JSONObject result = results.get(targetNodeId);
            orderedResults.add(result);
            String status = result.getString("status");
            if ("FAILED".equals(status)) {
                failed++;
            } else {
                succeeded++;
            }
        }
        if (nodeId == null || nodeId.isBlank()) {
            JSONObject centerResult = reloadCenterIfConfigured(operation, jarPath);
            if (centerResult != null) {
                orderedResults.add(centerResult);
                if ("FAILED".equals(centerResult.getString("status"))) {
                    failed++;
                } else {
                    succeeded++;
                }
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("operation", operation);
        response.put("targetJar", jarPath == null ? null : jarPath.trim());
        response.put("total", orderedResults.size());
        response.put("succeeded", succeeded);
        response.put("failed", failed);
        response.put("results", orderedResults);
        String message = failed == 0
                ? "热更完成，所有节点均返回结果"
                : "热更完成，但有 " + failed + " 个节点失败";
        success(ctx, response, message);

        ControllerManager.getController(OperationLogController.class).recordLog(
                ctx, OperationLogController.OperationType.HOTSWAP_JAR,
                "代码热更(操作:" + operation + ", JAR:" + (jarPath == null ? "-" : jarPath.trim())
                        + ", 节点:" + nodeIds + ")");
    }

    private static void completeNodeResult(
            String nodeId,
            RpcResult rpcResult,
            Map<String, JSONObject> results,
            CountDownLatch completed) {
        try {
            if (rpcResult.getResult() != 0) {
                results.put(nodeId, errorResult(nodeId,
                        "RPC 失败，错误码=" + rpcResult.getResult()));
                return;
            }
            Object report = rpcResult.getData("report");
            if (report == null) {
                results.put(nodeId, errorResult(nodeId, "节点未返回热更报告"));
                return;
            }
            results.put(nodeId, JSON.parseObject(String.valueOf(report)));
        } catch (Exception e) {
            results.put(nodeId, errorResult(nodeId, "解析节点结果失败: " + e.getMessage()));
        } finally {
            completed.countDown();
        }
    }

    private static JSONObject errorResult(String nodeId, String message) {
        JSONObject result = new JSONObject();
        result.put("nodeId", nodeId);
        result.put("status", "FAILED");
        result.put("message", message);
        return result;
    }

    private static JSONObject reloadCenterIfConfigured(String operation, String jarPath) {
        String baseUrl = ConfigReader.getString("hotswap.center-dashboard-url", "");
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        String endpoint = baseUrl.replaceAll("/+$", "")
                + ("status".equals(operation) ? "/api/hotswap/status" : "/api/hotswap/reload");
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofMillis(HOTSWAP_TIMEOUT_MS));
            if ("status".equals(operation)) {
                builder.GET();
            } else {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("operation", operation);
                body.put("jarPath", jarPath);
                builder.header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(body)));
            }
            HttpResponse<String> response = client.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                return errorResult("center", "Center 热更接口失败，HTTP " + response.statusCode());
            }
            JSONObject result = JSON.parseObject(response.body());
            result.putIfAbsent("nodeId", "center");
            result.putIfAbsent("nodeType", "CenterServer");
            return result;
        } catch (Exception e) {
            return errorResult("center", "调用 Center 热更接口失败: " + e.getMessage());
        }
    }
}
