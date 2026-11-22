package com.haven.sell.config;

import com.haven.sell.HavenSell;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Manages the sell GUI configuration from gui/sell-gui.yml.
 */
public class GuiConfig {

    private final HavenSell plugin;
    private final File guiFile;
    private YamlConfiguration config;

    public GuiConfig(HavenSell plugin) {
        this.plugin = plugin;
        File guiDir = new File(plugin.getDataFolder(), "gui");
        if (!guiDir.exists()) {
            guiDir.mkdirs();
        }
        this.guiFile = new File(guiDir, "sell-gui.yml");
        loadConfig(true);
    }

    /**
     * Loads the GUI config from disk.
     */
    private void loadConfig(boolean update) {
        if (!guiFile.exists()) {
            saveDefaultConfig();
        } else if (update) {
            try {
                com.haven.sell.util.ConfigUpdater.update(plugin, "gui/sell-gui.yml", guiFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to update sell-gui.yml with new defaults", e);
            }
        }

        config = YamlConfiguration.loadConfiguration(guiFile);
    }

    private void saveDefaultConfig() {
        try (InputStream in = plugin.getResource("gui/sell-gui.yml")) {
            if (in == null) return;
            try (java.io.OutputStream out = new java.io.FileOutputStream(guiFile)) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = in.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save default sell-gui.yml", e);
        }
    }

    /**
     * Reloads the GUI configuration.
     */
    public void reload() {
        loadConfig(false);
    }

    // ━━━━━━━━━━━━━━━ General ━━━━━━━━━━━━━━━

    public String getTitle() {
        return ConfigManager.colorize(config.getString("title", "&8&lSell Items"));
    }

    public int getRows() {
        return Math.max(1, Math.min(6, config.getInt("rows", 5)));
    }

    public int getSize() {
        return getRows() * 9;
    }

    // ━━━━━━━━━━━━━━━ Filler ━━━━━━━━━━━━━━━

    public boolean isFillerEnabled() {
        return config.getBoolean("filler.enabled", true);
    }

    public Material getFillerMaterial() {
        String mat = config.getString("filler.material", "BLACK_STAINED_GLASS_PANE");
        Material material = Material.matchMaterial(mat);
        return material != null ? material : Material.BLACK_STAINED_GLASS_PANE;
    }

    public String getFillerName() {
        return ConfigManager.colorize(config.getString("filler.name", " "));
    }

    // ━━━━━━━━━━━━━━━ Buttons ━━━━━━━━━━━━━━━

    public boolean isCloseEnabled() {
        return config.getBoolean("buttons.close.enabled", true);
    }

    public int getCloseSlot() {
        return config.getInt("buttons.close.slot", 36);
    }

    public Material getCloseMaterial() {
        String mat = config.getString("buttons.close.material", "BARRIER");
        Material material = Material.matchMaterial(mat);
        return material != null ? material : Material.BARRIER;
    }

    public String getCloseName() {
        return ConfigManager.colorize(config.getString("buttons.close.name", "&c&lClose"));
    }

    public List<String> getCloseLore() {
        return colorizeList(config.getStringList("buttons.close.lore"));
    }

    public boolean isSellEnabled() {
        return config.getBoolean("buttons.sell.enabled", true);
    }

    public int getSellSlot() {
        return config.getInt("buttons.sell.slot", 40);
    }

    public Material getSellMaterial() {
        String mat = config.getString("buttons.sell.material", "EMERALD");
        Material material = Material.matchMaterial(mat);
        return material != null ? material : Material.EMERALD;
    }

    public String getSellName() {
        return ConfigManager.colorize(config.getString("buttons.sell.name", "&a&lSell Items"));
    }

    public List<String> getSellLore() {
        return colorizeList(config.getStringList("buttons.sell.lore"));
    }

    public boolean isBalanceEnabled() {
        return config.getBoolean("buttons.balance.enabled", true);
    }

    public int getBalanceSlot() {
        return config.getInt("buttons.balance.slot", 44);
    }

    public Material getBalanceMaterial() {
        String mat = config.getString("buttons.balance.material", "PLAYER_HEAD");
        Material material = Material.matchMaterial(mat);
        return material != null ? material : Material.PLAYER_HEAD;
    }

    public String getBalanceName() {
        return ConfigManager.colorize(config.getString("buttons.balance.name", "&6&lYour Balance"));
    }

    public List<String> getBalanceLore() {
        return config.getStringList("buttons.balance.lore");
    }

    /**
     * Gets the balance lore with placeholders replaced.
     * Accepts pre-formatted strings (abbreviation/plain already applied by caller).
     *
     * @param balanceFormatted Formatted balance string e.g. "1.5K"
     * @param totalItems       Total items sold
     * @param earnedFormatted  Formatted total earned string e.g. "10M"
     * @param currencySymbol   Currency symbol e.g. "$"
     */
    public List<String> getBalanceLore(String balanceFormatted, int totalItems,
                                        String earnedFormatted, String currencySymbol) {
        List<String> lore = config.getStringList("buttons.balance.lore");
        List<String> parsed = new ArrayList<>();

        for (String line : lore) {
            line = line.replace("{balance}", balanceFormatted)
                    .replace("{total_items}", String.valueOf(totalItems))
                    .replace("{total_earned}", earnedFormatted)
                    .replace("{symbol}", currencySymbol);
            parsed.add(ConfigManager.colorize(line));
        }
        return parsed;
    }

    // ━━━━━━━━━━━━━━━ Confirm GUI ━━━━━━━━━━━━━━━

    public String getConfirmTitleHand() {
        return ConfigManager.colorize(config.getString("confirm.title-hand", "&8Confirm Sell Hand?"));
    }

    public String getConfirmTitleAll() {
        return ConfigManager.colorize(config.getString("confirm.title-all", "&8Confirm Sell All?"));
    }

    public int getConfirmRows() {
        return Math.max(1, Math.min(6, config.getInt("confirm.rows", 3)));
    }

    public int getConfirmSize() {
        return getConfirmRows() * 9;
    }

    public boolean isConfirmFillerEnabled() {
        return config.getBoolean("confirm.filler.enabled", true);
    }

    public Material getConfirmFillerMaterial() {
        String mat = config.getString("confirm.filler.material", "GRAY_STAINED_GLASS_PANE");
        Material material = Material.matchMaterial(mat);
        return material != null ? material : Material.GRAY_STAINED_GLASS_PANE;
    }

    public String getConfirmFillerName() {
        return ConfigManager.colorize(config.getString("confirm.filler.name", " "));
    }

    public int getConfirmButtonSlot() {
        return config.getInt("confirm.buttons.confirm.slot", 11);
    }

    public Material getConfirmButtonMaterial() {
        String mat = config.getString("confirm.buttons.confirm.material", "LIME_WOOL");
        Material material = Material.matchMaterial(mat);
        return material != null ? material : Material.LIME_WOOL;
    }

    public String getConfirmButtonName() {
        return ConfigManager.colorize(config.getString("confirm.buttons.confirm.name", "&a&lConfirm"));
    }

    public List<String> getConfirmButtonLore() {
        return colorizeList(config.getStringList("confirm.buttons.confirm.lore"));
    }

    public int getCancelButtonSlot() {
        return config.getInt("confirm.buttons.cancel.slot", 15);
    }

    public Material getCancelButtonMaterial() {
        String mat = config.getString("confirm.buttons.cancel.material", "RED_WOOL");
        Material material = Material.matchMaterial(mat);
        return material != null ? material : Material.RED_WOOL;
    }

    public String getCancelButtonName() {
        return ConfigManager.colorize(config.getString("confirm.buttons.cancel.name", "&c&lCancel"));
    }

    public List<String> getCancelButtonLore() {
        return colorizeList(config.getStringList("confirm.buttons.cancel.lore"));
    }

    // ━━━━━━━━━━━━━━━ Utility ━━━━━━━━━━━━━━━

    private List<String> colorizeList(List<String> list) {
        List<String> colored = new ArrayList<>();
        for (String s : list) {
            colored.add(ConfigManager.colorize(s));
        }
        return colored;
    }
}
