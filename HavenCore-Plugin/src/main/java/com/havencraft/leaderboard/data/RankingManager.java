package com.havencraft.leaderboard.data;

import com.havencraft.havencore.HavenCorePlugin;
import com.havencraft.havencore.stats.PlayerStats;
import com.havencraft.havencore.stats.StatisticType;
import com.havencraft.leaderboard.HavenLeaderboard;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RankingManager {

    private final HavenLeaderboard plugin;
    private final Map<StatisticType, List<PlayerStat>> cachedRankings = new ConcurrentHashMap<>();
    private final Map<StatisticType, Map<UUID, Integer>> cachedPlayerRanks = new ConcurrentHashMap<>();
    private final Set<StatisticType> enabledTypes = Collections.synchronizedSet(EnumSet.noneOf(StatisticType.class));

    private int rankBuildTaskId = -1;
    private int cacheSize = 150;
    private boolean sortDescending = true;

    public RankingManager(HavenLeaderboard plugin) {
        this.plugin = plugin;
        for (StatisticType type : StatisticType.values()) {
            cachedRankings.put(type, new ArrayList<>());
            cachedPlayerRanks.put(type, Collections.emptyMap());
        }
    }

    public void startTask() {
        stopTask();
        loadRuntimeSettings();

        int rankIntervalTicks = Math.max(20, plugin.getConfigManager().getConfig().getInt("rank-build-interval-seconds", 30) * 20);

        rankBuildTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin.getJavaPlugin(),
                this::rebuildRankings,
                20L,
                rankIntervalTicks
        ).getTaskId();
        
        // Do an initial build
        Bukkit.getScheduler().runTaskAsynchronously(plugin.getJavaPlugin(), this::rebuildRankings);
    }

    public void stopTask() {
        if (rankBuildTaskId != -1) Bukkit.getScheduler().cancelTask(rankBuildTaskId);
        rankBuildTaskId = -1;
    }

    private void loadRuntimeSettings() {
        cacheSize = Math.max(10, plugin.getConfigManager().getConfig().getInt("cache-size", 150));
        sortDescending = plugin.getConfigManager().getConfig().getBoolean("sort-descending", true);

        enabledTypes.clear();
        for (StatisticType type : StatisticType.values()) {
            String key = "enabled-statistics." + type.name().toLowerCase();
            boolean enabledByDefault = type != StatisticType.SHARDS;
            if (plugin.getConfigManager().getConfig().getBoolean(key, enabledByDefault)) {
                enabledTypes.add(type);
            }
        }
        if (enabledTypes.contains(StatisticType.SHARDS) && plugin.getPapiProvider() == null) {
            enabledTypes.remove(StatisticType.SHARDS);
        }
    }

    public boolean isEnabled(StatisticType type) {
        return enabledTypes.contains(type);
    }

    private void rebuildRankings() {
        Set<StatisticType> snapshotTypes = EnumSet.noneOf(StatisticType.class);
        synchronized (enabledTypes) {
            snapshotTypes.addAll(enabledTypes);
        }

        for (StatisticType type : StatisticType.values()) {
            if (!snapshotTypes.contains(type)) {
                cachedRankings.put(type, new ArrayList<>());
                continue;
            }

            if (type == StatisticType.SHARDS && Bukkit.getPluginManager().isPluginEnabled("HavenShards")) {
                List<PlayerStat> temp = new ArrayList<>();
                try {
                    java.util.List<?> topEntries = null;
                    try {
                        Class<?> apiClass = Class.forName("com.haven.shards.api.HavenShardsAPI");
                        topEntries = (java.util.List<?>) apiClass.getMethod("getCachedTopEntries").invoke(null);
                    } catch (Throwable t) {
                        Class<?> havenShardsClass = Class.forName("com.haven.shards.HavenShards");
                        Object shardInstance = havenShardsClass.getMethod("getInstance").invoke(null);
                        Object dbManager = havenShardsClass.getMethod("getDatabaseManager").invoke(shardInstance);
                        topEntries = (java.util.List<?>) dbManager.getClass().getMethod("getCachedTopEntries").invoke(dbManager);
                    }
                    
                    for (Object entry : topEntries) {
                        if (temp.size() >= cacheSize) break;
                        String name = (String) entry.getClass().getMethod("getName").invoke(entry);
                        double value = (double) entry.getClass().getMethod("getBalance").invoke(entry);
                        if (value <= 0 && sortDescending) continue;
                        
                        Player onlinePlayer = Bukkit.getPlayerExact(name);
                        UUID uuid;
                        if (onlinePlayer != null) {
                            uuid = onlinePlayer.getUniqueId();
                        } else {
                            OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(name);
                            uuid = (op != null) ? op.getUniqueId() : Bukkit.getOfflinePlayer(name).getUniqueId();
                        }
                        PlayerStats raw = HavenCorePlugin.getInstance().getStatsCache().getStats(uuid);
                        String skinUrl = (raw != null && raw.getSkinUrl() != null) ? raw.getSkinUrl() : "none";
                        
                        temp.add(new PlayerStat(uuid, name, value, formatValue(type, value), skinUrl));
                    }
                    if (!sortDescending) {
                        Collections.reverse(temp); // TopEntry is normally descending. Reverse if needed.
                    }
                    // Eagerly resolve skin only for top 10 to protect background CPU
                    for (int i = 0; i < Math.min(10, temp.size()); i++) {
                        PlayerStat ps = temp.get(i);
                        if ("none".equals(ps.getSkinUrl()) || ps.getSkinUrl() == null || ps.getSkinUrl().isEmpty()) {
                            PlayerStats raw = HavenCorePlugin.getInstance().getStatsCache().getStats(ps.getUuid());
                            if (raw != null) {
                                if (raw.getSkinUrl() == null || raw.getSkinUrl().isEmpty() || "none".equals(raw.getSkinUrl())) {
                                    HavenCorePlugin.getInstance().getStatsCache().resolveOfflineSkin(ps.getUuid(), ps.getName(), raw);
                                }
                                ps.setSkinUrl(raw.getSkinUrl());
                            }
                        }
                    }
                } catch (Exception e) {
                    plugin.getJavaPlugin().getLogger().log(java.util.logging.Level.WARNING, "Failed to hook into HavenShards top cache using reflection", e);
                }
                cachedRankings.put(type, temp);
                continue;
            }

            java.util.PriorityQueue<PlayerStats> pq = new java.util.PriorityQueue<>(
                    cacheSize + 1,
                    (a, b) -> sortDescending 
                            ? Double.compare(a.getStat(type), b.getStat(type)) // Min-heap keeps largest elements
                            : Double.compare(b.getStat(type), a.getStat(type)) // Max-heap keeps smallest elements
            );

            for (PlayerStats raw : HavenCorePlugin.getInstance().getStatsCache().getAllStats()) {
                String name = raw.getName();
                if (name == null || name.isEmpty()) continue;

                double value = raw.getStat(type);
                if (value > 0 || !sortDescending) {
                    pq.offer(raw);
                    if (pq.size() > cacheSize) {
                        pq.poll();
                    }
                }
            }

            List<PlayerStat> temp = new ArrayList<>();
            while (!pq.isEmpty()) {
                PlayerStats raw = pq.poll();
                double value = raw.getStat(type);
                temp.add(new PlayerStat(raw.getUuid(), raw.getName(), value, formatValue(type, value), raw.getSkinUrl()));
            }

            // Priority queue polling gives us elements in ascending order (if min-heap). 
            // Reverse the list so the top rank (highest value) is at index 0.
            Collections.reverse(temp);

            // Eagerly resolve skins ONLY for the top 10 ranks to prevent querying
            // SkinsRestorer thousands of times every 30 seconds.
            // Remaining players resolve on-demand when their GUI page is actually opened.
            for (int i = 0; i < Math.min(10, temp.size()); i++) {
                PlayerStat ps = temp.get(i);
                if (ps.getSkinUrl() == null || ps.getSkinUrl().isEmpty() || "none".equals(ps.getSkinUrl())) {
                    PlayerStats raw = HavenCorePlugin.getInstance().getStatsCache().getStats(ps.getUuid());
                    if (raw != null) {
                        if (raw.getSkinUrl() == null || raw.getSkinUrl().isEmpty() || "none".equals(raw.getSkinUrl())) {
                            HavenCorePlugin.getInstance().getStatsCache().resolveOfflineSkin(ps.getUuid(), ps.getName(), raw);
                        }
                        ps.setSkinUrl(raw.getSkinUrl());
                    }
                }
            }

            Map<UUID, Integer> rankMap = new HashMap<>(temp.size());
            for (int i = 0; i < temp.size(); i++) {
                rankMap.put(temp.get(i).getUuid(), i + 1);
            }
            cachedPlayerRanks.put(type, Collections.unmodifiableMap(rankMap));
            cachedRankings.put(type, temp);
        }
    }

    private String formatValue(StatisticType type, double value) {
        if (type == StatisticType.MONEY && plugin.getVaultProvider() != null) {
            return plugin.getVaultProvider().format(value);
        }
        if (type == StatisticType.PLAYTIME) {
            long totalSeconds = (long) (value);
            long days = totalSeconds / 86400;
            long hours = (totalSeconds % 86400) / 3600;
            long minutes = (totalSeconds % 3600) / 60;

            if (days > 0) return days + "d " + hours + "h";
            else if (hours > 0) return hours + "h " + minutes + "m";
            else return minutes + "m";
        }
        if (value == (long) value) return String.valueOf((long) value);
        return String.valueOf(value);
    }

    public void requestRebuild() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin.getJavaPlugin(), this::rebuildRankings);
    }

    public List<PlayerStat> getTopPlayers(StatisticType type) {
        return cachedRankings.getOrDefault(type, new ArrayList<>());
    }

    public int getPlayerRank(StatisticType type, UUID uuid) {
        if (type == null || uuid == null) return -1;
        Map<UUID, Integer> ranks = cachedPlayerRanks.get(type);
        if (ranks != null) {
            Integer rank = ranks.get(uuid);
            if (rank != null) {
                return rank;
            }
        }
        return -1;
    }
}
