package org.sunrise.game.game.logic.mail;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 邮件数据
 */
@Getter
@Setter
public class MailData {
    private long mailId;                    // 邮件ID
    private int templateId;                 // 模板ID
    private List<MailAttachment> attachments = new ArrayList<>();  // 附件列表
    private int status;                     // 状态：0-未读，1-已读，2-已领取
    private long createTime;                // 创建时间
    private String senderName;              // 发送者名称（可选）

    public MailData() {
    }

    public MailData(long mailId, int templateId, long createTime) {
        this.mailId = mailId;
        this.templateId = templateId;
        this.createTime = createTime;
        this.status = 0; // 默认未读
    }

    /**
     * 邮件附件
     */
    @Getter
    @Setter
    public static class MailAttachment {
        private int itemId;     // 奖励物品ID
        private int count;      // 奖励数量

        public MailAttachment() {
        }

        public MailAttachment(int itemId, int count) {
            this.itemId = itemId;
            this.count = count;
        }
    }
}
