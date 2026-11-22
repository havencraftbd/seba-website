package com.havencraft.havensettings;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.Bukkit;

/**
 * Manages per-player settings: load, save, toggle.
 * Thread-safe via ConcurrentHashMap for the cache.
 * All file I/O happens on the main thread to match Bukkit API conventions.
 */
public class SettingsManager implements SettingsService {

    private final JavaPlugin plugin;
    private final SettingsDatabase database;
    private final Map<UUID, PlayerSettings> cache = new ConcurrentHashMap<>();

    // Default values from config.yml
    private boolean defaultMsg = true;
    private boolean defaultTpa = true;
    private boolean defaultTpaHere = false;
    private boolean defaultTpaConfirmation = true;
    private boolean defaultScoreboard = true;
    private boolean defaultNightVision = false;
    private boolean defaultPublicChat = true;
    private boolean customTpaMessagesEnabled = true;
    private boolean tpaPortalProtectionEnabled = true;
    private double tpaPortalProtectionRadius = TpaMessageListener.DEFAULT_PORTAL_CHECK_RADIUS;

    // TPA World Warning settings
    private TpaWorldWarningConfig tpaWorldWarningConfig = TpaWorldWarningConfig.fromConfig(null);
    private boolean tabApiUnavailableWarned = false;

    // Custom command defaults: commandId → default state
    private final Map<String, Boolean> customCommandDefaults = new LinkedHashMap<>();
    private final Map<String, String> customCommandPermissions = new LinkedHashMap<>();
    private boolean customCommandsEnabled = false;

    public SettingsManager(JavaPlugin plugin, FileConfiguration config, FileConfiguration customCmdConfig) {
        this.plugin = plugin;
        this.database = new SettingsDatabase(plugin);
        this.database.connect();

        loadDefaults(config);
        loadCustomCommandDefaults(customCmdConfig);
    }

    public void closeDatabase() {
        database.close();
    }

    private void loadDefaults(FileConfiguration config) {
        this.defaultMsg = config.getBoolean("defaults.private-message", true);
        this.defaultTpa = config.getBoolean("defaults.tpa", true);
        this.defaultTpaHere = config.getBoolean("defaults.tpahere", false);
        this.defaultTpaConfirmation = config.getBoolean("defaults.tpa-confirmation", true);
        this.defaultScoreboard = config.getBoolean("defaults.scoreboard", true);
        this.defaultNightVision = config.getBoolean("defaults.night-vision", false);
        this.defaultPublicChat = config.getBoolean("defaults.public-chat", true);
        this.customTpaMessagesEnabled = config.getBoolean("custom-tpa-messages.enabled", true);
        this.tpaPortalProtectionEnabled = config.getBoolean("tpa-portal-protection.enabled", true);
        this.tpaPortalProtectionRadius = TpaMessageListener.sanitizePortalRadius(
                config.getDouble("tpa-portal-protection.radius", TpaMessageListener.DEFAULT_PORTAL_CHECK_RADIUS)
        );
        this.tpaWorldWarningConfig = TpaWorldWarningConfig.fromConfig(config);
    }

    public boolean isCustomTpaMessagesEnabled() {
        return customTpaMessagesEnabled;
    }

    public boolean isTpaPortalProtectionEnabled() {
        return tpaPortalProtectionEnabled;
    }

    public double getTpaPortalProtectionRadius() {
        return tpaPortalProtectionRadius;
    }

    public boolean isTpaWorldWarningEnabled() {
        return tpaWorldWarningConfig.isEnabled();
    }

    public String getTpaWorldWarningMode() {
        return tpaWorldWarningConfig.getMode();
    }

    public boolean isTpaWarningWorld(String worldName) {
        return tpaWorldWarningConfig.isWarnedWorld(worldName);
    }

    public String getTpaWorldDisplayName(String worldName) {
        return tpaWorldWarningConfig.getWorldDisplayName(worldName);
    }

