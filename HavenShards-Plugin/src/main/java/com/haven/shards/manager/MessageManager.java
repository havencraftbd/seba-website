package com.haven.shards.manager;

import com.haven.shards.HavenShards;
import com.haven.shards.api.event.HavenShardsReloadEvent;
import com.haven.shards.util.ColorUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MessageManager implements Listener {

    private final HavenShards plugin;
    private File configFile;
    private FileConfiguration config;
    private final Map<String, String> messageCache = new ConcurrentHashMap<>();
    private final Map<String, List<String>> messageListCache = new ConcurrentHashMap<>();

    public MessageManager(HavenShards plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        if (configFile == null) {
            configFile = new File(plugin.getDataFolder(), "messages.yml");
        }
        if (!configFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        com.haven.shards.util.ConfigUpdater.update(plugin, "messages.yml", configFile);
        config = YamlConfiguration.loadConfiguration(configFile);

        InputStream defaultStream = plugin.getResource("messages.yml");
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            config.setDefaults(defaultConfig);
        }

        cacheMessages();
    }

    private void cacheMessages() {
        messageCache.clear();
        messageListCache.clear();
        if (config == null) return;

        for (String key : config.getKeys(true)) {
            if (config.isString(key)) {
                String raw = config.getString(key);
                if (raw != null) {
                    messageCache.put(key, ColorUtil.colorize(raw));
                }
            } else if (config.isList(key)) {
                List<String> list = config.getStringList(key);
                List<String> coloredList = list.stream()
                        .map(ColorUtil::colorize)
                        .toList();
                messageListCache.put(key, coloredList);
            }
        }
    }

    public String getMessage(String key) {
        if (key == null) return "";
        String cached = messageCache.get(key);
        if (cached != null) return cached;

        String raw = config != null ? config.getString(key) : null;
        if (raw == null) return key;

        String colored = ColorUtil.colorize(raw);
        messageCache.put(key, colored);
        return colored;
    }

    public List<String> getMessageList(String path) {
        if (path == null) return Collections.emptyList();
        List<String> cached = messageListCache.get(path);
        if (cached != null) return cached;

        if (config == null) return Collections.emptyList();
        List<String> list = config.getStringList(path);
        if (list.isEmpty()) return Collections.emptyList();

        List<String> colored = list.stream()
                .map(ColorUtil::colorize)
                .toList();
        messageListCache.put(path, colored);
        return colored;
    }

    @EventHandler
    public void onReload(HavenShardsReloadEvent event) {
        loadConfig();
    }

    public FileConfiguration getMessagesConfig() {
        return config;
    }
}
