package com.havencraft.havensettings;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffectType;

/**
 * Handles player join/quit/respawn/potion-effects for settings persistence.
 * Loads settings on join, re-applies night vision, scoreboard, and TPA states.
 * Re-applies night vision on respawn after death and after milk consumption or effect cleansing.
 * Saves on quit.
 */
public class PlayerListener implements Listener {

    private final SettingsManager settingsManager;
    private final SettingsGUI settingsGUI;

    public PlayerListener(SettingsManager settingsManager, SettingsGUI settingsGUI) {
        this.settingsManager = settingsManager;
        this.settingsGUI = settingsGUI;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerSettings settings = settingsManager.loadPlayer(player.getUniqueId());

        // Re-apply night vision if enabled
        if (settings.isNightVisionEnabled()) {
            settingsGUI.applyNV(player);
        }

        // Apply TAB scoreboard state (delayed to let TAB initialize)
        Bukkit.getScheduler().runTaskLater(
                settingsGUI.getPlugin(), () -> {
                    if (player.isOnline()) {
                        settingsManager.applyScoreboard(player.getUniqueId(), settings.isScoreboardEnabled());
                    }
                }, 40L // 2 seconds delay for TAB to load
        );

        // Sync EssentialsX states: public chat, private messages, TPA requests
        settingsManager.applyExternalStates(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        applyNightVisionDelayed(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        applyNightVisionDelayed(player);
    }

    /**
     * Re-applies night vision if player drinks milk bucket.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (event.getItem().getType() == Material.MILK_BUCKET) {
            Player player = event.getPlayer();
            if (settingsManager.isNightVisionEnabled(player.getUniqueId())) {
                Bukkit.getScheduler().runTaskLater(settingsGUI.getPlugin(), () -> {
                    if (player.isOnline() && !player.isDead() && settingsManager.isNightVisionEnabled(player.getUniqueId())) {
                        settingsGUI.applyNV(player);
                    }
                }, 2L);
            }
        }
    }

    /**
     * Re-applies night vision if an effect is cleansed or removed while night vision setting is enabled.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPotionEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getOldEffect() == null || !event.getOldEffect().getType().equals(PotionEffectType.NIGHT_VISION)) return;
        if (event.getAction() == EntityPotionEffectEvent.Action.REMOVED ||
                event.getAction() == EntityPotionEffectEvent.Action.CLEARED) {
            if (player.isOnline() && !player.isDead() && settingsManager.isNightVisionEnabled(player.getUniqueId())) {
                Bukkit.getScheduler().runTask(settingsGUI.getPlugin(), () -> {
                    if (player.isOnline() && !player.isDead() && settingsManager.isNightVisionEnabled(player.getUniqueId())) {
                        settingsGUI.applyNV(player);
                    }
                });
            }
        }
    }

    private void applyNightVisionDelayed(Player player) {
        if (player == null) return;
        // Apply at tick 2, tick 12 to ensure post-respawn plugins / spawn teleports don't strip it
        Bukkit.getScheduler().runTaskLater(settingsGUI.getPlugin(), () -> {
            if (player.isOnline() && settingsManager.isNightVisionEnabled(player.getUniqueId())) {
                settingsGUI.applyNV(player);
            }
        }, 2L);
        Bukkit.getScheduler().runTaskLater(settingsGUI.getPlugin(), () -> {
            if (player.isOnline() && settingsManager.isNightVisionEnabled(player.getUniqueId())) {
                if (!player.hasPotionEffect(PotionEffectType.NIGHT_VISION)) {
                    settingsGUI.applyNV(player);
                }
            }
        }, 12L);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent event) {
        settingsManager.unloadPlayer(event.getPlayer().getUniqueId());
    }
}
