package org.sunrise.game.game.logic;

import com.google.protobuf.ByteString;
import org.sunrise.game.game.annotation.MsgHandlerClass;
import org.sunrise.game.game.annotation.MsgHandlerMethod;
import org.sunrise.game.game.human.HumanObject;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.utils.Utils;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LogicUtils {
    private static final Map<Integer, Method> idToMethodMap = new HashMap<>(); //id-Method

    public static void init(List<String> classPaths) {
        long totalStartTime = System.currentTimeMillis();
        int totalMethodCount = 0; // 记录总方法数

        for (String classPath : classPaths) {
            try {
                // 获取 classPath 下的所有类
                List<Class<?>> classes = Utils.findClasses(classPath); // 获取包下的类

                for (Class<?> clazz : classes) {
                    // 检查类上是否有 @MsgHandlerClass 注解
                    if (!clazz.isAnnotationPresent(MsgHandlerClass.class)) {
                        continue;
                    }

                    // 记录类的加载时间
                    long classStartTime = System.currentTimeMillis();

                    // 获取类上的 packetType
                    MsgHandlerClass classAnnotation = clazz.getAnnotation(MsgHandlerClass.class);
                    int packetType = classAnnotation.packetType();

                    Method[] methods = clazz.getDeclaredMethods();
                    int classMethodCount = 0; // 记录类方法数
                    if (methods != null) {
                        for (Method method : methods) {
                            // 检查方法上是否有 @MsgHandlerMethod 注解
                            if (method.isAnnotationPresent(MsgHandlerMethod.class)) {

                                MsgHandlerMethod methodAnnotation = method.getAnnotation(MsgHandlerMethod.class);
                                int packetId = methodAnnotation.packetId();

                                // 生成唯一的 ID：type × 10000 + id
                                int uniqueId = packetType * 10000 + packetId;

                                // 将 uniqueId 和方法映射到 idToMethodMap
                                idToMethodMap.put(uniqueId, method);

                                // 每加载一个方法，方法总数加1
                                totalMethodCount++;
                                // 类方法数加1
                                classMethodCount++;
                            }
                        }
                    }

                    long classEndTime = System.currentTimeMillis();
                    LogCore.GameServer.info("Load class end, name = { {} }, loaded {} methods, took {} ms", clazz.getName(), classMethodCount, classEndTime - classStartTime);
                }
            } catch (Exception e) {
                LogCore.GameServer.warn("Failed to load classes from package: {}, error: {}", classPath, e.getMessage());
            }
        }

        // 输出总耗时、加载的总方法数
        LogCore.GameServer.info("LogicUtils init end, loaded {} methods, took {} ms", totalMethodCount, System.currentTimeMillis() - totalStartTime);
    }

    public static void handler(HumanObject humanObject, int packetType, int packetId, ByteString packetData) {
        Method method = idToMethodMap.get(packetType * 10000 + packetId);
        if (method == null) {
            LogCore.GameServer.warn("No handler found for packetType: {}, packetId: {}", packetType, packetId);
            return;
        }
        long startTime = System.currentTimeMillis();
        try {
            Class<?>[] parameterTypes = method.getParameterTypes();

            if (parameterTypes.length == 1 && parameterTypes[0] == HumanObject.class) {
                LogCore.GameServer.debug("recv msg, humanId = {}, handler = {}.{}, packetType = {}, packetId = {}", humanObject.getHumanId(),
                        method.getDeclaringClass().getSimpleName(), method.getName(), packetType, packetId);
                method.invoke(null, humanObject);
            } else if (parameterTypes.length == 2 && parameterTypes[0] == HumanObject.class) {
                Object parseData = null;
                var parseMethod = ProtoParserUtils.getProtoParserClass(packetType, packetId);
                if (parseMethod != null && packetData != null && !packetData.isEmpty()) {
                    parseData = parseMethod.invoke(null, packetData);
                }
                LogCore.GameServer.debug("recv msg, humanId = {}, handler =  {}.{}, packetType = {}, packetId = {}, msgData = {{}}", humanObject.getHumanId(),
                        method.getDeclaringClass().getSimpleName(), method.getName(), packetType, packetId, parseData == null ? "" : parseData.toString().replace("\n", ""));
                method.invoke(null, humanObject, parseData);
            } else {
                LogCore.GameServer.warn("Handler method has unsupported parameter types for packetType: {}, packetId: {}", packetType, packetId);
            }
        } catch (Exception e) {
            LogCore.GameServer.error("Error invoking handler for packetType: {}, packetId: {}, error: {}", packetType, packetId, e.getMessage());
        }
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime; // 计算耗时

        LogCore.GameServer.debug("Handler end, for packetType: {}, packetId: {}, took {} ms", packetType, packetId, duration);
    }

}
