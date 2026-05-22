package org.sunrise.game.gmback.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import org.sunrise.game.gmback.server.AdminServer;
import org.sunrise.game.gmback.server.controller.AnnouncementController;
import org.sunrise.game.gmback.server.controller.BanHumanController;
import org.sunrise.game.gmback.server.controller.CdkController;
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
import java.util.Map;

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
            Map<String, Object> dataMap = JSON.parseObject(data, new TypeReference<Map<String, Object>>() {
            });
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
    private void handleNodeData(Map<String, Object> data) {
        String nodeId = (String) data.get("nodeId");
        int serverId = (int) data.get("serverId");
        String ip = (String) data.get("ip");
        int port = (int) data.get("port");
        int online = (int) data.get("online");
        String type = (String) data.get("type");
        List<String> humanIds = JSON.parseObject((String) data.get("humanIds"), new TypeReference<List<String>>() {});
        ControllerManager.getController(NodeController.class).updateNodeData(nodeId, serverId, ip, port, online, type);
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
    }

    private void handleCdkRedeem(Map<String, Object> dataMap) {
        String code = (String) dataMap.get("code");
        if (code != null && !code.isEmpty()) {
            ControllerManager.getController(CdkController.class).onRedeem(code);
        }
    }
}
