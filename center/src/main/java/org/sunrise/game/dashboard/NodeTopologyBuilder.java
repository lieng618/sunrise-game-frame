package org.sunrise.game.dashboard;

import com.alibaba.fastjson2.JSONObject;
import org.sunrise.game.rpc.center.NodeData;
import org.sunrise.game.rpc.center.NodeManager;
import org.sunrise.game.rpc.policy.RpcConnectPolicy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NodeTopologyBuilder {
    private NodeTopologyBuilder() {
    }

    public static JSONObject buildSnapshot() {
        long now = System.currentTimeMillis();
        JSONObject snapshot = new JSONObject();
        snapshot.put("timestamp", now);
        snapshot.put("policy", buildPolicy());
        snapshot.put("nodes", buildNodes(now));
        snapshot.put("edges", buildEdges());
        snapshot.put("stats", buildStats(snapshot));
        return snapshot;
    }

    private static JSONObject buildPolicy() {
        JSONObject policy = new JSONObject();
        policy.put("enabled", RpcConnectPolicy.isEnabled());
        policy.put("fullMesh", !RpcConnectPolicy.isEnabled());

        JSONObject rules = new JSONObject(new LinkedHashMap<>());
        for (Map.Entry<String, Set<String>> entry : RpcConnectPolicy.getRules().entrySet()) {
            rules.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        policy.put("rules", rules);
        return policy;
    }

    private static List<JSONObject> buildNodes(long now) {
        List<JSONObject> nodes = new ArrayList<>();
        for (NodeData node : NodeManager.datasByNodeId.values()) {
            JSONObject item = new JSONObject();
            item.put("nodeId", node.getNodeId());
            item.put("serverId", node.getServerId());
            item.put("ip", node.getIp());
            item.put("port", node.getPort());
            item.put("nodeType", node.getNodeType() == null ? "" : node.getNodeType());
            item.put("reportTime", node.getReportTime());
            boolean online = !NodeManager.isNodeDead(node);
            item.put("online", online);
            item.put("lastSeenMs", node.getReportTime() > 0 ? now - node.getReportTime() : -1);
            nodes.add(item);
        }
        nodes.sort(Comparator.comparing((JSONObject a) -> a.getString("nodeType")).thenComparingInt(a -> a.getIntValue("serverId")));
        return nodes;
    }

    private static List<JSONObject> buildEdges() {
        List<NodeData> nodes = new ArrayList<>(NodeManager.datasByNodeId.values());
        List<JSONObject> edges = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (int i = 0; i < nodes.size(); i++) {
            NodeData from = nodes.get(i);
            for (int j = i + 1; j < nodes.size(); j++) {
                NodeData to = nodes.get(j);
                if (NodeManager.isNodeDead(from) || NodeManager.isNodeDead(to)) {
                    continue;
                }
                if (from.getIp() == null || to.getIp() == null) {
                    continue;
                }

                boolean forward = RpcConnectPolicy.shouldConnect(from.getNodeType(), to.getNodeType());
                boolean backward = RpcConnectPolicy.shouldConnect(to.getNodeType(), from.getNodeType());
                if (!forward && !backward) {
                    continue;
                }

                String pairKey = from.getNodeId().compareTo(to.getNodeId()) < 0
                        ? from.getNodeId() + "|" + to.getNodeId()
                        : to.getNodeId() + "|" + from.getNodeId();
                if (!seen.add(pairKey)) {
                    continue;
                }

                JSONObject edge = new JSONObject();
                if (forward && backward) {
                    edge.put("from", from.getNodeId());
                    edge.put("to", to.getNodeId());
                    edge.put("direction", "bidirectional");
                } else if (forward) {
                    edge.put("from", from.getNodeId());
                    edge.put("to", to.getNodeId());
                    edge.put("direction", "unidirectional");
                } else {
                    edge.put("from", to.getNodeId());
                    edge.put("to", from.getNodeId());
                    edge.put("direction", "unidirectional");
                }
                edge.put("fromServerId", edge.getString("from").equals(from.getNodeId()) ? from.getServerId() : to.getServerId());
                edge.put("toServerId", edge.getString("to").equals(to.getNodeId()) ? to.getServerId() : from.getServerId());
                edges.add(edge);
            }
        }
        return edges;
    }

    private static JSONObject buildStats(JSONObject snapshot) {
        List<JSONObject> nodes = snapshot.getList("nodes", JSONObject.class);
        List<JSONObject> edges = snapshot.getList("edges", JSONObject.class);

        int online = 0;
        int offline = 0;
        Map<String, Integer> typeCount = new LinkedHashMap<>();
        for (JSONObject node : nodes) {
            if (node.getBooleanValue("online")) {
                online++;
            } else {
                offline++;
            }
            String type = node.getString("nodeType");
            if (type == null || type.isEmpty()) {
                type = "unknown";
            }
            typeCount.merge(type, 1, Integer::sum);
        }

        int bidirectional = 0;
        int unidirectional = 0;
        for (JSONObject edge : edges) {
            if ("bidirectional".equals(edge.getString("direction"))) {
                bidirectional++;
            } else {
                unidirectional++;
            }
        }

        JSONObject stats = new JSONObject();
        stats.put("totalNodes", nodes.size());
        stats.put("onlineNodes", online);
        stats.put("offlineNodes", offline);
        stats.put("totalEdges", edges.size());
        stats.put("bidirectionalEdges", bidirectional);
        stats.put("unidirectionalEdges", unidirectional);
        stats.put("typeCount", typeCount);
        return stats;
    }
}
