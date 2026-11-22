package com.haven.shards.manager;

import com.haven.shards.HavenShards;
import com.haven.shards.util.ColorUtil;
import com.haven.shards.util.ItemUtil;
import com.haven.shards.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

public class BoosterManager {

    private final HavenShards plugin;
    private final File boosterFile;
    private double currentMultiplier = 1.0;
    private long expireTimestamp = 0;
    private BukkitTask expireTask;

    public BoosterManager(HavenShards plugin) {
        this.plugin = plugin;
        this.boosterFile = new File(plugin.getDataFolder(), "data/booster.yml");
        loadPersistedBooster();
    }

    private void loadPersistedBooster() {
        if (!boosterFile.exists()) return;
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(boosterFile);
            double mult = config.getDouble("multiplier", 1.0);
            long expire = config.getLong("expire_timestamp", 0L);

            if (expire > System.currentTimeMillis() && mult > 1.0) {
                this.currentMultiplier = mult;
                this.expireTimestamp = expire;

                long remainingTicks = Math.max(1L, (expire - System.currentTimeMillis()) / 50L);
                this.expireTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    stopBooster();
                    String endMsg = plugin.getMessageManager().getMessage("booster-ended");
                    net.kyori.adventure.text.Component comp = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(plugin.getPrefix() + endMsg);
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.sendMessage(comp);
                        SoundUtil.playSound(plugin, player, "booster-end");
                    }
                }, remainingTicks);

                plugin.getLogger().info("[HavenShards] Resumed active booster (" + mult + "x) with " + getFormattedRemainingTime() + " remaining.");
            } else {
                clearPersistedBooster();
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load persisted booster data", e);
        }
    }

    private void savePersistedBooster() {
        try {
            if (!boosterFile.getParentFile().exists()) {
                boosterFile.getParentFile().mkdirs();
            }
            YamlConfiguration config = new YamlConfiguration();
            config.set("multiplier", this.currentMultiplier);
            config.set("expire_timestamp", this.expireTimestamp);
            config.save(boosterFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save booster data to file", e);
        }
    }

    private void clearPersistedBooster() {
        if (boosterFile.exists()) {
            boosterFile.delete();
        }
    }

    public void setBooster(double multiplier, int durationMinutes) {
        if (durationMinutes <= 0 || multiplier <= 1.0 || Double.isNaN(multiplier) || Double.isInfinite(multiplier)) {
            stopBooster();
            return;
        }
        stopBooster();
        this.currentMultiplier = Math.max(1.0, multiplier);
        this.expireTimestamp = System.currentTimeMillis() + (durationMinutes * 60L * 1000L);
        savePersistedBooster();

        // Broadcast booster start to all online players
        String rawMsg = plugin.getMessageManager().getMessage("booster-started");
        String formattedDuration = com.haven.shards.util.TimeUtil.formatMinutes(durationMinutes);
        String msg = rawMsg
                .replace("%multiplier%", ItemUtil.formatMoney(multiplier))
                .replace("%duration%", formattedDuration);

        net.kyori.adventure.text.Component startComp = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(plugin.getPrefix() + msg);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(startComp);
            SoundUtil.playSound(plugin, player, "booster-start");
        }

        // Schedule expiration task
        this.expireTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            stopBooster();
            String endMsg = plugin.getMessageManager().getMessage("booster-ended");
            net.kyori.adventure.text.Component endComp = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(plugin.getPrefix() + endMsg);
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(endComp);
                SoundUtil.playSound(plugin, player, "booster-end");
            }
        }, durationMinutes * 60L * 20L);
    }

    public void stopBooster() {
        if (expireTask != null) {
            expireTask.cancel();
            expireTask = null;
        }
        this.currentMultiplier = 1.0;
        this.expireTimestamp = 0;
        clearPersistedBooster();
    }

    public void onDisable() {
        if (expireTask != null) {
            expireTask.cancel();
            expireTask = null;
        }
        if (isBoosterActive()) {
            savePersistedBooster();
        } else {
            clearPersistedBooster();
        }
    }

    public boolean isBoosterActive() {
        return System.currentTimeMillis() < expireTimestamp && currentMultiplier > 1.0;
    }

    public double getMultiplier() {
        if (!isBoosterActive()) return 1.0;
        return currentMultiplier;
    }

    public long getRemainingMinutes() {
        if (!isBoosterActive()) return 0;
        long remainingMs = expireTimestamp - System.currentTimeMillis();
        return Math.max(1, remainingMs / 60000L);
    }

    public String getFormattedRemainingTime() {
        if (!isBoosterActive()) return "0s";
        long remainingMs = expireTimestamp - System.currentTimeMillis();
        long seconds = (remainingMs / 1000L) % 60;
        long minutes = (remainingMs / (60L * 1000L)) % 60;
        long hours = remainingMs / (3600L * 1000L);

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }
}

