package org.sunrise.game.rpc.function;

import org.sunrise.game.core.client.BaseClientManager;
import org.sunrise.game.core.server.BaseServerManager;
import org.sunrise.game.core.server.ConnectionManger;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.rpc.node.RpcNodeManager;
import org.sunrise.game.utils.IdGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 使用rpc需创建RpcNode，一个进程仅能创建一个RpcNode
 * 提供三种发送模式：1.群发给所有其他节点 2.随机挑选一个发送 3.指定节点发送（设置rpc服务器的节点）
 * 注意：未指定节点时，如果call调用的方法在当前节点注册过，则一定会由当前节点处理
 */
public class RpcFunction {

    public enum RpcCallType {
        SendRandom,  // 随机发送
        SendAll,      // 发送给所有节点
        SendDesignated // 发送给指定节点
    }

    private RpcCallType callType = RpcCallType.SendRandom;
    public static String nodeId; //当前rpc服务器的节点id
    private final List<Call> calls = new ArrayList<>();
    public static final Map<Integer, List<String>> callIdNodes = new ConcurrentHashMap<>();
    private String designatedServerNodeId; // 指定服务节点id

    public RpcFunction() {
    }

    public RpcFunction(RpcCallType callType) {
        this.callType = callType;
    }

    /**
     * 创建调用器，默认为随机发送
     */
    public static RpcFunction newInstance() {
        return new RpcFunction();
    }

    /**
     * 创建调用器，指定发送类型
     */
    public static RpcFunction newInstance(RpcCallType type) {
        return new RpcFunction(type);
    }

    /**
     * 创建调用器，指定节点
     */
    public static RpcFunction newInstance(String designatedNodeId) {
        // 指定的服务节点无效，则创建默认调用器，随机寻找一个节点发送
        if (!RpcNodeManager.isServerNodeActive(designatedNodeId)) {
            LogCore.RpcClient.debug("rpc newInstance SendDesignated, designatedNodeId is empty, create default");
            return new RpcFunction();
        }
        RpcFunction rpcFunction = new RpcFunction(RpcCallType.SendDesignated);
        rpcFunction.designatedServerNodeId = designatedNodeId;
        return rpcFunction;
    }

    /**
     * 发起一次调用
     */
    public boolean call(int id, Object... params) {
        var callNode = callIdNodes.get(id);
        // 没有任何节点提供此方法
        if (callNode == null || callNode.isEmpty()) {
            return false;
        }

        // 发给所有注册此方法的远端
        // 每个call使用同一个消息id，回调函数只调用一次
        if (callType == RpcCallType.SendAll) {
            long msgId = IdGenerator.getId();
            for (String s : callNode) {
                if (nodeId.equals(s)) {
                    Call call = new Call(nodeId, id, msgId);
                    call.setType(CallType.Call.ordinal());
                    call.setData(params);
                    calls.add(call);
                    BaseServerManager.recvFromClient(nodeId, call);
                    LogCore.RpcClient.debug("rpc call, callId = {}, messageId = { {} }, params = {}", call.getRpcId(), call.getMessageId(), params);
                } else {
                    Call call = new Call(getCurNodeByServerNode(s), id, msgId);
                    call.setType(CallType.Call.ordinal());
                    call.setData(params);
                    calls.add(call);
                    BaseClientManager.sendToServer(call);
                    LogCore.RpcClient.debug("rpc call, callId = {}, messageId = { {} }, params = {}", call.getRpcId(), call.getMessageId(), params);
                }
            }
        } else if (callType == RpcCallType.SendRandom) {
            for (String s : callNode) {
                //当前节点有这个方法，放入自身消息队列
                if (nodeId.equals(s)) {
                    Call call = new Call(nodeId, id);
                    call.setType(CallType.Call.ordinal());
                    call.setData(params);
                    calls.add(call);
                    BaseServerManager.recvFromClient(nodeId, call);
                    LogCore.RpcClient.debug("rpc call, callId = {}, messageId = { {} }, params = {}", call.getRpcId(), call.getMessageId(), params);
                    return true;
                }
            }

            // 随机挑选一个远端进行发送
            int index = ThreadLocalRandom.current().nextInt(callNode.size());
            Call call = new Call(getCurNodeByServerNode(callNode.get(index)), id);
            call.setType(CallType.Call.ordinal());
            call.setData(params);
            calls.add(call);
            BaseClientManager.sendToServer(call);
            LogCore.RpcClient.debug("rpc call, callId = {}, messageId = { {} }, params = {}", call.getRpcId(), call.getMessageId(), params);
        } else if (callType == RpcCallType.SendDesignated) {
            if (!RpcNodeManager.isServerNodeActive(designatedServerNodeId)) {
                LogCore.RpcClient.warn("rpc newInstance SendDesignated, designatedNodeId is empty, skip");
                return false;
            }
            if (nodeId.equals(designatedServerNodeId)) {
                //当前节点有这个方法，放入自身消息队列
                Call call = new Call(nodeId, id);
                call.setType(CallType.Call.ordinal());
                call.setData(params);
                calls.add(call);
                BaseServerManager.recvFromClient(nodeId, call);
                LogCore.RpcClient.debug("rpc call, callId = {}, messageId = { {} }, params = {}", call.getRpcId(), call.getMessageId(), params);
            } else {
                Call call = new Call(getCurNodeByServerNode(designatedServerNodeId), id);
                call.setType(CallType.Call.ordinal());
                call.setData(params);
                calls.add(call);
                BaseClientManager.sendToServer(call);
                LogCore.RpcClient.debug("rpc call, callId = {}, messageId = { {} }, params = {}", call.getRpcId(), call.getMessageId(), params);
            }
        }
        return true;
    }

