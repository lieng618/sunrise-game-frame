package org.sunrise.game.global.service.playerinfo;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import org.sunrise.game.game.logic.playerinfo.PlayerInfo;
import org.sunrise.game.rpc.annotation.RpcMethod;
import org.sunrise.game.rpc.annotation.RpcService;
import org.sunrise.game.rpc.service.BaseService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

@RpcService
public class GlobalPlayerInfoService extends BaseService {
    private static final int DEFAULT_PAGE_SIZE = 1_000;
    private static final int MAX_PAGE_SIZE = 10_000;

    private final NavigableMap<String, PlayerInfo> playerInfos = new ConcurrentSkipListMap<>();

    public GlobalPlayerInfoService(String nodeId) {
        super(nodeId);
    }

    @Override
    public void load() {
        getDbData("playerInfos", new TypeReference<Map<String, PlayerInfo>>() {
        }, value -> {
            if (value != null) {
                playerInfos.clear();
                playerInfos.putAll(value);
            }
        });
    }

    @Override
    public void save() {
        putDbData("playerInfos", playerInfos);
    }

    @RpcMethod
    public void updatePlayerInfo(String humanId, String name, int level, String headIcon, int sex, int fightPower) {
        PlayerInfo playerInfo = playerInfos.computeIfAbsent(humanId, k -> new PlayerInfo());
        playerInfo.setHumanId(humanId);
        playerInfo.setName(name);
        playerInfo.setLevel(level);
        playerInfo.setHeadIcon(headIcon);
        playerInfo.setSex(sex);
        playerInfo.setFightPower(fightPower);
    }

    @RpcMethod
    public void getPlayerInfo(String humanId) {
        PlayerInfo playerInfo = playerInfos.get(humanId);
        if (playerInfo != null) {
            returns("humanId", humanId, "playerInfoJson", JSON.toJSONString(playerInfo));
        } else {
            returns("humanId", humanId, "playerInfoJson", null);
        }
    }

    @RpcMethod
    public void getPlayerInfos(List<String> humanIds) {
        Map<String, PlayerInfo> result = new HashMap<>();
        for (String humanId : humanIds) {
            PlayerInfo info = playerInfos.get(humanId);
            if (info != null) {
                result.put(humanId, info);
            }
        }
        returns("playerInfosJson", JSON.toJSONString(result));
    }

    @RpcMethod
    public void getAllPlayerIds(String afterHumanId, int limit) {
        PlayerIdPage page = selectPlayerIdPage(playerInfos, afterHumanId, limit);
        returns(
                "humanIds", page.humanIds(),
                "nextCursor", page.nextCursor(),
                "hasMore", page.hasMore());
    }

    static PlayerIdPage selectPlayerIdPage(
            NavigableMap<String, PlayerInfo> playerInfos,
            String afterHumanId,
            int limit) {
        int pageSize = limit <= 0 ? DEFAULT_PAGE_SIZE : Math.min(limit, MAX_PAGE_SIZE);
        Iterator<String> iterator = afterHumanId == null
                ? playerInfos.navigableKeySet().iterator()
                : playerInfos.tailMap(afterHumanId, false).navigableKeySet().iterator();

        List<String> humanIds = new ArrayList<>(pageSize);
        for (int i = 0; i < pageSize && iterator.hasNext(); i++) {
            humanIds.add(iterator.next());
        }

        boolean hasMore = iterator.hasNext();
        String nextCursor = hasMore ? humanIds.getLast() : null;
        return new PlayerIdPage(List.copyOf(humanIds), nextCursor, hasMore);
    }

    record PlayerIdPage(List<String> humanIds, String nextCursor, boolean hasMore) {
    }
}
