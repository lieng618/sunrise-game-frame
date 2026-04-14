package org.sunrise.game.gmback.server.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import io.javalin.http.Context;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 禁言玩家控制器
 */
public class MuteHumanController extends BaseController {

    @Data
    public static class MuteRecord {
        private String humanId;       // 玩家ID
        private Long muteTime;        // 禁言时间
        private Long muteExpireTime;  // 禁言到期时间戳(毫秒)，0表示永久禁言
        private String reason;        // 禁言原因

        public MuteRecord() {
        }

        public MuteRecord(String humanId, String reason, Long muteExpireTime) {
            this.humanId = humanId;
            this.reason = reason;
            this.muteExpireTime = muteExpireTime;
            this.muteTime = System.currentTimeMillis();
        }
    }

    private final ConcurrentHashMap<String, MuteRecord> muteRecords = new ConcurrentHashMap<>();

    @Override
    public void load() {
        getDbData("muteRecords", new TypeReference<List<MuteRecord>>() {
        }, value -> {
            if (value != null) {
                muteRecords.clear();
                for (MuteRecord record : value) {
                    muteRecords.put(record.getHumanId(), record);
                }
            }
        });
    }

    @Override
    public void save() {
        List<MuteRecord> recordsCopy = new ArrayList<>(getMuteRecords().values());
        putDbData("muteRecords", recordsCopy);
    }

    /**
     * 禁言玩家
     */
    public void mute(Context ctx) {
        String humanId = getBodyParam(ctx, "humanId", String.class);
        String reason = getBodyParam(ctx, "reason", String.class);
        Long muteExpireTime = getBodyParam(ctx, "muteExpireTime", Long.class);

        if (humanId == null || humanId.trim().isEmpty()) {
            fail(ctx, 400, "Missing humanId");
            return;
        }

        humanId = humanId.trim();

        if (muteRecords.containsKey(humanId)) {
            fail(ctx, 400, "Player is already muted");
            return;
        }

        MuteRecord record = new MuteRecord(humanId, reason != null ? reason : "", muteExpireTime != null ? muteExpireTime : 0L);
        muteRecords.put(humanId, record);

        broadcastMuteListToGame();
        success(ctx, null, "Player muted successfully");

        ControllerManager.getController(OperationLogController.class).recordLog(
                ctx, OperationLogController.OperationType.MUTE_PLAYER, "禁言玩家(ID:" + humanId + ")");
    }

    /**
     * 解除禁言
     */
    public void unmute(Context ctx) {
        String humanId = getBodyParam(ctx, "humanId", String.class);

        if (humanId == null || humanId.trim().isEmpty()) {
            fail(ctx, 400, "Missing humanId");
            return;
        }

        humanId = humanId.trim();

        if (!muteRecords.containsKey(humanId)) {
            fail(ctx, 404, "Player is not muted");
            return;
        }

        muteRecords.remove(humanId);

        broadcastMuteListToGame();
        success(ctx, null, "Player unmuted successfully");

        ControllerManager.getController(OperationLogController.class).recordLog(
                ctx, OperationLogController.OperationType.UNMUTE_PLAYER, "解除禁言(ID:" + humanId + ")");
    }

    /**
     * 查询禁言玩家列表
     */
    public void list(Context ctx) {
        try {
            String pageStr = ctx.queryParam("page");
            String sizeStr = ctx.queryParam("size");

            List<MuteRecord> allRecords = new ArrayList<>(getMuteRecords().values());
            allRecords.sort((a, b) -> Long.compare(b.getMuteTime(), a.getMuteTime()));

            if (pageStr != null && sizeStr != null) {
                int page = Integer.parseInt(pageStr);
                int size = Integer.parseInt(sizeStr);
                int total = allRecords.size();
                int start = (page - 1) * size;
                int end = Math.min(start + size, total);

                List<MuteRecord> pageData = new ArrayList<>();
                if (start < total) {
                    pageData = allRecords.subList(start, end);
                }

                Map<String, Object> data = new HashMap<>();
                data.put("list", pageData);
                data.put("total", total);
                success(ctx, data);
            } else {
                success(ctx, allRecords);
            }
        } catch (NumberFormatException e) {
            fail(ctx, 400, "Invalid page or size parameter");
        } catch (Exception e) {
            fail(ctx, 500, "Failed to get mute list: " + e.getMessage());
        }
    }


    public ConcurrentHashMap<String, MuteRecord> getMuteRecords() {
        Iterator<Map.Entry<String, MuteRecord>> iterator = muteRecords.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, MuteRecord> entry = iterator.next();
            if (entry.getValue().getMuteExpireTime() > 0 && System.currentTimeMillis() > entry.getValue().getMuteExpireTime()) {
                iterator.remove();
            }
        }
        return muteRecords;
    }

    public void broadcastMuteListToGame() {
        Map<String, Object> muteData = new HashMap<>();
        ConcurrentHashMap<String, MuteRecord> records = getMuteRecords();
        if (records != null) {
            List<String> mutes = new ArrayList<>();
            for (MuteRecord muteRecord : records.values()) {
                mutes.add(muteRecord.humanId);
            }
            muteData.put("humanIds", JSON.toJSONString(mutes));
        }
        sendMessageToAllGame("muteHumanList", muteData);
    }
}