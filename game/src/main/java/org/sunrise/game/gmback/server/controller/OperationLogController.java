package org.sunrise.game.gmback.server.controller;

import com.alibaba.fastjson2.TypeReference;
import io.javalin.http.Context;
import lombok.Data;
import org.sunrise.game.jwt.JwtUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

/**
 * 操作日志控制器
 */
public class OperationLogController extends BaseController {
    /**
     * 操作类型枚举
     */
    public enum OperationType {
        LOGIN("登录"),
        SEND_MAIL("发送邮件"),
        KICK_PLAYER("踢出玩家"),
        RELOAD_CONFIG("热更配置"),
        TOGGLE_NODE("切换节点状态"),
        USER_MANAGER("用户管理"),
        BAN_PLAYER("封禁玩家"),
        UNBAN_PLAYER("解封玩家"),
        MUTE_PLAYER("禁言玩家"),
        UNMUTE_PLAYER("解除禁言"),
        OTHER("其他操作");

        OperationType(String description) {
        }
    }

    @Data
    public class OperationLog {
        private String operator;      // 操作人员
        private String ip;            // IP地址
        private OperationType operationType;  // 操作类型
        private String action;        // 操作行为描述
        private Long createTime;      // 创建时间

        public OperationLog() {
        }

        public OperationLog(String operator, String ip, OperationType operationType, String action) {
            this.operator = operator;
            this.ip = ip;
            this.operationType = operationType;
            this.action = action;
            this.createTime = System.currentTimeMillis();
        }
    }

    // 日志列表
    private final ConcurrentLinkedDeque<OperationLog> logs = new ConcurrentLinkedDeque<>();

    @Override
    public void load() {
        getDbData("logs", new TypeReference<List<OperationLog>>() {
        }, value -> {
            if (value != null) {
                logs.clear();
                logs.addAll(value);
            }
        });
    }

    @Override
    public void save() {
        List<OperationLog> logsCopy = new ArrayList<>(logs);
        putDbData("logs", logsCopy);
    }

    public void recordLoginLog(Context ctx, String user, OperationType operationType, String action) {
        ControllerManager.addAsyncEvent(() -> logs.addFirst(new OperationLog(user, ctx.ip(), operationType, action)));
    }

    /**
     * 记录操作日志
     */
    public void recordLog(Context ctx, OperationType operationType, String action) {
        String token = ctx.header("Authorization");
        if (token != null && !token.isEmpty()) {
            ControllerManager.addAsyncEvent(() -> logs.addFirst(new OperationLog(JwtUtil.verifyToken(token), ctx.ip(), operationType, action)));
        }
    }

    /**
     * 分页查询日志
     */
    public void list(Context ctx) {
        try {
            // 获取分页参数
            int page = Integer.parseInt(Objects.requireNonNull(ctx.queryParam("page")));
            int size = Integer.parseInt(Objects.requireNonNull(ctx.queryParam("size")));
            
            List<OperationLog> logsSnapshot = new ArrayList<>(logs);
            
            List<OperationLog> filteredLogs = logsSnapshot;
            String operationTypeStr = ctx.queryParam("operationType");
            if (operationTypeStr != null) {
                OperationType operationType = OperationType.valueOf(operationTypeStr);
                filteredLogs = logsSnapshot.stream()
                        .filter(log -> log.getOperationType() == operationType)
                        .sorted((a, b) -> Long.compare(b.getCreateTime(), a.getCreateTime()))
                        .collect(Collectors.toList());
            }

            int total = filteredLogs.size();
            int start = (page - 1) * size;
            int end = Math.min(start + size, total);

            List<OperationLog> pageData = new ArrayList<>();
            if (start < total) {
                pageData = filteredLogs.subList(start, end);
            }
            
            // 构建返回数据
            Map<String, Object> data = new HashMap<>();
            data.put("list", pageData);
            data.put("total", total);
            
            success(ctx, data);
        } catch (NumberFormatException e) {
            fail(ctx, 400, "Invalid page or size parameter");
        } catch (Exception e) {
            fail(ctx, 500, "Failed to get logs: " + e.getMessage());
        }
    }
}
