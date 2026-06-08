package org.sunrise.game.gmback.server.controller;

import com.alibaba.fastjson2.JSON;
import io.javalin.http.Context;
import org.sunrise.game.genRpc.gen.CallEnum;
import org.sunrise.game.rpc.function.RpcFunction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GmController extends BaseController {

    public void sendMail(Context ctx) {
        String humanId = getBodyParam(ctx, "humanId", String.class);
        Integer templateId = getBodyParam(ctx, "templateId", Integer.class);
        List attachments = getBodyParam(ctx, "attachments", List.class);

        if (humanId == null || humanId.trim().isEmpty()) {
            fail(ctx, 400, "Missing params");
            return;
        }
        String attachmentsStr = JSON.toJSONString(attachments);

        if ("-1".equals(humanId.trim())) {
            RpcFunction.newInstance().call(
                    CallEnum.GlobalMailService_sendMailToAll,
                    templateId,
                    attachmentsStr,
                    "运营邮件");
            ControllerManager.getController(OperationLogController.class).recordLog(ctx, OperationLogController.OperationType.SEND_MAIL, "发送全服邮件(attachments:" + attachmentsStr + ")");

        } else {
            RpcFunction.newInstance().call(
                    CallEnum.GlobalMailService_sendMail,
                    humanId.trim(),
                    templateId,
                    attachmentsStr,
                    "运营邮件");
            ControllerManager.getController(OperationLogController.class).recordLog(ctx, OperationLogController.OperationType.SEND_MAIL, "为玩家(ID:" + humanId.trim() + ")发送邮件(attachments:" + attachmentsStr + ")");
        }

        success(ctx, null, "Send mail command sent to game server");
    }

    public void kick(Context ctx) {
        String humanId = getBodyParam(ctx, "humanId", String.class);
        if (humanId == null || humanId.trim().isEmpty()) {
            fail(ctx, 400, "Missing params");
            return;
        }
        Map<String, Object> kickData = new HashMap<>();
        kickData.put("humanId", humanId.trim());
        sendMessageToAllGame("kickHuman", kickData);
        success(ctx, null, "Kick command sent");

        ControllerManager.getController(OperationLogController.class).recordLog(ctx, OperationLogController.OperationType.KICK_PLAYER, "踢出玩家(ID:" + humanId.trim() + ")");
    }
}