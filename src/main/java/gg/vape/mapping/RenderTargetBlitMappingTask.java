package gg.vape.mapping;

import gg.vape.Vape;
import gg.vape.event.impl.EventFramePresent;
import gg.vape.mapping.MappedClasses;
import gg.vape.mapping.mappings.MRenderTarget;
import gg.vape.wrapper.impl.ForgeVersion;


public class RenderTargetBlitMappingTask
extends JavassistMappingTask {
    public RenderTargetBlitMappingTask() {
        super(RenderTargetBlitMappingTask.resolveRenderTargetClass());
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

    @Override
    public void transform() {
        if (!ForgeVersion.MC_1_20_1.d() || ForgeVersion.MC_26_2.d()) {
            return;
        }
        MRenderTarget mRenderTarget = Vape.INSTANCE.getMappings().renderTargetBlit;
        if (mRenderTarget == null || mRenderTarget.blitToScreenMethod == null
                || mRenderTarget.blitToScreenMethod.hasResolutionFailed()) {
            Vape.debugLog("RBT: blitToScreen mapping unavailable, skipping");
            return;
        }
        try {
            this.k(mRenderTarget.blitToScreenMethod, EventFramePresent.class, "");
            Vape.debugLog("RBT: injected EventFramePresent after blitToScreen");
        }
        catch (Throwable throwable) {
            Vape.logThrowable(throwable);
        }
    }
}
