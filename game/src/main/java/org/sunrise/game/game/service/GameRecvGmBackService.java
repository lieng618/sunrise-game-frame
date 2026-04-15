package org.sunrise.game.game.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import org.sunrise.game.game.human.HumanObjectManger;
import org.sunrise.game.game.logic.ConfigUtils;
import org.sunrise.game.genRpc.gen.CallEnum;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.rpc.annotation.RpcMethod;
import org.sunrise.game.rpc.annotation.RpcService;
import org.sunrise.game.rpc.function.RpcFunction;
import org.sunrise.game.rpc.node.RpcNodeManager;
import org.sunrise.game.rpc.service.BaseService;
import org.sunrise.game.utils.Utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 处理从gm后台收到的gm消息
 * 心跳每五秒向gm后台上报自身数据，主要用于后台页面中的展示
 */
@RpcService
public class GameRecvGmBackService extends BaseService {
    public GameRecvGmBackService(String nodeId) {
        super(nodeId);
    }

    @RpcMethod
    public void recvMessage(String operation, String data) {
        try {
            Map<String, Object> dataMap = JSON.parseObject(data, new TypeReference<Map<String, Object>>() {
            });
            if (operation != null) {
                switch (operation) {
                    case "reloadConfig":
                        ConfigUtils.load();
                        break;
                    case "kickHuman":
                        handleKickHuman(dataMap);
                        break;
                    case "banHumanList":
                        handleBanHumanList(dataMap);
                        break;
                    case "muteHumanList":
                        handleMuteHumanList(dataMap);
                        break;
                    default:
                        LogCore.GameServer.warn("Unknown GM operation: {}", operation);
                        break;
                }
            }
        } catch (Exception e) {
            LogCore.GameServer.warn("Unknown GM message: {}", operation);
        }
    }

    @Override
    public void pulsePer5Sec() {
        super.pulsePer5Sec();
        pulseReportGameData();
    }

    /**
     * 处理玩家下线GM命令
     */
    private void handleKickHuman(Map<String, Object> data) {
        String humanId = (String) data.get("humanId");
        HumanObjectManger.deleteHumanQueue.add(humanId);
        LogCore.GameServer.debug("Received KickHuman command, humanId: {}", humanId);
    }

    /**
     * 处理封禁人员名单
     */
    private void handleBanHumanList(Map<String, Object> data) {
        String humanIds = (String) data.get("humanIds");
        List<String> bans = JSON.parseObject(humanIds, new TypeReference<List<String>>() {});
        HumanObjectManger.banHumanQueue.clear();
        HumanObjectManger.banHumanQueue.addAll(bans);
        HumanObjectManger.deleteHumanQueue.addAll(bans);
        LogCore.GameServer.debug("Received BanHumanList command, humanIds: {}", humanIds);
    }

    /**
     * 处理禁言人员名单
     */
    private void handleMuteHumanList(Map<String, Object> data) {
        String humanIds = (String) data.get("humanIds");
        List<String> bans = JSON.parseObject(humanIds, new TypeReference<List<String>>() {});
        HumanObjectManger.muteHumanQueue.clear();
        HumanObjectManger.muteHumanQueue.addAll(bans);
        LogCore.GameServer.debug("Received MuteHumanList command, humanIds: {}", humanIds);
    }

    /**
     * 定时上报game自身数据
     */
    private void pulseReportGameData() {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("nodeId", RpcNodeManager.getRpcServerNodeId());
        dataMap.put("serverId", RpcNodeManager.getRpcServerId());
        dataMap.put("ip", Utils.getLocalIpAddress());
        dataMap.put("port", RpcNodeManager.getRpcNode().getPort());
        dataMap.put("online", HumanObjectManger.getOnlineCount());
        dataMap.put("humanIds", JSON.toJSONString(HumanObjectManger.getOnlineHumanIds()));
        RpcFunction.newInstance().call(CallEnum.GmBackRecvMessageService_recvMessage, "operation", "reportGameData", "data", JSON.toJSONString(dataMap));
    }
}
