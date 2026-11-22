package com.haven.sell.listener;

import com.haven.sell.HavenSell;
import com.haven.sell.config.ConfigManager;
import com.haven.sell.config.PriceManager;
import com.haven.sell.util.ContainerCalculator;
import com.haven.sell.util.MessageUtil;
import com.haven.sell.util.SellAxeUtil;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Event listener that processes Sell Axe chest interactions.
 */
public class SellAxeListener implements Listener {

    private final HavenSell plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public SellAxeListener(HavenSell plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.isCancelled()) return;

        // We only care about right-clicking blocks
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        ItemStack offhand = player.getInventory().getItemInOffHand();

        // Check if holding a valid Sell Axe
        if (!SellAxeUtil.isSellAxe(hand)) {
            if (SellAxeUtil.isSellAxe(offhand)) {
                player.sendMessage(plugin.getMessagesManager().getMessage("sell-axe-mainhand-only"));
                event.setCancelled(true);
            }
            return;
        }

        // Check if another plugin already cancelled the block click (protection plugins)
        // IMPORTANT: Must check BEFORE setCancelled, because setCancelled sets useInteractedBlock to DENY
        if (event.useInteractedBlock() == org.bukkit.event.Event.Result.DENY) {
            player.sendMessage(plugin.getMessagesManager().getMessage("sell-axe-no-access"));
            return;
        }

        // Cancel the event to prevent opening the container GUI
        event.setCancelled(true);

        // Global and Sell Axe toggle check
        if (!plugin.getConfigManager().isSellEnabled() || !plugin.getConfigManager().isSellAxeEnabled()) {
            player.sendMessage(plugin.getMessagesManager().getMessage("sell-disabled"));
            return;
        }

        // Expiry check
        if (SellAxeUtil.isExpired(hand)) {
            player.getInventory().setItemInMainHand(null);
            player.sendMessage(plugin.getMessagesManager().getMessage("sell-axe-expired"));
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            return;
        }

        // Check permission
        if (!player.hasPermission("havensell.use")) {
            player.sendMessage(plugin.getMessagesManager().getMessage("no-permission"));
            return;
        }

        // Cooldown check (500ms)
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        if (now - cooldowns.getOrDefault(uuid, 0L) < 500L) {
            player.sendMessage(plugin.getMessagesManager().getMessage("sell-axe-cooldown"));
            return;
        }
        cooldowns.put(uuid, now);

        if (event.getClickedBlock() == null) return;
        BlockState state = event.getClickedBlock().getState();

        // Must be a container block (Chest, Barrel, Shulker Box, Hopper, etc.)
        if (!(state instanceof Container container)) return;

        // Verify that the vault economy exists
        if (plugin.getEconomyManager().getEconomy() == null) {
            player.sendMessage(plugin.getMessagesManager().getMessage("economy-error"));
            return;
        }

        Inventory inventory;
        if (container.getInventory().getHolder() instanceof org.bukkit.block.DoubleChest doubleChest) {
            inventory = doubleChest.getInventory();
        } else {
            inventory = container.getInventory();
        }
        PriceManager priceManager = plugin.getPriceManager();
        ConfigManager configManager = plugin.getConfigManager();
        boolean shulkerEnabled = configManager.isShulkerSupportEnabled();

        double totalMoney = 0.0;
        int totalItems = 0;
        String primaryMaterial = null;
        double highestValue = 0.0;

        // Perform chest inventory scan
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() == Material.AIR) continue;

            // Skip custom items (except specific ones like spawners allowed by hasCustomData)
            if (hasCustomData(item)) continue;

            // Containers (Shulker/Bundle)
            if (shulkerEnabled && ContainerCalculator.isShulkerBox(item.getType())) {
                ContainerCalculator.ContainerResult result = ContainerCalculator.processShulkerBox(item, priceManager);
                if (result.itemsSold > 0) {
                    totalMoney += result.money;
                    totalItems += result.itemsSold;

                    if (result.money > highestValue) {
                        highestValue = result.money;
                        primaryMaterial = result.primaryMaterial != null ? result.primaryMaterial : item.getType().name();
                    }

                    inventory.setItem(i, null);

                    // Place returned containers/unsellables back in the chest, drop overflow
                    for (ItemStack returned : result.toReturn) {
                        var overflow = inventory.addItem(returned.clone());
                        for (ItemStack dropped : overflow.values()) {
                            player.getWorld().dropItemNaturally(player.getLocation(), dropped);
                        }
                    }
                }
                continue;
            } else if (ContainerCalculator.isBundle(item.getType())) {
                ContainerCalculator.ContainerResult result = ContainerCalculator.processBundle(item, priceManager);
                if (result.itemsSold > 0) {
                    totalMoney += result.money;
                    totalItems += result.itemsSold;

                    if (result.money > highestValue) {
                        highestValue = result.money;
                        primaryMaterial = result.primaryMaterial != null ? result.primaryMaterial : item.getType().name();
                    }

                    inventory.setItem(i, null);

                    for (ItemStack returned : result.toReturn) {
                        var overflow = inventory.addItem(returned.clone());
                        for (ItemStack dropped : overflow.values()) {
                            player.getWorld().dropItemNaturally(player.getLocation(), dropped);
                        }
                    }
                }
                continue;
            }

            // Regular item
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
            }
        }

        // Commiting the sale
        if (totalItems > 0) {
            boolean depositSuccess = plugin.getEconomyManager().deposit(player, totalMoney);
            if (depositSuccess) {
                // Log record to player's sell history database
                plugin.getDataManager().addSellRecord(player.getUniqueId(), player.getName(), totalItems, totalMoney, primaryMaterial);

                // Play custom success sound
                playSound(player, configManager.getSellAxeSound());

                // Send title and chat feedbacks
                String formattedMoney = configManager.formatMoney(totalMoney);
                MessageUtil.sendSellFeedback(plugin, player, formattedMoney, totalItems);

                // Decrement axe uses unless player is in Creative mode
                if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                    SellAxeUtil.decrementUsesAndCheckBreak(player, hand, plugin);
                }
            } else {
                plugin.getLogger().warning("Economy deposit failed for " + player.getName() + " via Sell Axe — Amount: " + totalMoney);
                player.sendMessage(plugin.getMessagesManager().getMessage("economy-error"));
            }
        } else {
            player.sendMessage(plugin.getMessagesManager().getMessage("sell-axe-no-items"));
            playSound(player, configManager.getSound("no-items"));
        }
    }

    /**
     * Checks if the item stack contains custom data (excluding items like spawners, potions etc.).
     */
    private boolean hasCustomData(ItemStack item) {
        if (item == null) return false;

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
        if (meta.hasLore()) return true;
        return false;
    }

    private void playSound(Player player, String soundName) {
        if (soundName == null || soundName.isEmpty()) return;
        try {
            org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException ignored) {}
    }
}
