package org.sunrise.game.gmback.server.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import io.javalin.http.Context;
import lombok.Data;
import org.sunrise.game.genRpc.gen.CallEnum;
import org.sunrise.game.rpc.function.RpcFunction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WhitelistController extends BaseController {

    @Data
    public static class WhitelistRecord {
        private String uid;
        private String remark;
        private Long addTime;

        public WhitelistRecord() {
        }

        public WhitelistRecord(String uid, String remark) {
            this.uid = uid;
            this.remark = remark != null ? remark : "";
            this.addTime = System.currentTimeMillis();
        }
    }

    private final ConcurrentHashMap<String, WhitelistRecord> whitelistRecords = new ConcurrentHashMap<>();

    @Override
    public void load() {
        getDbData("whitelistRecords", new TypeReference<List<WhitelistRecord>>() {
        }, value -> {
            if (value != null) {
                whitelistRecords.clear();
                for (WhitelistRecord record : value) {
                    whitelistRecords.put(record.getUid(), record);
                }
            }
        });
    }

    @Override
    public void save() {
        List<WhitelistRecord> recordsCopy = new ArrayList<>(getWhitelistRecords().values());
        putDbData("whitelistRecords", recordsCopy);
    }

    public void add(Context ctx) {
        String uid = getBodyParam(ctx, "uid", String.class);
        String remark = getBodyParam(ctx, "remark", String.class);

        if (uid == null || uid.trim().isEmpty()) {
            fail(ctx, 400, "Missing uid");
            return;
        }

        uid = uid.trim();

        if (whitelistRecords.containsKey(uid)) {
            fail(ctx, 400, "UID is already in whitelist");
            return;
        }

        WhitelistRecord record = new WhitelistRecord(uid, remark);
        whitelistRecords.put(uid, record);

        syncWhitelistToHttp();

        success(ctx, null, "Added to whitelist successfully");

        ControllerManager.getController(OperationLogController.class).recordLog(
                ctx, OperationLogController.OperationType.WHITELIST, "添加白名单(UID:" + uid + ")");
    }

    public void remove(Context ctx) {
        String uid = getBodyParam(ctx, "uid", String.class);

        if (uid == null || uid.trim().isEmpty()) {
            fail(ctx, 400, "Missing uid");
            return;
        }

        uid = uid.trim();

        if (!whitelistRecords.containsKey(uid)) {
            fail(ctx, 404, "UID is not in whitelist");
            return;
        }

        whitelistRecords.remove(uid);

        syncWhitelistToHttp();

        success(ctx, null, "Removed from whitelist successfully");

        ControllerManager.getController(OperationLogController.class).recordLog(
                ctx, OperationLogController.OperationType.WHITELIST, "移除白名单(UID:" + uid + ")");
    }

    public void list(Context ctx) {
        try {
            String pageStr = ctx.queryParam("page");
            String sizeStr = ctx.queryParam("size");

            List<WhitelistRecord> allRecords = new ArrayList<>(getWhitelistRecords().values());
            allRecords.sort((a, b) -> Long.compare(b.getAddTime(), a.getAddTime()));

            if (pageStr != null && sizeStr != null) {
                int page = Integer.parseInt(pageStr);
                int size = Integer.parseInt(sizeStr);
                int total = allRecords.size();
                int start = (page - 1) * size;
                int end = Math.min(start + size, total);

                List<WhitelistRecord> pageData = new ArrayList<>();
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
            fail(ctx, 500, "Failed to get whitelist: " + e.getMessage());
        }
    }

    public ConcurrentHashMap<String, WhitelistRecord> getWhitelistRecords() {
        return whitelistRecords;
    }

    public void syncWhitelistToHttp() {
        List<String> uids = new ArrayList<>(whitelistRecords.keySet());
        RpcFunction.newInstance().call(CallEnum.HttpRecvMessageService_setWhitelist, JSON.toJSONString(uids));
    }
}
