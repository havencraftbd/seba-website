package com.havencraft.havencore.util;

import com.tchristofferson.configupdater.ConfigUpdater;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ConfigLoader {

    /**
     * Loads and updates a configuration file from the plugin jar.
     * Uses ConfigUpdater to preserve comments and layout.
     *
     * @param plugin The JavaPlugin instance
     * @param resourcePath The path to the resource inside the jar (e.g., "HavenPunish/config.yml")
     * @param ignoredSections List of sections to ignore during update (can be null or empty)
     * @return The loaded FileConfiguration
     */
    public static FileConfiguration load(JavaPlugin plugin, String resourcePath, List<String> ignoredSections, boolean autoUpdate) {
        String normalizedPath = resourcePath.replace('\\', '/');
        if (normalizedPath.startsWith("/")) {
            normalizedPath = normalizedPath.substring(1);
        }

        File file = new File(plugin.getDataFolder(), normalizedPath);
        if (!file.exists()) {
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            try {
                plugin.saveResource(normalizedPath, false);
            } catch (Throwable t) {
                plugin.getLogger().warning("Could not save default resource '" + normalizedPath + "': " + t.getMessage());
            }
        } else if (autoUpdate) {
            try {
                // Ensure default resource stream exists in jar before calling ConfigUpdater to avoid Reader.<init> NullPointerException
                try (InputStream checkStream = plugin.getResource(normalizedPath)) {
                    if (checkStream != null) {
                        if (ignoredSections != null && !ignoredSections.isEmpty()) {
                            ConfigUpdater.update(plugin, normalizedPath, file, ignoredSections);
                        } else {
                            ConfigUpdater.update(plugin, normalizedPath, file);
                        }
                    }
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("Could not update config: " + normalizedPath + " - " + t.getMessage());
            }
        }

        FileConfiguration config;
        try {
            config = YamlConfiguration.loadConfiguration(file);
        } catch (Throwable t) {
            plugin.getLogger().severe("Failed to load YAML configuration from " + file.getName() + ": " + t.getMessage());
            config = new YamlConfiguration();
        }

        // Merge defaults from jar
        try (InputStream defStream = plugin.getResource(normalizedPath)) {
            if (defStream != null) {
                try (InputStreamReader reader = new InputStreamReader(defStream, StandardCharsets.UTF_8)) {
                    config.setDefaults(YamlConfiguration.loadConfiguration(reader));
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not read/close resource stream for: " + normalizedPath + " - " + t.getMessage());
        }
        
        return config;
    }

    public static FileConfiguration load(JavaPlugin plugin, String resourcePath, List<String> ignoredSections) {
        return load(plugin, resourcePath, ignoredSections, true);
    }

    public static FileConfiguration load(JavaPlugin plugin, String resourcePath) {
        return load(plugin, resourcePath, null, true);
    }
}
