package com.haven.shards.gui;

import com.haven.shards.HavenShards;
import com.haven.shards.util.ColorUtil;
import com.haven.shards.util.ItemUtil;
import com.haven.shards.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import com.destroystokyo.paper.profile.PlayerProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ShardsMenuGUI implements InventoryHolder, Listener {

    private static Listener activeListener = null;
    private static final com.google.common.cache.Cache<UUID, Long> clickDebounce = com.google.common.cache.CacheBuilder.newBuilder()
            .expireAfterWrite(1, java.util.concurrent.TimeUnit.SECONDS)
            .maximumSize(500)
            .build();
    private final HavenShards plugin;
    private final Inventory inventory;

    public ShardsMenuGUI(HavenShards plugin) {
        this.plugin = plugin;
        this.inventory = null;
    }

    public static synchronized void registerListener(HavenShards plugin) {
        if (activeListener != null) {
            HandlerList.unregisterAll(activeListener);
        }
        activeListener = new ShardsMenuGUI(plugin);
        Bukkit.getPluginManager().registerEvents(activeListener, plugin);
    }

    public static synchronized void unregisterListener() {
        if (activeListener != null) {
            HandlerList.unregisterAll(activeListener);
            activeListener = null;
        }
        clickDebounce.invalidateAll();
    }

    private static boolean isDebounced(UUID uuid) {
        long now = System.currentTimeMillis();
        Long last = clickDebounce.getIfPresent(uuid);
        if (last != null && (now - last) < 500L) {
            return true;
        }
        clickDebounce.put(uuid, now);
        return false;
    }

    public ShardsMenuGUI(HavenShards plugin, Player player) {
        this.plugin = plugin;
        FileConfiguration dashConfig = plugin.getGuiManager().getDashboardConfig();
        String title = ColorUtil.colorize(dashConfig.getString("title", "&#00E5FF&lHavenShards &#888888» Dashboard"));
        int rows = dashConfig.getInt("rows", 4);
        this.inventory = Bukkit.createInventory(this, Math.max(1, Math.min(6, rows)) * 9, title);

        buildGUI(player);
    }

    private void buildGUI(Player player) {
        FileConfiguration dashConfig = plugin.getGuiManager().getDashboardConfig();

        // Stained glass background
        String bgMatStr = dashConfig.getString("background.material", "GRAY_STAINED_GLASS_PANE");
        if (bgMatStr != null && !bgMatStr.equalsIgnoreCase("AIR") && !bgMatStr.equalsIgnoreCase("NONE")) {
            Material bgMat;
            try {
                bgMat = Material.valueOf(bgMatStr.toUpperCase());
            } catch (Exception e) {
                bgMat = Material.GRAY_STAINED_GLASS_PANE;
            }
            if (bgMat != Material.AIR) {
                ItemStack border = new ItemStack(bgMat);
                ItemMeta borderMeta = border.getItemMeta();
                if (borderMeta != null) {
                    borderMeta.setDisplayName(ColorUtil.colorize(dashConfig.getString("background.name", " ")));
                    border.setItemMeta(borderMeta);
                }
                for (int i = 0; i < inventory.getSize(); i++) {
                    inventory.setItem(i, border);
                }
            }
        }

        String symbol = plugin.getConfig().getString("currency.symbol", "✨");
        String currency = plugin.getConfig().getString("currency.name", "Shards");
        double balance = plugin.getShardManager().getBalance(player);

        // --- Profile Slot ---
        int profileSlot = dashConfig.getInt("items.profile.slot", 13);
        if (profileSlot >= 0 && profileSlot < inventory.getSize()) {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta headMeta = (SkullMeta) head.getItemMeta();
            if (headMeta != null) {
                try {
                    PlayerProfile profile = player.getPlayerProfile();
                    if (profile.hasTextures()) {
                        headMeta.setPlayerProfile(profile);
                    }
                } catch (Exception ignored) {
                }
                String nameRaw = dashConfig.getString("items.profile.name", "&a&l%player%'s Profile");
                headMeta.setDisplayName(ColorUtil.colorize(nameRaw.replace("%player%", player.getName())));

                int intervalMins = plugin.getConfig().getInt("playtime-rewards.interval-minutes", 10);
                int currentMins = plugin.getPlaytimeRewardManager().getPlaytimeMinutes(player.getUniqueId());
                int remainingMins = Math.max(0, intervalMins - currentMins);
                double progressPercent = Math.min(100.0, ((double) currentMins / intervalMins) * 100.0);
                String progressBar = buildProgressBar(currentMins, intervalMins);

                List<String> rawLore = dashConfig.getStringList("items.profile.lore");
                List<String> lore = new ArrayList<>();
                for (String l : rawLore) {
                    lore.add(ColorUtil.colorize(l
                            .replace("%player%", player.getName())
                            .replace("%symbol%", symbol)
                            .replace("%currency%", currency)
                            .replace("%balance%", ItemUtil.formatMoney(balance))
                            .replace("%progress_bar%", progressBar)
                            .replace("%progress_percent%", String.valueOf((int) progressPercent))
                            .replace("%remaining_mins%", String.valueOf(remainingMins))
                            .replace("%reward_amount%", ItemUtil.formatMoney(plugin.getConfig().getDouble("playtime-rewards.reward-amount", 10.0)))
                    ));
                }
                headMeta.setLore(lore);
                head.setItemMeta(headMeta);
            }
            inventory.setItem(profileSlot, head);
        }

        // --- Daily Rewards Slot ---
        if (dashConfig.getBoolean("items.daily-rewards.enabled", true) && plugin.getConfig().getBoolean("daily-rewards.enabled", true)) {
            renderGenericItem(dashConfig, "items.daily-rewards", 10, Material.CHEST, "&e&lDaily Login Rewards", symbol, currency);
        }

        // --- Quests Button ---
        if (dashConfig.getBoolean("items.quests.enabled", true) && plugin.getConfig().getBoolean("daily-quests.enabled", true)) {
            renderGenericItem(dashConfig, "items.quests", 11, Material.BOOK, "&6&lDaily Quests", symbol, currency);
        }

        // --- Custom Withdraw Slot ---
        if (dashConfig.getBoolean("items.withdraw.enabled", true) && plugin.getConfig().getBoolean("physical-item.enabled", true)) {
            renderGenericItem(dashConfig, "items.withdraw", 12, Material.AMETHYST_SHARD, "&b&lCustom Withdraw", symbol, currency);
        }

        // --- Transaction History Slot ---
        if (dashConfig.getBoolean("items.history.enabled", true)) {
            renderGenericItem(dashConfig, "items.history", 14, Material.PAPER, "&d&lTransaction History", symbol, currency);
        }

        // --- Leaderboard Top Slot ---
        if (dashConfig.getBoolean("items.top.enabled", true)) {
            renderGenericItem(dashConfig, "items.top", 15, Material.GOLD_BLOCK, "&a&lRich Leaderboard", symbol, currency);
        }

        // --- Booster Status Slot ---
        if (dashConfig.getBoolean("items.booster.enabled", true)) {
            int bSlot = dashConfig.getInt("items.booster.slot", 16);
            if (bSlot >= 0 && bSlot < inventory.getSize()) {
                Material bMat;
                try {
                    bMat = Material.valueOf(dashConfig.getString("items.booster.material", "NETHER_STAR").toUpperCase());
                } catch (Exception e) {
                    bMat = Material.NETHER_STAR;
                }
                ItemStack boosterBtn = new ItemStack(bMat);
                ItemMeta bMeta = boosterBtn.getItemMeta();
                if (bMeta != null) {
                    bMeta.setDisplayName(ColorUtil.colorize(dashConfig.getString("items.booster.name", "&e&lServer Booster Status")));
                    int cmd = dashConfig.getInt("items.booster.custom-model-data", 0);
                    if (cmd > 0) bMeta.setCustomModelData(cmd);

                    List<String> rawLore;
                    if (plugin.getBoosterManager().isBoosterActive()) {
                        rawLore = dashConfig.getStringList("items.booster.lore-active");
                    } else {
                        rawLore = dashConfig.getStringList("items.booster.lore-inactive");
                    }

                    double mult = plugin.getBoosterManager().getMultiplier();
                    String remTime = plugin.getBoosterManager().getFormattedRemainingTime();

                    List<String> lore = new ArrayList<>();
                    for (String l : rawLore) {
                        lore.add(ColorUtil.colorize(l
                                .replace("%multiplier%", ItemUtil.formatMoney(mult))
                                .replace("%remaining_time%", remTime)
                        ));
                    }
                    bMeta.setLore(lore);
                    boosterBtn.setItemMeta(bMeta);
                }
                inventory.setItem(bSlot, boosterBtn);
            }
        }

        // --- Close Button ---
        int cSlot = dashConfig.getInt("items.close.slot", 31);
        if (cSlot >= 0 && cSlot < inventory.getSize()) {
            Material cMat;
            try {
                cMat = Material.valueOf(dashConfig.getString("items.close.material", "RED_STAINED_GLASS_PANE").toUpperCase());
            } catch (Exception e) {
                cMat = Material.RED_STAINED_GLASS_PANE;
            }
            ItemStack closeBtn = new ItemStack(cMat);
            ItemMeta closeMeta = closeBtn.getItemMeta();
            if (closeMeta != null) {
                closeMeta.setDisplayName(ColorUtil.colorize(dashConfig.getString("items.close.name", "&c&lClose Menu")));
                closeBtn.setItemMeta(closeMeta);
            }
            inventory.setItem(cSlot, closeBtn);
        }
    }

    private void renderGenericItem(FileConfiguration dashConfig, String path, int defaultSlot, Material defaultMat, String defaultName, String symbol, String currency) {
        int slot = dashConfig.getInt(path + ".slot", defaultSlot);
        if (slot < 0 || slot >= inventory.getSize()) return;

        Material mat;
        try {
            mat = Material.valueOf(dashConfig.getString(path + ".material", defaultMat.name()).toUpperCase());
        } catch (Exception e) {
            mat = defaultMat;
        }

        ItemStack btn = new ItemStack(mat);
        ItemMeta meta = btn.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.colorize(dashConfig.getString(path + ".name", defaultName)));
            int cmd = dashConfig.getInt(path + ".custom-model-data", 0);
            if (cmd > 0) meta.setCustomModelData(cmd);

            List<String> rawLore = dashConfig.getStringList(path + ".lore");
            List<String> lore = new ArrayList<>();
            for (String l : rawLore) {
                lore.add(ColorUtil.colorize(l.replace("%symbol%", symbol).replace("%currency%", currency)));
            }
            meta.setLore(lore);
            btn.setItemMeta(meta);
        }
        inventory.setItem(slot, btn);
    }

    private String buildProgressBar(int current, int max) {
        int totalBars = 10;
        int filledBars = (int) Math.round(((double) current / max) * totalBars);
        filledBars = Math.min(totalBars, Math.max(0, filledBars));

        StringBuilder sb = new StringBuilder("&a");
        for (int i = 0; i < filledBars; i++) {
            sb.append("■");
        }
        sb.append("&7");
        for (int i = filledBars; i < totalBars; i++) {
            sb.append("□");
        }
        return sb.toString();
    }

    public void open(Player player) {
        SoundUtil.playSound(plugin, player, "menu-open");
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof ShardsMenuGUI gui) {
            event.setCancelled(true);

            if (!(event.getWhoClicked() instanceof Player player)) return;
            int slot = event.getRawSlot();

            FileConfiguration dashConfig = gui.plugin.getGuiManager().getDashboardConfig();
            int closeSlot = dashConfig.getInt("items.close.slot", 31);
            int dailySlot = dashConfig.getInt("items.daily-rewards.slot", 10);
            int questsSlot = dashConfig.getInt("items.quests.slot", 11);
            int withdrawSlot = dashConfig.getInt("items.withdraw.slot", 12);
            int historySlot = dashConfig.getInt("items.history.slot", 14);
            int topSlot = dashConfig.getInt("items.top.slot", 15);

            if (slot == closeSlot) {
                SoundUtil.playSound(gui.plugin, player, "no-permission");
                player.closeInventory();
                return;
            }

            if (isDebounced(player.getUniqueId())) {
                return;
            }

            if (slot == dailySlot && dashConfig.getBoolean("items.daily-rewards.enabled", true) && gui.plugin.getConfig().getBoolean("daily-rewards.enabled", true)) {
                SoundUtil.playSound(gui.plugin, player, "admin-set");
                new DailyRewardGUI(gui.plugin, player).open(player);
            } else if (slot == questsSlot && dashConfig.getBoolean("items.quests.enabled", true) && gui.plugin.getConfig().getBoolean("daily-quests.enabled", true)) {
                SoundUtil.playSound(gui.plugin, player, "admin-set");
                new DailyQuestsGUI(gui.plugin, player).open(player);
            } else if (slot == withdrawSlot && dashConfig.getBoolean("items.withdraw.enabled", true)) {
                if (!gui.plugin.getConfig().getBoolean("physical-item.enabled", true)
                        || !gui.plugin.getConfig().getBoolean("physical-item.withdraw-enabled", true)) {
                    player.sendMessage(gui.plugin.getPrefix() + gui.plugin.getMessageManager().getMessage("withdraw-disabled"));
                    SoundUtil.playSound(gui.plugin, player, "no-permission");
                    return;
                }
                SoundUtil.playSound(gui.plugin, player, "admin-set");
                gui.plugin.getWithdrawPromptManager().startPrompt(player);
            } else if (slot == historySlot && dashConfig.getBoolean("items.history.enabled", true)) {
                SoundUtil.playSound(gui.plugin, player, "admin-set");
                int limit = gui.plugin.getConfig().getInt("security.history-default-limit", 15);
                gui.plugin.getDatabaseManager().getTransactionsAsync(player.getUniqueId(), limit, list -> {
                    if (player.isOnline() && player.getOpenInventory().getTopInventory().getHolder() instanceof ShardsMenuGUI) {
                        new TransactionHistoryGUI(gui.plugin, player, list, 1).open(player);
                    }
                });
            } else if (slot == topSlot && dashConfig.getBoolean("items.top.enabled", true)) {
                SoundUtil.playSound(gui.plugin, player, "admin-set");
                player.closeInventory();
                player.performCommand("shards top");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ShardsMenuGUI) {
            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot < event.getInventory().getSize()) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
