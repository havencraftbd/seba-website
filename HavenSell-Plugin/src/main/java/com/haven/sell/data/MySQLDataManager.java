package com.haven.sell.data;

import com.haven.sell.HavenSell;
import com.haven.sell.config.MySQLConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * MySQL-based data manager for player sell statistics and history.
 * All database operations run asynchronously to avoid blocking the main thread.
 * Uses two tables: one for stats, one for sell history entries.
 */
public class MySQLDataManager implements DataManager {

    private final HavenSell plugin;
    private final MySQLConfig config;
    private final String tableName;
    private final String historyTable;
    private final Map<UUID, PlayerData> cache;
    private HikariDataSource dataSource;

    public MySQLDataManager(HavenSell plugin, MySQLConfig config) throws SQLException {
        this.plugin = plugin;
        this.config = config;
        this.tableName = config.getTable();
        this.historyTable = config.getTable() + "_history";
        this.cache = new ConcurrentHashMap<>();

        connect();
        createTables();
    }

    private synchronized void connect() {
        if (dataSource != null && !dataSource.isClosed()) return;
        
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.getJdbcUrl());
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());
        hikariConfig.setMaximumPoolSize(config.getMaxConnections());
        hikariConfig.setConnectionTimeout(config.getConnectionTimeout());
        hikariConfig.setPoolName("HavenSell-MySQL-Pool");
        
        dataSource = new HikariDataSource(hikariConfig);
    }

    private Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) connect();
        return dataSource.getConnection();
    }

    private void createTables() {
        String statsSQL = "CREATE TABLE IF NOT EXISTS `" + tableName + "` (" +
                "`uuid` VARCHAR(36) NOT NULL PRIMARY KEY, " +
                "`player_name` VARCHAR(16) NOT NULL, " +
                "`total_items_sold` INT NOT NULL DEFAULT 0, " +
                "`total_money_earned` DOUBLE NOT NULL DEFAULT 0.0, " +
                "`last_updated` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        String historySQL = "CREATE TABLE IF NOT EXISTS `" + historyTable + "` (" +
                "`id` INT AUTO_INCREMENT PRIMARY KEY, " +
                "`uuid` VARCHAR(36) NOT NULL, " +
                "`items_sold` INT NOT NULL, " +
                "`money_earned` DOUBLE NOT NULL, " +
                "`timestamp` BIGINT NOT NULL, " +
                "INDEX `idx_uuid` (`uuid`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(statsSQL);
            stmt.executeUpdate(historySQL);
            // Add material_name column if it doesn't exist (safe migration for existing databases)
            try {
                stmt.executeUpdate("ALTER TABLE `" + historyTable + "` ADD COLUMN `material_name` VARCHAR(64) DEFAULT NULL;");
            } catch (SQLException ignored) {
                // Column already exists — ignore
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create MySQL tables!", e);
        }
    }

    @Override
    public PlayerData loadPlayerData(UUID uuid, String playerName) {
        PlayerData cached = cache.get(uuid);
        if (cached != null) return cached;

        String sql = "SELECT * FROM `" + tableName + "` WHERE `uuid` = ?;";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                int totalItems = rs.getInt("total_items_sold");
                double totalMoney = rs.getDouble("total_money_earned");
                List<SellEntry> history = loadHistory(uuid);
                PlayerData data = new PlayerData(uuid, playerName, totalItems, totalMoney, history);
                cache.put(uuid, data);
                return data;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load player data from MySQL for " + playerName, e);
        }

        PlayerData data = new PlayerData(uuid, playerName);
        cache.put(uuid, data);
        return data;
    }

    @Override
    public PlayerData loadPlayerDataByName(String playerName) {
        for (PlayerData pd : cache.values()) {
            if (pd.getPlayerName().equalsIgnoreCase(playerName)) return pd;
        }

        String sql = "SELECT * FROM `" + tableName + "` WHERE `player_name` = ? LIMIT 1;";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerName);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                int totalItems = rs.getInt("total_items_sold");
                double totalMoney = rs.getDouble("total_money_earned");
                List<SellEntry> history = loadHistory(uuid);
                return new PlayerData(uuid, playerName, totalItems, totalMoney, history);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load player data by name from MySQL for " + playerName, e);
        }
        return null;
    }

    private List<SellEntry> loadHistory(UUID uuid) {
        List<SellEntry> entries = new ArrayList<>();
        String sql = "SELECT `items_sold`, `money_earned`, `timestamp`, `material_name` FROM `" + historyTable +
                "` WHERE `uuid` = ? ORDER BY `timestamp` ASC LIMIT " + PlayerData.MAX_HISTORY + ";";

        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String materialName = rs.getString("material_name");
                entries.add(new SellEntry(rs.getInt("items_sold"), rs.getDouble("money_earned"), rs.getLong("timestamp"), materialName));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load sell history from MySQL for " + uuid, e);
        }
        return entries;
    }

    @Override
    public void savePlayerData(PlayerData data) {
        cache.put(data.getUuid(), data);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> writeToDatabase(data));
    }

    @Override
    public void addSellRecord(UUID uuid, String playerName, int itemCount, double amount, String materialName) {
        PlayerData data = loadPlayerData(uuid, playerName);
        data.addSellRecord(itemCount, amount, materialName);
        savePlayerData(data);

        // Also insert history row asynchronously
        List<SellEntry> history = data.getHistory();
        if (!history.isEmpty()) {
            SellEntry latest = history.get(history.size() - 1);
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                String sql = "INSERT INTO `" + historyTable + "` (`uuid`, `items_sold`, `money_earned`, `timestamp`, `material_name`) VALUES (?, ?, ?, ?, ?);";
                try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, uuid.toString());
                    ps.setInt(2, latest.getItemCount());
                    ps.setDouble(3, latest.getMoneyEarned());
                    ps.setLong(4, latest.getTimestamp());
                    if (latest.getMaterialName() != null) {
                        ps.setString(5, latest.getMaterialName());
                    } else {
                        ps.setNull(5, java.sql.Types.VARCHAR);
                    }
                    ps.executeUpdate();
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to insert history entry for " + playerName, e);
                }

                // Prune old entries beyond MAX_HISTORY
                String pruneSQL = "DELETE FROM `" + historyTable + "` WHERE `uuid` = ? AND `id` NOT IN " +
                        "(SELECT `id` FROM (SELECT `id` FROM `" + historyTable + "` WHERE `uuid` = ? " +
                        "ORDER BY `timestamp` DESC LIMIT " + PlayerData.MAX_HISTORY + ") AS t);";
                try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(pruneSQL)) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, uuid.toString());
                    ps.executeUpdate();
                } catch (SQLException e) {
                    // Non-critical - ignore pruning failures
                }
            });
        }
    }

    @Override
    public void unloadPlayerData(UUID uuid) {
        cache.remove(uuid);
    }

    @Override
    public void shutdown() {
        for (PlayerData data : cache.values()) writeToDatabase(data);
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
        cache.clear();
    }

    private void writeToDatabase(PlayerData data) {
        String sql = "INSERT INTO `" + tableName + "` (`uuid`, `player_name`, `total_items_sold`, `total_money_earned`) " +
                "VALUES (?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE `player_name` = VALUES(`player_name`), " +
                "`total_items_sold` = VALUES(`total_items_sold`), " +
                "`total_money_earned` = VALUES(`total_money_earned`);";

        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, data.getUuid().toString());
            ps.setString(2, data.getPlayerName());
            ps.setInt(3, data.getTotalItemsSold());
            ps.setDouble(4, data.getTotalMoneyEarned());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save player data to MySQL for " + data.getPlayerName(), e);
        }
    }
}
