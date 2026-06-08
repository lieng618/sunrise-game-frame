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
import java.util.concurrent.atomic.AtomicInteger;

public class AnnouncementController extends BaseController {

    @Data
    public static class AnnouncementRecord {
        private Integer id;
        private String title;
        private String content;
        private Long startTime;
        private Long endTime;
        private Long createTime;

        public AnnouncementRecord() {
        }

        public AnnouncementRecord(Integer id, String title, String content, Long startTime, Long endTime) {
            this.id = id;
            this.title = title;
            this.content = content;
            this.startTime = startTime;
            this.endTime = endTime;
            this.createTime = System.currentTimeMillis();
        }
    }

    private final ConcurrentHashMap<Integer, AnnouncementRecord> announcementRecords = new ConcurrentHashMap<>();
    private final AtomicInteger idGenerator = new AtomicInteger(1);

    @Override
    public void load() {
        getDbData("announcementRecords", new TypeReference<List<AnnouncementRecord>>() {
        }, value -> {
            if (value != null) {
                announcementRecords.clear();
                int maxId = 0;
                for (AnnouncementRecord record : value) {
                    announcementRecords.put(record.getId(), record);
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
        List<AnnouncementRecord> recordsCopy = new ArrayList<>(announcementRecords.values());
        putDbData("announcementRecords", recordsCopy);
    }

    public void add(Context ctx) {
        String title = getBodyParam(ctx, "title", String.class);
        String content = getBodyParam(ctx, "content", String.class);
        Long startTime = getBodyParam(ctx, "startTime", Long.class);
        Long endTime = getBodyParam(ctx, "endTime", Long.class);

        if (title == null || title.trim().isEmpty()) {
            fail(ctx, 400, "Missing title");
            return;
        }

        if (content == null || content.trim().isEmpty()) {
            fail(ctx, 400, "Missing content");
            return;
        }

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

        int id = idGenerator.getAndIncrement();
        AnnouncementRecord record = new AnnouncementRecord(id, title.trim(), content.trim(), startTime, endTime);
        announcementRecords.put(id, record);

        syncAnnouncementsToHttp();

        Map<String, Object> data = new HashMap<>();
        data.put("id", id);
        success(ctx, data, "Announcement added successfully");

        ControllerManager.getController(OperationLogController.class).recordLog(
                ctx, OperationLogController.OperationType.ANNOUNCEMENT, "发布公告(ID:" + id + ", 标题:" + title.trim() + ")");
    }

    public void update(Context ctx) {
        Integer id = getBodyParam(ctx, "id", Integer.class);
        String title = getBodyParam(ctx, "title", String.class);
        String content = getBodyParam(ctx, "content", String.class);
        Long startTime = getBodyParam(ctx, "startTime", Long.class);
        Long endTime = getBodyParam(ctx, "endTime", Long.class);

        if (id == null) {
            fail(ctx, 400, "Missing id");
            return;
        }

        AnnouncementRecord record = announcementRecords.get(id);
        if (record == null) {
            fail(ctx, 404, "Announcement not found");
            return;
        }

        if (title != null && !title.trim().isEmpty()) {
            record.setTitle(title.trim());
        }
        if (content != null && !content.trim().isEmpty()) {
            record.setContent(content.trim());
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

        syncAnnouncementsToHttp();

        success(ctx, null, "Announcement updated successfully");

        ControllerManager.getController(OperationLogController.class).recordLog(
                ctx, OperationLogController.OperationType.ANNOUNCEMENT, "修改公告(ID:" + id + ")");
    }

    public void remove(Context ctx) {
        Integer id = getBodyParam(ctx, "id", Integer.class);

        if (id == null) {
            fail(ctx, 400, "Missing id");
            return;
        }

        if (!announcementRecords.containsKey(id)) {
            fail(ctx, 404, "Announcement not found");
            return;
        }

        announcementRecords.remove(id);

        syncAnnouncementsToHttp();

        success(ctx, null, "Announcement removed successfully");

        ControllerManager.getController(OperationLogController.class).recordLog(
                ctx, OperationLogController.OperationType.ANNOUNCEMENT, "删除公告(ID:" + id + ")");
    }

    public void list(Context ctx) {
        try {
            String pageStr = ctx.queryParam("page");
            String sizeStr = ctx.queryParam("size");

            List<AnnouncementRecord> allRecords = new ArrayList<>(announcementRecords.values());
            allRecords.sort((a, b) -> Long.compare(b.getCreateTime(), a.getCreateTime()));

            if (pageStr != null && sizeStr != null) {
                int page = Integer.parseInt(pageStr);
                int size = Integer.parseInt(sizeStr);
                int total = allRecords.size();
                int start = (page - 1) * size;
                int end = Math.min(start + size, total);

                List<AnnouncementRecord> pageData = new ArrayList<>();
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
            fail(ctx, 500, "Failed to get announcement list: " + e.getMessage());
        }
    }

    public List<AnnouncementRecord> getActiveAnnouncements() {
        long now = System.currentTimeMillis();
        List<AnnouncementRecord> activeList = new ArrayList<>();
        for (AnnouncementRecord record : announcementRecords.values()) {
            if (record.getStartTime() <= now && record.getEndTime() > now) {
                activeList.add(record);
            }
        }
        return activeList;
    }

    public void syncAnnouncementsToHttp() {
        List<AnnouncementRecord> activeList = getActiveAnnouncements();
        List<Map<String, Object>> announcements = new ArrayList<>();
        for (AnnouncementRecord record : activeList) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", record.getId());
            item.put("title", record.getTitle());
            item.put("content", record.getContent());
            item.put("startTime", record.getStartTime());
            item.put("endTime", record.getEndTime());
            announcements.add(item);
        }
        RpcFunction.newInstance().call(CallEnum.HttpRecvMessageService_setAnnouncements, JSON.toJSONString(announcements));
    }
}
