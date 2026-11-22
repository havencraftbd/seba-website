package com.havencraft.havencore;

import com.havencraft.havencore.combat.CombatListener;
import com.havencraft.havencore.combat.CombatManager;
import com.havencraft.havencore.stats.CentralStatsCache;
import com.havencraft.havencore.stats.StatsListener;
import com.havencraft.havensync.HavenSyncPlugin;
import com.havencraft.leaderboard.HavenLeaderboard;
import com.tchristofferson.configupdater.ConfigUpdater;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import com.havencraft.havencore.adminhome.AdminHomeCommand;
import com.havencraft.havencore.adminhome.AdminHomeListener;

import java.io.File;
import java.io.IOException;

public class HavenCorePlugin extends JavaPlugin {

    private static HavenCorePlugin instance;
    private CentralStatsCache statsCache;
    private com.havencraft.havencore.stats.StatsGUI statsGUI;

    private HavenSyncPlugin havenSyncModule;
    private HavenLeaderboard leaderboardModule;
    private com.havencraft.havensettings.HavenSettings settingsModule;
    private com.havencraft.havenpunish.HavenPunish punishModule;
    private CombatManager combatManager;
    private com.havencraft.havencore.security.OpSecurityManager opSecurityManager;
    private com.havencraft.havencore.security.TeamEchestSecurityManager teamEchestSecurityManager;
    private com.havencraft.havencore.security.BetterTeamsHook betterTeamsHook;
    private com.havencraft.havencore.stats.HavenCoreExpansion havenCoreExpansion;

    public static HavenCorePlugin getInstance() {
        return instance;
    }

    public CentralStatsCache getStatsCache() {
        return statsCache;
    }

    public HavenSyncPlugin getHavenSyncModule() {
        return havenSyncModule;
    }

    public HavenLeaderboard getLeaderboardModule() {
        return leaderboardModule;
    }

    public CombatManager getCombatManager() {
        return combatManager;
    }

    public com.havencraft.havencore.security.OpSecurityManager getOpSecurityManager() {
        return opSecurityManager;
    }

    public com.havencraft.havencore.security.TeamEchestSecurityManager getTeamEchestSecurityManager() {
        return teamEchestSecurityManager;
    }

    public com.havencraft.havencore.security.BetterTeamsHook getBetterTeamsHook() {
        return betterTeamsHook;
    }

    /**
     * Gets the centralized global plugin prefix from core.prefix in config.yml.
     */
    public String getGlobalPrefix() {
        return getConfig().getString("core.prefix", "&#00E5FF&lHavenCraft &#888888» ");
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        File guideFile = new File(getDataFolder(), "guide.yml");
        if (!guideFile.exists()) {
            saveResource("guide.yml", false);
        }
        try {
            ConfigUpdater.update(this, "config.yml", new File(getDataFolder(), "config.yml"));
        } catch (IOException e) {
            getLogger().warning("Could not update config.yml: " + e.getMessage());
        }
        reloadConfig();

        // Initialize BetterTeams Hook
        this.betterTeamsHook = new com.havencraft.havencore.security.BetterTeamsHook(this);

        // Initialize OP Security Manager & Listener
        this.opSecurityManager = new com.havencraft.havencore.security.OpSecurityManager(this);
        getServer().getPluginManager().registerEvents(new com.havencraft.havencore.security.OpSecurityListener(opSecurityManager), this);

        // Initialize Team Echest Anti-Dupe Security Manager & Listener
        this.teamEchestSecurityManager = new com.havencraft.havencore.security.TeamEchestSecurityManager(this);
        getServer().getPluginManager().registerEvents(new com.havencraft.havencore.security.TeamEchestSecurityListener(teamEchestSecurityManager), this);

        // Register /opauth command
        PluginCommand opauthCmd = getCommand("opauth");
        if (opauthCmd != null) {
            com.havencraft.havencore.security.OpAuthCommand opAuthExecutor = new com.havencraft.havencore.security.OpAuthCommand(opSecurityManager);
            opauthCmd.setExecutor(opAuthExecutor);
            opauthCmd.setTabCompleter(opAuthExecutor);
        }

        // Initialize and enable all internal submodules synchronously without arbitrary delays
        initializeAndEnableModules();

        // Register /havencore command instantly
        PluginCommand coreCmd = getCommand("havencore");
        if (coreCmd != null) {
            coreCmd.setExecutor(new HavenCoreCommand(this));
        }

        // Register /ahome command
        AdminHomeListener adminHomeListener = new AdminHomeListener(this);
        getServer().getPluginManager().registerEvents(adminHomeListener, this);
        PluginCommand ahomeCmd = getCommand("ahome");
        if (ahomeCmd != null) {
            AdminHomeCommand adminHomeExecutor = new AdminHomeCommand(this, adminHomeListener);
            ahomeCmd.setExecutor(adminHomeExecutor);
            ahomeCmd.setTabCompleter(adminHomeExecutor);
        }

        // Register /stats command (now guaranteed to have statsCache initialized)
        this.statsGUI = new com.havencraft.havencore.stats.StatsGUI(this);
        PluginCommand statsCmd = getCommand("stats");
        if (statsCmd != null) {
            com.havencraft.havencore.stats.StatsCommand statsExecutor = new com.havencraft.havencore.stats.StatsCommand(this, this.statsGUI);
            statsCmd.setExecutor(statsExecutor);
        }
    }

