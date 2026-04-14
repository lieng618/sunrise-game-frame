package org.sunrise.game.core.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.msgpack.jackson.dataformat.MessagePackFactory;

public class MessageUtils {

    private static final ObjectMapper objectMapper = new ObjectMapper(new MessagePackFactory());

    // 从 MessagePack 字节数组反序列化为对象
    public static <T> T fromMessage(byte[] bytes, Class<T> clazz) {
        try {
            return objectMapper.readValue(bytes, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize message", e);
        }
    }

    // 将对象序列化为 MessagePack 字节数组
    public static <T> byte[] toBytes(T message) {
        try {
            return objectMapper.writeValueAsBytes(message);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize message", e);
        }
    }
}
