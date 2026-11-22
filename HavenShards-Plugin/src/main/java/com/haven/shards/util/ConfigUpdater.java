package com.haven.shards.util;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ConfigUpdater {

    /**
     * Automatically updates the target configuration file on disk by adding any missing
     * keys and comments present in the default resource file inside the plugin JAR.
     * Existing custom values set by the user are preserved and never overwritten.
     *
     * @param plugin       The plugin instance
     * @param resourceName The name of the resource file inside JAR (e.g. "config.yml")
     * @param configFile   The target file on disk (e.g. getDataFolder()/config.yml)
     */
    public static void update(Plugin plugin, String resourceName, File configFile) {
        if (!configFile.exists()) {
            plugin.saveResource(resourceName, false);
            return;
        }

        try {
            InputStream resourceStream = plugin.getResource(resourceName);
            if (resourceStream == null) {
                return;
            }

            FileConfiguration diskConfig = YamlConfiguration.loadConfiguration(configFile);
            FileConfiguration defaultConfig;
            try (InputStreamReader reader = new InputStreamReader(resourceStream, StandardCharsets.UTF_8)) {
                defaultConfig = YamlConfiguration.loadConfiguration(reader);
            }

            int addedKeysCount = 0;

            // Check and set root file header comments if disk config lacks them
            List<String> rootComments = defaultConfig.getComments("");
            if ((diskConfig.getComments("") == null || diskConfig.getComments("").isEmpty())
                    && rootComments != null && !rootComments.isEmpty()) {
                diskConfig.setComments("", rootComments);
            }

            for (String path : defaultConfig.getKeys(true)) {
                // If path is a section holding children, skip direct value assignment unless it's missing completely
                if (defaultConfig.isConfigurationSection(path)) {
                    if (!diskConfig.contains(path)) {
                        diskConfig.createSection(path);
                        List<String> comments = defaultConfig.getComments(path);
                        if (comments != null && !comments.isEmpty()) {
                            diskConfig.setComments(path, comments);
                        }
                    }
                    continue;
                }

                // If path is missing on disk, add default value and comments
                if (!diskConfig.contains(path)) {
                    diskConfig.set(path, defaultConfig.get(path));
                    addedKeysCount++;

                    List<String> comments = defaultConfig.getComments(path);
                    if (comments != null && !comments.isEmpty()) {
                        diskConfig.setComments(path, comments);
                    }

                    List<String> inlineComments = defaultConfig.getInlineComments(path);
                    if (inlineComments != null && !inlineComments.isEmpty()) {
                        diskConfig.setInlineComments(path, inlineComments);
                    }
                }
            }

            if (addedKeysCount > 0) {
                diskConfig.save(configFile);
                plugin.getLogger().info("Auto-updated " + configFile.getName() + " with " + addedKeysCount + " new setting(s). Existing custom values were preserved.");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to auto-update " + configFile.getName() + ": " + e.getMessage());
        }
    }
}
