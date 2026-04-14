package org.sunrise.game.rpc.report;

import io.netty.channel.ChannelHandlerContext;
import org.sunrise.game.core.client.BaseClientHandler;
import org.sunrise.game.rpc.center.NodeManager;

public class ReportClientHandler extends BaseClientHandler {

    public ReportClientHandler(String nodeId) {
        super(nodeId);
    }

    /**
     * 连接到中心服后，上报自身数据
     */
    @Override
    public void onConnectSuccess() {
        super.onConnectSuccess();
        NodeManager.reportFull(getNodeId());
    }

    /**
     * 与中心服断开，需要重连
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        // 启动重连
        ReportClient reportClient = ReportClientManager.getReportClient(getNodeId());
        if (reportClient != null && !reportClient.getConnectToCenter().isShutdown()) {
            reportClient.reConnectMaster();
        }
    }
}