    public boolean isTpaWarningApplyToTpaHere() {
        return tpaWorldWarningConfig.isTpaHerePopupEnabled();
    }

    public boolean isTpaWarningApplyToTpa() {
        return tpaWorldWarningConfig.isTpaPopupEnabled();
    }

    private void loadCustomCommandDefaults(FileConfiguration customCmdConfig) {
        customCommandDefaults.clear();
        customCommandPermissions.clear();
        customCommandsEnabled = customCmdConfig.getBoolean("enabled", false);
        if (!customCommandsEnabled) return;

        ConfigurationSection section = customCmdConfig.getConfigurationSection("custom-commands");
        if (section == null) return;

        for (String id : section.getKeys(false)) {
            String permission = section.getString(id + ".permission", "").trim();
            if (permission.isEmpty()) {
                plugin.getLogger().warning("[HavenSettings] Skipping custom command '" + id + "' because permission is missing.");
                continue;
            }
            boolean def = section.getBoolean(id + ".default", false);
            customCommandDefaults.put(id, def);
            customCommandPermissions.put(id, permission);
        }
    }

    // ━━━━━━━━━━━━━━━ Load / Save ━━━━━━━━━━━━━━━

    /**
     * Loads player settings from YAML. Called on PlayerJoinEvent.
     */
    public PlayerSettings loadPlayer(UUID uuid) {
        PlayerSettings cached = cache.get(uuid);
        if (cached != null) return cached;

        PlayerSettings settings = database.loadPlayer(uuid, this);
        cache.put(uuid, settings);
        return settings;
    }

    /**
     * Saves player settings asynchronously.
     */
    public void savePlayer(UUID uuid) {
        savePlayer(uuid, true);
    }

