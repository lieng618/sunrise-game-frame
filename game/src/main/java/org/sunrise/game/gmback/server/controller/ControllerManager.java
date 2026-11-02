package org.sunrise.game.gmback.server.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import org.sunrise.game.db.DbManager;
import org.sunrise.game.db.entity.EntityServerData;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.thread.DispatchThread;
import org.sunrise.game.utils.Utils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

public class ControllerManager {
    private static long initStartTime;
    private static final Map<String, BaseController> controllers = new HashMap<>();
    private static long lastSaveDbTime = System.currentTimeMillis(); //上次保存数据的时间
    public static ConcurrentLinkedQueue<Runnable> asyncQueue = new ConcurrentLinkedQueue<>();
    private static final Object asyncQueueLock = new Object();
    private static volatile DispatchThread saveThread;
    private static volatile boolean stopping = false;

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
        controllers.put(CheatUserController.class.getSimpleName(), new CheatUserController());
        controllers.put(AnnouncementController.class.getSimpleName(), new AnnouncementController());
        controllers.put(CdkController.class.getSimpleName(), new CdkController());
        controllers.put(HotswapController.class.getSimpleName(), new HotswapController());
        load();
        waitInitEnd();
        startSaveDbPulse();
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

            Utils.sleep(30);
        }
    }

    /**
     * ControllerManager load db
     */
    public static void load() {
        for (Map.Entry<String, BaseController> entry : controllers.entrySet()) {
            DbManager.getDbService().queryGetOneByParamsAsync((result -> {
                if (result != null) {
                    EntityServerData serverData = new EntityServerData(result);
                    entry.getValue().setDataMap(JSON.parseObject(serverData.getData(), new TypeReference<Map<String, String>>() {}.getType()));
                    entry.getValue().load();
                } else {
                    DbManager.getDbService().executeAsync("insert into `server_data` (server_id,name,data) values (?,?,?)", 0, entry.getKey(), JSON.toJSONBytes(entry.getValue().getDataMap()));
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

            DbManager.getDbService().executeAsync("update `server_data` set `data` = ? where `server_id` = ? and `name` = ?",
                    JSON.toJSONBytes(entry.getValue().getDataMap()), 0, entry.getKey());
        }
    }

    /**
     * ControllerManager save db (同步)
     */
    public static void saveSync() {
        for (Map.Entry<String, BaseController> entry : controllers.entrySet()) {
            entry.getValue().save();

            DbManager.getDbService().execute("update `server_data` set `data` = ? where `server_id` = ? and `name` = ?",
                    JSON.toJSONBytes(entry.getValue().getDataMap()), 0, entry.getKey());
        }
    }

    public static synchronized void startSaveDbPulse() {
        if (saveThread != null) {
            return;
        }
        saveThread = new DispatchThread(ControllerManager::pulse, "ControllerManagerPulse");
        saveThread.setInterval(1000);
        saveThread.start();
    }

    public static void addAsyncEvent(Runnable task) {
        if (task == null) {
            return;
        }
        synchronized (asyncQueueLock) {
            if (stopping) {
                LogCore.GmBackServer.warn("Controller async event rejected during shutdown");
                return;
            }
            asyncQueue.add(task);
        }
    }

    public static void pulse() {
        while (!asyncQueue.isEmpty()) {
            Runnable task = asyncQueue.poll();
            if (task == null) {
                continue;
            }
            task.run();
        }

        if (stopping) {
            return;
        }

        long cur = System.currentTimeMillis();
        // 每分钟保存一次数据库
        if (lastSaveDbTime + 60 * 1000L <= cur) {
            lastSaveDbTime = cur;
            save();
        }
    }

    /**
     * 冻结 Controller 异步入口，处理已入队事件后停止独立 pulse 线程。
     * 最终 Controller 快照随后由 RPC DispatchThread 串行执行。
     */
    public static void prepareShutdown() {
        DispatchThread thread;
        CountDownLatch drained = new CountDownLatch(1);
        synchronized (asyncQueueLock) {
            stopping = true;
            thread = saveThread;
            if (thread == null) {
                return;
            }
            asyncQueue.add(drained::countDown);
        }

        try {
            if (!drained.await(5, TimeUnit.SECONDS)) {
                thread.shutdown();
                throw new IllegalStateException("Controller async queue did not drain within 5s");
            }
        } catch (InterruptedException e) {
            thread.shutdown();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while draining Controller async queue", e);
        }

        thread.shutdown();
        if (!thread.awaitTermination(5_000)) {
            throw new IllegalStateException("ControllerManagerPulse did not terminate");
        }
    }
}
