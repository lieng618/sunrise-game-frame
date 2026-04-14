package org.sunrise.game.global.service.mail;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import org.sunrise.game.game.logic.mail.MailData;
import org.sunrise.game.genProto.gen.MailProto;
import org.sunrise.game.genRpc.gen.CallEnum;
import org.sunrise.game.rpc.annotation.RpcMethod;
import org.sunrise.game.rpc.annotation.RpcService;
import org.sunrise.game.rpc.function.ErrorType;
import org.sunrise.game.rpc.function.RpcFunction;
import org.sunrise.game.rpc.service.BaseService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 跨服邮件系统
 */
@RpcService
public class MailService extends BaseService {
    // 所有玩家的邮件：humanId -> List<MailData>
    private Map<String, List<MailData>> playerMails = new HashMap<>();

    public MailService(String nodeId) {
        super(nodeId);
    }

    @Override
    public void load() {
        getDbData("playerMails", new TypeReference<Map<String, List<MailData>>>() {
        }, value -> {
            if (value != null) {
                playerMails = value;
            }
        });
    }

    @Override
    public void save() {
        // 保存所有邮件
        putDbData("playerMails", playerMails);
    }

    /**
     * 获取玩家的下一个邮件ID
     */
    private long getNextMailId(String humanId) {
        List<MailData> mails = playerMails.computeIfAbsent(humanId, k -> new ArrayList<>());
        if (mails.isEmpty()) {
            return 1L;
        }
        long maxId = 0;
        for (MailData mail : mails) {
            if (mail.getMailId() > maxId) {
                maxId = mail.getMailId();
            }
        }
        return maxId + 1;
    }

    /**
     * 给玩家发送邮件
     */
    @RpcMethod
    public void sendMail(String humanId, int templateId, String attachmentsJson, String senderName) {
        long mailId = getNextMailId(humanId);
        long createTime = System.currentTimeMillis();

        MailData mail = new MailData(mailId, templateId, createTime);
        if (attachmentsJson != null && !attachmentsJson.isEmpty()) {
            List<MailData.MailAttachment> attachments = JSON.parseObject(
                    attachmentsJson,
                    new TypeReference<List<MailData.MailAttachment>>() {
                    }
            );
            mail.setAttachments(attachments);
        }
        if (senderName != null && !senderName.isEmpty()) {
            mail.setSenderName(senderName);
        }

        // 添加到邮件列表
        List<MailData> mails = playerMails.computeIfAbsent(humanId, k -> new ArrayList<>());
        mails.add(mail);

        // 构建proto消息并广播到所有游戏服
        MailProto.STMailInfo.Builder mailInfoBuilder = MailProto.STMailInfo.newBuilder()
                .setMailId(mailId)
                .setTemplateId(templateId)
                .setStatus(0) // 未读
                .setCreateTime(createTime);

        if (senderName != null && !senderName.isEmpty()) {
            mailInfoBuilder.setSenderName(senderName);
        }

        if (mail.getAttachments() != null && !mail.getAttachments().isEmpty()) {
            for (MailData.MailAttachment attachment : mail.getAttachments()) {
                MailProto.STMailAttachment.Builder attachBuilder = MailProto.STMailAttachment.newBuilder()
                        .setItemId(attachment.getItemId())
                        .setCount(attachment.getCount());
                mailInfoBuilder.addAttachments(attachBuilder);
            }
        }

        MailProto.MS2C_NewMail.Builder newMailBuilder = MailProto.MS2C_NewMail.newBuilder()
                .setMail(mailInfoBuilder.build());

        byte[] protoData = newMailBuilder.build().toByteArray();

        // 广播proto消息到所有游戏服
        RpcFunction.newInstance(RpcFunction.RpcCallType.SendAll)
                .call(CallEnum.MailRpcListenService_onNewMail,
                        "humanId", humanId,
                        "protoData", protoData);
    }

    /**
     * 给多个玩家发送邮件
     */
    @RpcMethod
    public void sendMailToMultiple(List<String> humanIds, int templateId, String attachmentsJson, String senderName) {
        for (String humanId : humanIds) {
            sendMail(humanId, templateId, attachmentsJson, senderName);
        }
    }

