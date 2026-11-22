package com.havencraft.leaderboard.gui;

import com.havencraft.havencore.stats.StatisticType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class LeaderboardHolder implements InventoryHolder {

    private Inventory inventory;
    private final String guiName;
    private final StatisticType statType; // Null if main menu
    private final int page;

    public LeaderboardHolder(String guiName) {
        this(guiName, null, 1);
    }

    public LeaderboardHolder(String guiName, StatisticType statType, int page) {
        this.guiName = guiName;
        this.statType = statType;
        this.page = page;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public String getGuiName() {
        return guiName;
    }

    public StatisticType getStatType() {
        return statType;
    }

    public int getPage() {
        return page;
    }
}
