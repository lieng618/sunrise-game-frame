package org.sunrise.game.core.message;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.sunrise.game.utils.IdGenerator;

@Getter
@Setter
@ToString
public class BaseMessage {
    private String nodeId; //消息所属节点
    private String toNodeId; //发送到哪个节点
    private long messageId; //消息唯一id
    private Object msg;
    public BaseMessage() {
    }
    public BaseMessage(String nodeId) {
        this.nodeId = nodeId;
        this.messageId = IdGenerator.getId();
    }
    public BaseMessage(String nodeId, long messageId) {
        this.nodeId = nodeId;
        this.messageId = messageId;
    }
    public BaseMessage(String nodeId, Object msg) {
        this.nodeId = nodeId;
        this.messageId = IdGenerator.getId();
        this.msg = msg;
    }
    public BaseMessage(String nodeId, long messageId, Object msg) {
        this.nodeId = nodeId;
        this.messageId = messageId;
        this.msg = msg;
    }
    public BaseMessage(String nodeId, String toNodeId, long messageId, Object msg) {
        this.nodeId = nodeId;
        this.toNodeId = toNodeId;
        this.messageId = messageId;
        this.msg = msg;
    }
}
