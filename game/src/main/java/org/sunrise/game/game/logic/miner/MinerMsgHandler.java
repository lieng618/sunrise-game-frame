package org.sunrise.game.game.logic.miner;

import org.sunrise.game.game.annotation.MsgHandlerClass;
import org.sunrise.game.game.annotation.MsgHandlerMethod;
import org.sunrise.game.game.human.HumanObject;
import org.sunrise.game.game.logic.system.GameSystemUtils;
import org.sunrise.game.game.logic.system.MinerSystem;
import org.sunrise.game.game.modules.MinerModule;
import org.sunrise.game.genProto.gen.MinerProto;
import org.sunrise.game.genProto.gen.TopicProto;

import java.util.HashMap;
import java.util.List;

@MsgHandlerClass(packetType = TopicProto.TOPIC.TOPIC_TYPE_MINER_VALUE)
public class MinerMsgHandler {
    @MsgHandlerMethod(packetId = MinerProto.FROM_CLIENT.C2S_SyncData_VALUE)
    public static void syncData(HumanObject humanObject, MinerProto.MC2S_SyncData data) {
        MinerModule module = humanObject.getModule(MinerModule.class);
        if (module == null) return;
        module.syncData(data);
        GameSystemUtils.getSystem(MinerSystem.class).update(humanObject);
    }

    @MsgHandlerMethod(packetId = MinerProto.FROM_CLIENT.C2S_RankList_VALUE)
    public static void rankList(HumanObject humanObject) {
        MinerModule module = humanObject.getModule(MinerModule.class);
        if (module == null) return;

        MinerSystem minerSystem = GameSystemUtils.getSystem(MinerSystem.class);
        HashMap<String, Integer> info = minerSystem.getRanks(); // 获取分数 Map
        List<String> sortedNames = minerSystem.getSortRanks(); // 获取有序名字 List

        MinerProto.MS2C_RankList.Builder newBuilder = MinerProto.MS2C_RankList.newBuilder();

        // 遍历有序列表
        // 这里的 limit 可以控制只返回前 N 名 (例如前100名)，防止包过大
        int limit = 100;
        int count = 0;

        for (String name : sortedNames) {
            if (count >= limit) break; // 达到数量限制停止

            Integer level = info.get(name);
            if (level != null) {
                MinerProto.RankInfo.Builder builder = MinerProto.RankInfo.newBuilder();
                builder.setName(name).setLevelIndex(level);
                newBuilder.addInfos(builder);
                count++;
            }
        }

        humanObject.sendMsg(TopicProto.TOPIC.TOPIC_TYPE_MINER_VALUE, MinerProto.FROM_SERVER.S2C_RankList_VALUE, newBuilder);
    }
}