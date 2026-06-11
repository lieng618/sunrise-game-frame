package org.sunrise.game.graceful;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个静态方法为优雅停机回调。
 * 方法签名必须为 {@code public static void}，无参数。
 *
 * <h3>Order 建议范围</h3>
 * <table>
 *   <tr><td>20-29</td><td>RPC pending 排空 + 清理</td></tr>
 *   <tr><td>30-39</td><td>在线玩家数据保存</td></tr>
 *   <tr><td>40-49</td><td>Service 数据保存</td></tr>
 *   <tr><td>50-59</td><td>GameSystem 数据保存</td></tr>
 *   <tr><td>60-69</td><td>Controller 数据保存</td></tr>
 *   <tr><td>70-79</td><td>网络监听关闭 (ExternalServer)</td></tr>
 *   <tr><td>80-89</td><td>客户端连接关闭</td></tr>
 *   <tr><td>90-99</td><td>服务端监听关闭</td></tr>
 *   <tr><td>100+</td><td>数据库连接池关闭</td></tr>
 * </table>
 *
 * <p>Order=10 保留为系统停机标志（框架内置，不由注解驱动）。</p>
 *
 * @see GracefulShutdown#scanAndInstall(String...)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OnShutdown {

    /** 执行顺序，越小越先执行。默认 100。 */
    int order() default 100;

    /** 此阶段的最大等待时间（毫秒），超过则 warn 并继续下一阶段。默认 10 秒。 */
    long timeoutMs() default 10_000;

    /** 阶段名称（用于日志），为空则自动使用 "{类名}.{方法名}"。 */
    String name() default "";
}
