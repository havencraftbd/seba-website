package com.haven.shards.manager;

import com.haven.shards.HavenShards;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import com.haven.shards.api.event.HavenShardsReloadEvent;

public class GUIManager implements Listener {

    private final HavenShards plugin;
    private File dashboardFile;
    private FileConfiguration dashboardConfig;

    private File historyFile;
    private FileConfiguration historyConfig;

    private File questsFile;
    private FileConfiguration questsConfig;

    private File dailyFile;
    private FileConfiguration dailyConfig;

    public GUIManager(HavenShards plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        File guiFolder = new File(plugin.getDataFolder(), "gui");
        if (!guiFolder.exists()) {
            guiFolder.mkdirs();
        }

        // Load Dashboard Config
        dashboardFile = new File(guiFolder, "dashboard.yml");
        if (!dashboardFile.exists()) {
            plugin.saveResource("gui/dashboard.yml", false);
        }
        com.haven.shards.util.ConfigUpdater.update(plugin, "gui/dashboard.yml", dashboardFile);
        dashboardConfig = YamlConfiguration.loadConfiguration(dashboardFile);
        InputStream defaultDashStream = plugin.getResource("gui/dashboard.yml");
        if (defaultDashStream != null) {
            dashboardConfig.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defaultDashStream, StandardCharsets.UTF_8)));
        }

        // Load History Config
        historyFile = new File(guiFolder, "history.yml");
        if (!historyFile.exists()) {
            plugin.saveResource("gui/history.yml", false);
        }
        com.haven.shards.util.ConfigUpdater.update(plugin, "gui/history.yml", historyFile);
        historyConfig = YamlConfiguration.loadConfiguration(historyFile);
        InputStream defaultHistStream = plugin.getResource("gui/history.yml");
        if (defaultHistStream != null) {
            historyConfig.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defaultHistStream, StandardCharsets.UTF_8)));
        }

        // Load Quests Config
        questsFile = new File(guiFolder, "quests.yml");
        if (!questsFile.exists()) {
            plugin.saveResource("gui/quests.yml", false);
        }
        com.haven.shards.util.ConfigUpdater.update(plugin, "gui/quests.yml", questsFile);
        questsConfig = YamlConfiguration.loadConfiguration(questsFile);
        InputStream defaultQuestsStream = plugin.getResource("gui/quests.yml");
        if (defaultQuestsStream != null) {
            questsConfig.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defaultQuestsStream, StandardCharsets.UTF_8)));
        }

        // Load Daily Rewards Config
        dailyFile = new File(guiFolder, "daily.yml");
        if (!dailyFile.exists()) {
            plugin.saveResource("gui/daily.yml", false);
        }
        com.haven.shards.util.ConfigUpdater.update(plugin, "gui/daily.yml", dailyFile);
        dailyConfig = YamlConfiguration.loadConfiguration(dailyFile);
        InputStream defaultDailyStream = plugin.getResource("gui/daily.yml");
        if (defaultDailyStream != null) {
            dailyConfig.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defaultDailyStream, StandardCharsets.UTF_8)));
        }
    }

    public FileConfiguration getDashboardConfig() {
        return dashboardConfig;
    }

    public FileConfiguration getHistoryConfig() {
        return historyConfig;
    }

    public FileConfiguration getQuestsConfig() {
        return questsConfig;
    }

    public FileConfiguration getDailyConfig() {
        return dailyConfig;
    }

    @EventHandler
    public void onReload(HavenShardsReloadEvent event) {
        loadConfig();
    }
}
