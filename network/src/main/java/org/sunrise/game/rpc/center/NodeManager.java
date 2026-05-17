package org.sunrise.game.rpc.center;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import org.sunrise.game.core.client.BaseClientManager;
import org.sunrise.game.core.message.BaseMessage;
import org.sunrise.game.core.server.BaseServerManager;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.rpc.report.ReportClient;
import org.sunrise.game.rpc.report.ReportClientManager;
import org.sunrise.game.utils.IdGenerator;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NodeManager {
    public static int INTERVAL_SIMPLE = 3000; // 上报间隔-发送简易包证明活跃

    // 节点id-节点信息 所有rpc节点
    public static Map<String, NodeData> datasByNodeId = new ConcurrentHashMap<>();

    public static void updateNode(BaseMessage reportMessage) {
        boolean isHaveNewNode = false;

        NodeData nodeData = datasByNodeId.get(reportMessage.getNodeId());
        if (nodeData == null) {
            nodeData = new NodeData(reportMessage.getNodeId());
        }
        if (reportMessage.getMsg() != null) {
            String msg = (String) reportMessage.getMsg();
            Map<String, Object> data = JSON.parseObject(msg, new TypeReference<Map<String, Object>>() {});
            nodeData.setIp((String) data.get("ip"));
            nodeData.setPort((Integer) data.get("port"));
            nodeData.setServerId((Integer) data.get("id"));
        }
        long oldReportTime = nodeData.getReportTime();
        nodeData.setReportTime(System.currentTimeMillis());
        if (nodeData.getReportTime() - oldReportTime >= INTERVAL_SIMPLE + 1000L) {
            isHaveNewNode = true;
        }
        datasByNodeId.put(reportMessage.getNodeId(), nodeData);

        // 有新增节点上报
        if (isHaveNewNode) {
            LogCore.CenterServer.info("recv report, data = { {} }", reportMessage);
            // 广播节点信息
            broadcastToNode(nodeData);
        }
    }

    // 判断节点是否失效
    public static boolean isNodeDead(NodeData node) {
        return node.getReportTime() + INTERVAL_SIMPLE * 2L < System.currentTimeMillis();
    }

    public static void broadcastToNode(NodeData newnodeData) {
        for (NodeData nodeData : datasByNodeId.values()) {
            // 自己的信息不用发
            if (nodeData.getNodeId().equals(newnodeData.getNodeId())) {
                continue;
            }
            // 已经失效
            if (isNodeDead(nodeData)) {
                continue;
            }
            // 上报的ip为空，无效
            if (nodeData.getIp() == null) {
                continue;
            }

            // 把旧节点信息 同步给新节点
            BaseMessage messageToNew = new BaseMessage(CenterServerManager.getCenterServerNodeId());
            messageToNew.setToNodeId(newnodeData.getNodeId());
            Map<String, Object> dataToNew = new HashMap<>();
            dataToNew.put("ip", nodeData.getIp());
            dataToNew.put("port", nodeData.getPort());
            dataToNew.put("id", nodeData.getServerId());
            messageToNew.setMsg(JSON.toJSONString(dataToNew));
            BaseServerManager.sendToClient(messageToNew);

            // 把新节点信息 同步给旧节点
            BaseMessage messageToOld = new BaseMessage(CenterServerManager.getCenterServerNodeId());
            messageToOld.setToNodeId(nodeData.getNodeId());
            Map<String, Object> dataToOld = new HashMap<>();
            dataToOld.put("ip", newnodeData.getIp());
            dataToOld.put("port", newnodeData.getPort());
            dataToOld.put("id", newnodeData.getServerId());
            messageToOld.setMsg(JSON.toJSONString(dataToOld));
            BaseServerManager.sendToClient(messageToOld);
        }
    }

    public static void reportFull(String clientNode) {
        ReportClient reportClient = ReportClientManager.getReportClient(clientNode);
        if (reportClient == null) {
            return;
        }
        String serverNodeId = reportClient.getConnectToCenter().getServerNodeId();
        if (serverNodeId == null) {
            return;
        }
        BaseMessage message = new BaseMessage();
        message.setMessageId(IdGenerator.getId());
        message.setNodeId(clientNode);

        Map<String, Object> data = new HashMap<>();
        data.put("ip", reportClient.getClientIp());
        data.put("port", reportClient.getClientPort());
        data.put("id", reportClient.getServerId());
        message.setMsg(JSON.toJSONString(data));
        LogCore.ReportClient.info("report, cur NodeId = { {} }, serverNodeId = { {} }, data = { {} }", clientNode, serverNodeId, message);
        BaseClientManager.sendToServer(message);
    }

    public static boolean reportSimple(String clientNode) {
        if (!ReportClientManager.getReportClient(clientNode).getConnectToCenter().getConnectFinish().get()) {
            return false;
        }
        BaseMessage message = new BaseMessage();
        message.setNodeId(clientNode);
        BaseClientManager.sendToServer(message);
        return true;
    }
}
