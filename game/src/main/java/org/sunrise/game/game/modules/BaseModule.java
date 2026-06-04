package org.sunrise.game.game.modules;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import lombok.Getter;
import lombok.Setter;
import org.sunrise.game.game.human.HumanObject;
import org.sunrise.game.game.human.HumanObjectManger;
import org.sunrise.game.game.logic.attribute.AttributeProvider;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

@Getter
@Setter
public class BaseModule {
    private final String humanId; //当前模块所属的玩家id
    private Map<String, String> dataMap = new HashMap<>(); //每个模块要存储的数据

    public BaseModule(String humanId) {
        this.humanId = humanId;
    }

    /**
     * 模块初始化，首次创建新角色时调用
     * 做一些数据的初始化操作，此接口内不能发送协议数据
     * 所有数据都应在sendToClient()中发送
     */
    public void init() {

    }

    /**
     * 加载数据
     * 新角色首次登录不会调用
     * 新增的模块，老用户登录不会调用
     */
    public void load() {

    }

    /**
     * 保存数据，心跳中会定时存储
     */
    public void save() {

    }

    /**
     * db加载完成，通知客户端的数据
     */
    public void sendToClient() {

    }

    /**
     * 模块心跳
     */
    public void pulse() {

    }

    /**
     * 模块心跳 每100毫秒
     */
    public void pulsePer100Ms() {

    }

    /**
     * 模块心跳 每秒
     */
    public void pulsePerSec() {

    }

    /**
     * 每日刷新
     */
    public void dailyReset() {

    }

    /**
     * 每周刷新
     */
    public void weekReset() {

    }

    /**
     * 当前模块的属性值
     */
    public AttributeProvider getAttribute() {
        return null;
    }

    public HumanObject getHuman() {
        return HumanObjectManger.getHumanObject(humanId);
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
