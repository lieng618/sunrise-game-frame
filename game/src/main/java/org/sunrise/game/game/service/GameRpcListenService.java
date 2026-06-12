package org.sunrise.game.game.service;

import org.sunrise.game.game.human.HumanObject;
import org.sunrise.game.game.human.HumanObjectManager;
import org.sunrise.game.rpc.annotation.RpcMethod;
import org.sunrise.game.rpc.annotation.RpcService;
import org.sunrise.game.rpc.service.BaseService;

@RpcService
public class GameRpcListenService extends BaseService {
    public GameRpcListenService(String nodeId) {
        super(nodeId);
    }

    /**
     * 转发proto消息给本服所有玩家
     */
    @RpcMethod
    public void sendToAllHuman(int packetType, int packetId, byte[] protoData) {
        for (HumanObject humanObject : HumanObjectManager.getHumanObjects()) {
            humanObject.sendMsg(packetType, packetId, protoData);
        }
    }

    /**
     * 转发proto消息给指定玩家
     */
    @RpcMethod
    public void sendToHuman(String humanId, int packetType, int packetId, byte[] protoData) {
        HumanObject humanObject = HumanObjectManager.getHumanObject(humanId);
        if (humanObject != null) {
            humanObject.sendMsg(packetType, packetId, protoData);
        }
    }
}