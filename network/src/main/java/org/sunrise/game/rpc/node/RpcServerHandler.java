package org.sunrise.game.rpc.node;

import org.sunrise.game.core.server.BaseServerHandler;
import org.sunrise.game.rpc.function.CallUtils;
import org.sunrise.game.rpc.function.RpcFunction;

/**
 * rpc节点服务器处理类
 * 接收到新连接时，将当前节点管理的rpc方法发给对方
 */
public class RpcServerHandler extends BaseServerHandler {
    public RpcServerHandler(String nodeId) {
        super(nodeId);
    }

    @Override
    public void onRecvConnect() {
        super.onRecvConnect();
        RpcFunction.newInstance().update(getConnectNode(), CallUtils.getCallIds());
    }
}
