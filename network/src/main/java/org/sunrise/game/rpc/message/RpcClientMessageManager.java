package org.sunrise.game.rpc.message;

import org.sunrise.game.core.message.ClientMessageManager;
import org.sunrise.game.log.LogCore;

/**
 * pulseSender 向其他节点的server发送call
 */
public class RpcClientMessageManager extends ClientMessageManager {
    public RpcClientMessageManager(String nodeId) {
        super(nodeId);
    }

    @Override
    public void pulse() {
        try {
            pulseSender();
        } catch (Exception e) {
            LogCore.RpcClient.error("DispatchThread pulse, error : ", e);
        }
    }
}
