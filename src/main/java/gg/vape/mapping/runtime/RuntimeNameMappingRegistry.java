package gg.vape.mapping.runtime;

import gg.vape.Vape;
import gg.vape.runtime.NativeBridge;
import gg.vape.wrapper.impl.ForgeVersion;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

public class RuntimeNameMappingRegistry {
    private static MemberNameRemapTable memberNameRemapTable;
    private static final Map<String, String> registeredClassNames;
    private static final ClassNameRemapTable classNameRemapTable;

    public static void registerClassName(String sourceClassName, String runtimeClassName) {
        registeredClassNames.put(sourceClassName.replace("/", "."), runtimeClassName.replace("/", "."));
        NativeBridge.scm(sourceClassName, runtimeClassName);
    }

    @Nullable
    public static MemberLookupSignature lookupMethodMapping(Class ownerClass, String methodName) {
        return lookupMethodMapping(ownerClass, methodName, null);
    }

    @Nullable
    public static MemberLookupSignature lookupMethodMapping(Class ownerClass, String methodName, Class<?>[] parameterTypes) {
        if (memberNameRemapTable == null) {
            return null;
        }
        MemberLookupSignature signature = memberNameRemapTable.lookupMethodMapping(ownerClass, methodName);
        if (signature == null) {
            
            
            
            
            
            
            if (!NativeBridge.isForgeAbsent()) {
                return null;
            }
            int version = ForgeVersion.c();
            if (version == 47 && !NativeBridge.isNeoForge1201Runtime()) {
                String obfuscated = NeoForgeObfMap.lookupMethod1201(
                        ownerClass, methodName, buildParamDesc(parameterTypes));
                if (obfuscated != null) {
                    return new MemberLookupSignature(obfuscated, null, null, parameterTypes);
                }
            } else if (version == 52 && !NativeBridge.isNeoForge1211Runtime()) {
                String obfuscated = NeoForgeObfMap.lookupMethod1211(
                        ownerClass, methodName, buildParamDesc(parameterTypes));
                if (obfuscated != null) {
                    return new MemberLookupSignature(obfuscated, null, null, parameterTypes);
                }
            }
            return null;
        }
        
        
        
        
        if (!NativeBridge.isForgeAbsent()) {
            return signature;
        }
        int version = ForgeVersion.c();
        if (version == 47 && !NativeBridge.isNeoForge1201Runtime()) {
            String obfuscated = NeoForgeObfMap.lookupMethod1201(
                    ownerClass, signature.runtimeName, buildParamDesc(descTypes(signature, parameterTypes)));
            if (obfuscated != null) {
                return new MemberLookupSignature(obfuscated,
                        signature.getMappedMemberOverride(), signature.resolvedType, signature.parameterTypes);
            }
        } else if (version == 52 && !NativeBridge.isNeoForge1211Runtime()) {
            String obfuscated = NeoForgeObfMap.lookupMethod1211(
                    ownerClass, signature.runtimeName, buildParamDesc(descTypes(signature, parameterTypes)));
            if (obfuscated != null) {
                return new MemberLookupSignature(obfuscated,
                        signature.getMappedMemberOverride(), signature.resolvedType, signature.parameterTypes);
            }
        }
        return signature;
    }

    
    private static Class<?>[] descTypes(MemberLookupSignature signature, Class<?>[] parameterTypes) {
        if (signature.parameterTypes != null && signature.parameterTypes.length > 0) {
            return signature.parameterTypes;
        }
        return parameterTypes;
    }

    private static String buildParamDesc(Class<?>[] parameterTypes) {
        if (parameterTypes == null) {
            return "()";
        }
        StringBuilder descriptor = new StringBuilder("(");
        for (Class<?> parameterType : parameterTypes) {
            if (parameterType == null) {
                
                
                continue;
            }
            if (parameterType == Integer.TYPE) {
                descriptor.append('I');
            } else if (parameterType == Boolean.TYPE) {
                descriptor.append('Z');
            } else if (parameterType == Float.TYPE) {
                descriptor.append('F');
            } else if (parameterType == Double.TYPE) {
                descriptor.append('D');
            } else if (parameterType == Long.TYPE) {
                descriptor.append('J');
            } else if (parameterType == Short.TYPE) {
                descriptor.append('S');
            } else if (parameterType == Byte.TYPE) {
                descriptor.append('B');
            } else if (parameterType == Character.TYPE) {
                descriptor.append('C');
            } else if (parameterType == Void.TYPE) {
                descriptor.append('V');
            } else {
                descriptor.append('L').append(parameterType.getName().replace('.', '/')).append(';');
            }
        }
        return descriptor.append(')').toString();
    }

