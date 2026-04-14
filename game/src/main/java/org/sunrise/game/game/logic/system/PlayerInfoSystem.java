package org.sunrise.game.game.logic.system;//package org.sunrise.game.game.logic.system;
//
//import com.alibaba.fastjson2.TypeReference;
//import lombok.Getter;
//import org.sunrise.game.game.logic.playerinfo.PlayerInfo;
//
//import java.util.HashMap;
//import java.util.Map;
//
///**
// * 玩家简略信息系统
// * 存储全服所有玩家的简略信息
// */
//@Getter
//public class PlayerInfoSystem extends BaseSystem {
//    // 玩家简略信息：humanId -> PlayerInfo
//    private Map<String, PlayerInfo> playerInfos = new HashMap<>();
//
//    @Override
//    public void init() {
//        playerInfos = new HashMap<>();
//    }
//
//    @Override
//    public void load() {
//        getDbData("playerInfos", new TypeReference<Map<String, PlayerInfo>>() {}, value -> {
//            if (value != null) {
//                playerInfos = value;
//            }
//        });
//    }
//
//    @Override
//    public void save() {
//        putDbData("playerInfos", playerInfos);
//    }
//
//    /**
//     * 更新玩家信息
//     */
//    public void updatePlayerInfo(String humanId, String name, int level, String headIcon, int sex, int fightPower) {
//        PlayerInfo playerInfo = playerInfos.computeIfAbsent(humanId, k -> new PlayerInfo());
//        playerInfo.setHumanId(humanId);
//        playerInfo.setName(name);
//        playerInfo.setLevel(level);
//        playerInfo.setHeadIcon(headIcon);
//        playerInfo.setSex(sex);
//        playerInfo.setFightPower(fightPower);
//    }
//
//    /**
//     * 获取玩家信息
//     */
//    public PlayerInfo getPlayerInfo(String humanId) {
//        return playerInfos.get(humanId);
//    }
//}
