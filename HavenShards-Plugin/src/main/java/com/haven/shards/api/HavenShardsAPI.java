package com.haven.shards.api;

import com.haven.shards.HavenShards;
import com.haven.shards.manager.DatabaseManager;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class HavenShardsAPI {

    private static HavenShards getPlugin() {
        HavenShards plugin = HavenShards.getInstance();
        if (plugin == null) {
            throw new IllegalStateException("HavenShards is not enabled!");
        }
        return plugin;
    }

    public static List<DatabaseManager.TopEntry> getCachedTopEntries() {
        return getPlugin().getDatabaseManager().getCachedTopEntries();
    }

    public static double getBalance(UUID uuid) {
        return getPlugin().getShardManager().getBalance(uuid);
    }

    public static double getBalance(Player player) {
        return getPlugin().getShardManager().getBalance(player);
    }

    public static boolean hasBalance(Player player, double amount) {
        return getPlugin().getShardManager().hasBalance(player, amount);
    }

    public static void addBalance(UUID uuid, double amount) {
        getPlugin().getShardManager().addBalance(uuid, amount);
    }

    public static void addBalance(Player player, double amount) {
        getPlugin().getShardManager().addBalance(player, amount);
    }

    public static boolean removeBalance(UUID uuid, double amount) {
        return getPlugin().getShardManager().removeBalance(uuid, amount);
    }

    public static boolean removeBalance(Player player, double amount) {
        return getPlugin().getShardManager().removeBalance(player, amount);
    }

    public static void setBalance(Player player, double amount) {
        getPlugin().getShardManager().setBalance(player, amount);
    }

    public static void modifyOfflineBalance(OfflinePlayer target, double delta, Consumer<Boolean> callback) {
        getPlugin().getShardManager().modifyOfflineBalance(target, delta, callback);
    }

    public static double getGlobalBoosterMultiplier() {
        return getPlugin().getBoosterManager().getMultiplier();
    }

    public static double getRankMultiplier(Player player) {
        return getPlugin().getShardManager().getRankMultiplier(player);
    }

    public static double getTotalMultiplier(Player player) {
        return getPlugin().getShardManager().getTotalMultiplier(player);
    }
}
