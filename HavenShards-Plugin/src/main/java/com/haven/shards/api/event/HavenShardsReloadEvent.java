package com.haven.shards.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class HavenShardsReloadEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public HavenShardsReloadEvent() {
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
