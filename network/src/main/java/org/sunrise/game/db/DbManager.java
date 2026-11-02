package org.sunrise.game.db;

import org.sunrise.game.rpc.node.RpcNodeManager;

public class DbManager {

    private static DbService dbService;

    public static DbService getDbService() {
        if (dbService == null) {
            if (RpcNodeManager.getRpcNode() != null) {
                return RpcNodeManager.getRpcNode().getDbService();
            }
            dbService = new DbService();
        }
        return dbService;
    }

    private static DbService currentDbService() {
        if (dbService != null) {
            return dbService;
        }
        return RpcNodeManager.getRpcNode() == null
                ? null
                : RpcNodeManager.getRpcNode().getDbService();
    }

    /**
     * 等待当前已提交的异步 DB 任务及其回调完成。
     *
     * <p>pending 计数在 thenAccept/whenComplete 回调结束后才归零，因此返回 true
     * 表示数据库工作和回调触发的后续任务都已结束。</p>
     */
    public static boolean awaitIdle(long timeoutMs) {
        DbService service = currentDbService();
        return service == null || service.awaitAsyncTasks(timeoutMs);
    }

    /**
     * 关闭所有可能由当前进程创建的 DB 服务。必须放在停机最后一步，
     * 前面关闭网络时仍可能同步更新服务器租约或状态。
     */
    public static void close() {
        if (dbService != null) {
            dbService.close();
            dbService = null;
        }
        if (RpcNodeManager.getRpcNode() != null) {
            RpcNodeManager.getRpcNode().getDbService().close();
        }
    }

}
