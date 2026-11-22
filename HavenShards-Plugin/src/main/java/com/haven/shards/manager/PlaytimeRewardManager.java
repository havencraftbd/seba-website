package com.haven.shards.manager;

import com.haven.shards.HavenShards;
import com.haven.shards.util.ColorUtil;
import com.haven.shards.util.ItemUtil;
import com.haven.shards.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import com.haven.shards.api.event.HavenShardsReloadEvent;

public class PlaytimeRewardManager implements Listener {

    private final HavenShards plugin;
    private final Map<UUID, Integer> playtimeMinutes = new ConcurrentHashMap<>();
    private BukkitTask tickerTask;

    // Config Cache
    private boolean enabled;
    private int interval;
    private double rewardAmount;
    private boolean notify;
    private String rawMsg;
    private String displayType;
    private boolean logHistory;
    private double afkMultiplier;

    public PlaytimeRewardManager(HavenShards plugin) {
        this.plugin = plugin;
        startTicker();
    }

    public void reloadConfigCaches() {
        this.enabled = plugin.getConfig().getBoolean("playtime-rewards.enabled", true);
        this.interval = plugin.getConfig().getInt("playtime-rewards.interval-minutes", 10);
        this.rewardAmount = plugin.getConfig().getDouble("playtime-rewards.reward-amount", 10.0);
        this.notify = plugin.getConfig().getBoolean("playtime-rewards.notify", true);
        this.rawMsg = plugin.getConfig().getString("playtime-rewards.message", "&a+%amount% ✨ Shards &7earned for %interval% minutes of active playtime!");
        this.displayType = plugin.getConfig().getString("playtime-rewards.display-type", "ACTION_BAR");
        this.logHistory = plugin.getConfig().getBoolean("playtime-rewards.log-history", true);
        this.afkMultiplier = plugin.getConfig().getDouble("playtime-rewards.afk-multiplier", 0.5);
    }

    public void startTicker() {
        stopTicker();
        reloadConfigCaches();

        if (!enabled) {
            return;
        }

        // Ticker runs every 60 seconds (1200 ticks) - ZERO TPS impact!
        this.tickerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (plugin.getQuestManager() != null) {
                plugin.getQuestManager().checkMidnightReset();
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();
                int currentMins = playtimeMinutes.getOrDefault(uuid, 0) + 1;

                boolean isEssentialsAfk = plugin.getEssentialsHook() != null && plugin.getEssentialsHook().isAfk(player);

                if (!isEssentialsAfk && plugin.getQuestManager() != null) {
                    if (plugin.getQuestManager().hasActiveQuest(uuid, "PLAYTIME")) {
                        plugin.getQuestManager().incrementProgress(player, "PLAYTIME", 1, null);
                    }
                }

                if (currentMins >= interval) {
                    playtimeMinutes.put(uuid, 0); // Reset timer for player

                    double earnedAmount = rewardAmount * plugin.getShardManager().getTotalMultiplier(player);
                    if (isEssentialsAfk) {
                        earnedAmount *= afkMultiplier;
                    }

                    if (earnedAmount > 0) {
                        plugin.getShardManager().addBalance(player, earnedAmount);
                        SoundUtil.playSound(plugin, player, "playtime-reward");

                        if (logHistory) {
                            double newBal = plugin.getShardManager().getBalance(player);
                            plugin.getDatabaseManager().logTransactionAsync(null, null, uuid, player.getName(), "PLAYTIME", earnedAmount, 0, newBal);
                        }

                        if (notify && player.isOnline()) {
                            String formattedMsg = ColorUtil.colorize(rawMsg
                                    .replace("%amount%", ItemUtil.formatMoney(earnedAmount))
                                    .replace("%interval%", String.valueOf(interval))
                            );
                            com.haven.shards.util.NotificationUtil.sendNotification(plugin, player, formattedMsg, displayType, "ACTION_BAR");
                        }
                    }
                } else {
                    playtimeMinutes.put(uuid, currentMins);
                }
            }
        }, 1200L, 1200L);
    }

    public int getPlaytimeMinutes(UUID uuid) {
        return playtimeMinutes.getOrDefault(uuid, 0);
    }

    public void stopTicker() {
        if (tickerTask != null) {
            tickerTask.cancel();
            tickerTask = null;
        }
    }

    public void onDisable() {
        stopTicker();
        playtimeMinutes.clear();
    }

    public void unloadPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        playtimeMinutes.remove(uuid);
    }

    @EventHandler
    public void onReload(HavenShardsReloadEvent event) {
        startTicker();
    }
}
