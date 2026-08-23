package gg.vape.reflect;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

 
public final class Vanilla1165Mappings {
    private static final VanillaSrgMappings MAPPINGS = new VanillaSrgMappings(
            "Minecraft 1.16.5",
            "/mappings/vanilla1165/joined.srg",
            "djz",
            "C",
            "F",
            "dwt",
            "dzm",
            "dzj",
            "brx");

    private Vanilla1165Mappings() {
    }

    public static String remapClassName(String sourceClassName) {
        return MAPPINGS.remapClassName(sourceClassName);
    }

    public static Class<?> resolveClass(String sourceClassName,
                                        ClassLoader... preferredLoaders) {
        return MAPPINGS.resolveClass(sourceClassName, preferredLoaders);
    }

    public static boolean isRuntimePresent(ClassLoader... preferredLoaders) {
        return MAPPINGS.isRuntimePresent(preferredLoaders);
    }

    static String lookupFieldSrgName(Field field) {
        return MAPPINGS.lookupFieldSrgName(field);
    }

    static String lookupMethodSrgName(Method method) {
        return MAPPINGS.lookupMethodSrgName(method);
    }

    public static int getClassMappingCount() {
        return MAPPINGS.getClassMappingCount();
    }

    public static int getFieldMappingCount() {
        return MAPPINGS.getFieldMappingCount();
    }

    public static int getMethodMappingCount() {
        return MAPPINGS.getMethodMappingCount();
    }
}
