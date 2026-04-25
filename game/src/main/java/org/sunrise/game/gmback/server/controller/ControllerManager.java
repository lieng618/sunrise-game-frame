package org.sunrise.game.gmback.server.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import org.sunrise.game.db.DbService;
import org.sunrise.game.db.entity.EntityServerData;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.thread.DispatchThread;
import org.sunrise.game.utils.Utils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ControllerManager {
    private static long initStartTime;
    public static final DbService dbService = new DbService();
    private static final Map<String, BaseController> controllers = new HashMap<>();
    private static long lastSaveDbTime = System.currentTimeMillis(); //上次保存数据的时间
    public static ConcurrentLinkedQueue<Runnable> asyncQueue = new ConcurrentLinkedQueue<>();

    public static void initController() {
        initStartTime = System.currentTimeMillis();
        controllers.put(AuthController.class.getSimpleName(), new AuthController());
        controllers.put(NodeController.class.getSimpleName(), new NodeController());
        controllers.put(GmController.class.getSimpleName(), new GmController());
        controllers.put(OperationLogController.class.getSimpleName(), new OperationLogController());
        controllers.put(UserController.class.getSimpleName(), new UserController());
        controllers.put(BanHumanController.class.getSimpleName(), new BanHumanController());
        controllers.put(MuteHumanController.class.getSimpleName(), new MuteHumanController());
        controllers.put(OnlinePlayerController.class.getSimpleName(), new OnlinePlayerController());
        controllers.put(ServerStatusController.class.getSimpleName(), new ServerStatusController());
        controllers.put(WhitelistController.class.getSimpleName(), new WhitelistController());
        load();
        waitInitEnd();
        startSaveDbPulse();
        Utils.setShutdownHook(ControllerManager::saveSync);
    }

    @SuppressWarnings("unchecked")
    public static <T extends BaseController> T getController(Class<T> clazz) {
        return (T) controllers.get(clazz.getSimpleName());
    }

    /**
     * 等待所有系统初始化完成
     */
    public static void waitInitEnd() {
        while (true) {
            boolean allInitEnd = true;
            for (Map.Entry<String, BaseController> entry : controllers.entrySet()) {
                if (!entry.getValue().isInitEnd()) {
                    allInitEnd = false;
                    break;
                }
            }

            if (allInitEnd) {
                LogCore.GmBackServer.debug("Load All Controller end, took {} ms", System.currentTimeMillis() - initStartTime);
                break;
            }

            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * ControllerManager load db
     */
    public static void load() {
        for (Map.Entry<String, BaseController> entry : controllers.entrySet()) {
            dbService.queryGetOneByParamsAsync((result -> {
                if (result != null) {
                    EntityServerData serverData = new EntityServerData(result);
                    entry.getValue().setDataMap(JSON.parseObject(serverData.getData(), new TypeReference<Map<String, String>>() {}.getType()));
                    entry.getValue().load();
                } else {
                    dbService.executeAsync("insert into `server_data` (server_id,name,data) values (?,?,?)", 0, entry.getKey(), JSON.toJSONBytes(entry.getValue().getDataMap()));
                }
                entry.getValue().setInitEnd(true);
            }), "select * from `server_data` where `server_id` = ? and `name` = ?", 0, entry.getKey());
        }
    }

    /**
     * ControllerManager save db (异步)
     */
    public static void save() {
        for (Map.Entry<String, BaseController> entry : controllers.entrySet()) {
            entry.getValue().save();

            dbService.executeAsync("update `server_data` set `data` = ? where `server_id` = ? and `name` = ?",
                    JSON.toJSONBytes(entry.getValue().getDataMap()), 0, entry.getKey());
        }
    }

    /**
     * ControllerManager save db (同步)
     */
    public static void saveSync() {
        for (Map.Entry<String, BaseController> entry : controllers.entrySet()) {
            entry.getValue().save();

            dbService.execute("update `server_data` set `data` = ? where `server_id` = ? and `name` = ?",
                    JSON.toJSONBytes(entry.getValue().getDataMap()), 0, entry.getKey());
        }
    }

    public static void startSaveDbPulse() {
        DispatchThread checkThread = new DispatchThread(ControllerManager::pulse, "ControllerManagerPulse");
        checkThread.setInterval(1000);
        checkThread.start();
    }

    public static void addAsyncEvent(Runnable task) {
        asyncQueue.add(task);
    }

    public static void pulse() {
        while (!asyncQueue.isEmpty()) {
            Runnable task = asyncQueue.poll();
            if (task == null) {
                continue;
            }
            task.run();
        }

        long cur = System.currentTimeMillis();
        // 每分钟保存一次数据库
        if (lastSaveDbTime + 60 * 1000L <= cur) {
            lastSaveDbTime = cur;
            save();
        }
    }
}
