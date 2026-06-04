package org.sunrise.game.game.logic.system;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

@Getter
@Setter
public class BaseSystem {
    private boolean isInitEnd = false; //是否初始化完成
    private Map<String, String> dataMap = new HashMap<>(); //每个模块要存储的数据

    /**
     * 初始化
     */
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
     * 心跳 每100毫秒
     */
    public void pulsePer100Ms() {

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
}
