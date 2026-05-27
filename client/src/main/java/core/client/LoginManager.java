package core.client;

import com.alibaba.fastjson2.JSON;
import com.google.protobuf.ByteString;
import org.sunrise.game.config.ConfigReader;
import org.sunrise.game.genProto.gen.LoginProto;
import org.sunrise.game.genProto.gen.TopicProto;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.utils.Utils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 登录管理器 - 统一管理登录相关功能
 */
public class LoginManager {

    private static String httpUrl = "127.0.0.1:8090";
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 初始化登录管理器
     */
    public static void initialize(String httpUrl) {
        LoginManager.httpUrl = httpUrl;
    }


    /**
     * 登录（带状态回调）
     */
    public static void login(SocketClient client, Consumer<String> statusCallback) {
        try {
            Boolean success = loginAsync(client, client.getUid(), statusCallback).get();
            if (!success) {
                throw new RuntimeException("Login failed");
            }
        } catch (Exception e) {
            throw new RuntimeException("Login exception", e);
        }
    }

    /**
     * 异步登录
     */
    private static CompletableFuture<Boolean> loginAsync(SocketClient client, String uid,
                                                         Consumer<String> statusCallback) {
        return fetchExternalAddressOnly(uid)
                .thenCompose(address -> {
                    if (address == null) {
                        statusCallback.accept("获取服务器地址失败");
                        return CompletableFuture.completedFuture(false);
                    }
                    return connectAndSendLogin(client, address, statusCallback);
                });
    }

    /**
     * 阶段一：仅通过 HTTP 获取对外服地址（server_status + external_address，KCP 时含 conv）
     */
    public static CompletableFuture<ExternalAddress> fetchExternalAddressOnly(String uid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!checkServerStatus(uid)) {
                    LogCore.Client.warn("Server not open for uid={}", uid);
                    return null;
                }
                ServerAddress address = getExternalServerAddress(uid).get(10, TimeUnit.SECONDS);
                if (address == null) {
                    return null;
                }
                if ("kcp".equals(ConfigReader.getProp().getProperty("client.socket", "tcp"))) {
                    address.conv = getKcpConv();
                }
                return new ExternalAddress(address.host, address.port, address.conv);
            } catch (Exception e) {
                LogCore.Client.error("fetchExternalAddressOnly failed, uid={}", uid, e);
                return null;
            }
        });
    }

    /**
     * 阶段二：连接对外服并发送 C2S_Login
     */
    public static CompletableFuture<Boolean> connectAndSendLogin(SocketClient client,
                                                                 ExternalAddress address,
                                                                 Consumer<String> statusCallback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if ("kcp".equals(ConfigReader.getProp().getProperty("client.socket", "tcp"))
                        && address.conv > 0
                        && client instanceof KcpClientImpl kcpClient) {
                    kcpClient.setConv(address.conv);
                }
                statusCallback.accept("正在连接对外服...");
                client.connect(address.host, address.port);

                statusCallback.accept("等待连接建立...");
                if (!waitForConnection(client, 10, TimeUnit.SECONDS)) {
                    statusCallback.accept("连接超时");
                    return false;
                }

                statusCallback.accept("发送登录请求...");
                ByteString loginData = LoginProto.MC2S_Login.newBuilder()
                        .setUid(client.getUid())
                        .build()
                        .toByteString();
                if (!client.sendMsg(TopicProto.TOPIC.TOPIC_TYPE_LOGIN,
                        LoginProto.FROM_CLIENT.C2S_Login_VALUE,
                        loginData)) {
                    statusCallback.accept("发送登录请求失败");
                    LogCore.Client.error("send C2S_Login failed, uid={}, channelActive={}",
                            client.getUid(), client.isActive());
                    return false;
                }
                client.setLoginStartTime(System.currentTimeMillis());

                statusCallback.accept("登录请求已发送");
                return true;
            } catch (Exception e) {
                LogCore.Client.error("connectAndSendLogin failed, uid={}", client.getUid(), e);
                statusCallback.accept("连接登录失败: " + e.getMessage());
                return false;
            }
        });
    }

    /** 对外服地址（HTTP 阶段结果） */
    public static class ExternalAddress {
        public final String host;
        public final int port;
        public final int conv;

        public ExternalAddress(String host, int port, int conv) {
            this.host = host;
            this.port = port;
            this.conv = conv;
        }
    }


    /**
     * 检查服务器是否开放
     */
    private static boolean checkServerStatus(String uid) {
        String url = "http://" + httpUrl + "/server_status?uid=" + uid;
        String response = httpRequest(url);
        if (response == null || response.isEmpty()) {
            LogCore.Client.error("Failed to check server status");
            return false;
        }
        try {
            return JSON.parseObject(response).getBooleanValue("open");
        } catch (Exception e) {
            LogCore.Client.error("Failed to parse server_status response: {}", response, e);
            return false;
        }
    }

    /**
     * 异步获取对外服务器地址
     */
    private static CompletableFuture<ServerAddress> getExternalServerAddress(String uid) {
        return CompletableFuture.supplyAsync(() -> {
            String socketType = ConfigReader.getProp().getProperty("client.socket", "tcp");
            String url = "http://" + httpUrl + "/external_address?type=" + socketType + "&" + "uid=" + uid;
            String response = httpRequest(url);
            if (response == null || response.isEmpty()) {
                return null;
            }
            
            try {
                String address = JSON.parseObject(response).getString("address");
                if (!isStressUid(uid)) {
                    LogCore.Client.info("Request external_address success, address={}", address);
                }
                
                if (address == null || address.isEmpty()) {
                    return null;
                }
                
                String[] parts = address.split(":");
                if (parts.length != 2) {
                    LogCore.Client.error("Invalid address format: {}", address);
                    return null;
                }
                
                return new ServerAddress(parts[0], Integer.parseInt(parts[1]));
            } catch (Exception e) {
                LogCore.Client.error("Failed to parse external_address response: {}", response, e);
                return null;
            }
        });
    }

    /**
     * 从服务端获取唯一的kcp conv id
     */
    private static int getKcpConv() {
        String url = "http://" + httpUrl + "/kcp_conv";
        String response = httpRequest(url);
        if (response == null || response.isEmpty()) {
            return 0;
        }
        try {
            return JSON.parseObject(response).getIntValue("conv");
        } catch (Exception e) {
            LogCore.Client.error("Failed to parse kcp_conv response: {}", response, e);
            return 0;
        }
    }

    /**
     * 执行HTTP请求
     */
    private static String httpRequest(String url) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                LogCore.Client.error("HTTP request failed: url={}, code={}", url, response.statusCode());
                return null;
            }
            return response.body();
        } catch (Exception e) {
            LogCore.Client.error("HTTP request exception: url={}", url, e);
            return null;
        }
    }

    /**
     * 等待连接建立
     */
    private static boolean waitForConnection(SocketClient client, long timeout, TimeUnit unit) {
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        while (System.currentTimeMillis() < deadline) {
            if (client.isConnectSuccess() && client.isActive()) {
                return true;
            }
            Utils.sleep(50);
        }
        return false;
    }

    private static boolean isStressUid(String uid) {
        return uid != null && uid.startsWith("stress");
    }

    /**
     * 服务器地址数据类
     */
    private static class ServerAddress {
        final String host;
        final int port;
        int conv;

        ServerAddress(String host, int port) {
            this.host = host;
            this.port = port;
            this.conv = 0;
        }
    }
}
