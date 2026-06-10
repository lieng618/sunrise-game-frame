package org.sunrise.game.rpc.function;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.sunrise.game.core.message.BaseMessage;
import org.sunrise.game.core.message.RpcDataSanitizer;

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
        data = RpcDataSanitizer.sanitizeArray(params);
    }

    public Object getData(int index) {
        return data[index];
    }
}
