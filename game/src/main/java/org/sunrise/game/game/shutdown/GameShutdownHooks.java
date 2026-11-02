package org.sunrise.game.game.shutdown;

import org.sunrise.game.core.client.BaseClientManager;
import org.sunrise.game.core.server.BaseServerManager;
import org.sunrise.game.db.DbManager;
import org.sunrise.game.external.server.ExternalServer;
import org.sunrise.game.game.logic.system.GameSystemUtils;
import org.sunrise.game.game.service.GameMasterService;
import org.sunrise.game.gmback.server.controller.ControllerManager;
import org.sunrise.game.graceful.OnShutdown;
import org.sunrise.game.graceful.ShutdownPhase;
import org.sunrise.game.rpc.function.RpcManager;
import org.sunrise.game.rpc.service.ServiceManager;

/**
 * 应用层停机入口。完整顺序只在这里表达一次，底层组件只提供可执行的关闭方法。
 *
 * <p>业务保存必须保留在 {@link ShutdownPhase#FINAL_BUSINESS_STATE}。该阶段由
 * {@link org.sunrise.game.graceful.GracefulShutdown} 投递到原 RPC
 * DispatchThread，保证不会与普通消息处理并发修改玩家状态。</p>
 */
public final class GameShutdownHooks {

    private GameShutdownHooks() {
    }

    /**
     * 先停止 Controller 自己的异步生产者，再冻结网络消息入口。
     * 此时不停止业务 DispatchThread，冻结前已经入队的消息仍可处理。
     */
    @OnShutdown(
            phase = ShutdownPhase.STOP_INPUT,
            timeoutMs = 20_000,
            name = "stop-input")
    public static void stopInput() {
        ControllerManager.prepareShutdown();
        BaseServerManager.prepareShutdown();
        BaseClientManager.prepareShutdown();
    }

    /**
     * 整个最终保存过程只占用一次业务 DispatchThread barrier。
     */
    @OnShutdown(
            phase = ShutdownPhase.FINAL_BUSINESS_STATE,
            timeoutMs = 90_000,
            name = "final-business-state")
    public static void finalizeBusinessState() {
        // 先清理无法在限定时间内返回的 RPC；回调可能再次投递业务事件，
        // 因此必须在后续业务队列排空中处理。
        RpcManager.cleanupPendingCalls();

        // DB 回调可能继续投递异步事件，先让业务队列与 DB 回调稳定下来。
        GameMasterService.prepareFinalSnapshot(30_000);

        // 以下保存全部是同步写库，并按“玩家 -> 服务 -> 系统 -> Controller”
        // 的固定顺序执行。它们共用当前业务线程和同一个最终快照时间点。
        GameSystemUtils.saveAllHumansSync();
        ServiceManager.saveSync();
        GameSystemUtils.saveSync();
        ControllerManager.saveSync();

        // 最终快照后再确认一次 DB；有残留说明仍有线程在冻结后制造写入，
        // 此时直接失败，不能假装停机成功。
        if (!DbManager.awaitIdle(15_000)) {
            throw new IllegalStateException(
                    "Async DB tasks were still pending after the final snapshot");
        }
    }

    /**
     * 最终快照和 DB 写入确认完成后，才关闭对外连接。
     * 服务端自身的 onStop 会释放租约，因此这一步必须早于 CLOSE_DB。
     */
    @OnShutdown(
            phase = ShutdownPhase.CLOSE_IO,
            timeoutMs = 45_000,
            name = "close-io")
    public static void closeIo() {
        ExternalServer.shutdownInstance();
        BaseClientManager.shutdownAll();
        BaseServerManager.shutdownAll();
    }

    /** DB 必须是最后一个关闭的资源，供前面的租约释放和同步保存继续使用。 */
    @OnShutdown(
            phase = ShutdownPhase.CLOSE_DB,
            timeoutMs = 45_000,
            name = "close-db")
    public static void closeDb() {
        DbManager.close();
    }
}
