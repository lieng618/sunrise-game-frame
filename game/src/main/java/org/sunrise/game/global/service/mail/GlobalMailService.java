package org.sunrise.game.global.service.mail;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import org.sunrise.game.game.logic.mail.MailData;
import org.sunrise.game.genProto.gen.MailProto;
import org.sunrise.game.genProto.gen.TopicProto;
import org.sunrise.game.genRpc.gen.CallEnum;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.rpc.annotation.RpcMethod;
import org.sunrise.game.rpc.annotation.RpcService;
import org.sunrise.game.rpc.function.ErrorType;
import org.sunrise.game.rpc.function.RpcFunction;
import org.sunrise.game.rpc.function.RpcResult;
import org.sunrise.game.rpc.service.BaseService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RpcService
public class GlobalMailService extends BaseService {
    private static final int PLAYER_PAGE_SIZE = 1_000;

    private Map<String, List<MailData>> playerMails = new HashMap<>();

    public GlobalMailService(String nodeId) {
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
        putDbData("playerMails", playerMails);
    }

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

        List<MailData> mails = playerMails.computeIfAbsent(humanId, k -> new ArrayList<>());
        mails.add(mail);

        MailProto.STMailInfo.Builder mailInfoBuilder = MailProto.STMailInfo.newBuilder()
                .setMailId(mailId)
                .setTemplateId(templateId)
                .setStatus(0)
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

        RpcFunction.newInstance(RpcFunction.RpcCallType.SendAll)
                .call(CallEnum.GameRpcListenService_sendToHuman,
                        humanId, TopicProto.TOPIC.TOPIC_TYPE_MAIL_VALUE, MailProto.FROM_SERVER.S2C_NewMail_VALUE, protoData);
    }

    @RpcMethod
    public void sendMailToMultiple(List<String> humanIds, int templateId, String attachmentsJson, String senderName) {
        for (String humanId : humanIds) {
            sendMail(humanId, templateId, attachmentsJson, senderName);
        }
    }

    @RpcMethod
    public void sendMailToAll(int templateId, String attachmentsJson, String senderName) {
        LogCore.GlobalServer.info(
                "global mail broadcast started, templateId = {}, pageSize = {}",
                templateId,
                PLAYER_PAGE_SIZE);
        requestPlayerPage(null, templateId, attachmentsJson, senderName, 1, 0);
    }

    private void requestPlayerPage(
            String afterHumanId,
            int templateId,
            String attachmentsJson,
            String senderName,
            int pageNumber,
            int sentCount) {
        RpcFunction rpcFunction = RpcFunction.newInstance();
        boolean called = rpcFunction.call(
                CallEnum.GlobalPlayerInfoService_getAllPlayerIds,
                afterHumanId,
                PLAYER_PAGE_SIZE);
        if (!called) {
            LogCore.GlobalServer.error(
                    "global mail broadcast failed: player page RPC unavailable, templateId = {}, cursor = {}",
                    templateId,
                    afterHumanId);
            return;
        }

        rpcFunction.listenResult(rpcResult -> {
            if (rpcResult.getResult() != ErrorType.SUCCESS) {
                LogCore.GlobalServer.error(
                        "global mail broadcast failed: player page RPC error, templateId = {}, page = {}, result = {}, cursor = {}",
                        templateId,
                        pageNumber,
                        rpcResult.getResult(),
                        afterHumanId);
                return;
            }

            List<String> humanIds;
            try {
                humanIds = extractHumanIds(rpcResult);
            } catch (IllegalArgumentException e) {
                LogCore.GlobalServer.error(
                        "global mail broadcast failed: invalid player page, templateId = {}, page = {}, cursor = {}, reason = {}",
                        templateId,
                        pageNumber,
                        afterHumanId,
                        e.getMessage());
                return;
            }

            for (String humanId : humanIds) {
                sendMail(humanId, templateId, attachmentsJson, senderName);
            }

            int totalSent = sentCount + humanIds.size();
            boolean hasMore = Boolean.TRUE.equals(rpcResult.getData("hasMore"));
            String nextCursor = rpcResult.getData("nextCursor") instanceof String cursor ? cursor : null;
            LogCore.GlobalServer.info(
                    "global mail broadcast progress, templateId = {}, page = {}, pageCount = {}, sent = {}, cursor = {}, hasMore = {}",
                    templateId,
                    pageNumber,
                    humanIds.size(),
                    totalSent,
                    nextCursor,
                    hasMore);

            if (!hasMore) {
                LogCore.GlobalServer.info(
                        "global mail broadcast completed, templateId = {}, sent = {}, pages = {}",
                        templateId,
                        totalSent,
                        pageNumber);
                return;
            }
            if (humanIds.isEmpty() || nextCursor == null || nextCursor.equals(afterHumanId)) {
                LogCore.GlobalServer.error(
                        "global mail broadcast stopped: invalid pagination cursor, templateId = {}, page = {}, sent = {}, cursor = {}",
                        templateId,
                        pageNumber,
                        totalSent,
                        nextCursor);
                return;
            }

            requestPlayerPage(
                    nextCursor,
                    templateId,
                    attachmentsJson,
                    senderName,
                    pageNumber + 1,
                    totalSent);
        });
    }

    static List<String> extractHumanIds(RpcResult rpcResult) {
        Object rawHumanIds = rpcResult.getData("humanIds");
        if (rawHumanIds == null) {
            return List.of();
        }
        if (!(rawHumanIds instanceof List<?> values)) {
            throw new IllegalArgumentException(
                    "humanIds must be a List, actual type = " + rawHumanIds.getClass().getName());
        }

        List<String> humanIds = new ArrayList<>(values.size());
        for (Object value : values) {
            if (!(value instanceof String humanId)) {
                throw new IllegalArgumentException("humanIds contains a non-String value");
            }
            humanIds.add(humanId);
        }
        return humanIds;
    }

    @RpcMethod
    public void getPlayerMails(String humanId) {
        List<MailData> mails = playerMails.computeIfAbsent(humanId, k -> new ArrayList<>());

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

    @RpcMethod
    public void getMailAttachmentInfo(String humanId, long mailId) {
        MailData mail = getPlayerMail(humanId, mailId);
        if (mail != null && mail.getStatus() != 2 && mail.getAttachments() != null && !mail.getAttachments().isEmpty()) {
            String attachmentsJson = JSON.toJSONString(mail.getAttachments());
            returns("humanId", humanId, "mailId", mailId, "success", true, "attachmentsJson", attachmentsJson);
        } else {
            returns("humanId", humanId, "mailId", mailId, "success", false);
        }
    }

    @RpcMethod
    public void confirmMailAttachment(String humanId, long mailId) {
        MailData mail = getPlayerMail(humanId, mailId);
        if (mail != null && mail.getStatus() != 2 && mail.getAttachments() != null && !mail.getAttachments().isEmpty()) {
            mail.setStatus(2);
            returns("humanId", humanId, "mailId", mailId, "success", true);
        } else {
            returns("humanId", humanId, "mailId", mailId, "success", false);
        }
    }

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
