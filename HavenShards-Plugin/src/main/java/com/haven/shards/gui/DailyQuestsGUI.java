package com.haven.shards.gui;

import com.haven.shards.HavenShards;
import com.haven.shards.manager.QuestManager;
import com.haven.shards.model.QuestData;
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

import java.util.*;

public class DailyQuestsGUI implements InventoryHolder, Listener {

    private static Listener activeListener = null;
    private static final com.google.common.cache.Cache<UUID, Long> clickDebounce = com.google.common.cache.CacheBuilder.newBuilder()
            .expireAfterWrite(1, java.util.concurrent.TimeUnit.SECONDS)
            .maximumSize(50)
            .build();
    private final HavenShards plugin;
    private final Inventory inventory;
    private final Player player;
    private final Map<Integer, String> slotQuestMap = new HashMap<>();

    public DailyQuestsGUI(HavenShards plugin) {
        this.plugin = plugin;
        this.inventory = null;
        this.player = null;
    }

    public static synchronized void registerListener(HavenShards plugin) {
        if (activeListener != null) {
            HandlerList.unregisterAll(activeListener);
        }
        activeListener = new DailyQuestsGUI(plugin);
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

    public DailyQuestsGUI(HavenShards plugin, Player player) {
        this.plugin = plugin;
        this.player = player;

        FileConfiguration qConfig = plugin.getGuiManager().getQuestsConfig();
        String rawTitle = qConfig.getString("title", "&#00E5FF&lHavenShards &#888888» Daily Quests");
        int rows = qConfig.getInt("rows", 4);

        this.inventory = Bukkit.createInventory(this, Math.max(1, Math.min(6, rows)) * 9, ColorUtil.colorize(rawTitle));
        buildGUI();
    }

    private void buildGUI() {
        FileConfiguration qConfig = plugin.getGuiManager().getQuestsConfig();
        String symbol = plugin.getConfig().getString("currency.symbol", "✨");
        String currency = plugin.getConfig().getString("currency.name", "Shards");

        // Fill background
        String bgMatStr = qConfig.getString("background.material", "BLACK_STAINED_GLASS_PANE");
        if (bgMatStr != null && !bgMatStr.equalsIgnoreCase("AIR") && !bgMatStr.equalsIgnoreCase("NONE")) {
            Material bgMat;
            try {
                bgMat = Material.valueOf(bgMatStr.toUpperCase());
            } catch (Exception e) {
                bgMat = Material.BLACK_STAINED_GLASS_PANE;
            }

            ItemStack pane = new ItemStack(bgMat);
            ItemMeta paneMeta = pane.getItemMeta();
            if (paneMeta != null) {
                paneMeta.setDisplayName(ColorUtil.colorize(qConfig.getString("background.name", " ")));
                pane.setItemMeta(paneMeta);
            }
            for (int i = 0; i < inventory.getSize(); i++) {
                inventory.setItem(i, pane);
            }
        }

        // Back Button
        int backSlot = qConfig.getInt("buttons.back.slot", 27);
        if (backSlot >= 0 && backSlot < inventory.getSize()) {
            Material bMat;
            try {
                bMat = Material.valueOf(qConfig.getString("buttons.back.material", "RED_STAINED_GLASS_PANE").toUpperCase());
            } catch (Exception e) {
                bMat = Material.RED_STAINED_GLASS_PANE;
            }
            ItemStack back = new ItemStack(bMat);
            ItemMeta backMeta = back.getItemMeta();
            if (backMeta != null) {
                backMeta.setDisplayName(ColorUtil.colorize(qConfig.getString("buttons.back.name", "&c&l« Back to Dashboard")));
                back.setItemMeta(backMeta);
            }
            inventory.setItem(backSlot, back);
        }

        // Close Button
        int closeSlot = qConfig.getInt("buttons.close.slot", -1);
        if (closeSlot >= 0 && closeSlot < inventory.getSize()) {
            Material cMat;
            try {
                cMat = Material.valueOf(qConfig.getString("buttons.close.material", "BARRIER").toUpperCase());
            } catch (Exception e) {
                cMat = Material.BARRIER;
            }
            ItemStack close = new ItemStack(cMat);
            ItemMeta closeMeta = close.getItemMeta();
            if (closeMeta != null) {
                closeMeta.setDisplayName(ColorUtil.colorize(qConfig.getString("buttons.close.name", "&c&lClose")));
                close.setItemMeta(closeMeta);
            }
            inventory.setItem(closeSlot, close);
        }

        // Render Quests
        List<QuestManager.QuestDefinition> quests = plugin.getQuestManager().getQuestDefinitions();
        List<Integer> configSlots = qConfig.getIntegerList("quest-slots");
        int[] defaultSlots;
        if (configSlots != null && !configSlots.isEmpty()) {
            defaultSlots = configSlots.stream().mapToInt(Integer::intValue).toArray();
        } else {
            defaultSlots = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        }
        int slotIndex = 0;

        long now = System.currentTimeMillis();
        for (QuestManager.QuestDefinition def : quests) {
            int targetSlot = def.getSlot();
            if (targetSlot < 0) {
                if (slotIndex >= defaultSlots.length) break;
                targetSlot = defaultSlots[slotIndex++];
            }
            if (targetSlot < 0 || targetSlot >= inventory.getSize()) {
                plugin.getLogger().warning("[HavenShards] Quest '" + def.getId() + "' configured with invalid slot: " + targetSlot + " (inventory size: " + inventory.getSize() + "). Skipping.");
                continue;
            }
            slotQuestMap.put(targetSlot, def.getId());

            QuestData qd = plugin.getQuestManager().getQuestData(player.getUniqueId(), def.getId());
            boolean isClaimed = qd.isClaimed();
            boolean isClaimable = !isClaimed && qd.getProgress() >= def.getTarget();

            String stateKey = isClaimed ? "claimed" : (isClaimable ? "claimable" : "in-progress");
            String path = "quest-states." + stateKey;

            String matStr = isClaimed ? qConfig.getString(path + ".material", "LIME_DYE") : def.getMaterial();
            Material mat;
            try {
                mat = Material.valueOf(matStr.toUpperCase());
            } catch (Exception e) {
                mat = Material.BOOK;
            }

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                String rawName = qConfig.getString(path + ".name", "&e&l%title%");
                meta.setDisplayName(ColorUtil.colorize(rawName
                        .replace("%title%", ColorUtil.colorize(def.getTitle()))
                ));

                boolean glow = qConfig.getBoolean(path + ".glow", isClaimable);
                if (glow) {
                    org.bukkit.enchantments.Enchantment unbreaking = org.bukkit.Registry.ENCHANTMENT.get(org.bukkit.NamespacedKey.minecraft("unbreaking"));
                    if (unbreaking == null) {
                        unbreaking = org.bukkit.enchantments.Enchantment.getByKey(org.bukkit.NamespacedKey.minecraft("unbreaking"));
                    }
                    if (unbreaking != null) {
                        meta.addEnchant(unbreaking, 1, true);
                        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                    }
                }

                if (def.getCustomModelData() > 0) {
                    meta.setCustomModelData(def.getCustomModelData());
                }

                // Progress Bar Math
                int length = qConfig.getInt("progress-bar.length", 10);
                String filledChar = qConfig.getString("progress-bar.filled-char", "■");
                String emptyChar = qConfig.getString("progress-bar.empty-char", "□");
                String filledColor = qConfig.getString("progress-bar.filled-color", "&a");
                String emptyColor = qConfig.getString("progress-bar.empty-color", "&7");

                double percentVal = Math.min(100.0, ((double) qd.getProgress() / def.getTarget()) * 100.0);
                int filledCount = (int) Math.round((percentVal / 100.0) * length);

                StringBuilder bar = new StringBuilder(filledColor);
                for (int b = 0; b < length; b++) {
                    if (b == filledCount) bar.append(emptyColor);
                    bar.append(b < filledCount ? filledChar : emptyChar);
                }

                long remainingMillis = Math.max(0L, plugin.getQuestManager().getNextResetMillis() - now);
                String resetsIn = TimeUtil.formatTime(remainingMillis / 1000L);

                String typeDesc = getQuestTypeDescription(def.getType(), def.getTarget());

                List<String> rawLore = qConfig.getStringList(path + ".lore");
                List<String> lore = new ArrayList<>();
                for (String l : rawLore) {
                    lore.add(ColorUtil.colorize(l
                            .replace("%title%", ColorUtil.colorize(def.getTitle()))
                            .replace("%type_desc%", typeDesc)
                            .replace("%current%", String.valueOf(qd.getProgress()))
                            .replace("%target%", String.valueOf(def.getTarget()))
                            .replace("%percent%", String.format("%.0f", percentVal))
                            .replace("%progress_bar%", bar.toString())
                            .replace("%reward%", ItemUtil.formatMoney(def.getReward() * plugin.getShardManager().getTotalMultiplier(player)))
                            .replace("%symbol%", symbol)
                            .replace("%currency%", currency)
                            .replace("%resets_in%", resetsIn)
                    ));
                }
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            if (targetSlot >= 0 && targetSlot < inventory.getSize()) {
                inventory.setItem(targetSlot, item);
            }
        }
    }

    private String getQuestTypeDescription(String type, int target) {
        return switch (type.toUpperCase()) {
            case "MINE_BLOCKS" -> "Mine " + target + " blocks/ores";
            case "KILL_MOBS" -> "Kill " + target + " mobs";
            case "KILL_PLAYERS" -> "Defeat " + target + " players";
            case "CATCH_FISH" -> "Catch " + target + " fish";
            case "HARVEST_CROPS" -> "Harvest " + target + " crops";
            case "PLAYTIME" -> "Play active for " + target + " minutes";
            default -> "Complete " + target + " tasks";
        };
    }

    public void open(Player p) {
        p.openInventory(inventory);
    }

    public void refresh() {
        if (inventory != null) {
            inventory.clear();
            slotQuestMap.clear();
            buildGUI();
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof DailyQuestsGUI gui) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) return;

            int slot = event.getRawSlot();
            FileConfiguration qConfig = gui.plugin.getGuiManager().getQuestsConfig();
            int backSlot = qConfig.getInt("buttons.back.slot", 27);
            int closeSlot = qConfig.getInt("buttons.close.slot", -1);

            if (slot == backSlot) {
                SoundUtil.playSound(gui.plugin, player, "admin-set");
                new ShardsMenuGUI(gui.plugin, player).open(player);
                return;
            }

            if (slot == closeSlot) {
                player.closeInventory();
                return;
            }

            if (gui.slotQuestMap.containsKey(slot)) {
                if (isDebounced(player.getUniqueId())) {
                    return;
                }
                String questId = gui.slotQuestMap.get(slot);
                boolean success = gui.plugin.getQuestManager().claimQuest(player, questId);
                if (success) {
                    gui.refresh();
                } else {
                    SoundUtil.playSound(gui.plugin, player, "insufficient-funds");
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof DailyQuestsGUI) {
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
