package org.sunrise.game.rpc.function;

public class ErrorType {
    // 成功
    public static final int SUCCESS = 0;
    // 超时
    public static final int RPC_TIMEOUT = 100;
    // 远端没有此服务
    public static final int RPC_SERVICE_NOT_FOUND = 101;
    // 远端没有此方法
    public static final int RPC_METHOD_NOT_FOUND = 102;
    // 调用参数不匹配
    public static final int RPC_ARGS_NOT_MATCH = 103;
    // 调用参数类型不匹配
    public static final int RPC_ARG_TYPE_MISMATCH = 106;
    // 异常
    public static final int RPC_CALL_CATCH = 104;
    // 远端没有注册此方法
    public static final int RPC_NOT_REGISTER = 105;
    // 停机中，调用被中断
    public static final int RPC_SHUTDOWN = 107;
}
