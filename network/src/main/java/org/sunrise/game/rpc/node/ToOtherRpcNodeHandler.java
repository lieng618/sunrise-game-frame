package org.sunrise.game.rpc.node;

import io.netty.channel.ChannelHandlerContext;
import org.sunrise.game.core.client.BaseClientHandler;

/**
 * rpc节点客户端处理类
 * 其他节点断开连接时，将对方管理的rpc方法移除
 */
public class ToOtherRpcNodeHandler extends BaseClientHandler {
    public ToOtherRpcNodeHandler(String nodeId) {
        super(nodeId);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        RpcNodeManager.getRpcNode().otherOffline(RpcNodeManager.getServerIdByClientNodeId(getNodeId()));
    }
}
