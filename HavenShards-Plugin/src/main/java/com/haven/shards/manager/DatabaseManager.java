package com.haven.shards.manager;

import com.haven.shards.HavenShards;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import com.haven.shards.api.event.HavenShardsReloadEvent;
import com.haven.shards.util.ItemUtil;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

public class DatabaseManager implements Listener {

    public static class TopEntry {
        private final String name;
        private final double balance;

        public TopEntry(String name, double balance) {
            this.name = name;
            this.balance = balance;
        }

        public String getName() { return name; }
        public double getBalance() { return balance; }
    }

    private final HavenShards plugin;
    private HikariDataSource dataSource;
    private boolean isMySQL = false;
    private volatile List<TopEntry> cachedTopEntries = java.util.Collections.emptyList();
    private volatile double cachedTotalBalance = 0.0;
    private final Cache<UUID, Double> offlineBalanceCache = CacheBuilder.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .maximumSize(5000)
            .build();
    private final Map<UUID, List<ItemStack>> pendingRefundsCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicInteger activeAsyncQueries = new java.util.concurrent.atomic.AtomicInteger(0);
    private org.bukkit.scheduler.BukkitTask topCacheTask;

    public DatabaseManager(HavenShards plugin) {
        this.plugin = plugin;
        initDatabase();
        startTopCacheTicker();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private volatile boolean isShuttingDown = false;
    private final Map<String, java.util.concurrent.CompletableFuture<Void>> pendingMintFutures = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, String> playerNameCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Double> pendingBalanceSaves = new java.util.concurrent.ConcurrentHashMap<>();

    public void cacheOfflineBalance(UUID uuid, double balance) {
        if (uuid != null) {
            offlineBalanceCache.put(uuid, round(balance));
        }
    }

    public void cachePlayerName(UUID uuid, String name) {
        if (uuid != null && name != null && !name.isEmpty()) {
            playerNameCache.put(uuid, name);
        }
    }

    public void removeCachedPlayerName(UUID uuid) {
        if (uuid != null) {
            playerNameCache.remove(uuid);
        }
    }

    private double round(double val) {
        return Math.round(val * 100.0) / 100.0;
    }

    public boolean isMySQL() {
        return isMySQL;
    }

    public Connection getConnection() throws SQLException {
        if (isShuttingDown) {
            throw new SQLException("Database is shutting down; rejecting new connection requests.");
        }
        if (dataSource == null || dataSource.isClosed()) {
            initDatabase();
        }
        return dataSource.getConnection();
    }

    private synchronized void initDatabase() {
        if (dataSource != null && !dataSource.isClosed()) {
            return;
        }

        try {
            String dbType = plugin.getConfig().getString("database.type", "SQLITE").toUpperCase();
            this.isMySQL = dbType.equals("MYSQL");

            HikariConfig config = new HikariConfig();
            config.setPoolName("HavenShards-Pool");

            if (isMySQL) {
                String host = plugin.getConfig().getString("database.mysql.host", "localhost");
                int port = plugin.getConfig().getInt("database.mysql.port", 3306);
                String database = plugin.getConfig().getString("database.mysql.database", "havenshards");
                String username = plugin.getConfig().getString("database.mysql.username", "root");
                String password = plugin.getConfig().getString("database.mysql.password", "");
                boolean ssl = plugin.getConfig().getBoolean("database.mysql.ssl", false);

                config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=" + ssl + "&autoReconnect=true&characterEncoding=utf8");
                config.setUsername(username);
                config.setPassword(password);
                config.setMaximumPoolSize(10);
                config.setMinimumIdle(2);
                config.setIdleTimeout(30000);
                config.setConnectionTimeout(5000);
                config.addDataSourceProperty("cachePrepStmts", "true");
                config.addDataSourceProperty("prepStmtCacheSize", "250");
                config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            } else {
                File dataFolder = plugin.getDataFolder();
                if (!dataFolder.exists()) {
                    dataFolder.mkdirs();
                }

                File dbFile = new File(dataFolder, "shards.db");
                config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
                config.setDriverClassName("org.sqlite.JDBC");
                config.setMaximumPoolSize(3);
                config.setConnectionTimeout(3000);
                config.setConnectionInitSql("PRAGMA journal_mode=WAL;");
                config.addDataSourceProperty("synchronous", "NORMAL");
                config.addDataSourceProperty("busy_timeout", "5000");
            }

            this.dataSource = new HikariDataSource(config);
            plugin.getLogger().info("[HavenShards] Connected to " + (isMySQL ? "MySQL" : "SQLite") + " Database Pool successfully.");
            createTables();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[HavenShards] Failed to initialize " + (isMySQL ? "MySQL" : "SQLite") + " database pool", e);
        }
    }

    private void createTables() throws SQLException {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            if (!isMySQL) {
                try {
                    stmt.execute("PRAGMA journal_mode=WAL;");
                    stmt.execute("PRAGMA synchronous=NORMAL;");
                    stmt.execute("PRAGMA busy_timeout=10000;");
                } catch (SQLException ignored) {}
            }
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS haven_shards (" +
                            "uuid VARCHAR(36) PRIMARY KEY, " +
                            "player_name VARCHAR(32), " +
                            "balance DOUBLE NOT NULL DEFAULT 0.0" +
                            ");"
            );
            try {
                stmt.executeUpdate("ALTER TABLE haven_shards ADD COLUMN player_name VARCHAR(32);");
            } catch (SQLException ignored) {
                // Column likely exists
            }
            try {
                stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_haven_shards_balance ON haven_shards(balance DESC);");
            } catch (SQLException e) {
                try {
                    stmt.executeUpdate("CREATE INDEX idx_haven_shards_balance ON haven_shards(balance DESC);");
                } catch (SQLException ignored) {
                    // Index already exists
                }
            }
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS haven_claimed_items (" +
                            "item_uuid VARCHAR(36) PRIMARY KEY, " +
                            "claimed_by VARCHAR(36) NOT NULL, " +
                            "amount DOUBLE NOT NULL, " +
                            "claimed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                            ");"
            );
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS haven_minted_items (" +
                            "item_uuid VARCHAR(36) PRIMARY KEY, " +
                            "minter_uuid VARCHAR(36) NOT NULL, " +
                            "amount DOUBLE NOT NULL, " +
                            "minted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                            "is_claimed BOOLEAN NOT NULL DEFAULT 0, " +
                            "claimed_by VARCHAR(36), " +
                            "claimed_at TIMESTAMP" +
                            ");"
            );

            String intType = isMySQL ? "INT" : "INTEGER";
            String autoInc = isMySQL ? "AUTO_INCREMENT" : "AUTOINCREMENT";
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS haven_transactions (" +
                            "id " + intType + " PRIMARY KEY " + autoInc + ", " +
                            "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                            "sender_uuid VARCHAR(36), " +
                            "sender_name VARCHAR(64), " +
                            "receiver_uuid VARCHAR(36), " +
                            "receiver_name VARCHAR(64), " +
                            "transaction_type VARCHAR(32) NOT NULL, " +
                            "amount DOUBLE NOT NULL, " +
                            "tax_amount DOUBLE DEFAULT 0.0, " +
                            "sender_bal_after DOUBLE, " +
                            "receiver_bal_after DOUBLE" +
                            ");"
            );
            try {
                stmt.executeUpdate("ALTER TABLE haven_transactions ADD COLUMN tax_amount DOUBLE DEFAULT 0.0;");
            } catch (SQLException ignored) {
                // Column likely exists
            }
            try {
                stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_haven_tx_sender ON haven_transactions(sender_uuid, id);");
                stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_haven_tx_receiver ON haven_transactions(receiver_uuid, id);");
            } catch (SQLException e) {
                try {
                    stmt.executeUpdate("CREATE INDEX idx_haven_tx_sender ON haven_transactions(sender_uuid, id);");
                    stmt.executeUpdate("CREATE INDEX idx_haven_tx_receiver ON haven_transactions(receiver_uuid, id);");
                } catch (SQLException ignored) {
                    // Index already exists
                }
            }
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS haven_daily_rewards (" +
                            "uuid VARCHAR(36) PRIMARY KEY, " +
                            "last_claim_millis BIGINT NOT NULL DEFAULT 0, " +
                            "streak INTEGER NOT NULL DEFAULT 0" +
                            ");"
            );
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS haven_quests (" +
                            "uuid VARCHAR(36) NOT NULL, " +
                            "quest_id VARCHAR(64) NOT NULL, " +
                            "progress INT NOT NULL DEFAULT 0, " +
                            "claimed BOOLEAN NOT NULL DEFAULT 0, " +
                            "last_reset BIGINT NOT NULL DEFAULT 0, " +
                            "PRIMARY KEY (uuid, quest_id)" +
                            ");"
            );
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS haven_claimed_advancements (" +
                            "uuid VARCHAR(36) NOT NULL, " +
                            "advancement_key VARCHAR(128) NOT NULL, " +
                            "claimed_at BIGINT NOT NULL, " +
                            "PRIMARY KEY (uuid, advancement_key)" +
                            ");"
            );
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS haven_daily_pay_limits (" +
                            "uuid VARCHAR(36) PRIMARY KEY, " +
                            "day_key VARCHAR(16) NOT NULL, " +
                            "total_sent DOUBLE NOT NULL DEFAULT 0.0, " +
                            "transaction_count INT NOT NULL DEFAULT 0" +
                            ");"
            );
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS haven_daily_withdraw_limits (" +
                            "uuid VARCHAR(36) PRIMARY KEY, " +
                            "day_key VARCHAR(16) NOT NULL, " +
                            "total_withdrawn DOUBLE NOT NULL DEFAULT 0.0, " +
                            "withdraw_count INT NOT NULL DEFAULT 0" +
                            ");"
            );
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS haven_pending_refunds (" +
                            "id " + intType + " PRIMARY KEY " + autoInc + ", " +
                            "uuid VARCHAR(36) NOT NULL, " +
                            "item_data TEXT NOT NULL, " +
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                            ");"
            );
            try {
                stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_haven_refunds_uuid ON haven_pending_refunds(uuid);");
            } catch (SQLException ignored) {
            }
        }
    }

    public void startTopCacheTicker() {
        if (topCacheTask != null) {
            topCacheTask.cancel();
        }
        long intervalSeconds = plugin.getConfig().getLong("leaderboard.refresh-interval-seconds", 120L);
        long intervalTicks = Math.max(1200L, intervalSeconds * 20L); // Default 2 minutes (2400 ticks), minimum 60 seconds (1200 ticks)
        this.topCacheTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::refreshTopCache, 60L, intervalTicks);
    }

    @EventHandler
    public void onReload(HavenShardsReloadEvent event) {
        startTopCacheTicker();
    }

    public void executeAsync(Runnable runnable) {
        if (dataSource == null || dataSource.isClosed()) {
            return;
        }
        activeAsyncQueries.incrementAndGet();
        try {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    runnable.run();
                } finally {
                    activeAsyncQueries.decrementAndGet();
                }
            });
        } catch (Throwable t) {
            activeAsyncQueries.decrementAndGet();
            throw t;
        }
    }

    public void refreshTopCache() {
        int limit = plugin.getConfig().getInt("leaderboard.cache-limit", 500);
        List<TopEntry> newTop = new ArrayList<>();
        String query = "SELECT uuid, player_name, balance FROM haven_shards ORDER BY balance DESC LIMIT ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, limit);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("player_name");
                    double bal = round(rs.getDouble("balance"));
                    if (name == null || name.isEmpty()) {
                        UUID uuid = UUID.fromString(rs.getString("uuid"));
                        name = playerNameCache.get(uuid);
                        if (name == null) {
                            org.bukkit.OfflinePlayer off = Bukkit.getOfflinePlayer(uuid);
                            name = off.getName();
                        }
                        if (name != null && !name.isEmpty()) {
                            updatePlayerNameAsync(uuid, name);
                        }
                    }
                    if (name == null || name.isEmpty()) name = "Unknown";
                    newTop.add(new TopEntry(name, bal));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to refresh top cache", e);
        }

        double total = 0.0;
        String sumQuery = "SELECT SUM(balance) as total_balance FROM haven_shards";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sumQuery);
             ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) {
                total = round(rs.getDouble("total_balance"));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to fetch total balance for cache", e);
        }

        this.cachedTopEntries = java.util.Collections.unmodifiableList(newTop);
        this.cachedTotalBalance = total;
    }

    public List<TopEntry> getCachedTopEntries() {
        return cachedTopEntries;
    }

    public double getCachedTotalBalance() {
        return cachedTotalBalance;
    }

    public void loadPlayerBalanceAsync(UUID uuid, java.util.function.Consumer<Double> callback) {
        if (uuid == null) {
            if (callback != null) callback.accept(0.0);
            return;
        }
        Double pending = pendingBalanceSaves.get(uuid);
        if (pending != null) {
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(pending));
            return;
        }

        executeAsync(() -> {
            Double pendingAsync = pendingBalanceSaves.get(uuid);
            if (pendingAsync != null) {
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(pendingAsync));
                return;
            }
            double balance = 0.0;
            String query = "SELECT balance FROM haven_shards WHERE uuid = ?";
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, uuid.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        balance = round(rs.getDouble("balance"));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load balance for " + uuid, e);
            }
            final double finalBal = balance;
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(finalBal));
        });
    }

    public double getOfflineBalanceFast(UUID uuid) {
        if (uuid == null) return 0.0;
        if (plugin != null && plugin.getShardManager() != null && plugin.getShardManager().isLoaded(uuid)) {
            return plugin.getShardManager().getBalance(uuid);
        }
        Double cached = offlineBalanceCache.getIfPresent(uuid);
        if (cached != null) return cached;
        // Fast non-blocking load: return 0.0 immediately and populate cache in background
        loadPlayerBalanceAsync(uuid, null);
        return 0.0;
    }

    public UUID getPlayerUuidByName(String name) {
        if (name == null || name.isEmpty()) return null;
        for (Map.Entry<UUID, String> entry : playerNameCache.entrySet()) {
            if (name.equalsIgnoreCase(entry.getValue())) {
                return entry.getKey();
            }
        }
        String query = "SELECT uuid FROM haven_shards WHERE LOWER(player_name) = ? LIMIT 1;";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, name.toLowerCase(java.util.Locale.ROOT));
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    UUID u = UUID.fromString(rs.getString("uuid"));
                    playerNameCache.put(u, name);
                    return u;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to resolve player UUID by name: " + name, e);
        }
        return null;
    }

    public double getOfflineBalanceSync(UUID uuid) {
        if (uuid == null) return 0.0;
        if (plugin != null && plugin.getShardManager() != null && plugin.getShardManager().isLoaded(uuid)) {
            return plugin.getShardManager().getBalance(uuid);
        }
        Double cached = offlineBalanceCache.getIfPresent(uuid);
        if (cached != null) {
            return cached;
        }
        String query = "SELECT balance FROM haven_shards WHERE uuid = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, uuid.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    double bal = round(rs.getDouble("balance"));
                    offlineBalanceCache.put(uuid, bal);
                    return bal;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to fetch sync balance for offline player " + uuid, e);
        }
        offlineBalanceCache.put(uuid, 0.0);
        return 0.0;
    }

    public com.haven.shards.model.WithdrawalResult withdrawOfflineSync(UUID uuid, double amount) {
        amount = round(amount);
        if (amount < 0 || uuid == null) return new com.haven.shards.model.WithdrawalResult(false, getOfflineBalanceSync(uuid));
        
        Double currentCached = offlineBalanceCache.getIfPresent(uuid);
        if (currentCached != null && currentCached < amount) {
            return new com.haven.shards.model.WithdrawalResult(false, currentCached);
        }

        String updateQuery = "UPDATE haven_shards SET balance = ROUND(balance - ?, 2) WHERE uuid = ? AND ROUND(balance, 2) >= ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(updateQuery)) {
            pstmt.setDouble(1, amount);
            pstmt.setString(2, uuid.toString());
            pstmt.setDouble(3, amount);
            int rows = pstmt.executeUpdate();
            
            if (rows > 0) {
                if (currentCached != null) {
                    double newBal = round(currentCached - amount);
                    offlineBalanceCache.put(uuid, newBal);
                    return new com.haven.shards.model.WithdrawalResult(true, newBal);
                }
            }
            offlineBalanceCache.invalidate(uuid);
            double newBalance = getOfflineBalanceSync(uuid);
            return new com.haven.shards.model.WithdrawalResult(rows > 0, newBalance);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to withdraw offline sync for " + uuid, e);
            return new com.haven.shards.model.WithdrawalResult(false, getOfflineBalanceSync(uuid));
        }
    }

    public double depositOfflineSync(UUID uuid, double amount) {
        amount = round(amount);
        if (uuid == null || amount <= 0) return getOfflineBalanceSync(uuid);
        
        Double currentCached = offlineBalanceCache.getIfPresent(uuid);
        double maxBal = plugin.getConfig().getDouble("max-balance", 1000000000.0);

        String query;
        if (isMySQL) {
            query = "INSERT INTO haven_shards (uuid, balance) VALUES (?, LEAST(?, ?)) " +
                    "ON DUPLICATE KEY UPDATE balance = LEAST(?, ROUND(balance + VALUES(balance), 2));";
        } else {
            query = "INSERT INTO haven_shards (uuid, balance) VALUES (?, MIN(?, ?)) " +
                    "ON CONFLICT(uuid) DO UPDATE SET balance = MIN(?, ROUND(balance + excluded.balance, 2));";
        }
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, uuid.toString());
            pstmt.setDouble(2, maxBal);
            pstmt.setDouble(3, amount);
            pstmt.setDouble(4, maxBal);
            pstmt.executeUpdate();
            if (currentCached != null) {
                double newBal = Math.min(round(currentCached + amount), maxBal);
                offlineBalanceCache.put(uuid, newBal);
                return newBal;
            }
            offlineBalanceCache.invalidate(uuid);
            return getOfflineBalanceSync(uuid);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to deposit offline sync for " + uuid, e);
            return getOfflineBalanceSync(uuid);
        }
    }

    public void savePlayerBalanceAsync(UUID uuid, double balance) {
        if (uuid == null) return;
        final double finalBalance = round(balance);
        offlineBalanceCache.put(uuid, finalBalance);
        pendingBalanceSaves.put(uuid, finalBalance);
        org.bukkit.entity.Player onlinePlayer = Bukkit.getPlayer(uuid);
        String name = onlinePlayer != null ? onlinePlayer.getName() : playerNameCache.get(uuid);
        executeAsync(() -> {
            try {
                String query;
                if (name != null) {
                    if (isMySQL) {
                        query = "INSERT INTO haven_shards (uuid, player_name, balance) VALUES (?, ?, ?) " +
                                "ON DUPLICATE KEY UPDATE player_name = VALUES(player_name), balance = VALUES(balance);";
                    } else {
                        query = "INSERT INTO haven_shards (uuid, player_name, balance) VALUES (?, ?, ?) " +
                                "ON CONFLICT(uuid) DO UPDATE SET player_name = excluded.player_name, balance = excluded.balance;";
                    }
                } else {
                    if (isMySQL) {
                        query = "INSERT INTO haven_shards (uuid, balance) VALUES (?, ?) " +
                                "ON DUPLICATE KEY UPDATE balance = VALUES(balance);";
                    } else {
                        query = "INSERT INTO haven_shards (uuid, balance) VALUES (?, ?) " +
                                "ON CONFLICT(uuid) DO UPDATE SET balance = excluded.balance;";
                    }
                }

                try (Connection conn = getConnection();
                     PreparedStatement pstmt = conn.prepareStatement(query)) {
                    pstmt.setString(1, uuid.toString());
                    if (name != null) {
                        pstmt.setString(2, name);
                        pstmt.setDouble(3, finalBalance);
                    } else {
                        pstmt.setDouble(2, finalBalance);
                    }
                    pstmt.executeUpdate();
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to save balance for " + uuid, e);
                }
            } finally {
                pendingBalanceSaves.remove(uuid, finalBalance);
            }
        });
    }

    public void savePlayerBalanceSync(UUID uuid, double balance) {
        if (uuid == null) return;
        final double finalBalance = round(balance);
        offlineBalanceCache.put(uuid, finalBalance);
        pendingBalanceSaves.remove(uuid);
        org.bukkit.entity.Player onlinePlayer = Bukkit.getPlayer(uuid);
        String name = onlinePlayer != null ? onlinePlayer.getName() : playerNameCache.get(uuid);
        String query;
        if (name != null) {
            if (isMySQL) {
                query = "INSERT INTO haven_shards (uuid, player_name, balance) VALUES (?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE player_name = VALUES(player_name), balance = VALUES(balance);";
            } else {
                query = "INSERT INTO haven_shards (uuid, player_name, balance) VALUES (?, ?, ?) " +
                        "ON CONFLICT(uuid) DO UPDATE SET player_name = excluded.player_name, balance = excluded.balance;";
            }
        } else {
            if (isMySQL) {
                query = "INSERT INTO haven_shards (uuid, balance) VALUES (?, ?) " +
                        "ON DUPLICATE KEY UPDATE balance = VALUES(balance);";
            } else {
                query = "INSERT INTO haven_shards (uuid, balance) VALUES (?, ?) " +
                        "ON CONFLICT(uuid) DO UPDATE SET balance = excluded.balance;";
            }
        }
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, uuid.toString());
            if (name != null) {
                pstmt.setString(2, name);
                pstmt.setDouble(3, finalBalance);
            } else {
                pstmt.setDouble(2, finalBalance);
            }
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to sync save balance for " + uuid, e);
        }
    }

    public void savePlayerBalancesBatchSync(Map<UUID, Double> batch) {
        if (batch == null || batch.isEmpty()) return;

        // Clean up pending balance saves and update offline balance cache
        for (Map.Entry<UUID, Double> entry : batch.entrySet()) {
            UUID uuid = entry.getKey();
            Double balance = entry.getValue();
            if (uuid != null && balance != null) {
                offlineBalanceCache.put(uuid, round(balance));
                pendingBalanceSaves.remove(uuid);
            }
        }

        String query;
        if (isMySQL) {
            query = "INSERT INTO haven_shards (uuid, player_name, balance) VALUES (?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE player_name = COALESCE(VALUES(player_name), haven_shards.player_name), balance = VALUES(balance);";
        } else {
            query = "INSERT INTO haven_shards (uuid, player_name, balance) VALUES (?, ?, ?) " +
                    "ON CONFLICT(uuid) DO UPDATE SET player_name = COALESCE(excluded.player_name, haven_shards.player_name), balance = excluded.balance;";
        }

        try (Connection conn = getConnection()) {
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                for (Map.Entry<UUID, Double> entry : batch.entrySet()) {
                    UUID uuid = entry.getKey();
                    Double balance = entry.getValue();
                    if (uuid == null || balance == null) continue;

                    double finalBalance = round(balance);
                    Player onlinePlayer = Bukkit.getPlayer(uuid);
                    String name = onlinePlayer != null ? onlinePlayer.getName() : playerNameCache.get(uuid);

                    pstmt.setString(1, uuid.toString());
                    pstmt.setString(2, name);
                    pstmt.setDouble(3, finalBalance);
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to batch save player balances (" + batch.size() + " entries)", e);
        }
    }

    public void savePlayerBalancesBatchAsync(Map<UUID, Double> batch) {
        if (batch == null || batch.isEmpty()) return;
        executeAsync(() -> savePlayerBalancesBatchSync(batch));
    }

    public void updatePlayerNameAsync(UUID uuid, String name) {
        if (name == null || name.isEmpty()) return;
        executeAsync(() -> {
            String query;
            if (isMySQL) {
                query = "INSERT INTO haven_shards (uuid, player_name, balance) VALUES (?, ?, 0.0) " +
                        "ON DUPLICATE KEY UPDATE player_name = VALUES(player_name);";
            } else {
                query = "INSERT INTO haven_shards (uuid, player_name, balance) VALUES (?, ?, 0.0) " +
                        "ON CONFLICT(uuid) DO UPDATE SET player_name = excluded.player_name;";
            }
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, uuid.toString());
                pstmt.setString(2, name);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to update player name for " + uuid, e);
            }
        });
    }

    public void getServerTotalBalanceAsync(java.util.function.Consumer<Double> callback) {
        executeAsync(() -> {
            double total = 0.0;
            String query = "SELECT SUM(balance) as total_balance FROM haven_shards";
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(query);
                 ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    total = round(rs.getDouble("total_balance"));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to get server total balance", e);
            }
            final double finalTotal = total;
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(finalTotal));
        });
    }

    public void loadTopEntriesAsync(int limit, java.util.function.Consumer<List<TopEntry>> callback) {
        executeAsync(() -> {
            List<TopEntry> list = new ArrayList<>();
            String query = "SELECT uuid, player_name, balance FROM haven_shards ORDER BY balance DESC LIMIT ?";
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, limit);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        String name = rs.getString("player_name");
                        double bal = round(rs.getDouble("balance"));
                        if (name == null || name.isEmpty()) {
                            UUID uuid = UUID.fromString(rs.getString("uuid"));
                            name = playerNameCache.get(uuid);
                            if (name == null) {
                                org.bukkit.OfflinePlayer off = Bukkit.getOfflinePlayer(uuid);
                                name = off.getName();
                            }
                            if (name != null && !name.isEmpty()) {
                                updatePlayerNameAsync(uuid, name);
                            }
                        }
                        if (name == null || name.isEmpty()) name = "Unknown";
                        list.add(new TopEntry(name, bal));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load top entries", e);
            }
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(list));
        });
    }

    public void loadPlayerQuestsAsync(UUID uuid, java.util.function.Consumer<Map<String, com.haven.shards.model.QuestData>> callback) {
        executeAsync(() -> {
            Map<String, com.haven.shards.model.QuestData> map = new HashMap<>();
            String query = "SELECT quest_id, progress, claimed, last_reset FROM haven_quests WHERE uuid = ?";
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, uuid.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        String qId = rs.getString("quest_id");
                        int prog = rs.getInt("progress");
                        boolean claimed = rs.getBoolean("claimed");
                        long lastReset = rs.getLong("last_reset");
                        map.put(qId, new com.haven.shards.model.QuestData(uuid, qId, prog, claimed, lastReset));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load player quests for " + uuid, e);
            }
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(map));
        });
    }

    public void saveQuestDataAsync(com.haven.shards.model.QuestData data) {
        executeAsync(() -> {
            saveQuestDataSync(data);
        });
    }

    public void saveQuestDataSync(com.haven.shards.model.QuestData data) {
        String query;
        if (isMySQL) {
            query = "INSERT INTO haven_quests (uuid, quest_id, progress, claimed, last_reset) VALUES (?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE progress = VALUES(progress), claimed = VALUES(claimed), last_reset = VALUES(last_reset);";
        } else {
            query = "INSERT OR REPLACE INTO haven_quests (uuid, quest_id, progress, claimed, last_reset) VALUES (?, ?, ?, ?, ?);";
        }
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, data.getPlayerUuid().toString());
            pstmt.setString(2, data.getQuestId());
            pstmt.setInt(3, data.getProgress());
            pstmt.setBoolean(4, data.isClaimed());
            pstmt.setLong(5, data.getLastResetMillis());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to sync save quest data for " + data.getPlayerUuid(), e);
        }
    }

    public void saveQuestDataBatchAsync(List<com.haven.shards.model.QuestData> list) {
        if (list == null || list.isEmpty()) return;
        executeAsync(() -> saveQuestDataBatchSync(list));
    }

    public void saveQuestDataBatchSync(List<com.haven.shards.model.QuestData> list) {
        if (list == null || list.isEmpty()) return;
        String query;
        if (isMySQL) {
            query = "INSERT INTO haven_quests (uuid, quest_id, progress, claimed, last_reset) VALUES (?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE progress = VALUES(progress), claimed = VALUES(claimed), last_reset = VALUES(last_reset);";
        } else {
            query = "INSERT OR REPLACE INTO haven_quests (uuid, quest_id, progress, claimed, last_reset) VALUES (?, ?, ?, ?, ?);";
        }
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            for (com.haven.shards.model.QuestData data : list) {
                pstmt.setString(1, data.getPlayerUuid().toString());
                pstmt.setString(2, data.getQuestId());
                pstmt.setInt(3, data.getProgress());
                pstmt.setBoolean(4, data.isClaimed());
                pstmt.setLong(5, data.getLastResetMillis());
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to batch save quest data", e);
        }
    }

    public void modifyOfflineBalanceAsync(UUID uuid, double delta, java.util.function.Consumer<Boolean> callback) {
        final double finalDelta = round(delta);
        executeAsync(() -> {
            boolean success = false;
            try (Connection conn = getConnection()) {
                if (finalDelta >= 0) {
                    double maxBal = plugin.getConfig().getDouble("max-balance", 1000000000.0);
                    String query;
                    if (isMySQL) {
                        query = "INSERT INTO haven_shards (uuid, balance) VALUES (?, LEAST(?, ?)) ON DUPLICATE KEY UPDATE balance = LEAST(?, ROUND(balance + VALUES(balance), 2));";
                    } else {
                        query = "INSERT INTO haven_shards (uuid, balance) VALUES (?, MIN(?, ?)) ON CONFLICT(uuid) DO UPDATE SET balance = MIN(?, ROUND(balance + excluded.balance, 2));";
                    }
                    try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                        pstmt.setString(1, uuid.toString());
                        pstmt.setDouble(2, maxBal);
                        pstmt.setDouble(3, finalDelta);
                        pstmt.setDouble(4, maxBal);
                        pstmt.executeUpdate();
                        success = true;
                    }
                } else {
                    String updateQuery = "UPDATE haven_shards SET balance = ROUND(balance + ?, 2) WHERE uuid = ? AND ROUND(balance + ?, 2) >= 0";
                    try (PreparedStatement pstmt = conn.prepareStatement(updateQuery)) {
                        pstmt.setDouble(1, finalDelta);
                        pstmt.setString(2, uuid.toString());
                        pstmt.setDouble(3, finalDelta);
                        int affected = pstmt.executeUpdate();
                        if (affected > 0) {
                            success = true;
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to modify offline balance for " + uuid, e);
            }
            if (success) {
                offlineBalanceCache.invalidate(uuid);
            }
            final boolean finalSuccess = success;
            if (callback != null) {
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(finalSuccess));
            }
        });
    }

    public void mintItemUuidSync(String itemUuidStr, UUID playerUuid, double amount) {
        if (itemUuidStr == null || itemUuidStr.isEmpty()) return;
        String query = "INSERT INTO haven_minted_items (item_uuid, minter_uuid, amount, is_claimed) VALUES (?, ?, ?, 0);";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, itemUuidStr);
            pstmt.setString(2, playerUuid.toString());
            pstmt.setDouble(3, amount);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to record minted shard item UUID sync", e);
        }
    }

    public void mintItemUuidAsync(String itemUuidStr, UUID playerUuid, double amount) {
        if (itemUuidStr == null || itemUuidStr.isEmpty()) return;
        java.util.concurrent.CompletableFuture<Void> future = new java.util.concurrent.CompletableFuture<>();
        pendingMintFutures.put(itemUuidStr, future);
        executeAsync(() -> {
            try {
                mintItemUuidSync(itemUuidStr, playerUuid, amount);
            } finally {
                pendingMintFutures.remove(itemUuidStr);
                future.complete(null);
            }
        });
    }

    public void claimItemUuidAsync(String itemUuidStr, UUID playerUuid, double amount, java.util.function.Consumer<Integer> callback) {
        if (itemUuidStr == null || itemUuidStr.isEmpty()) {
            if (callback != null) {
                callback.accept(0); // Error
            }
            return;
        }

        java.util.concurrent.CompletableFuture<Void> pendingMint = pendingMintFutures.get(itemUuidStr);
        if (pendingMint != null && !pendingMint.isDone()) {
            pendingMint.whenComplete((v, ex) -> {
                executeAsync(() -> processClaimItem(itemUuidStr, playerUuid, amount, callback));
            });
            return;
        }

        executeAsync(() -> processClaimItem(itemUuidStr, playerUuid, amount, callback));
    }

    private void processClaimItem(String itemUuidStr, UUID playerUuid, double amount, java.util.function.Consumer<Integer> callback) {
        int result = 0; // Default to error
        try (Connection conn = getConnection()) {
            // 1. Try atomic claim update on minted items table
            String updateQuery = "UPDATE haven_minted_items SET is_claimed = 1, claimed_by = ?, claimed_at = CURRENT_TIMESTAMP " +
                    "WHERE item_uuid = ? AND is_claimed = 0;";
            try (PreparedStatement pstmt = conn.prepareStatement(updateQuery)) {
                pstmt.setString(1, playerUuid.toString());
                pstmt.setString(2, itemUuidStr);
                int rows = pstmt.executeUpdate();
                if (rows > 0) {
                    result = 1; // Success
                }
            }

            // 2. Fallback check for legacy items or legacy claim table
            if (result == 0) {
                // Check if already in haven_minted_items as claimed
                String checkQuery = "SELECT is_claimed FROM haven_minted_items WHERE item_uuid = ?;";
                boolean existsInMinted = false;
                try (PreparedStatement checkStmt = conn.prepareStatement(checkQuery)) {
                    checkStmt.setString(1, itemUuidStr);
                    try (ResultSet rs = checkStmt.executeQuery()) {
                        if (rs.next()) {
                            existsInMinted = true; // It's in minted table and was already claimed
                        }
                    }
                }

                if (!existsInMinted) {
                    // If it is STILL in pendingMintFutures, do not allow legacy fallback (prevent race condition dupe)
                    if (pendingMintFutures.containsKey(itemUuidStr)) {
                        result = 0; // Still minting in background, reject claim for retry
                    } else {
                        // Legacy fallback using haven_claimed_items table
                        String legacyQuery = "INSERT INTO haven_claimed_items (item_uuid, claimed_by, amount) VALUES (?, ?, ?);";
                        try (PreparedStatement legacyStmt = conn.prepareStatement(legacyQuery)) {
                            legacyStmt.setString(1, itemUuidStr);
                            legacyStmt.setString(2, playerUuid.toString());
                            legacyStmt.setDouble(3, amount);
                            legacyStmt.executeUpdate();
                            result = 1; // Success
                        } catch (SQLException ignored) {
                            result = 2; // Duplicate / Already claimed in legacy table
                        }
                    }
                } else {
                    result = 2; // Duplicate in minted table
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error claiming shard item UUID: " + itemUuidStr, e);
            result = 0; // Error
        }

        if (result == 2) {
            plugin.getLogger().warning("[Anti-Dupe Alert] Blocked duplicate/fraudulent item claim for UUID: " + itemUuidStr + " by player: " + playerUuid);
        }

        final int finalResult = result;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (callback != null) {
                callback.accept(finalResult);
            }
        });
    }

    public void logTransactionAsync(UUID senderUuid, String senderName, UUID receiverUuid, String receiverName,
                                    String type, double amount, double senderBalAfter, double receiverBalAfter) {
        logTransactionAsync(senderUuid, senderName, receiverUuid, receiverName, type, amount, 0.0, senderBalAfter, receiverBalAfter);
    }

    public void logTransactionAsync(UUID senderUuid, String senderName, UUID receiverUuid, String receiverName,
                                    String type, double amount, double taxAmount, double senderBalAfter, double receiverBalAfter) {
        executeAsync(() -> {
            try (Connection conn = getConnection()) {
                String query = "INSERT INTO haven_transactions (sender_uuid, sender_name, receiver_uuid, receiver_name, " +
                        "transaction_type, amount, tax_amount, sender_bal_after, receiver_bal_after) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);";
                try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                    pstmt.setString(1, senderUuid != null ? senderUuid.toString() : null);
                    pstmt.setString(2, senderName);
                    pstmt.setString(3, receiverUuid != null ? receiverUuid.toString() : null);
                    pstmt.setString(4, receiverName);
                    pstmt.setString(5, type);
                    pstmt.setDouble(6, amount);
                    pstmt.setDouble(7, taxAmount);
                    pstmt.setDouble(8, senderBalAfter);
                    pstmt.setDouble(9, receiverBalAfter);
                    pstmt.executeUpdate();
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to log transaction", e);
                }

                int maxSaved = plugin.getConfig().getInt("security.max-saved-transactions-per-player", 200);
                if (maxSaved > 0) {
                    if (senderUuid != null) {
                        prunePlayerTransactions(conn, senderUuid.toString(), maxSaved);
                    }
                    if (receiverUuid != null) {
                        prunePlayerTransactions(conn, receiverUuid.toString(), maxSaved);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to get database connection for transaction logging", e);
            }
        });
    }

    private void prunePlayerTransactions(Connection conn, String uuidStr, int maxSaved) {
        if (uuidStr == null || uuidStr.isEmpty() || maxSaved <= 0) return;
        // 1. Find the ID of the maxSaved-th transaction. If not found, player has <= maxSaved rows -> zero deletes needed!
        String findThresholdQuery = "SELECT id FROM haven_transactions WHERE (sender_uuid = ? OR receiver_uuid = ?) ORDER BY id DESC LIMIT 1 OFFSET ?;";
        long thresholdId = -1;
        try (PreparedStatement findStmt = conn.prepareStatement(findThresholdQuery)) {
            findStmt.setString(1, uuidStr);
            findStmt.setString(2, uuidStr);
            findStmt.setInt(3, maxSaved);
            try (ResultSet rs = findStmt.executeQuery()) {
                if (rs.next()) {
                    thresholdId = rs.getLong("id");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.FINE, "Failed to find transaction prune threshold for " + uuidStr, e);
            return;
        }

        // If player has fewer transactions than maxSaved, do nothing!
        if (thresholdId <= 0) {
            return;
        }

        // 2. Delete all older transactions below the threshold in a single fast, indexed range delete
        String deleteQuery = "DELETE FROM haven_transactions WHERE (sender_uuid = ? OR receiver_uuid = ?) AND id <= ?;";
        try (PreparedStatement delStmt = conn.prepareStatement(deleteQuery)) {
            delStmt.setString(1, uuidStr);
            delStmt.setString(2, uuidStr);
            delStmt.setLong(3, thresholdId);
            delStmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.FINE, "Failed to prune transactions for " + uuidStr, e);
        }
    }

    public void getTransactionsAsync(UUID playerUuid, int limit, java.util.function.Consumer<List<com.haven.shards.model.TransactionEntry>> callback) {
        executeAsync(() -> {
            List<com.haven.shards.model.TransactionEntry> list = new ArrayList<>();
            String query = "SELECT id, timestamp, sender_name, receiver_name, transaction_type, amount, " +
                    "tax_amount, sender_bal_after, receiver_bal_after FROM haven_transactions " +
                    "WHERE sender_uuid = ? OR receiver_uuid = ? ORDER BY id DESC LIMIT ?;";
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(query)) {
                String uuidStr = playerUuid != null ? playerUuid.toString() : "";
                pstmt.setString(1, uuidStr);
                pstmt.setString(2, uuidStr);
                pstmt.setInt(3, limit);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        list.add(new com.haven.shards.model.TransactionEntry(
                                rs.getInt("id"),
                                rs.getString("timestamp"),
                                rs.getString("sender_name"),
                                rs.getString("receiver_name"),
                                rs.getString("transaction_type"),
                                rs.getDouble("amount"),
                                rs.getDouble("tax_amount"),
                                rs.getDouble("sender_bal_after"),
                                rs.getDouble("receiver_bal_after")
                        ));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to fetch transactions", e);
            }
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(list));
        });
    }

    public void loadDailyRewardAsync(UUID uuid, java.util.function.Consumer<com.haven.shards.model.DailyRewardData> callback) {
        executeAsync(() -> {
            long lastClaim = 0L;
            int streak = 0;
            String query = "SELECT last_claim_millis, streak FROM haven_daily_rewards WHERE uuid = ?";
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, uuid.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        lastClaim = rs.getLong("last_claim_millis");
                        streak = rs.getInt("streak");
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load daily reward data for " + uuid, e);
            }
            final long fLastClaim = lastClaim;
            final int fStreak = streak;
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(new com.haven.shards.model.DailyRewardData(uuid, fLastClaim, fStreak)));
        });
    }

    public void saveDailyRewardAsync(UUID uuid, long lastClaimMillis, int streak) {
        executeAsync(() -> {
            saveDailyRewardSync(uuid, lastClaimMillis, streak);
        });
    }

    public void saveDailyRewardSync(UUID uuid, long lastClaimMillis, int streak) {
        String query;
        if (isMySQL) {
            query = "INSERT INTO haven_daily_rewards (uuid, last_claim_millis, streak) VALUES (?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE last_claim_millis = VALUES(last_claim_millis), streak = VALUES(streak);";
        } else {
            query = "INSERT INTO haven_daily_rewards (uuid, last_claim_millis, streak) VALUES (?, ?, ?) " +
                    "ON CONFLICT(uuid) DO UPDATE SET last_claim_millis = excluded.last_claim_millis, streak = excluded.streak;";
        }
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, uuid.toString());
            pstmt.setLong(2, lastClaimMillis);
            pstmt.setInt(3, streak);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to sync save daily reward data for " + uuid, e);
        }
    }

    public void loadClaimedAdvancementsAsync(UUID uuid, java.util.function.Consumer<java.util.Set<String>> callback) {
        executeAsync(() -> {
            java.util.Set<String> set = java.util.concurrent.ConcurrentHashMap.newKeySet();
            String query = "SELECT advancement_key FROM haven_claimed_advancements WHERE uuid = ?";
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, uuid.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        set.add(rs.getString("advancement_key"));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load claimed advancements for " + uuid, e);
            }
            if (callback != null) {
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(set));
            }
        });
    }

    public void saveClaimedAdvancementAsync(UUID uuid, String advancementKey) {
        executeAsync(() -> {
            String query;
            if (isMySQL) {
                query = "INSERT IGNORE INTO haven_claimed_advancements (uuid, advancement_key, claimed_at) VALUES (?, ?, ?);";
            } else {
                query = "INSERT OR IGNORE INTO haven_claimed_advancements (uuid, advancement_key, claimed_at) VALUES (?, ?, ?);";
            }
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, uuid.toString());
                pstmt.setString(2, advancementKey);
                pstmt.setLong(3, System.currentTimeMillis());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to save claimed advancement for " + uuid, e);
            }
        });
    }

    public void loadDailyPayLimitAsync(UUID uuid, java.util.function.Consumer<com.haven.shards.model.DailyPayData> callback) {
        executeAsync(() -> {
            String dayKey = "";
            double totalSent = 0.0;
            int txCount = 0;
            boolean error = false;
            String query = "SELECT day_key, total_sent, transaction_count FROM haven_daily_pay_limits WHERE uuid = ?";
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, uuid.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        dayKey = rs.getString("day_key");
                        totalSent = rs.getDouble("total_sent");
                        txCount = rs.getInt("transaction_count");
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load daily pay limit for " + uuid, e);
                error = true;
            }

            final String fDayKey = dayKey;
            final double fTotalSent = totalSent;
            final int fTxCount = txCount;
            final boolean fError = error;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (callback != null) {
                    if (fError) {
                        callback.accept(null);
                    } else {
                        callback.accept(new com.haven.shards.model.DailyPayData(uuid, fDayKey, fTotalSent, fTxCount));
                    }
                }
            });
        });
    }

    public void saveDailyPayLimitAsync(UUID uuid, String dayKey, double totalSent, int txCount) {
        executeAsync(() -> {
            String query;
            if (isMySQL) {
                query = "INSERT INTO haven_daily_pay_limits (uuid, day_key, total_sent, transaction_count) VALUES (?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE day_key = VALUES(day_key), total_sent = VALUES(total_sent), transaction_count = VALUES(transaction_count);";
            } else {
                query = "INSERT INTO haven_daily_pay_limits (uuid, day_key, total_sent, transaction_count) VALUES (?, ?, ?, ?) " +
                        "ON CONFLICT(uuid) DO UPDATE SET day_key = excluded.day_key, total_sent = excluded.total_sent, transaction_count = excluded.transaction_count;";
            }
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, uuid.toString());
                pstmt.setString(2, dayKey);
                pstmt.setDouble(3, totalSent);
                pstmt.setInt(4, txCount);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to save daily pay limit for " + uuid, e);
            }
        });
    }

    public void loadDailyWithdrawLimitAsync(UUID uuid, java.util.function.Consumer<com.haven.shards.model.DailyWithdrawData> callback) {
        executeAsync(() -> {
            String dayKey = "";
            double totalWithdrawn = 0.0;
            int withdrawCount = 0;
            boolean error = false;
            String query = "SELECT day_key, total_withdrawn, withdraw_count FROM haven_daily_withdraw_limits WHERE uuid = ?";
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, uuid.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        dayKey = rs.getString("day_key");
                        totalWithdrawn = rs.getDouble("total_withdrawn");
                        withdrawCount = rs.getInt("withdraw_count");
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load daily withdraw limit for " + uuid, e);
                error = true;
            }

            final String fDayKey = dayKey;
            final double fTotalWithdrawn = totalWithdrawn;
            final int fWithdrawCount = withdrawCount;
            final boolean fError = error;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (callback != null) {
                    if (fError) {
                        callback.accept(null);
                    } else {
                        callback.accept(new com.haven.shards.model.DailyWithdrawData(uuid, fDayKey, fTotalWithdrawn, fWithdrawCount));
                    }
                }
            });
        });
    }

    public void saveDailyWithdrawLimitAsync(UUID uuid, String dayKey, double totalWithdrawn, int withdrawCount) {
        executeAsync(() -> {
            String query;
            if (isMySQL) {
                query = "INSERT INTO haven_daily_withdraw_limits (uuid, day_key, total_withdrawn, withdraw_count) VALUES (?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE day_key = VALUES(day_key), total_withdrawn = VALUES(total_withdrawn), withdraw_count = VALUES(withdraw_count);";
            } else {
                query = "INSERT INTO haven_daily_withdraw_limits (uuid, day_key, total_withdrawn, withdraw_count) VALUES (?, ?, ?, ?) " +
                        "ON CONFLICT(uuid) DO UPDATE SET day_key = excluded.day_key, total_withdrawn = excluded.total_withdrawn, withdraw_count = excluded.withdraw_count;";
            }
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, uuid.toString());
                pstmt.setString(2, dayKey);
                pstmt.setDouble(3, totalWithdrawn);
                pstmt.setInt(4, withdrawCount);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to save daily withdraw limit for " + uuid, e);
            }
        });
    }

    public void savePendingRefundAsync(UUID uuid, ItemStack item) {
        if (uuid == null || item == null) return;
        final ItemStack clone = item.clone();
        pendingRefundsCache.computeIfAbsent(uuid, k -> java.util.Collections.synchronizedList(new ArrayList<>())).add(clone);
        final String base64 = ItemUtil.serializeItemStack(clone);
        if (base64 == null) return;
        executeAsync(() -> {
            String query = "INSERT INTO haven_pending_refunds (uuid, item_data) VALUES (?, ?);";
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, uuid.toString());
                pstmt.setString(2, base64);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to save pending refund to DB for " + uuid, e);
            }
        });
    }

    public void claimPendingRefundsAsync(UUID uuid, java.util.function.Consumer<List<ItemStack>> callback) {
        if (uuid == null) {
            if (callback != null) callback.accept(java.util.Collections.emptyList());
            return;
        }
        executeAsync(() -> {
            List<ItemStack> list = new ArrayList<>();
            List<ItemStack> memList = pendingRefundsCache.remove(uuid);
            if (memList != null && !memList.isEmpty()) {
                list.addAll(memList);
            }

            List<Integer> idsToDelete = new ArrayList<>();
            String selectQuery = "SELECT id, item_data FROM haven_pending_refunds WHERE uuid = ?;";
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(selectQuery)) {
                pstmt.setString(1, uuid.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        int id = rs.getInt("id");
                        String base64 = rs.getString("item_data");
                        ItemStack item = ItemUtil.deserializeItemStack(base64);
                        if (item != null) {
                            if (memList == null || memList.isEmpty()) {
                                list.add(item);
                            }
                        }
                        idsToDelete.add(id);
                    }
                }

                if (!idsToDelete.isEmpty()) {
                    String deleteQuery = "DELETE FROM haven_pending_refunds WHERE id = ?;";
                    try (PreparedStatement delStmt = conn.prepareStatement(deleteQuery)) {
                        for (int id : idsToDelete) {
                            delStmt.setInt(1, id);
                            delStmt.addBatch();
                        }
                        delStmt.executeBatch();
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to claim pending refunds for " + uuid, e);
            }

            final List<ItemStack> finalList = list;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (callback != null) {
                    callback.accept(finalList);
                }
            });
        });
    }

    public synchronized void close() {
        this.isShuttingDown = true;
        if (topCacheTask != null) {
            topCacheTask.cancel();
            topCacheTask = null;
        }
        long waitStart = System.currentTimeMillis();
        while (activeAsyncQueries.get() > 0 && (System.currentTimeMillis() - waitStart) < 5000) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("[HavenShards] Database pool closed cleanly.");
        }
    }
}
