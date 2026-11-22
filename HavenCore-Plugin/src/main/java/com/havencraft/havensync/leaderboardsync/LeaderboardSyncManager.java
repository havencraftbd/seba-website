package com.havencraft.havensync.leaderboardsync;

import com.havencraft.havencore.HavenCorePlugin;
import com.havencraft.havencore.stats.PlayerStats;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Owner and orchestrator of the entire Leaderboard Sync system.
 */
public class LeaderboardSyncManager {

    private final JavaPlugin plugin;
    private final LeaderboardHttpClient httpClient;
    private final boolean debug;
    private int syncTaskId = -1;
    private int historicalSyncTaskId = -1;
    private long lastSyncTime = 0;

    public LeaderboardSyncManager(
            JavaPlugin plugin,
            String supabaseUrl,
            String tableName,
            String apiKey,
            String conflictKey,
            int connectTimeout,
            int readTimeout,
            int syncIntervalTicks,
            int maxBatchSize,
            boolean debug,
            Map<String, String> columnMap
    ) {
        this.plugin = plugin;
        this.debug = debug;

        this.httpClient = new LeaderboardHttpClient(supabaseUrl, tableName, apiKey, conflictKey, connectTimeout, readTimeout, maxBatchSize, plugin.getLogger(), debug, columnMap);

        // Sync runs asynchronously since CentralStatsCache handles main thread Vault balances.
        this.syncTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> triggerFlush(false), syncIntervalTicks, syncIntervalTicks).getTaskId();
        this.lastSyncTime = System.currentTimeMillis();
    }

    /**
     * Triggers a startup historical sync that pushes ALL cached player data to Supabase
     * in throttled batches. Runs fully async with configurable delay and batch intervals.
     *
     * @param delayTicks       ticks to wait before starting the historical sync
     * @param batchSize        number of players per batch
     * @param batchIntervalTicks ticks to wait between each batch
     */
    public void triggerHistoricalSync(int delayTicks, int batchSize, int batchIntervalTicks) {
        // Schedule the historical sync after the initial delay
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            // Ensure Shards are freshly synced for all cached players prior to pushing
            HavenCorePlugin.getInstance().getStatsCache().syncShardsFromHavenShardsAsync(
                    HavenCorePlugin.getInstance().getStatsCache().getAllStats(), null, 0
            );

            List<PlayerStats> allStats = new ArrayList<>(HavenCorePlugin.getInstance().getStatsCache().getAllStats());
            int totalPlayers = allStats.size();

            if (totalPlayers == 0) {
                plugin.getLogger().info("[LeaderboardSync] Historical sync: No players to sync.");
                return;
            }

            plugin.getLogger().info("[LeaderboardSync] Historical sync started: " + totalPlayers + " players in batches of " + batchSize + ".");

            // Split into batches and schedule them with intervals
            AtomicInteger batchIndex = new AtomicInteger(0);
            int totalBatches = (int) Math.ceil((double) totalPlayers / batchSize);

            historicalSyncTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
                int currentBatch = batchIndex.getAndIncrement();
                int start = currentBatch * batchSize;

                if (start >= totalPlayers) {
                    // All batches sent, cancel task
                    if (historicalSyncTaskId != -1) {
                        Bukkit.getScheduler().cancelTask(historicalSyncTaskId);
                        historicalSyncTaskId = -1;
                    }
                    plugin.getLogger().info("[LeaderboardSync] Historical sync completed! All " + totalPlayers + " players synced.");
                    return;
                }

                int end = Math.min(start + batchSize, totalPlayers);
                List<PlayerStats> batch = allStats.subList(start, end);
                httpClient.pushBatchData(new ArrayList<>(batch));

                if (debug) {
                    plugin.getLogger().info("[LeaderboardSync] Historical sync batch " + (currentBatch + 1) + "/" + totalBatches + " sent (" + batch.size() + " players).");
                }
            }, 0L, batchIntervalTicks).getTaskId();

        }, delayTicks);
    }

    public void triggerFlush(boolean isShutdown) {
        List<PlayerStats> snapshot = new ArrayList<>();
        long currentSyncTime = System.currentTimeMillis();
        
        for (PlayerStats stats : HavenCorePlugin.getInstance().getStatsCache().getAllStats()) {
            if (isShutdown || stats.getLastModified() > lastSyncTime) {
                snapshot.add(stats);
            }
        }
        
        lastSyncTime = currentSyncTime;
        httpClient.pushBatchData(snapshot);
    }

    public void triggerFlushSync() {
        List<PlayerStats> snapshot = new ArrayList<>();
        
        for (PlayerStats stats : HavenCorePlugin.getInstance().getStatsCache().getAllStats()) {
            if (stats.getLastModified() > lastSyncTime) {
                snapshot.add(stats);
            }
        }
        
        httpClient.pushBatchDataSync(snapshot);
    }

    public void shutdown() {
        if (syncTaskId != -1) {
            Bukkit.getScheduler().cancelTask(syncTaskId);
        }
        if (historicalSyncTaskId != -1) {
            Bukkit.getScheduler().cancelTask(historicalSyncTaskId);
        }
        
        triggerFlushSync();
        httpClient.forceDisconnect();
    }

}