    private void initializeAndEnableModules() {
        // Initialize Central Cache first
        this.statsCache = new CentralStatsCache(this);
        getServer().getPluginManager().registerEvents(new StatsListener(statsCache), this);
        this.statsCache.startTask();

        // Initialize modules
        this.havenSyncModule = new HavenSyncPlugin(this);
        this.leaderboardModule = new HavenLeaderboard(this);
        this.settingsModule = new com.havencraft.havensettings.HavenSettings(this);
        this.punishModule = new com.havencraft.havenpunish.HavenPunish(this);

        this.havenSyncModule.onEnable();
        this.leaderboardModule.onEnable();
        this.settingsModule.onEnable();
        this.punishModule.onEnable();

        this.combatManager = new CombatManager(this);
        getServer().getPluginManager().registerEvents(new CombatListener(this.combatManager), this);
        this.combatManager.startTask();

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            this.havenCoreExpansion = new com.havencraft.havencore.stats.HavenCoreExpansion(this);
            this.havenCoreExpansion.register();
        }

        getLogger().info("All HavenCore modules initialized and enabled successfully!");
    }

    @Override
    public void onDisable() {
        if (this.havenCoreExpansion != null) {
            try {
                this.havenCoreExpansion.unregister();
            } catch (Throwable ignored) {}
            this.havenCoreExpansion = null;
        }
        if (this.statsCache != null) {
            this.statsCache.close();
        }
        if (this.combatManager != null) {
            this.combatManager.stopTask();
        }
        if (this.havenSyncModule != null) {
            this.havenSyncModule.onDisable();
        }
        if (this.leaderboardModule != null) {
            this.leaderboardModule.onDisable();
        }
        if (this.settingsModule != null) {
            this.settingsModule.onDisable();
        }
        if (this.punishModule != null) {
            this.punishModule.onDisable();
        }
    }

    /**
     * Reloads all module configs. Called by /havencore reload.
     */
    public void reloadAllModules() {
        try {
            reloadConfig();
        } catch (Throwable t) {
            getLogger().severe("Error reloading main config.yml: " + t.getMessage());
        }
        if (this.statsGUI != null) {
            try { this.statsGUI.reloadConfig(); } catch (Throwable t) { getLogger().severe("Error reloading statsGUI: " + t.getMessage()); }
        }
        if (this.opSecurityManager != null) {
            try { this.opSecurityManager.reload(); } catch (Throwable t) { getLogger().severe("Error reloading opSecurityManager: " + t.getMessage()); }
        }
        if (this.teamEchestSecurityManager != null) {
            try { this.teamEchestSecurityManager.reload(); } catch (Throwable t) { getLogger().severe("Error reloading teamEchestSecurityManager: " + t.getMessage()); }
        }
        if (this.havenSyncModule != null) {
            try { this.havenSyncModule.reload(); } catch (Throwable t) { getLogger().severe("Error reloading havenSyncModule: " + t.getMessage()); }
        }
        if (this.leaderboardModule != null) {
            try { this.leaderboardModule.reload(); } catch (Throwable t) { getLogger().severe("Error reloading leaderboardModule: " + t.getMessage()); }
        }
        if (this.settingsModule != null) {
            try { this.settingsModule.reload(); } catch (Throwable t) { getLogger().severe("Error reloading settingsModule: " + t.getMessage()); }
        }
        if (this.punishModule != null) {
            try { this.punishModule.reload(); } catch (Throwable t) { getLogger().severe("Error reloading punishModule: " + t.getMessage()); }
        }
        if (this.combatManager != null) {
            try { this.combatManager.reload(); } catch (Throwable t) { getLogger().severe("Error reloading combatManager: " + t.getMessage()); }
        }
    }
}
