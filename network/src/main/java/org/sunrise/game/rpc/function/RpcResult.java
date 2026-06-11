package org.sunrise.game.rpc.function;

import lombok.Data;
import lombok.Setter;

import java.util.Map;

/**
 * RPC 调用的返回结果。
 *
 * <h3>context 合约</h3>
 * context 是调用方透传给回调的键值对上下文（如 humanId），
 * 由 {@code RpcFunction.listenResult(callback, contextMap)} 注入。
 * 调用方应使用 {@link Map#of(Object, Object)} 等方式构造，
 * 保证 key 为 String 类型。
 */
@Data
public class RpcResult {
    /** 调用方透传的上下文键值对，可能为 null
     * -- SETTER --
     *  设置上下文键值对。
     *
     * @param context key(String) → value 的映射；传 null 则清空上下文
     */
    @Setter
    private Map<String, Object> context;
    private Object[] data;
    private int result;

    public RpcResult() {
    }

    /**
     * 按 key 获取上下文值。
     *
     * @param name 上下文 key
     * @return 对应的 value，未找到或 context 为 null 时返回 null
     */
    public Object getContext(String name) {
        if (context != null) {
            return context.get(name);
        }
        return null;
    }

    /**
     * 按 key 获取返回数据中的值。
     * data 数组为 [key, value, key, value, ...] 平行数组格式。
     *
     * @param name 数据 key
     * @return 对应的 value，未找到或 data 为 null 时返回 null
     */
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