    /**
     * rpcClient发起调用之后，注册回调函数
     */
    public void listenResult(Callback<RpcResult> callback, Object... contexts) {
        if (calls.isEmpty()) {
            LogCore.RpcClient.error("rpc listenResult, calls empty");
            return;
        }
        Call last = calls.getLast();
        if (contexts.length % 2 != 0) {
            LogCore.RpcClient.error("rpc listenResult, contexts length error, contexts = {}", contexts);
        }
        CallResult callResult = new CallResult(callback, contexts);
        if (last.getNodeId() != null) {
            RpcManager.registerCallback(last.getMessageId(), callResult);
        } else {
            callResult.getRpcResult().setResult(ErrorType.RPC_NOT_REGISTER);
            callback.process(callResult.getRpcResult());
        }
    }

    /**
     * 监听返回值后，手动设置超时时间；mills 为 0 时使用默认 10 秒，不调用则同为默认 10 秒
     */
    public void setTimeOut(long mills) {
        if (calls.isEmpty()) {
            return;
        }
        Call last = calls.getLast();
        RpcManager.setTimeOut(last.getMessageId(), mills);
    }

    /**
     * rpcServer发起一次更新，推送给指定node的rpcClient，通知自身所管理的rpc方法
     */
    public void update(String connectNode, List<Integer> callIds) {
        if (!ConnectionManger.isConnectExist(connectNode)) {
            return;
        }
        if (nodeId == null) {
            return;
        }
        Call call = new Call(nodeId);
        call.setType(CallType.Update.ordinal());
        call.setMsg(callIds);
        call.setToNodeId(connectNode);
        calls.add(call);
        BaseServerManager.sendToClient(call);
    }

    /**
     * rpc客户端收到rpc服务器发来的rpc列表
     */
    @SuppressWarnings("unchecked")
    public static void onUpdate(Call call) {
        List<Integer> callIds = (List<Integer>) call.getMsg();
        if (callIds == null) {
            return;
        }
        for (int callId : callIds) {
            List<String> list = RpcFunction.callIdNodes.get(callId);
            if (list == null) {
                list = new ArrayList<>();
            }
            list.add(call.getNodeId());
            RpcFunction.callIdNodes.put(callId, list);
        }
        LogCore.RpcServer.info("rpc update, from NodeId = { {} }, rpcId = {}, data = {{}}", call.getNodeId(), call.getRpcId(), call.getMsg());

    }

    private static String getCurNodeByServerNode(String serverNode) {
        return RpcNodeManager.getClientNodeIdByServerNodeId(serverNode);
    }
}
