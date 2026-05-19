package org.sunrise.game.core;

import java.io.*;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class Hotswap {
    private static Instrumentation inst = null;

    public static void premain(String agentArgs, Instrumentation i) {
        inst = i;
    }

    public static void reloadClasses(Map<String, HotswapScanner.JarEntryInfo> reloadEntries, String jarPath) throws IOException, ClassNotFoundException, UnmodifiableClassException {
        try (JarFile jarFile = new JarFile(jarPath)) {
            Map<Class<?>, byte[]> reloadClassBytes = new HashMap<>();

            for (Map.Entry<String, HotswapScanner.JarEntryInfo> it : reloadEntries.entrySet()) {
                try (InputStream stream = jarFile.getInputStream(jarFile.getEntry(it.getKey()))) {
                    Class<?> clazz = Class.forName(it.getValue().getClazzName());
                    reloadClassBytes.put(clazz, stream.readAllBytes());
                }
            }
            reloadClasses(reloadClassBytes);
        }
    }

    public static void reload(Class<?> cls, File file) throws IOException, ClassNotFoundException, UnmodifiableClassException {
        byte[] code = loadBytes(cls, file);
        if (code == null) {
            throw new IOException("Unknown File");
        } else {
            ClassDefinition def = new ClassDefinition(cls, code);
            inst.redefineClasses(new ClassDefinition[]{def});
            System.err.println(cls.getName() + " reloaded");
        }
    }

    public static void reloadClasses(Map<Class<?>, byte[]> classBytes) throws ClassNotFoundException, UnmodifiableClassException {
        if (classBytes.isEmpty()) return;

        List<ClassDefinition> clazzDefs = new ArrayList<>(classBytes.size());
        for (Map.Entry<Class<?>, byte[]> it : classBytes.entrySet()) {
            ClassDefinition clazzDef = new ClassDefinition(it.getKey(), it.getValue());
            clazzDefs.add(clazzDef);
        }
        if (clazzDefs.isEmpty()) return;
        ClassDefinition[] defs = new ClassDefinition[clazzDefs.size()];
        for (int i = 0; i < clazzDefs.size(); ++i) {
            defs[i] = clazzDefs.get(i);
        }
        inst.redefineClasses(defs);
    }

    private static byte[] loadBytes(Class<?> cls, File file) throws IOException, ClassNotFoundException {
        String name = file.getName();
        if (name.endsWith(".jar")) {
            return loadBytesFromJarFile(cls, file);
        } else {
            return name.endsWith(".class") ? loadBytesFromClassFile(file) : null;
        }
    }

    private static byte[] loadBytesFromClassFile(File classFile) throws IOException {
//        byte[] buffer = new byte[(int) classFile.length()];
//        FileInputStream fis = new FileInputStream(classFile);
//        BufferedInputStream bis = new BufferedInputStream(fis);
//
//        try {
//            bis.read(buffer);
//        } catch (IOException var8) {
//            throw var8;
//        } finally {
//            bis.close();
//        }
//
//        return buffer;
        try (FileInputStream fis = new FileInputStream(classFile);
             BufferedInputStream bis = new BufferedInputStream(fis)) {
            return bis.readAllBytes();
        }
    }

    private static byte[] loadBytesFromJarFile(Class<?> cls, File file) throws IOException, ClassNotFoundException {
//        try (JarFile jarFile = new JarFile(file)) {
//            String name = cls.getName();
//            name = name.replaceAll("\\.", "/") + ".class";
//            JarEntry en = jarFile.getJarEntry(name);
//            if (en == null) {
//                throw new ClassNotFoundException(name);
//            } else {
//                byte[] buffer = new byte[(int) en.getSize()];
//                BufferedInputStream bis = new BufferedInputStream(jarFile.getInputStream(en));
//                try {
//                    bis.read(buffer);
//                } catch (IOException var11) {
//                    throw var11;
//                } finally {
//                    bis.close();
//                }
//
//                return buffer;
//            }
//        }
        try (JarFile jarFile = new JarFile(file)) {
            String name = cls.getName().replace('.', '/') + ".class";
            JarEntry en = jarFile.getJarEntry(name);
            if (en == null) throw new ClassNotFoundException(name);

            try (InputStream is = jarFile.getInputStream(en)) {
                return is.readAllBytes();
            }
        }
    }
}
