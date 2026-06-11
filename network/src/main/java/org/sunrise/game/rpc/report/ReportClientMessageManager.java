package org.sunrise.game.rpc.report;


import org.sunrise.game.core.message.BaseMessage;
import org.sunrise.game.core.message.ClientMessageManager;
import org.sunrise.game.core.message.MessageUtils;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.rpc.center.NodeManager;
import org.sunrise.game.rpc.node.RpcNodeManager;

/**
 * rpc上报客户端消息管理器
 * 收到中心服发来的其他节点信息，进行连接
 */
public class ReportClientMessageManager extends ClientMessageManager {

    private volatile long lastSimpleReportTime = 0;

    public ReportClientMessageManager(String nodeId) {
        super(nodeId);
    }

    @Override
    public void pulse() {
        try {
            pulseHandler();
            pulseReport();
            pulseSender();
        } catch (Exception e) {
            LogCore.ReportClient.error("DispatchThread pulse, error : ", e);
        }
    }

    @Override
    protected void pulseHandlerOne(Object data) {
        var message = MessageUtils.fromMessage((byte[]) data, BaseMessage.class);
        if (message == null) {
            return;
        }
        LogCore.BaseClient.debug("recv msg, cur NodeId = { {} }, from NodeId = { {} }, messageId = { {} }, data = { {} }", getNodeId(), message.getNodeId(), message.getMessageId(), message.getMsg());
        RpcNodeManager.getRpcNode().connectOther(message);
    }

    /**
     * 心跳上报
     */
    private void pulseReport() {
        long cur = System.currentTimeMillis();
        if (cur - lastSimpleReportTime >= NodeManager.INTERVAL_SIMPLE) {
            if (NodeManager.reportSimple(getNodeId())) {
                lastSimpleReportTime = cur;
            }
        }
    }
}
