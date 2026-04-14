package org.sunrise.game.rpc.function;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.sunrise.game.core.message.BaseMessage;

@Getter
@Setter
@ToString(callSuper = true)
public class Call extends BaseMessage {
    private int rpcId;
    private int type;
    private Object[] data;
    private int result;

    public Call() {
    }

    public Call(String nodeId) {
        super(nodeId);
    }

    public Call(String nodeId, int rpcId) {
        super(nodeId);
        this.rpcId = rpcId;
    }

    public Call(String nodeId, int rpcId, long messageId) {
        super(nodeId, messageId);
        this.rpcId = rpcId;

    }

    public void setData(Object... params) {
        data = params;
    }

    public Object getData(String name) {
        if (data != null) {
            for (int i = 0; i < data.length; i += 2) {
                if (data[i] instanceof String key) {
                    if (key.equals(name)) {
                        return data[i + 1];
                    }
                }
            }
        }
        return null;
    }

    public Object getData(int index) {
        return data[index * 2 + 1];
    }
}
