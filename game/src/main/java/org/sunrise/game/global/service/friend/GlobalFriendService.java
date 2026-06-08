package org.sunrise.game.global.service.friend;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import org.sunrise.game.game.logic.friend.FriendRequestData;
import org.sunrise.game.genRpc.gen.CallEnum;
import org.sunrise.game.rpc.annotation.RpcMethod;
import org.sunrise.game.rpc.annotation.RpcService;
import org.sunrise.game.rpc.function.RpcFunction;
import org.sunrise.game.rpc.service.BaseService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RpcService
public class GlobalFriendService extends BaseService {
    private Map<String, Set<String>> friends = new HashMap<>();
    private Map<String, List<FriendRequestData>> friendRequests = new HashMap<>();

    private static final long REQUEST_EXPIRE_TIME = 7 * 24 * 60 * 60 * 1000L;
    private static final int MAX_FRIENDS = 100;

    public GlobalFriendService(String nodeId) {
        super(nodeId);
    }

    @Override
    public void load() {
        getDbData("friends", new TypeReference<Map<String, Set<String>>>() {
        }, value -> {
            if (value != null) {
                friends = value;
            }
        });
        getDbData("friendRequests", new TypeReference<Map<String, List<FriendRequestData>>>() {
        }, value -> {
            if (value != null) {
                friendRequests = value;
            }
        });
    }

    @Override
    public void save() {
        putDbData("friends", friends);
        putDbData("friendRequests", friendRequests);
    }

    @Override
    public void pulse() {
        cleanExpiredRequests();
    }

    @RpcMethod
    public void sendFriendRequest(String applicantHumanId, String targetHumanId) {
        if (targetHumanId.equals(applicantHumanId)) {
            returns("applicantHumanId", applicantHumanId, "targetHumanId", targetHumanId, "result", 1);
            return;
        }

        if (isFriend(applicantHumanId, targetHumanId)) {
            returns("applicantHumanId", applicantHumanId, "targetHumanId", targetHumanId, "result", 2);
            return;
        }

        if (hasSentRequest(applicantHumanId, targetHumanId)) {
            returns("applicantHumanId", applicantHumanId, "targetHumanId", targetHumanId, "result", 3);
            return;
        }

        Set<String> friendIds = friends.getOrDefault(applicantHumanId, new HashSet<>());
        if (friendIds.size() >= MAX_FRIENDS) {
            returns("applicantHumanId", applicantHumanId, "targetHumanId", targetHumanId, "result", 4);
            return;
        }

        addFriendRequest(applicantHumanId, targetHumanId, System.currentTimeMillis());

        RpcFunction.newInstance(RpcFunction.RpcCallType.SendAll)
                .call(CallEnum.FriendRpcListenService_onNewFriendRequest, targetHumanId);

        returns("applicantHumanId", applicantHumanId, "targetHumanId", targetHumanId, "result", 0);
    }

    @RpcMethod
    public void handleFriendRequest(String targetHumanId, String applicantHumanId, int action) {
        FriendRequestData request = handleFriendRequestInternal(targetHumanId, applicantHumanId, action);

        if (request != null && action == 1) {
            RpcFunction.newInstance(RpcFunction.RpcCallType.SendAll)
                    .call(CallEnum.FriendRpcListenService_onFriendAdded, targetHumanId, applicantHumanId);

            RpcFunction.newInstance(RpcFunction.RpcCallType.SendAll)
                    .call(CallEnum.FriendRpcListenService_onFriendAdded, applicantHumanId, targetHumanId);
        }

        returns("targetHumanId", targetHumanId, "applicantHumanId", applicantHumanId, "action", action, "success", request != null);
    }

    @RpcMethod
    public void deleteFriend(String humanId1, String humanId2) {
        removeFriend(humanId1, humanId2);

        RpcFunction.newInstance(RpcFunction.RpcCallType.SendAll)
                .call(CallEnum.FriendRpcListenService_onFriendDeleted, humanId1, humanId2);

        RpcFunction.newInstance(RpcFunction.RpcCallType.SendAll)
                .call(CallEnum.FriendRpcListenService_onFriendDeleted, humanId2, humanId1);

        returns("humanId1", humanId1, "humanId2", humanId2, "success", true);
    }

    @RpcMethod
    public void getFriends(String humanId) {
        Set<String> friendIds = friends.getOrDefault(humanId, new HashSet<>());
        returns("humanId", humanId, "friends", new ArrayList<>(friendIds));
    }

    @RpcMethod
    public void getFriendRequests(String targetHumanId) {
        List<FriendRequestData> requests =
                friendRequests.getOrDefault(targetHumanId, new ArrayList<>());
        returns("targetHumanId", targetHumanId, "requestsJson", JSON.toJSONString(requests));
    }

    public void cleanExpiredRequests() {
        long currentTime = System.currentTimeMillis();
        Iterator<Map.Entry<String, List<FriendRequestData>>> iterator = friendRequests.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, List<FriendRequestData>> entry = iterator.next();
            List<FriendRequestData> requests = entry.getValue();

            requests.removeIf(req -> currentTime - req.getRequestTime() > REQUEST_EXPIRE_TIME);

            if (requests.isEmpty()) {
                iterator.remove();
            }
        }
    }

    private void addFriend(String humanId1, String humanId2) {
        friends.computeIfAbsent(humanId1, k -> new HashSet<>()).add(humanId2);
        friends.computeIfAbsent(humanId2, k -> new HashSet<>()).add(humanId1);
    }

    private void removeFriend(String humanId1, String humanId2) {
        Set<String> friends1 = friends.get(humanId1);
        Set<String> friends2 = friends.get(humanId2);
        if (friends1 != null) {
            friends1.remove(humanId2);
            if (friends1.isEmpty()) {
                friends.remove(humanId1);
            }
        }
        if (friends2 != null) {
            friends2.remove(humanId1);
            if (friends2.isEmpty()) {
                friends.remove(humanId2);
            }
        }
    }

    private boolean isFriend(String humanId1, String humanId2) {
        return friends.getOrDefault(humanId1, new HashSet<>()).contains(humanId2);
    }

    private void addFriendRequest(String applicantHumanId, String targetHumanId, long requestTime) {
        FriendRequestData request = new FriendRequestData(applicantHumanId, requestTime);
        friendRequests.computeIfAbsent(targetHumanId, k -> new ArrayList<>()).add(request);
    }

    private boolean hasSentRequest(String applicantHumanId, String targetHumanId) {
        List<FriendRequestData> requests = friendRequests.get(targetHumanId);
        if (requests == null) {
            return false;
        }
        for (FriendRequestData request : requests) {
            if (request.getApplicantHumanId().equals(applicantHumanId)) {
                return true;
            }
        }
        return false;
    }

    private FriendRequestData handleFriendRequestInternal(String targetHumanId, String applicantHumanId, int action) {
        List<FriendRequestData> requests = friendRequests.get(targetHumanId);
        if (requests == null) {
            return null;
        }

        FriendRequestData request = null;
        Iterator<FriendRequestData> iterator = requests.iterator();
        while (iterator.hasNext()) {
            FriendRequestData req = iterator.next();
            if (req.getApplicantHumanId().equals(applicantHumanId)) {
                request = req;
                iterator.remove();
                break;
            }
        }

        if (request != null) {
            if (requests.isEmpty()) {
                friendRequests.remove(targetHumanId);
            }
            if (action == 1) {
                addFriend(targetHumanId, applicantHumanId);
            }
        }

        return request;
    }
}
