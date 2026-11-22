package com.haven.shards.gui;

import com.haven.shards.HavenShards;
import com.haven.shards.manager.DailyRewardManager;
import com.haven.shards.util.ColorUtil;
import com.haven.shards.util.ItemUtil;
import com.haven.shards.util.SoundUtil;
import com.haven.shards.util.TimeUtil;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DailyRewardGUI implements InventoryHolder, Listener {

    private static Listener activeListener = null;
    private static final com.google.common.cache.Cache<UUID, Long> clickDebounce = com.google.common.cache.CacheBuilder.newBuilder()
            .expireAfterWrite(1, java.util.concurrent.TimeUnit.SECONDS)
            .maximumSize(50)
            .build();
    private final HavenShards plugin;
    private final Inventory inventory;

    public DailyRewardGUI(HavenShards plugin) {
        this.plugin = plugin;
        this.inventory = null;
    }

    public static synchronized void registerListener(HavenShards plugin) {
        if (activeListener != null) {
            HandlerList.unregisterAll(activeListener);
        }
        activeListener = new DailyRewardGUI(plugin);
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

    public DailyRewardGUI(HavenShards plugin, Player player) {
        this.plugin = plugin;
        FileConfiguration dConfig = plugin.getGuiManager().getDailyConfig();
        String title = ColorUtil.colorize(dConfig.getString("title", "&#00E5FF&lHavenShards &#888888» Daily Rewards"));
        int rows = dConfig.getInt("rows", 3);
        this.inventory = Bukkit.createInventory(this, Math.max(1, Math.min(6, rows)) * 9, title);
        buildGUI(player);
    }

    private void buildGUI(Player player) {
        UUID uuid = player.getUniqueId();
        DailyRewardManager manager = plugin.getDailyRewardManager();
        FileConfiguration dConfig = plugin.getGuiManager().getDailyConfig();

        String symbol = plugin.getConfig().getString("currency.symbol", "✨");
        String currency = plugin.getConfig().getString("currency.name", "Shards");

        // Stained glass background
        String bgMatStr = dConfig.getString("background.material", "GRAY_STAINED_GLASS_PANE");
        if (bgMatStr != null && !bgMatStr.equalsIgnoreCase("AIR") && !bgMatStr.equalsIgnoreCase("NONE")) {
            Material bgMat;
            try {
                bgMat = Material.valueOf(bgMatStr.toUpperCase());
            } catch (Exception e) {
                bgMat = Material.GRAY_STAINED_GLASS_PANE;
            }
            ItemStack bg = new ItemStack(bgMat);
            ItemMeta bgMeta = bg.getItemMeta();
            if (bgMeta != null) {
                bgMeta.setDisplayName(ColorUtil.colorize(dConfig.getString("background.name", " ")));
                bg.setItemMeta(bgMeta);
            }
            for (int i = 0; i < inventory.getSize(); i++) {
                inventory.setItem(i, bg);
            }
        }

        boolean isLoaded = manager.isDataLoaded(uuid);
        boolean canClaim = isLoaded && manager.canClaim(uuid);
        int currentStreak = manager.getStreak(uuid);
        int nextStreak = canClaim ? (currentStreak + 1) : currentStreak;

        double baseReward = plugin.getConfig().getDouble("daily-rewards.base-reward", 25.0);
        double streakBonus = plugin.getConfig().getDouble("daily-rewards.streak-bonuses." + nextStreak, 0.0);
        double totalMult = plugin.getShardManager().getTotalMultiplier(player);

        // Claim Button (Slot 13)
        int claimSlot = dConfig.getInt("items.claim-available.slot", 13);
        if (claimSlot >= 0 && claimSlot < inventory.getSize()) {
            if (!isLoaded) {
                ItemStack loadingItem = new ItemStack(Material.CLOCK);
                ItemMeta meta = loadingItem.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ColorUtil.colorize("&e&l⏳ LOADING DATA..."));
                    meta.setLore(Collections.singletonList(ColorUtil.colorize("&7Your daily reward data is loading, please wait...")));
                    loadingItem.setItemMeta(meta);
                }
                inventory.setItem(claimSlot, loadingItem);
            } else {
                String path = canClaim ? "items.claim-available" : "items.claim-claimed";
                String defaultMatStr = canClaim ? "NETHER_STAR" : "CLOCK";
                Material claimMat;
                try {
                    claimMat = Material.valueOf(dConfig.getString(path + ".material", defaultMatStr).toUpperCase());
                } catch (Exception e) {
                    claimMat = canClaim ? Material.NETHER_STAR : Material.CLOCK;
                }

                ItemStack claimItem = new ItemStack(claimMat);
                ItemMeta meta = claimItem.getItemMeta();
                if (meta != null) {
                    String defaultName = canClaim ? "&a&l✨ CLAIM DAILY REWARD" : "&c&lALREADY CLAIMED TODAY";
                    meta.setDisplayName(ColorUtil.colorize(dConfig.getString(path + ".name", defaultName)));

                    int cmd = dConfig.getInt(path + ".custom-model-data", 0);
                    if (cmd > 0) meta.setCustomModelData(cmd);

                    long remSec = manager.getRemainingMillis(uuid) / 1000L;
                    String remainingStr = TimeUtil.formatTime(remSec);

                    List<String> rawLore = dConfig.getStringList(path + ".lore");
                    List<String> lore = new ArrayList<>();
                    for (String l : rawLore) {
                        lore.add(ColorUtil.colorize(l
                                .replace("%streak%", String.valueOf(currentStreak))
                                .replace("%base_reward%", ItemUtil.formatMoney(baseReward))
                                .replace("%streak_bonus%", ItemUtil.formatMoney(streakBonus))
                                .replace("%multiplier%", String.format("%.1f", totalMult))
                                .replace("%remaining%", remainingStr)
                                .replace("%symbol%", symbol)
                                .replace("%currency%", currency)
                        ));
                    }
                    meta.setLore(lore);
                    claimItem.setItemMeta(meta);
                }
                inventory.setItem(claimSlot, claimItem);
            }
        }

        // Dynamic Milestone Displays
        List<Integer> milestoneDays = manager.getMilestoneDays();
        int[] defaultSlots = {10, 11, 12, 14, 15, 16, 9, 17};

        for (int i = 0; i < Math.min(milestoneDays.size(), defaultSlots.length); i++) {
            int day = milestoneDays.get(i);
            int slot = defaultSlots[i];

            Material mat;
            String statusStr;
            if (currentStreak >= day) {
                mat = Material.LIME_STAINED_GLASS;
                statusStr = "&a✔ Completed";
            } else if (nextStreak == day) {
                mat = Material.YELLOW_STAINED_GLASS;
                statusStr = "&e★ Next Goal";
            } else {
                mat = Material.RED_STAINED_GLASS;
                statusStr = "&c🔒 Locked";
            }

            ItemStack mItem = new ItemStack(mat);
            ItemMeta mMeta = mItem.getItemMeta();
            if (mMeta != null) {
                mMeta.setDisplayName(ColorUtil.colorize("&e&lDay " + day + " Milestone"));
                List<String> mLore = new ArrayList<>();
                double bBonus = manager.getStreakBonus(day);
                mLore.add(ColorUtil.colorize("&7Status: " + statusStr));
                if (day == 1) {
                    mLore.add(ColorUtil.colorize("&7Reward: &a+" + ItemUtil.formatMoney(baseReward) + " Shards"));
                } else {
                    mLore.add(ColorUtil.colorize("&7Streak Bonus: &a+" + ItemUtil.formatMoney(bBonus) + " Shards"));
                }
                mMeta.setLore(mLore);
                mItem.setItemMeta(mMeta);
            }
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, mItem);
            }
        }

        // Back to Dashboard Button
        int backSlot = dConfig.getInt("buttons.back.slot", 22);
        if (backSlot >= 0 && backSlot < inventory.getSize()) {
            Material backMat;
            try {
                backMat = Material.valueOf(dConfig.getString("buttons.back.material", "RED_STAINED_GLASS_PANE").toUpperCase());
            } catch (Exception e) {
                backMat = Material.RED_STAINED_GLASS_PANE;
            }
            ItemStack backItem = new ItemStack(backMat);
            ItemMeta backMeta = backItem.getItemMeta();
            if (backMeta != null) {
                backMeta.setDisplayName(ColorUtil.colorize(dConfig.getString("buttons.back.name", "&c&l« Back to Dashboard")));
                int cmd = dConfig.getInt("buttons.back.custom-model-data", 0);
                if (cmd > 0) backMeta.setCustomModelData(cmd);
                backItem.setItemMeta(backMeta);
            }
            inventory.setItem(backSlot, backItem);
        }

        // Close Button
        int closeSlot = dConfig.getInt("buttons.close.slot", -1);
        if (closeSlot >= 0 && closeSlot < inventory.getSize()) {
            Material closeMat;
            try {
                closeMat = Material.valueOf(dConfig.getString("buttons.close.material", "BARRIER").toUpperCase());
            } catch (Exception e) {
                closeMat = Material.BARRIER;
            }
            ItemStack closeItem = new ItemStack(closeMat);
            ItemMeta closeMeta = closeItem.getItemMeta();
            if (closeMeta != null) {
                closeMeta.setDisplayName(ColorUtil.colorize(dConfig.getString("buttons.close.name", "&c&lClose")));
                int cmd = dConfig.getInt("buttons.close.custom-model-data", 0);
                if (cmd > 0) closeMeta.setCustomModelData(cmd);
                closeItem.setItemMeta(closeMeta);
            }
            inventory.setItem(closeSlot, closeItem);
        }
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    public void refresh(Player player) {
        if (inventory != null) {
            inventory.clear();
            buildGUI(player);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof DailyRewardGUI gui) {
            event.setCancelled(true);

            if (!(event.getWhoClicked() instanceof Player player)) return;
            int slot = event.getRawSlot();

            FileConfiguration dConfig = gui.plugin.getGuiManager().getDailyConfig();
            int backSlot = dConfig.getInt("buttons.back.slot", 22);
            int closeSlot = dConfig.getInt("buttons.close.slot", -1);
            int claimSlot = dConfig.getInt("items.claim-available.slot", 13);

            if (slot == backSlot) {
                SoundUtil.playSound(gui.plugin, player, "admin-set");
                new ShardsMenuGUI(gui.plugin, player).open(player);
                return;
            }

            if (slot == closeSlot) {
                player.closeInventory();
                return;
            }

            if (slot == claimSlot) {
                if (isDebounced(player.getUniqueId())) {
                    return;
                }
                if (!gui.plugin.getDailyRewardManager().isDataLoaded(player.getUniqueId())) {
                    String msg = gui.plugin.getMessageManager().getMessage("daily-loading");
                    if (msg.equals("daily-loading")) {
                        msg = "&cYour daily reward data is loading, please wait a moment...";
                    }
                    player.sendMessage(gui.plugin.getPrefix() + ColorUtil.colorize(msg));
                    SoundUtil.playSound(gui.plugin, player, "insufficient-funds");
                    return;
                }

                boolean claimed = gui.plugin.getDailyRewardManager().claimReward(player);
                if (claimed) {
                    player.closeInventory();
                } else {
                    gui.refresh(player);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof DailyRewardGUI) {
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
