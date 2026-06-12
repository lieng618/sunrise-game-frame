package org.sunrise.game.game.modules;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import lombok.Getter;
import lombok.Setter;
import org.sunrise.game.game.annotation.HumanModule;
import org.sunrise.game.game.human.HumanObject;
import org.sunrise.game.game.human.HumanObjectManager;
import org.sunrise.game.game.logic.mail.MailData;
import org.sunrise.game.genProto.gen.MailProto;
import org.sunrise.game.genProto.gen.TopicProto;
import org.sunrise.game.genRpc.gen.CallEnum;
import org.sunrise.game.rpc.function.ErrorType;
import org.sunrise.game.rpc.function.RpcFunction;

import java.util.List;
import java.util.Map;

/**
 * 邮件模块
 */
@HumanModule
@Getter
@Setter
public class MailModule extends BaseModule {

    public MailModule(String humanId) {
        super(humanId);
    }

    @Override
    public void sendToClient() {
        sendMailList();
    }

    /**
     * 发送邮件列表给客户端
     */
    public void sendMailList() {
        RpcFunction rpcFunction = RpcFunction.newInstance();
        rpcFunction.call(CallEnum.GlobalMailService_getPlayerMails, getHumanId());
        rpcFunction.listenResult(rpcResult -> {
            String humanId = (String) rpcResult.getContext("humanId");
            HumanObject humanObj = HumanObjectManager.getHumanObject(humanId);
            if (humanObj == null) return;
            if (rpcResult.getResult() != ErrorType.SUCCESS) {
                return;
            }
            byte[] protoData = (byte[]) rpcResult.getData("protoData");
            humanObj.sendMsg(TopicProto.TOPIC.TOPIC_TYPE_MAIL_VALUE,
                    MailProto.FROM_SERVER.S2C_GetMailList_VALUE, protoData);
        }, Map.of("humanId", getHumanId()));
    }

    /**
     * 读取邮件（标记为已读）
     */
    public void readMail(long mailId) {
        RpcFunction rpcFunction = RpcFunction.newInstance();
        rpcFunction.call(CallEnum.GlobalMailService_readMail, getHumanId(), mailId);
        rpcFunction.listenResult(rpcResult -> {
            if (getHuman() == null) {
                return;
            }
            if (rpcResult.getResult() != ErrorType.SUCCESS) {
                return;
            }
            Boolean success = (Boolean) rpcResult.getData("success");
            if (success != null && success) {
                notifyMailUpdate(mailId, 1);
            }
        });
    }

    /**
     * 领取邮件附件
     */
    public void receiveMailAttachment(long mailId) {
        RpcFunction rpcFunction = RpcFunction.newInstance();
        rpcFunction.call(CallEnum.GlobalMailService_receiveMailAttachment, getHumanId(), mailId);
        rpcFunction.listenResult(rpcResult -> {
            if (getHuman() == null) {
                return;
            }
            if (rpcResult.getResult() != ErrorType.SUCCESS) {
                return;
            }
            Boolean success = (Boolean) rpcResult.getData("success");
            if (success != null && success) {
                // 添加物品
                String attachmentsJson = (String) rpcResult.getData("attachmentsJson");
                if (attachmentsJson != null && !attachmentsJson.isEmpty()) {
                    List<MailData.MailAttachment> attachments = JSON.parseObject(
                        attachmentsJson,
                        new TypeReference<List<MailData.MailAttachment>>() {}
                    );
                    ItemModule itemModule = getHuman().getModule(ItemModule.class);
                    if (itemModule != null) {
                        for (MailData.MailAttachment attachment : attachments) {
                            itemModule.addItem(attachment.getItemId(), attachment.getCount(), true);
                        }
                    }
                }
                notifyMailUpdate(mailId, 2);
            }
        });
    }

    /**
     * 删除邮件
     * 未领取时无法删除
     */
    public void deleteMail(long mailId) {
        RpcFunction rpcFunction = RpcFunction.newInstance();
        rpcFunction.call(CallEnum.GlobalMailService_deleteMail, getHumanId(), mailId);
        rpcFunction.listenResult(rpcResult -> {
            if (getHuman() == null) {
                return;
            }
            if (rpcResult.getResult() != ErrorType.SUCCESS) {
                return;
            }
            Boolean success = (Boolean) rpcResult.getData("success");
            if (success != null && success) {
                notifyMailUpdate(mailId, 3);
            }
        });
    }

    /**
     * 通知邮件更新
     */
    private void notifyMailUpdate(long mailId, int status) {
        MailProto.MS2C_MailUpdate.Builder builder = MailProto.MS2C_MailUpdate.newBuilder();
        builder.setMailId(mailId);
        builder.setStatus(status);
        getHuman().sendMsg(TopicProto.TOPIC.TOPIC_TYPE_MAIL_VALUE,
                MailProto.FROM_SERVER.S2C_MailUpdate_VALUE, builder);
    }
}
