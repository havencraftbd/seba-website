package com.havencraft.havenpunish.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Collections;
import java.util.Map;

/**
 * Custom InventoryHolder for HavenPunish GUIs.
 * Eliminates memory leaks from keeping CraftInventory references in static/global collections.
 */
public class PunishGUIHolder implements InventoryHolder {

    public enum Type {
        MAIN,
        DURATION,
        REASON,
        CONFIRM
    }

    private final Type type;
    private final Map<Integer, String> slotMap;
    private Inventory inventory;

    public PunishGUIHolder(Type type, Map<Integer, String> slotMap) {
        this.type = type;
        this.slotMap = slotMap != null ? slotMap : new java.util.HashMap<>();
    }

    public Type getType() {
        return type;
    }

    public Map<Integer, String> getSlotMap() {
        return slotMap;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
