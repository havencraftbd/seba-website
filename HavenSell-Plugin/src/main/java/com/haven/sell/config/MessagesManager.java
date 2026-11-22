package com.haven.sell.config;

import com.haven.sell.HavenSell;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.FileOutputStream;
import java.util.logging.Level;

/**
 * Manages the messages configuration with auto-updating support.
 */
public class MessagesManager {

    private final HavenSell plugin;
    private final File messagesFile;
    private YamlConfiguration config;
    private String prefix;

    public MessagesManager(HavenSell plugin) {
        this.plugin = plugin;
        this.messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        loadConfig(true);
    }

    private void loadConfig(boolean update) {
        if (!messagesFile.exists()) {
            saveDefaultConfig();
        } else if (update) {
            try {
                com.haven.sell.util.ConfigUpdater.update(plugin, "messages.yml", messagesFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to update messages.yml with new defaults", e);
            }
        }

        config = YamlConfiguration.loadConfiguration(messagesFile);

        // Load prefix from messages.yml (configurable for both console & in-game)
        String defaultPrefix = config.getString("prefix", "&#92bed8HavenSell &7• ");
        prefix = colorize(plugin.getConfig().getString("prefix", defaultPrefix));
    }

    private void saveDefaultConfig() {
        try (InputStream in = plugin.getResource("messages.yml")) {
            if (in == null) return;
            try (OutputStream out = new FileOutputStream(messagesFile)) {
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save default messages.yml", e);
        }
    }

    public void reload() {
        loadConfig(false);
    }

    public String getPrefix() {
        return prefix;
    }

    /**
     * Retrieves a message from messages.yml, colors it, and prepends the prefix.
     */
    public String getMessage(String key) {
        String msg = config.getString(key, "&cMessage not found: " + key);
        return prefix + colorize(msg);
    }

    /**
     * Retrieves a raw message from messages.yml and colors it (no prefix).
     */
    public String getRawMessage(String key) {
        String msg = config.getString(key, "&cMessage not found: " + key);
        return colorize(msg);
    }

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    public static String colorize(String text) {
        if (text == null) return "";
        // Translate hex color codes: &#RRGGBB -> §x§R§R§G§G§B§B
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            StringBuilder hex = new StringBuilder("§x");
            for (char c : matcher.group(1).toCharArray()) {
                hex.append('§').append(c);
            }
            matcher.appendReplacement(buffer, hex.toString());
        }
        matcher.appendTail(buffer);
        // Translate legacy color codes: &a, &7, etc.
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }
}
