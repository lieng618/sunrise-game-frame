package org.sunrise.game.http.service;

import lombok.Data;
import org.sunrise.game.config.ConfigReader;
import org.sunrise.game.http.server.HttpServer;
import org.sunrise.game.rpc.annotation.RpcMethod;
import org.sunrise.game.rpc.annotation.RpcService;
import org.sunrise.game.rpc.service.BaseService;

import java.util.HashMap;
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
    public void recvMessage(int serverId, String host, int port) {
        ExternalRemoteData externalRemoteData = remoteStatus.get(serverId);
        if (externalRemoteData == null) {
            externalRemoteData = new ExternalRemoteData();
            remoteStatus.put(serverId,  externalRemoteData);
        }
        externalRemoteData.serverId = serverId;
        externalRemoteData.ip = host;
        externalRemoteData.port = port;
        externalRemoteData.time = System.currentTimeMillis();
    }

    @Override
    public void pulsePer5Sec() {
        super.pulsePer5Sec();
        for (ExternalRemoteData remoteData : remoteStatus.values()) {
            ConcurrentHashMap<Integer, String> tcp = httpServer.getExternalAddress().computeIfAbsent("tcp", k -> new ConcurrentHashMap<>());
            tcp.put(remoteData.getServerId(), remoteData.getIp() + ":" + remoteData.getPort());

            ConcurrentHashMap<Integer, String> websocket = httpServer.getExternalAddress().computeIfAbsent("websocket", k -> new ConcurrentHashMap<>());
            websocket.put(remoteData.getServerId(), remoteData.getIp() + ":" + remoteData.getPort() + 1);
        }
    }
}
