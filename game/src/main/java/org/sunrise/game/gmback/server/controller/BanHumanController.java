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
 * 封禁玩家控制器
 */
public class BanHumanController extends BaseController {

    @Data
    public static class BanRecord {
        private String humanId;       // 玩家ID
        private Long banTime;         // 封禁时间
        private Long banExpireTime;   // 封禁到期时间戳(毫秒)，0表示永久封禁
        private String reason;        // 封禁原因

        public BanRecord() {
        }

        public BanRecord(String humanId, String reason, Long banExpireTime) {
            this.humanId = humanId;
            this.reason = reason;
            this.banExpireTime = banExpireTime;
            this.banTime = System.currentTimeMillis();
        }
    }

    private final ConcurrentHashMap<String, BanRecord> banRecords = new ConcurrentHashMap<>();

    @Override
    public void load() {
        getDbData("banRecords", new TypeReference<List<BanRecord>>() {
        }, value -> {
            if (value != null) {
                banRecords.clear();
                for (BanRecord record : value) {
                    banRecords.put(record.getHumanId(), record);
                }
            }
        });
    }

    @Override
    public void save() {
        List<BanRecord> recordsCopy = new ArrayList<>(getBanRecords().values());
        putDbData("banRecords", recordsCopy);
    }

    /**
     * 封禁玩家
     */
    public void ban(Context ctx) {
        String humanId = getBodyParam(ctx, "humanId", String.class);
        String reason = getBodyParam(ctx, "reason", String.class);
        Long banExpireTime = getBodyParam(ctx, "banExpireTime", Long.class);

        if (humanId == null || humanId.trim().isEmpty()) {
            fail(ctx, 400, "Missing humanId");
            return;
        }

        humanId = humanId.trim();

        if (banRecords.containsKey(humanId)) {
            fail(ctx, 400, "Player is already banned");
            return;
        }

        BanRecord record = new BanRecord(humanId, reason != null ? reason : "", banExpireTime != null ? banExpireTime : 0L);
        banRecords.put(humanId, record);

        broadcastBanListToGame();
        success(ctx, null, "Player banned successfully");

        ControllerManager.getController(OperationLogController.class).recordLog(
                ctx, OperationLogController.OperationType.BAN_PLAYER, "封禁玩家(ID:" + humanId + ")");
    }

    /**
     * 解封玩家
     */
    public void unban(Context ctx) {
        String humanId = getBodyParam(ctx, "humanId", String.class);

        if (humanId == null || humanId.trim().isEmpty()) {
            fail(ctx, 400, "Missing humanId");
            return;
        }

        humanId = humanId.trim();

        if (!banRecords.containsKey(humanId)) {
            fail(ctx, 404, "Player is not banned");
            return;
        }

        banRecords.remove(humanId);

        broadcastBanListToGame();
        success(ctx, null, "Player unbanned successfully");

        ControllerManager.getController(OperationLogController.class).recordLog(
                ctx, OperationLogController.OperationType.UNBAN_PLAYER, "解封玩家(ID:" + humanId + ")");
    }

    /**
     * 查询封禁玩家列表
     */
    public void list(Context ctx) {
        try {
            String pageStr = ctx.queryParam("page");
            String sizeStr = ctx.queryParam("size");

            List<BanRecord> allRecords = new ArrayList<>(getBanRecords().values());
            allRecords.sort((a, b) -> Long.compare(b.getBanTime(), a.getBanTime()));

            if (pageStr != null && sizeStr != null) {
                int page = Integer.parseInt(pageStr);
                int size = Integer.parseInt(sizeStr);
                int total = allRecords.size();
                int start = (page - 1) * size;
                int end = Math.min(start + size, total);

                List<BanRecord> pageData = new ArrayList<>();
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
            fail(ctx, 500, "Failed to get ban list: " + e.getMessage());
        }
    }


    public ConcurrentHashMap<String, BanRecord> getBanRecords() {
        Iterator<Map.Entry<String, BanRecord>> iterator = banRecords.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, BanRecord> entry = iterator.next();
            if (entry.getValue().getBanExpireTime() > 0 && System.currentTimeMillis() > entry.getValue().getBanExpireTime()) {
                iterator.remove();
            }
        }
        return banRecords;
    }

    public void broadcastBanListToGame() {
        Map<String, Object> unbanData = new HashMap<>();
        ConcurrentHashMap<String, BanRecord> records = getBanRecords();
        if (records != null) {
            List<String> bans = new ArrayList<>();
            for (BanRecord banRecord : records.values()) {
                bans.add(banRecord.humanId);
            }
            unbanData.put("humanIds", JSON.toJSONString(bans));
        }
        sendMessageToAllGame("banHumanList", unbanData);
    }
}
