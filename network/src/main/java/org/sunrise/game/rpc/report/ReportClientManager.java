package org.sunrise.game.rpc.report;

import java.util.HashMap;
import java.util.Map;

public class ReportClientManager {
    private static final Map<String, ReportClient> reportClients = new HashMap<>();

    public static ReportClient createReportClient(String nodeId, int serverId, String ip, int port) {
        var reportClient = new ReportClient(nodeId, serverId, ip, port);
        reportClients.put(nodeId, reportClient);
        return reportClient;
    }

    public static ReportClient getReportClient(String nodeId) {
        return reportClients.get(nodeId);
    }
}
