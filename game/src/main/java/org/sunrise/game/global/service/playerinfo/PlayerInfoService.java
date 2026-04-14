package org.sunrise.game.global.service.playerinfo;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import org.sunrise.game.game.logic.playerinfo.PlayerInfo;
import org.sunrise.game.rpc.annotation.RpcMethod;
import org.sunrise.game.rpc.annotation.RpcService;
import org.sunrise.game.rpc.service.BaseService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 跨服玩家简略信息系统
 */
@RpcService
public class PlayerInfoService extends BaseService {
    // 玩家简略信息：humanId -> PlayerInfo
    private Map<String, PlayerInfo> playerInfos = new HashMap<>();

    public PlayerInfoService(String nodeId) {
        super(nodeId);
    }

    @Override
    public void load() {
        getDbData("playerInfos", new TypeReference<Map<String, PlayerInfo>>() {
        }, value -> {
            if (value != null) {
                playerInfos = value;
            }
        });
    }

    @Override
    public void save() {
        putDbData("playerInfos", playerInfos);
    }

    /**
     * 更新玩家信息
     */
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

    /**
     * 获取玩家信息
     */
    @RpcMethod
    public void getPlayerInfo(String humanId) {
        PlayerInfo playerInfo = playerInfos.get(humanId);
        if (playerInfo != null) {
            returns("humanId", humanId, "playerInfoJson", JSON.toJSONString(playerInfo));
        } else {
            returns("humanId", humanId, "playerInfoJson", null);
        }
    }

    /**
     * 批量获取玩家信息
     */
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

    /**
     * 获取所有玩家id
     */
    @RpcMethod
    public void getAllPlayerIds() {
        returns("humanIds", playerInfos.keySet());
    }
}
