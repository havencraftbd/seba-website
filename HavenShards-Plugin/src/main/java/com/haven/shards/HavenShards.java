package com.haven.shards;

import com.haven.shards.command.ShardsCommand;
import com.haven.shards.gui.DailyQuestsGUI;
import com.haven.shards.gui.DailyRewardGUI;
import com.haven.shards.gui.ShardsMenuGUI;
import com.haven.shards.gui.TransactionHistoryGUI;
import com.haven.shards.hook.EconomyShopGUIHook;
import com.haven.shards.hook.EssentialsHook;
import com.haven.shards.hook.GeyserHook;
import com.haven.shards.hook.HavenShopHook;
import com.haven.shards.hook.PAPIExpansion;
import com.haven.shards.hook.VaultEconomyHook;
import com.haven.shards.listener.ItemRedeemListener;
import com.haven.shards.listener.QuestEventListener;
import com.haven.shards.listener.RewardEventListener;
import com.haven.shards.manager.BoosterManager;
import com.haven.shards.manager.DailyRewardManager;
import com.haven.shards.manager.DatabaseManager;
import com.haven.shards.manager.DiscordWebhookManager;
import com.haven.shards.manager.GUIManager;
import com.haven.shards.manager.MessageManager;
import com.haven.shards.manager.PlaytimeRewardManager;
import com.haven.shards.manager.QuestManager;
import com.haven.shards.manager.ShardManager;
import com.haven.shards.manager.SoundManager;
import com.haven.shards.manager.WithdrawPromptManager;
import com.haven.shards.util.ColorUtil;
import com.haven.shards.util.ConfigUpdater;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class HavenShards extends JavaPlugin {

    private static HavenShards instance;
    private DatabaseManager databaseManager;
    private ShardManager shardManager;
    private PlaytimeRewardManager playtimeRewardManager;
    private MessageManager messageManager;
    private SoundManager soundManager;
    private BoosterManager boosterManager;
    private DiscordWebhookManager discordWebhookManager;
    private GUIManager guiManager;
    private DailyRewardManager dailyRewardManager;
    private QuestManager questManager;
    private WithdrawPromptManager withdrawPromptManager;
    private GeyserHook geyserHook;
    private EssentialsHook essentialsHook;
    private PAPIExpansion papiExpansion;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        ConfigUpdater.update(this, "config.yml", new File(getDataFolder(), "config.yml"));
        reloadConfig();
        com.haven.shards.util.ItemUtil.reloadPattern(this);
        try {
            saveResource("GUIDE.txt", true);
        } catch (Exception e) {
            getLogger().warning("Could not save GUIDE.txt: " + e.getMessage());
        }

        // 1. Initialize Managers
        this.messageManager = new MessageManager(this);
        this.soundManager = new SoundManager(this);
        this.databaseManager = new DatabaseManager(this);
        this.shardManager = new ShardManager(this, databaseManager);
        this.boosterManager = new BoosterManager(this);
        this.playtimeRewardManager = new PlaytimeRewardManager(this);
        this.discordWebhookManager = new DiscordWebhookManager(this);
        this.guiManager = new GUIManager(this);
        this.dailyRewardManager = new DailyRewardManager(this);
        this.questManager = new QuestManager(this);
        this.withdrawPromptManager = new WithdrawPromptManager(this);

        // 2. Register Listeners & GUI
        getServer().getPluginManager().registerEvents(new ItemRedeemListener(this), this);
        getServer().getPluginManager().registerEvents(new RewardEventListener(this), this);
        getServer().getPluginManager().registerEvents(new QuestEventListener(this), this);
        getServer().getPluginManager().registerEvents(this.messageManager, this);
        getServer().getPluginManager().registerEvents(this.soundManager, this);
        getServer().getPluginManager().registerEvents(this.guiManager, this);
        getServer().getPluginManager().registerEvents(this.playtimeRewardManager, this);
        getServer().getPluginManager().registerEvents(this.dailyRewardManager, this);
        getServer().getPluginManager().registerEvents(this.questManager, this);
        getServer().getPluginManager().registerEvents(this.shardManager, this);
        getServer().getPluginManager().registerEvents(this.discordWebhookManager, this);
        ShardsMenuGUI.registerListener(this);
        TransactionHistoryGUI.registerListener(this);
        DailyRewardGUI.registerListener(this);
        DailyQuestsGUI.registerListener(this);

        // 3. Register Commands & Aliases
        ShardsCommand shardsCmd = new ShardsCommand(this);
        PluginCommand cmd = getCommand("shards");
        if (cmd != null) {
            cmd.setExecutor(shardsCmd);
            cmd.setTabCompleter(shardsCmd);
        }
        getServer().getPluginManager().registerEvents(shardsCmd, this);

        // 4. Register Hooks
        VaultEconomyHook.register(this);
        EconomyShopGUIHook.tryHook(this);
        HavenShopHook.tryHook(this);
        this.geyserHook = new GeyserHook(this);
        this.essentialsHook = new EssentialsHook(this);

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            this.papiExpansion = new PAPIExpansion(this);
            this.papiExpansion.registerAll();
            getLogger().info("[HavenShards] Successfully hooked into PlaceholderAPI!");
        }

        getLogger().info("=======================================");
        getLogger().info("  HavenShards v" + getDescription().getVersion() + " Enabled!");
        getLogger().info("  ✦ Modules: Economy, Quests, Boosters");
        getLogger().info("  ✦ Status: Highly Optimized & Ready");
        getLogger().info("=======================================");
    }

    @Override
    public void onDisable() {
        getLogger().info("Shutting down HavenShards and saving player data...");
        VaultEconomyHook.unregister(this);
        if (papiExpansion != null) {
            papiExpansion.unregisterAll();
            papiExpansion = null;
        }
        // Unregister GUI listeners to prevent ClassLoader memory leaks and broken event handling on reload
        ShardsMenuGUI.unregisterListener();
        TransactionHistoryGUI.unregisterListener();
        DailyRewardGUI.unregisterListener();
        DailyQuestsGUI.unregisterListener();

        if (shardManager != null) {
            shardManager.saveAllOnlinePlayers();
        }
        if (dailyRewardManager != null) {
            dailyRewardManager.saveAllOnlinePlayers();
        }
        if (questManager != null) {
            questManager.saveAllOnlinePlayers();
        }
        if (playtimeRewardManager != null) {
            playtimeRewardManager.onDisable();
        }
        if (withdrawPromptManager != null) {
            withdrawPromptManager.onDisable();
        }
        if (boosterManager != null) {
            boosterManager.onDisable();
        }
        if (discordWebhookManager != null) {
            discordWebhookManager.shutdown();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        instance = null;
        getLogger().info("HavenShards Disabled Cleanly!");
    }

    public static HavenShards getInstance() { return instance; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public ShardManager getShardManager() { return shardManager; }
    public PlaytimeRewardManager getPlaytimeRewardManager() { return playtimeRewardManager; }
    public MessageManager getMessageManager() { return messageManager; }
    public SoundManager getSoundManager() { return soundManager; }
    public BoosterManager getBoosterManager() { return boosterManager; }
    public DiscordWebhookManager getDiscordWebhookManager() { return discordWebhookManager; }
    public GUIManager getGuiManager() { return guiManager; }
    public DailyRewardManager getDailyRewardManager() { return dailyRewardManager; }
    public QuestManager getQuestManager() { return questManager; }
    public WithdrawPromptManager getWithdrawPromptManager() { return withdrawPromptManager; }
    public GeyserHook getGeyserHook() { return geyserHook; }
    public EssentialsHook getEssentialsHook() { return essentialsHook; }

    public String getPrefix() {
        return ColorUtil.colorize(getConfig().getString("prefix", "&b[HavenShards] &7"));
    }
}
