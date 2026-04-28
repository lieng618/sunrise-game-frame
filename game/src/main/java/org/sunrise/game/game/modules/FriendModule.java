package org.sunrise.game.game.modules;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import lombok.Getter;
import lombok.Setter;
import org.sunrise.game.game.annotation.HumanModule;
import org.sunrise.game.game.human.HumanObjectManger;
import org.sunrise.game.game.logic.friend.FriendRequestData;
import org.sunrise.game.game.logic.playerinfo.PlayerInfo;
import org.sunrise.game.genProto.gen.FriendProto;
import org.sunrise.game.genProto.gen.TopicProto;
import org.sunrise.game.genRpc.gen.CallEnum;
import org.sunrise.game.rpc.function.ErrorType;
import org.sunrise.game.rpc.function.RpcFunction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 好友模块
 */
@HumanModule
@Getter
@Setter
public class FriendModule extends BaseModule {

    public FriendModule(String humanId) {
        super(humanId);
    }

    @Override
    public void sendToClient() {
        // 登录时发送好友列表和申请列表
        sendFriendList();
        sendFriendRequestList();
    }

    /**
     * 发送好友列表
     */
    public void sendFriendList() {
        RpcFunction rpcFunction = RpcFunction.newInstance();
        rpcFunction.call(CallEnum.GlobalFriendService_getFriends, "humanId", getHumanId());
        rpcFunction.listenResult(rpcResult -> {
            if (getHuman() == null) {
                return;
            }
            if (rpcResult.getResult() != ErrorType.SUCCESS) {
                return;
            }
            @SuppressWarnings("unchecked")
            List<String> friendIds = (List<String>) rpcResult.getData("friends");

            if (friendIds == null || friendIds.isEmpty()) {
                FriendProto.MS2C_GetFriendList.Builder builder = FriendProto.MS2C_GetFriendList.newBuilder();
                getHuman().sendMsg(TopicProto.TOPIC.TOPIC_TYPE_FRIEND_VALUE,
                        FriendProto.FROM_SERVER.S2C_GetFriendList_VALUE, builder);
                return;
            }

            // 在回调中再次发起RPC调用，批量获取玩家信息
            RpcFunction rpcFunction2 = RpcFunction.newInstance();
            rpcFunction2.call(CallEnum.GlobalPlayerInfoService_getPlayerInfos, "humanIds", friendIds);
            rpcFunction2.listenResult(rpcResult2 -> {
                if (getHuman() == null) {
                    return;
                }
                if (rpcResult2.getResult() != ErrorType.SUCCESS) {
                    return;
                }
                
                String playerInfosJson = (String) rpcResult2.getData("playerInfosJson");
                Map<String, PlayerInfo> playerInfosMap = new HashMap<>();
                if (playerInfosJson != null && !playerInfosJson.isEmpty()) {
                    playerInfosMap = JSON.parseObject(playerInfosJson, new TypeReference<Map<String, PlayerInfo>>() {});
                }

                FriendProto.MS2C_GetFriendList.Builder builder = FriendProto.MS2C_GetFriendList.newBuilder();
                for (String friendId : friendIds) {
                    PlayerInfo playerInfo = playerInfosMap.get(friendId);
                    FriendProto.STFriendInfo.Builder friendInfoBuilder = FriendProto.STFriendInfo.newBuilder()
                            .setHumanId(friendId)
                            .setOnlineStatus(HumanObjectManger.getHumanObject(friendId) != null ? 1 : 0);
                    
                    if (playerInfo != null) {
                        friendInfoBuilder.setName(playerInfo.getName() != null ? playerInfo.getName() : "")
                                .setLevel(playerInfo.getLevel());
                    } else {
                        friendInfoBuilder.setName("").setLevel(0);
                    }
                    builder.addFriends(friendInfoBuilder.build());
                }
                
                getHuman().sendMsg(TopicProto.TOPIC.TOPIC_TYPE_FRIEND_VALUE,
                        FriendProto.FROM_SERVER.S2C_GetFriendList_VALUE, builder);
            });
        });
    }

