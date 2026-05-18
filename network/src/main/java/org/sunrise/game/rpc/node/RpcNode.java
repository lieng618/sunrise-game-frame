package org.sunrise.game.rpc.node;

import ch.qos.logback.classic.Level;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.EventLoopGroup;
import lombok.Getter;
import lombok.Setter;
import org.sunrise.game.config.ConfigReader;
import org.sunrise.game.core.client.BaseClient;
import org.sunrise.game.core.client.BaseClientManager;
import org.sunrise.game.core.message.BaseMessage;
import org.sunrise.game.core.server.BaseServer;
import org.sunrise.game.core.server.BaseServerManager;
import org.sunrise.game.db.DbService;
import org.sunrise.game.db.entity.EntityRpcServerSystem;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.rpc.function.RpcFunction;
import org.sunrise.game.rpc.message.RpcClientMessageManager;
import org.sunrise.game.rpc.message.RpcServerMessageManager;
import org.sunrise.game.rpc.report.ReportClientManager;
import org.sunrise.game.rpc.report.ReportClientMessageManager;
import org.sunrise.game.utils.IdGenerator;
import org.sunrise.game.utils.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一个完整的rpc服务节点
 * 包含：一个rpcServer服务、若干个连接到其他rpc服务的BaseClient、一个reportClient
 * 使用时通过RpcNodeManager.createRpcNode()创建
 */
@Getter
@Setter
public class RpcNode {
    private final int serverId;
    private String ip = null; //绑定ip
    private int port; //绑定端口
    private DbService dbService = new DbService();

    private EventLoopGroup group = null;
    private Bootstrap b = null;
    private BaseServer rpcServer;
    private final Map<Integer, BaseClient> connectToOthers = new ConcurrentHashMap<>();
    private RpcClientMessageManager fromOtherMessageManager = null;

    public RpcNode(int serverId) {
        this.serverId = serverId;
        this.rpcServer = new BaseServer(this.getClass().getSimpleName() + serverId) {
            @Override
            public void onStart() {
                super.onStart();
                if (dbService != null) {
                    dbService.execute("update `rpc_server_system` set `status` = ?, `ip` = ? where `id` = ?", 1, Utils.getLocalIpAddress(), serverId);
                }
            }

            @Override
            public void onStop() {
                if (isStartSuccess()) {
                    super.onStop();
                    if (dbService != null) {
                        dbService.execute("update `rpc_server_system` set `status` = ? where `id` = ?", 0, serverId);
                    }
                }
            }
        };
        rpcServer.setMessageManager(new RpcServerMessageManager(rpcServer.getNodeId()));
        rpcServer.setServerHandler(r -> new RpcServerHandler(rpcServer.getNodeId()));
        BaseServerManager.register(rpcServer);
        IdGenerator.init(serverId);
        LogCore.setLogLevel("BasePulse", Level.INFO);
    }

    public String getNodeId() {
        return rpcServer.getNodeId();
    }

    /**
     * 传入指定端口，进行启动，需手动连接主服
     */
    public void start(int port) {
        setIp(Utils.getListenIpAddress());
        setPort(port);
        rpcServer.startListen(this.ip, this.port);
    }

