package org.sunrise.game.gmback.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import org.sunrise.game.gmback.server.AdminServer;
import org.sunrise.game.gmback.server.controller.BanHumanController;
import org.sunrise.game.gmback.server.controller.ControllerManager;
import org.sunrise.game.gmback.server.controller.MuteHumanController;
import org.sunrise.game.gmback.server.controller.NodeController;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.rpc.annotation.RpcMethod;
import org.sunrise.game.rpc.annotation.RpcService;
import org.sunrise.game.rpc.service.BaseService;

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
                    case "reportGameData":
                        handleGameData(dataMap);
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
     * 处理game发来的数据
     */
    private void handleGameData(Map<String, Object> data) {
        String nodeId = (String) data.get("nodeId");
        int serverId = (int) data.get("serverId");
        String ip = (String) data.get("ip");
        int port = (int) data.get("port");
        int online = (int) data.get("online");
        ControllerManager.getController(NodeController.class).updateGameData(nodeId, serverId, ip, port, online);
    }

    @Override
    public void pulsePerMin() {
        super.pulsePerMin();
        // 定时下发封禁与禁言名单
        ControllerManager.getController(BanHumanController.class).broadcastBanListToGame();
        ControllerManager.getController(MuteHumanController.class).broadcastMuteListToGame();
    }
}
