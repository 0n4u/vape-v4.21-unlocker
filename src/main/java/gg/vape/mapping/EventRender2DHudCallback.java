package gg.vape.mapping;

import gg.vape.event.impl.EventRender2D;
import gg.vape.mapping.InsertedCallbackMarker;


public class EventRender2DHudCallback
extends InsertedCallbackMarker {
    public static void call() {
        EventRender2D.createHud();
    }
}