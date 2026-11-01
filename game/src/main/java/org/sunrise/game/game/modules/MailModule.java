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
import org.sunrise.game.log.LogCore;
import org.sunrise.game.rpc.function.ErrorType;
import org.sunrise.game.rpc.function.RpcFunction;

import java.util.LinkedHashMap;
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
        HumanObject human = getHuman();
        if (human == null) {
            return;
        }

        RpcFunction infoRpc = RpcFunction.newInstance();
        if (!infoRpc.call(CallEnum.GlobalMailService_getMailAttachmentInfo, getHumanId(), mailId)) {
            LogCore.GameServer.warn("get mail attachment info failed to send, humanId = {}, mailId = {}",
                    getHumanId(), mailId);
            return;
        }

        // 查询、入账、确认必须连续执行，期间不允许其他玩家消息修改背包或重复领取。
        human.rpcLock();
        infoRpc.listenResult(rpcResult -> {
            HumanObject currentHuman = getHuman();
            if (currentHuman == null) {
                return;
            }

            boolean keepLocked = false;
            try {
                if (rpcResult.getResult() != ErrorType.SUCCESS) {
                    return;
                }

                Boolean success = (Boolean) rpcResult.getData("success");
                if (success == null || !success) {
                    return;
                }

                String attachmentsJson = (String) rpcResult.getData("attachmentsJson");
                if (attachmentsJson == null || attachmentsJson.isEmpty()) {
                    return;
                }

                List<MailData.MailAttachment> attachments = JSON.parseObject(
                        attachmentsJson,
                        new TypeReference<List<MailData.MailAttachment>>() {}
                );
                Map<Integer, Long> itemCounts = new LinkedHashMap<>();
                for (MailData.MailAttachment attachment : attachments) {
                    itemCounts.merge(attachment.getItemId(), (long) attachment.getCount(), Long::sum);
                }

                ItemModule itemModule = currentHuman.getModule(ItemModule.class);
                if (itemModule == null) {
                    LogCore.GameServer.error("mail item module not found, humanId = {}, mailId = {}",
                            getHumanId(), mailId);
                    return;
                }

                if (!itemModule.tryAddItems(itemCounts, true)) {
                    currentHuman.sendTips("背包空间不足");
                    return;
                }

                // 物品完整入账后再推进 Global 邮件状态。
                keepLocked = confirmMailAttachment(mailId);
            } catch (RuntimeException e) {
                LogCore.GameServer.error("receive mail attachment failed, humanId = {}, mailId = {}, error = {}",
                        getHumanId(), mailId, e.getMessage());
            } finally {
                if (!keepLocked) {
                    currentHuman.unRpcLock();
                }
            }
        });
    }

    private boolean confirmMailAttachment(long mailId) {
        RpcFunction confirmRpc = RpcFunction.newInstance();
        if (!confirmRpc.call(CallEnum.GlobalMailService_confirmMailAttachment, getHumanId(), mailId)) {
            LogCore.GameServer.warn("confirm mail attachment failed to send, humanId = {}, mailId = {}",
                    getHumanId(), mailId);
            return false;
        }

        confirmRpc.listenResult(rpcResult -> {
            HumanObject human = getHuman();
            try {
                if (human != null
                        && rpcResult.getResult() == ErrorType.SUCCESS
                        && Boolean.TRUE.equals(rpcResult.getData("success"))) {
                    notifyMailUpdate(mailId, 2);
                } else {
                    LogCore.GameServer.warn("confirm mail attachment failed, humanId = {}, mailId = {}, result = {}",
                            getHumanId(), mailId, rpcResult.getResult());
                }
            } finally {
                if (human != null) {
                    human.unRpcLock();
                }
            }
        });
        return true;
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
