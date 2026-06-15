package org.sunrise.game.rpc.function;

import org.sunrise.game.core.server.BaseServerManager;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.rpc.annotation.RpcMethod;
import org.sunrise.game.rpc.annotation.RpcService;
import org.sunrise.game.rpc.service.BaseService;
import org.sunrise.game.rpc.service.ServiceManager;
import org.sunrise.game.startup.FatalStartupException;
import org.sunrise.game.utils.Utils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public class CallUtils {

    private static final Map<Integer, Method> rpcIdToMethodMap = new HashMap<>(); //rpcId-Method
    private static String curNodeId;

    /**
     * RPC 服务扫描注册，由 {@link org.sunrise.game.rpc.node.RpcNodeManager} 在创建节点时自动调用。
     *
     * @param nodeId        当前 RPC 节点 ID，注入到各 {@link BaseService} 构造函数
     * @param classPaths    要扫描的包列表（来自 {@code rpc.scan.packages} 或 nodeType 约定）
     * @param callEnumClass 生成的 CallEnum，其 static int 字段名须与 {@code ServiceName_methodName} 缓存键一致
     * @param strict        为 true 时任一服务注册失败抛出 {@link FatalStartupException}
     */
    public static void init(String nodeId, List<String> classPaths, Class<?> callEnumClass, boolean strict) {
        if (callEnumClass == null) {
            return;
        }
        curNodeId = nodeId;
        long startTime = System.currentTimeMillis();
        Map<String, Method> methodsCache = new HashMap<>();
        // 1. 扫描包内 @RpcService，实例化并缓存 @RpcMethod
        RegistrationResult registration = registerServices(nodeId, classPaths, methodsCache);
        // 2. 将 CallEnum 中的 rpcId 与 methodsCache 中的 Method 绑定
        bindCallEnumMethods(callEnumClass, methodsCache);
        // 3. 校验注册结果（strict 模式、至少一个服务）
        validateRegistration(classPaths, strict, startTime, registration);
        initCurRegisterCallIds();
        ServiceManager.initAll();
    }

    /** 扫描阶段的注册成功/失败服务名列表，供汇总日志与 strict 校验 */
    private record RegistrationResult(List<String> registered, List<String> failed) {
    }

    /** 遍历各扫描包，收集 @RpcService 类并尝试注册到 {@link ServiceManager} */
    private static RegistrationResult registerServices(String nodeId, List<String> classPaths, Map<String, Method> methodsCache) {
        List<String> registeredServices = new ArrayList<>();
        List<String> failedServices = new ArrayList<>();
        for (String classPath : classPaths) {
            try {
                List<Class<?>> classes = Utils.findClasses(classPath);
                for (Class<?> clazz : classes) {
                    registerServiceClass(nodeId, clazz, methodsCache, registeredServices, failedServices);
                }
            } catch (Exception e) {
                failedServices.add(classPath + ": " + e.getMessage());
                LogCore.RpcUtils.warn("Failed to load classes from package: {}, error: {}", classPath, e.getMessage());
            }
        }
        return new RegistrationResult(registeredServices, failedServices);
    }

    /** 处理单个候选类：过滤非 Service 后实例化，失败时记入 failedServices 而非中断（由 validateRegistration 决定） */
    private static void registerServiceClass(
            String nodeId,
            Class<?> clazz,
            Map<String, Method> methodsCache,
            List<String> registeredServices,
            List<String> failedServices) {
        if (!clazz.isAnnotationPresent(RpcService.class)) {
            return;
        }
        if (!BaseService.class.isAssignableFrom(clazz) || clazz == BaseService.class) {
            return;
        }
        long classStartTime = System.currentTimeMillis();
        @SuppressWarnings("unchecked")
        Class<? extends BaseService> serviceClass = (Class<? extends BaseService>) clazz;

        String className = clazz.getSimpleName();
        int classMethodCount = cacheRpcMethods(clazz, className, methodsCache);
        try {
            ServiceManager.registerService(serviceClass.getConstructor(String.class).newInstance(nodeId));
            registeredServices.add(clazz.getName());
            LogCore.RpcUtils.info(
                    "Load class end, name = { {} }, loaded {} methods, took {} ms",
                    clazz.getName(),
                    classMethodCount,
                    System.currentTimeMillis() - classStartTime);
        } catch (Exception e) {
            failedServices.add(clazz.getName() + ": " + e.getMessage());
            LogCore.RpcUtils.warn("Failed to instantiate class: {} with nodeId: {}, error: {}", className, nodeId, e.getMessage());
        }
    }

    /** 缓存键格式 {@code ClassSimpleName_methodName}，与 CallEnum 字段名一一对应 */
    private static int cacheRpcMethods(Class<?> clazz, String className, Map<String, Method> methodsCache) {
        int classMethodCount = 0;
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(RpcMethod.class)) {
                methodsCache.put(className + "_" + method.getName(), method);
                classMethodCount++;
            }
        }
        return classMethodCount;
    }

    /** 遍历 CallEnum 的 static int 字段，建立 rpcId → Method 映射 */
    private static void bindCallEnumMethods(Class<?> callEnumClass, Map<String, Method> methodsCache) {
        for (Field field : callEnumClass.getDeclaredFields()) {
            try {
                if (field.getType() != int.class || !Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                int rpcId = field.getInt(null);
                if (rpcIdToMethodMap.containsKey(rpcId)) {
                    continue;
                }
                Method method = methodsCache.get(field.getName());
                if (method != null) {
                    rpcIdToMethodMap.put(rpcId, method);
                }
            } catch (Exception e) {
                LogCore.RpcUtils.warn("Error processing field: {}, error: {}", field.getName(), e.getLocalizedMessage());
            }
        }
    }

    /**
     * 启动阶段校验：输出汇总日志；strict 模式下任一失败即退出；扫描结果为空一律视为配置错误。
     */
    private static void validateRegistration(
            List<String> classPaths,
            boolean strict,
            long startTime,
            RegistrationResult registration) {
        List<String> registeredServices = registration.registered();
        List<String> failedServices = registration.failed();
        LogCore.RpcUtils.info(
                "CallUtils init end, services = {}/{}, rpc methods = {}, scan packages = {}, took {} ms",
                registeredServices.size(),
                registeredServices.size() + failedServices.size(),
                rpcIdToMethodMap.size(),
                classPaths,
                System.currentTimeMillis() - startTime);
        if (!failedServices.isEmpty()) {
            StringJoiner joiner = new StringJoiner("; ");
            failedServices.forEach(joiner::add);
            if (strict) {
                throw new FatalStartupException("RPC service registration failed: " + joiner);
            }
            // 非 strict：仅告警，允许开发环境跳过个别无法实例化的服务
            LogCore.RpcUtils.warn("RPC service registration failures (rpc.init.strict=false): {}", joiner);
        }
        if (registeredServices.isEmpty()) {
            throw new FatalStartupException("No RPC services registered, scan packages = " + classPaths);
        }
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
                Object[] callData = call.getData();
                int paramCount = callData == null ? 0 : callData.length;
                if (paramCount != method.getParameterCount()) {
                    result = ErrorType.RPC_ARGS_NOT_MATCH;
                } else {
                    Object[] args;
                    try {
                        args = parseCallArgs(call, method);
                    } catch (IllegalArgumentException e) {
                        LogCore.RpcServer.warn(
                                "RPC argument type mismatch, rpcId = {}, expected parameter types = {}, data = {}, error = {}",
                                call.getRpcId(), method.getParameterTypes(), call.getData(), e.getMessage());
                        result = ErrorType.RPC_ARG_TYPE_MISMATCH;
                        args = null;
                    }
                    if (args != null) {
                        BaseService service = ServiceManager.getService(method.getDeclaringClass().getSimpleName());
                        if (service == null) {
                            result = ErrorType.RPC_SERVICE_NOT_FOUND;
                        } else {
                            method.invoke(service, args);
                        }
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
        Class<?>[] parameterTypes = method.getParameterTypes();
        Object[] args = new Object[parameterTypes.length];
        for (int i = 0; i < args.length; i++) {
            args[i] = RpcArgConverter.convert(call.getData(i), parameterTypes[i]);
        }
        return args;
    }
}