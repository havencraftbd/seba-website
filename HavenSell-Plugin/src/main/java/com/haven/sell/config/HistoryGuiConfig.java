package com.haven.sell.config;

import com.haven.sell.HavenSell;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Manages the sell history GUI configuration from gui/sellhistory.yml.
 */
public class HistoryGuiConfig {

    private final HavenSell plugin;
    private final File guiFile;
    private YamlConfiguration config;

    public HistoryGuiConfig(HavenSell plugin) {
        this.plugin = plugin;
        File guiDir = new File(plugin.getDataFolder(), "gui");
        if (!guiDir.exists()) guiDir.mkdirs();
        this.guiFile = new File(guiDir, "sellhistory.yml");
        loadConfig(true);
    }

    private void loadConfig(boolean update) {
        if (!guiFile.exists()) {
            saveDefaultConfig();
        } else if (update) {
            try {
                com.haven.sell.util.ConfigUpdater.update(plugin, "gui/sellhistory.yml", guiFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to update sellhistory.yml with new defaults", e);
            }
        }

        config = YamlConfiguration.loadConfiguration(guiFile);
    }

    private void saveDefaultConfig() {
        try (java.io.InputStream in = plugin.getResource("gui/sellhistory.yml")) {
            if (in == null) { plugin.getLogger().warning("Default sellhistory.yml not found in resources."); return; }
            try (java.io.OutputStream out = new java.io.FileOutputStream(guiFile)) {
                byte[] buf = new byte[1024]; int len;
                while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save default sellhistory.yml", e);
        }
    }

    public void reload() { loadConfig(false); }

    // ━━━━━━━━━━━━━━━ General ━━━━━━━━━━━━━━━

    public String getTitle(String playerName) {
        return ConfigManager.colorize(config.getString("title", "&8&lSell History &7— &f{player}")
                .replace("{player}", playerName));
    }

    public int getRows() { return Math.max(1, Math.min(6, config.getInt("rows", 6))); }
    public int getSize() { return getRows() * 9; }

    // ━━━━━━━━━━━━━━━ Filler ━━━━━━━━━━━━━━━

    public boolean isFillerEnabled() { return config.getBoolean("filler.enabled", true); }

    public Material getFillerMaterial() {
        String mat = config.getString("filler.material", "GRAY_STAINED_GLASS_PANE");
        Material m = Material.matchMaterial(mat);
        return m != null ? m : Material.GRAY_STAINED_GLASS_PANE;
    }

    public String getFillerName() { return ConfigManager.colorize(config.getString("filler.name", " ")); }

    // ━━━━━━━━━━━━━━━ Player Head ━━━━━━━━━━━━━━━

    public int getHeadSlot() {
        int slot = config.getInt("player-head.slot", 0);
        return (slot >= 0 && slot < getSize()) ? slot : 0;
    }

    public String getHeadName(String playerName) {
        return ConfigManager.colorize(config.getString("player-head.name", "&6{player}'s History")
                .replace("{player}", playerName));
    }

    public List<String> getHeadLore(int items, String formattedMoney, String symbol) {
        List<String> raw = config.getStringList("player-head.lore");
        List<String> result = new ArrayList<>();
        for (String line : raw) {
            line = line.replace("{items}", String.valueOf(items))
                       .replace("{money}", formattedMoney)
                       .replace("{symbol}", symbol);
            result.add(ConfigManager.colorize(line));
        }
        return result;
    }

    // ━━━━━━━━━━━━━━━ Summary Item ━━━━━━━━━━━━━━━

    public int getSummarySlot() { return config.getInt("summary.slot", 4); }

    public Material getSummaryMaterial() {
        String mat = config.getString("summary.material", "BOOK");
        Material m = Material.matchMaterial(mat);
        return m != null ? m : Material.BOOK;
    }

    public String getSummaryName() { return ConfigManager.colorize(config.getString("summary.name", "&6&lSell Statistics")); }

    public List<String> getSummaryLore(String playerName, int totalItems, double totalEarned,
                                        String formattedEarned, String symbol, int maxEntries) {
        List<String> raw = config.getStringList("summary.lore");
        List<String> result = new ArrayList<>();
        for (String line : raw) {
            line = line.replace("{player}", playerName)
                       .replace("{total_items}", String.valueOf(totalItems))
                       .replace("{total_earned}", formattedEarned)
                       .replace("{symbol}", symbol)
                       .replace("{max_entries}", String.valueOf(maxEntries));
            result.add(ConfigManager.colorize(line));
        }
        return result;
    }

    // ━━━━━━━━━━━━━━━ History Entries ━━━━━━━━━━━━━━━

    public int getHistoryStartSlot() { return config.getInt("history-start-slot", 9); }
    public int getHistoryEndSlot()   { return config.getInt("history-end-slot", 44); }
    public int getMaxEntries()       { return config.getInt("max-entries", 10); }

    public Material getHistoryItemMaterial() {
        String mat = config.getString("history-item.material", "PAPER");
        Material m = Material.matchMaterial(mat);
        return m != null ? m : Material.PAPER;
    }

    public String getHistoryItemName() {
        return ConfigManager.colorize(config.getString("history-item.name", "&a&lSell Transaction"));
    }

    public List<String> getHistoryItemLore(int items, String formattedMoney, String symbol, String date) {
        List<String> raw = config.getStringList("history-item.lore");
        List<String> result = new ArrayList<>();
        for (String line : raw) {
            line = line.replace("{items}", String.valueOf(items))
                       .replace("{money}", formattedMoney)
                       .replace("{symbol}", symbol)
                       .replace("{date}", date);
            result.add(ConfigManager.colorize(line));
        }
        return result;
    }

    // ━━━━━━━━━━━━━━━ Bottom Controls ━━━━━━━━━━━━━━━

    public Material getBorderMaterial() {
        String mat = config.getString("border.material", "BLACK_STAINED_GLASS_PANE");
        Material m = Material.matchMaterial(mat);
        return m != null ? m : Material.BLACK_STAINED_GLASS_PANE;
    }

    public String getBorderName() { return ConfigManager.colorize(config.getString("border.name", " ")); }

    public int getBackSlot()  { return config.getInt("back-button.slot", 49); }

    public Material getBackMaterial() {
        String mat = config.getString("back-button.material", "ARROW");
        Material m = Material.matchMaterial(mat);
        return m != null ? m : Material.ARROW;
    }

    public String getBackName() { return ConfigManager.colorize(config.getString("back-button.name", "&c&lBack")); }
    public List<String> getBackLore() { return colorizeList(config.getStringList("back-button.lore")); }

    public int getCloseSlot() { return config.getInt("close-button.slot", 53); }

    public Material getCloseMaterial() {
        String mat = config.getString("close-button.material", "BARRIER");
        Material m = Material.matchMaterial(mat);
        return m != null ? m : Material.BARRIER;
    }

    public String getCloseName() { return ConfigManager.colorize(config.getString("close-button.name", "&4&lClose")); }
    public List<String> getCloseLore() { return colorizeList(config.getStringList("close-button.lore")); }

    private List<String> colorizeList(List<String> list) {
        List<String> out = new ArrayList<>();
        for (String s : list) out.add(ConfigManager.colorize(s));
        return out;
    }
}
