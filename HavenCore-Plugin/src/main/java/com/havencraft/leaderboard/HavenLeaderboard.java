package com.havencraft.leaderboard;

import com.havencraft.leaderboard.commands.LeaderboardCommand;
import com.havencraft.leaderboard.config.ConfigManager;
import com.havencraft.leaderboard.data.RankingManager;
import com.havencraft.leaderboard.gui.GUIManager;
import com.havencraft.leaderboard.listeners.InventoryListener;
import com.havencraft.leaderboard.providers.PapiProvider;
import com.havencraft.leaderboard.providers.VaultProvider;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class HavenLeaderboard {

    private final JavaPlugin javaPlugin;

    private ConfigManager configManager;
    private RankingManager rankingManager;
    private GUIManager guiManager;
    private InventoryListener inventoryListener;

    private VaultProvider vaultProvider;
    private PapiProvider papiProvider;

    public HavenLeaderboard(JavaPlugin javaPlugin) {
        this.javaPlugin = javaPlugin;
    }

    public void onEnable() {
        // Initialize config
        this.configManager = new ConfigManager(this);
        this.configManager.loadConfigs();

        // Check soft-depends
        if (Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            this.vaultProvider = new VaultProvider(this);
        }

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            this.papiProvider = new PapiProvider();
        }

        // Initialize Managers
        this.guiManager = new GUIManager(this);
        this.rankingManager = new RankingManager(this);

        // Register permissions at runtime for LuckPerms visibility
        org.bukkit.plugin.PluginManager pm = javaPlugin.getServer().getPluginManager();
        if (pm.getPermission("leaderboard.use") == null) {
            pm.addPermission(new org.bukkit.permissions.Permission("leaderboard.use",
                    "Allows players to open the leaderboard menu", org.bukkit.permissions.PermissionDefault.TRUE));
        }
        if (pm.getPermission("leaderboard.reload") == null) {
            pm.addPermission(new org.bukkit.permissions.Permission("leaderboard.reload",
                    "Allows admins to reload the plugin configuration", org.bukkit.permissions.PermissionDefault.OP));
        }

        // Register events
        this.inventoryListener = new InventoryListener(this);
        javaPlugin.getServer().getPluginManager().registerEvents(this.inventoryListener, javaPlugin);

        // Register commands securely
        org.bukkit.command.PluginCommand lbCommand = javaPlugin.getCommand("leaderboard");
        if (lbCommand != null) {
            lbCommand.setExecutor(new LeaderboardCommand(this));
        } else {
            javaPlugin.getLogger().severe("Failed to register /leaderboard command! Please check plugin.yml");
        }

        // Start caching task
        this.rankingManager.startTask();
    }

    public void onDisable() {
        if (this.rankingManager != null) {
            this.rankingManager.stopTask();
        }
        if (this.inventoryListener != null) {
            org.bukkit.event.HandlerList.unregisterAll(this.inventoryListener);
        }
    }

    public void reload() {
        onDisable();
        onEnable();
    }

    public JavaPlugin getJavaPlugin() {
        return javaPlugin;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public RankingManager getRankingManager() {
        return rankingManager;
    }

    public GUIManager getGuiManager() {
        return guiManager;
    }

    public VaultProvider getVaultProvider() {
        return vaultProvider;
    }

    public PapiProvider getPapiProvider() {
        return papiProvider;
    }
}
