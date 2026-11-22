package com.haven.sell.gui;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.haven.sell.HavenSell;
import com.haven.sell.config.ConfigManager;
import com.haven.sell.config.HistoryGuiConfig;
import com.haven.sell.data.PlayerData;
import com.haven.sell.data.SellEntry;
import com.haven.sell.util.NumberFormatter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages sell history GUI creation, display, and navigation.
 */
public class HistoryGui implements Listener {

    private final HavenSell plugin;

    /** Inventories currently identified as history GUIs */
    private final Set<Inventory> historyInventories = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /** Maps history GUI inventory → the player who owns the history (may differ from viewer) */
    private final java.util.Map<Inventory, String> inventoryOwnerName = new ConcurrentHashMap<>();

    /** For viewers of a history GUI that is not their own (admin view) */
    private final java.util.Map<UUID, String> viewingHistory = new ConcurrentHashMap<>();

    public HistoryGui(HavenSell plugin) {
        this.plugin = plugin;
    }

    // ━━━━━━━━━━━━━━━ Open ━━━━━━━━━━━━━━━

    /**
     * Opens the sell history GUI for a player viewing their own data.
     */
    public void openOwnHistory(Player viewer) {
        PlayerData data = plugin.getDataManager().loadPlayerData(viewer.getUniqueId(), viewer.getName());
        openHistory(viewer, data, viewer.getName());
    }

    /**
     * Opens the sell history GUI showing data of a specific player (admin use for offline players too).
     *
     * @param viewer         The admin viewer
     * @param targetName     The target player's name
     * @param targetUuid     The target player's UUID (may be offline)
     */
    public void openOtherHistory(Player viewer, String targetName, UUID targetUuid) {
        PlayerData data = plugin.getDataManager().loadPlayerData(targetUuid, targetName);
        viewingHistory.put(viewer.getUniqueId(), targetName);
        openHistory(viewer, data, targetName);
    }

    private void openHistory(Player viewer, PlayerData data, String ownerName) {
        HistoryGuiConfig cfg = plugin.getHistoryGuiConfig();
        ConfigManager cm = plugin.getConfigManager();

        int size = cfg.getSize();
        Inventory gui = Bukkit.createInventory(null, size, cfg.getTitle(ownerName));

        boolean abbrev = cm.isAbbreviationsEnabled();
        List<String> suffixes = cm.getAbbreviationSuffixes();
        String symbol = cm.getCurrencySymbol();

        // Fill top border row (row 1 = slots 0-8)
        if (cfg.isFillerEnabled()) {
            ItemStack topFiller = createItem(cfg.getFillerMaterial(), cfg.getFillerName(), null);
            for (int i = 0; i < 9; i++) gui.setItem(i, topFiller);
        }

        // Fill bottom border row (row 6 = slots 45-53)
        if (cfg.isFillerEnabled()) {
            ItemStack botFiller = createItem(cfg.getBorderMaterial(), cfg.getBorderName(), null);
            for (int i = 45; i < size; i++) gui.setItem(i, botFiller);
        }

        // Summary item
        String formattedTotal = NumberFormatter.format(data.getTotalMoneyEarned(), abbrev, suffixes);
        List<String> summaryLore = cfg.getSummaryLore(
                ownerName, data.getTotalItemsSold(), data.getTotalMoneyEarned(),
                formattedTotal, symbol, cfg.getMaxEntries());
        gui.setItem(cfg.getSummarySlot(), createItem(cfg.getSummaryMaterial(), cfg.getSummaryName(), summaryLore));

        // Player skull directly from config
        int headSlot = cfg.getHeadSlot();
        String headName = cfg.getHeadName(ownerName);
        List<String> headLore = cfg.getHeadLore(data.getTotalItemsSold(), formattedTotal, symbol);
        setOwnerSkull(gui, viewer, ownerName, headSlot, headName, headLore);

        // Back button
        gui.setItem(cfg.getBackSlot(), createItem(cfg.getBackMaterial(), cfg.getBackName(), cfg.getBackLore()));

        // Close button
        gui.setItem(cfg.getCloseSlot(), createItem(cfg.getCloseMaterial(), cfg.getCloseName(), cfg.getCloseLore()));

        // History entries (most recent first)
        populateHistoryEntries(gui, data, cfg, cm, abbrev, suffixes, symbol);

        historyInventories.add(gui);
        inventoryOwnerName.put(gui, ownerName);
        viewer.openInventory(gui);
    }

