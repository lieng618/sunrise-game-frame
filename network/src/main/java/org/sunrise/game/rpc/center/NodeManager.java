package org.sunrise.game.rpc.center;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import org.sunrise.game.core.client.BaseClientManager;
import org.sunrise.game.core.message.BaseMessage;
import org.sunrise.game.core.server.BaseServerManager;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.rpc.policy.RpcConnectPolicy;
import org.sunrise.game.rpc.report.ReportClient;
import org.sunrise.game.rpc.report.ReportClientManager;
import org.sunrise.game.utils.IdGenerator;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NodeManager {
    public static final int INTERVAL_SIMPLE = 3000; // 上报间隔-发送简易包证明活跃

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
            nodeData.setNodeType((String) data.get("type"));
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

            // 新节点应对旧节点建连时，才把旧节点信息同步给新节点
            if (RpcConnectPolicy.shouldConnect(newnodeData.getNodeType(), nodeData.getNodeType())) {
                BaseMessage messageToNew = new BaseMessage(CenterServerManager.getCenterServerNodeId());
                messageToNew.setToNodeId(newnodeData.getNodeId());
                messageToNew.setMsg(JSON.toJSONString(buildNodeReportData(nodeData)));
                BaseServerManager.sendToClient(messageToNew);
            }

            // 旧节点应对新节点建连时，才把新节点信息同步给旧节点
            if (RpcConnectPolicy.shouldConnect(nodeData.getNodeType(), newnodeData.getNodeType())) {
                BaseMessage messageToOld = new BaseMessage(CenterServerManager.getCenterServerNodeId());
                messageToOld.setToNodeId(nodeData.getNodeId());
                messageToOld.setMsg(JSON.toJSONString(buildNodeReportData(newnodeData)));
                BaseServerManager.sendToClient(messageToOld);
            }
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
        message.setMsg(JSON.toJSONString(buildNodeReportData(reportClient)));
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

    private static Map<String, Object> buildNodeReportData(NodeData nodeData) {
        Map<String, Object> data = new HashMap<>();
        data.put("ip", nodeData.getIp());
        data.put("port", nodeData.getPort());
        data.put("id", nodeData.getServerId());
        if (nodeData.getNodeType() != null && !nodeData.getNodeType().isEmpty()) {
            data.put("type", nodeData.getNodeType());
        }
        return data;
    }

    private static Map<String, Object> buildNodeReportData(ReportClient reportClient) {
        Map<String, Object> data = new HashMap<>();
        data.put("ip", reportClient.getClientIp());
        data.put("port", reportClient.getClientPort());
        data.put("id", reportClient.getServerId());
        if (reportClient.getNodeType() != null && !reportClient.getNodeType().isEmpty()) {
            data.put("type", reportClient.getNodeType());
        }
        return data;
    }
}
