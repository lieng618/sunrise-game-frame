package org.sunrise.game.http.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import lombok.Data;
import org.sunrise.game.config.ConfigReader;
import org.sunrise.game.http.server.HttpServer;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.rpc.annotation.RpcMethod;
import org.sunrise.game.rpc.annotation.RpcService;
import org.sunrise.game.rpc.service.BaseService;
import org.sunrise.game.utils.NodeTimeouts;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RpcService
public class HttpRecvMessageService extends BaseService {
    @Data
    public class ExternalRemoteData {
        private long time = 0L;
        private String ip;
        private int serverId;
        private int tcpPort;
        private int wsPort;
        private int kcpPort;
        private boolean tcpEnabled;
        private boolean wsEnabled;
        private boolean kcpEnabled;
    }

    private static final Map<Integer, ExternalRemoteData> remoteStatus = new ConcurrentHashMap<>();
    private final HttpServer httpServer;

    public HttpRecvMessageService(String nodeId) {
        super(nodeId);
        httpServer = new HttpServer(ConfigReader.getInt("http.port", 8090));
        httpServer.start();
    }

    @Override
    public void load() {
        getDbData("authUsers", new TypeReference<Map<String, HttpServer.AuthUser>>() {
        }, value -> {
            if (value != null) {
                httpServer.setAuthUsers(value);
            }
        });
    }

    @Override
    public void save() {
        putDbData("authUsers", httpServer.getAuthUsers());
    }

    @RpcMethod
    public void updateExternalRemoteData(int serverId, String host, int tcpPort, int wsPort, int kcpPort,
                                         boolean tcpEnabled, boolean wsEnabled, boolean kcpEnabled) {
        ExternalRemoteData externalRemoteData = new ExternalRemoteData();
        externalRemoteData.serverId = serverId;
        externalRemoteData.ip = host;
        externalRemoteData.tcpPort = tcpPort;
        externalRemoteData.wsPort = wsPort;
        externalRemoteData.kcpPort = kcpPort;
        externalRemoteData.tcpEnabled = tcpEnabled;
        externalRemoteData.wsEnabled = wsEnabled;
        externalRemoteData.kcpEnabled = kcpEnabled;
        externalRemoteData.time = System.currentTimeMillis();
        remoteStatus.put(serverId, externalRemoteData);
        // 立即生效，不再等下一轮 5 秒心跳，避免重启换端口后继续下发旧地址
        applyRemoteData(externalRemoteData);
    }

    @RpcMethod
    public void setExternalServerStatus(boolean open) {
        httpServer.setServerOpen(open);
    }

    @RpcMethod
    public void setWhitelist(String uids) {
        List<String> uidList = JSON.parseObject(uids, new TypeReference<List<String>>() {
        });
        httpServer.setWhitelist(uidList);
    }

    @RpcMethod
    public void setAnnouncements(String announcements) {
        List<Map<String, Object>> announcementList = JSON.parseObject(announcements, new TypeReference<List<Map<String, Object>>>() {
        });
        httpServer.setAnnouncements(announcementList);
    }

    @Override
    public void pulsePer5Sec() {
        super.pulsePer5Sec();
        long now = System.currentTimeMillis();
        for (Map.Entry<Integer, ExternalRemoteData> entry : remoteStatus.entrySet()) {
            ExternalRemoteData remoteData = entry.getValue();
            if (now - remoteData.getTime() > NodeTimeouts.EXTERNAL_ADDRESS_STALE_TIMEOUT_MS) {
                if (remoteStatus.remove(entry.getKey(), remoteData)) {
                    removeExternal(entry.getKey());
                    LogCore.HttpServer.warn("External node {} report timeout ({} ms), address removed",
                            entry.getKey(), now - remoteData.getTime());
                }
            } else {
                applyRemoteData(remoteData);
            }
        }
    }

    /**
     * 把上报的端口写入对客户端下发的地址表。三种协议各自使用上报的端口。
     */
    private void applyRemoteData(ExternalRemoteData remoteData) {
        int serverId = remoteData.getServerId();
        putAddress("tcp", serverId, remoteData.getIp(), remoteData.getTcpPort(), remoteData.isTcpEnabled());
        putAddress("websocket", serverId, remoteData.getIp(), remoteData.getWsPort(), remoteData.isWsEnabled());
        putAddress("kcp", serverId, remoteData.getIp(), remoteData.getKcpPort(), remoteData.isKcpEnabled());
    }

    private void putAddress(String protocol, int serverId, String ip, int port, boolean enabled) {
        ConcurrentHashMap<Integer, String> addresses = httpServer.getExternalAddress()
                .computeIfAbsent(protocol, k -> new ConcurrentHashMap<>());
        if (enabled && port > 0) {
            addresses.put(serverId, ip + ":" + port);
        } else {
            addresses.remove(serverId);
        }
    }

    private void removeExternal(int serverId) {
        for (ConcurrentHashMap<Integer, String> addresses : httpServer.getExternalAddress().values()) {
            addresses.remove(serverId);
        }
        // 清掉 uid -> serverId 的粘性映射，避免客户端继续被分到已经下线的节点
        httpServer.getUidExternals().entrySet().removeIf(entry -> entry.getValue() != null && entry.getValue() == serverId);
    }
}
