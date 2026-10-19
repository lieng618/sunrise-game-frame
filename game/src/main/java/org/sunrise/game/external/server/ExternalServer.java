package org.sunrise.game.external.server;

import ch.qos.logback.classic.Level;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import kcp.ChannelConfig;
import kcp.KcpServer;
import lombok.Data;
import lombok.Getter;
import org.sunrise.game.config.ConfigReader;
import org.sunrise.game.core.coder.SocketMessageDecoder;
import org.sunrise.game.core.coder.SocketMessageEncoder;
import org.sunrise.game.core.coder.WebSocketMessageCodec;
import org.sunrise.game.db.DbManager;
import org.sunrise.game.graceful.OnShutdown;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.rpc.node.RpcNodeManager;
import org.sunrise.game.utils.NodeTimeouts;
import org.sunrise.game.utils.Utils;

import java.net.DatagramSocket;
import java.net.InetSocketAddress;

@Data
public class ExternalServer {
    private String externalHost;
    private int tcpPort;
    private int wsPort;
    private int kcpPort;
    private KcpServer kcpServer;
    private boolean tcpEnabled;
    private boolean wsEnabled;
    private boolean kcpEnabled;

    private Channel tcpChannel;
    private Channel wsChannel;
    private volatile boolean isShutdown;

    @Getter
    private static ExternalServer instance;

    // 持有 EventLoopGroup 引用，供停机时关闭
    private EventLoopGroup tcpBossGroup;
    private EventLoopGroup tcpWorkerGroup;
    private EventLoopGroup wsBossGroup;
    private EventLoopGroup wsWorkerGroup;

    public void start() {
        instance = this;
        int serverId = RpcNodeManager.getRpcServerId();
        boolean claimed = false;
        try {
            tcpEnabled = ConfigReader.getBoolean("external.listen.tcp", true);
            wsEnabled = ConfigReader.getBoolean("external.listen.ws", false);
            kcpEnabled = ConfigReader.getBoolean("external.listen.kcp", false);
            if (!tcpEnabled && !wsEnabled && !kcpEnabled) {
                throw new IllegalArgumentException("at least one of external.listen.tcp/ws/kcp must be enabled");
            }

            registerServerRow(serverId);
            claimed = claimServerId(serverId);
            if (!claimed) {
                LogCore.ExternalServer.error("Server StartUp Failed, name = { ExternalServer }, serverId = {}, reason = {}",
                        serverId, "server running (lease is still alive)");
                System.exit(-1);
            }
            bindPorts(Utils.getListenIpAddress());
        } catch (Exception e) {
            if (claimed) {
                releaseServer(serverId);
            }
            LogCore.ExternalServer.error("Server StartUp Failed, name = { ExternalServer }, serverId = {}, reason = {}", serverId, e.getLocalizedMessage());
            System.exit(-1);
        }
    }

    /**
     * 注册表只记录 serverId 和机器 ip；端口由本进程探测后上报。
     */
    private void registerServerRow(int serverId) {
        DbManager.getDbService().execute(
                "insert into `external_system` (`id`, `ip`) values (?, ?) on duplicate key update `id` = `id`",
                serverId, Utils.getLocalIpAddress());
    }

    /**
     * 原子抢占 serverId：只有 status=0 或租约已过期时才能更新成功，
     * 避免多个实例同时通过“先查询再判断”的竞态。
     */
    private boolean claimServerId(int serverId) {
        return DbManager.getDbService().executeUpdate(
                "update `external_system` set `status` = 1, `ip` = ?, `update_time` = now() "
                        + "where `id` = ? and (`status` = 0 or `update_time` <= now() - interval "
                        + NodeTimeouts.LEASE_TIMEOUT_SECONDS + " second)",
                Utils.getLocalIpAddress(), serverId) > 0;
    }

    private void releaseServer(int serverId) {
        try {
            DbManager.getDbService().execute(
                    "update `external_system` set `status` = 0 where `id` = ?", serverId);
        } catch (Exception e) {
            LogCore.ExternalServer.error("ExternalServer release status error", e);
        }
    }

    /**
     * 解析端口配置并完成绑定。
     * 候选块固定为 3 个连续端口 (tcp, ws, kcp) = (p, p+1, p+2)：
     * - 配置了 external.listen.port 时只尝试该端口；
     * - 否则从 external.listen.base-port 开始向上探测，最多扫描 external.listen.port-range 个端口。
     */
    private void bindPorts(String ip) {
        int basePort = ConfigReader.getInt("external.listen.base-port", 10000);
        int range = ConfigReader.getInt("external.listen.port-range", 100);
        if (range < 3) {
            throw new IllegalArgumentException("external.listen.port-range must be >= 3, current = " + range);
        }
        if (basePort < 1 || basePort + range - 1 > 65535) {
            throw new IllegalArgumentException("invalid external.listen.base-port/port-range: base = " + basePort + ", range = " + range);
        }

        int port = ConfigReader.getInt("external.listen.port", 0);
        if (port > 0) {
            if (port > 65533) {
                throw new IllegalArgumentException(
                        "external.listen.port must be <= 65533 (ws = port + 1, kcp = port + 2), current = " + port);
            }
            if (!tryBindBlock(ip, port)) {
                throw new IllegalStateException("configured external.listen.port " + port + " is not available (needs tcp="
                        + port + ", ws=" + (port + 1) + ", kcp=" + (port + 2) + ")");
            }
            return;
        }

        int lastPort = basePort + range - 1;
        for (int candidate = basePort; candidate + 2 <= lastPort; candidate++) {
            if (tryBindBlock(ip, candidate)) {
                if (candidate != basePort) {
                    LogCore.ExternalServer.warn("External base port {} is unavailable, allocated port block {}-{} instead",
                            basePort, candidate, candidate + 2);
                }
                return;
            }
        }
        throw new IllegalStateException("no free port block in [" + basePort + ", " + lastPort
                + "], adjust external.listen.base-port or external.listen.port-range");
    }

