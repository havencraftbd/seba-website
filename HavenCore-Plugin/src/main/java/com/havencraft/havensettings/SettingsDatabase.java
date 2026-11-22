package com.havencraft.havensettings;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.logging.Level;

/**
 * High-concurrency SQLite storage engine for player settings using HikariCP connection pooling
 * and WAL (Write-Ahead Logging) mode.
 */
public class SettingsDatabase {

    private final JavaPlugin plugin;
    private final File dataFolder;
    private HikariDataSource dataSource;

    public SettingsDatabase(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "HavenSettings");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
    }

    public void connect() {
        try {
            File dbFile = new File(dataFolder, "database.db");
            HikariConfig config = new HikariConfig();
            config.setPoolName("HavenSettings-Pool");
            config.setDriverClassName("org.sqlite.JDBC");
            config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setConnectionTimeout(10000); // 10s
            config.setIdleTimeout(600000); // 10m
            config.setMaxLifetime(1800000); // 30m
            config.setLeakDetectionThreshold(60000); // 60s
            // High-performance SQLite PRAGMAs: WAL mode allows concurrent readers without write blocking
            config.setConnectionInitSql("PRAGMA journal_mode = WAL; PRAGMA synchronous = NORMAL; PRAGMA temp_store = MEMORY; PRAGMA mmap_size = 268435456;");

            this.dataSource = new HikariDataSource(config);
            createTables();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to connect to HavenSettings SQLite database!", e);
        }
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    private Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("HavenSettings DataSource is not initialized or closed.");
        }
        return dataSource.getConnection();
    }

    private void createTables() throws SQLException {
        try (Connection conn = getConnection(); Statement statement = conn.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS haven_settings (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "private_message BOOLEAN, " +
                    "tpa BOOLEAN, " +
                    "tpa_here BOOLEAN, " +
                    "tpa_confirmation BOOLEAN, " +
                    "scoreboard BOOLEAN, " +
                    "night_vision BOOLEAN, " +
                    "public_chat BOOLEAN, " +
                    "public_chat_muted_by_settings BOOLEAN" +
                    ");");

            ensureColumnExists(conn, "haven_settings", "tpa_confirmation", "BOOLEAN");

            statement.execute("CREATE TABLE IF NOT EXISTS haven_settings_custom (" +
                    "uuid VARCHAR(36), " +
                    "command_id VARCHAR(64), " +
                    "enabled BOOLEAN, " +
                    "PRIMARY KEY(uuid, command_id)" +
                    ");");
        }
    }

    private void ensureColumnExists(Connection conn, String tableName, String columnName, String columnType) throws SQLException {
        boolean exists = false;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (rs.next()) {
                if (columnName.equalsIgnoreCase(rs.getString("name"))) {
                    exists = true;
                    break;
                }
            }
        }
        if (!exists) {
            try (Statement alter = conn.createStatement()) {
                alter.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnType + ";");
            }
        }
    }

    public PlayerSettings loadPlayer(UUID uuid, SettingsManager manager) {
        PlayerSettings settings = new PlayerSettings(uuid);
        boolean found = false;

        try (Connection conn = getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM haven_settings WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        found = true;
                        settings.setMsgEnabled(rs.getBoolean("private_message"));
                        settings.setTpaEnabled(rs.getBoolean("tpa"));
                        settings.setTpaHereEnabled(rs.getBoolean("tpa_here"));
                        Object rawTpaConfirm = rs.getObject("tpa_confirmation");
                        settings.setTpaConfirmationEnabled(rawTpaConfirm != null
                                ? rs.getBoolean("tpa_confirmation")
                                : manager.getDefaultTpaConfirmation());
                        settings.setScoreboardEnabled(rs.getBoolean("scoreboard"));
                        settings.setNightVisionEnabled(rs.getBoolean("night_vision"));
                        settings.setPublicChatEnabled(rs.getBoolean("public_chat"));
                        settings.setPublicChatMutedBySettings(rs.getBoolean("public_chat_muted_by_settings"));
                    }
                }
            }

            if (found) {
                try (PreparedStatement ps = conn.prepareStatement("SELECT command_id, enabled FROM haven_settings_custom WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            settings.setCustomCommand(rs.getString("command_id"), rs.getBoolean("enabled"));
                        }
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load settings for " + uuid, e);
        }

        if (!found) {
            // Apply defaults for new player
            settings = manager.createDefaults(uuid);
        }

        // Ensure new custom commands get their defaults
        for (java.util.Map.Entry<String, Boolean> entry : manager.getCustomCommandDefaults().entrySet()) {
            if (!settings.hasCustomCommand(entry.getKey())) {
                settings.setCustomCommand(entry.getKey(), entry.getValue());
            }
        }

        return settings;
    }

    public void savePlayer(PlayerSettings settings) {
        String coreSql = "INSERT INTO haven_settings (uuid, private_message, tpa, tpa_here, tpa_confirmation, scoreboard, night_vision, public_chat, public_chat_muted_by_settings) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET " +
                "private_message = excluded.private_message, " +
                "tpa = excluded.tpa, " +
                "tpa_here = excluded.tpa_here, " +
                "tpa_confirmation = excluded.tpa_confirmation, " +
                "scoreboard = excluded.scoreboard, " +
                "night_vision = excluded.night_vision, " +
                "public_chat = excluded.public_chat, " +
                "public_chat_muted_by_settings = excluded.public_chat_muted_by_settings;";

        String customSql = "INSERT INTO haven_settings_custom (uuid, command_id, enabled) VALUES (?, ?, ?) " +
                "ON CONFLICT(uuid, command_id) DO UPDATE SET enabled = excluded.enabled;";

        try (Connection conn = getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(coreSql)) {
                ps.setString(1, settings.getUuid().toString());
                ps.setBoolean(2, settings.isMsgEnabled());
                ps.setBoolean(3, settings.isTpaEnabled());
                ps.setBoolean(4, settings.isTpaHereEnabled());
                ps.setBoolean(5, settings.isTpaConfirmationEnabled());
                ps.setBoolean(6, settings.isScoreboardEnabled());
                ps.setBoolean(7, settings.isNightVisionEnabled());
                ps.setBoolean(8, settings.isPublicChatEnabled());
                ps.setBoolean(9, settings.isPublicChatMutedBySettings());
                ps.executeUpdate();
            }

            if (!settings.getCustomCommands().isEmpty()) {
                try (PreparedStatement ps = conn.prepareStatement(customSql)) {
                    for (java.util.Map.Entry<String, Boolean> entry : settings.getCustomCommands().entrySet()) {
                        ps.setString(1, settings.getUuid().toString());
                        ps.setString(2, entry.getKey());
                        ps.setBoolean(3, entry.getValue());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save settings for " + settings.getUuid(), e);
        }
    }
}
