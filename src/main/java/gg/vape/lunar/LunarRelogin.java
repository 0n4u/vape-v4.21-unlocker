package gg.vape.lunar;

import gg.vape.Vape;
import gg.vape.reflect.LunarMappings;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public final class LunarRelogin {
    private static final String MANAGER_CLASS =
            "com.moonsworth.lunar.client.ROOORHOIRCHIOOCRCHIOICIOHRRIRC";
    private static final String MANAGER_GETTER =
            "OIIRRRIHHRORIICOCIIOOCCIHCROCO";
    private static final String UTIL_CLASS =
            "com.moonsworth.lunar.client.util.CHOORIOROOCCCHHOICHHIOCROHCRCH";
    private static final String SERVICE_HUB_CLASS =
            "com.moonsworth.lunar.client.HHICIOCHCIOHIHHROHHCHCRORORCHH.ROOORHOIRCHIOOCRCHIOICIOHRRIRC";

    
    
    private static final String CURRENT_SERVICE_HUB_GETTER =
            "OOICHOOHHCHRRHHHCIHIOCOHHIIIII";
    private static final String LEGACY_SERVICE_HUB_FIELD =
            "RCIOIRCCOOHIOIICOHRCOROIOCHHRI";

    
    
    private static final String CURRENT_EXECUTOR_GETTER =
            "ICCCHOHIHIHOCOOORROOHOHCIORHHO";
    private static final String LEGACY_EXECUTOR_GETTER =
            "RHIOCIRHRIIIIOOOCORCIHHOIOCIIC";


    
    
    private static final String CURRENT_REVISION_MARKER =
            "OROCHOCOHRHOCCCCRIRROHHHHHRCOO";

    private LunarRelogin() {
    }

    static List<String> loginMethods(LunarUnlockSettings settings,
                                     boolean currentRevision) {
        List<LunarServicePlan.ServiceEntry> enabled =
                LunarServicePlan.enabled(settings);
        List<String> methods = new ArrayList<String>(enabled.size());
        for (LunarServicePlan.ServiceEntry entry : enabled) {
            methods.add(currentRevision
                    ? entry.currentLoginMethod() : entry.legacyLoginMethod());
        }
        return Collections.unmodifiableList(methods);
    }

    public static void trigger() {
        Object executor = resolveRenderExecutor();
        if (executor != null) {
            try {
                Method submit = findMethod(executor.getClass(),
                        "bridge$submit", Runnable.class);
                if (submit != null) {
                    submit.setAccessible(true);
                    submit.invoke(executor, (Runnable) LunarRelogin::replay);
                    Vape.debugLog("LUNAR relogin: submitted to render thread");
                    return;
                }
            }
            catch (Throwable error) {
                Vape.debugLog("LUNAR relogin: render submit failed: " + error);
            }
        }

        Vape.debugLog("LUNAR relogin: render executor unavailable; running inline");
        replay();
    }

    private static Object resolveRenderExecutor() {
        try {
            Class<?> utilClass = LunarMappings.resolveClass(UTIL_CLASS);
            Object named = invokeStaticNoArg(utilClass, CURRENT_EXECUTOR_GETTER);
            if (hasSubmitBridge(named)) {
                return named;
            }
            named = invokeStaticNoArg(utilClass, LEGACY_EXECUTOR_GETTER);
            if (hasSubmitBridge(named)) {
                return named;
            }

            
            
            Class<?> current = utilClass;
            while (current != null) {
                for (Method method : current.getDeclaredMethods()) {
                    if (!Modifier.isStatic(method.getModifiers())
                            || method.getParameterTypes().length != 0
                            || !hasMethod(method.getReturnType(),
                            "bridge$submit", Runnable.class)) {
                        continue;
                    }
                    method.setAccessible(true);
                    Object candidate = method.invoke(null);
                    if (hasSubmitBridge(candidate)) {
                        Vape.debugLog("LUNAR relogin: render executor found structurally via "
                                + method.getName());
                        return candidate;
                    }
                }
                current = current.getSuperclass();
            }
        }
        catch (Throwable error) {
            Vape.debugLog("LUNAR relogin: render executor resolution failed: " + error);
        }
        return null;
    }

    private static void replay() {
        Object manager = resolveManager();
        if (manager == null) {
            return;
        }
        Object serviceHub = resolveServiceHub(manager);
        if (serviceHub == null) {
            return;
        }

        boolean currentRevision = hasMethod(serviceHub.getClass(),
                CURRENT_REVISION_MARKER);
        List<String> loginMethods = loginMethods(
                LunarUnlockSettings.current(), currentRevision);
        Vape.debugLog("LUNAR relogin: using "
                + (currentRevision ? "current" : "legacy") + " method map ("
                + loginMethods.size() + " enabled services)");
        if (loginMethods.isEmpty()) {
            Vape.debugLog("LUNAR relogin: no services enabled; skipping replay");
            return;
        }

        boolean anyInvoked = false;
        for (String methodName : loginMethods) {
            anyInvoked |= invokeLogin(serviceHub, methodName);
        }
        if (!anyInvoked) {
            Vape.debugLog("LUNAR relogin: no login method matched; reconnecting");
            fallbackReconnect(serviceHub);
        }
    }

    private static Object resolveManager() {
        try {
            Class<?> managerClass = LunarMappings.resolveClass(MANAGER_CLASS);
            Object manager = invokeStaticNoArg(managerClass, MANAGER_GETTER);
            Vape.debugLog("LUNAR relogin: manager resolved "
                    + (manager == null ? "<null>" : manager.getClass().getName()));
            return manager;
        }
        catch (Throwable error) {
            Vape.debugLog("LUNAR relogin: manager resolution failed: " + error);
            return null;
        }
    }

    private static Object resolveServiceHub(Object manager) {
        try {
            Object candidate = invokeNoArg(manager, CURRENT_SERVICE_HUB_GETTER);
            if (looksLikeServiceHub(candidate)) {
                Vape.debugLog("LUNAR relogin: service hub resolved by current getter");
                return candidate;
            }

            
            
            
            Class<?> current = manager.getClass();
            while (current != null) {
                for (Method method : current.getDeclaredMethods()) {
                    if (Modifier.isStatic(method.getModifiers())
                            || method.getParameterTypes().length != 0
                            || !looksLikeServiceHubType(method.getReturnType())) {
                        continue;
                    }
                    method.setAccessible(true);
                    candidate = method.invoke(manager);
                    if (looksLikeServiceHub(candidate)) {
                        Vape.debugLog("LUNAR relogin: service hub found structurally via "
                                + method.getName());
                        return candidate;
                    }
                }
                current = current.getSuperclass();
            }

            Field legacy = findField(manager.getClass(), LEGACY_SERVICE_HUB_FIELD);
            if (legacy != null) {
                legacy.setAccessible(true);
                candidate = legacy.get(manager);
                if (looksLikeServiceHub(candidate)) {
                    Vape.debugLog("LUNAR relogin: service hub resolved by legacy field");
                    return candidate;
                }
            }

            
            current = manager.getClass();
            while (current != null) {
                for (Field field : current.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())
                            || !looksLikeServiceHubType(field.getType())) {
                        continue;
                    }
                    field.setAccessible(true);
                    candidate = field.get(manager);
                    if (looksLikeServiceHub(candidate)) {
                        Vape.debugLog("LUNAR relogin: service hub found structurally in field "
                                + field.getName());
                        return candidate;
                    }
                }
                current = current.getSuperclass();
            }
        }
        catch (Throwable error) {
            Vape.debugLog("LUNAR relogin: service hub resolution failed: " + error);
            return null;
        }

        Vape.debugLog("LUNAR relogin: asset websocket service hub not found");
        return null;
    }

    private static boolean invokeLogin(Object serviceHub, String methodName) {
        try {
            Method method = findMethod(serviceHub.getClass(), methodName);
            if (method == null || method.getParameterTypes().length != 0) {
                Vape.debugLog("LUNAR relogin: method " + methodName
                        + " not found on " + serviceHub.getClass().getName());
                return false;
            }
            method.setAccessible(true);
            method.invoke(serviceHub);
            Vape.debugLog("LUNAR relogin: invoked " + methodName);
            return true;
        }
        catch (Throwable error) {
            Vape.debugLog("LUNAR relogin: invoking " + methodName
                    + " failed: " + error);
            return false;
        }
    }

    private static void fallbackReconnect(Object serviceHub) {
        try {
            Method reconnect = findMethod(serviceHub.getClass(), "reconnect");
            if (reconnect == null) {
                Vape.debugLog("LUNAR relogin: reconnect() unavailable; "
                        + "login will fire on the next websocket reconnect");
                return;
            }
            reconnect.setAccessible(true);
            reconnect.invoke(serviceHub);
            Vape.debugLog("LUNAR relogin: reconnect() invoked");
        }
        catch (Throwable error) {
            Vape.debugLog("LUNAR relogin: reconnect() failed: " + error);
        }
    }

    private static Object invokeStaticNoArg(Class<?> type, String name) {
        try {
            Method method = findMethod(type, name);
            if (method == null || method.getParameterTypes().length != 0
                    || !Modifier.isStatic(method.getModifiers())) {
                return null;
            }
            method.setAccessible(true);
            return method.invoke(null);
        }
        catch (Throwable ignored) {
            return null;
        }
    }

    private static Object invokeNoArg(Object target, String name) {
        try {
            Method method = findMethod(target.getClass(), name);
            if (method == null || method.getParameterTypes().length != 0
                    || Modifier.isStatic(method.getModifiers())) {
                return null;
            }
            method.setAccessible(true);
            return method.invoke(target);
        }
        catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean hasSubmitBridge(Object candidate) {
        return candidate != null && hasMethod(candidate.getClass(),
                "bridge$submit", Runnable.class);
    }

    private static boolean looksLikeServiceHub(Object candidate) {
        return candidate != null && looksLikeServiceHubType(candidate.getClass());
    }

    private static boolean looksLikeServiceHubType(Class<?> type) {
        Class<?> current = type;
        while (current != null) {
            if (SERVICE_HUB_CLASS.equals(current.getName())
                    || "org.java_websocket.client.WebSocketClient".equals(
                    current.getName())) {
                return true;
            }
            current = current.getSuperclass();
        }
        return false;
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            }
            catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static boolean hasMethod(Class<?> type, String name,
                                     Class<?>... parameterTypes) {
        return findMethod(type, name, parameterTypes) != null;
    }

    private static Method findMethod(Class<?> type, String name,
                                     Class<?>... parameterTypes) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod(name, parameterTypes);
            }
            catch (NoSuchMethodException ignored) {
                try {
                    return current.getMethod(name, parameterTypes);
                }
                catch (NoSuchMethodException ignoredPublic) {
                    current = current.getSuperclass();
                }
            }
        }
        return null;
    }
}
