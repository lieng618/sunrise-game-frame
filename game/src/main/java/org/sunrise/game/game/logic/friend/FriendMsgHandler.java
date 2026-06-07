package org.sunrise.game.game.logic.friend;

import org.sunrise.game.game.annotation.MsgHandlerClass;
import org.sunrise.game.game.annotation.MsgHandlerMethod;
import org.sunrise.game.game.human.HumanObject;
import org.sunrise.game.game.modules.FriendModule;
import org.sunrise.game.genProto.gen.FriendProto;
import org.sunrise.game.genProto.gen.TopicProto;

/**
 * 好友消息处理器
 */
@MsgHandlerClass(packetType = TopicProto.TOPIC.TOPIC_TYPE_FRIEND_VALUE)
public class FriendMsgHandler {

    /**
     * 获取好友列表（空消息）
     */
    @MsgHandlerMethod(packetId = FriendProto.FROM_CLIENT.C2S_GetFriendList_VALUE)
    public static void getFriendList(HumanObject humanObject) {
        FriendModule module = humanObject.getModule(FriendModule.class);
        if (module != null) {
            module.sendFriendList();
        }
    }

    /**
     * 搜索玩家
     */
    @MsgHandlerMethod(packetId = FriendProto.FROM_CLIENT.C2S_SearchPlayer_VALUE)
    public static void searchPlayer(HumanObject humanObject, FriendProto.MC2S_SearchPlayer data) {
        FriendModule module = humanObject.getModule(FriendModule.class);
        if (module != null) {
            module.searchPlayer(data.getKeyword());
        }
    }

    /**
     * 发送好友申请
     */
    @MsgHandlerMethod(packetId = FriendProto.FROM_CLIENT.C2S_AddFriendRequest_VALUE)
    public static void addFriendRequest(HumanObject humanObject, FriendProto.MC2S_AddFriendRequest data) {
        FriendModule module = humanObject.getModule(FriendModule.class);
        if (module != null) {
            module.sendFriendRequest(data.getTargetHumanId());
        }
    }

    /**
     * 处理好友申请（同意/拒绝）
     */
    @MsgHandlerMethod(packetId = FriendProto.FROM_CLIENT.C2S_HandleFriendRequest_VALUE)
    public static void handleFriendRequest(HumanObject humanObject, FriendProto.MC2S_HandleFriendRequest data) {
        FriendModule module = humanObject.getModule(FriendModule.class);
        if (module != null) {
            module.handleFriendRequest(data.getApplicantHumanId(), data.getAction());
        }
    }

    /**
     * 删除好友
     */
    @MsgHandlerMethod(packetId = FriendProto.FROM_CLIENT.C2S_DeleteFriend_VALUE)
    public static void deleteFriend(HumanObject humanObject, FriendProto.MC2S_DeleteFriend data) {
        FriendModule module = humanObject.getModule(FriendModule.class);
        if (module != null) {
            module.deleteFriend(data.getFriendHumanId());
        }
    }

    /**
     * 获取好友申请列表（空消息）
     */
    @MsgHandlerMethod(packetId = FriendProto.FROM_CLIENT.C2S_GetFriendRequestList_VALUE)
    public static void getFriendRequestList(HumanObject humanObject) {
        FriendModule module = humanObject.getModule(FriendModule.class);
        if (module != null) {
            module.sendFriendRequestList();
        }
    }

    /**
     * 获取好友推荐列表（空消息）
     */
    @MsgHandlerMethod(packetId = FriendProto.FROM_CLIENT.C2S_GetFriendRecommendationList_VALUE)
    public static void GetFriendRecommendationList(HumanObject humanObject) {
        FriendModule module = humanObject.getModule(FriendModule.class);
        if (module != null) {
            module.sendFriendRecommendationList();
        }
    }
}