    /**
     * 通过数据表rpc_server_system，保证当前服务的唯一性
     */
    public void start() {
        int maxPort = 0;
        List<EntityRpcServerSystem> rpcServerSystems = new ArrayList<>();
        try {
            var resultSet = dbService.queryAll("select * from `rpc_server_system`");
            for (Map<String, Object> objectMap : resultSet) {
                rpcServerSystems.add(new EntityRpcServerSystem(objectMap));
            }

            for (var rpcServerSystem : rpcServerSystems) {
                if (rpcServerSystem.getId() == this.serverId) {
                    if (rpcServerSystem.getStatus() == 1) {
                        LogCore.ServerStartUp.error("Server StartUp Failed, name = { RpcServer }, serverId = {}, reason = {}", serverId, "server running");
                        System.exit(-1);
                    } else {
                        setIp(Utils.getListenIpAddress());
                        setPort(rpcServerSystem.getPort());
                    }
                }
                maxPort = Math.max(maxPort, rpcServerSystem.getPort());
            }
            if (ip == null) {
                setIp(Utils.getListenIpAddress());
                setPort(maxPort == 0 ? 20000 : maxPort + 1);
                dbService.execute("insert into `rpc_server_system` (id,ip,port) values (?,?,?)", serverId, Utils.getLocalIpAddress(), port);
            }
            rpcServer.startListen(this.ip, this.port);
            connectMaster();
        } catch (Exception e) {
            LogCore.ServerStartUp.error("Server StartUp Failed, name = { RpcServer }, serverId = {}, reason = {}", serverId, e.getLocalizedMessage());
            System.exit(-1);
        }
    }

    public void otherOffline(int id) {
        BaseClient removeClient;

        synchronized (connectToOthers) {
            // 原子：先移除连接
            removeClient = connectToOthers.remove(id);
            if (removeClient == null) {
                return;
            }
            String serverNodeId = removeClient.getServerNodeId();
            for (List<String> callIdNodes : RpcFunction.callIdNodes.values()) {
                callIdNodes.remove(serverNodeId);
            }
        }

        BaseClientManager.remove(removeClient.getNodeId());
    }

    public void connectOther(BaseMessage message) {
        if (message.getMsg() == null) {
            return;
        }
        String msg = (String) message.getMsg();
        Map<String, Object> data = JSON.parseObject(msg, new TypeReference<Map<String, Object>>() {
        });
        Integer otherServerId = (Integer) data.get("id");
        String ip = (String) data.get("ip");
        Integer port = (Integer) data.get("port");
        if (otherServerId == null || ip == null || port == null) {
            return;
        }
        BaseClient baseClient = connectToOthers.get(otherServerId);
        if (baseClient != null) {
            return;
        }
        var connectToOther = new BaseClient(this.getClass().getSimpleName() + serverId + "-ConnectTo-RpcNode" + otherServerId, group, b) {
            @Override
            public void onStart() {
                super.onStart();
            }

            @Override
            public void onFail() {
                super.onFail();
                otherOffline(otherServerId);
            }
        };

        if (fromOtherMessageManager == null) {
            // 与rpc通信的客户端共用一个消息管理器
            fromOtherMessageManager = new RpcClientMessageManager("");
            // rpc客户端接收队列，设置为rpc服务器的接收队列，所有从远端rpc服务器发给rpc客户端的数据，也都会由当前节点的rpc服务器处理
            fromOtherMessageManager.setRecvMsgQueue(rpcServer.getMessageManager().getRecvMsgQueue());
        }
        connectToOther.setMessageManager(fromOtherMessageManager);
        connectToOther.setClientHandler(r -> new ToOtherRpcNodeHandler(connectToOther.getNodeId()));
        BaseClientManager.register(connectToOther);

        connectToOthers.put(otherServerId, connectToOther);
        connectToOther.connect((String) data.get("ip"), (Integer) data.get("port"));
    }

    public void connectMaster() {
        Properties properties = ConfigReader.getProp();
        if (properties == null) {
            return;
        }
        int id = Integer.parseInt(properties.getProperty("master.id"));
        if (id <= 0) {
            return;
        }
        String masterIp = properties.getProperty("master.address");
        int masterPort = Integer.parseInt(properties.getProperty("master.port"));
        String reportIp = properties.getProperty("report.address");

        connectMaster(masterIp, masterPort, reportIp);
    }

    public void connectMaster(String masterIp, int masterPort, String reportIp) {
        var reportClient = ReportClientManager.createReportClient(rpcServer.getNodeId() + "-ReportClient", serverId, reportIp, port);
        reportClient.getConnectToCenter().setMessageManager(new ReportClientMessageManager(reportClient.getConnectToCenter().getNodeId()));
        reportClient.connectMaster(masterIp, masterPort);
    }
}
