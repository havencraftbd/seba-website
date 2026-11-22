package com.haven.sell;

import com.haven.sell.command.PriceCommand;
import com.haven.sell.command.SellCommand;
import com.haven.sell.config.ConfigManager;
import com.haven.sell.config.GuiConfig;
import com.haven.sell.config.HistoryGuiConfig;
import com.haven.sell.config.MessagesManager;
import com.haven.sell.config.MySQLConfig;
import com.haven.sell.config.PriceManager;
import com.haven.sell.data.DataManager;
import com.haven.sell.data.MySQLDataManager;
import com.haven.sell.data.SQLiteDataManager;
import com.haven.sell.data.YamlDataManager;
import com.haven.sell.economy.EconomyManager;
import com.haven.sell.gui.BrowseGui;
import com.haven.sell.gui.GuiListener;
import com.haven.sell.gui.HistoryGui;
import com.haven.sell.gui.SellGui;
import com.haven.sell.gui.ConfirmGui;
import com.haven.sell.listener.PlayerQuitListener;
import com.haven.sell.listener.PlayerJoinListener;
import com.haven.sell.listener.SellAxeListener;
import com.haven.sell.util.SellAxeUtil;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;
import com.haven.sell.util.MessageUtil;

/**
 * HavenSell - A professional sell system plugin for Paper servers.
 */
public final class HavenSell extends JavaPlugin {

    private ConfigManager configManager;
    private MessagesManager messagesManager;
    private PriceManager priceManager;
    private GuiConfig guiConfig;
    private HistoryGuiConfig historyGuiConfig;
    private MySQLConfig mysqlConfig;
    private EconomyManager economyManager;
    private DataManager dataManager;
    private SellGui sellGui;
    private HistoryGui historyGui;
    private ConfirmGui confirmGui;
    private BrowseGui browseGui;

    @Override
    public void onEnable() {
        // Initialize MessageUtil so console logs use the configurable prefix
        MessageUtil.init(this);

        // 1. Load all configurations
        try {
            this.configManager     = new ConfigManager(this);
            this.messagesManager   = new MessagesManager(this);
            this.priceManager      = new PriceManager(this);
            this.guiConfig         = new GuiConfig(this);
            this.historyGuiConfig  = new HistoryGuiConfig(this);
            this.mysqlConfig       = new MySQLConfig(this);
        } catch (Exception e) {
            MessageUtil.logSevere("Failed to load configuration files! Disabling plugin.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 2. Setup economy (Vault)
        this.economyManager = new EconomyManager(this);
        if (!economyManager.setupEconomy()) {
            MessageUtil.logSevere("Vault economy not found! Please install Vault and an economy plugin.", null);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 3. Setup data manager (MySQL or SQLite)
        try {
            if (mysqlConfig.isEnabled()) {
                this.dataManager = new MySQLDataManager(this, mysqlConfig);
                MessageUtil.logInfo("Using MySQL for player data storage.");
            } else {
                this.dataManager = new SQLiteDataManager(this);
                MessageUtil.logInfo("Using SQLite database for player data storage.");
            }
        } catch (Exception e) {
            MessageUtil.logWarning("Database connection failed, falling back to SQLite storage: " + e.getMessage());
            try {
                this.dataManager = new SQLiteDataManager(this);
            } catch (Exception ex) {
                MessageUtil.logSevere("Failed to initialize SQLite storage!", ex);
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
        }

        // 4. Setup GUI managers
        this.sellGui    = new SellGui(this);
        this.historyGui = new HistoryGui(this);
        this.confirmGui = new ConfirmGui(this);
        this.browseGui  = new BrowseGui(this);

        // Initialize Sell Axe utility NBT keys
        SellAxeUtil.init(this);

        // 5. Register commands
        PluginCommand sellCommand = getCommand("sell");
        if (sellCommand != null) {
            SellCommand executor = new SellCommand(this);
            sellCommand.setExecutor(executor);
            sellCommand.setTabCompleter(executor);
        }

        // Register /price and /worth commands
        PriceCommand priceExecutor = new PriceCommand(this);
        PluginCommand priceCmd = getCommand("price");
        if (priceCmd != null) {
            priceCmd.setExecutor(priceExecutor);
            priceCmd.setTabCompleter(priceExecutor);
        }
        PluginCommand worthCmd = getCommand("worth");
        if (worthCmd != null) {
            worthCmd.setExecutor(priceExecutor);
            worthCmd.setTabCompleter(priceExecutor);
        }

        // 6. Register listeners
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(historyGui, this);
        getServer().getPluginManager().registerEvents(confirmGui, this);
        getServer().getPluginManager().registerEvents(browseGui, this);
        getServer().getPluginManager().registerEvents(new SellAxeListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);

        // 7. Hook into PlaceholderAPI
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new com.haven.sell.hook.PAPIExpansion(this).register();
            MessageUtil.logInfo("Successfully hooked into PlaceholderAPI!");
        }

        MessageUtil.logInfo("HavenSell v" + getDescription().getVersion() + " has been enabled!");
    }

    @Override
    public void onDisable() {
        if (sellGui    != null) sellGui.closeAllGuis();
        if (historyGui != null) historyGui.closeAll();
        if (browseGui  != null) browseGui.closeAll();
        if (dataManager != null) dataManager.shutdown();
        MessageUtil.logInfo("HavenSell has been disabled.");
    }

    /**
     * Reloads all plugin configurations, prices, and GUI settings.
     */
    public void reloadAllConfigs() {
        configManager.reload();
        messagesManager.reload();
        priceManager.reload();
        guiConfig.reload();
        historyGuiConfig.reload();

        // Rebuild browse GUI categories and price lists from reloaded data
        if (browseGui != null) browseGui.reload();
        mysqlConfig.reload();

        DataManager oldManager = this.dataManager;
        try {
            if (mysqlConfig.isEnabled()) {
                this.dataManager = new MySQLDataManager(this, mysqlConfig);
                MessageUtil.logInfo("Switched to MySQL storage.");
            } else {
                this.dataManager = new SQLiteDataManager(this);
                MessageUtil.logInfo("Switched to SQLite storage.");
            }
        } catch (Exception e) {
            MessageUtil.logWarning("Failed to reinitialize data manager, keeping current: " + e.getMessage());
            this.dataManager = oldManager;
            return;
        }
        oldManager.shutdown();
    }

    // ━━━━━━━━━━━━━━━ Getters ━━━━━━━━━━━━━━━

    public ConfigManager    getConfigManager()    { return configManager; }
    public MessagesManager  getMessagesManager()  { return messagesManager; }
    public PriceManager     getPriceManager()     { return priceManager; }
    public GuiConfig        getGuiConfig()        { return guiConfig; }
    public HistoryGuiConfig getHistoryGuiConfig() { return historyGuiConfig; }
    public EconomyManager   getEconomyManager()   { return economyManager; }
    public DataManager      getDataManager()      { return dataManager; }
    public SellGui          getSellGui()          { return sellGui; }
    public HistoryGui       getHistoryGui()       { return historyGui; }
    public ConfirmGui       getConfirmGui()       { return confirmGui; }
    public BrowseGui        getBrowseGui()        { return browseGui; }
}