    @Nullable
    public static String lookupRegisteredClassName(Class<?> clazz) {
        if (clazz == null) {
            return null;
        }
        return registeredClassNames.get(clazz.getName());
    }

    @Nullable
    public static MemberLookupSignature lookupFieldMapping(Class ownerClass, String fieldName) {
        if (memberNameRemapTable == null) {
            return null;
        }
        MemberLookupSignature signature = memberNameRemapTable.lookupFieldMapping(ownerClass, fieldName);
        if (signature == null) {
            
            
            
            
            
            if (!NativeBridge.isForgeAbsent()) {
                return null;
            }
            int version = ForgeVersion.c();
            if (version == 47 && !NativeBridge.isNeoForge1201Runtime()) {
                String obfuscated = NeoForgeObfMap.lookupField1201(ownerClass, fieldName);
                if (obfuscated != null) {
                    return new MemberLookupSignature(obfuscated, null, null);
                }
            } else if (version == 52 && !NativeBridge.isNeoForge1211Runtime()) {
                String obfuscated = NeoForgeObfMap.lookupField1211(ownerClass, fieldName);
                if (obfuscated != null) {
                    return new MemberLookupSignature(obfuscated, null, null);
                }
            }
            return null;
        }
        
        
        if (NativeBridge.isNeoForge1211Runtime()) {
            String mojmap = NeoForgeFieldMap.lookup1211(ownerClass, signature.runtimeName);
            if (mojmap != null) {
                return new MemberLookupSignature(mojmap, signature.getMappedMemberOverride(), signature.resolvedType);
            }
        } else if (NativeBridge.isNeoForge1201Runtime()) {
            String mojmap = NeoForgeFieldMap.lookup1201(ownerClass, signature.runtimeName);
            if (mojmap != null) {
                return new MemberLookupSignature(mojmap, signature.getMappedMemberOverride(), signature.resolvedType);
            }
        } else {
            
            
            
            
            if (!NativeBridge.isForgeAbsent()) {
                return signature;
            }
            int version = ForgeVersion.c();
            if (version == 47) {
                String obfuscated = NeoForgeObfMap.lookupField1201(ownerClass, signature.runtimeName);
                if (obfuscated != null) {
                    return new MemberLookupSignature(obfuscated,
                            signature.getMappedMemberOverride(), signature.resolvedType);
                }
            } else if (version == 52) {
                String obfuscated = NeoForgeObfMap.lookupField1211(ownerClass, signature.runtimeName);
                if (obfuscated != null) {
                    return new MemberLookupSignature(obfuscated,
                            signature.getMappedMemberOverride(), signature.resolvedType);
                }
            }
        }
        return signature;
    }

    public static void initializeRegistry() {
        int forgeVersion = ForgeVersion.c();
        switch (forgeVersion) {
            case 35: 
            case 36: {
                memberNameRemapTable = new MemberNameRemapTableV35V36();
                break;
            }
            case 37: {
                memberNameRemapTable = new MemberNameRemapTableV37();
                break;
            }
            case 47: {
                
                memberNameRemapTable = new MemberNameRemapTableV50();
                break;
            }
            case 50: {
                memberNameRemapTable = new MemberNameRemapTableV50();
                break;
            }
            case 51: {
                memberNameRemapTable = new MemberNameRemapTableV51();
                break;
            }
            case 52: {
                
                memberNameRemapTable = new MemberNameRemapTableV51();
                break;
            }
            case 54: {
                memberNameRemapTable = new MemberNameRemapTableV54();
                break;
            }
            case 55: {
                memberNameRemapTable = new MemberNameRemapTableV55();
                break;
            }
            case 56: {
                memberNameRemapTable = new MemberNameRemapTableV56();
                break;
            }
            case 60: {
                memberNameRemapTable = new MemberNameRemapTableV60();
                break;
            }
            case 61: {
                memberNameRemapTable = new MemberNameRemapTableV61();
                break;
            }
            case 100: {
                memberNameRemapTable = new MemberNameRemapTableV100();
                break;
            }
            case 110: {
                memberNameRemapTable = new MemberNameRemapTableV110();
            }
        }
        if (memberNameRemapTable != null) {
            memberNameRemapTable.initializeMappings();
        }
    }

