package org.sunrise.game.game.modules;

import org.sunrise.game.game.annotation.HumanModule;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.utils.Utils;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ModuleUtils {

    private static final List<Class<? extends BaseModule>> moduleClasses = new ArrayList<>();

    public static void init(List<String> classPaths) {
        long startTime = System.currentTimeMillis();
        for (String classPath : classPaths) {
            try {
                List<Class<?>> classes = Utils.findClasses(classPath);
                for (Class<?> clazz : classes) {
                    if (!clazz.isAnnotationPresent(HumanModule.class)) {
                        continue;
                    }
                    if (!BaseModule.class.isAssignableFrom(clazz) || clazz == BaseModule.class) {
                        continue;
                    }
                    long classStartTime = System.currentTimeMillis();
                    @SuppressWarnings("unchecked")
                    Class<? extends BaseModule> moduleClass = (Class<? extends BaseModule>) clazz;
                    moduleClasses.add(moduleClass);
                    long classEndTime = System.currentTimeMillis();
                    LogCore.GameServer.info("Load class end, name = { {} }, took {} ms", clazz.getName(), classEndTime - classStartTime);
                }
            } catch (Exception e) {
                LogCore.GameServer.error("ModuleUtils init failed, error: {}", e.getMessage(), e);
            }
        }
        LogCore.GameServer.info("ModuleUtils init end, loaded {} modules, took {} ms", moduleClasses.size(), System.currentTimeMillis() - startTime);
    }

    public static List<BaseModule> createModules(String humanId) {
        if (moduleClasses.isEmpty()) {
            return Collections.emptyList();
        }
        List<BaseModule> modules = new ArrayList<>(moduleClasses.size());
        for (Class<? extends BaseModule> clazz : moduleClasses) {
            try {
                Constructor<? extends BaseModule> constructor = clazz.getConstructor(String.class);
                BaseModule module = constructor.newInstance(humanId);
                modules.add(module);
            } catch (Exception e) {
                LogCore.GameServer.error("ModuleFactory create module failed, class: {}, error: {}", clazz.getSimpleName(), e.getMessage(), e);
            }
        }
        return modules;
    }
}
