package org.sunrise.game.core.message;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.rpc.function.Call;

public class MessageUtils {

    private static final ObjectMapper objectMapper = createSecureObjectMapper();

    private static ObjectMapper createSecureObjectMapper() {
        ObjectMapper mapper = new ObjectMapper(new MessagePackFactory());
        mapper.deactivateDefaultTyping();
        mapper.enable(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE);
        return mapper;
    }

    // 从 MessagePack 字节数组反序列化为对象
    public static <T> T fromMessage(byte[] bytes, Class<T> clazz) {
        try {
            T result = objectMapper.readValue(bytes, clazz);
            sanitizeRpcPayload(result);
            return result;
        } catch (IllegalArgumentException e) {
            LogCore.RpcUtils.error("RPC data rejected during deserialization: {}", e.getMessage());
        } catch (Exception e) {
            LogCore.RpcUtils.error("Failed to deserialize message", e);
        }
        return null;
    }

    private static <T> void sanitizeRpcPayload(T result) {
        if (result instanceof Call call) {
            call.setData(call.getData());
            call.setMsg(call.getMsg());
        } else if (result instanceof BaseMessage baseMessage) {
            baseMessage.setMsg(baseMessage.getMsg());
        }
    }

    // 将对象序列化为 MessagePack 字节数组
    public static <T> byte[] toBytes(T message) {
        try {
            return objectMapper.writeValueAsBytes(message);
        } catch (Exception e) {
            LogCore.RpcUtils.error("Failed to serialize message", e);
            throw new RuntimeException("Failed to serialize message", e);
        }
    }
}
