package gg.vape.reflect;

import gg.vape.Vape;

import java.net.URL;
import java.util.HashSet;
import java.util.Set;


public final class LunarMappings {
    private static final String[] MARKER_RESOURCES = {
            "com/lunarclient/websocket/badge/v1/BadgeService$Stub.class",
            "com/lunarclient/websocket/cosmetic/v2/CosmeticService$Stub.class",
            "com/lunarclient/websocket/emote/v1/EmoteService$Stub.class",
            "com/lunarclient/websocket/spray/v1/SprayService$Stub.class"
    };

    private LunarMappings() {
    }

    public static boolean isRuntimePresent() {
        Set<ClassLoader> loaders = enumerateAllClassLoaders();
        boolean found = hasLunarMarker(loaders);
        Vape.debugLog("LUNAR detection: " + (found ? "YES" : "NO")
                + " (" + loaders.size() + " loaders probed)");
        return found;
    }

    public static Class<?> resolveClass(String sourceClassName) {
        Set<ClassLoader> loaders = enumerateAllClassLoaders();
        for (ClassLoader loader : loaders) {
            try {
                return Class.forName(sourceClassName, false, loader);
            }
            catch (ClassNotFoundException ignored) {
            }
            catch (LinkageError ignored) {
            }
        }
        throw new IllegalStateException(
                "Unable to resolve Lunar class " + sourceClassName
                + " (" + loaders.size() + " loaders tried)");
    }

    private static boolean hasLunarMarker(Set<ClassLoader> loaders) {
        for (ClassLoader loader : loaders) {
            for (String marker : MARKER_RESOURCES) {
                try {
                    URL resource = loader.getResource(marker);
                    if (resource != null) {
                        Vape.debugLog("LUNAR marker found: " + marker
                                + " @ " + resource + " [" + loader + "]");
                        return true;
                    }
                }
                catch (RuntimeException ignored) {
                }
                catch (LinkageError ignored) {
                }
            }
        }
        
        
        
        String probeName = MARKER_RESOURCES[0]
                .replace('/', '.').replace(".class", "");
        for (ClassLoader loader : loaders) {
            try {
                Class.forName(probeName, false, loader);
                Vape.debugLog("LUNAR marker found via Class.forName: "
                        + probeName + " [" + loader + "]");
                return true;
            }
            catch (ClassNotFoundException ignored) {
            }
            catch (LinkageError ignored) {
            }
        }
        return false;
    }

    private static Set<ClassLoader> enumerateAllClassLoaders() {
        Set<ClassLoader> loaders = new HashSet<>();

        
        
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            ClassLoader cl = thread.getContextClassLoader();
            if (cl != null) {
                addWithParents(loaders, cl);
            }
        }

        
        ClassLoader own = LunarMappings.class.getClassLoader();
        if (own != null) {
            addWithParents(loaders, own);
        }

        
        try {
            ClassLoader system = ClassLoader.getSystemClassLoader();
            if (system != null) {
                addWithParents(loaders, system);
            }
        }
        catch (SecurityException ignored) {
        }

        
        try {
            Class<?> cl = Class.forName("java.lang.ClassLoader$PlatformClassLoader");
            
            
        }
        catch (ClassNotFoundException ignored) {
        }

        return loaders;
    }

    private static void addWithParents(Set<ClassLoader> loaders,
                                        ClassLoader loader) {
        ClassLoader current = loader;
        while (current != null) {
            if (!loaders.add(current)) {
                break; 
            }
            current = current.getParent();
        }
    }
}