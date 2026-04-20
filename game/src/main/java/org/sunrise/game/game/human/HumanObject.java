package org.sunrise.game.game.human;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.google.protobuf.Message;
import lombok.Data;
import org.sunrise.game.game.modules.ActivityModule;
import org.sunrise.game.game.modules.AttributeModule;
import org.sunrise.game.game.modules.BaseModule;
import org.sunrise.game.game.modules.DataModule;
import org.sunrise.game.game.modules.FriendModule;
import org.sunrise.game.game.modules.ItemModule;
import org.sunrise.game.game.modules.MailModule;
import org.sunrise.game.game.modules.MapModule;
import org.sunrise.game.game.modules.MinerModule;
import org.sunrise.game.game.modules.TaskModule;
import org.sunrise.game.genProto.gen.HumanProto;
import org.sunrise.game.genProto.gen.TopicProto;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

@Data
public class HumanObject {
    private ConnectObject connectObject;
    private String humanId;
    private int serverId; //连接的逻辑服务器id
    private Map<String, String> roleData = new HashMap<>(); //玩家数据: 模块名-json串
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
        modules.put(ItemModule.class.getSimpleName(), new ItemModule(humanId));
        modules.put(DataModule.class.getSimpleName(), new DataModule(humanId));
        modules.put(MapModule.class.getSimpleName(), new MapModule(humanId));
        modules.put(MinerModule.class.getSimpleName(), new MinerModule(humanId));
        modules.put(TaskModule.class.getSimpleName(), new TaskModule(humanId));
        modules.put(FriendModule.class.getSimpleName(), new FriendModule(humanId));
        modules.put(MailModule.class.getSimpleName(), new MailModule(humanId));
        modules.put(ActivityModule.class.getSimpleName(), new ActivityModule(humanId));
        modules.put(AttributeModule.class.getSimpleName(), new AttributeModule(humanId));
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
        roleData = JSON.parseObject(data, new TypeReference<Map<String, String>>() {
        }.getType());
        for (Map.Entry<String, String> entry : roleData.entrySet()) {
            BaseModule module = modules.get(entry.getKey());
            if (module != null) {
                module.setDataMap(JSON.parseObject(entry.getValue(), new TypeReference<HashMap<String, String>>() {
                }));
                module.load();
            }
        }
    }

    public Map<String, String> save() {
        for (Map.Entry<String, BaseModule> entry : modules.entrySet()) {
            entry.getValue().save();
            if (!entry.getValue().getDataMap().isEmpty()) {
                roleData.put(entry.getKey(), JSON.toJSONString(entry.getValue().getDataMap()));
            }
        }
        return roleData;
    }

    public void pulse() {
        for (Map.Entry<String, BaseModule> entry : modules.entrySet()) {
            entry.getValue().pulse();
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
