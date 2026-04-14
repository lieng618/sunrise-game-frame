package org.sunrise.game.game.logic.system;//package org.sunrise.game.game.logic.system;
//
//import com.alibaba.fastjson2.TypeReference;
//import lombok.Getter;
//import org.sunrise.game.game.human.HumanObject;
//import org.sunrise.game.game.human.HumanObjectManger;
//import org.sunrise.game.game.logic.mail.MailData;
//import org.sunrise.game.game.modules.ItemModule;
//import org.sunrise.game.game.modules.MailModule;
//
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//
///**
// * 邮件系统
// * 统一管理所有玩家的邮件
// */
//@Getter
//public class MailSystem extends BaseSystem {
//    // 所有玩家的邮件：humanId -> List<MailData>
//    private Map<String, List<MailData>> playerMails = new HashMap<>();
//
//    @Override
//    public void load() {
//        getDbData("playerMails", new TypeReference<Map<String, List<MailData>>>() {
//        }, value -> {
//            if (value != null) {
//                playerMails = value;
//            }
//        });
//    }
//
//    @Override
//    public void save() {
//        // 保存所有邮件
//        putDbData("playerMails", playerMails);
//    }
//
//    /**
//     * 获取玩家的下一个邮件ID
//     * 从当前玩家的邮件列表中找出最大的ID，+1
//     */
//    private long getNextMailId(String humanId) {
//        List<MailData> mails = getPlayerMails(humanId);
//        if (mails.isEmpty()) {
//            return 1L;
//        }
//        long maxId = 0;
//        for (MailData mail : mails) {
//            if (mail.getMailId() > maxId) {
//                maxId = mail.getMailId();
//            }
//        }
//        return maxId + 1;
//    }
//
//    /**
//     * 给玩家发送邮件
//     *
//     * @param humanId     玩家ID
//     * @param templateId  模板ID
//     * @param attachments 附件列表
//     * @param senderName  发送者名称（可选）
//     */
//    public void sendMail(String humanId, int templateId, List<MailData.MailAttachment> attachments, String senderName) {
//        long mailId = getNextMailId(humanId);
//        long createTime = System.currentTimeMillis();
//
//        MailData mail = new MailData(mailId, templateId, createTime);
//        if (attachments != null) {
//            mail.setAttachments(new ArrayList<>(attachments));
//        }
//        if (senderName != null) {
//            mail.setSenderName(senderName);
//        }
//
//        // 添加到邮件列表
//        List<MailData> mails = getPlayerMails(humanId);
//        mails.add(mail);
//
//        // 如果玩家在线，通知客户端
//        HumanObject human = HumanObjectManger.getHumanObject(humanId);
//        if (human != null) {
//            MailModule mailModule = human.getModule(MailModule.class);
//            if (mailModule != null) {
//                mailModule.notifyNewMail(mail);
//            }
//        }
//    }
//
//    /**
//     * 给玩家发送邮件（无附件）
//     *
//     * @param humanId    玩家ID
//     * @param templateId 模板ID
//     * @param senderName 发送者名称（可选）
//     */
//    public void sendMail(String humanId, int templateId, String senderName) {
//        sendMail(humanId, templateId, null, senderName);
//    }
//
//    /**
//     * 给多个玩家发送邮件
//     *
//     * @param humanIds   玩家ID列表
//     * @param templateId 模板ID
//     * @param attachments 附件列表
//     * @param senderName 发送者名称（可选）
//     */
//    public void sendMailToMultiple(List<String> humanIds, int templateId, List<MailData.MailAttachment> attachments, String senderName) {
//        for (String humanId : humanIds) {
//            sendMail(humanId, templateId, attachments, senderName);
//        }
//    }
//
//    /**
//     * 读取邮件（标记为已读）
//     */
//    public boolean readMail(String humanId, long mailId) {
//        List<MailData> mails = getPlayerMails(humanId);
//        MailData mail = findMail(mails, mailId);
//        if (mail == null) {
//            return false;
//        }
//        if (mail.getStatus() == 0) { // 未读状态
//            mail.setStatus(1); // 标记为已读
//        }
//        return true;
//    }
//
//    /**
//     * 领取邮件附件
//     */
//    public boolean receiveMailAttachment(String humanId, long mailId) {
//        MailData mail = getPlayerMail(humanId, mailId);
//        if (mail == null) {
//            return false;
//        }
//        if (mail.getStatus() == 2) { // 已领取
//            return false;
//        }
//        if (mail.getAttachments().isEmpty()) {
//            return false;
//        }
//
//        HumanObject human = HumanObjectManger.getHumanObject(humanId);
//        if (human != null) {
//            ItemModule itemModule = human.getModule(ItemModule.class);
//            if (itemModule != null) {
//                for (MailData.MailAttachment attachment : mail.getAttachments()) {
//                    itemModule.addItem(attachment.getItemId(), attachment.getCount(), true);
//                }
//            }
//            // 标记为已领取
//            mail.setStatus(2);
//        }
//        return true;
//    }
//
//    /**
//     * 删除邮件
//     * 有附件未领取时无法删除
//     */
//    public boolean deleteMail(String humanId, long mailId) {
//        List<MailData> mails = getPlayerMails(humanId);
//        MailData mail = findMail(mails, mailId);
//        if (mail == null) {
//            return false;
//        }
//        if (!mail.getAttachments().isEmpty() && mail.getStatus() != 2) { // 不是已领取状态
//            return false;
//        }
//        mails.remove(mail);
//        return true;
//    }
//
//    /**
//     * 查找邮件
//     */
//    private MailData findMail(List<MailData> mails, long mailId) {
//        for (MailData mail : mails) {
//            if (mail.getMailId() == mailId) {
//                return mail;
//            }
//        }
//        return null;
//    }
//
//    /**
//     * 获取玩家的邮件
//     */
//    private MailData getPlayerMail(String humanId, long mailId) {
//        for (MailData mail : getPlayerMails(humanId)) {
//            if (mail.getMailId() == mailId) {
//                return mail;
//            }
//        }
//        return null;
//    }
//
//    /**
//     * 获取玩家的邮件列表
//     */
//    public List<MailData> getPlayerMails(String humanId) {
//        return playerMails.computeIfAbsent(humanId, k -> new ArrayList<>());
//    }
//}
