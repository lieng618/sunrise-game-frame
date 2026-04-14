package org.sunrise.game.core.server;

import io.netty.channel.Channel;
import lombok.Data;

@Data
public class Connection {
    private final String connectId;
    private final Channel channel;
    public Connection(String connectId, Channel channel) {
        this.connectId = connectId;
        this.channel = channel;
    }
}
