package org.sunrise.game.gmback.server.controller;

import io.javalin.http.Context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class OnlinePlayerController extends BaseController {
    private final ConcurrentHashMap<Integer, Long> lastUpdateTime = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, List<String>> serverHumans = new ConcurrentHashMap<>();

    public void updateHumanData(int serverId, List<String> humanIds) {
        serverHumans.put(serverId, new ArrayList<>(humanIds));
        lastUpdateTime.put(serverId, System.currentTimeMillis());
    }

    public void list(Context ctx) {
        try {
            String serverIdStr = ctx.queryParam("serverId");
            String pageStr = ctx.queryParam("page");
            String sizeStr = ctx.queryParam("size");

            List<Integer> deletes = new ArrayList<>();
            for (Map.Entry<Integer, Long> entry : lastUpdateTime.entrySet()) {
                if (System.currentTimeMillis() - entry.getValue() >= 60 * 1000L) {
                    deletes.add(entry.getKey());
                }
            }
            for (Integer delete : deletes) {
                serverHumans.remove(delete);
                lastUpdateTime.remove(delete);
            }

            List<Map<String, Object>> allPlayers = new ArrayList<>();
            for (Map.Entry<Integer, List<String>> entry : serverHumans.entrySet()) {
                int sid = entry.getKey();
                if (serverIdStr != null && !serverIdStr.isEmpty()) {
                    int filterServerId = Integer.parseInt(serverIdStr);
                    if (sid != filterServerId) {
                        continue;
                    }
                }
                for (String humanId : entry.getValue()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("serverId", sid);
                    map.put("humanId", humanId);
                    allPlayers.add(map);
                }
            }

            int total = allPlayers.size();

            if (pageStr != null && sizeStr != null) {
                int page = Integer.parseInt(pageStr);
                int size = Integer.parseInt(sizeStr);
                int start = (page - 1) * size;
                int end = Math.min(start + size, total);

                List<Map<String, Object>> pageData = new ArrayList<>();
                if (start < total) {
                    pageData = allPlayers.subList(start, end);
                }

                Map<String, Object> data = new HashMap<>();
                data.put("list", pageData);
                data.put("total", total);
                data.put("serverIds", new ArrayList<>(serverHumans.keySet()));
                success(ctx, data);
            } else {
                Map<String, Object> data = new HashMap<>();
                data.put("list", allPlayers);
                data.put("total", total);
                data.put("serverIds", new ArrayList<>(serverHumans.keySet()));
                success(ctx, data);
            }
        } catch (NumberFormatException e) {
            fail(ctx, 400, "Invalid parameter");
        } catch (Exception e) {
            fail(ctx, 500, "Failed to get online players: " + e.getMessage());
        }
    }
}
