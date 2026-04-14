package org.sunrise.game.game.service;

import org.sunrise.game.game.human.HumanObject;
import org.sunrise.game.game.human.HumanObjectManger;
import org.sunrise.game.genProto.gen.MailProto;
import org.sunrise.game.genProto.gen.TopicProto;
import org.sunrise.game.rpc.annotation.RpcMethod;
import org.sunrise.game.rpc.annotation.RpcService;
import org.sunrise.game.rpc.service.BaseService;

/**
 * 邮件RPC监听服务
 * 接收Global服务的通知
// */
@RpcService
public class MailRpcListenService extends BaseService {
    public MailRpcListenService(String nodeId) {
        super(nodeId);
    }

    /**
     * 收到新邮件通知（proto消息）
     */
    @RpcMethod
    public void onNewMail(String humanId, byte[] protoData) {
        HumanObject human = HumanObjectManger.getHumanObject(humanId);
        if (human != null) {
            // 直接转发proto消息给客户端
            human.sendMsg(TopicProto.TOPIC.TOPIC_TYPE_MAIL_VALUE,
                    MailProto.FROM_SERVER.S2C_NewMail_VALUE, protoData);
        }
    }
}
