package gg.vape.mapping;

import gg.vape.Vape;
import gg.vape.mapping.JavassistMappingTask;
import gg.vape.mapping.MappedClasses;
import javassist.CannotCompileException;
import javassist.CtBehavior;


public class GuiRendererRenderTickMappingTask
extends JavassistMappingTask {
    public GuiRendererRenderTickMappingTask() {
        super(MappedClasses.w);
    }

    @Override
    public void transform() {
        MappingMethod mappingMethod = Vape.INSTANCE.getMappings().Ca.z;
        if (mappingMethod == null || mappingMethod.hasResolutionFailed()) {
            return;
        }
        CtBehavior ctBehavior = this.F(mappingMethod);
        if (ctBehavior == null) {
            return;
        }
        try {
            ctBehavior.insertBefore("{"
                    + EventRender2DHudCallback.class.getName() + "#call();}");
            ctBehavior.insertAfter("{"
                    + EventRender2DGuiCallback.class.getName() + "#call();}");
        }
        catch (CannotCompileException cannotCompileException) {
            Vape.logThrowable(cannotCompileException);
        }
    }
}
