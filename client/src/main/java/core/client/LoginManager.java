package core.client;

import com.alibaba.fastjson2.JSON;
import com.google.protobuf.ByteString;
import org.sunrise.game.config.ConfigReader;
import org.sunrise.game.genProto.gen.LoginProto;
import org.sunrise.game.genProto.gen.TopicProto;
import org.sunrise.game.log.LogCore;

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
        return CompletableFuture.supplyAsync(() -> {
            try {
                statusCallback.accept("正在获取服务器信息...");

                // 获取对外服务器地址
                ServerAddress address = getExternalServerAddress(uid).get(10, TimeUnit.SECONDS);
                if (address == null) {
                    statusCallback.accept("获取服务器地址失败");
                    return false;
                }

                // 设置连接ID并连接
                if ("kcp".equals(ConfigReader.getProp().getProperty("client.socket", "tcp"))) {
                    int conv = getKcpConv();
                    address.conv = conv;
                    if (client instanceof KcpClientImpl kcpClient) {
                        kcpClient.setConv(conv);
                    }
                }
                client.connect(address.host, address.port);

                // 等待连接成功（带超时）
                statusCallback.accept("等待连接建立...");
                if (!waitForConnection(client, 10, TimeUnit.SECONDS)) {
                    statusCallback.accept("连接超时");
                    return false;
                }
                
                // 发送登录消息
                statusCallback.accept("发送登录请求...");
                ByteString loginData = LoginProto.MC2S_Login.newBuilder()
                        .setUid(uid)
                        .build()
                        .toByteString();
                client.sendMsg(TopicProto.TOPIC.TOPIC_TYPE_LOGIN,
                        LoginProto.FROM_CLIENT.C2S_Login_VALUE, 
                        loginData);
                client.setLoginStartTime(System.currentTimeMillis());
                
                statusCallback.accept("登录请求已发送");
                return true;
            } catch (Exception e) {
                LogCore.Client.error("Login failed", e);
                statusCallback.accept("登录失败: " + e.getMessage());
                return false;
            }
        });
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
                LogCore.Client.info("Request external_address success, address={}", address);
                
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
            if (client.isConnectSuccess()) {
                return true;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
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
