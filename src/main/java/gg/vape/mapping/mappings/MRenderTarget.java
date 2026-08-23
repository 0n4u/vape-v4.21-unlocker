package gg.vape.mapping.mappings;

import gg.vape.Vape;
import gg.vape.mapping.MappedClasses;
import gg.vape.mapping.Mapping;
import gg.vape.mapping.MappingMethod;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.wrapper.impl.ForgeVersion;

 
public class MRenderTarget
extends Mapping {
    public MappingMethod blitToScreenMethod;
    private static boolean renderTargetControlFlowState;

    public static boolean getRenderTargetControlFlowState() {
        return renderTargetControlFlowState;
    }

    public static boolean getDisabledControlFlowState() {
        boolean bl = MRenderTarget.getRenderTargetControlFlowState();
        return false;
    }

    public static void setRenderTargetControlFlowState(boolean state) {
        renderTargetControlFlowState = state;
    }

    public MRenderTarget() {
        this(MRenderTarget.getDisabledControlFlowState());
    }

    private MRenderTarget(boolean bl) {
        super(MRenderTarget.resolveRenderTargetClass());
        if (bl) {
            this.registerBlitToScreen();
            GuiComponent.setLegacyComponentState(new GuiComponent[2]);
            return;
        }
        this.registerBlitToScreen();
    }

    private static Class<?> resolveRenderTargetClass() {
        Class<?> mappedClass = MappedClasses.DA;
        if (mappedClass != null) {
            return mappedClass;
        }
        if (!gg.vape.Vape.INSTANCE.isForgeAbsent()) {
            

            try {
                return Class.forName(
                        "com.mojang.blaze3d.pipeline.RenderTarget");
            }
            catch (Throwable throwable) {
                

            }
        }
        

        return gg.vape.runtime.NativeBridge.gvc(
                "com/mojang/blaze3d/pipeline/RenderTarget");
    }

    private void registerBlitToScreen() {
        if (ForgeVersion.MC_1_21_4.d() && ForgeVersion.MC_26_2.v()) {
            

            

            this.blitToScreenMethod = this.Y("blitToScreen", true, Void.TYPE, new Class[0]);
        } else if (ForgeVersion.MC_1_21_0.d() && ForgeVersion.MC_1_21_4.v()) {
            

            Class[] classArray = new Class[]{Integer.TYPE, Integer.TYPE};
            this.blitToScreenMethod = this.Y("blitToScreen", true, Void.TYPE, classArray);
        } else if (ForgeVersion.MC_1_20_1.d() && ForgeVersion.MC_1_21_0.v()) {
            

            

            Class[] classArray = new Class[]{Integer.TYPE, Integer.TYPE};
            this.blitToScreenMethod = this.Y("blitToScreen", true, Void.TYPE, classArray);
        }
    }

    static {
        MRenderTarget.setRenderTargetControlFlowState(true);
    }
}
