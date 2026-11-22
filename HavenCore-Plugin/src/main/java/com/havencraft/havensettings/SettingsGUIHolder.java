package com.havencraft.havensettings;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Custom InventoryHolder for HavenSettings GUIs.
 * Eliminates memory leaks from keeping CraftInventory references in static/global collections.
 */
public class SettingsGUIHolder implements InventoryHolder {

    private final UUID targetUuid;
    private final Map<Integer, String> slotMap = new HashMap<>();
    private Inventory inventory;

    public SettingsGUIHolder(UUID targetUuid) {
        this.targetUuid = targetUuid;
    }

    public UUID getTargetUuid() {
        return targetUuid;
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
