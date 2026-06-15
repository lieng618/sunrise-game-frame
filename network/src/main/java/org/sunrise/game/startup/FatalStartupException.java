package org.sunrise.game.startup;

/**
 * 启动阶段不可恢复错误。
 * <p>
 * 由 {@link org.sunrise.game.rpc.node.RpcNodeManager#runMain}、{@link org.sunrise.game.rpc.function.CallUtils}、
 * {@link org.sunrise.game.rpc.node.RpcScanConfig} 等在初始化失败时抛出，main 统一捕获后以 exit 1 退出，
 * 避免 RPC 未完整注册仍继续监听导致运行时大量调用失败。
 */
public class FatalStartupException extends RuntimeException {
    public FatalStartupException(String message) {
        super(message);
    }

    public FatalStartupException(String message, Throwable cause) {
        super(message, cause);
    }
}
