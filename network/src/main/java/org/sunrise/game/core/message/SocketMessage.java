package org.sunrise.game.core.message;

import lombok.Getter;
import lombok.Setter;

/**
 * 最终在网络上传输的消息
 */
@Getter
@Setter
public class SocketMessage {
    int messageType;
    byte[] data;

    public SocketMessage() {
    }

    public SocketMessage(int messageType, byte[] data) {
        this.messageType = messageType;
        this.data = data;
    }
}
