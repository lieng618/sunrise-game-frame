package org.sunrise.game.game.logic.mail;

import org.sunrise.game.game.annotation.MsgHandlerClass;
import org.sunrise.game.game.annotation.MsgHandlerMethod;
import org.sunrise.game.game.human.HumanObject;
import org.sunrise.game.game.modules.MailModule;
import org.sunrise.game.genProto.gen.MailProto;
import org.sunrise.game.genProto.gen.TopicProto;

/**
 * 邮件消息处理器
 */
@MsgHandlerClass(packetType = TopicProto.TOPIC.TOPIC_TYPE_MAIL_VALUE)
public class MailMsgHandler {
    /**
     * 获取邮件列表
     */
    @MsgHandlerMethod(packetId = MailProto.FROM_CLIENT.C2S_GetMailList_VALUE)
    public static void getMailList(HumanObject humanObject) {
        MailModule mailModule = humanObject.getModule(MailModule.class);
        if (mailModule != null) {
            mailModule.sendMailList();
        }
    }

    /**
     * 读取邮件（标记为已读）
     */
    @MsgHandlerMethod(packetId = MailProto.FROM_CLIENT.C2S_ReadMail_VALUE)
    public static void readMail(HumanObject humanObject, MailProto.MC2S_ReadMail data) {
        MailModule mailModule = humanObject.getModule(MailModule.class);
        if (mailModule != null) {
            mailModule.readMail(data.getMailId());
        }
    }

    /**
     * 领取邮件附件
     */
    @MsgHandlerMethod(packetId = MailProto.FROM_CLIENT.C2S_ReceiveMailAttachment_VALUE)
    public static void receiveMailAttachment(HumanObject humanObject, MailProto.MC2S_ReceiveMailAttachment data) {
        MailModule mailModule = humanObject.getModule(MailModule.class);
        if (mailModule != null) {
            mailModule.receiveMailAttachment(data.getMailId());
        }
    }

    /**
     * 删除邮件
     */
    @MsgHandlerMethod(packetId = MailProto.FROM_CLIENT.C2S_DeleteMail_VALUE)
    public static void deleteMail(HumanObject humanObject, MailProto.MC2S_DeleteMail data) {
        MailModule mailModule = humanObject.getModule(MailModule.class);
        if (mailModule != null) {
            mailModule.deleteMail(data.getMailId());
        }
    }
}
