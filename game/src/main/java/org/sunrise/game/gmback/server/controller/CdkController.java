package org.sunrise.game.gmback.server.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import io.javalin.http.Context;
import lombok.Data;
import org.sunrise.game.game.logic.mail.MailData;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class CdkController extends BaseController {

    private static final String CDK_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int RANDOM_CODE_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Data
    public static class CdkRecord {
        private Integer id;
        private String code;
        private Long startTime;
        private Long endTime;
        private Integer totalCount;
        private Integer usedCount;
        private Integer templateId;
        private List<MailData.MailAttachment> attachments;
        private Long createTime;

        public CdkRecord() {
        }

        public CdkRecord(Integer id, String code, Long startTime, Long endTime, Integer totalCount,
                         Integer templateId, List<MailData.MailAttachment> attachments) {
            this.id = id;
            this.code = code;
            this.startTime = startTime;
            this.endTime = endTime;
            this.totalCount = totalCount;
            this.usedCount = 0;
            this.templateId = templateId != null ? templateId : 1;
            this.attachments = attachments != null ? attachments : new ArrayList<>();
            this.createTime = System.currentTimeMillis();
        }
    }

    private final ConcurrentHashMap<Integer, CdkRecord> cdkRecords = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> codeToId = new ConcurrentHashMap<>();
    private final AtomicInteger idGenerator = new AtomicInteger(1);

    @Override
    public void load() {
        getDbData("cdkRecords", new TypeReference<List<CdkRecord>>() {
        }, value -> {
            if (value != null) {
                cdkRecords.clear();
                codeToId.clear();
                int maxId = 0;
                for (CdkRecord record : value) {
                    cdkRecords.put(record.getId(), record);
                    codeToId.put(record.getCode(), record.getId());
                    if (record.getId() > maxId) {
                        maxId = record.getId();
                    }
                }
                idGenerator.set(maxId + 1);
            }
        });
    }

    @Override
    public void save() {
        putDbData("cdkRecords", new ArrayList<>(cdkRecords.values()));
    }

    public void add(Context ctx) {
        String code = getBodyParam(ctx, "code", String.class);
        Boolean randomGenerate = getBodyParam(ctx, "randomGenerate", Boolean.class);
        Long startTime = getBodyParam(ctx, "startTime", Long.class);
        Long endTime = getBodyParam(ctx, "endTime", Long.class);
        Integer totalCount = getBodyParam(ctx, "totalCount", Integer.class);
        Integer templateId = getBodyParam(ctx, "templateId", Integer.class);
        List attachments = getBodyParam(ctx, "attachments", List.class);

        if (startTime == null) {
            fail(ctx, 400, "Missing startTime");
            return;
        }
        if (endTime == null) {
            fail(ctx, 400, "Missing endTime");
            return;
        }
        if (startTime >= endTime) {
            fail(ctx, 400, "StartTime must be before endTime");
            return;
        }
        if (totalCount == null || totalCount <= 0) {
            fail(ctx, 400, "TotalCount must be greater than 0");
            return;
        }

        boolean useRandom = randomGenerate != null && randomGenerate;
        if (useRandom) {
            code = generateUniqueCode();
        } else {
            if (code == null || code.trim().isEmpty()) {
                fail(ctx, 400, "Missing code");
                return;
            }
            code = code.trim();
            if (codeToId.containsKey(code)) {
                fail(ctx, 400, "CDK code already exists");
                return;
            }
        }

        List<MailData.MailAttachment> attachmentList = parseAttachments(attachments);

        int id = idGenerator.getAndIncrement();
        CdkRecord record = new CdkRecord(id, code, startTime, endTime, totalCount, templateId, attachmentList);
        cdkRecords.put(id, record);
        codeToId.put(code, id);

        syncCdkToGame();

        Map<String, Object> data = new HashMap<>();
        data.put("id", id);
        data.put("code", code);
        success(ctx, data, "CDK added successfully");

        ControllerManager.getController(OperationLogController.class).recordLog(
                ctx, OperationLogController.OperationType.CDK, "创建兑换码(ID:" + id + ", 码:" + code + ")");
    }

    public void update(Context ctx) {
        Integer id = getBodyParam(ctx, "id", Integer.class);
        Long startTime = getBodyParam(ctx, "startTime", Long.class);
        Long endTime = getBodyParam(ctx, "endTime", Long.class);
        Integer templateId = getBodyParam(ctx, "templateId", Integer.class);
        List attachments = getBodyParam(ctx, "attachments", List.class);

        if (id == null) {
            fail(ctx, 400, "Missing id");
            return;
        }

        CdkRecord record = cdkRecords.get(id);
        if (record == null) {
            fail(ctx, 404, "CDK not found");
            return;
        }

        if (startTime != null) {
            record.setStartTime(startTime);
        }
        if (endTime != null) {
            record.setEndTime(endTime);
        }
        if (record.getStartTime() >= record.getEndTime()) {
            fail(ctx, 400, "StartTime must be before endTime");
            return;
        }
        if (templateId != null) {
            record.setTemplateId(templateId);
        }
        if (attachments != null) {
            record.setAttachments(parseAttachments(attachments));
        }

        syncCdkToGame();
        success(ctx, null, "CDK updated successfully");

        ControllerManager.getController(OperationLogController.class).recordLog(
                ctx, OperationLogController.OperationType.CDK, "修改兑换码(ID:" + id + ", 码:" + record.getCode() + ")");
    }

    public void adjustCount(Context ctx) {
        Integer id = getBodyParam(ctx, "id", Integer.class);
        Integer delta = getBodyParam(ctx, "delta", Integer.class);

        if (id == null) {
            fail(ctx, 400, "Missing id");
            return;
        }
        if (delta == null || delta == 0) {
            fail(ctx, 400, "Delta must be non-zero");
            return;
        }

        CdkRecord record = cdkRecords.get(id);
        if (record == null) {
            fail(ctx, 404, "CDK not found");
            return;
        }

        int newTotal = record.getTotalCount() + delta;
        if (newTotal < record.getUsedCount()) {
            fail(ctx, 400, "TotalCount cannot be less than usedCount (" + record.getUsedCount() + ")");
            return;
        }
        if (newTotal <= 0) {
            fail(ctx, 400, "TotalCount must be greater than 0");
            return;
        }

        record.setTotalCount(newTotal);
        syncCdkToGame();
        success(ctx, null, "CDK count adjusted successfully");

        ControllerManager.getController(OperationLogController.class).recordLog(
                ctx, OperationLogController.OperationType.CDK,
                "调整兑换码数量(ID:" + id + ", 码:" + record.getCode() + ", delta:" + delta + ", 新总量:" + newTotal + ")");
    }

    public void remove(Context ctx) {
        Integer id = getBodyParam(ctx, "id", Integer.class);

        if (id == null) {
            fail(ctx, 400, "Missing id");
            return;
        }

        CdkRecord record = cdkRecords.remove(id);
        if (record == null) {
            fail(ctx, 404, "CDK not found");
            return;
        }
        codeToId.remove(record.getCode());

        syncCdkToGame();
        success(ctx, null, "CDK removed successfully");

        ControllerManager.getController(OperationLogController.class).recordLog(
                ctx, OperationLogController.OperationType.CDK, "删除兑换码(ID:" + id + ", 码:" + record.getCode() + ")");
    }

    public void list(Context ctx) {
        try {
            String pageStr = ctx.queryParam("page");
            String sizeStr = ctx.queryParam("size");

            List<CdkRecord> allRecords = new ArrayList<>(cdkRecords.values());
            allRecords.sort((a, b) -> Long.compare(b.getCreateTime(), a.getCreateTime()));

            if (pageStr != null && sizeStr != null) {
                int page = Integer.parseInt(pageStr);
                int size = Integer.parseInt(sizeStr);
                int total = allRecords.size();
                int start = (page - 1) * size;
                int end = Math.min(start + size, total);

                List<CdkRecord> pageData = new ArrayList<>();
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
            fail(ctx, 500, "Failed to get CDK list: " + e.getMessage());
        }
    }

    /**
     * 游戏服兑换成功后上报，增加已使用数量
     */
    public void onRedeem(String code) {
        Integer id = codeToId.get(code);
        if (id == null) {
            return;
        }
        CdkRecord record = cdkRecords.get(id);
        if (record == null) {
            return;
        }
        record.setUsedCount(record.getUsedCount() + 1);
    }

    public void syncCdkToGame() {
        List<Map<String, Object>> activeList = getActiveCdksForSync();
        Map<String, Object> data = new HashMap<>();
        data.put("cdkList", JSON.toJSONString(activeList));
        sendMessageToAllGame("cdkList", data);
    }

    private List<Map<String, Object>> getActiveCdksForSync() {
        long now = System.currentTimeMillis();
        List<Map<String, Object>> list = new ArrayList<>();
        for (CdkRecord record : cdkRecords.values()) {
            int remaining = record.getTotalCount() - record.getUsedCount();
            if (remaining <= 0) {
                continue;
            }
            if (record.getStartTime() > now || record.getEndTime() <= now) {
                continue;
            }
            Map<String, Object> item = new HashMap<>();
            item.put("code", record.getCode());
            item.put("startTime", record.getStartTime());
            item.put("endTime", record.getEndTime());
            item.put("remainingCount", remaining);
            item.put("templateId", record.getTemplateId());
            item.put("attachments", record.getAttachments());
            list.add(item);
        }
        return list;
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < 100; attempt++) {
            StringBuilder sb = new StringBuilder(RANDOM_CODE_LENGTH);
            for (int i = 0; i < RANDOM_CODE_LENGTH; i++) {
                sb.append(CDK_CHARS.charAt(RANDOM.nextInt(CDK_CHARS.length())));
            }
            String code = sb.toString();
            if (!codeToId.containsKey(code)) {
                return code;
            }
        }
        throw new RuntimeException("Failed to generate unique CDK code");
    }

    @SuppressWarnings("unchecked")
    private List<MailData.MailAttachment> parseAttachments(List attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return new ArrayList<>();
        }
        String json = JSON.toJSONString(attachments);
        List<MailData.MailAttachment> list = JSON.parseObject(json, new TypeReference<List<MailData.MailAttachment>>() {
        });
        return list != null ? list : new ArrayList<>();
    }
}
