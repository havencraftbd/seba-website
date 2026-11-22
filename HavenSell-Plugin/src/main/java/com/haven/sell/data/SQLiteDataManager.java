package com.haven.sell.data;

import com.haven.sell.HavenSell;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * SQLite-based data manager for player sell statistics and history.
 * All database operations run asynchronously to avoid blocking the main thread.
 * Uses two tables: one for stats, one for sell history entries.
 */
public class SQLiteDataManager implements DataManager {

    private final HavenSell plugin;
    private final String tableName = "player_stats";
    private final String historyTable = "player_history";
    private final Map<UUID, PlayerData> cache;
    private Connection connection;
    private final File dbFile;
    private final Object dbLock = new Object();

    public SQLiteDataManager(HavenSell plugin) throws SQLException {
        this.plugin = plugin;
        this.cache = new ConcurrentHashMap<>();
        this.dbFile = new File(plugin.getDataFolder(), "database.db");

        connect();
        createTables();
        migrateFromYaml(); // Automatically migrate old YAML data on first run
    }

    private void connect() throws SQLException {
        synchronized (dbLock) {
            if (connection != null && !connection.isClosed()) return;
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException e) {
                plugin.getLogger().log(Level.SEVERE, "SQLite JDBC Driver not found!", e);
            }
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        }
    }

    private Connection getConnection() throws SQLException {
        synchronized (dbLock) {
            if (connection == null || connection.isClosed()) connect();
            return connection;
        }
    }

    private void createTables() {
        String statsSQL = "CREATE TABLE IF NOT EXISTS `" + tableName + "` (" +
                "`uuid` VARCHAR(36) NOT NULL PRIMARY KEY, " +
                "`player_name` VARCHAR(16) NOT NULL, " +
                "`total_items_sold` INTEGER NOT NULL DEFAULT 0, " +
                "`total_money_earned` REAL NOT NULL DEFAULT 0.0" +
                ");";

        String historySQL = "CREATE TABLE IF NOT EXISTS `" + historyTable + "` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "`uuid` VARCHAR(36) NOT NULL, " +
                "`items_sold` INTEGER NOT NULL, " +
                "`money_earned` REAL NOT NULL, " +
                "`timestamp` INTEGER NOT NULL, " +
                "`material_name` VARCHAR(64)" +
                ");";

        String indexSQL = "CREATE INDEX IF NOT EXISTS `idx_uuid` ON `" + historyTable + "` (`uuid`);";

        try (Statement stmt = getConnection().createStatement()) {
            stmt.execute(statsSQL);
            stmt.execute(historySQL);
            stmt.execute(indexSQL);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create SQLite tables!", e);
        }
    }

    private void migrateFromYaml() {
        File dataDir = new File(plugin.getDataFolder(), "playerdata");
        if (!dataDir.exists() || !dataDir.isDirectory()) return;

        File[] files = dataDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) return;

        plugin.getLogger().info("==================================================");
        plugin.getLogger().info("Found " + files.length + " YAML data files. Starting migration to SQLite...");

        YamlDataManager yamlDataManager = new YamlDataManager(plugin);
        int migrated = 0;

        for (File file : files) {
            String playerName = file.getName().replace(".yml", "");
            PlayerData data = yamlDataManager.loadPlayerDataByName(playerName);
            if (data != null) {
                // Save to cache and SQLite
                cache.put(data.getUuid(), data);
                writeToDatabaseSync(data);
                
                // Insert history
                for (SellEntry entry : data.getHistory()) {
                     insertHistorySync(data.getUuid(), entry);
                }
                migrated++;
            }
        }
        
        // Rename the old folder to avoid migrating again
        File backupDir = new File(plugin.getDataFolder(), "playerdata_backup_migrated");
        dataDir.renameTo(backupDir);

        plugin.getLogger().info("Migration completed successfully! Migrated " + migrated + " players to SQLite.");
        plugin.getLogger().info("The old folder was renamed to 'playerdata_backup_migrated'.");
        plugin.getLogger().info("==================================================");
    }

    @Override
    public PlayerData loadPlayerData(UUID uuid, String playerName) {
        PlayerData cached = cache.get(uuid);
        if (cached != null) return cached;

        String sql = "SELECT * FROM `" + tableName + "` WHERE `uuid` = ?;";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
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
            plugin.getLogger().log(Level.WARNING, "Failed to load player data from SQLite for " + playerName, e);
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

        String sql = "SELECT * FROM `" + tableName + "` WHERE `player_name` = ? COLLATE NOCASE LIMIT 1;";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
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
            plugin.getLogger().log(Level.WARNING, "Failed to load player data by name from SQLite for " + playerName, e);
        }
        return null;
    }

    private List<SellEntry> loadHistory(UUID uuid) {
        List<SellEntry> entries = new ArrayList<>();
        String sql = "SELECT `items_sold`, `money_earned`, `timestamp`, `material_name` FROM `" + historyTable +
                "` WHERE `uuid` = ? ORDER BY `timestamp` ASC LIMIT " + PlayerData.MAX_HISTORY + ";";

        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String materialName = rs.getString("material_name");
                entries.add(new SellEntry(rs.getInt("items_sold"), rs.getDouble("money_earned"), rs.getLong("timestamp"), materialName));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load sell history from SQLite for " + uuid, e);
        }
        return entries;
    }

    @Override
    public void savePlayerData(PlayerData data) {
        cache.put(data.getUuid(), data);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> writeToDatabaseSync(data));
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
                insertHistorySync(uuid, latest);

                // Prune old entries beyond MAX_HISTORY
                synchronized (dbLock) {
                    String pruneSQL = "DELETE FROM `" + historyTable + "` WHERE `uuid` = ? AND `id` NOT IN " +
                            "(SELECT `id` FROM `" + historyTable + "` WHERE `uuid` = ? " +
                            "ORDER BY `timestamp` DESC LIMIT " + PlayerData.MAX_HISTORY + ");";
                    try (PreparedStatement ps = getConnection().prepareStatement(pruneSQL)) {
                        ps.setString(1, uuid.toString());
                        ps.setString(2, uuid.toString());
                        ps.executeUpdate();
                    } catch (SQLException e) {
                        // Non-critical - ignore pruning failures
                    }
                }
            });
        }
    }

    private void insertHistorySync(UUID uuid, SellEntry entry) {
        synchronized (dbLock) {
            String sql = "INSERT INTO `" + historyTable + "` (`uuid`, `items_sold`, `money_earned`, `timestamp`, `material_name`) VALUES (?, ?, ?, ?, ?);";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, entry.getItemCount());
                ps.setDouble(3, entry.getMoneyEarned());
                ps.setLong(4, entry.getTimestamp());
                if (entry.getMaterialName() != null) {
                    ps.setString(5, entry.getMaterialName());
                } else {
                    ps.setNull(5, java.sql.Types.VARCHAR);
                }
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to insert SQLite history entry for " + uuid, e);
            }
        }
    }

    @Override
    public void unloadPlayerData(UUID uuid) {
        cache.remove(uuid);
    }

    @Override
    public void shutdown() {
        for (PlayerData data : cache.values()) writeToDatabaseSync(data);
        synchronized (dbLock) {
            try {
                if (connection != null && !connection.isClosed()) connection.close();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to close SQLite connection.", e);
            }
        }
        cache.clear();
    }

    private void writeToDatabaseSync(PlayerData data) {
        synchronized (dbLock) {
            String sql = "INSERT INTO `" + tableName + "` (`uuid`, `player_name`, `total_items_sold`, `total_money_earned`) " +
                    "VALUES (?, ?, ?, ?) " +
                    "ON CONFLICT(`uuid`) DO UPDATE SET " +
                    "`player_name`=excluded.`player_name`, " +
                    "`total_items_sold`=excluded.`total_items_sold`, " +
                    "`total_money_earned`=excluded.`total_money_earned`;";

            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setString(1, data.getUuid().toString());
                ps.setString(2, data.getPlayerName());
                ps.setInt(3, data.getTotalItemsSold());
                ps.setDouble(4, data.getTotalMoneyEarned());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to save player data to SQLite for " + data.getPlayerName(), e);
            }
        }
    }
}
