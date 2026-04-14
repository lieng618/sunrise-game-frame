package org.sunrise.game.gmback.server.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import io.javalin.http.Context;
import lombok.Getter;
import lombok.Setter;
import org.sunrise.game.genRpc.gen.CallEnum;
import org.sunrise.game.rpc.function.RpcFunction;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
@Getter
@Setter
public abstract class BaseController {
    private boolean isInitEnd = false; //是否初始化完成
    private Map<String, String> dataMap = new HashMap<>(); //每个模块要存储的数据

    /**
     * 加载数据
     */
    public void load() {

    }

    /**
     * 保存数据
     */
    public void save() {
    }

    public <T> void getDbData(String key, TypeReference<T> typeReference, Consumer<T> func) {
        String value = dataMap.get(key);
        if (value == null) {
            return;
        }
        func.accept(JSON.parseObject(value, typeReference));
    }

    public void putDbData(String key, Object value) {
        dataMap.put(key, JSON.toJSONString(value));
    }

    protected void success(Context ctx) {
        success(ctx, null, "Success");
    }

    protected void success(Context ctx, Object data) {
        success(ctx, data, "Success");
    }

    protected void success(Context ctx, Object data, String msg) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("msg", msg);
        if (data != null) {
            result.put("data", data);
        }
        ctx.json(result);
    }

    protected void fail(Context ctx, int code, String msg) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", code);
        result.put("msg", msg);
        ctx.json(result);
    }

    protected <T> T getBodyParam(Context ctx, String key, Class<T> clazz) {
        Map body = ctx.bodyAsClass(Map.class);
        Object val = body.get(key);
        if (val == null) return null;
        return clazz.cast(val);
    }

    protected void sendMessageToAllGame(String operation, Map<String, Object> extraData) {
        RpcFunction.newInstance(RpcFunction.RpcCallType.SendAll).call(CallEnum.GameRecvGmBackService_recvMessage, "operation", operation, "data", JSON.toJSONString(extraData));
    }
}