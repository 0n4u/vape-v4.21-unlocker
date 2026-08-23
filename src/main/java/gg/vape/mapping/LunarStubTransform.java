package gg.vape.mapping;

import gg.vape.Vape;
import gg.vape.asm.transform.ClassTransformer;
import gg.vape.lunar.PrometheusHooks;
import gg.vape.reflect.LunarMappings;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;


public final class LunarStubTransform extends ClassTransformer {
    private static final String HOOK_OWNER = "gg/vape/lunar/PrometheusHooks";

    private final String targetClassName;
    private final String[] methodNames;
    private final int[] argSlots;   
    private final String[] hookNames;
    private boolean inactive;

    
    public LunarStubTransform(String targetClassName,
                              String[] methodNames,
                              int[] argSlots,
                              String[] hookNames) {
        super(resolveSafely(targetClassName));
        this.targetClassName = targetClassName;
        this.methodNames = methodNames;
        this.argSlots = argSlots;
        this.hookNames = hookNames;
        this.inactive = this.targetClass == null;
        if (this.inactive) {
            Vape.debugLog("LUNAR transform inactive (class unavailable): "
                    + targetClassName);
        } else {
            Vape.debugLog("LUNAR transform registered: " + targetClassName);
        }
    }

    private static Class<?> resolveSafely(String targetClassName) {
        try {
            return LunarMappings.resolveClass(targetClassName);
        }
        catch (Throwable error) {
            return null;
        }
    }

    @Override
    public boolean isApplied() {
        return this.inactive || super.isApplied();
    }

    @Override
    public Class getTargetClass() {
        return this.targetClass == null
                ? LunarStubTransform.class : this.targetClass;
    }

    @Override
    public void prepare() {
        if (this.inactive || this.targetClass == null) {
            this.originalBytecode = new byte[0];
            this.classNode = null;
            return;
        }
        super.prepare();
    }

    @Override
    public void transform() {
        if (this.inactive || this.classNode == null) {
            return;
        }
        for (int i = 0; i < methodNames.length; ++i) {
            MethodNode method = findMethod(this.classNode, methodNames[i]);
            if (method == null) {
                Vape.debugLog("LUNAR method not present in this revision: "
                        + this.targetClassName + "#" + methodNames[i]
                        + " (skipped)");
                continue;
            }
            method.instructions.clear();
            InsnList replacement = new InsnList();
            if (argSlots[i] >= 0) {
                replacement.add(new VarInsnNode(Opcodes.ALOAD, argSlots[i]));
            }
            replacement.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC, HOOK_OWNER, hookNames[i],
                    "(Ljava/lang/Object;)V", false));
            replacement.add(new InsnNode(Opcodes.RETURN));
            method.instructions.add(replacement);
            Vape.debugLog("LUNAR method rewritten: "
                    + this.targetClassName + "#" + methodNames[i]
                    + " -> " + hookNames[i]);
        }
    }

    @Override
    public int commit() {
        if (this.inactive) {
            return 0;
        }
        int result = super.commit();
        Vape.debugLog("LUNAR commit " + this.targetClassName
                + (result == 0 ? " OK" : " -> " + result));
        return result;
    }

    
    private static MethodNode findMethod(org.objectweb.asm.tree.ClassNode classNode,
                                         String name) {
        for (MethodNode method : classNode.methods) {
            if (method.name.equals(name)) {
                return method;
            }
        }
        return null;
    }
}