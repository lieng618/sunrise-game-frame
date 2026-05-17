package org.sunrise.game.rpc.function;


import org.sunrise.game.core.server.BaseServerManager;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.rpc.annotation.RpcMethod;
import org.sunrise.game.rpc.annotation.RpcService;
import org.sunrise.game.rpc.service.BaseService;
import org.sunrise.game.rpc.service.ServiceManager;
import org.sunrise.game.utils.Utils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CallUtils {

    private static final Map<Integer, Method> rpcIdToMethodMap = new HashMap<>(); //rpcId-Method
    private static String curNodeId;

    /**
     * rpc初始化
     * @param nodeId 当前rpc服务器节点id
     * @param classPaths 要注册的rpc方法包路径
     * @param callEnumClass 生成的调用方法枚举类
     */
    public static void init(String nodeId, List<String> classPaths, Class<?> callEnumClass) {
        if (callEnumClass == null) {
            return;
        }
        curNodeId = nodeId;
        long startTime = System.currentTimeMillis();
        Map<String, Method> methodsCache = new HashMap<>();
        // 预先加载所有 classPaths 下的类和方法，并缓存
        for (String classPath : classPaths) {
            try {
                // 获取 classPath 下的所有类
                List<Class<?>> classes = Utils.findClasses(classPath); //获取包下的类
                for (Class<?> clazz : classes) {
                    if (!clazz.isAnnotationPresent(RpcService.class)) {
                        continue;
                    }
                    if (!BaseService.class.isAssignableFrom(clazz) || clazz == BaseService.class) {
                        continue;
                    }
                    long classStartTime = System.currentTimeMillis();
                    @SuppressWarnings("unchecked")
                    Class<? extends BaseService> serviceClass = (Class<? extends BaseService>) clazz;

                    String className = clazz.getSimpleName();
                    Method[] methods = clazz.getDeclaredMethods();
                    int classMethodCount = 0; // 记录类方法数
                    if (methods != null) {
                        for (Method method : methods) {
                            if (method.isAnnotationPresent(RpcMethod.class)) {
                                methodsCache.put(className + "_" + method.getName(), method);

                                // 类方法数加1
                                classMethodCount++;
                            }
                        }
                    }
                    try {
                        // 实例化类
                        ServiceManager.registerService(serviceClass.getConstructor(String.class).newInstance(nodeId));

                        long classEndTime = System.currentTimeMillis();
                        LogCore.RpcUtils.info("Load class end, name = { {} }, loaded {} methods, took {} ms", clazz.getName(), classMethodCount, classEndTime - classStartTime);
                    } catch (Exception e) {
                        LogCore.RpcUtils.warn("Failed to instantiate class: {} with nodeId: {}, error: {}", className, nodeId, e.getMessage());
                    }
                }
            } catch (Exception e) {
                LogCore.RpcUtils.warn("Failed to load classes from package: {}, error: {}", classPath, e.getMessage());
            }
        }

        Field[] fields = callEnumClass.getDeclaredFields();
        for (Field field : fields) {
            try {
                if (field.getType() == int.class && Modifier.isStatic(field.getModifiers())) {
                    int rpcId = field.getInt(null);
                    if (rpcIdToMethodMap.get(rpcId) != null) {
                        continue;
                    }

                    String fieldName = field.getName();
                    Method method = methodsCache.get(fieldName);
                    if (method != null) {
                        rpcIdToMethodMap.put(rpcId, method);
                    }
                }
            } catch (Exception e) {
                LogCore.RpcUtils.warn("Error processing field: {}, error: {}", field.getName(), e.getLocalizedMessage());
            }
        }

        LogCore.RpcUtils.info("CallUtils init end, took {} ms", System.currentTimeMillis() - startTime);
        initCurRegisterCallIds();
        // 初始化所有服务
        ServiceManager.initAll();
    }

    /**
     * 添加当前节点管理的rpc列表
     */
    private static void initCurRegisterCallIds() {
        for (Integer callId : getCallIds()) {
            List<String> list = RpcFunction.callIdNodes.get(callId);
            if (list == null) {
                list = new ArrayList<>();
            }
            list.add(curNodeId);
            RpcFunction.callIdNodes.put(callId, list);
        }
        RpcFunction.nodeId = curNodeId;
    }

    public static ArrayList<Integer> getCallIds() {
        return new ArrayList<>(rpcIdToMethodMap.keySet());
    }

    /**
     * rpcServer收到rpcClient发来的call调用之后的处理
     */
    public static void handler(Call call) {
        try {
            LogCore.RpcServer.debug("recv call, callId = {}, messageId = { {} }, cur NodeId = { {} }, from NodeId = { {} }, data = { {} }", call.getRpcId(), call.getMessageId(), curNodeId, call.getNodeId(), call.getData());

            // 将call请求压栈
            CallContext.push(call);

            int result = ErrorType.SUCCESS;
            Method method = rpcIdToMethodMap.get(call.getRpcId());
            if (method == null) {
                result = ErrorType.RPC_METHOD_NOT_FOUND;
            } else {
                if (call.getData().length / 2 != method.getParameterCount()) {
                    result = ErrorType.RPC_ARGS_NOT_MATCH;
                } else {
                    Object[] args = parseCallArgs(call, method);
                    BaseService service = ServiceManager.getService(method.getDeclaringClass().getSimpleName());
                    if (service == null) {
                        result = ErrorType.RPC_SERVICE_NOT_FOUND;
                    } else {
                        method.invoke(service, args);
                    }
                }
            }
            if (result > 0) {
                returns(curNodeId, result);
            }

        } catch (Exception e) {
            returns(curNodeId, ErrorType.RPC_CALL_CATCH);
            LogCore.RpcServer.error("recv call, handler error = {},  rpcId = {}, data = { {} }", e.getLocalizedMessage(), call.getRpcId(), call.getData());
        } finally {
            // 请求处理完，出栈
            CallContext.pop();
        }
    }

    public static void returns(String nodeId, int result, Object ... params) {
        Call call = CallContext.getLastCall();
        if (call == null) {
            return;
        }
        returns(call, nodeId, result, params);
    }

    public static void returns(Call from, String nodeId, int result, Object ... params) {
        Call rep = new Call(nodeId);
        rep.setType(CallType.CallResult.ordinal());
        rep.setToNodeId(from.getNodeId());
        rep.setRpcId(from.getRpcId());
        rep.setMessageId(from.getMessageId());
        rep.setResult(result);

        rep.setData(params);
        // 返回的目标节点是自己
        if (from.getNodeId().equals(nodeId)) {
            RpcManager.callResult(rep);
        } else {
            BaseServerManager.sendToClient(rep);
        }
    }

    // 将call中的数据，传递给方法的参数
    private static Object[] parseCallArgs(Call call, Method method) {
        Object[] args = new Object[method.getParameterCount()];
        for (int i = 0; i < args.length; i++) {
            args[i] = call.getData(i);
        }
        return args;
    }
}