package com.havencraft.havencore.stats;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DatabaseManager {
    private final JavaPlugin plugin;
    private final HikariDataSource dataSource;

    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
        File dataFolder = new File(plugin.getDataFolder(), "StatsCache");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        String dbPath = new File(dataFolder, "stats.db").getAbsolutePath();

        HikariConfig config = new HikariConfig();
        config.setPoolName("HavenCore-StatsPool");
        config.setDriverClassName("org.sqlite.JDBC");
        config.setJdbcUrl("jdbc:sqlite:" + dbPath);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(10000); // 10s timeout
        config.setIdleTimeout(600000); // 10m idle timeout
        config.setMaxLifetime(1800000); // 30m max lifetime
        config.setLeakDetectionThreshold(60000); // 60s leak detection
        // High-performance SQLite PRAGMA settings: WAL mode allows concurrent readers without write blocking
        config.setConnectionInitSql("PRAGMA journal_mode = WAL; PRAGMA synchronous = NORMAL; PRAGMA temp_store = MEMORY; PRAGMA mmap_size = 268435456;");

        this.dataSource = new HikariDataSource(config);
    }

    public void init() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS players (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "name VARCHAR(32), " +
                    "skin_url TEXT, " +
                    "last_modified BIGINT)");

            stmt.execute("CREATE TABLE IF NOT EXISTS player_stats (" +
                    "uuid VARCHAR(36), " +
                    "stat_type VARCHAR(32), " +
                    "stat_value DOUBLE, " +
                    "PRIMARY KEY (uuid, stat_type))");
        } catch (SQLException e) {
            plugin.getLogger().severe("[HavenCore] Failed to initialize SQLite database: " + e.getMessage());
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    public boolean isEmpty() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM players")) {
            if (rs.next()) {
                return rs.getInt(1) == 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[HavenCore] Failed to check if SQLite is empty: " + e.getMessage());
        }
        return true;
    }

    public void saveAll(Collection<PlayerStats> allStats) {
        if (allStats == null || allStats.isEmpty()) return;
        
        String insertPlayer = "INSERT OR REPLACE INTO players (uuid, name, skin_url, last_modified) VALUES (?, ?, ?, ?)";
        String insertStat = "INSERT OR REPLACE INTO player_stats (uuid, stat_type, stat_value) VALUES (?, ?, ?)";
        
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            
            try (PreparedStatement psPlayer = conn.prepareStatement(insertPlayer);
                 PreparedStatement psStat = conn.prepareStatement(insertStat)) {
                 
                 for (PlayerStats stats : allStats) {
                     psPlayer.setString(1, stats.getUuid().toString());
                     psPlayer.setString(2, stats.getName());
                     psPlayer.setString(3, stats.getSkinUrl());
                     psPlayer.setLong(4, stats.getLastModified());
                     psPlayer.addBatch();
                     
                     for (Map.Entry<StatisticType, Double> entry : stats.getValues().entrySet()) {
                         psStat.setString(1, stats.getUuid().toString());
                         psStat.setString(2, entry.getKey().name());
                         psStat.setDouble(3, entry.getValue());
                         psStat.addBatch();
                     }
                 }
                 
                 psPlayer.executeBatch();
                 psStat.executeBatch();
                 conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[HavenCore] Failed to save stats to SQLite: " + e.getMessage());
        }
    }

    public PlayerStats loadPlayer(UUID uuid) {
        PlayerStats stats = null;
        try (Connection conn = getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM players WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        stats = new PlayerStats(uuid, rs.getString("name"));
                        stats.setSkinUrl(rs.getString("skin_url"));
                    }
                }
            }
            if (stats != null) {
                try (PreparedStatement ps = conn.prepareStatement("SELECT stat_type, stat_value FROM player_stats WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            StatisticType type = StatisticType.fromString(rs.getString("stat_type"));
                            if (type != null) {
                                stats.setStat(type, rs.getDouble("stat_value"));
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[HavenCore] Failed to load player from SQLite: " + e.getMessage());
        }
        return stats;
    }

    public List<UUID> getTopPlayers(StatisticType type, int limit) {
        List<UUID> top = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT uuid FROM player_stats WHERE stat_type = ? ORDER BY stat_value DESC LIMIT ?")) {
            ps.setString(1, type.name());
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        top.add(UUID.fromString(rs.getString("uuid")));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[HavenCore] Failed to get top players from SQLite: " + e.getMessage());
        }
        return top;
    }
}
