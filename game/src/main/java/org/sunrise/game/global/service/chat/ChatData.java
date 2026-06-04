package org.sunrise.game.global.service.chat;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatData {
    private String humanId;
    private String name;
    private long sendTime;
    private String message;

    public ChatData(String humanId, String name, long sendTime, String message) {
        this.humanId = humanId;
        this.name = name;
        this.sendTime = sendTime;
        this.message = message;
    }
}
