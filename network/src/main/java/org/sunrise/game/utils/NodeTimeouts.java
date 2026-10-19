package org.sunrise.game.utils;

/**
 * 节点存活判定相关的时间参数，集中放在这里，避免各处的魔法值不一致。
 */
public final class NodeTimeouts {

    /**
     * 租约续租间隔（秒）。与 ServiceManager 的 5 秒心跳保持一致。
     */
    public static final int LEASE_RENEW_INTERVAL_SECONDS = 5;

    /**
     * 租约超时（秒）。允许连续错过 5 次续租后视为节点已失效。
     */
    public static final int LEASE_TIMEOUT_SECONDS = LEASE_RENEW_INTERVAL_SECONDS * 6;

    /**
     * 对外服地址上报间隔（秒）。
     */
    public static final int EXTERNAL_REPORT_INTERVAL_SECONDS = 5;

    /**
     * 对外服地址过期时间（毫秒）。连续 4 个上报周期没有数据就从地址表移除。
     */
    public static final long EXTERNAL_ADDRESS_STALE_TIMEOUT_MS =
            4L * EXTERNAL_REPORT_INTERVAL_SECONDS * 1000L;

    /**
     * GM 节点列表上报间隔（秒）。
     */
    public static final int GM_NODE_REPORT_INTERVAL_SECONDS = 60;

    /**
     * GM 节点列表过期时间（毫秒）。连续 3 个上报周期没有数据就从列表移除。
     */
    public static final long GM_NODE_LIST_STALE_TIMEOUT_MS =
            3L * GM_NODE_REPORT_INTERVAL_SECONDS * 1000L;

    private NodeTimeouts() {
    }
}
