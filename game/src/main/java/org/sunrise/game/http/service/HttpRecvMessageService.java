package org.sunrise.game.http.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import lombok.Data;
import org.sunrise.game.config.ConfigReader;
import org.sunrise.game.http.server.HttpServer;
import org.sunrise.game.rpc.annotation.RpcMethod;
import org.sunrise.game.rpc.annotation.RpcService;
import org.sunrise.game.rpc.service.BaseService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

@RpcService
public class HttpRecvMessageService extends BaseService {
    @Data
    public class ExternalRemoteData {
        private long time = 0L;
        private String ip;
        private int port;
        private int serverId;
        private boolean tcpEnabled;
        private boolean wsEnabled;
        private boolean kcpEnabled;
    }
    private static final Map<Integer, ExternalRemoteData> remoteStatus = new HashMap<>();
    private final HttpServer httpServer;

    public HttpRecvMessageService(String nodeId) {
        super(nodeId);
        int port = 8090;
        Properties properties = ConfigReader.getProp();
        if (properties != null) {
            port = Integer.parseInt(properties.getProperty("http.port"));
        }
        httpServer = new HttpServer(port);
        httpServer.start();
    }

    @RpcMethod
    public void updateExternalRemoteData(int serverId, String host, int port, boolean tcpEnabled, boolean wsEnabled, boolean kcpEnabled) {
        ExternalRemoteData externalRemoteData = remoteStatus.get(serverId);
        if (externalRemoteData == null) {
            externalRemoteData = new ExternalRemoteData();
            remoteStatus.put(serverId,  externalRemoteData);
        }
        externalRemoteData.serverId = serverId;
        externalRemoteData.ip = host;
        externalRemoteData.port = port;
        externalRemoteData.tcpEnabled = tcpEnabled;
        externalRemoteData.wsEnabled = wsEnabled;
        externalRemoteData.kcpEnabled = kcpEnabled;
        externalRemoteData.time = System.currentTimeMillis();
    }

    @RpcMethod
    public void setExternalServerStatus(boolean open) {
        httpServer.setServerOpen(open);
    }

    @RpcMethod
    public void setWhitelist(String uids) {
        List<String> uidList = JSON.parseObject(uids, new TypeReference<List<String>>() {});
        httpServer.setWhitelist(uidList);
    }

    @RpcMethod
    public void setAnnouncements(String announcements) {
        List<Map<String, Object>> announcementList = JSON.parseObject(announcements, new TypeReference<List<Map<String, Object>>>() {});
        httpServer.setAnnouncements(announcementList);
    }

    @Override
    public void pulsePer5Sec() {
        super.pulsePer5Sec();
        for (ExternalRemoteData remoteData : remoteStatus.values()) {
            int serverId = remoteData.getServerId();
            ConcurrentHashMap<Integer, String> tcp = httpServer.getExternalAddress().computeIfAbsent("tcp", k -> new ConcurrentHashMap<>());
            if (remoteData.isTcpEnabled()) {
                tcp.put(serverId, remoteData.getIp() + ":" + remoteData.getPort());
            } else {
                tcp.remove(serverId);
            }

            ConcurrentHashMap<Integer, String> websocket = httpServer.getExternalAddress().computeIfAbsent("websocket", k -> new ConcurrentHashMap<>());
            if (remoteData.isWsEnabled()) {
                websocket.put(serverId, remoteData.getIp() + ":" + (remoteData.getPort() + 1));
            } else {
                websocket.remove(serverId);
            }

            ConcurrentHashMap<Integer, String> kcp = httpServer.getExternalAddress().computeIfAbsent("kcp", k -> new ConcurrentHashMap<>());
            if (remoteData.isKcpEnabled()) {
                kcp.put(serverId, remoteData.getIp() + ":" + (remoteData.getPort() + 2));
            } else {
                kcp.remove(serverId);
            }
        }
    }
}
