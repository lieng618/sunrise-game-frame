package org.sunrise.game.gmback.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.TypeReference;
import org.sunrise.game.gmback.server.AdminServer;
import org.sunrise.game.gmback.server.controller.AnnouncementController;
import org.sunrise.game.gmback.server.controller.BanHumanController;
import org.sunrise.game.gmback.server.controller.CdkController;
import org.sunrise.game.gmback.server.controller.CheatUserController;
import org.sunrise.game.gmback.server.controller.ControllerManager;
import org.sunrise.game.gmback.server.controller.MuteHumanController;
import org.sunrise.game.gmback.server.controller.NodeController;
import org.sunrise.game.gmback.server.controller.OnlinePlayerController;
import org.sunrise.game.gmback.server.controller.WhitelistController;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.rpc.annotation.RpcMethod;
import org.sunrise.game.rpc.annotation.RpcService;
import org.sunrise.game.rpc.service.BaseService;

import java.util.List;

@RpcService
public class GmBackRecvMessageService extends BaseService {

    private AdminServer adminServer;

    public GmBackRecvMessageService(String nodeId) {
        super(nodeId);
    }

    @Override
    public void init() {
        super.init();
        adminServer = new AdminServer();
        adminServer.start();
    }

    @RpcMethod
    public void recvMessage(String operation, String data) {
        try {
            JSONObject dataMap = JSON.parseObject(data);
            if (operation != null) {
                switch (operation) {
                    case "reportNodeData":
                        handleNodeData(dataMap);
                        break;
                    case "cdkRedeem":
                        handleCdkRedeem(dataMap);
                        break;
                    default:
                        LogCore.GmBackServer.warn("Unknown GM operation: {}", operation);
                        break;
                }
            }
        } catch (Exception e) {
            LogCore.GmBackServer.warn("Unknown GM message: {}", operation);
        }
    }

    /**
     * 处理其他节点发来的节点数据
     */
    private void handleNodeData(JSONObject data) {
        String nodeId = data.getString("nodeId");
        int serverId = data.getIntValue("serverId");
        String ip = data.getString("ip");
        int port = data.getIntValue("port");
        int tcpPort = data.getIntValue("tcpPort", port);
        int wsPort = data.getIntValue("wsPort");
        int kcpPort = data.getIntValue("kcpPort");
        int online = data.getIntValue("online");
        String type = data.getString("type");
        long processId = data.getLongValue("processId");
        boolean tcpEnabled = data.getBooleanValue("tcpEnabled");
        boolean wsEnabled = data.getBooleanValue("wsEnabled");
        boolean kcpEnabled = data.getBooleanValue("kcpEnabled");
        String humanIdsJson = data.getString("humanIds");
        List<String> humanIds = humanIdsJson == null ? null
                : JSON.parseObject(humanIdsJson, new TypeReference<List<String>>() {
        });
        ControllerManager.getController(NodeController.class).updateNodeData(nodeId, serverId, ip, port,
                tcpPort, wsPort, kcpPort, online, type, processId, tcpEnabled, wsEnabled, kcpEnabled);
        if (humanIds != null) {
            ControllerManager.getController(OnlinePlayerController.class).updateHumanData(serverId, humanIds);
        }
    }

    @Override
    public void pulsePerMin() {
        super.pulsePerMin();
        // 定时下发封禁与禁言名单
        ControllerManager.getController(BanHumanController.class).broadcastBanListToGame();
        ControllerManager.getController(MuteHumanController.class).broadcastMuteListToGame();
        // 定时同步白名单到HttpServer
        ControllerManager.getController(WhitelistController.class).syncWhitelistToHttp();
        // 定时同步公告到HttpServer
        ControllerManager.getController(AnnouncementController.class).syncAnnouncementsToHttp();
        // 定时同步兑换码到GameServer
        ControllerManager.getController(CdkController.class).syncCdkToGame();
        // 定时同步金手指用户到GameServer
        ControllerManager.getController(CheatUserController.class).syncCheatUsersToGame();
    }

    private void handleCdkRedeem(JSONObject dataMap) {
        String code = dataMap.getString("code");
        if (code != null && !code.isEmpty()) {
            ControllerManager.getController(CdkController.class).onRedeem(code);
        }
    }
}
