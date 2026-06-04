package org.sunrise.game.rpc.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import org.sunrise.game.db.DbManager;
import org.sunrise.game.db.entity.EntityServerData;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.rpc.node.RpcNodeManager;
import org.sunrise.game.utils.Utils;

import java.util.HashMap;
import java.util.Map;

public class ServiceManager {
    private static long initStartTime;
    private static long lastPulsePerMinTime = 0L; //上次系统每分钟心跳的时间
    private static long lastPulsePerSecTime = 0L; //上次系统每秒心跳的时间
    private static long lastPulsePer100MsTime = 0L; //上次系统每100毫秒心跳的时间
    private static long lastPulsePer5SecTime = 0L; //上次系统每5秒心跳的时间
    private static final Map<String, BaseService> services = new HashMap<>();

    public static void registerService(BaseService instance) {
        services.put(instance.getClass().getSimpleName(), instance);
    }

    public static BaseService getService(String className) {
        return services.get(className);
    }

    public static void initAll() {
        initStartTime = System.currentTimeMillis();
        for (BaseService service : services.values()) {
            service.init();
        }
        load();
        waitInitEnd();
        Utils.setShutdownHook(ServiceManager::saveSync);
    }

    /**
     * 等待所有系统初始化完成
     */
    public static void waitInitEnd() {
        while (true) {
            boolean allInitEnd = true;
            for (Map.Entry<String, BaseService> entry : services.entrySet()) {
                if (!entry.getValue().isInitEnd()) {
                    allInitEnd = false;
                    break;
                }
            }

            if (allInitEnd) {
                LogCore.RpcUtils.info("Load All Service end, took {} ms", System.currentTimeMillis() - initStartTime);
                break;
            }

            Utils.sleep(30);
        }
    }

    /**
     * ServiceManager load db
     */
    public static void load() {
        for (Map.Entry<String, BaseService> entry : services.entrySet()) {
            DbManager.getDbService().queryGetOneByParamsAsync((result -> {
                try {
                    if (result != null) {
                        EntityServerData serverData = new EntityServerData(result);
                        entry.getValue().setDataMap(JSON.parseObject(serverData.getData(), new TypeReference<Map<String, String>>() {}.getType()));
                        entry.getValue().load();
                    } else {
                        DbManager.getDbService().executeAsync("insert into `server_data` (server_id,name,data) values (?,?,?)", RpcNodeManager.getRpcServerId(), entry.getKey(), JSON.toJSONBytes(entry.getValue().getDataMap()));
                    }
                } catch (Exception e) {
                    LogCore.RpcUtils.warn("Load service data failed, name = {}, error = {}", entry.getKey(), e.getMessage(), e);
                } finally {
                    entry.getValue().setInitEnd(true);
                }
            }), "select * from `server_data` where `server_id` = ? and `name` = ?", RpcNodeManager.getRpcServerId(), entry.getKey());
        }
    }

    /**
     * ServiceManager save db
     */
    public static void save() {
        for (Map.Entry<String, BaseService> entry : services.entrySet()) {
            entry.getValue().save();

            DbManager.getDbService().executeAsync("update `server_data` set `data` = ? where `server_id` = ? and `name` = ?",
                    JSON.toJSONBytes(entry.getValue().getDataMap()), RpcNodeManager.getRpcServerId(), entry.getKey());
        }
    }

    /**
     * ServiceManager save db (同步)
     */
    public static void saveSync() {
        for (Map.Entry<String, BaseService> entry : services.entrySet()) {
            entry.getValue().save();

            DbManager.getDbService().execute("update `server_data` set `data` = ? where `server_id` = ? and `name` = ?",
                    JSON.toJSONBytes(entry.getValue().getDataMap()), RpcNodeManager.getRpcServerId(), entry.getKey());
        }
    }

    /**
     * ServiceManager 主心跳
     */
    public static void pulse() {
        long cur = System.currentTimeMillis();

        if (lastPulsePer100MsTime + 100L <= cur) {
            lastPulsePer100MsTime = cur;
            for (Map.Entry<String, BaseService> entry : services.entrySet()) {
                entry.getValue().pulsePer100Ms();
            }
        }

        if (lastPulsePerSecTime + 1000L <= cur) {
            lastPulsePerSecTime = cur;
            for (Map.Entry<String, BaseService> entry : services.entrySet()) {
                entry.getValue().pulsePerSec();
            }
        }

        if (lastPulsePer5SecTime + 5 * 1000L <= cur) {
            lastPulsePer5SecTime = cur;
            for (Map.Entry<String, BaseService> entry : services.entrySet()) {
                entry.getValue().pulsePer5Sec();
            }
        }

        if (lastPulsePerMinTime + 60 * 1000L <= cur) {
            lastPulsePerMinTime = cur;
            save();
            for (Map.Entry<String, BaseService> entry : services.entrySet()) {
                entry.getValue().pulsePerMin();
            }
        }

        for (Map.Entry<String, BaseService> entry : services.entrySet()) {
            entry.getValue().pulse();
        }
    }
}

