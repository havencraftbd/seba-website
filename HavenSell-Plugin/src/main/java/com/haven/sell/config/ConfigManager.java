package com.haven.sell.config;

import com.haven.sell.HavenSell;
import com.haven.sell.util.NumberFormatter;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Manages the main config.yml and provides access to all general settings.
 */
public class ConfigManager {

    private final HavenSell plugin;
    private final File configFile;
    private YamlConfiguration config;

    public ConfigManager(HavenSell plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
        loadConfig(true);
    }

    private void loadConfig(boolean update) {
        if (!configFile.exists()) {
            plugin.saveDefaultConfig();
        } else if (update) {
            try {
                com.haven.sell.util.ConfigUpdater.update(plugin, "config.yml", configFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to update config.yml with new defaults", e);
            }
        }

        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public void reload() { loadConfig(false); }

    // ━━━━━━━━━━━━━━━ General ━━━━━━━━━━━━━━━

    /** Whether the global sell system is enabled. Reloads with /sell reload. */
    public boolean isSellEnabled() {
        return config.getBoolean("sell.enabled", true);
    }

    public boolean isSellOnClose() {
        return config.getBoolean("sell-on-close", true);
    }

    public int getSellAllCooldownSeconds() {
        return Math.max(0, config.getInt("cooldowns.sell-all", 3));
    }

    // ━━━━━━━━━━━━━━━ Shulker Support ━━━━━━━━━━━━━━━

    public boolean isShulkerSupportEnabled() {
        return config.getBoolean("shulker-support.enabled", true);
    }

    // ━━━━━━━━━━━━━━━ Abbreviation Settings ━━━━━━━━━━━━━━━

    public boolean isAbbreviationsEnabled() {
        return config.getBoolean("abbreviations.enabled", true);
    }

    public List<String> getAbbreviationSuffixes() {
        List<String> list = config.getStringList("abbreviations.formats");
        return list.isEmpty() ? java.util.Arrays.asList("K", "M", "B", "T") : list;
    }

    // ━━━━━━━━━━━━━━━ Title Settings ━━━━━━━━━━━━━━━

    public boolean isTitleEnabled()  { return config.getBoolean("title.enabled", true); }
    public String  getTitleText()    { return colorize(config.getString("title.title",    "&a+{symbol}{money}")); }
    public String  getSubtitleText() { return colorize(config.getString("title.subtitle", "&7You sold &f{amount} &7items")); }
    public int getTitleFadeIn()  { return config.getInt("title.fade-in",  10); }
    public int getTitleStay()    { return config.getInt("title.stay",     40); }
    public int getTitleFadeOut() { return config.getInt("title.fade-out", 10); }

    // ━━━━━━━━━━━━━━━ Sound Settings ━━━━━━━━━━━━━━━

    public String getSound(String key) { return config.getString("sounds." + key, ""); }

    // ━━━━━━━━━━━━━━━ Currency Settings ━━━━━━━━━━━━━━━

    public String getCurrencySymbol() { return config.getString("currency.symbol",   "$"); }
    public int getCurrencyDecimals()  { return config.getInt(   "currency.decimals", 2);   }

    /**
     * Formats a money amount using the abbreviation system (if enabled),
     * otherwise falls back to a plain decimal string.
     */
    public String formatMoney(double amount) {
        if (isAbbreviationsEnabled()) {
            return NumberFormatter.format(amount, true, getAbbreviationSuffixes());
        }
        return NumberFormatter.formatPlain(amount, getCurrencyDecimals());
    }

    // ━━━━━━━━━━━━━━━ Sell Axe Settings ━━━━━━━━━━━━━━━

    public boolean isSellAxeEnabled() {
        return config.getBoolean("sell-axe.enabled", true);
    }

    public String getSellAxeMaterial() {
        return config.getString("sell-axe.material", "DIAMOND_AXE");
    }

    public String getSellAxeName() {
        return config.getString("sell-axe.name", "&#92bed8&lSell Axe &7({uses})");
    }

    public List<String> getSellAxeLore() {
        return config.getStringList("sell-axe.lore");
    }

    public int getSellAxeDefaultUses() {
        return config.getInt("sell-axe.default-uses", 100);
    }

    public String getSellAxeDateFormat() {
        return config.getString("sell-axe.date-format", "yyyy-MM-dd HH:mm");
    }

    public String getSellAxeNeverExpireText() {
        return config.getString("sell-axe.never-expire-text", "Never");
    }

    public String getSellAxeUnlimitedUsesText() {
        return config.getString("sell-axe.unlimited-uses-text", "Unlimited");
    }

    public String getSellAxeSound() {
        return config.getString("sell-axe.sound", "ENTITY_PLAYER_LEVELUP");
    }


    // ━━━━━━━━━━━━━━━ Utility ━━━━━━━━━━━━━━━

    public static String colorize(String text) {
        if (text == null) return "";
        // Handle hex color codes: &#RRGGBB → §x§R§R§G§G§B§B
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("&#([A-Fa-f0-9]{6})").matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("\u00A7x");
            for (char c : hex.toCharArray()) {
                replacement.append('\u00A7').append(c);
            }
            matcher.appendReplacement(sb, replacement.toString());
        }
        matcher.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }
}
