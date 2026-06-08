package org.sunrise.game.gmback.server.controller;

import com.alibaba.fastjson2.TypeReference;
import io.javalin.http.Context;
import org.sunrise.game.genRpc.gen.CallEnum;
import org.sunrise.game.rpc.function.RpcFunction;

import java.util.HashMap;
import java.util.Map;

public class ServerStatusController extends BaseController {

    private boolean serverOpen = true;

    @Override
    public void load() {
        getDbData("serverOpen", new TypeReference<Boolean>() {
        }, value -> {
            if (value != null) {
                serverOpen = value;
            }
        });
    }

    @Override
    public void save() {
        putDbData("serverOpen", serverOpen);
    }

    public void getStatus(Context ctx) {
        Map<String, Object> data = new HashMap<>();
        data.put("open", serverOpen);
        success(ctx, data);
    }

    public void setStatus(Context ctx) {
        Boolean open = getBodyParam(ctx, "open", Boolean.class);
        if (open == null) {
            fail(ctx, 400, "Missing params");
            return;
        }

        serverOpen = open;
        RpcFunction.newInstance().call(CallEnum.HttpRecvMessageService_setExternalServerStatus, serverOpen);

        String action = serverOpen ? "开启服务器" : "关闭服务器";
        ControllerManager.getController(OperationLogController.class).recordLog(
                ctx, OperationLogController.OperationType.SERVER_STATUS, action);

        success(ctx, null, action + "成功");
    }
}
