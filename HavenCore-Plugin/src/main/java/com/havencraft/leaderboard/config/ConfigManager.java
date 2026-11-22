package com.havencraft.leaderboard.config;

import com.havencraft.havencore.util.TextUtil;
import com.havencraft.leaderboard.HavenLeaderboard;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.tchristofferson.configupdater.ConfigUpdater;

public class ConfigManager {

    private final HavenLeaderboard plugin;
    private FileConfiguration config;
    private FileConfiguration messages;
    private FileConfiguration sounds;
    private FileConfiguration mainGui;
    private final Map<String, FileConfiguration> guiConfigs = new HashMap<>();

    public ConfigManager(HavenLeaderboard plugin) {
        this.plugin = plugin;
    }

    public void loadConfigs() {
        config = loadYamlFile("Leaderboard/config.yml");

        messages = loadYamlFile("Leaderboard/basic-setup/messages.yml");
        sounds = loadYamlFile("Leaderboard/basic-setup/sounds.yml");
        loadYamlFile("Leaderboard/basic-setup/money-provider.yml"); // Just save default
        
        mainGui = loadYamlFile("Leaderboard/gui/main-gui.yml");
        guiConfigs.clear();
    }

    private FileConfiguration loadYamlFile(String path) {
        File file = new File(plugin.getJavaPlugin().getDataFolder(), path);
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            plugin.getJavaPlugin().saveResource(path, false);
        } else {
            InputStream defStream = plugin.getJavaPlugin().getResource(path);
            if (defStream != null) {
                try {
                    if (path.equals("Leaderboard/gui/main-gui.yml")) {
                        ConfigUpdater.update(plugin.getJavaPlugin(), path, file, Collections.singletonList("items"));
                    } else {
                        ConfigUpdater.update(plugin.getJavaPlugin(), path, file);
                    }
                } catch (IOException e) {
                    plugin.getJavaPlugin().getLogger().warning("Could not update config file " + path + ": " + e.getMessage());
                }
            }
        }
        FileConfiguration fileConfig = YamlConfiguration.loadConfiguration(file);
        
        // Load defaults from jar if possible to ensure we have fallback mappings
        InputStream defStream = plugin.getJavaPlugin().getResource(path);
        if (defStream != null) {
            fileConfig.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8)));
        }
        return fileConfig;
    }

    private String colorize(String text) {
        return TextUtil.colorize(text);
    }

    public String getMessage(String path) {
        String msg = messages.getString(path, "&cMessage not found: " + path);
        String prefix = messages.getString("prefix", "");
        return colorize(prefix + msg);
    }
    
    public String getRawMessage(String path) {
        String msg = messages.getString(path, "&cMessage not found: " + path);
        return colorize(msg);
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public FileConfiguration getMessages() {
        return messages;
    }

    public FileConfiguration getSounds() {
        return sounds;
    }

    public FileConfiguration getMainGui() {
        return mainGui;
    }
    
    public FileConfiguration getGuiConfig(String name) {
        return guiConfigs.computeIfAbsent(name, key -> loadYamlFile("Leaderboard/gui/" + key + ".yml"));
    }
}