    private void populateHistoryEntries(Inventory gui, PlayerData data, HistoryGuiConfig cfg,
                                         ConfigManager cm, boolean abbrev, List<String> suffixes, String symbol) {
        List<SellEntry> history = data.getHistory();
        if (history.isEmpty()) return;

        // Show most recent first
        List<SellEntry> entries = new java.util.ArrayList<>(history);
        Collections.reverse(entries);

        int maxEntries = cfg.getMaxEntries();
        int startSlot = cfg.getHistoryStartSlot();
        int endSlot = cfg.getHistoryEndSlot();
        int slot = startSlot;
        int shown = 0;

        for (SellEntry entry : entries) {
            if (shown >= maxEntries) break;
            if (slot > endSlot) break;

            String formattedMoney = NumberFormatter.format(entry.getMoneyEarned(), abbrev, suffixes);
            List<String> lore = cfg.getHistoryItemLore(
                    entry.getItemCount(), formattedMoney, symbol, entry.getFormattedTime());

            Material material = cfg.getHistoryItemMaterial();
            if (entry.getMaterialName() != null) {
                Material entryMat = Material.matchMaterial(entry.getMaterialName());
                if (entryMat != null && entryMat.isItem()) {
                    material = entryMat;
                }
            }

            gui.setItem(slot, createItem(material, cfg.getHistoryItemName(), lore));
            slot++;
            shown++;
        }
    }

    private void setOwnerSkull(Inventory gui, Player viewer, String ownerName, int slot, String name, List<String> lore) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta == null) return;

        // Apply skin strictly from server RAM (zero Mojang HTTP requests for offline/cracked servers)
        if (viewer.getName().equalsIgnoreCase(ownerName)) {
            applyLocalPlayerSkin(meta, viewer);
        } else {
            Player online = Bukkit.getPlayerExact(ownerName);
            if (online != null) {
                applyLocalPlayerSkin(meta, online);
            }
            // If offline, leave as default head — never query Mojang session servers
        }
        meta.setDisplayName(name);
        if (lore != null && !lore.isEmpty()) {
            meta.setLore(lore);
        }
        skull.setItemMeta(meta);
        gui.setItem(slot, skull);
    }

    private static void applyLocalPlayerSkin(SkullMeta skullMeta, Player player) {
        if (skullMeta == null || player == null) return;
        try {
            PlayerProfile playerProfile = player.getPlayerProfile();
            if (playerProfile.hasTextures()) {
                for (ProfileProperty property : playerProfile.getProperties()) {
                    if ("textures".equalsIgnoreCase(property.getName())
                            && property.getValue() != null
                            && !property.getValue().isEmpty()) {
                        PlayerProfile safeProfile = Bukkit.createProfile(UUID.randomUUID());
                        safeProfile.setProperty(new ProfileProperty("textures", property.getValue(), property.getSignature()));
                        skullMeta.setPlayerProfile(safeProfile);
                        return;
                    }
                }
                skullMeta.setPlayerProfile(playerProfile);
            }
        } catch (Throwable ignored) {
            // Never fall back to setOwningPlayer() — prevents Mojang HTTP 429 on cracked/offline servers
        }
    }

    // ━━━━━━━━━━━━━━━ Event Handling ━━━━━━━━━━━━━━━

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHistoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory topInv = event.getView().getTopInventory();
        if (!historyInventories.contains(topInv)) return;

        event.setCancelled(true);

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= topInv.getSize()) return;

        HistoryGuiConfig cfg = plugin.getHistoryGuiConfig();

        // Back button - close history and reopen main sell GUI if viewing own
        if (rawSlot == cfg.getBackSlot()) {
            String ownerName = inventoryOwnerName.getOrDefault(topInv, player.getName());
            boolean viewingOwn = player.getName().equalsIgnoreCase(ownerName);

            player.closeInventory();

            if (viewingOwn && plugin.getEconomyManager().getEconomy() != null) {
                // Schedule on next tick to avoid close conflict
                Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.getSellGui().openGui(player), 1L);
            }
            return;
        }

        // Close button
        if (rawSlot == cfg.getCloseSlot()) {
            player.closeInventory();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHistoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Inventory topInv = event.getView().getTopInventory();
        if (!historyInventories.contains(topInv)) return;

        historyInventories.remove(topInv);
        inventoryOwnerName.remove(topInv);
        viewingHistory.remove(player.getUniqueId());
    }

    // ━━━━━━━━━━━━━━━ Utility ━━━━━━━━━━━━━━━

    public boolean isHistoryGui(Inventory inventory) {
        return historyInventories.contains(inventory);
    }

    public void closeAll() {
        historyInventories.clear();
        inventoryOwnerName.clear();
        viewingHistory.clear();
    }

    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
