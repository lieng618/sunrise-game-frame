package org.sunrise.game.rpc.function;

import lombok.Data;

import java.util.Map;

@Data
public class CallResult {
    private Callback<RpcResult> callback;
    private RpcResult rpcResult;
    public CallResult(Callback<RpcResult> callback, Map<String, Object> context) {
        this.callback = callback;
        this.rpcResult = new RpcResult();
        if (context != null) {
            this.rpcResult.setContext(context);
        }
    }
}
