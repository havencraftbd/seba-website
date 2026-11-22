package com.haven.sell.hook;

import com.haven.sell.HavenSell;
import com.haven.sell.data.PlayerData;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class PAPIExpansion extends PlaceholderExpansion {

    private final HavenSell plugin;

    public PAPIExpansion(HavenSell plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "havensell";
    }

    @Override
    public @NotNull String getAuthor() {
        return "HavenCraft";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true; 
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";

        if (params.equalsIgnoreCase("earned")) {
            PlayerData data = plugin.getDataManager().loadPlayerData(player.getUniqueId(), player.getName());
            if (data == null) return "0";
            double earned = data.getTotalMoneyEarned();
            if (earned == (long) earned) return String.valueOf((long) earned);
            return String.format("%.2f", earned);
        }

        if (params.equalsIgnoreCase("items_sold")) {
            PlayerData data = plugin.getDataManager().loadPlayerData(player.getUniqueId(), player.getName());
            if (data == null) return "0";
            return String.valueOf(data.getTotalItemsSold());
        }

        return null; 
    }
}
