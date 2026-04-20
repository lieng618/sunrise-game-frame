package core.message;

import core.client.SocketClient;
import com.google.protobuf.ByteString;
import org.sunrise.game.genProto.gen.TopicProto;
import org.sunrise.game.log.LogCore;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 协议路由器 - 统一管理协议的路由和处理
 */
public class ProtocolRouter {

    private static final Map<Integer, Method> handlerMethods = new ConcurrentHashMap<>();
    private static final Map<Integer, Method> protoParserMap = new ConcurrentHashMap<>();

    /**
     * 注册消息处理器
     */
    public static void registerHandler(int packetType, int packetId, Method handlerMethod) {
        int key = generateKey(packetType, packetId);
        handlerMethods.put(key, handlerMethod);
    }

    /**
     * 注册协议解析器
     */
    public static void registerParser(int packetType, int packetId, Method parserMethod) {
        int key = generateKey(packetType, packetId);
        protoParserMap.put(key, parserMethod);
    }

    /**
     * 获取协议解析器（供外部使用）
     */
    public static Method getParser(int key) {
        return protoParserMap.get(key);
    }

    /**
     * 路由并处理协议消息
     */
    public static void route(SocketClient client, TopicProto.MBasePacketData packet) throws Exception {
        TopicProto.TOPIC topic = packet.getPacketType();
        if (topic == TopicProto.TOPIC.UNRECOGNIZED) {
            return;
        }

        int packetId = packet.getPacketId();
        int key = generateKey(topic.getNumber(), packetId);

        // 解析协议数据
        ByteString packetData = packet.getPacketData();
        Object parsedData = parsePacketData(topic.getNumber(), packetId, packetData);

        // 记录日志（解析后的数据）
        logReceivedMessage(client.getUid(), topic.getNumber(), packetId, parsedData);

        // 查找对应的处理器
        Method handler = handlerMethods.get(key);
        if (handler == null) {
            String topicName = getTopicName(topic.getNumber());
//            LogCore.Client.debug("No handler found for topic={}({}), packetId={}, ignoring", topicName, topic.getNumber(), packetId);
            return;
        }

        // 调用处理器，根据方法签名决定传递参数
        invokeHandler(handler, client, parsedData);
    }

    /**
     * 调用处理器方法
     */
    private static void invokeHandler(Method handler, SocketClient client, Object parsedData) throws Exception {
        Parameter[] parameters = handler.getParameters();
        Object[] args;

        if (parameters.length == 1) {
            // 只有一个参数：必须是SocketClient
            if (parameters[0].getType() == SocketClient.class) {
                args = new Object[]{client};
            } else {
                LogCore.Client.error("Unsupported handler method signature: {} - first parameter must be SocketClient", handler.getName());
                return;
            }
        } else if (parameters.length == 2) {
            // 两个参数：第一个必须是SocketClient
            if (parameters[0].getType() != SocketClient.class) {
                LogCore.Client.error("Unsupported handler method signature: {} - first parameter must be SocketClient", handler.getName());
                return;
            }

            Class<?> secondParamType = parameters[1].getType();
            if (parsedData != null) {
                // 检查类型是否匹配
                if (secondParamType.isAssignableFrom(parsedData.getClass())) {
                    // 类型匹配，传递解析后的对象
                    args = new Object[]{client, parsedData};
                } else {
                    // 类型不匹配，尝试传递ByteString（向后兼容）
                    LogCore.Client.warn("Type mismatch for handler {}: expected {}, got {}, using ByteString instead",
                            handler.getName(), secondParamType.getSimpleName(), parsedData.getClass().getSimpleName());
                    return;
                }
            } else {
                return;
            }
        } else {
            // 不支持的方法签名
            LogCore.Client.error("Unsupported handler method signature: {} - must have 1 or 2 parameters", handler.getName());
            return;
        }

        handler.invoke(null, args);
    }

    /**
     * 解析协议数据
     */
    private static Object parsePacketData(int packetType, int packetId, ByteString packetData) {
        if (packetData == null || packetData.isEmpty()) {
            return null;
        }

        int key = generateKey(packetType, packetId);
        Method parser = protoParserMap.get(key);
        if (parser == null) {
            return null;
        }

        try {
            return parser.invoke(null, packetData);
        } catch (Exception e) {
            LogCore.Client.debug("Failed to parse packet data for packetType={}, packetId={}", packetType, packetId);
            return null;
        }
    }

    /**
     * 记录接收到的消息（解析后的格式）
     */
    private static void logReceivedMessage(String uid, int packetType, int packetId, Object parsedData) {
        String topicName = getTopicName(packetType);
        String dataStr;
        if (parsedData != null) {
            dataStr = parsedData.toString().replace("\n", "");
        } else {
            dataStr = "";
        }
        LogCore.Client.debug("Received: uid={}, topic={}({}), packetId={}, data={}",
                uid, topicName, packetType, packetId, dataStr);
    }

    /**
     * 获取主题名称
     */
    public static String getTopicName(int packetType) {
        TopicProto.TOPIC topic = TopicProto.TOPIC.forNumber(packetType);
        if (topic != null && topic != TopicProto.TOPIC.UNRECOGNIZED) {
            return topic.name();
        }
        return "UNKNOWN(" + packetType + ")";
    }

    /**
     * 生成协议键值
     */
    public static int generateKey(int packetType, int packetId) {
        return packetType * 100000 + packetId;
    }
}
