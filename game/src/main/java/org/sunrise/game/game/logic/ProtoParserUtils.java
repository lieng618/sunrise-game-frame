package org.sunrise.game.game.logic;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.Parser;
import org.sunrise.game.genProto.gen.TopicProto;
import org.sunrise.game.log.LogCore;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unchecked")
public class ProtoParserUtils {

    /**
     * 根据packetType和packetId，解析packetData为对应的协议结构
     * 例如：TOPIC_TYPE_LOGIN 和 C2S_Login，获取的Method为 LoginProto.MC2S_Login.parseFrom()
     *      会把字节类型的packet_data转化为MC2S_Login，再传递给消息处理函数
     */
    private static final Map<Integer, Method> protoParserMap = new HashMap<>();

    public static Method getProtoParserClass(int packetType, int packetId) {
        return protoParserMap.get(generateKey(packetType, packetId));
    }

    public static void init() {
        registerTopics();
    }

    /**
     * 注册所有主题和协议解析器 - 自动从TOPIC枚举中提取
     */
    private static void registerTopics() {
        // 遍历TOPIC枚举，自动提取Proto类名
        for (TopicProto.TOPIC topic : TopicProto.TOPIC.values()) {
            if (topic == TopicProto.TOPIC.UNRECOGNIZED) {
                continue;
            }

            int packetType = topic.getNumber();
            // 从枚举名称中提取Proto类名（最后一个_之后的字符串）
            String protoClassName = extractProtoClassName(topic.name());
            if (protoClassName != null) {
                registerClientTopic(packetType, protoClassName);
            }
        }
    }

    /**
     * 从TOPIC枚举名称中提取Proto类名
     * 例如：TOPIC_TYPE_LOGIN -> LoginProto
     *      TOPIC_TYPE_MAP -> MapProto
     */
    private static String extractProtoClassName(String topicName) {
        int lastUnderscoreIndex = topicName.lastIndexOf('_');
        if (lastUnderscoreIndex == -1 || lastUnderscoreIndex == topicName.length() - 1) {
            return null;
        }

        // 获取最后一个_之后的字符串（通常是全大写，如LOGIN）
        String suffix = topicName.substring(lastUnderscoreIndex + 1);
        if (suffix.isEmpty()) {
            return null;
        }

        // 转换为首字母大写，其余小写的格式
        String firstChar = suffix.substring(0, 1).toUpperCase();
        String restChars = suffix.length() > 1 ? suffix.substring(1).toLowerCase() : "";
        return firstChar + restChars + "Proto";
    }

    public static int generateKey(int packetType, int packetId) {
        return packetType * 100000 + packetId;
    }

    public static void registerClientTopic(int packetType, String proto) {
        Class<?> enumClass;
        try {
            enumClass = Class.forName("org.sunrise.game.genProto.gen." + proto + "$FROM_CLIENT");
            Class<? extends Enum<?>> Class = (Class<? extends Enum<?>>) enumClass;
            if (Class == null) {
                LogCore.ServerStartUp.error("GameServer StartUp Failed, proto:{} invalid", proto + "FROM_CLIENT");
                System.exit(-1);
            }
            if (Class != null) {
                for (Enum<?> enumConstant : Class.getEnumConstants()) {
                    if (!enumConstant.name().equals("UNRECOGNIZED")) {
                        Field valueField = enumClass.getDeclaredField("value");
                        valueField.setAccessible(true);
                        int value = (int) valueField.get(enumConstant);
                        String className = "org.sunrise.game.genProto.gen." + proto + "$M" + enumConstant.name();
                        Method method = null;
                        try {
                            method = java.lang.Class.forName(className).getMethod("parseFrom", ByteString.class);
                        } catch (Exception ignored) {}
                        if (method != null) {
                            protoParserMap.put(generateKey(packetType, value), method);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    public static <T extends Message> T parseProto(ByteString data, Parser<T> parser) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        try {
            return parser.parseFrom(data);
        } catch (InvalidProtocolBufferException e) {
            LogCore.BaseServer.error("parseProto, error : ", e);
            return null;
        }
    }
}