    /**
     * 搜索玩家（只能搜索玩家ID）
     */
    public void searchPlayer(String playerId) {
        // 通过PlayerInfoService搜索玩家
        RpcFunction rpcFunction = RpcFunction.newInstance();
        rpcFunction.call(CallEnum.GlobalPlayerInfoService_getPlayerInfo, "humanId", playerId);
        rpcFunction.listenResult(rpcResult -> {
            if (getHuman() == null) {
                return;
            }
            if (rpcResult.getResult() != ErrorType.SUCCESS) {
                return;
            }
            String playerInfoJson = (String) rpcResult.getData("playerInfoJson");
            if (playerInfoJson != null && !playerInfoJson.isEmpty() && !playerId.equals(getHumanId())) {
                // 找到了玩家，构建消息
                PlayerInfo playerInfo = JSON.parseObject(playerInfoJson, PlayerInfo.class);
                FriendProto.STFriendInfo friendInfo = FriendProto.STFriendInfo.newBuilder()
                        .setHumanId(playerId)
                        .setName(playerInfo.getName() != null ? playerInfo.getName() : "")
                        .setLevel(playerInfo.getLevel())
                        .setOnlineStatus(HumanObjectManger.getHumanObject(playerId) != null ? 1 : 0)
                        .build();

                FriendProto.MS2C_SearchPlayer.Builder builder = FriendProto.MS2C_SearchPlayer.newBuilder();
                builder.addPlayers(friendInfo);
                getHuman().sendMsg(TopicProto.TOPIC.TOPIC_TYPE_FRIEND_VALUE,
                        FriendProto.FROM_SERVER.S2C_SearchPlayer_VALUE, builder);
            } else {
                // 未找到玩家
                FriendProto.MS2C_SearchPlayer.Builder builder = FriendProto.MS2C_SearchPlayer.newBuilder();
                getHuman().sendMsg(TopicProto.TOPIC.TOPIC_TYPE_FRIEND_VALUE,
                        FriendProto.FROM_SERVER.S2C_SearchPlayer_VALUE, builder);
            }
        });
    }

    /**
     * 发送好友申请
     */
    public void sendFriendRequest(String targetHumanId) {
        RpcFunction rpcFunction = RpcFunction.newInstance();
        rpcFunction.call(CallEnum.GlobalFriendService_sendFriendRequest,
            "applicantHumanId", getHumanId(),
            "targetHumanId", targetHumanId);
    }

    /**
     * 处理好友申请
     */
    public void handleFriendRequest(String applicantHumanId, int action) {
        RpcFunction rpcFunction = RpcFunction.newInstance();
        rpcFunction.call(CallEnum.GlobalFriendService_handleFriendRequest,
            "targetHumanId", getHumanId(),
            "applicantHumanId", applicantHumanId,
            "action", action);
    }

    /**
     * 删除好友
     */
    public void deleteFriend(String friendHumanId) {
        RpcFunction rpcFunction = RpcFunction.newInstance();
        rpcFunction.call(CallEnum.GlobalFriendService_deleteFriend,
            "humanId1", getHumanId(),
            "humanId2", friendHumanId);
        rpcFunction.listenResult(rpcResult -> {

        });
    }

    /**
     * 发送好友申请列表
     */
    public void sendFriendRequestList() {
        RpcFunction rpcFunction = RpcFunction.newInstance();
        rpcFunction.call(CallEnum.GlobalFriendService_getFriendRequests, "targetHumanId", getHumanId());
        rpcFunction.listenResult(rpcResult -> {
            if (getHuman() == null) {
                return;
            }
            if (rpcResult.getResult() != ErrorType.SUCCESS) {
                return;
            }
            String requestsJson = (String) rpcResult.getData("requestsJson");
            List<FriendRequestData> requests = JSON.parseObject(requestsJson, new TypeReference<List<FriendRequestData>>() {});

            if (requests == null || requests.isEmpty()) {
                FriendProto.MS2C_GetFriendRequestList.Builder builder = FriendProto.MS2C_GetFriendRequestList.newBuilder();
                getHuman().sendMsg(TopicProto.TOPIC.TOPIC_TYPE_FRIEND_VALUE,
                        FriendProto.FROM_SERVER.S2C_GetFriendRequestList_VALUE, builder);
                return;
            }

            // 收集所有申请人ID，批量获取玩家信息
            List<String> applicantIds = new java.util.ArrayList<>();
            for (FriendRequestData request : requests) {
                applicantIds.add(request.getApplicantHumanId());
            }

            // 在回调中再次发起RPC调用，批量获取玩家信息
            RpcFunction rpcFunction2 = RpcFunction.newInstance();
            rpcFunction2.call(CallEnum.GlobalPlayerInfoService_getPlayerInfos, "humanIds", applicantIds);
            rpcFunction2.listenResult(rpcResult2 -> {
                if (getHuman() == null) {
                    return;
                }
                if (rpcResult2.getResult() != ErrorType.SUCCESS) {
                    return;
                }
                
                String playerInfosJson = (String) rpcResult2.getData("playerInfosJson");
                Map<String, PlayerInfo> playerInfosMap = new HashMap<>();
                if (playerInfosJson != null && !playerInfosJson.isEmpty()) {
                    playerInfosMap = JSON.parseObject(playerInfosJson, new TypeReference<Map<String, PlayerInfo>>() {});
                }

                FriendProto.MS2C_GetFriendRequestList.Builder builder = FriendProto.MS2C_GetFriendRequestList.newBuilder();
                for (FriendRequestData request : requests) {
                    String applicantId = request.getApplicantHumanId();
                    PlayerInfo playerInfo = playerInfosMap.get(applicantId);
                    
                    FriendProto.STFriendRequestInfo.Builder requestInfoBuilder = FriendProto.STFriendRequestInfo.newBuilder()
                            .setApplicantHumanId(applicantId)
                            .setRequestTime(request.getRequestTime());
                    
                    if (playerInfo != null) {
                        requestInfoBuilder.setApplicantName(playerInfo.getName() != null ? playerInfo.getName() : "")
                                .setApplicantLevel(playerInfo.getLevel());
                    } else {
                        requestInfoBuilder.setApplicantName("").setApplicantLevel(0);
                    }
                    builder.addRequests(requestInfoBuilder.build());
                }
                
                getHuman().sendMsg(TopicProto.TOPIC.TOPIC_TYPE_FRIEND_VALUE,
                        FriendProto.FROM_SERVER.S2C_GetFriendRequestList_VALUE, builder);
            });
        });
    }

