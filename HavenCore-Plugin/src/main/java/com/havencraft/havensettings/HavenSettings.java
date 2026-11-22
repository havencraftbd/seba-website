package com.havencraft.havensettings;

import com.havencraft.havencore.util.ConfigLoader;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.logging.Logger;

/**
 * HavenSettings Module — Player settings GUI with toggle system.
 * Integrates with HavenCore as a module (not a standalone plugin).
 */
public final class HavenSettings {

    private final JavaPlugin plugin;
    private SettingsManager settingsManager;
    private SettingsGUI settingsGUI;

    private PlayerListener playerListener;
    private CommandListener commandListener;
    private ItemRestrictionListener itemRestrictionListener;
    private TpaMessageListener tpaMessageListener;

    public HavenSettings(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void onEnable() {
        Logger logger = plugin.getLogger();

        // Migrate legacy/corrupted tpa-world-warning schema before ConfigUpdater loads it
        File settingsConfigFile = new File(plugin.getDataFolder(), "HavenSettings/config.yml");
        HavenSettingsConfigMigrator.migrateIfNeeded(settingsConfigFile, logger);

        // Load config files (ignore custom worlds map only when already present)
        java.util.List<String> ignoredConfigSections = HavenSettingsConfigMigrator.hasTpaWarningWorldsSection(settingsConfigFile)
                ? Collections.singletonList("tpa-world-warning.worlds")
                : Collections.emptyList();
        File guiFile = new File(plugin.getDataFolder(), "HavenSettings/gui.yml");
        HavenSettingsConfigMigrator.migrateGuiIfNeeded(guiFile, logger);

        FileConfiguration config = ConfigLoader.load(plugin, "HavenSettings/config.yml", ignoredConfigSections);
        FileConfiguration guiConfig = ConfigLoader.load(plugin, "HavenSettings/gui.yml", Collections.singletonList("buttons"));
        FileConfiguration messagesConfig = ConfigLoader.load(plugin, "HavenSettings/messages.yml");
        FileConfiguration customCmdConfig = ConfigLoader.load(plugin, "HavenSettings/custom-commands.yml", Collections.singletonList("custom-commands"));

        String consolePrefix = messagesConfig.getString("console-prefix", "HavenSettings •");

        if (!config.getBoolean("enabled", true)) {
            logger.info("[" + consolePrefix + "] Module disabled in config.");
            return;
        }

        // Initialize manager
        this.settingsManager = new SettingsManager(plugin, config, customCmdConfig);

        // Initialize GUI
        this.settingsGUI = new SettingsGUI(plugin, settingsManager, guiConfig, messagesConfig, customCmdConfig);

        this.playerListener = new PlayerListener(settingsManager, settingsGUI);
        this.commandListener = new CommandListener(plugin, settingsManager, messagesConfig, customCmdConfig);
        this.itemRestrictionListener = new ItemRestrictionListener(plugin, config);
        this.tpaMessageListener = new TpaMessageListener(plugin, settingsManager, messagesConfig);

        // Register listeners
        Bukkit.getPluginManager().registerEvents(settingsGUI, plugin);
        Bukkit.getPluginManager().registerEvents(playerListener, plugin);
        Bukkit.getPluginManager().registerEvents(commandListener, plugin);
        Bukkit.getPluginManager().registerEvents(itemRestrictionListener, plugin);
        Bukkit.getPluginManager().registerEvents(tpaMessageListener, plugin);

        // Register command
        PluginCommand cmd = plugin.getCommand("settings");
        if (cmd != null) {
            cmd.setExecutor(new SettingsCommand(this, settingsGUI, messagesConfig));
        } else {
            logger.warning("[" + consolePrefix + "] Command 'settings' not found in plugin.yml!");
        }

        PluginCommand nvCmd = plugin.getCommand("nv");
        if (nvCmd != null) {
            nvCmd.setExecutor(new NightVisionCommand(settingsManager, settingsGUI, messagesConfig));
        } else {
            logger.warning("[" + consolePrefix + "] Command 'nv' not found in plugin.yml!");
        }

        PluginCommand sbCmd = plugin.getCommand("sb");
        if (sbCmd != null) {
            sbCmd.setExecutor(new ScoreboardCommand(settingsManager, messagesConfig));
        } else {
            logger.warning("[" + consolePrefix + "] Command 'sb' not found in plugin.yml!");
        }

        // Load settings for all online players (in case of /reload)
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerSettings ps = settingsManager.loadPlayer(player.getUniqueId());
            if (ps.isNightVisionEnabled()) {
                settingsGUI.applyNV(player);
            }
            settingsManager.applyExternalStates(player.getUniqueId());
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    settingsManager.applyScoreboard(player.getUniqueId(), ps.isScoreboardEnabled());
                }
            }, 40L);
        }

        logger.info("[" + consolePrefix + "] Module enabled successfully!");
    }

    public void onDisable() {
        if (settingsManager != null) {
            settingsManager.saveAll();
            settingsManager.closeDatabase();
        }
        if (settingsGUI != null) {
            settingsGUI.closeAll();
        }
    }

    public void reload() {
        onDisable();
        
        if (settingsGUI != null) {
            org.bukkit.event.HandlerList.unregisterAll(settingsGUI);
        }
        if (playerListener != null) {
            org.bukkit.event.HandlerList.unregisterAll(playerListener);
        }
        if (commandListener != null) {
            org.bukkit.event.HandlerList.unregisterAll(commandListener);
        }
        if (itemRestrictionListener != null) {
            org.bukkit.event.HandlerList.unregisterAll(itemRestrictionListener);
        }
        if (tpaMessageListener != null) {
            org.bukkit.event.HandlerList.unregisterAll(tpaMessageListener);
        }

        onEnable();
    }

    /**
     * Returns the SettingsService API for other modules.
     */
    public SettingsService getService() {
        return settingsManager;
    }

}
