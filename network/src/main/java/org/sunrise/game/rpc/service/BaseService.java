package org.sunrise.game.rpc.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import lombok.Getter;
import lombok.Setter;
import org.sunrise.game.db.DbService;
import org.sunrise.game.rpc.function.Call;
import org.sunrise.game.rpc.function.CallUtils;
import org.sunrise.game.rpc.function.ErrorType;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

@Getter
@Setter
public class BaseService {

    private final String nodeId;

    private boolean isInitEnd = false; //是否初始化完成
    private Map<String, String> dataMap = new HashMap<>(); //每个模块要存储的数据

    public BaseService(String nodeId) {
        this.nodeId = nodeId;
    }

    public void init() {
    }

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

    /**
     * 心跳
     */
    public void pulse() {
    }

    /**
     * 心跳 每秒
     */
    public void pulsePerSec() {
    }

    /**
     * 心跳 每5秒
     */
    public void pulsePer5Sec() {
    }

    /**
     * 心跳 每分钟
     */
    public void pulsePerMin() {
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

    public DbService getDbService() {
        return ServiceManager.dbService;
    }

    public void returns(Object ... params) {
        CallUtils.returns(nodeId, ErrorType.SUCCESS, params);
    }

    public void returns(Call from, Object ... params) {
        CallUtils.returns(from, nodeId, ErrorType.SUCCESS, params);
    }
}
