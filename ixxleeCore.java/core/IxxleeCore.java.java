package com.ixxlee.core;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.lang.reflect.Method;

public class IxxleeCore {

    public static void addJarToClasspath(String jarPath) {
        try {
            File file = new File(jarPath);
            if (!file.exists()) {
                throw new RuntimeException("JAR not found: " + jarPath);
            }

            URLClassLoader sysLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
            Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            method.setAccessible(true);
            method.invoke(sysLoader, file.toURI().toURL());
        } catch (Exception e) {
            throw new RuntimeException("Cannot add JAR to classpath", e);
        }
    }

    public static void initRuntimeJars() {
        addJarToClasspath("libs/kotlin-stdlib.jar");
        addJarToClasspath("libs/your-runtime-dependency.jar");
    }

    public static void main(String[] args) {
        File jar = new File("app-all.jar");
        if (!jar.exists()) {
            System.out.println("app-all.jar not found in " + jar.getAbsoluteFile().getParent());
            return;
        }

        try {
            Process process = new ProcessBuilder("java", "-jar", jar.getAbsolutePath())
                .directory(jar.getParentFile() != null ? jar.getParentFile() : new File("."))
                .inheritIO()
                .start();

            int exitCode = process.waitFor();
            System.out.println("app-all.jar exited with code " + exitCode);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

// ---------------------------------------------------------
// Developer Mode
// ---------------------------------------------------------

class DevModeManager {
    private static final String PASSCODE = "006660";
    private static boolean enabled = false;

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean unlock(String input) {
        boolean ok = input.equals(PASSCODE);
        if (ok) enabled = true;
        return ok;
    }
}