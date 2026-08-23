package gg.vape.event.impl;

import gg.vape.event.Event;
import gg.vape.event.EventListeners;
import gg.vape.utils.ThreadBoundExecutor;
import gg.vape.utils.render.GuiRenderPrimitives;
import gg.vape.wrapper.impl.DeltaTracker;
import gg.vape.wrapper.impl.Util;

public class EventRenderWorldPassExecutorDrain
extends Event {
    private static final EventListeners EVENT_LISTENERS;
    private final float partialTicks;
    public static final ThreadBoundExecutor EXECUTOR;
    private static String[] obfuscationState;

    public EventRenderWorldPassExecutorDrain(float partialTicks) {
        this.partialTicks = partialTicks;
    }

    public EventRenderWorldPassExecutorDrain() {
        this.partialTicks = 0.0f;
    }

    public static String[] getWorldPassObfuscationState() {
        return obfuscationState;
    }

    private static boolean contextWarned;

    @Override
    public boolean fire() {
        try {
            

            

            

            

            if (GuiRenderPrimitives.d() && !EventRenderWorldPassExecutorDrain.isGlContextReady()) {
                if (!EventRenderWorldPassExecutorDrain.contextWarned) {
                    EventRenderWorldPassExecutorDrain.contextWarned = true;
                    gg.vape.Vape.debugLog("ERWPD: GL context not ready at update entry (thread="
                            + Thread.currentThread().getName() + "). 26.2 若使用 Vulkan 图形后端则没有"
                            + " OpenGL 上下文，请将视频设置中的图形 API 切换为 OpenGL 后重试。");
                }
                return false;
            }
            EXECUTOR.runPending();
        }
        catch (Throwable throwable) {
            

            

            gg.vape.Vape.logThrowable(throwable);
        }
        return false;
    }

    private static boolean isGlContextReady() {
        try {
            return Util.glfwGetCurrentContext() != 0L;
        }
        catch (Throwable throwable) {
            return true;
        }
    }

    public static void setWorldPassObfuscationState(String[] state) {
        obfuscationState = state;
    }

    @Override
    public EventListeners getListeners() {
        return EVENT_LISTENERS;
    }

    public EventRenderWorldPassExecutorDrain(Object deltaTrackerHandle) {
        DeltaTracker deltaTracker = new DeltaTracker(deltaTrackerHandle);
        this.partialTicks = deltaTracker.getGameTimeDeltaTicks();
    }

    public static EventListeners getEventListeners() {
        return EVENT_LISTENERS;
    }

    static {
        EXECUTOR = new ThreadBoundExecutor();
        EVENT_LISTENERS = new EventListeners();
        EventRenderWorldPassExecutorDrain.setWorldPassObfuscationState(null);
    }
}

