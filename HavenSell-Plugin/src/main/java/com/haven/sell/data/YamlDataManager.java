package com.haven.sell.data;

import com.haven.sell.HavenSell;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * YAML-based data manager that stores player data in individual .yml files.
 * File writes are performed asynchronously to avoid blocking the main thread.
 * Sell history entries are stored under the 'history' section.
 */
public class YamlDataManager implements DataManager {

    private final HavenSell plugin;
    private final File dataDir;
    private final Map<UUID, PlayerData> cache;
    private final Map<UUID, Object> writeLocks;

    public YamlDataManager(HavenSell plugin) {
        this.plugin = plugin;
        this.dataDir = new File(plugin.getDataFolder(), "playerdata");
        this.cache = new ConcurrentHashMap<>();
        this.writeLocks = new ConcurrentHashMap<>();
        if (!dataDir.exists()) dataDir.mkdirs();
    }

    @Override
    public PlayerData loadPlayerData(UUID uuid, String playerName) {
        PlayerData cached = cache.get(uuid);
        if (cached != null) return cached;

        File file = getPlayerFile(playerName);
        if (!file.exists()) {
            PlayerData data = new PlayerData(uuid, playerName);
            cache.put(uuid, data);
            return data;
        }

        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            int totalItems    = yaml.getInt("total-items-sold", 0);
            double totalMoney = yaml.getDouble("total-money-earned", 0.0);
            List<SellEntry> history = loadHistory(yaml);

            PlayerData data = new PlayerData(uuid, playerName, totalItems, totalMoney, history);
            cache.put(uuid, data);
            return data;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load player data for " + playerName, e);
            PlayerData data = new PlayerData(uuid, playerName);
            cache.put(uuid, data);
            return data;
        }
    }

    @Override
    public PlayerData loadPlayerDataByName(String playerName) {
        // Check cache first (case-insensitive)
        for (PlayerData pd : cache.values()) {
            if (pd.getPlayerName().equalsIgnoreCase(playerName)) return pd;
        }

        // Try exact file match first, then case-insensitive scan
        File file = getPlayerFile(playerName);
        if (!file.exists()) {
            // Scan directory for case-insensitive match
            File[] files = dataDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.getName().equalsIgnoreCase(playerName + ".yml")) {
                        file = f;
                        break;
                    }
                }
            }
            if (!file.exists()) return null;
        }

        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            String uuidStr    = yaml.getString("uuid", null);
            UUID   uuid       = (uuidStr != null) ? UUID.fromString(uuidStr) : UUID.randomUUID();
            int    totalItems = yaml.getInt("total-items-sold", 0);
            double totalMoney = yaml.getDouble("total-money-earned", 0.0);
            List<SellEntry> history = loadHistory(yaml);

            return new PlayerData(uuid, playerName, totalItems, totalMoney, history);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load player data by name for " + playerName, e);
            return null;
        }
    }

    @Override
    public void savePlayerData(PlayerData data) {
        cache.put(data.getUuid(), data);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> writeToFile(data));
    }

    @Override
    public void addSellRecord(UUID uuid, String playerName, int itemCount, double amount, String materialName) {
        PlayerData data = loadPlayerData(uuid, playerName);
        data.addSellRecord(itemCount, amount, materialName);
        savePlayerData(data);
    }

    @Override
    public void unloadPlayerData(UUID uuid) {
        cache.remove(uuid);
        writeLocks.remove(uuid);
    }

    @Override
    public void shutdown() {
        for (PlayerData data : cache.values()) writeToFile(data);
        cache.clear();
    }

    // ━━━━━━━━━━━━━━━ Internal File I/O ━━━━━━━━━━━━━━━

    private void writeToFile(PlayerData data) {
        Object lock = writeLocks.computeIfAbsent(data.getUuid(), k -> new Object());
        
        synchronized (lock) {
            try {
                File file = getPlayerFile(data.getPlayerName());
                YamlConfiguration yaml = new YamlConfiguration();

                yaml.set("uuid",               data.getUuid().toString());
                yaml.set("player-name",        data.getPlayerName());
                yaml.set("total-items-sold",   data.getTotalItemsSold());
                yaml.set("total-money-earned", data.getTotalMoneyEarned());

                // Serialize history entries
                List<Map<String, Object>> historyList = new ArrayList<>();
                for (SellEntry entry : data.getHistory()) {
                    Map<String, Object> map = new java.util.LinkedHashMap<>();
                    map.put("items",     entry.getItemCount());
                    map.put("money",     entry.getMoneyEarned());
                    map.put("timestamp", entry.getTimestamp());
                    if (entry.getMaterialName() != null) {
                        map.put("material", entry.getMaterialName());
                    }
                    historyList.add(map);
                }
                yaml.set("history", historyList);

                yaml.save(file);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to save player data for " + data.getPlayerName(), e);
            }
        }
    }

    /**
     * Loads history entries from the YAML configuration.
     * Uses safe casting to avoid type issues with Map<?,?>.
     */
    private List<SellEntry> loadHistory(YamlConfiguration yaml) {
        List<SellEntry> entries = new ArrayList<>();
        List<?> rawList = yaml.getList("history");
        if (rawList == null) return entries;

        for (Object raw : rawList) {
            try {
                if (!(raw instanceof Map<?, ?> map)) continue;

                // Safe nullable get + cast
                Object itemsObj = map.get("items");
                Object moneyObj = map.get("money");
                Object tsObj    = map.get("timestamp");

                int    items = (itemsObj instanceof Number n) ? n.intValue() : 0;
                double money = (moneyObj instanceof Number n) ? n.doubleValue() : 0.0;
                long   ts    = (tsObj    instanceof Number n) ? n.longValue()   : 0L;

                Object matObj = map.get("material");
                String materialName = (matObj instanceof String s) ? s : null;

                if (items > 0 || money > 0) {
                    entries.add(new SellEntry(items, money, ts, materialName));
                }
            } catch (Exception e) {
                // Skip malformed entries silently
            }
        }
        return entries;
    }

    private File getPlayerFile(String playerName) {
        return new File(dataDir, playerName + ".yml");
    }
}
