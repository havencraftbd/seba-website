package com.haven.shards.manager;

import com.haven.shards.HavenShards;
import com.haven.shards.model.DailyRewardData;
import com.haven.shards.util.ColorUtil;
import com.haven.shards.util.ItemUtil;
import com.haven.shards.util.SoundUtil;
import com.haven.shards.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import com.haven.shards.api.event.HavenShardsReloadEvent;

public class DailyRewardManager implements Listener {

    private final HavenShards plugin;
    private final Map<UUID, DailyRewardData> dataCache = new ConcurrentHashMap<>();
    private final Set<UUID> pendingDailyRewardLoad = ConcurrentHashMap.newKeySet();
    private Map<Integer, Double> streakBonusCache = new ConcurrentHashMap<>();

    public static final long DAY_MILLIS = 24 * 60 * 60 * 1000L;
    public static final long MISS_MILLIS = 48 * 60 * 60 * 1000L;

    public DailyRewardManager(HavenShards plugin) {
        this.plugin = plugin;
        reloadConfigCaches();
        for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            loadPlayer(p);
        }
    }

    public void reloadConfigCaches() {
        Map<Integer, Double> map = new ConcurrentHashMap<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("daily-rewards.streak-bonuses");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String cleaned = key.toLowerCase().replace("day-", "").replace("day_", "").replace("day", "").trim();
                try {
                    int dayNum = Integer.parseInt(cleaned);
                    double val = section.getDouble(key, 0.0);
                    map.put(dayNum, val);
                } catch (NumberFormatException ignored) {}
            }
        }
        this.streakBonusCache = map;
    }

    public void loadPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        pendingDailyRewardLoad.add(uuid);
        plugin.getDatabaseManager().loadDailyRewardAsync(uuid, data -> {
            try {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null || !p.isOnline()) {
                    return; // Guard against quick join-quit memory leak
                }
                if (data != null) {
                    dataCache.put(uuid, data);
                }
            } finally {
                pendingDailyRewardLoad.remove(uuid);
            }
        });
    }

    public void unloadPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        pendingDailyRewardLoad.remove(uuid);
        dataCache.remove(uuid);
    }

    public boolean isDataLoaded(UUID uuid) {
        return !pendingDailyRewardLoad.contains(uuid) && dataCache.containsKey(uuid);
    }

    public void saveAllOnlinePlayers() {
        for (Map.Entry<UUID, DailyRewardData> entry : dataCache.entrySet()) {
            DailyRewardData data = entry.getValue();
            plugin.getDatabaseManager().saveDailyRewardSync(entry.getKey(), data.getLastClaimMillis(), data.getStreak());
        }
    }

    public DailyRewardData getRewardData(UUID uuid) {
        DailyRewardData data = dataCache.get(uuid);
        if (data != null) {
            return data;
        }
        return new DailyRewardData(uuid, 0L, 0);
    }

    private volatile long cachedMidnightMillis = 0;
    private volatile long midnightCacheExpiresAt = 0;

    private long getMidnightMillis() {
        long now = System.currentTimeMillis();
        if (now < midnightCacheExpiresAt && cachedMidnightMillis > 0) {
            return cachedMidnightMillis;
        }

        String tzString = plugin.getConfig().getString("daily-rewards.reset-timezone", "Asia/Dhaka");
        java.util.TimeZone tz = java.util.TimeZone.getTimeZone(tzString);
        Calendar cal = Calendar.getInstance(tz);
        cal.setTimeInMillis(now);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long todayMidnight = cal.getTimeInMillis();

        cal.add(Calendar.DAY_OF_YEAR, 1);
        long nextMidnight = cal.getTimeInMillis();

        this.cachedMidnightMillis = todayMidnight;
        this.midnightCacheExpiresAt = nextMidnight;
        return todayMidnight;
    }

    public boolean canClaim(UUID uuid) {
        if (pendingDailyRewardLoad.contains(uuid)) return false;
        DailyRewardData data = getRewardData(uuid);
        if (data.getLastClaimMillis() == 0L) return true;
        return data.getLastClaimMillis() < getMidnightMillis();
    }

    public long getRemainingMillis(UUID uuid) {
        DailyRewardData data = getRewardData(uuid);
        if (data.getLastClaimMillis() == 0L) return 0L;
        if (data.getLastClaimMillis() < getMidnightMillis()) return 0L;
        
        long diff = midnightCacheExpiresAt - System.currentTimeMillis();
        return Math.max(0L, diff);
    }

    public int getStreak(UUID uuid) {
        DailyRewardData data = getRewardData(uuid);
        long last = data.getLastClaimMillis();
        if (last == 0L) return 0;

        boolean resetOnMiss = plugin.getConfig().getBoolean("daily-rewards.reset-on-miss", true);
        if (resetOnMiss && last < (getMidnightMillis() - 86400000L)) {
            return 0;
        }
        return data.getStreak();
    }

    public boolean claimReward(Player player) {
        if (player == null) return false;
        UUID uuid = player.getUniqueId();
        if (pendingDailyRewardLoad.contains(uuid)) {
            String msg = plugin.getMessageManager().getMessage("daily-loading");
            if (msg.equals("daily-loading")) {
                msg = plugin.getConfig().getString("daily-rewards.loading-message",
                        "&cYour daily reward data is loading, please wait a moment...");
            }
            player.sendMessage(plugin.getPrefix() + ColorUtil.colorize(msg));
            SoundUtil.playSound(plugin, player, "insufficient-funds");
            return false;
        }

        DailyRewardData data = dataCache.computeIfAbsent(uuid, k -> new DailyRewardData(k, 0L, 0));

        synchronized (data) {
            if (!canClaim(uuid)) {
                String remainingFormatted = TimeUtil.formatTime(getRemainingMillis(uuid) / 1000L);
                String msg = plugin.getMessageManager().getMessage("daily-already-claimed");
                if (msg.equals("daily-already-claimed")) {
                    msg = plugin.getConfig().getString("daily-rewards.already-claimed-message",
                            "&cYou have already claimed your daily reward! Next claim in: &e%remaining%");
                }
                player.sendMessage(plugin.getPrefix() + ColorUtil.colorize(msg.replace("%remaining%", remainingFormatted)));
                SoundUtil.playSound(plugin, player, "insufficient-funds");
                return false;
            }

            long now = System.currentTimeMillis();
            long last = data.getLastClaimMillis();
            int currentStreak = data.getStreak();
            boolean resetOnMiss = plugin.getConfig().getBoolean("daily-rewards.reset-on-miss", true);

            int nextStreak;
            if (last == 0L) {
                nextStreak = 1;
            } else if (last >= (getMidnightMillis() - 86400000L)) {
                nextStreak = currentStreak + 1;
            } else {
                nextStreak = resetOnMiss ? 1 : currentStreak + 1;
            }

            double baseReward = plugin.getConfig().getDouble("daily-rewards.base-reward", 25.0);
            double streakBonus = getStreakBonus(nextStreak);
            double multiplier = plugin.getShardManager().getTotalMultiplier(player);
            double totalReward = (baseReward + streakBonus) * multiplier;

            plugin.getShardManager().addBalance(player, totalReward);
            data.setLastClaimMillis(now);
            data.setStreak(nextStreak);

            plugin.getDatabaseManager().saveDailyRewardAsync(uuid, now, nextStreak);
            SoundUtil.playSound(plugin, player, "daily-reward-claim");

        if (plugin.getConfig().getBoolean("daily-rewards.log-history", true)) {
            double newBal = plugin.getShardManager().getBalance(player);
            plugin.getDatabaseManager().logTransactionAsync(null, null, uuid, player.getName(), "DAILY", totalReward, 0, newBal);
        }

        if (plugin.getConfig().getBoolean("daily-rewards.notify", true)) {
            String rawMsg = plugin.getConfig().getString("daily-rewards.message",
                    "&a+%amount% ✨ Shards &7claimed for Daily Reward! &8(&eDay %streak% Streak&8)");
            String symbol = plugin.getConfig().getString("currency.symbol", "✨");
            String currency = plugin.getConfig().getString("currency.name", "Shards");
            String displayType = plugin.getConfig().getString("daily-rewards.display-type", "CHAT");

            String msg = ColorUtil.colorize(rawMsg
                    .replace("%amount%", ItemUtil.formatMoney(totalReward))
                    .replace("%streak%", String.valueOf(nextStreak))
                    .replace("%bonus%", ItemUtil.formatMoney(streakBonus))
                    .replace("%symbol%", symbol)
                    .replace("%currency%", currency)
            );
            com.haven.shards.util.NotificationUtil.sendNotification(plugin, player, msg, displayType, "CHAT");
        }
        return true;
        }
    }

    public double getStreakBonus(int day) {
        return streakBonusCache.getOrDefault(day, 0.0);
    }

    public List<Integer> getMilestoneDays() {
        List<Integer> days = new ArrayList<>(streakBonusCache.keySet());
        if (!days.contains(1)) days.add(1);
        Collections.sort(days);
        return days;
    }

    @EventHandler
    public void onReload(HavenShardsReloadEvent event) {
        reloadConfigCaches();
    }
}
