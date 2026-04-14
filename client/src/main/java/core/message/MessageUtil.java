package core.message;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import core.message.annotation.Handler;
import org.sunrise.game.genProto.gen.TopicProto;
import org.sunrise.game.log.LogCore;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息工具类
 */
public class MessageUtil {
    // UI相关映射（用于界面显示）
    private static final Map<String, Integer> topicNumMap = new HashMap<>();
    private static final Map<String, Integer> idNumMap = new HashMap<>();
    private static final Map<String, Class<?>> idClassMap = new HashMap<>();
    private static final Map<Integer, Class<?>> registerTopic = new HashMap<>();

    /**
     * 初始化消息系统 - 注册所有处理器和协议解析器
     */
    public static void init() {
        registerHandlers();
        registerTopics();
    }

    /**
     * 注册所有消息处理器
     */
    private static void registerHandlers() {
        Method[] methods = MessageHandler.class.getDeclaredMethods();
        for (Method method : methods) {
            if (method.isAnnotationPresent(Handler.class)) {
                Handler handler = method.getAnnotation(Handler.class);
                ProtocolRouter.registerHandler(handler.packetType(), handler.packetId(), method);
            }
        }
    }

    /**
     * 注册所有主题和协议解析器 - 自动从TOPIC枚举中提取
     */
    private static void registerTopics() {
        // 遍历TOPIC枚举，自动提取Proto类名
        for (TopicProto.TOPIC topic : TopicProto.TOPIC.values()) {
            // 跳过UNRECOGNIZED
            if (topic == TopicProto.TOPIC.UNRECOGNIZED) {
                continue;
            }

            int packetType = topic.getNumber();
            // 从枚举名称中提取Proto类名（最后一个_之后的字符串）
            String protoClassName = extractProtoClassName(topic.name());
            if (protoClassName != null) {
                registerTopic(packetType, protoClassName);
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

    /**
     * 注册单个主题
     */
    private static void registerTopic(int packetType, String protoClassName) {
        try {
            // 注册主题名称映射（用于UI）
            TopicProto.TOPIC topic = TopicProto.TOPIC.forNumber(packetType);
            if (topic != null && topic != TopicProto.TOPIC.UNRECOGNIZED) {
                topicNumMap.put(topic.name(), packetType);
            }
            
            // 获取FROM_CLIENT枚举类
            Class<?> enumClass = Class.forName("org.sunrise.game.genProto.gen." + protoClassName + "$FROM_CLIENT");
            if (enumClass != null) {
                // 注册枚举类（用于UI）
                registerTopic.put(packetType, enumClass);

                // 遍历枚举常量，注册解析器和UI映射
                for (Enum<?> enumConstant : ((Class<? extends Enum<?>>) enumClass).getEnumConstants()) {
                    if (enumConstant.name().equals("UNRECOGNIZED")) {
                        continue;
                    }

                    Field valueField = enumClass.getDeclaredField("value");
                    valueField.setAccessible(true);
                    int packetId = (int) valueField.get(enumConstant);

                    // 注册协议解析器（用于接收消息）
                    String messageClassName = "org.sunrise.game.genProto.gen." + protoClassName + "$M" + enumConstant.name();

                    // 注册UI映射（用于界面显示）
                    idNumMap.put(packetType + enumConstant.name(), packetId);
                    try {
                        idClassMap.put(packetType + enumConstant.name(), Class.forName(messageClassName));
                    } catch (Exception ignored) {
                        // 某些消息类型可能没有对应的消息类
                    }
                }
            }

            // 获取FROM_SERVER枚举类
            Class<?> serverenumClass = Class.forName("org.sunrise.game.genProto.gen." + protoClassName + "$FROM_SERVER");
            if (serverenumClass != null) {
                // 遍历枚举常量，注册解析器
                for (Enum<?> enumConstant : ((Class<? extends Enum<?>>) serverenumClass).getEnumConstants()) {
                    if (enumConstant.name().equals("UNRECOGNIZED")) {
                        continue;
                    }

                    Field valueField = serverenumClass.getDeclaredField("value");
                    valueField.setAccessible(true);
                    int packetId = (int) valueField.get(enumConstant);

                    // 注册协议解析器（用于接收消息）
                    String messageClassName = "org.sunrise.game.genProto.gen." + protoClassName + "$M" + enumConstant.name();
                    try {
                        Method parserMethod = Class.forName(messageClassName).getMethod("parseFrom", ByteString.class);
                        ProtocolRouter.registerParser(packetType, packetId, parserMethod);
                    } catch (Exception ignored) {
                        // 某些消息类型可能没有对应的解析类
                    }
                }
            }
        } catch (Exception e) {
            LogCore.Client.error("Failed to register topic: packetType={}, proto={}", packetType, protoClassName, e);
        }
    }

    /**
     * 获取所有主题名称（用于UI下拉框）
     */
    public static String[] getTopicNames() {
        List<String> topicNames = new ArrayList<>(topicNumMap.keySet());
        Collections.sort(topicNames);
        return topicNames.toArray(new String[0]);
    }

    /**
     * 获取主题数字映射（用于UI）
     */
    public static Map<String, Integer> getTopicNumMap() {
        return Collections.unmodifiableMap(topicNumMap);
    }

    /**
     * 获取ID数字映射（用于UI）
     */
    public static Map<String, Integer> getIdNumMap() {
        return Collections.unmodifiableMap(idNumMap);
    }

    /**
     * 获取ID类映射（用于UI）
     */
    public static Map<String, Class<?>> getIdClassMap() {
        return Collections.unmodifiableMap(idClassMap);
    }

    /**
     * 获取注册的主题映射（用于UI）
     */
    public static Map<Integer, Class<?>> getRegisterTopic() {
        return Collections.unmodifiableMap(registerTopic);
    }

    /**
     * 获取protobuf类的字段描述符（用于UI）
     */
    public static Descriptors.FieldDescriptor[] getFields(Class<?> protoClass) {
        try {
            Method getDescriptorMethod = protoClass.getMethod("getDescriptor");
            Object descriptor = getDescriptorMethod.invoke(null);
            Method getFieldsMethod = descriptor.getClass().getMethod("getFields");
            @SuppressWarnings("unchecked")
            List<Descriptors.FieldDescriptor> fields = (List<Descriptors.FieldDescriptor>) getFieldsMethod.invoke(descriptor);
            return fields.toArray(new Descriptors.FieldDescriptor[0]);
        } catch (Exception ex) {
            LogCore.Client.error("Failed to get fields for class: {}", protoClass.getName(), ex);
            return new Descriptors.FieldDescriptor[0];
        }
    }

    /**
     * 将下划线命名转换为驼峰命名
     */
    public static String toCamelCase(String fieldName) {
        String[] parts = fieldName.split("_");
        if (parts.length == 0) {
            return fieldName;
        }
        StringBuilder camelCaseString = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (parts[i].isEmpty()) {
                continue;
            }
            camelCaseString.append(parts[i].substring(0, 1).toUpperCase())
                    .append(parts[i].substring(1).toLowerCase());
        }
        return camelCaseString.toString();
    }

    /**
     * 设置protobuf builder的字段值（用于UI）
     */
    public static void invoke(Message.Builder builder, Descriptors.FieldDescriptor fieldDescriptor, String text) {
        try {
            String fieldName = fieldDescriptor.getName();
            String camelCaseFieldName = toCamelCase(fieldName);
            String setterMethodName = "set" + Character.toUpperCase(camelCaseFieldName.charAt(0)) 
                    + camelCaseFieldName.substring(1);
            String addAllMethodName = "addAll" + Character.toUpperCase(camelCaseFieldName.charAt(0)) 
                    + camelCaseFieldName.substring(1);

            if (fieldDescriptor.isRepeated()) {
                Method addAllMethod = builder.getClass().getMethod(addAllMethodName, Iterable.class);
                List<?> listValue = parseRepeatedField(fieldDescriptor, text);
                addAllMethod.invoke(builder, listValue);
            } else {
                Object value = parseFieldValue(fieldDescriptor, text);
                Class<?> javaType = getJavaType(fieldDescriptor);
                Method setter = builder.getClass().getMethod(setterMethodName, javaType);
                setter.invoke(builder, value);
            }
        } catch (Exception e) {
            LogCore.Client.error("Failed to invoke setter for field: {}", fieldDescriptor.getName(), e);
        }
    }

    /**
     * 解析字段值
     */
    private static Object parseFieldValue(Descriptors.FieldDescriptor fieldDescriptor, String text) {
        switch (fieldDescriptor.getType()) {
            case INT32:
            case SINT32:
            case SFIXED32:
            case UINT32:
            case FIXED32:
                return Integer.parseInt(text);
            case INT64:
            case SINT64:
            case SFIXED64:
            case UINT64:
            case FIXED64:
                return Long.parseLong(text);
            case BOOL:
                return Boolean.parseBoolean(text);
            case FLOAT:
                return Float.parseFloat(text);
            case DOUBLE:
                return Double.parseDouble(text);
            case STRING:
                return text;
            case BYTES:
                return ByteString.copyFrom(text.getBytes());
            case ENUM:
                return fieldDescriptor.getEnumType().findValueByName(text);
            default:
                throw new IllegalArgumentException("Unsupported field type: " + fieldDescriptor.getType());
        }
    }

    /**
     * 获取Java类型
     */
    private static Class<?> getJavaType(Descriptors.FieldDescriptor fieldDescriptor) {
        return switch (fieldDescriptor.getType()) {
            case INT32, SINT32, SFIXED32, UINT32, FIXED32 -> Integer.TYPE;
            case INT64, SINT64, SFIXED64, UINT64, FIXED64 -> Long.TYPE;
            case BOOL -> Boolean.TYPE;
            case FLOAT -> Float.TYPE;
            case DOUBLE -> Double.TYPE;
            case STRING -> String.class;
            case BYTES -> ByteString.class;
            case ENUM -> fieldDescriptor.getEnumType().getClass();
            default -> throw new IllegalArgumentException("Unsupported field type: " + fieldDescriptor.getType());
        };
    }

    /**
     * 解析重复字段
     */
    private static List<?> parseRepeatedField(Descriptors.FieldDescriptor fieldDescriptor, String text) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }
        
        String[] parts = text.split(",");
        List<Object> list = new ArrayList<>();
        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) {
                continue;
            }
            
            switch (fieldDescriptor.getType()) {
                case INT32, SINT32, SFIXED32, UINT32, FIXED32:
                    list.add(Integer.parseInt(part));
                    break;
                case INT64, SINT64, SFIXED64, UINT64, FIXED64:
                    list.add(Long.parseLong(part));
                    break;
                case BOOL:
                    list.add(Boolean.parseBoolean(part));
                    break;
                case FLOAT:
                    list.add(Float.parseFloat(part));
                    break;
                case DOUBLE:
                    list.add(Double.parseDouble(part));
                    break;
                case STRING:
                    list.add(part);
                    break;
                case BYTES:
                    list.add(ByteString.copyFrom(part.getBytes()));
                    break;
                case ENUM:
                    list.add(fieldDescriptor.getEnumType().findValueByName(part));
                    break;
                default:
                    LogCore.Client.warn("Unsupported repeated field type: {}", fieldDescriptor.getType());
            }
        }
        return list;
    }
}
