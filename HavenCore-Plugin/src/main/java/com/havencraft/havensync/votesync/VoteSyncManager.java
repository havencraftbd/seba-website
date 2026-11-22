package com.havencraft.havensync.votesync;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.logging.Logger;

/**
 * Initializes and manages the schedule for the Vote Sync subsystem.
 */
public class VoteSyncManager {
    
    private final JavaPlugin plugin;
    private final VoteHttpClient httpClient;
    private final VoteListener voteListener;
    private int syncTaskId = -1;

    public VoteSyncManager(JavaPlugin plugin, String supabaseUrl, String tableName, String apiKey, int connectTimeout, int readTimeout, int syncIntervalTicks, int duplicateWindowSeconds, boolean debug, Map<String, String> columnMap) {
        this.plugin = plugin;
        Logger logger = plugin.getLogger();

        if (!isVotifierAvailable()) {
            this.httpClient = null;
            this.voteListener = null;
            logger.info("[VoteSync] Votifier plugin not detected; VoteSync listener skipped.");
            return;
        }

        this.httpClient = new VoteHttpClient(supabaseUrl, tableName, apiKey, connectTimeout, readTimeout, logger, debug, columnMap);
        this.voteListener = new VoteListener(plugin, logger, duplicateWindowSeconds);
        
        plugin.getServer().getPluginManager().registerEvents(voteListener, plugin);
        
        // Setup isolated repeating task purely for VoteSync batched upserts
        this.syncTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::triggerFlush, syncIntervalTicks, syncIntervalTicks).getTaskId();

        logger.info("[VoteSync] Modular subsystem initialized. Batched interval: " + syncIntervalTicks + " ticks.");
    }

    private static boolean isVotifierAvailable() {
        try {
            Class.forName("com.vexsoftware.votifier.model.VotifierEvent", false, VoteSyncManager.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }

    private void triggerFlush() {
        if (httpClient != null && voteListener != null) {
            httpClient.pushBatchData(voteListener.drainCache());
        }
    }

    public void shutdown() {
        if (syncTaskId != -1) {
            Bukkit.getScheduler().cancelTask(syncTaskId);
            syncTaskId = -1;
        }
        if (voteListener != null) {
            org.bukkit.event.HandlerList.unregisterAll(voteListener);
        }
        if (httpClient != null) {
            // Force one final flush synchronously on shutdown
            if (voteListener != null) {
                httpClient.pushBatchDataSync(voteListener.drainCache());
            }
            httpClient.forceDisconnect();
        }
    }
}