    static {
        registeredClassNames = new LinkedHashMap<String, String>();
        int forgeVersion = ForgeVersion.c();
        switch (forgeVersion) {
            case 23: {
                classNameRemapTable = new ClassNameRemapTableV23();
                break;
            }
            case 35: 
            case 36: {
                if (Vape.INSTANCE.isForgeAbsent()) {
                    classNameRemapTable = new ClassNameRemapTableV35V36Direct();
                    break;
                }
                classNameRemapTable = new ClassNameRemapTableV35V36Layered();
                break;
            }
            case 37: {
                classNameRemapTable = new ClassNameRemapTableV37();
                break;
            }
            case 47: {
                
                
                classNameRemapTable = new ClassNameRemapTableV50();
                break;
            }
            case 50: {
                classNameRemapTable = new ClassNameRemapTableV50();
                break;
            }
            case 51: {
                classNameRemapTable = new ClassNameRemapTableV51();
                break;
            }
            case 52: {
                
                
                classNameRemapTable = new ClassNameRemapTableV51();
                break;
            }
            case 54: {
                classNameRemapTable = new ClassNameRemapTableV54();
                break;
            }
            case 55: {
                classNameRemapTable = new ClassNameRemapTableV55();
                break;
            }
            case 56: {
                classNameRemapTable = new ClassNameRemapTableV56();
                break;
            }
            case 60: {
                classNameRemapTable = new ClassNameRemapTableV60();
                break;
            }
            case 61: {
                classNameRemapTable = new ClassNameRemapTableV61();
                break;
            }
            case 100: {
                classNameRemapTable = new ClassNameRemapTableV100();
                break;
            }
            case 110: {
                classNameRemapTable = new ClassNameRemapTableV110();
                break;
            }
            default: {
                classNameRemapTable = null;
            }
        }
        if (ForgeVersion.MC_1_16_5_ACTUAL.Y() && !Vape.INSTANCE.isForgeAbsent()) {
            ClassNameRemapTable.propagateMappingsToRuntimeRegistry = true;
            new ClassNameRemapTableV35V36Direct();
        }
    }


    public static String remapClassName(String sourceClassName) {
        String mapped = classNameRemapTable == null ? null
                : classNameRemapTable.lookupRemappedClassName(sourceClassName);
        boolean mojmapRuntime = NativeBridge.isNeoForge1201Runtime()
                || NativeBridge.isNeoForge1211Runtime()
                
                
                || (!NativeBridge.isForgeAbsent() && ForgeVersion.c() == 47);
        if (mapped != null) {
            if (mojmapRuntime) {
                
                
                String mojmap = NativeBridge.isNeoForge1211Runtime()
                        ? NeoForgeClassMap.lookupObfuscated1211(mapped)
                        : NeoForgeClassMap.lookupObfuscated1201(mapped);
                return mojmap != null ? mojmap : mapped;
            }
            return mapped;
        }
        
        
        boolean forge1201Mojmap = !NativeBridge.isForgeAbsent()
                && ForgeVersion.c() == 47;
        if (NativeBridge.isNeoForge1211Runtime()) {
            return NeoForgeClassMap.lookupObfuscated1211(sourceClassName);
        }
        if (NativeBridge.isNeoForge1201Runtime() || forge1201Mojmap) {
            return NeoForgeClassMap.lookupObfuscated1201(sourceClassName);
        }
        return null;
    }
}

