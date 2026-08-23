package gg.vape.ui.click.frame.impl.main;

import gg.vape.ui.click.frame.impl.main.ClickGuiLayer;

class ClickGuiLayerSwitchMap {
    static final int[] d = new int[ClickGuiLayer.values().length];

    ClickGuiLayerSwitchMap() {
    }

    static {
        try {
            ClickGuiLayerSwitchMap.d[ClickGuiLayer.MAIN.ordinal()] = 1;
        }
        catch (NoSuchFieldError noSuchFieldError) {
            

        }
        try {
            ClickGuiLayerSwitchMap.d[ClickGuiLayer.OVERLAYS.ordinal()] = 2;
        }
        catch (NoSuchFieldError noSuchFieldError) {
            

        }
    }
}

