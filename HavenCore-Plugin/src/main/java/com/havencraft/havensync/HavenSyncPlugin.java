package com.havencraft.havensync;

import com.havencraft.havencore.util.ConfigLoader;
import com.havencraft.havensync.leaderboardsync.LeaderboardSyncManager;
import com.havencraft.havensync.votesync.VoteSyncManager;
import com.tchristofferson.configupdater.ConfigUpdater;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * HavenSync — Isolated Vote and Leaderboard Bridge.
 */
public final class HavenSyncPlugin {

    private final JavaPlugin plugin;

    private VoteSyncManager voteSyncManager;
    private LeaderboardSyncManager leaderboardSyncManager;
    private boolean isReload = false;

    public HavenSyncPlugin(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public LeaderboardSyncManager getLeaderboardSyncManager() {
        return leaderboardSyncManager;
    }

    public void onEnable() {
        FileConfiguration config = ConfigLoader.load(plugin, "HavenSync/config.yml", Arrays.asList("vote-sync.columns", "leaderboard-sync.columns"));

        try {
            File guideFile = new File(plugin.getDataFolder(), "HavenSync/SUPABASE_SETUP_GUIDE.md");
            if (!guideFile.exists()) {
                plugin.saveResource("HavenSync/SUPABASE_SETUP_GUIDE.md", false);
            }
        } catch (IllegalArgumentException e) {
            // Document might not be in jar in some build configs
        }
        
        Logger logger = plugin.getLogger();

        // 1. Initialize Vote Sync
        if (config.getBoolean("vote-sync.enabled", true)) {
            String vUrl = config.getString("vote-sync.supabase_url", "");
            String vKey = config.getString("vote-sync.api_key", "");
            if (isValid(vUrl, vKey)) {
                String vTable = config.getString("vote-sync.table", "");
                Map<String, String> vColumns = readColumnMapping(config, "vote-sync.columns");

                this.voteSyncManager = new VoteSyncManager(
                        plugin,
                        vUrl,
                        vTable,
                        vKey,
                        config.getInt("vote-sync.connect_timeout_ms", 5000),
                        config.getInt("vote-sync.read_timeout_ms", 5000),
                        config.getInt("vote-sync.sync_interval_ticks", 600),
                        config.getInt("vote-sync.duplicate_window_seconds", 300),
                        config.getBoolean("vote-sync.debug", false),
                        vColumns
                );
            } else {
                logger.severe("[VoteSync] Invalid credentials! Module disabled.");
            }
        }

        // 2. Initialize Leaderboard Sync
        if (config.getBoolean("leaderboard-sync.enabled", true)) {
            String lUrl = config.getString("leaderboard-sync.supabase_url", "");
            String lKey = config.getString("leaderboard-sync.api_key", "");
            if (isValid(lUrl, lKey)) {
                String lTable = config.getString("leaderboard-sync.table", "");
                String lConflictKey = config.getString("leaderboard-sync.upsert_conflict_key", "uuid");
                Map<String, String> lColumns = readColumnMapping(config, "leaderboard-sync.columns");

                this.leaderboardSyncManager = new LeaderboardSyncManager(
                        plugin,
                        lUrl,
                        lTable,
                        lKey,
                        lConflictKey,
                        config.getInt("leaderboard-sync.connect_timeout_ms", 8000),
                        config.getInt("leaderboard-sync.read_timeout_ms", 8000),
                        config.getInt("leaderboard-sync.sync_interval_ticks", 1200),
                        config.getInt("leaderboard-sync.max_batch_size", 50),
                        config.getBoolean("leaderboard-sync.debug", false),
                        lColumns
                );

                // Startup historical sync — only on first startup, not on /hc reload
                if (!isReload && config.getBoolean("leaderboard-sync.startup_historical_sync_enabled", true)) {
                    int delayTicks = config.getInt("leaderboard-sync.startup_historical_sync_delay_ticks", 200);
                    int batchSize = config.getInt("leaderboard-sync.startup_historical_sync_batch_size", 300);
                    int batchInterval = config.getInt("leaderboard-sync.startup_historical_sync_batch_interval_ticks", 20);
                    this.leaderboardSyncManager.triggerHistoricalSync(delayTicks, batchSize, batchInterval);
                    logger.info("[LeaderboardSync] Startup historical sync scheduled (delay: " + delayTicks + " ticks).");
                }
            } else {
                logger.severe("[LeaderboardSync] Invalid credentials! Module disabled.");
            }
        }
    }

    public void onDisable() {
        if (voteSyncManager != null) {
            voteSyncManager.shutdown();
            voteSyncManager = null;
        }
        if (leaderboardSyncManager != null) {
            leaderboardSyncManager.shutdown();
            leaderboardSyncManager = null;
        }
    }

    public void reload() {
        onDisable();
        isReload = true;
        onEnable();
        isReload = false;
    }

    /**
     * Reads a YAML columns section into a String→String map.
     * Returns an empty map if the section doesn't exist (HttpClients will use hardcoded defaults).
     */
    private Map<String, String> readColumnMapping(FileConfiguration config, String path) {
        Map<String, String> map = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String value = section.getString(key);
                if (value != null && !value.isEmpty()) {
                    map.put(key, value);
                }
            }
        }
        return map;
    }

    private boolean isValid(String url, String key) {
        return url != null && url.startsWith("http") && !url.contains("YOUR_") && !url.contains("ey...")
                && key != null && key.length() > 20 && !key.contains("YOUR_") && !key.equals("ey...");
    }
}