    /**
     * 尝试绑定一个连续端口块；任意一个端口失败都会回滚已经绑定的端口。
     */
    private boolean tryBindBlock(String ip, int tcpCandidate) {
        int wsCandidate = tcpCandidate + 1;
        int kcpCandidate = tcpCandidate + 2;

        if (kcpEnabled && !isUdpPortFree(ip, kcpCandidate)) {
            return false;
        }
        if (tcpEnabled) {
            tcpChannel = bindTcp(ip, tcpCandidate);
            if (tcpChannel == null) {
                return false;
            }
        }
        if (wsEnabled) {
            wsChannel = bindWs(ip, wsCandidate);
            if (wsChannel == null) {
                closeChannel(tcpChannel);
                tcpChannel = null;
                return false;
            }
        }
        if (kcpEnabled) {
            try {
                if (!startKcpListen(ip, kcpCandidate)) {
                    closeChannel(tcpChannel);
                    closeChannel(wsChannel);
                    tcpChannel = null;
                    wsChannel = null;
                    return false;
                }
            } catch (Exception e) {
                LogCore.ExternalServer.warn("External KCP bind failed on {}:{}, cause={}", ip, kcpCandidate, e.getMessage());
                closeChannel(tcpChannel);
                closeChannel(wsChannel);
                tcpChannel = null;
                wsChannel = null;
                if (kcpServer != null) {
                    kcpServer.stop();
                    kcpServer = null;
                }
                return false;
            }
        }

        tcpPort = tcpEnabled ? tcpCandidate : 0;
        wsPort = wsEnabled ? wsCandidate : 0;
        kcpPort = kcpEnabled ? kcpCandidate : 0;
        LogCore.ExternalServer.info("External server bound, tcp = {}, ws = {}, kcp = {}", tcpPort, wsPort, kcpPort);
        return true;
    }

