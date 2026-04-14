package org.sunrise.game.game.human;

import org.sunrise.game.db.entity.EntityHumanList;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class HumanObjectManger {
    // humanId-玩家对象
    private static final Map<String, HumanObject> humanObjects = new HashMap<>();
    // connectId-所属对外服节点
    public static final Map<Long, String> connectObjectExternalNode = new HashMap<>();
    // connectId-连接对象
    private static final Map<Long, ConnectObject> connectObjects = new HashMap<>();
    // connectId-humanId
    public static final Map<Long, String> humanIds = new HashMap<>();
    // uid-accountId
    public static final Map<String, Long> uidAccounts = new HashMap<>();
    // uid-roleList
    public static final Map<String, List<EntityHumanList>> uidPlays = new HashMap<>();
    // 玩家人数计数器
    private static final AtomicInteger onlineCount = new AtomicInteger(0);
    // 待下线的玩家队列
    public static ConcurrentLinkedQueue<String> deleteHumanQueue = new ConcurrentLinkedQueue<>();
    // 封禁玩家队列
    public static ConcurrentLinkedQueue<String> banHumanQueue = new ConcurrentLinkedQueue<>();
    // 禁言玩家队列
    public static ConcurrentLinkedQueue<String> muteHumanQueue = new ConcurrentLinkedQueue<>();

    public static HumanObject getHumanObject(String id) {
        return humanObjects.get(id);
    }

    public static Collection<HumanObject> getHumanObjects() {
        return humanObjects.values();
    }

    public static void removeHumanObject(String id) {
        HumanObject removed = humanObjects.remove(id);
        if (removed != null) {
            onlineCount.decrementAndGet();
        }
    }

    public static void addHumanObject(String id, HumanObject humanObject) {
        HumanObject old = humanObjects.put(id, humanObject);
        if (old == null) {
            onlineCount.incrementAndGet();
        }
    }

    public static int getOnlineCount() {
        return onlineCount.get();
    }

    public static ConnectObject getConnectObject(long id) {
        return connectObjects.get(id);
    }

    public static void removeConnectObject(long id) {
        connectObjects.remove(id);
    }

    public static void addConnectObject(long id, ConnectObject connectObject) {
        connectObjects.put(id, connectObject);
    }
}