package org.sunrise.game.rpc.function;

import java.util.LinkedList;

public class CallContext {
    private static final LinkedList<Call> callStack = new LinkedList<>();

    public static void push(Call call) {
        callStack.addLast(call);
    }

    public static void pop() {
        callStack.removeLast();
    }

    public static Call getLastCall() {
        if (callStack.isEmpty()) {
            return null;
        }
        return callStack.getLast();
    }
}
