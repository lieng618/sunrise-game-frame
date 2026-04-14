package org.sunrise.game.rpc.function;

import lombok.Data;

@Data
public class RpcResult {
    private Object[] context;
    private Object[] data;
    private int result;

    public RpcResult() {
    }

    public void setContext(Object[] context) {
        this.context = context;
        if (context.length % 2 != 0) {
            this.context = null;
        }
    }

    public Object getContext(String name) {
        if (context != null) {
            for (int i = 0; i < context.length - 1; i += 2) {
                if (context[i] instanceof String contextName) {
                    if (contextName.equals(name)) {
                        return context[i + 1];
                    }
                }
            }
        }
        return null;
    }

    public Object getData(String name) {
        if (data != null) {
            for (int i = 0; i < data.length - 1; i += 2) {
                if (data[i] instanceof String dataName) {
                    if (dataName.equals(name)) {
                        return data[i + 1];
                    }
                }
            }
        }
        return null;
    }
}
