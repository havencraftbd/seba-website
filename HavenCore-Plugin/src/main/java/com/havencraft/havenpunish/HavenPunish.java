package com.havencraft.havenpunish;

import com.havencraft.havencore.util.ConfigLoader;
import com.havencraft.havenpunish.gui.*;
import com.havencraft.havenpunish.manager.PunishExecutor;
import com.havencraft.havenpunish.manager.SessionManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.logging.Logger;

/**
 * HavenPunish Module — GUI-based punishment system integrated with LiteBans.
 * Integrates with HavenCore as a module (not a standalone plugin).
 */
public final class HavenPunish {

    private final JavaPlugin plugin;
    private SessionManager sessionManager;
    private PunishExecutor executor;

    private PunishMainGUI mainGUI;
    private DurationGUI durationGUI;
    private ReasonGUI reasonGUI;
    private ConfirmGUI confirmGUI;

    private PunishClickListener clickListener;
    private PunishChatListener chatListener;

    public HavenPunish(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public JavaPlugin getJavaPlugin() {
        return plugin;
    }

    public void onEnable() {
        Logger logger = plugin.getLogger();

        // Load config files
        FileConfiguration config = ConfigLoader.load(plugin, "HavenPunish/config.yml");
        FileConfiguration guiConfig = ConfigLoader.load(plugin, "HavenPunish/gui.yml", Collections.singletonList("duration-menu.presets"));
        FileConfiguration reasonsConfig = ConfigLoader.load(plugin, "HavenPunish/reasons.yml", Collections.singletonList("reasons"));
        FileConfiguration messagesConfig = ConfigLoader.load(plugin, "HavenPunish/messages.yml");

        String consolePrefix = messagesConfig.getString("console-prefix", "HavenPunish •");

        if (!config.getBoolean("enabled", true)) {
            logger.info("[" + consolePrefix + "] Module disabled in config.");
            return;
        }

        // Initialize managers
        this.sessionManager = new SessionManager();
        this.executor = new PunishExecutor(plugin, sessionManager, messagesConfig, config.getInt("cooldown", 3));

        // Initialize GUIs
        this.mainGUI = new PunishMainGUI(plugin, guiConfig, sessionManager);
        this.durationGUI = new DurationGUI(guiConfig, sessionManager);
        this.reasonGUI = new ReasonGUI(guiConfig, reasonsConfig, sessionManager);
        this.confirmGUI = new ConfirmGUI(guiConfig, messagesConfig, sessionManager, executor);

        // Initialize listeners
        this.clickListener = new PunishClickListener(plugin, guiConfig, sessionManager,
                mainGUI, durationGUI, reasonGUI, confirmGUI);
        this.chatListener = new PunishChatListener(plugin, sessionManager,
                durationGUI, reasonGUI, confirmGUI);

        // Register listeners
        Bukkit.getPluginManager().registerEvents(clickListener, plugin);
        Bukkit.getPluginManager().registerEvents(chatListener, plugin);
        Bukkit.getPluginManager().registerEvents(executor, plugin);

        // Register command
        PluginCommand cmd = plugin.getCommand("punish");
        if (cmd != null) {
            PunishCommand punishCmd = new PunishCommand(this, mainGUI, messagesConfig, config);
            cmd.setExecutor(punishCmd);
            cmd.setTabCompleter(punishCmd);
        } else {
            logger.warning("[" + consolePrefix + "] Command 'punish' not found in plugin.yml!");
        }

        logger.info("[" + consolePrefix + "] Module enabled successfully!");
    }

    public void onDisable() {
        if (mainGUI != null) mainGUI.closeAll();
        if (durationGUI != null) durationGUI.closeAll();
        if (reasonGUI != null) reasonGUI.closeAll();
        if (confirmGUI != null) confirmGUI.closeAll();
        if (sessionManager != null) sessionManager.clearAll();
    }

    public void reload() {
        onDisable();

        // Unregister listeners
        if (clickListener != null) {
            org.bukkit.event.HandlerList.unregisterAll(clickListener);
        }
        if (chatListener != null) {
            org.bukkit.event.HandlerList.unregisterAll(chatListener);
        }
        if (executor != null) {
            org.bukkit.event.HandlerList.unregisterAll(executor);
        }

        onEnable();
    }
}
