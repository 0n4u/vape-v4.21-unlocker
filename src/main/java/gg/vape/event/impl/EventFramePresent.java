package gg.vape.event.impl;

import gg.vape.event.Event;
import gg.vape.event.EventListeners;

 
public class EventFramePresent
extends Event {
    private static final EventListeners EVENT_LISTENERS = new EventListeners();

    public EventFramePresent() {
    }

    @Override
    public boolean fire() {
        return super.fire();
    }

    @Override
    public EventListeners getListeners() {
        return EVENT_LISTENERS;
    }

    public static EventListeners getEventListeners() {
        return EVENT_LISTENERS;
    }
}
