package org.sunrise.game.graceful;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个静态方法为优雅停机回调。
 * 方法签名必须为 {@code public static void}，无参数。
 *
 * <p>每个 {@link ShutdownPhase} 只应有一个入口。一个入口可以聚合多个
 * 同类资源，但不要把同一阶段的内部顺序继续拆成魔法数字。</p>
 *
 * @see GracefulShutdown#scanAndInstall(String...)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OnShutdown {

    /** 此回调所属的固定生命周期阶段。 */
    ShutdownPhase phase();

    /**
     * 此阶段的最大等待时间（毫秒）。超时后会中断阶段线程并终止停机流程，
     * 不再继续关闭后续资源。默认 10 秒。
     */
    long timeoutMs() default 10_000;

    /** 阶段名称（用于日志），为空则自动使用 "{类名}.{方法名}"。 */
    String name() default "";
}