    /**
     * Saves player settings with option for async/sync execution.
     */
    public void savePlayer(UUID uuid, boolean async) {
        PlayerSettings settings = cache.get(uuid);
        if (settings == null) return;

        if (async && plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                database.savePlayer(settings);
            });
        } else {
            database.savePlayer(settings);
        }
    }

    /**
     * Called on player quit — save synchronously and remove from cache.
     */
    public void unloadPlayer(UUID uuid) {
        savePlayer(uuid, false);
        cache.remove(uuid);
    }

    /**
     * Saves all cached players synchronously (called on disable).
     */
    public void saveAll() {
        for (UUID uuid : cache.keySet()) {
            savePlayer(uuid, false);
        }
        cache.clear();
    }

    // ━━━━━━━━━━━━━━━ Toggle ━━━━━━━━━━━━━━━

    public boolean toggleMsg(UUID uuid) {
        PlayerSettings s = getOrLoad(uuid);
        s.setMsgEnabled(!s.isMsgEnabled());
        savePlayer(uuid);
        
        // Force Essentials to allow, so HavenSettings handles it
        com.earth2me.essentials.User user = getEssentialsUser(uuid);
        if (user != null) {
            user.setIgnoreMsg(false);
        }
        
        return s.isMsgEnabled();
    }

    public boolean toggleTpa(UUID uuid) {
        PlayerSettings s = getOrLoad(uuid);
        s.setTpaEnabled(!s.isTpaEnabled());
        if (!s.isTpaEnabled()) {
            s.setTpaHereEnabled(false);
        }
        savePlayer(uuid);
        
        // Force Essentials to allow (so HavenSettings handles it) if custom TPA messages are enabled,
        // otherwise synchronize with the player's TPA setting.
        com.earth2me.essentials.User user = getEssentialsUser(uuid);
        if (user != null) {
            if (customTpaMessagesEnabled) {
                user.setTeleportEnabled(true);
            } else {
                user.setTeleportEnabled(s.isTpaEnabled());
            }
        }
        
        return s.isTpaEnabled();
    }

    public boolean toggleTpaHere(UUID uuid) {
        PlayerSettings s = getOrLoad(uuid);
        s.setTpaHereEnabled(!s.isTpaHereEnabled());
        savePlayer(uuid);
        return s.isTpaHereEnabled();
    }

    public boolean toggleTpaConfirmation(UUID uuid) {
        PlayerSettings s = getOrLoad(uuid);
        s.setTpaConfirmationEnabled(!s.isTpaConfirmationEnabled());
        savePlayer(uuid);
        return s.isTpaConfirmationEnabled();
    }

    public boolean toggleScoreboard(UUID uuid) {
        PlayerSettings s = getOrLoad(uuid);
        s.setScoreboardEnabled(!s.isScoreboardEnabled());
        savePlayer(uuid);
        applyScoreboard(uuid, s.isScoreboardEnabled());
        return s.isScoreboardEnabled();
    }

    public boolean toggleNightVision(UUID uuid) {
        PlayerSettings s = getOrLoad(uuid);
        s.setNightVisionEnabled(!s.isNightVisionEnabled());
        savePlayer(uuid);
        return s.isNightVisionEnabled();
    }

    public boolean togglePublicChat(UUID uuid) {
        PlayerSettings s = getOrLoad(uuid);
        s.setPublicChatEnabled(!s.isPublicChatEnabled());
        applyEssentialsPublicChat(uuid, s);
        savePlayer(uuid);
        return s.isPublicChatEnabled();
    }

    public boolean toggleCustomCommand(UUID uuid, String commandId) {
        if (!customCommandsEnabled || !customCommandDefaults.containsKey(commandId)) {
            return false;
        }
        PlayerSettings s = getOrLoad(uuid);
        boolean current = s.isCustomCommandEnabled(commandId);
        s.setCustomCommand(commandId, !current);
        savePlayer(uuid);
        return !current;
    }

    // ━━━━━━━━━━━━━━━ SettingsService API ━━━━━━━━━━━━━━━

    @Override
    public boolean isMsgEnabled(UUID uuid) {
        PlayerSettings s = cache.get(uuid);
        return s != null ? s.isMsgEnabled() : defaultMsg;
    }

    @Override
    public boolean isTpaEnabled(UUID uuid) {
        PlayerSettings s = cache.get(uuid);
        return s != null ? s.isTpaEnabled() : defaultTpa;
    }

    @Override
    public boolean isTpaHereEnabled(UUID uuid) {
        PlayerSettings s = cache.get(uuid);
        return s != null ? s.isTpaHereEnabled() : defaultTpaHere;
    }

    @Override
    public boolean isTpaConfirmationEnabled(UUID uuid) {
        PlayerSettings s = cache.get(uuid);
        return s != null ? s.isTpaConfirmationEnabled() : defaultTpaConfirmation;
    }

    public boolean getDefaultTpaConfirmation() {
        return defaultTpaConfirmation;
    }

    @Override
    public boolean isScoreboardEnabled(UUID uuid) {
        PlayerSettings s = cache.get(uuid);
        return s != null ? s.isScoreboardEnabled() : defaultScoreboard;
    }

    @Override
    public boolean isNightVisionEnabled(UUID uuid) {
        PlayerSettings s = cache.get(uuid);
        return s != null ? s.isNightVisionEnabled() : defaultNightVision;
    }

    @Override
    public boolean isPublicChatEnabled(UUID uuid) {
        PlayerSettings s = cache.get(uuid);
        return s != null ? s.isPublicChatEnabled() : defaultPublicChat;
    }

    @Override
    public boolean isCustomCommandEnabled(UUID uuid, String commandId) {
        PlayerSettings s = cache.get(uuid);
        return s != null ? s.isCustomCommandEnabled(commandId) : customCommandDefaults.getOrDefault(commandId, false);
    }

    // ━━━━━━━━━━━━━━━ Helpers ━━━━━━━━━━━━━━━

    public PlayerSettings getOrLoad(UUID uuid) {
        PlayerSettings s = cache.get(uuid);
        return s != null ? s : loadPlayer(uuid);
    }

    public Map<String, Boolean> getCustomCommandDefaults() {
        return Collections.unmodifiableMap(customCommandDefaults);
    }

    public Map<String, String> getCustomCommandPermissions() {
        return Collections.unmodifiableMap(customCommandPermissions);
    }

    public boolean isCustomCommandsEnabled() {
        return customCommandsEnabled;
    }

    public PlayerSettings createDefaults(UUID uuid) {
        PlayerSettings s = new PlayerSettings(uuid);
        s.setMsgEnabled(defaultMsg);
        s.setTpaEnabled(defaultTpa);
        s.setTpaHereEnabled(defaultTpaHere);
        s.setTpaConfirmationEnabled(defaultTpaConfirmation);
        s.setScoreboardEnabled(defaultScoreboard);
        s.setNightVisionEnabled(defaultNightVision);
        s.setPublicChatEnabled(defaultPublicChat);
        s.setPublicChatMutedBySettings(false);
        for (Map.Entry<String, Boolean> entry : customCommandDefaults.entrySet()) {
            s.setCustomCommand(entry.getKey(), entry.getValue());
        }
        return s;
    }

    public void applyScoreboard(UUID uuid, boolean enabled) {
        if (!Bukkit.getPluginManager().isPluginEnabled("TAB")) {
            return;
        }
        try {
            me.neznamy.tab.api.TabAPI tabAPI = me.neznamy.tab.api.TabAPI.getInstance();
            me.neznamy.tab.api.scoreboard.ScoreboardManager sm = tabAPI.getScoreboardManager();
            if (sm != null) {
                me.neznamy.tab.api.TabPlayer tabPlayer = tabAPI.getPlayer(uuid);
                if (tabPlayer != null) {
                    sm.setScoreboardVisible(tabPlayer, enabled, true);
                }
            }
        } catch (IllegalStateException e) {
            if (!tabApiUnavailableWarned) {
                tabApiUnavailableWarned = true;
                String detail = e.getMessage() == null ? "null instance" : e.getMessage().split("\\R", 2)[0];
                plugin.getLogger().warning("[Settings] TAB plugin is enabled, but TabAPI is not ready ("
                        + detail + "). Check [TAB] startup logs for Paper compatibility.");
            }
        } catch (Exception | LinkageError e) {
            plugin.getLogger().warning("Failed to update TAB scoreboard: " + e.getMessage());
        }
    }

    public void applyEssentialsPublicChat(UUID uuid, PlayerSettings settings) {
        // No-op to prevent Essentials mute bypass exploits
    }

    public void applyExternalStates(UUID uuid) {
        PlayerSettings settings = getOrLoad(uuid);
        applyEssentialsPublicChat(uuid, settings);
        
        // If custom TPA messages are enabled, force Essentials internal toggle to always allow.
        // Otherwise, sync EssentialsX teleport status with player settings.
        com.earth2me.essentials.User user = getEssentialsUser(uuid);
        if (user != null) {
            try {
                if (customTpaMessagesEnabled) {
                    user.setTeleportEnabled(true);
                } else {
                    user.setTeleportEnabled(settings.isTpaEnabled());
                }
                user.setIgnoreMsg(false);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to reset Essentials state for " + uuid + ": " + e.getMessage());
            }
        }
    }

    public com.earth2me.essentials.User getEssentialsUser(UUID uuid) {
        if (!Bukkit.getPluginManager().isPluginEnabled("Essentials")) {
            return null;
        }
        try {
            com.earth2me.essentials.Essentials ess =
                    (com.earth2me.essentials.Essentials) Bukkit.getPluginManager().getPlugin("Essentials");
            if (ess == null) return null;
            return ess.getUser(uuid);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to access Essentials user: " + e.getMessage());
            return null;
        }
    }
}
