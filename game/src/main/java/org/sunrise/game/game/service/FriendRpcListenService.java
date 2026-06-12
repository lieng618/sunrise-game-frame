package org.sunrise.game.game.service;

import org.sunrise.game.game.human.HumanObject;
import org.sunrise.game.game.human.HumanObjectManager;
import org.sunrise.game.game.modules.FriendModule;
import org.sunrise.game.rpc.annotation.RpcMethod;
import org.sunrise.game.rpc.annotation.RpcService;
import org.sunrise.game.rpc.service.BaseService;

/**
 * 好友RPC监听服务
 */
@RpcService
public class FriendRpcListenService extends BaseService {
    public FriendRpcListenService(String nodeId) {
        super(nodeId);
    }

    @RpcMethod
    public void onNewFriendRequest(String targetHumanId) {
        HumanObject human = HumanObjectManager.getHumanObject(targetHumanId);
        if (human != null) {
            FriendModule friendModule = human.getModule(FriendModule.class);
            if (friendModule != null) {
                friendModule.notifyNewFriendRequest();
            }
        }
    }

    @RpcMethod
    public void onFriendAdded(String humanId1, String humanId2) {
        HumanObject human = HumanObjectManager.getHumanObject(humanId1);
        if (human != null) {
            FriendModule friendModule = human.getModule(FriendModule.class);
            if (friendModule != null) {
                friendModule.notifyFriendAdded(humanId2);
            }
        }
    }

    @RpcMethod
    public void onFriendDeleted(String humanId1, String humanId2) {
        HumanObject human = HumanObjectManager.getHumanObject(humanId1);
        if (human != null) {
            FriendModule friendModule = human.getModule(FriendModule.class);
            if (friendModule != null) {
                friendModule.notifyFriendDeleted(humanId2);
            }
        }
    }
}
