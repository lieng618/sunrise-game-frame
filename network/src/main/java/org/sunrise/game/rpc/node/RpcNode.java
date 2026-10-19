package org.sunrise.game.rpc.node;

import ch.qos.logback.classic.Level;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
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
import org.sunrise.game.utils.NodeTimeouts;
import org.sunrise.game.utils.Utils;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 一个完整的rpc服务节点
 * 包含：一个rpcServer服务、若干个连接到其他rpc服务的BaseClient、一个reportClient
 * 使用时通过 RpcNodeManager.createRpcNode(serverId, nodeType) 创建（自动完成 RPC 服务注册）
 */
@Getter
@Setter
public class RpcNode {
    private final int serverId;
    private final String nodeType;
    private String ip = null; //绑定ip
    private int port; //绑定端口
    private DbService dbService = new DbService();

    private EventLoopGroup group = null;
    private Bootstrap b = null;
    private BaseServer rpcServer;
    private final Map<Integer, BaseClient> connectToOthers = new ConcurrentHashMap<>();
    private RpcClientMessageManager fromOtherMessageManager = null;

    private final Object connectLock = new Object();
    private ScheduledExecutorService leaseExecutor;

    public RpcNode(int serverId, String nodeType) {
        this.serverId = serverId;
        this.nodeType = nodeType.trim().toLowerCase();
        this.rpcServer = new BaseServer(this.getClass().getSimpleName() + serverId) {
            @Override
            public void onStart() {
                super.onStart();
                if (dbService != null) {
                    dbService.execute("update `rpc_server_system` set `status` = ?, `ip` = ?, `update_time` = now() where `id` = ?", 1, Utils.getLocalIpAddress(), serverId);
                    startLeaseHeartbeat();
                }
            }

            @Override
            public void onStop() {
                if (isStartSuccess()) {
                    stopLeaseHeartbeat();
                    try {
                        super.onStop();
                    } finally {
                        if (dbService != null) {
                            releaseServerId();
                        }
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
        EntityRpcServerSystem existing = null;
        boolean claimed = false;
        try {
            var resultSet = dbService.queryAll("select * from `rpc_server_system`");
            for (Map<String, Object> objectMap : resultSet) {
                EntityRpcServerSystem row = new EntityRpcServerSystem(objectMap);
                if (row.getId() == this.serverId) {
                    existing = row;
                }
                maxPort = Math.max(maxPort, row.getPort());
            }

            setIp(Utils.getListenIpAddress());
            if (existing != null) {
                setPort(existing.getPort());
            } else {
                setPort(maxPort == 0 ? 20000 : maxPort + 1);
                dbService.execute(
                        "insert into `rpc_server_system` (`id`, `ip`, `port`) values (?, ?, ?) on duplicate key update `id` = `id`",
                        serverId, Utils.getLocalIpAddress(), port);
            }

            claimed = claimServerId();
            if (!claimed) {
                LogCore.ServerStartUp.error("Server StartUp Failed, name = { RpcServer }, serverId = {}, reason = {}",
                        serverId, "server running (lease is still alive)");
                System.exit(-1);
            }
            if (existing != null && existing.getStatus() == 1) {
                LogCore.ServerStartUp.warn("RPC node {} has a stale running record, taking over", serverId);
            }

            rpcServer.startListen(this.ip, this.port);
            connectMaster();
        } catch (Exception e) {
            if (claimed) {
                releaseServerId();
            }
            LogCore.ServerStartUp.error("Server StartUp Failed, name = { RpcServer }, serverId = {}, reason = {}", serverId, e.getLocalizedMessage());
            System.exit(-1);
        }
    }

    /**
     * 原子抢占 serverId：只有 status=0 或租约已过期时才能更新成功。
     */
    private boolean claimServerId() {
        return dbService.executeUpdate(
                "update `rpc_server_system` set `status` = 1, `ip` = ?, `update_time` = now() "
                        + "where `id` = ? and (`status` = 0 or `update_time` <= now() - interval "
                        + NodeTimeouts.LEASE_TIMEOUT_SECONDS + " second)",
                Utils.getLocalIpAddress(), serverId) > 0;
    }

    private void releaseServerId() {
        try {
            dbService.execute("update `rpc_server_system` set `status` = 0 where `id` = ?", serverId);
        } catch (Exception e) {
            LogCore.RpcServer.error("RPC server release status failed, serverId = {}", serverId, e);
        }
    }

    /**
     * 运行期间每 5 秒刷新一次 update_time，让其他实例知道本节点还活着。
     */
    private void startLeaseHeartbeat() {
        if (leaseExecutor != null) {
            return;
        }
        leaseExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "RpcLeaseHeartbeat-" + serverId);
            thread.setDaemon(true);
            return thread;
        });
        leaseExecutor.scheduleAtFixedRate(() -> {
            try {
                dbService.execute(
                        "update `rpc_server_system` set `update_time` = now() where `id` = ? and `status` = 1", serverId);
            } catch (Exception e) {
                LogCore.RpcServer.warn("RPC lease renew failed, serverId = {}, cause = {}", serverId, e.getMessage());
            }
        }, NodeTimeouts.LEASE_RENEW_INTERVAL_SECONDS, NodeTimeouts.LEASE_RENEW_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void stopLeaseHeartbeat() {
        ScheduledExecutorService executor = leaseExecutor;
        if (executor == null) {
            return;
        }
        leaseExecutor = null;
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                LogCore.RpcServer.warn("RPC lease heartbeat did not stop within 2s, serverId = {}", serverId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void otherOffline(int id) {
        BaseClient removeClient;

        synchronized (connectLock) {
            removeClient = connectToOthers.remove(id);
            if (removeClient == null) {
                return;
            }
            String serverNodeId = removeClient.getServerNodeId();
            RpcFunction.removeNodeFromAllCallIds(serverNodeId);
        }

        BaseClientManager.remove(removeClient.getNodeId());
    }

    public void connectOther(BaseMessage message) {
        if (message.getMsg() == null) {
            return;
        }
        String msg = (String) message.getMsg();
        JSONObject data = JSON.parseObject(msg);
        if (data == null) {
            return;
        }
        Integer otherServerId = data.getInteger("id");
        String ip = data.getString("ip");
        Integer port = data.getInteger("port");
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
        connectToOther.connect(ip, port);
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
        var reportClient = ReportClientManager.createReportClient(
                rpcServer.getNodeId() + "-ReportClient",
                serverId,
                reportIp,
                port,
                nodeType);
        reportClient.getConnectToCenter().setMessageManager(new ReportClientMessageManager(reportClient.getConnectToCenter().getNodeId()));
        reportClient.connectMaster(masterIp, masterPort);
    }
}
