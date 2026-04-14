package org.sunrise.game.global.service.chat;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatData {
    private String humanId;
    private long sendTime;
    private String message;

    public ChatData(String humanId, long sendTime, String message) {
        this.humanId = humanId;
        this.sendTime = sendTime;
        this.message = message;
    }
}
