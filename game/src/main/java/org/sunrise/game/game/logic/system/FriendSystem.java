package org.sunrise.game.game.logic.system;//package org.sunrise.game.game.logic.system;
//
//import com.alibaba.fastjson2.TypeReference;
//import lombok.Getter;
//import org.sunrise.game.game.human.HumanObject;
//import org.sunrise.game.game.human.HumanObjectManger;
//import org.sunrise.game.game.logic.friend.FriendRequestData;
//import org.sunrise.game.game.logic.playerinfo.PlayerInfo;
//import org.sunrise.game.game.modules.FriendModule;
//
//import java.util.*;
//
///**
// * 好友系统
// * 统一管理所有好友关系和好友申请
// */
//@Getter
//public class FriendSystem extends BaseSystem {
//    // 好友关系：humanId -> Set<friendHumanId>（双向关系）
//    // 如果 A 和 B 是好友，则 friends.get("A") 包含 "B"，friends.get("B") 包含 "A"
//    private Map<String, Set<String>> friends = new HashMap<>();
//
//    // 好友申请：targetHumanId -> List<FriendRequestData>
//    private Map<String, List<FriendRequestData>> friendRequests = new HashMap<>();
//
//    // 申请过期时间（7天）
//    private static final long REQUEST_EXPIRE_TIME = 7 * 24 * 60 * 60 * 1000L;
//
//    // 好友上限
//    private static final int maxFriends = 100;
//
//    @Override
//    public void load() {
//        getDbData("friends", new TypeReference<Map<String, Set<String>>>() {}, value -> {
//            if (value != null) {
//                friends = value;
//            }
//        });
//        getDbData("friendRequests", new TypeReference<Map<String, List<FriendRequestData>>>() {}, value -> {
//            if (value != null) {
//                friendRequests = value;
//            }
//        });
//    }
//
//    @Override
//    public void save() {
//        putDbData("friends", friends);
//        putDbData("friendRequests", friendRequests);
//    }
//
//    @Override
//    public void pulse() {
//        // 清理过期申请
//        cleanExpiredRequests();
//    }
//
//    /**
//     * 添加好友（双向）
//     */
//    public void addFriend(String humanId1, String humanId2) {
//        friends.computeIfAbsent(humanId1, k -> new HashSet<>()).add(humanId2);
//        friends.computeIfAbsent(humanId2, k -> new HashSet<>()).add(humanId1);
//    }
//
//    /**
//     * 删除好友（双向）
//     */
//    public void removeFriend(String humanId1, String humanId2) {
//        Set<String> friends1 = friends.get(humanId1);
//        Set<String> friends2 = friends.get(humanId2);
//        if (friends1 != null) {
//            friends1.remove(humanId2);
//            if (friends1.isEmpty()) {
//                friends.remove(humanId1);
//            }
//        }
//        if (friends2 != null) {
//            friends2.remove(humanId1);
//            if (friends2.isEmpty()) {
//                friends.remove(humanId2);
//            }
//        }
//    }
//
//    /**
//     * 获取好友列表
//     */
//    public Set<String> getFriends(String humanId) {
//        return friends.getOrDefault(humanId, new HashSet<>());
//    }
//
//    /**
//     * 检查是否为好友
//     */
//    public boolean isFriend(String humanId1, String humanId2) {
//        return friends.getOrDefault(humanId1, new HashSet<>()).contains(humanId2);
//    }
//
//    /**
//     * 添加好友申请
//     */
//    public void addFriendRequest(String applicantHumanId, String targetHumanId, long requestTime) {
//        FriendRequestData request = new FriendRequestData(applicantHumanId, requestTime);
//        friendRequests.computeIfAbsent(targetHumanId, k -> new ArrayList<>()).add(request);
//    }
//
//    /**
//     * 获取好友申请列表
//     */
//    public List<FriendRequestData> getFriendRequests(String targetHumanId) {
//        return friendRequests.getOrDefault(targetHumanId, new ArrayList<>());
//    }
//
//    /**
//     * 检查是否已发送申请
//     * 通过检查目标玩家的申请列表中是否包含申请人的ID来判断
//     */
//    public boolean hasSentRequest(String applicantHumanId, String targetHumanId) {
//        List<FriendRequestData> requests = friendRequests.get(targetHumanId);
//        if (requests == null) {
//            return false;
//        }
//        for (FriendRequestData request : requests) {
//            if (request.getApplicantHumanId().equals(applicantHumanId)) {
//                return true;
//            }
//        }
//        return false;
//    }
//
//    /**
//     * 处理好友申请（同意/拒绝）
//     * @param targetHumanId 处理申请的玩家ID
//     * @param applicantHumanId 申请人ID
//     * @param action 操作：1-同意，2-拒绝
//     * @return 处理的好友申请数据，如果不存在返回null
//     */
//    public FriendRequestData handleFriendRequest(String targetHumanId, String applicantHumanId, int action) {
//        List<FriendRequestData> requests = friendRequests.get(targetHumanId);
//        if (requests == null) {
//            return null;
//        }
//
//        FriendRequestData request = null;
//        Iterator<FriendRequestData> iterator = requests.iterator();
//        while (iterator.hasNext()) {
//            FriendRequestData req = iterator.next();
//            if (req.getApplicantHumanId().equals(applicantHumanId)) {
//                request = req;
//                iterator.remove();
//                break;
//            }
//        }
//
//        if (request != null) {
//            // 如果申请列表为空，移除该key
//            if (requests.isEmpty()) {
//                friendRequests.remove(targetHumanId);
//            }
//
//            // 同意则添加好友
//            if (action == 1) {
//                addFriend(targetHumanId, applicantHumanId);
//            }
//        }
//
//        return request;
//    }
//
//    /**
//     * 发送好友申请（包含所有检查逻辑）
//     * @param applicantHumanId 申请人ID
//     * @param targetHumanId 目标玩家ID
//     * @return 0-成功，1-不能添加自己，2-已经是好友，3-已发送申请，4-好友数量已达上限
//     */
//    public int sendFriendRequest(String applicantHumanId, String targetHumanId) {
//        // 检查是否是自己
//        if (targetHumanId.equals(applicantHumanId)) {
//            return 1;
//        }
//
//        // 检查是否已经是好友
//        if (isFriend(applicantHumanId, targetHumanId)) {
//            return 2;
//        }
//
//        // 检查是否已发送申请
//        if (hasSentRequest(applicantHumanId, targetHumanId)) {
//            return 3;
//        }
//
//        // 检查好友上限
//        Set<String> friends = getFriends(applicantHumanId);
//        if (friends.size() >= maxFriends) {
//            return 4;
//        }
//
//        // 添加申请
//        addFriendRequest(applicantHumanId, targetHumanId, System.currentTimeMillis());
//        return 0;
//    }
//
//    /**
//     * 搜索玩家（只能搜索玩家ID）
//     * @param playerId 玩家ID
//     * @param excludeHumanId 排除的玩家ID（搜索者自己）
//     * @return 如果找到且不是自己，返回玩家ID，否则返回null
//     */
//    public String searchPlayer(String playerId, String excludeHumanId) {
//        if (playerId == null || playerId.trim().isEmpty()) {
//            return null;
//        }
//
//        // 不能搜索自己
//        if (playerId.equals(excludeHumanId)) {
//            return null;
//        }
//
//        // 从玩家简略信息系统中搜索
//        PlayerInfoSystem playerInfoSystem = GameSystem.getSystem(PlayerInfoSystem.class);
//        PlayerInfo playerInfo = playerInfoSystem.getPlayerInfo(playerId);
//        if (playerInfo != null) {
//            return playerId;
//        }
//
//        return null;
//    }
//
//    /**
//     * 处理好友申请并通知相关玩家
//     *
//     * @param targetHumanId    处理申请的玩家ID
//     * @param applicantHumanId 申请人ID
//     * @param action           操作：1-同意，2-拒绝
//     */
//    public void handleFriendRequestWithNotify(String targetHumanId, String applicantHumanId, int action) {
//        FriendRequestData request = handleFriendRequest(targetHumanId, applicantHumanId, action);
//
//        if (request != null && action == 1) {
//            // 同意后，通知双方
//            notifyFriendAdded(targetHumanId, applicantHumanId);
//            notifyFriendAdded(applicantHumanId, targetHumanId);
//        }
//    }
//
//    /**
//     * 删除好友并通知相关玩家
//     * @param humanId1 玩家1 ID
//     * @param humanId2 玩家2 ID
//     */
//    public void removeFriendWithNotify(String humanId1, String humanId2) {
//        removeFriend(humanId1, humanId2);
//        notifyFriendDeleted(humanId1, humanId2);
//        notifyFriendDeleted(humanId2, humanId1);
//    }
//
//    /**
//     * 通知玩家有新好友申请
//     * @param targetHumanId 目标玩家ID
//     * @param applicantHumanId 申请人ID
//     */
//    public void notifyNewFriendRequest(String targetHumanId, String applicantHumanId) {
//        HumanObject targetHuman = HumanObjectManger.getHumanObject(targetHumanId);
//        if (targetHuman != null) {
//            targetHuman.getModule(FriendModule.class).notifyNewFriendRequest(applicantHumanId);
//        }
//    }
//
//    /**
//     * 通知玩家好友添加
//     * @param humanId 玩家ID
//     * @param friendHumanId 好友ID
//     */
//    private void notifyFriendAdded(String humanId, String friendHumanId) {
//        HumanObject human = HumanObjectManger.getHumanObject(humanId);
//        if (human != null) {
//            human.getModule(FriendModule.class).notifyFriendAdded(friendHumanId);
//        }
//    }
//
//    /**
//     * 通知玩家好友删除
//     * @param humanId 玩家ID
//     * @param friendHumanId 好友ID
//     */
//    private void notifyFriendDeleted(String humanId, String friendHumanId) {
//        HumanObject human = HumanObjectManger.getHumanObject(humanId);
//        if (human != null) {
//            human.getModule(FriendModule.class).notifyFriendDeleted(friendHumanId);
//        }
//    }
//
//    /**
//     * 清理过期申请
//     */
//    private void cleanExpiredRequests() {
//        long currentTime = System.currentTimeMillis();
//        Iterator<Map.Entry<String, List<FriendRequestData>>> iterator = friendRequests.entrySet().iterator();
//
//        while (iterator.hasNext()) {
//            Map.Entry<String, List<FriendRequestData>> entry = iterator.next();
//            List<FriendRequestData> requests = entry.getValue();
//
//            Iterator<FriendRequestData> reqIterator = requests.iterator();
//            while (reqIterator.hasNext()) {
//                FriendRequestData req = reqIterator.next();
//                if (currentTime - req.getRequestTime() > REQUEST_EXPIRE_TIME) {
//                    // 移除过期申请
//                    reqIterator.remove();
//                }
//            }
//
//            // 如果列表为空，移除该key
//            if (requests.isEmpty()) {
//                iterator.remove();
//            }
//        }
//    }
//}