    /**
     * 给全服发送邮件
     */
    @RpcMethod
    public void sendMailToAll(int templateId, String attachmentsJson, String senderName) {
        RpcFunction rpcFunction = RpcFunction.newInstance();
        rpcFunction.call(CallEnum.PlayerInfoService_getAllPlayerIds);
        rpcFunction.listenResult(rpcResult -> {
            if (rpcResult.getResult() != ErrorType.SUCCESS) {
                return;
            }
            @SuppressWarnings("unchecked")
            Set<String> humanIds = (Set<String>) rpcResult.getData("humanIds");
            for (String humanId : humanIds) {
                sendMail(humanId, templateId, attachmentsJson, senderName);
            }
        });
    }

    /**
     * 获取玩家邮件列表
     */
    @RpcMethod
    public void getPlayerMails(String humanId) {
        List<MailData> mails = playerMails.computeIfAbsent(humanId, k -> new ArrayList<>());

        // 构建proto消息
        MailProto.MS2C_GetMailList.Builder builder = MailProto.MS2C_GetMailList.newBuilder();
        for (MailData mail : mails) {
            MailProto.STMailInfo.Builder mailInfoBuilder = MailProto.STMailInfo.newBuilder()
                    .setMailId(mail.getMailId())
                    .setTemplateId(mail.getTemplateId())
                    .setStatus(mail.getStatus())
                    .setCreateTime(mail.getCreateTime());

            if (mail.getSenderName() != null) {
                mailInfoBuilder.setSenderName(mail.getSenderName());
            }

            if (mail.getAttachments() != null) {
                for (MailData.MailAttachment attachment : mail.getAttachments()) {
                    MailProto.STMailAttachment.Builder attachBuilder = MailProto.STMailAttachment.newBuilder()
                            .setItemId(attachment.getItemId())
                            .setCount(attachment.getCount());
                    mailInfoBuilder.addAttachments(attachBuilder);
                }
            }

            builder.addMails(mailInfoBuilder.build());
        }

        byte[] protoData = builder.build().toByteArray();
        returns("humanId", humanId, "protoData", protoData);
    }

    /**
     * 读取邮件（标记为已读）
     */
    @RpcMethod
    public void readMail(String humanId, long mailId) {
        List<MailData> mails = playerMails.computeIfAbsent(humanId, k -> new ArrayList<>());
        MailData mail = findMail(mails, mailId);
        if (mail != null && mail.getStatus() == 0) {
            mail.setStatus(1);
            returns("humanId", humanId, "mailId", mailId, "success", true);
        } else {
            returns("humanId", humanId, "mailId", mailId, "success", false);
        }
    }

    /**
     * 领取邮件附件
     */
    @RpcMethod
    public void receiveMailAttachment(String humanId, long mailId) {
        MailData mail = getPlayerMail(humanId, mailId);
        if (mail != null && mail.getStatus() != 2 && mail.getAttachments() != null && !mail.getAttachments().isEmpty()) {
            // 标记为已领取，实际物品添加在游戏服处理
            mail.setStatus(2);
            String attachmentsJson = JSON.toJSONString(mail.getAttachments());
            returns("humanId", humanId, "mailId", mailId, "success", true, "attachmentsJson", attachmentsJson);
        } else {
            returns("humanId", humanId, "mailId", mailId, "success", false);
        }
    }

    /**
     * 删除邮件
     */
    @RpcMethod
    public void deleteMail(String humanId, long mailId) {
        List<MailData> mails = playerMails.computeIfAbsent(humanId, k -> new ArrayList<>());
        MailData mail = findMail(mails, mailId);
        if (mail != null) {
            if (mail.getAttachments() != null && !mail.getAttachments().isEmpty() && mail.getStatus() != 2) {
                returns("humanId", humanId, "mailId", mailId, "success", false, "reason", "attachments not received");
                return;
            }
            mails.remove(mail);
            returns("humanId", humanId, "mailId", mailId, "success", true);
        } else {
            returns("humanId", humanId, "mailId", mailId, "success", false);
        }
    }

    private MailData findMail(List<MailData> mails, long mailId) {
        for (MailData mail : mails) {
            if (mail.getMailId() == mailId) {
                return mail;
            }
        }
        return null;
    }

    private MailData getPlayerMail(String humanId, long mailId) {
        return findMail(playerMails.computeIfAbsent(humanId, k -> new ArrayList<>()), mailId);
    }
}
