package org.sunrise.game.game.logic.rank;

import java.util.Map;

import org.sunrise.game.game.annotation.MsgHandlerClass;
import org.sunrise.game.game.annotation.MsgHandlerMethod;
import org.sunrise.game.game.human.HumanObject;
import org.sunrise.game.game.human.HumanObjectManger;
import org.sunrise.game.genProto.gen.RankProto;
import org.sunrise.game.genProto.gen.TopicProto;
import org.sunrise.game.genRpc.gen.CallEnum;
import org.sunrise.game.rpc.function.ErrorType;
import org.sunrise.game.rpc.function.RpcFunction;

/**
 * 排行榜：客户端协议处理
 */
@MsgHandlerClass(packetType = TopicProto.TOPIC.TOPIC_TYPE_RANK_VALUE)
public class RankMsgHandler {

    @MsgHandlerMethod(packetId = RankProto.FROM_CLIENT.C2S_GetRankList_VALUE)
    public static void getRankList(HumanObject humanObject, RankProto.MC2S_GetRankList data) {
        RpcFunction rpcFunction = RpcFunction.newInstance();
        rpcFunction.call(CallEnum.GlobalRankService_getRankList,
                data.getRankType(),
                data.getPage(),
                data.getPageSize());
        rpcFunction.listenResult(rpcResult -> {
            String humanId = (String) rpcResult.getContext("humanId");
            HumanObject humanObj = HumanObjectManger.getHumanObject(humanId);
            if (humanObj == null || rpcResult.getResult() != ErrorType.SUCCESS) return;
            byte[] protoData = (byte[]) rpcResult.getData("protoData");
            humanObj.sendMsg(TopicProto.TOPIC.TOPIC_TYPE_RANK_VALUE,
                    RankProto.FROM_SERVER.S2C_GetRankList_VALUE, protoData);
        }, Map.of("humanId", humanObject.getHumanId()));
    }

    @MsgHandlerMethod(packetId = RankProto.FROM_CLIENT.C2S_GetMyRank_VALUE)
    public static void getMyRank(HumanObject humanObject, RankProto.MC2S_GetMyRank data) {
        RpcFunction rpcFunction = RpcFunction.newInstance();
        rpcFunction.call(CallEnum.GlobalRankService_getMyRank,
                data.getRankType(),
                humanObject.getHumanId());
        rpcFunction.listenResult(rpcResult -> {
            String humanId = (String) rpcResult.getContext("humanId");
            HumanObject humanObj = HumanObjectManger.getHumanObject(humanId);
            if (humanObj == null || rpcResult.getResult() != ErrorType.SUCCESS) return;
            byte[] protoData = (byte[]) rpcResult.getData("protoData");
            humanObject.sendMsg(TopicProto.TOPIC.TOPIC_TYPE_RANK_VALUE,
                    RankProto.FROM_SERVER.S2C_GetMyRank_VALUE, protoData);
        }, Map.of("humanId", humanObject.getHumanId()));
    }
}
