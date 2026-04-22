package org.sunrise.game.game.logic.system;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import org.sunrise.game.db.entity.EntityServerData;
import org.sunrise.game.game.annotation.GameSystem;
import org.sunrise.game.game.db.DbManager;
import org.sunrise.game.game.human.HumanObject;
import org.sunrise.game.game.human.HumanObjectManger;
import org.sunrise.game.game.logic.ToolsUtils;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.rpc.node.RpcNodeManager;
import org.sunrise.game.utils.Utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameSystemUtils {
    private static long loadStartTime;
    private static long lastSaveDbTime = System.currentTimeMillis(); //上次保存数据的时间
    private static final Map<String, BaseSystem> systems = new HashMap<>();

    private static void createSystems(List<String> classPaths) {
        long startTime = System.currentTimeMillis();
        for (String classPath : classPaths) {
            try {
                List<Class<?>> classes = Utils.findClasses(classPath);
                for (Class<?> clazz : classes) {
                    if (!clazz.isAnnotationPresent(GameSystem.class)) {
                        continue;
                    }
                    if (!BaseSystem.class.isAssignableFrom(clazz) || clazz == BaseSystem.class) {
                        continue;
                    }
                    long classStartTime = System.currentTimeMillis();
                    @SuppressWarnings("unchecked")
                    Class<? extends BaseSystem> systemClass = (Class<? extends BaseSystem>) clazz;
                    systems.put(systemClass.getSimpleName(), systemClass.getConstructor().newInstance());
                    long classEndTime = System.currentTimeMillis();
                    LogCore.GameServer.info("Load class end, name = { {} }, took {} ms", clazz.getName(), classEndTime - classStartTime);
                }
            } catch (Exception e) {
                LogCore.GameServer.error("GameSystemUtils init failed, error: {}", e.getMessage(), e);
            }
        }
        LogCore.GameServer.info("GameSystemUtils init end, loaded {} systems, took {} ms", systems.size(), System.currentTimeMillis() - startTime);
    }

    @SuppressWarnings("unchecked")
    public static <T extends BaseSystem> T getSystem(Class<T> clazz) {
        return (T) systems.get(clazz.getSimpleName());
    }

    /**
     * GameSystem 初始化
     */
    public static void init(List<String> classPaths) {
        createSystems(classPaths);
        for (Map.Entry<String, BaseSystem> entry : systems.entrySet()) {
            entry.getValue().init();
        }
        loadStartTime = System.currentTimeMillis();
        load();
        waitInitEnd();
        Utils.setShutdownHook(GameSystemUtils::saveSync);
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
                LogCore.GameServer.info("Load All Systems end, took {} ms", System.currentTimeMillis() - loadStartTime);
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
                    entry.getValue().setDataMap(JSON.parseObject(serverData.getData(), new TypeReference<Map<String, String>>() {
                    }.getType()));
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
        // 系统的心跳
        for (Map.Entry<String, BaseSystem> entry : systems.entrySet()) {
            if (entry.getValue().isInitEnd()) {
                entry.getValue().pulse();
            }
        }

        // 玩家模块心跳
        for (HumanObject humanObject : HumanObjectManger.getHumanObjects()) {
            humanObject.pulse();
        }
    }

    /**
     * GameSystem 主心跳 每秒
     */
    public static void pulsePerSec() {
        long cur = System.currentTimeMillis();

        // 每分钟保存一次数据库
        if (lastSaveDbTime + ToolsUtils.MINUTE_MILLS <= cur) {
            lastSaveDbTime = cur;
            save();
        }

        // 每秒调用一次系统的心跳
        for (Map.Entry<String, BaseSystem> entry : systems.entrySet()) {
            if (entry.getValue().isInitEnd()) {
                entry.getValue().pulsePerSec();
            }
        }

        // 每秒调用一次玩家模块心跳
        for (HumanObject humanObject : HumanObjectManger.getHumanObjects()) {
            humanObject.pulsePerSec();
        }
    }

}
