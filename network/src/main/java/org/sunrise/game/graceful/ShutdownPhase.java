package org.sunrise.game.graceful;

/**
 * 固定的停机阶段。阶段之间是强顺序关系，避免各模块依赖任意数字约定。
 *
 * <p>阶段按枚举声明顺序执行：</p>
 * <pre>
 * DRAIN_RPC -> STOP_INPUT -> FINAL_BUSINESS_STATE -> CLOSE_IO -> CLOSE_DB
 * </pre>
 *
 * <p>只有 {@link #FINAL_BUSINESS_STATE} 会进入业务 DispatchThread。其余阶段
 * 用于等待、冻结入口或关闭资源，不能在业务状态仍可能被修改时提前关闭依赖。</p>
 */
public enum ShutdownPhase {
    /** 等待已经在途的 RPC 调用结束，业务线程仍可继续处理回调。 */
    DRAIN_RPC(false),

    /** 停止接收新请求并冻结业务入口。 */
    STOP_INPUT(false),

    /**
     * 在业务 DispatchThread 中排空队列、保存最终状态并确认 DB 写入。
     *
     * <p>玩家、Service、System、Controller 的最终快照都放在这一个阶段，
     * 避免相邻快照之间重新开放业务入口或引入新的线程执行者。</p>
     */
    FINAL_BUSINESS_STATE(true),

    /** 关闭外部监听、客户端连接和服务端监听。 */
    CLOSE_IO(false),

    /** 最后关闭 DB executor 和连接池。 */
    CLOSE_DB(false);

    /** 是否必须由业务 DispatchThread 执行。 */
    private final boolean runsOnBusinessThread;

    ShutdownPhase(boolean runsOnBusinessThread) {
        this.runsOnBusinessThread = runsOnBusinessThread;
    }

    /** 返回当前阶段是否要求业务线程亲和性。 */
    public boolean usesBusinessThread() {
        return runsOnBusinessThread;
    }
}
