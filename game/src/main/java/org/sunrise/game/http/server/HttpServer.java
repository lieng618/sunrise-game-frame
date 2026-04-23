package org.sunrise.game.http.server;

import ch.qos.logback.classic.Level;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import io.javalin.Javalin;
import lombok.Data;
import org.sunrise.game.log.LogCore;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * http服务 管理所有对外服地址
 * client通过curl请求，获取对外服地址进行连接
 * 同一个client的uid，多次请求尽量分给同一个地址
 */
@Data
public class HttpServer {
    private final int port;
    private Javalin app;
    private final Random random = new Random();
    // uid-对外服id
    private final ConcurrentHashMap<String, Integer> uidExternals = new ConcurrentHashMap<>();
    // 对外服类型-<对外服id-地址>
    // "tcp" - <1001, 127.0.0.1:10000>
    // "websocket" - <1001, 127.0.0.1:10001>
    // "kcp" - <1001, 127.0.0.1:10002>
    private final ConcurrentHashMap<String, ConcurrentHashMap<Integer, String>> externalAddress = new ConcurrentHashMap<>();

    public HttpServer(int port) {
        this.port = port;
        app = Javalin.create();
        // 接口：/external_address
        // 功能：分配网关节点并返回 ip:port
        app.get("/external_address", ctx -> {
            JSONObject jsonResponse = new JSONObject();
            String type = ctx.queryParam("type");
            if (type == null) {
                return;
            }
            String uid = ctx.queryParam("uid");
            if (uid == null) {
                return;
            }
            ConcurrentHashMap<Integer, String> address = externalAddress.get(type);
            if (address == null) {
                return;
            }
            Integer oldExternalId = uidExternals.get(uid);
            if (oldExternalId != null) {
                // 有旧的地址
                String s = address.get(oldExternalId);
                if (s != null) {
                    jsonResponse.put("address", s);
                } else {
                    // 旧地址已失效
                    int randomId = random.nextInt(address.size());
                    int i = 0;
                    for (Integer key : address.keySet()) {
                        if (i == randomId) {
                            uidExternals.put(uid, key);
                            jsonResponse.put("address", address.get(key));
                        }
                        ++i;
                    }
                }
            } else {
                // 没有旧地址 随机一个新的
                int randomId = random.nextInt(address.size());
                int i = 0;
                for (Integer key : address.keySet()) {
                    if (i == randomId) {
                        uidExternals.put(uid, key);
                        jsonResponse.put("address", address.get(key));
                    }
                    ++i;
                }
            }
            ctx.result(jsonResponse.toJSONString());
        });
        // 接口：/external_address_list
        // 功能：返回所有的对外服地址
        app.get("/external_address_list", ctx -> {
            JSONArray finalAddress = new JSONArray();
            for (Map.Entry<String, ConcurrentHashMap<Integer, String>> entry : externalAddress.entrySet()) {
                for (Map.Entry<Integer, String> addressEntry : entry.getValue().entrySet()) {
                    JSONObject oneResponse = new JSONObject();
                    oneResponse.put("address", addressEntry.getValue());
                    oneResponse.put("type", entry.getKey());
                    finalAddress.add(oneResponse);
                }
            }
            ctx.result(finalAddress.toJSONString());
        });
    }

    public void start() {
        // 将 io.javalin 包下的日志级别设置为 WARN
        LogCore.setLogLevel("io.javalin", Level.WARN);
        try {
            app.start(port);
            LogCore.HttpServer.info("HttpServer started on : {}", app.jettyServer().server().getURI());
        } catch (Exception e) {
            LogCore.HttpServer.error("HttpServer start failed", e);
        }
    }
}
