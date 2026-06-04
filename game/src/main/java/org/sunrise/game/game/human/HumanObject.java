package org.sunrise.game.game.human;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.google.protobuf.Message;
import lombok.Data;
import org.sunrise.game.game.logic.system.GameSystemUtils;
import org.sunrise.game.game.logic.system.ResetSystem;
import org.sunrise.game.game.modules.BaseModule;
import org.sunrise.game.game.modules.DataModule;
import org.sunrise.game.game.modules.ModuleUtils;
import org.sunrise.game.genProto.gen.ChatProto;
import org.sunrise.game.genProto.gen.HumanProto;
import org.sunrise.game.genProto.gen.TopicProto;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Data
public class HumanObject {
    private ConnectObject connectObject;
    private String humanId;
    private int serverId; //连接的逻辑服务器id
    private boolean isCalling; //调用了rpc，正在等待返回
    private long lastPingTime = System.currentTimeMillis(); //上次ping发来的时间
    private long lastSaveDbTime = System.currentTimeMillis(); //上次保存数据的时间
    private final LinkedList<TopicProto.MBasePacketData> msgQueue = new LinkedList<>(); //协议消息队列
    private final Map<String, BaseModule> modules = new HashMap<>();

    public HumanObject(String humanId, int serverId, ConnectObject connectObject, boolean newHumanObj) {
        this.humanId = humanId;
        this.serverId = serverId;
        this.connectObject = connectObject;
        createModules();
        if (newHumanObj) {
            moduleInit();
        }
    }

    private void createModules() {
        List<BaseModule> moduleList = ModuleUtils.createModules(humanId);
        for (BaseModule module : moduleList) {
            modules.put(module.getClass().getSimpleName(), module);
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends BaseModule> T getModule(Class<T> clazz) {
        return (T) modules.get(clazz.getSimpleName());
    }

    /**
     * 模块初始化，首次创建新角色时调用
     */
    public void moduleInit() {
        for (Map.Entry<String, BaseModule> entry : modules.entrySet()) {
            entry.getValue().init();
        }
    }

    // 登录完成 发送基础数据
    public void sendHumanData() {
        DataModule module = getModule(DataModule.class);
        HumanProto.MS2C_HumanInfo.Builder builder = HumanProto.MS2C_HumanInfo.newBuilder();
        builder.setHumanId(humanId);
        builder.setUid(connectObject.getUid());
        builder.setAccountId(connectObject.getAccountId());
        builder.setServerId(serverId);
        builder.setLevel(module.getLevel());
        builder.setExp(module.getExp());
        builder.setFightPower(module.getFightPower());
        builder.setName(module.getName());
        builder.setHeadIcon(module.getHeadIcon());
        builder.setSex(module.getSex());
        connectObject.onSendHumanData(builder);

        // 调用所有模块的发包
        for (BaseModule baseModule : modules.values()) {
            baseModule.sendToClient();
        }

        // 所有模块数据发送完成
        connectObject.onSendHumanDataEnd();
    }

    public void load(byte[] data) {
        Map<String, Map<String, String>> saved = JSON.parseObject(data, new TypeReference<Map<String, Map<String, String>>>() {
        }.getType());
        if (saved == null) {
            return;
        }
        for (Map.Entry<String, Map<String, String>> entry : saved.entrySet()) {
            BaseModule module = modules.get(entry.getKey());
            if (module != null && entry.getValue() != null) {
                module.setDataMap(entry.getValue());
                module.load();
            }
        }
    }

    /**
     * 从 DB 加载后补偿离线期间错过的跨周、跨天刷新
     */
    public void checkAndRefresh() {
        ResetSystem resetSystem = GameSystemUtils.getSystem(ResetSystem.class);
        if (resetSystem != null) {
            resetSystem.checkAndRefreshHuman(this);
        }
    }

    public Map<String, Map<String, String>> save() {
        Map<String, Map<String, String>> saved = new HashMap<>();
        for (Map.Entry<String, BaseModule> entry : modules.entrySet()) {
            entry.getValue().save();
            if (!entry.getValue().getDataMap().isEmpty()) {
                saved.put(entry.getKey(), entry.getValue().getDataMap());
            }
        }
        return saved;
    }

    public void pulse() {
        for (Map.Entry<String, BaseModule> entry : modules.entrySet()) {
            entry.getValue().pulse();
        }
    }

    public void pulsePer100Ms() {
        for (Map.Entry<String, BaseModule> entry : modules.entrySet()) {
            entry.getValue().pulsePer100Ms();
        }
    }

    public void pulsePerSec() {
        for (Map.Entry<String, BaseModule> entry : modules.entrySet()) {
            entry.getValue().pulsePerSec();
        }
    }

    public void sendMsg(int packetType, int packetId) {
        connectObject.sendMsg(packetType, packetId);
    }

    public void sendMsg(int packetType, int packetId, Message.Builder builder) {
        connectObject.sendMsg(packetType, packetId, builder);
    }

    public void sendMsg(int packetType, int packetId, byte[] rawData) {
        connectObject.sendMsg(packetType, packetId, rawData);
    }

    public void sendTips(String tipMsg) {
        sendTips(0, tipMsg);
    }

    public void sendTips(int id) {
        sendTips(0, "");
    }

    public void sendTips(int id, String tipMsg) {
        ChatProto.MS2C_Tips.Builder builder = ChatProto.MS2C_Tips.newBuilder();
        builder.setId(id);
        builder.setMsg(tipMsg);
        sendMsg(TopicProto.TOPIC.TOPIC_TYPE_CHAT_VALUE, ChatProto.FROM_SERVER.S2C_Tips_VALUE, builder);
    }

    public void kick(String reason) {
        connectObject.kick(reason);
    }

    public boolean isRpcLock() {
        return isCalling;
    }

    /**
     * 调用rpc可选择上锁，上锁后，后续消息rpc返回后再统一处理
     * 注意rpc发送成功时再上锁
     */
    public void rpcLock() {
        isCalling = true;
    }

    public void unRpcLock() {
        isCalling = false;
    }
}
