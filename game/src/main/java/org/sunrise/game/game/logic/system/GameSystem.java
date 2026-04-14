package org.sunrise.game.game.logic.system;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import org.sunrise.game.db.entity.EntityServerData;
import org.sunrise.game.game.db.DbManager;
import org.sunrise.game.game.logic.ToolsUtils;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.rpc.node.RpcNodeManager;
import org.sunrise.game.utils.Utils;

import java.util.HashMap;
import java.util.Map;

public class GameSystem {
    private static long initStartTime;
    private static long lastSaveDbTime = System.currentTimeMillis(); //上次保存数据的时间
    private static final Map<String, BaseSystem> systems = new HashMap<>();

    private static void createSystems() {
        systems.put(ResetSystem.class.getSimpleName(), new ResetSystem());
        systems.put(MinerSystem.class.getSimpleName(), new MinerSystem());
        systems.put(MapSystem.class.getSimpleName(), new MapSystem());
        systems.put(ActivitySystem.class.getSimpleName(), new ActivitySystem());
    }

    @SuppressWarnings("unchecked")
    public static <T extends BaseSystem> T getSystem(Class<T> clazz) {
        return (T) systems.get(clazz.getSimpleName());
    }

    /**
     * GameSystem 初始化
     */
    public static void init() {
        initStartTime = System.currentTimeMillis();
        createSystems();
        for (Map.Entry<String, BaseSystem> entry : systems.entrySet()) {
            entry.getValue().init();
        }
        load();
        waitInitEnd();
        Utils.setShutdownHook(GameSystem::saveSync);
    }

    /**
     * 等待所有系统初始化完成
     */
    public static void waitInitEnd() {
        while (true) {
            boolean allInitEnd = true;
            for (Map.Entry<String, BaseSystem> entry : systems.entrySet()) {
                if (!entry.getValue().isInitEnd()) {
                    allInitEnd = false;
                    break;
                }
            }
            
            if (allInitEnd) {
                LogCore.GameServer.info("Load All Systems end, took {} ms", System.currentTimeMillis() - initStartTime);
                break;
            }
            
            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * GameSystem load db
     */
    public static void load() {
        for (Map.Entry<String, BaseSystem> entry : systems.entrySet()) {
            DbManager.getDbService().queryGetOneByParamsAsync((result -> {
                if (result != null) {
                    EntityServerData serverData = new EntityServerData(result);
                    entry.getValue().setDataMap(JSON.parseObject(serverData.getData(), new TypeReference<Map<String, String>>() {}.getType()));
                    entry.getValue().load();
                } else {
                    DbManager.getDbService().executeAsync("insert into `server_data` (server_id,name,data) values (?,?,?)", RpcNodeManager.getRpcServerId(), entry.getKey(), JSON.toJSONBytes(entry.getValue().getDataMap()));
                }
                entry.getValue().setInitEnd(true);
            }), "select * from `server_data` where `server_id` = ? and `name` = ?", RpcNodeManager.getRpcServerId(), entry.getKey());
        }
    }

    /**
     * GameSystem save db
     */
    public static void save() {
        for (Map.Entry<String, BaseSystem> entry : systems.entrySet()) {
            entry.getValue().save();

            DbManager.getDbService().executeAsync("update `server_data` set `data` = ? where `server_id` = ? and `name` = ?",
                    JSON.toJSONBytes(entry.getValue().getDataMap()), RpcNodeManager.getRpcServerId(), entry.getKey());
        }
    }

    /**
     * GameSystem save db (同步)
     */
    public static void saveSync() {
        for (Map.Entry<String, BaseSystem> entry : systems.entrySet()) {
            entry.getValue().save();

            DbManager.getDbService().execute("update `server_data` set `data` = ? where `server_id` = ? and `name` = ?",
                    JSON.toJSONBytes(entry.getValue().getDataMap()), RpcNodeManager.getRpcServerId(), entry.getKey());
        }
    }

    /**
     * GameSystem 主心跳
     */
    public static void pulse() {
        long cur = System.currentTimeMillis();
        
        // 每分钟保存一次数据库
        if (lastSaveDbTime + ToolsUtils.MINUTE_MILLS <= cur) {
            lastSaveDbTime = cur;
            save();
        }
        
        // 每秒调用一次系统的 pulse
        for (Map.Entry<String, BaseSystem> entry : systems.entrySet()) {
            if (entry.getValue().isInitEnd()) {
                entry.getValue().pulse();
            }
        }
    }

}
