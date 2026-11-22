package com.haven.shards.manager;

import com.haven.shards.HavenShards;
import com.haven.shards.api.event.HavenShardsReloadEvent;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SoundManager implements Listener {

    public record CachedSound(boolean enabled, Sound bukkitSound, String customSound, float volume, float pitch) {}

    private final HavenShards plugin;
    private File configFile;
    private FileConfiguration config;
    private final Map<String, CachedSound> soundCache = new ConcurrentHashMap<>();

    public SoundManager(HavenShards plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        if (configFile == null) {
            configFile = new File(plugin.getDataFolder(), "sounds.yml");
        }
        if (!configFile.exists()) {
            plugin.saveResource("sounds.yml", false);
        }
        com.haven.shards.util.ConfigUpdater.update(plugin, "sounds.yml", configFile);
        config = YamlConfiguration.loadConfiguration(configFile);

        InputStream defaultStream = plugin.getResource("sounds.yml");
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            config.setDefaults(defaultConfig);
        }

        cacheSounds();
    }

    private void cacheSounds() {
        soundCache.clear();
        if (config == null) return;
        ConfigurationSection soundsSec = config.getConfigurationSection("sounds");
        if (soundsSec == null) return;

        for (String key : soundsSec.getKeys(false)) {
            String basePath = "sounds." + key;
            boolean enabled = config.getBoolean(basePath + ".enabled", true);
            if (!enabled) {
                soundCache.put(key.toLowerCase(), new CachedSound(false, null, null, 1f, 1f));
                continue;
            }

            String soundName = config.getString(basePath + ".sound");
            if (soundName == null || soundName.isEmpty()) {
                soundCache.put(key.toLowerCase(), new CachedSound(false, null, null, 1f, 1f));
                continue;
            }

            float volume = (float) config.getDouble(basePath + ".volume", 1.0);
            float pitch = (float) config.getDouble(basePath + ".pitch", 1.0);

            Sound bukkitSound = null;
            try {
                bukkitSound = Sound.valueOf(soundName.toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // Not a Bukkit Enum sound - treated as custom resource pack sound
            }

            String customSound = bukkitSound == null ? soundName.toLowerCase() : null;
            soundCache.put(key.toLowerCase(), new CachedSound(true, bukkitSound, customSound, volume, pitch));
        }
    }

    public CachedSound getCachedSound(String key) {
        if (key == null) return null;
        CachedSound cs = soundCache.get(key.toLowerCase());
        if (cs != null) return cs;
        if (config == null) return null;
        String basePath = "sounds." + key;
        if (!config.contains(basePath)) return null;
        boolean enabled = config.getBoolean(basePath + ".enabled", true);
        if (!enabled) {
            cs = new CachedSound(false, null, null, 1f, 1f);
            soundCache.put(key.toLowerCase(), cs);
            return cs;
        }
        String soundName = config.getString(basePath + ".sound");
        if (soundName == null || soundName.isEmpty()) {
            cs = new CachedSound(false, null, null, 1f, 1f);
            soundCache.put(key.toLowerCase(), cs);
            return cs;
        }
        float volume = (float) config.getDouble(basePath + ".volume", 1.0);
        float pitch = (float) config.getDouble(basePath + ".pitch", 1.0);
        Sound bukkitSound = null;
        try {
            bukkitSound = Sound.valueOf(soundName.toUpperCase());
        } catch (IllegalArgumentException ignored) {}
        cs = new CachedSound(true, bukkitSound, bukkitSound == null ? soundName.toLowerCase() : null, volume, pitch);
        soundCache.put(key.toLowerCase(), cs);
        return cs;
    }

    public FileConfiguration getSoundsConfig() {
        return config;
    }

    @EventHandler
    public void onReload(HavenShardsReloadEvent event) {
        loadConfig();
    }
}