    /**
     * KCP 的 KcpServer.init 是异步绑定且不会抛异常，这里先用 DatagramSocket 探测 UDP 端口，
     * 绑定完成后再次反查，避免实际没起来却仍上报端口。
     */
    private static boolean isUdpPortFree(String ip, int port) {
        try (DatagramSocket socket = new DatagramSocket(null)) {
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress(ip, port));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean waitForUdpBound(String ip, int port, long timeoutMs) {
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        do {
            if (!isUdpPortFree(ip, port)) {
                return true;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        } while (System.nanoTime() < deadline);
        return false;
    }

    /**
     * 优雅停机入口（静态方法，供 {@code @OnShutdown} 注解扫描）。
     */
    @OnShutdown(order = 70, timeoutMs = 15_000)
    public static void shutdownInstance() {
        ExternalServer es = getInstance();
        if (es != null) es.onStop();
    }

    /**
     * 优雅停机：关闭所有监听端口的 EventLoopGroup 并更新 DB 状态为离线。
     */
    public void onStop() {
        if (isShutdown) return;
        isShutdown = true;
        LogCore.ExternalServer.info("ExternalServer onStop begin, tcp={}, ws={}, kcp={}", tcpPort, wsPort, kcpPort);

        // 更新状态为离线
        int serverId = RpcNodeManager.getRpcServerId();
        releaseServer(serverId);

        // 关闭 KCP
        if (kcpServer != null) {
            try {
                kcpServer.stop();
            } catch (Exception e) {
                LogCore.ExternalServer.error("ExternalServer kcpServer stop error", e);
            }
        }

        closeChannel(tcpChannel);
        closeChannel(wsChannel);

        // 关闭 TCP EventLoopGroup
        shutdownGroup(tcpBossGroup, "tcpBoss");
        shutdownGroup(tcpWorkerGroup, "tcpWorker");
        // 关闭 WS EventLoopGroup
        shutdownGroup(wsBossGroup, "wsBoss");
        shutdownGroup(wsWorkerGroup, "wsWorker");

        LogCore.ExternalServer.info("ExternalServer onStop end, tcp={}, ws={}, kcp={}", tcpPort, wsPort, kcpPort);
    }

    private void shutdownGroup(EventLoopGroup group, String name) {
        if (group != null) {
            try {
                group.shutdownGracefully().awaitUninterruptibly(5000);
                LogCore.ExternalServer.info("ExternalServer shutdown {} group done", name);
            } catch (Exception e) {
                LogCore.ExternalServer.error("ExternalServer shutdown {} group error", name, e);
            }
        }
    }

    private void ensureTcpGroups() {
        if (this.tcpBossGroup == null) {
            this.tcpBossGroup = Utils.createEventLoopGroup(1);
            this.tcpWorkerGroup = Utils.createEventLoopGroup(0);
        }
    }

    private void ensureWsGroups() {
        if (this.wsBossGroup == null) {
            this.wsBossGroup = Utils.createEventLoopGroup(1);
            this.wsWorkerGroup = Utils.createEventLoopGroup(0);
        }
    }

    private static void closeChannel(Channel channel) {
        if (channel != null) {
            channel.close();
        }
    }

    /**
     * 同步绑定 TCP；返回 null 表示端口不可用，由上层继续探测下一个候选。
     */
    private Channel bindTcp(String ip, int port) {
        ensureTcpGroups();
        ServerBootstrap b = new ServerBootstrap();
        b.group(this.tcpBossGroup, this.tcpWorkerGroup)
                .channel(Utils.getServerChannelClass())
                .option(ChannelOption.SO_BACKLOG, 10240) //内核为这个套接字排队的最大连接数
                .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT) //使用内存池
                .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT) //使用内存池
                .childOption(ChannelOption.TCP_NODELAY, true) //禁用 Nagle 算法
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(128 * 1024, 256 * 1024)) // 控制输出流量
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new SocketMessageEncoder());
                        ch.pipeline().addLast(new SocketMessageDecoder());
                        ch.pipeline().addLast(new ExternalServerHandler());
                    }
                });

        ChannelFuture future = b.bind(ip, port).awaitUninterruptibly();
        if (!future.isSuccess()) {
            LogCore.ExternalServer.warn("External TCP bind failed on {}:{}, cause={}", ip, port,
                    future.cause() == null ? "unknown" : future.cause().toString());
            return null;
        }
        LogCore.ExternalServer.info("External Tcp Server start, port = { {} }", port);
        return future.channel();
    }

    /**
     * 同步绑定 WebSocket；返回 null 表示端口不可用。
     */
    private Channel bindWs(String ip, int port) {
        ensureWsGroups();
        ServerBootstrap b = new ServerBootstrap();
        b.group(this.wsBossGroup, this.wsWorkerGroup)
                .channel(Utils.getServerChannelClass())
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new HttpServerCodec()); // HTTP 协议解析，用于握手阶段
                        ch.pipeline().addLast(new HttpObjectAggregator(65536)); // HTTP 协议解析，用于握手阶段
                        ch.pipeline().addLast(new WebSocketServerCompressionHandler(8 * 1024 * 1024)); // WebSocket 数据压缩扩展
                        ch.pipeline().addLast(new WebSocketMessageCodec());
                        ch.pipeline().addLast(new WebSocketServerProtocolHandler("/", null, true, 128 * 1024 * 1024)); // WebSocket 握手、控制帧处理
                        ch.pipeline().addLast(new ExternalServerHandler());
                    }
                });

        ChannelFuture future = b.bind(ip, port).awaitUninterruptibly();
        if (!future.isSuccess()) {
            LogCore.ExternalServer.warn("External WS bind failed on {}:{}, cause={}", ip, port,
                    future.cause() == null ? "unknown" : future.cause().toString());
            return null;
        }
        LogCore.ExternalServer.info("External Ws Server start, port = { {} }", port);
        return future.channel();
    }

    private boolean startKcpListen(String ip, int port) {
        LogCore.setLogLevel("kcp", Level.WARN);
        ChannelConfig channelConfig = new ChannelConfig();
        channelConfig.nodelay(true, 10, 2, true);
        channelConfig.setSndwnd(256);
        channelConfig.setRcvwnd(256);
        channelConfig.setMtu(1400);
        channelConfig.setTimeoutMillis(30000);
        channelConfig.setUseConvChannel(true);
        channelConfig.setCrc32Check(true);

        KcpServerHandler kcpServerHandler = new KcpServerHandler();
        kcpServer = new KcpServer();
        kcpServer.init(kcpServerHandler, channelConfig, port);
        if (!waitForUdpBound(ip, port, 1000)) {
            LogCore.ExternalServer.warn("External Kcp Server failed to bind, port = { {} }", port);
            kcpServer.stop();
            kcpServer = null;
            return false;
        }

        LogCore.ExternalServer.info("External Kcp Server start, port = { {} }", port);
        return true;
    }

    /**
     * 续租：刷新 update_time。必须显式写 update_time，否则字段值没变化时 MySQL 不会触发
     */
    public void renewLease() {
        if (isShutdown) {
            return;
        }
        DbManager.getDbService().executeAsync(
                "update `external_system` set `update_time` = now() where `id` = ? and `status` = 1",
                RpcNodeManager.getRpcServerId());
    }
}
