package com.havencraft.havencore.stats;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.Statistic;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import net.milkbowl.vault.economy.Economy;
import me.clip.placeholderapi.PlaceholderAPI;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CentralStatsCache {

    private final JavaPlugin plugin;
    private final Map<UUID, PlayerStats> rawData = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> pendingLoads = ConcurrentHashMap.newKeySet();
    private DatabaseManager dbManager;

    private int onlineRefreshTaskId = -1;
    private int autoSaveTaskId = -1;
    private int batchedMoneyFetchTaskId = -1;

    private final File dataFile;
    private final Gson gson;
    private Economy econ = null;
    private boolean hasPapi = false;

    public CentralStatsCache(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "StatsCache/data.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.dbManager = new DatabaseManager(plugin);
        this.dbManager.init();
    }

    public void startTask() {
        stopTask();

        // Setup Economy
        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                econ = rsp.getProvider();
            }
        }
        hasPapi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;

        File statsDir = new File(Bukkit.getWorlds().get(0).getWorldFolder(), "stats");
        File serverRoot = Bukkit.getWorlds().get(0).getWorldFolder().getParentFile();

        if (dbManager == null) {
            dbManager = new DatabaseManager(plugin);
            dbManager.init();
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // 1. Migration
            if (dataFile.exists()) {
                plugin.getLogger().info("[HavenCore] Migrating data.json to SQLite database...");
                Map<UUID, PlayerStats> allData = loadAllDataFromFile();
                dbManager.saveAll(allData.values());
                File oldFile = new File(dataFile.getParentFile(), "data.json.old");
                if (oldFile.exists()) oldFile.delete();
                dataFile.renameTo(oldFile);
                plugin.getLogger().info("[HavenCore] Migration complete.");
            }
            
            // 2. Preload from Minecraft stats files ONLY if the database is empty (first-time import)
            if (dbManager.isEmpty()) {
                plugin.getLogger().info("[HavenCore] Stats database is empty. Preloading statistics from Minecraft stats files (this may take a while)...");
                Map<UUID, PlayerStats> preloadedData = new HashMap<>();
                preloadFromStatsFiles(statsDir, serverRoot, preloadedData);
                dbManager.saveAll(preloadedData.values());
            }

            // 3. Find top players to keep in cache
            java.util.Set<UUID> keep = new java.util.HashSet<>();
            int cacheSize = getLimit();
            for (StatisticType type : StatisticType.values()) {
                List<UUID> topUUIDs = dbManager.getTopPlayers(type, cacheSize);
                keep.addAll(topUUIDs);
            }
            // Include top shard players from HavenShards database
            syncShardsFromHavenShardsAsync(null, keep, cacheSize);

            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    keep.add(player.getUniqueId());
                }
                
                rawData.clear();
                
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    for (UUID uuid : keep) {
                        PlayerStats stats = dbManager.loadPlayer(uuid);
                        if (stats != null) {
                            rawData.put(uuid, stats);
                        } else {
                            PlayerStats newStats = new PlayerStats(uuid, null);
                            rawData.put(uuid, newStats);
                        }
                    }
                    
                    // Sync shard balances for all cached players
                    syncShardsFromHavenShardsAsync(rawData.values(), null, 0);

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        // Initialize online players
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            handlePlayerJoin(player);
                        }
                        
                        if (econ != null) {
                            scheduleBatchedMoneyFetch();
                        }
                        
                        plugin.getLogger().info("[HavenCore] CentralStatsCache ready with " + rawData.size() + " active/top players loaded.");
                    });
                });
            });
        });

        // Refresh online players every 2 minutes (mostly for Vault/PAPI polling)
        onlineRefreshTaskId = Bukkit.getScheduler().runTaskTimer(
                plugin,
                () -> refreshAllOnlinePlayers(2400),
                2400L,
                2400L
        ).getTaskId();

        // Save and prune every 10 minutes
        autoSaveTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin,
                () -> {
                    saveData();
                    pruneOfflinePlayers();
                },
                12000L,
                12000L
        ).getTaskId();
    }

    public void stopTask() {
        if (onlineRefreshTaskId != -1) Bukkit.getScheduler().cancelTask(onlineRefreshTaskId);
        if (autoSaveTaskId != -1) Bukkit.getScheduler().cancelTask(autoSaveTaskId);
        if (batchedMoneyFetchTaskId != -1) Bukkit.getScheduler().cancelTask(batchedMoneyFetchTaskId);

        onlineRefreshTaskId = -1;
        autoSaveTaskId = -1;
        batchedMoneyFetchTaskId = -1;

        saveData();
    }

    private Map<UUID, PlayerStats> loadAllDataFromFile() {
        if (!dataFile.exists()) return new HashMap<>();
        try (java.io.InputStreamReader reader = new java.io.InputStreamReader(new java.io.FileInputStream(dataFile), java.nio.charset.StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<UUID, PlayerStats>>(){}.getType();
            Map<UUID, PlayerStats> loaded = gson.fromJson(reader, type);
            return loaded != null ? loaded : new HashMap<>();
        } catch (Exception e) {
            plugin.getLogger().severe("[HavenCore] Could not load StatsCache data.json! " + e.getMessage());
            return new HashMap<>();
        }
    }

    private synchronized void saveData() {
        if (dbManager == null) return;
        try {
            dbManager.saveAll(rawData.values());
        } catch (Exception e) {
            plugin.getLogger().severe("[HavenCore] Could not save StatsCache to SQLite! " + e.getMessage());
        }
    }

    private void pruneOfflinePlayers() {
        if (dbManager == null) return;
        java.util.Set<UUID> keep = new java.util.HashSet<>();
        
        int cacheSize = getLimit();
        for (StatisticType type : StatisticType.values()) {
            keep.addAll(dbManager.getTopPlayers(type, cacheSize));
        }
        syncShardsFromHavenShardsAsync(null, keep, cacheSize);
        
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                keep.add(p.getUniqueId());
            }
            rawData.keySet().retainAll(keep);
        });
    }

    private void loadStatsFromFile(UUID uuid, PlayerStats playerStats, File statsDir) {
        File sf = new File(statsDir, uuid.toString() + ".json");
        if (!sf.exists()) return;

        try (java.io.InputStreamReader reader = new java.io.InputStreamReader(new java.io.FileInputStream(sf), java.nio.charset.StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (!root.has("stats")) return;
            JsonObject stats = root.getAsJsonObject("stats");

            if (stats.has("minecraft:custom")) {
                JsonObject custom = stats.getAsJsonObject("minecraft:custom");

                {
                    long pt = 0;
                    if (custom.has("minecraft:play_time")) pt = custom.get("minecraft:play_time").getAsLong();
                    else if (custom.has("minecraft:play_one_minute")) pt = custom.get("minecraft:play_one_minute").getAsLong();
                    updateStatSafely(playerStats, StatisticType.PLAYTIME, pt / 20.0);
                }
                if (custom.has("minecraft:player_kills") && !playerStats.getValues().containsKey(StatisticType.PLAYER_KILLS)) {
                    updateStatSafely(playerStats, StatisticType.PLAYER_KILLS, custom.get("minecraft:player_kills").getAsInt());
                }
                if (custom.has("minecraft:deaths") && !playerStats.getValues().containsKey(StatisticType.DEATHS)) {
                    updateStatSafely(playerStats, StatisticType.DEATHS, custom.get("minecraft:deaths").getAsInt());
                }
                if (custom.has("minecraft:mob_kills") && !playerStats.getValues().containsKey(StatisticType.MOBS_KILLED)) {
                    updateStatSafely(playerStats, StatisticType.MOBS_KILLED, custom.get("minecraft:mob_kills").getAsInt());
                }
            }

            if (stats.has("minecraft:mined") && !playerStats.getValues().containsKey(StatisticType.BLOCKS_BROKEN)) {
                long total = 0;
                for (Map.Entry<String, JsonElement> entry : stats.getAsJsonObject("minecraft:mined").entrySet()) {
                    total += entry.getValue().getAsLong();
                }
                updateStatSafely(playerStats, StatisticType.BLOCKS_BROKEN, total);
            }

            if (stats.has("minecraft:placed") && !playerStats.getValues().containsKey(StatisticType.BLOCKS_PLACED)) {
                long total = 0;
                for (Map.Entry<String, JsonElement> entry : stats.getAsJsonObject("minecraft:placed").entrySet()) {
                    total += entry.getValue().getAsLong();
                }
                updateStatSafely(playerStats, StatisticType.BLOCKS_PLACED, total);
            }
        } catch (Exception ignored) {}
    }

    private void preloadFromStatsFiles(File statsDir, File serverRoot, Map<UUID, PlayerStats> allData) {
        if (!statsDir.exists() || !statsDir.isDirectory()) return;

        Map<String, String> uuidToName = new HashMap<>();
        File usercache = new File(serverRoot, "usercache.json");
        if (usercache.exists()) {
            try (java.io.InputStreamReader reader = new java.io.InputStreamReader(new java.io.FileInputStream(usercache), java.nio.charset.StandardCharsets.UTF_8)) {
                JsonArray arr = JsonParser.parseReader(reader).getAsJsonArray();
                for (JsonElement el : arr) {
                    JsonObject obj = el.getAsJsonObject();
                    if (obj.has("uuid") && obj.has("name")) {
                        uuidToName.put(obj.get("uuid").getAsString(), obj.get("name").getAsString());
                    }
                }
            } catch (Exception ignored) {}
        }

        File[] statFiles = statsDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (statFiles == null) return;

        for (File sf : statFiles) {
            String filename = sf.getName().replace(".json", "");
            UUID uuid;
            try {
                uuid = UUID.fromString(filename);
            } catch (IllegalArgumentException e) {
                continue;
            }

            if (allData.containsKey(uuid)) {
                continue;
            }

            String name = uuidToName.get(uuid.toString());
            if (name == null || name.isEmpty()) {
                name = "Player";
            }
            final String finalName = name;

            PlayerStats playerStats = allData.computeIfAbsent(uuid, id -> new PlayerStats(id, finalName));
            loadStatsFromFile(uuid, playerStats, statsDir);
        }
    }

    private void scheduleBatchedMoneyFetch() {
        if (batchedMoneyFetchTaskId != -1) {
            Bukkit.getScheduler().cancelTask(batchedMoneyFetchTaskId);
            batchedMoneyFetchTaskId = -1;
        }

        if (econ == null) return;

        java.util.List<UUID> uuids = new java.util.ArrayList<>(rawData.keySet());
        java.util.Iterator<UUID> iterator = uuids.iterator();

        // Run on the primary server thread safely at a rate of 1 offline player per 20 ticks (1s).
        // 100% thread-safe for Vault & EssentialsX without off-main thread CME or database corruption.
        batchedMoneyFetchTaskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!iterator.hasNext()) {
                if (batchedMoneyFetchTaskId != -1) {
                    Bukkit.getScheduler().cancelTask(batchedMoneyFetchTaskId);
                    batchedMoneyFetchTaskId = -1;
                }
                return;
            }

            UUID uuid = iterator.next();
            // Skip online players (handled by refreshOnlinePlayerValues on main thread)
            if (Bukkit.getPlayer(uuid) != null) {
                return;
            }

            PlayerStats stats = rawData.get(uuid);
            if (stats != null && stats.getName() != null) {
                try {
                    OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
                    if (offlinePlayer != null) {
                        double bal = econ.getBalance(offlinePlayer);
                        stats.setStat(StatisticType.MONEY, Math.max(0D, bal));
                    }
                } catch (Throwable ignored) {}
            }
        }, 20L, 20L).getTaskId();
    }

    public void updateStatSafely(UUID uuid, String name, StatisticType type, double newValue) {
        PlayerStats stats = rawData.get(uuid);
        if (stats == null) {
            stats = rawData.computeIfAbsent(uuid, id -> {
                PlayerStats newStats = new PlayerStats(id, name);
                triggerAsyncLoad(id, newStats);
                return newStats;
            });
        }
        if (name != null && !name.isEmpty() && stats.getName() == null) stats.setName(name);
        updateStatSafely(stats, type, newValue);
    }

    private void updateStatSafely(PlayerStats stats, StatisticType type, double newValue) {
        if (type.isIncremental()) {
            double currentVal = stats.getStat(type);
            if (newValue > currentVal) {
                stats.setStat(type, newValue);
            }
        } else {
            stats.setStat(type, newValue);
        }
    }

    private void syncCacheToBukkit(Player player, PlayerStats stats) {
        try {
            if (stats.getValues().containsKey(StatisticType.PLAYER_KILLS)) {
                player.setStatistic(Statistic.PLAYER_KILLS, (int) stats.getStat(StatisticType.PLAYER_KILLS));
            }
            if (stats.getValues().containsKey(StatisticType.DEATHS)) {
                player.setStatistic(Statistic.DEATHS, (int) stats.getStat(StatisticType.DEATHS));
            }
            if (stats.getValues().containsKey(StatisticType.MOBS_KILLED)) {
                player.setStatistic(Statistic.MOB_KILLS, (int) stats.getStat(StatisticType.MOBS_KILLED));
            }
            if (stats.getValues().containsKey(StatisticType.PLAYTIME)) {
                player.setStatistic(Statistic.PLAY_ONE_MINUTE, (int) (stats.getStat(StatisticType.PLAYTIME) * 20));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[HavenCore] Failed to sync cache stats to Bukkit for " + player.getName() + ": " + e.getMessage());
        }
    }

    public void handlePlayerJoin(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerStats stats = rawData.get(uuid);
        if (stats == null) {
            stats = rawData.computeIfAbsent(uuid, id -> {
                PlayerStats newStats = new PlayerStats(id, player.getName());
                triggerAsyncLoad(id, newStats);
                return newStats;
            });
        }
        stats.setName(player.getName());

        // Cache the skin URL
        try {
            org.bukkit.profile.PlayerProfile profile = player.getPlayerProfile();
            if (profile.getTextures().getSkin() != null) {
                stats.setSkinUrl(profile.getTextures().getSkin().toString());
            } else if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                String papiSkin = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, "%skinsrestorer_texture_url%");
                if (papiSkin != null && papiSkin.startsWith("http")) {
                    stats.setSkinUrl(papiSkin);
                }
            }
        } catch (Exception ignored) {}

        // Sync our cache database values to player's Bukkit statistics FIRST if they exist in cache,
        // so Bukkit's native stats match what we have in cache.
        syncCacheToBukkit(player, stats);

        if (!stats.getValues().containsKey(StatisticType.BLOCKS_BROKEN) || !stats.getValues().containsKey(StatisticType.BLOCKS_PLACED)) {
            File statsDir = new File(Bukkit.getWorlds().get(0).getWorldFolder(), "stats");
            final PlayerStats targetStats = stats;
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                loadStatsFromFile(uuid, targetStats, statsDir);
            });
        }

        updateStatSafely(stats, StatisticType.PLAYTIME, player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20.0);
        if (!stats.getValues().containsKey(StatisticType.PLAYER_KILLS)) {
            updateStatSafely(stats, StatisticType.PLAYER_KILLS, player.getStatistic(Statistic.PLAYER_KILLS));
        }
        if (!stats.getValues().containsKey(StatisticType.DEATHS)) {
            updateStatSafely(stats, StatisticType.DEATHS, player.getStatistic(Statistic.DEATHS));
        }
        if (!stats.getValues().containsKey(StatisticType.MOBS_KILLED)) {
            updateStatSafely(stats, StatisticType.MOBS_KILLED, player.getStatistic(Statistic.MOB_KILLS));
        }

        refreshOnlinePlayerValues(player, 0, false);

        // Schedule delayed Shards update by 40 ticks to allow HavenShards async database loading to complete
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                PlayerStats s = rawData.get(player.getUniqueId());
                if (s != null) {
                    updatePlayerShards(player, s, false);
                }
            }
        }, 40L);
    }

    public void handlePlayerQuit(Player player) {
        refreshOnlinePlayerValues(player, 0, true); // Force final fetch with quit protection
        
        // Also do a Bukkit sync just in case
        PlayerStats stats = rawData.get(player.getUniqueId());
        if (stats != null) {
            updateStatSafely(stats, StatisticType.PLAYER_KILLS, player.getStatistic(Statistic.PLAYER_KILLS));
            updateStatSafely(stats, StatisticType.DEATHS, player.getStatistic(Statistic.DEATHS));
            updateStatSafely(stats, StatisticType.MOBS_KILLED, player.getStatistic(Statistic.MOB_KILLS));
            updateStatSafely(stats, StatisticType.PLAYTIME, player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20.0);
        }

        // Save data to SQLite asynchronously and then prune
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (stats != null) {
                    dbManager.saveAll(List.of(stats));
                }
            } catch (Exception e) {
                plugin.getLogger().severe("[HavenCore] Failed to save quitting player to SQLite: " + e.getMessage());
            }
            pruneOfflinePlayers();
        });
    }

    public void incrementStat(UUID uuid, String name, StatisticType type, double delta) {
        if (delta == 0) return;
        PlayerStats stats = rawData.get(uuid);
        if (stats == null) {
            stats = rawData.computeIfAbsent(uuid, id -> {
                PlayerStats newStats = new PlayerStats(id, name);
                triggerAsyncLoad(id, newStats);
                return newStats;
            });
        }
        if (name != null && !name.isEmpty() && stats.getName() == null) stats.setName(name);
        stats.incrementStat(type, delta);
    }

    private void refreshAllOnlinePlayers(int elapsedTicks) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refreshOnlinePlayerValues(player, elapsedTicks, false);
        }
    }

    private void refreshOnlinePlayerValues(Player player, int elapsedTicks) {
        refreshOnlinePlayerValues(player, elapsedTicks, false);
    }

    private void refreshOnlinePlayerValues(Player player, int elapsedTicks, boolean isQuit) {
        PlayerStats stats = rawData.get(player.getUniqueId());
        if (stats == null) return;

        updateStatSafely(stats, StatisticType.PLAYER_KILLS, player.getStatistic(Statistic.PLAYER_KILLS));
        updateStatSafely(stats, StatisticType.DEATHS, player.getStatistic(Statistic.DEATHS));
        updateStatSafely(stats, StatisticType.MOBS_KILLED, player.getStatistic(Statistic.MOB_KILLS));

        updateStatSafely(stats, StatisticType.PLAYTIME, player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20.0);
        if (econ != null) {
            try {
                stats.setStat(StatisticType.MONEY, Math.max(0D, econ.getBalance(player)));
            } catch (Exception ignored) {}
        }
        updatePlayerShards(player, stats, isQuit);
    }

    private void updatePlayerShards(Player player, PlayerStats stats, boolean isQuit) {
        boolean shardsUpdated = false;
        if (Bukkit.getPluginManager().isPluginEnabled("HavenShards")) {
            try {
                Class<?> apiClass = Class.forName("com.haven.shards.api.HavenShardsAPI");
                double bal = (double) apiClass.getMethod("getBalance", Player.class).invoke(null, player);
                if (bal > 0D) {
                    stats.setStat(StatisticType.SHARDS, bal);
                    shardsUpdated = true;
                } else if (bal == 0D) {
                    // Prevent overwriting positive balance when HavenShards unloads player on quit
                    // or when HavenShards is still loading player on join
                    if (stats.getStat(StatisticType.SHARDS) > 0D) {
                        if (isQuit) {
                            shardsUpdated = true; // Preserve positive balance on quit
                        } else {
                            try {
                                Class<?> hsClass = Class.forName("com.haven.shards.HavenShards");
                                Object hsInstance = hsClass.getMethod("getInstance").invoke(null);
                                Object sm = hsClass.getMethod("getShardManager").invoke(hsInstance);
                                boolean loaded = (boolean) sm.getClass().getMethod("isLoaded", UUID.class).invoke(sm, player.getUniqueId());
                                if (loaded) {
                                    stats.setStat(StatisticType.SHARDS, 0D);
                                }
                                shardsUpdated = true;
                            } catch (Throwable ignored) {}
                        }
                    } else {
                        stats.setStat(StatisticType.SHARDS, 0D);
                        shardsUpdated = true;
                    }
                }
            } catch (Throwable ignored) {}
        }
        if (!shardsUpdated && hasPapi) {
            try {
                String parsed = PlaceholderAPI.setPlaceholders(player, "%havenshards_balance%");
                if (parsed.equals("%havenshards_balance%") || !parsed.matches("-?\\d+(\\.\\d+)?")) {
                    parsed = PlaceholderAPI.setPlaceholders(player, "%eco_shards%");
                }
                String clean = parsed.replace(",", "").trim();
                if (clean.matches("-?\\d+(\\.\\d+)?")) {
                    double bal = Double.parseDouble(clean);
                    if (bal > 0D || stats.getStat(StatisticType.SHARDS) == 0D || !isQuit) {
                        stats.setStat(StatisticType.SHARDS, Math.max(0D, bal));
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    /**
     * Efficiently and asynchronously synchronizes Shards from HavenShards database pool.
     * ZERO main-thread blocking, zero TPS impact.
     */
    public void syncShardsFromHavenShardsAsync(Collection<PlayerStats> targetStats, Set<UUID> outTopUUIDs, int topLimit) {
        if (!Bukkit.getPluginManager().isPluginEnabled("HavenShards")) return;
        try {
            Class<?> havenShardsClass = Class.forName("com.haven.shards.HavenShards");
            Object shardInstance = havenShardsClass.getMethod("getInstance").invoke(null);
            if (shardInstance == null) return;
            Object hsDbManager = havenShardsClass.getMethod("getDatabaseManager").invoke(shardInstance);
            if (hsDbManager == null) return;

            try (Connection conn = (Connection) hsDbManager.getClass().getMethod("getConnection").invoke(hsDbManager)) {
                if (conn == null) return;

                // 1. Fetch top shard player UUIDs if requested
                if (outTopUUIDs != null && topLimit > 0) {
                    String topQuery = "SELECT uuid FROM haven_shards ORDER BY balance DESC LIMIT ?";
                    try (PreparedStatement ps = conn.prepareStatement(topQuery)) {
                        ps.setInt(1, topLimit);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                try {
                                    outTopUUIDs.add(UUID.fromString(rs.getString("uuid")));
                                } catch (IllegalArgumentException ignored) {}
                            }
                        }
                    }
                }

                // 2. Map existing players to their Shards balance and player name
                if (targetStats != null && !targetStats.isEmpty()) {
                    Map<UUID, PlayerStats> map = new HashMap<>();
                    for (PlayerStats ps : targetStats) {
                        if (ps.getUuid() != null) map.put(ps.getUuid(), ps);
                    }

                    String balQuery = "SELECT uuid, player_name, balance FROM haven_shards WHERE balance > 0";
                    try (Statement stmt = conn.createStatement();
                         ResultSet rs = stmt.executeQuery(balQuery)) {
                        while (rs.next()) {
                            try {
                                UUID u = UUID.fromString(rs.getString("uuid"));
                                PlayerStats ps = map.get(u);
                                if (ps != null) {
                                    double bal = rs.getDouble("balance");
                                    ps.setStat(StatisticType.SHARDS, bal);
                                    String pName = rs.getString("player_name");
                                    if (pName != null && !pName.isEmpty() && (ps.getName() == null || ps.getName().isEmpty())) {
                                        ps.setName(pName);
                                    }
                                }
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[HavenCore] Could not sync shards from HavenShards: " + t.getMessage());
        }
    }

    public PlayerStats getStats(UUID uuid) {
        PlayerStats stats = rawData.get(uuid);
        if (stats == null) {
            if (Bukkit.isPrimaryThread()) {
                triggerAsyncLoad(uuid, null);
                return null;
            } else {
                stats = dbManager.loadPlayer(uuid);
                if (stats != null) {
                    rawData.putIfAbsent(uuid, stats);
                }
            }
        }
        return stats;
    }

    private void triggerAsyncLoad(UUID uuid, PlayerStats targetStats) {
        if (!pendingLoads.add(uuid)) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                PlayerStats fromDb = dbManager.loadPlayer(uuid);
                if (fromDb != null) {
                    if (targetStats != null) {
                        mergeDbStatsIntoCache(targetStats, fromDb);
                    } else {
                        rawData.putIfAbsent(uuid, fromDb);
                    }
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("[HavenCore] Failed to async-load player stats for " + uuid + ": " + t.getMessage());
            } finally {
                pendingLoads.remove(uuid);
            }
        });
    }

    private void mergeDbStatsIntoCache(PlayerStats target, PlayerStats fromDb) {
        if (fromDb.getName() != null && (target.getName() == null || target.getName().isEmpty())) {
            target.setName(fromDb.getName());
        }
        if (fromDb.getSkinUrl() != null && target.getSkinUrl() == null) {
            target.setSkinUrl(fromDb.getSkinUrl());
        }
        for (Map.Entry<StatisticType, Double> entry : fromDb.getValues().entrySet()) {
            target.getValues().merge(entry.getKey(), entry.getValue(), (current, dbVal) -> {
                if (entry.getKey().isIncremental()) {
                    return dbVal + current;
                } else {
                    return Math.max(dbVal, current);
                }
            });
        }
    }

    public void close() {
        stopTask();
        if (dbManager != null) {
            dbManager.close();
        }
    }

    private int getLimit() {
        int cacheSize = 150;
        File configFile = new File(plugin.getDataFolder(), "Leaderboard/config.yml");
        if (configFile.exists()) {
            try {
                org.bukkit.configuration.file.YamlConfiguration config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(configFile);
                cacheSize = Math.max(10, config.getInt("cache-size", 150));
            } catch (Exception ignored) {}
        }
        return cacheSize;
    }

    public void resolveOfflineSkin(UUID uuid, String name, PlayerStats stats) {
        if (uuid == null || name == null || name.isEmpty()) return;
        if (stats.getSkinUrl() != null && !stats.getSkinUrl().isEmpty()) return;
        if (Bukkit.getPluginManager().getPlugin("SkinsRestorer") == null) {
            stats.setSkinUrl("none");
            return;
        }
        try {
            net.skinsrestorer.api.SkinsRestorer api = net.skinsrestorer.api.SkinsRestorerProvider.get();
            java.util.Optional<net.skinsrestorer.api.property.SkinProperty> property = api.getPlayerStorage().getSkinForPlayer(uuid, name);
            if (!property.isPresent()) {
                property = api.getPlayerStorage().getSkinOfPlayer(uuid);
            }
            if (property.isPresent()) {
                String base64 = property.get().getValue();
                if (base64 != null && !base64.isEmpty()) {
                    byte[] decoded = java.util.Base64.getDecoder().decode(base64);
                    String json = new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
                    JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                    if (obj.has("textures")) {
                        JsonObject textures = obj.getAsJsonObject("textures");
                        if (textures.has("SKIN")) {
                            JsonObject skin = textures.getAsJsonObject("SKIN");
                            if (skin.has("url")) {
                                stats.setSkinUrl(skin.get("url").getAsString());
                                return;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // Catch all Throwable (including NPE/StorageException from SkinsRestorer) to prevent console warning loops
        }
        
        // Cache negative lookup so we never call SkinsRestorer API again for this player
        stats.setSkinUrl("none");
    }

    public Collection<PlayerStats> getAllStats() {
        return rawData.values();
    }
}
