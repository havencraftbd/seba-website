package com.haven.sell.gui;

import com.haven.sell.HavenSell;
import com.haven.sell.config.ConfigManager;
import com.haven.sell.config.GuiConfig;
import com.haven.sell.config.PriceManager;
import com.haven.sell.util.MessageUtil;
import com.haven.sell.util.ContainerCalculator;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles all inventory events for the sell GUI.
 * Manages item placement, selling, safe item return, and shulker box support.
 */
public class GuiListener implements Listener {

    private final HavenSell plugin;

    /** Tracks players currently processing a sell to prevent double-sells. */
    private final Set<UUID> processing = ConcurrentHashMap.newKeySet();

    /** Tracks players whose sell was already processed (for sell-on-close dedup). */
    private final Set<UUID> alreadySold = ConcurrentHashMap.newKeySet();

    public GuiListener(HavenSell plugin) {
        this.plugin = plugin;
    }

    // ━━━━━━━━━━━━━━━ Click Handling ━━━━━━━━━━━━━━━

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory topInventory = event.getView().getTopInventory();
        if (!plugin.getSellGui().isSellGui(topInventory)) return;

        // Block all interaction during sell processing
        if (processing.contains(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        int rawSlot = event.getRawSlot();
        Set<Integer> controlSlots = plugin.getSellGui().getControlSlots();

        // Clicking inside the sell GUI area
        if (rawSlot >= 0 && rawSlot < topInventory.getSize()) {
            if (controlSlots.contains(rawSlot)) {
                event.setCancelled(true);

                GuiConfig guiConfig = plugin.getGuiConfig();

                // Close button
                if (rawSlot == guiConfig.getCloseSlot() && guiConfig.isCloseEnabled()) {
                    player.closeInventory();
                    return;
                }

                // Balance head → open sell history
                if (rawSlot == guiConfig.getBalanceSlot() && guiConfig.isBalanceEnabled()) {
                    player.closeInventory();
                    Bukkit.getScheduler().runTaskLater(plugin,
                            () -> plugin.getHistoryGui().openOwnHistory(player), 1L);
                    return;
                }

                // Sell button
                if (rawSlot == guiConfig.getSellSlot() && guiConfig.isSellEnabled() && !plugin.getConfigManager().isSellOnClose()) {
                    processSell(player, topInventory, false);
                    return;
                }

                return;
            }

            // Non-control slots — allow placing items
            InventoryAction action = event.getAction();
            if (action == InventoryAction.COLLECT_TO_CURSOR) {
                event.setCancelled(true); // Prevent collecting from control slots
            }
        }
        // Clicking in player inventory — shift-click is handled by Bukkit naturally
    }

    // ━━━━━━━━━━━━━━━ Drag Handling ━━━━━━━━━━━━━━━

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory topInventory = event.getView().getTopInventory();
        if (!plugin.getSellGui().isSellGui(topInventory)) return;

        if (processing.contains(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        Set<Integer> controlSlots = plugin.getSellGui().getControlSlots();
        for (int slot : event.getRawSlots()) {
            if (slot >= 0 && slot < topInventory.getSize() && controlSlots.contains(slot)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // ━━━━━━━━━━━━━━━ Close Handling ━━━━━━━━━━━━━━━

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        Inventory topInventory = event.getView().getTopInventory();
        if (!plugin.getSellGui().isSellGui(topInventory)) return;

        UUID uuid = player.getUniqueId();

        // If the sell button was already clicked and processed, skip re-selling
        if (alreadySold.remove(uuid)) {
            // Items were already handled by processSell — just clean up the session
            returnAllItems(player, topInventory); // Return any leftovers
            plugin.getSellGui().removeSession(uuid, topInventory);
            return;
        }

        if (plugin.getConfigManager().isSellOnClose()) {
            processSell(player, topInventory, true);
        } else {
            returnAllItems(player, topInventory);
            plugin.getSellGui().removeSession(uuid, topInventory);
        }
    }

    // ━━━━━━━━━━━━━━━ Sell Processing ━━━━━━━━━━━━━━━

    /**
     * Processes the sale of all valid items in the GUI.
     * Supports shulker box content scanning when enabled in config.
     * Skips custom items (enchanted/renamed) to prevent accidental sales.
     */
    private void processSell(Player player, Inventory inventory, boolean isClosing) {
        UUID uuid = player.getUniqueId();

        // Prevent double-processing (atomic add)
        if (!processing.add(uuid)) return;

        try {
            PriceManager priceManager = plugin.getPriceManager();
            ConfigManager configManager = plugin.getConfigManager();
            Set<Integer> controlSlots = plugin.getSellGui().getControlSlots();
            boolean shulkerEnabled = configManager.isShulkerSupportEnabled();

            double totalMoney = 0.0;
            int totalItems = 0;
            String primaryMaterial = null;
            double highestValue = 0.0;

            for (int i = 0; i < inventory.getSize(); i++) {
                if (controlSlots.contains(i)) continue;

                ItemStack item = inventory.getItem(i);
                if (item == null || item.getType() == Material.AIR) continue;

                // ── Container handling (Shulker / Bundle) ──
                if (shulkerEnabled && ContainerCalculator.isShulkerBox(item.getType())) {
                    ContainerCalculator.ContainerResult result = ContainerCalculator.processShulkerBox(item, priceManager);

                    totalMoney += result.money;
                    totalItems += result.itemsSold;
                    
                    if (result.money > highestValue) {
                        highestValue = result.money;
                        primaryMaterial = result.primaryMaterial != null ? result.primaryMaterial : item.getType().name();
                    }

                    // Remove container from GUI
                    inventory.setItem(i, null);

                    // Return unsellable items and/or empty container
                    for (ItemStack returnedItem : result.toReturn) {
                        returnItem(player, returnedItem);
                    }
                    continue;
                } else if (ContainerCalculator.isBundle(item.getType())) {
                    ContainerCalculator.ContainerResult result = ContainerCalculator.processBundle(item, priceManager);

                    totalMoney += result.money;
                    totalItems += result.itemsSold;
                    
                    if (result.money > highestValue) {
                        highestValue = result.money;
                        primaryMaterial = result.primaryMaterial != null ? result.primaryMaterial : item.getType().name();
                    }

                    // Remove container from GUI
                    inventory.setItem(i, null);

                    // Return unsellable items and/or empty container
                    for (ItemStack returnedItem : result.toReturn) {
                        returnItem(player, returnedItem);
                    }
                    continue;
                }

                // ── Skip custom/enchanted items to prevent accidental sales ──
                if (hasCustomData(item)) {
                    returnItem(player, item);
                    inventory.setItem(i, null);
                    continue;
                }

                // ── Regular item handling ──
                double price = priceManager.getPrice(item);
                if (price > 0) {
                    double itemTotal = price * item.getAmount();
                    totalMoney += itemTotal;
                    totalItems += item.getAmount();
                    if (itemTotal > highestValue) {
                        highestValue = itemTotal;
                        primaryMaterial = item.getType().name();
                    }
                    inventory.setItem(i, null);
                } else {
                    // Not sellable — return to player safely
                    returnItem(player, item);
                    inventory.setItem(i, null);
                }
            }

            // ── Finalize sale ──
            if (totalItems > 0) {
                boolean depositSuccess = plugin.getEconomyManager().deposit(player, totalMoney);

                if (depositSuccess) {
                    plugin.getDataManager().addSellRecord(uuid, player.getName(), totalItems, totalMoney, primaryMaterial);

                    String formattedMoney = configManager.formatMoney(totalMoney);
                    MessageUtil.sendSellFeedback(plugin, player, formattedMoney, totalItems);
                    playSound(player, configManager.getSound("sell-success"));
                } else {
                    plugin.getLogger().warning("Economy deposit failed for " + player.getName()
                            + " — Amount: " + totalMoney);
                    MessageUtil.sendMessage(player, plugin.getMessagesManager().getMessage("economy-error"));
                }

                // Mark that we already sold so close handler doesn't re-sell
                if (!isClosing) {
                    alreadySold.add(uuid);
                }
            } else if (!isClosing) {
                // Only show "no items" when clicking the button, not on close
                MessageUtil.sendMessage(player, plugin.getMessagesManager().getMessage("no-items"));
                playSound(player, configManager.getSound("no-items"));
            }

            if (isClosing) {
                plugin.getSellGui().removeSession(uuid, inventory);
            }
        } finally {
            processing.remove(uuid);
        }
    }

    // ━━━━━━━━━━━━━━━ Custom Item Detection ━━━━━━━━━━━━━━━

    /**
     * Checks if an item has custom data that suggests it's a special item
     * and should NOT be auto-sold (e.g. enchanted tools, renamed items, items with lore).
     */
    private boolean hasCustomData(ItemStack item) {
        if (item == null) return false;
        
        // Allow all spawners to be sold regardless of custom names or lore 
        // (needed for plugins like Smart Spawners, SilkSpawners)
        Material type = item.getType();
        if (type == Material.SPAWNER ||
            type == Material.ENCHANTED_BOOK ||
            type == Material.POTION ||
            type == Material.SPLASH_POTION ||
            type == Material.LINGERING_POTION) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        // Items with lore are often custom/special items
        if (meta.hasLore()) return true;

        return false;
    }

    // ━━━━━━━━━━━━━━━ Item Return ━━━━━━━━━━━━━━━

    private void returnAllItems(Player player, Inventory inventory) {
        Set<Integer> controlSlots = plugin.getSellGui().getControlSlots();
        for (int i = 0; i < inventory.getSize(); i++) {
            if (controlSlots.contains(i)) continue;
            ItemStack item = inventory.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                returnItem(player, item);
                inventory.setItem(i, null);
            }
        }
    }

    /**
     * Returns an item to a player's inventory. Drops on ground if full.
     */
    private void returnItem(Player player, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;

        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(item.clone());
        for (ItemStack dropped : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), dropped);
        }
    }

    // ━━━━━━━━━━━━━━━ Utility ━━━━━━━━━━━━━━━

    private void playSound(Player player, String soundName) {
        if (soundName == null || soundName.isEmpty()) return;
        try {
            org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException ignored) {
            // Invalid sound name in config — silently ignore
        }
    }
}
