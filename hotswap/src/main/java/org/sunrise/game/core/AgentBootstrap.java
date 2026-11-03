package org.sunrise.game.core;

import java.lang.instrument.Instrumentation;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;

/**
 * Agent Jar 中唯一需要提前加载的引导类。
 * 热更内核位于共享 sunrise-lib.jar，避免 Agent 与业务 ClassLoader 各自加载一份 Hotswap。
 */
public final class AgentBootstrap {
    private static final String RUNTIME_CLASS = "org.sunrise.game.core.Hotswap";
    private static JarFile runtimeJar;

    private AgentBootstrap() {
    }

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        try {
            ensureRuntimeOnSystemClassPath(instrumentation);
            Class<?> runtimeClass = Class.forName(RUNTIME_CLASS, true, ClassLoader.getSystemClassLoader());
            runtimeClass.getMethod("install", Instrumentation.class).invoke(null, instrumentation);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("hotswap agent runtime is unavailable: " + RUNTIME_CLASS, e);
        } catch (Exception e) {
            throw new IllegalStateException("hotswap agent bootstrap failed", e);
        }
    }

    private static void ensureRuntimeOnSystemClassPath(Instrumentation instrumentation) throws Exception {
        try {
            Class.forName(RUNTIME_CLASS, false, ClassLoader.getSystemClassLoader());
            return;
        } catch (ClassNotFoundException ignored) {
            // The -jar main manifest has not been processed yet; add the sibling runtime explicitly.
        }

        Path runtimePath = resolveRuntimePath();
        if (runtimePath == null || !Files.isRegularFile(runtimePath)) {
            throw new IllegalStateException("sunrise-lib.jar not found next to hotswap-agent.jar");
        }
        runtimeJar = new JarFile(runtimePath.toFile());
        instrumentation.appendToSystemClassLoaderSearch(runtimeJar);
    }

    private static Path resolveRuntimePath() throws URISyntaxException {
        String configured = System.getProperty("sunrise.hotswap.runtime");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }
        Path agentPath = Path.of(AgentBootstrap.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();
        return agentPath.getParent() == null ? null : agentPath.getParent().resolve("sunrise-lib.jar");
    }
}
