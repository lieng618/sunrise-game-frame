package org.sunrise.game.game.service;

import com.alibaba.fastjson2.JSON;
import org.sunrise.game.db.DbManager;
import org.sunrise.game.game.async.AsyncEventManager;
import org.sunrise.game.game.human.HumanObject;
import org.sunrise.game.game.human.HumanObjectManager;
import org.sunrise.game.game.logic.LogicUtils;
import org.sunrise.game.game.logic.ToolsUtils;
import org.sunrise.game.game.logic.system.GameSystemUtils;
import org.sunrise.game.game.modules.PlayerUnitModule;
import org.sunrise.game.genProto.gen.TopicProto;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.rpc.annotation.RpcService;
import org.sunrise.game.rpc.service.BaseService;

@RpcService
public class GameMasterService extends BaseService {
    public GameMasterService(String nodeId) {
        super(nodeId);
    }

    @Override
    public void init() {
        super.init();
    }

    @Override
    public void pulse() {
        super.pulse();
        // 处理玩家队列中的消息
        pulseHandlerHumanMsg();
        // 处理玩家的异步回调
        pulseHandlerHumanAsyncEvent();
        // 系统心跳
        GameSystemUtils.pulse();
    }

    @Override
    public void pulsePer100Ms() {
        super.pulsePer100Ms();
        GameSystemUtils.pulsePer100Ms();
    }

    @Override
    public void pulsePerSec() {
        super.pulsePerSec();
        // 检测玩家掉线，进行数据清理
        pulseHandlerHumanClear();
        pulseHandlerDeleteHuman();
        // 玩家数据定时存库
        pulseHandlerHumanDbSave();
        // 系统心跳 每秒
        GameSystemUtils.pulsePerSec();
    }

    /**
     * 处理 rpcLock 期间积压的玩家协议
     */
    private void pulseHandlerHumanMsg() {
        for (HumanObject humanObject : HumanObjectManager.getHumanObjects()) {
            if (humanObject.getMsgQueue().isEmpty()) {
                continue;
            }
            while (!humanObject.getMsgQueue().isEmpty()) {
                if (humanObject.isRpcLock()) {
                    break;
                }
                TopicProto.MBasePacketData data = humanObject.getMsgQueue().poll();
                if (data == null) {
                    continue;
                }
                LogicUtils.handler(humanObject, data.getPacketTypeValue(), data.getPacketId(), data.getPacketData());
            }
        }
    }

    /**
     * 处理玩家的异步回调
     */
    private void pulseHandlerHumanAsyncEvent() {
        while (!AsyncEventManager.asyncQueue.isEmpty()) {
            Runnable task = AsyncEventManager.asyncQueue.poll();
            if (task == null) {
                continue;
            }
            task.run();
        }
    }

    /**
     * 检测玩家掉线，进行数据清理
     */
    private void pulseHandlerHumanClear() {
        long cur = System.currentTimeMillis();
        for (HumanObject humanObject : HumanObjectManager.getHumanObjects()) {
            if (humanObject.getLastPingTime() + 60 * 1000 < cur) {
                String humanId = humanObject.getHumanId();
                HumanObjectManager.deleteHumanQueue.add(humanId);
            }
        }
    }

    /**
     * 处理待下线玩家
     */
    private void pulseHandlerDeleteHuman() {
        for (String humanId : HumanObjectManager.deleteHumanQueue) {
            HumanObject humanObject = HumanObjectManager.getHumanObject(humanId);
            if (humanObject != null) {
                long connectId = humanObject.getConnectObject().getConnectId();
                String uid = humanObject.getConnectObject().getUid();

                DbManager.getDbService().executeAsync("update `human_info` set `role_data` = ? where `human_id` = ?", JSON.toJSONBytes(humanObject.save()), humanId);
                humanObject.getModule(PlayerUnitModule.class).leaveMap();
                humanObject.kick("kick");

                HumanObjectManager.removeConnectObject(connectId);
                HumanObjectManager.uidAccounts.remove(uid);
                HumanObjectManager.uidPlays.remove(uid);
                HumanObjectManager.humanIds.remove(connectId);
                HumanObjectManager.removeHumanObject(humanId);

                LogCore.GameServer.info("humanId = { {} }, uid = { {} }, clear data", humanId, uid);
            }
        }
        HumanObjectManager.deleteHumanQueue.clear();
    }

    /**
     * 玩家数据定时存库
     */
    private void pulseHandlerHumanDbSave() {
        long cur = System.currentTimeMillis();
        for (HumanObject humanObject : HumanObjectManager.getHumanObjects()) {
            if (humanObject.getLastSaveDbTime() + ToolsUtils.MINUTE_MILLIS < cur) {
                humanObject.setLastSaveDbTime(cur);
                DbManager.getDbService().executeAsync("update `human_info` set `role_data` = ? where `human_id` = ?", JSON.toJSONBytes(humanObject.save()),  humanObject.getHumanId());
            }
        }
    }
}