    /**
     * 通知新好友申请
     */
    public void notifyNewFriendRequest() {
        FriendProto.MS2C_FriendRequestUpdate.Builder builder = FriendProto.MS2C_FriendRequestUpdate.newBuilder();
        getHuman().sendMsg(TopicProto.TOPIC.TOPIC_TYPE_FRIEND_VALUE,
                FriendProto.FROM_SERVER.S2C_FriendRequestUpdate_VALUE, builder);
    }

    /**
     * 通知好友添加
     */
    public void notifyFriendAdded(String friendHumanId) {
        // 在RPC回调中获取好友信息
        RpcFunction rpcFunction = RpcFunction.newInstance();
        rpcFunction.call(CallEnum.GlobalPlayerInfoService_getPlayerInfo, "humanId", friendHumanId);
        rpcFunction.listenResult(rpcResult -> {
            if (getHuman() == null) {
                return;
            }
            if (rpcResult.getResult() != ErrorType.SUCCESS) {
                return;
            }
            
            String playerInfoJson = (String) rpcResult.getData("playerInfoJson");
            PlayerInfo playerInfo = null;
            if (playerInfoJson != null && !playerInfoJson.isEmpty()) {
                playerInfo = JSON.parseObject(playerInfoJson, PlayerInfo.class);
            }
            
            FriendProto.STFriendInfo.Builder friendInfoBuilder = FriendProto.STFriendInfo.newBuilder()
                    .setHumanId(friendHumanId)
                    .setOnlineStatus(HumanObjectManger.getHumanObject(friendHumanId) != null ? 1 : 0);
            
            if (playerInfo != null) {
                friendInfoBuilder.setName(playerInfo.getName() != null ? playerInfo.getName() : "")
                        .setLevel(playerInfo.getLevel());
            } else {
                friendInfoBuilder.setName("").setLevel(0);
            }
            
            FriendProto.MS2C_FriendUpdate.Builder builder = FriendProto.MS2C_FriendUpdate.newBuilder();
            builder.setFriend(friendInfoBuilder.build());
            builder.setUpdateType(1); // 添加
            getHuman().sendMsg(TopicProto.TOPIC.TOPIC_TYPE_FRIEND_VALUE,
                    FriendProto.FROM_SERVER.S2C_FriendUpdate_VALUE, builder);
        });
    }

    /**
     * 通知好友删除
     */
    public void notifyFriendDeleted(String friendHumanId) {
        FriendProto.STFriendInfo friendInfo = FriendProto.STFriendInfo.newBuilder()
                .setHumanId(friendHumanId)
                .build();

        FriendProto.MS2C_FriendUpdate.Builder builder = FriendProto.MS2C_FriendUpdate.newBuilder();
        builder.setFriend(friendInfo);
        builder.setUpdateType(2); // 删除
        getHuman().sendMsg(TopicProto.TOPIC.TOPIC_TYPE_FRIEND_VALUE,
                FriendProto.FROM_SERVER.S2C_FriendUpdate_VALUE, builder);
    }

}
