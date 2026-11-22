package com.haven.shards.listener;

import com.haven.shards.HavenShards;
import com.haven.shards.util.ColorUtil;
import com.haven.shards.util.ItemUtil;
import com.haven.shards.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class ItemRedeemListener implements Listener {

    private final HavenShards plugin;

    public ItemRedeemListener(HavenShards plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getDatabaseManager().cachePlayerName(player.getUniqueId(), player.getName());
        plugin.getShardManager().loadPlayer(player);
        plugin.getDailyRewardManager().loadPlayer(player);

        // Process any pending item refunds from failed async verifications while offline
        plugin.getDatabaseManager().claimPendingRefundsAsync(player.getUniqueId(), refunds -> {
            if (refunds != null && !refunds.isEmpty()) {
                Player p = Bukkit.getPlayer(player.getUniqueId());
                if (p != null && p.isOnline()) {
                    for (ItemStack refundItem : refunds) {
                        ItemUtil.giveOrDropItem(p, refundItem);
                    }
                    p.sendMessage(plugin.getPrefix() + ColorUtil.colorize("&aYour unverified shard item(s) have been returned to your inventory."));
                    SoundUtil.playSound(plugin, p, "redeem-shard");
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getPlaytimeRewardManager().unloadPlayer(player);
        plugin.getDailyRewardManager().unloadPlayer(player);
        plugin.getShardManager().unloadPlayer(player);
        plugin.getDatabaseManager().removeCachedPlayerName(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Global toggle for physical items
        if (!plugin.getConfig().getBoolean("physical-item.enabled", true)
                || !plugin.getConfig().getBoolean("physical-item.deposit-enabled", true)) {
            return;
        }

        // Toggle for right-click redeeming
        if (!plugin.getConfig().getBoolean("physical-item.right-click-redeem", true)) {
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (event.getHand() == null) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) return;

        double value = ItemUtil.getShardItemValue(plugin, item);
        if (value <= 0) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        // 1. Creative Mode Block
        if (plugin.getConfig().getBoolean("physical-item.block-creative-mode", true) && player.getGameMode() == GameMode.CREATIVE) {
            player.sendMessage(plugin.getPrefix() + plugin.getMessageManager().getMessage("redeem-creative-blocked"));
            SoundUtil.playSound(plugin, player, "no-permission");
            return;
        }

        String itemUuid = ItemUtil.getShardItemId(plugin, item);
        int amount = item.getAmount();

        // Clone single item for safe refund before deducting 1 item stack from hand immediately
        ItemStack singleItemRefund = item.clone();
        singleItemRefund.setAmount(1);

        if (amount > 1) {
            item.setAmount(amount - 1);
            ItemUtil.regenerateShardItemId(plugin, item);
            String newUuid = ItemUtil.getShardItemId(plugin, item);
            if (newUuid != null) {
                double remainingValue = Math.round(value * (amount - 1) * 100.0) / 100.0;
                plugin.getDatabaseManager().mintItemUuidAsync(newUuid, player.getUniqueId(), remainingValue);
            }
            if (event.getHand() != null) {
                player.getInventory().setItem(event.getHand(), item);
            }
        } else {
            if (event.getHand() != null) {
                player.getInventory().setItem(event.getHand(), null);
            }
        }

        // 2. Anti-Dupe Ledger Verification
        if (itemUuid != null && !itemUuid.isEmpty()) {
            final UUID playerUuid = player.getUniqueId();
            final String playerName = player.getName();

            // Track in-flight async verification so unloadPlayer retains balance safely
            plugin.getShardManager().startPendingOperation(playerUuid);

            plugin.getDatabaseManager().claimItemUuidAsync(itemUuid, playerUuid, value, result -> {
                try {
                    if (result == 1) { // Success
                        processSuccessfulRedeem(playerUuid, playerName, value);
                    } else if (result == 2) { // Duplicate / Counterfeit
                        Player p = Bukkit.getPlayer(playerUuid);
                        if (p != null && p.isOnline()) {
                            p.sendMessage(plugin.getPrefix() + plugin.getMessageManager().getMessage("security-invalid-item"));
                            SoundUtil.playSound(plugin, p, "no-permission");
                        }

                        // Notify admins
                        String adminAlert = plugin.getMessageManager().getMessage("security-admin-alert").replace("%player%", playerName);
                        for (Player op : Bukkit.getOnlinePlayers()) {
                            if (op.hasPermission("havenshards.admin")) {
                                op.sendMessage(plugin.getPrefix() + adminAlert);
                            }
                        }

                        // Trigger Discord Alert
                        plugin.getDiscordWebhookManager().logDupeAttempt(playerName, itemUuid, value);
                        // Counterfeit item is confiscated (already removed)
                    } else { // 0 = Error
                        // Refund the item safely due to database/network error
                        Player p = Bukkit.getPlayer(playerUuid);
                        if (p != null && p.isOnline()) {
                            p.sendMessage(plugin.getPrefix() + ColorUtil.colorize("&cNetwork error while verifying item. Please try again later."));
                            SoundUtil.playSound(plugin, p, "no-permission");
                            ItemUtil.giveOrDropItem(p, singleItemRefund);
                        } else {
                            // Player quit while verification was running: save to pending refunds so item is never lost!
                            plugin.getDatabaseManager().savePendingRefundAsync(playerUuid, singleItemRefund);
                        }
                    }
                } finally {
                    plugin.getShardManager().finishPendingOperation(playerUuid);
                }
            });
        } else {
            // Unregistered item without UUID is treated as counterfeit
            player.sendMessage(plugin.getPrefix() + plugin.getMessageManager().getMessage("security-invalid-item"));
            SoundUtil.playSound(plugin, player, "no-permission");
        }
    }

    private void processSuccessfulRedeem(UUID playerUuid, String playerName, double value) {
        plugin.getShardManager().addBalance(playerUuid, value);

        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline() && plugin.getShardManager().isLoaded(playerUuid)) {
            SoundUtil.playSound(plugin, player, "redeem-shard");

            String symbol = plugin.getConfig().getString("currency.symbol", "✨");
            String currency = plugin.getConfig().getString("currency.name", "Shards");
            String rawMsg = plugin.getMessageManager().getMessage("redeem-success");
            String msg = rawMsg
                    .replace("%symbol%", symbol)
                    .replace("%amount%", ItemUtil.formatMoney(value))
                    .replace("%currency%", currency);
            player.sendMessage(plugin.getPrefix() + msg);

            // Audit Logging
            double newBal = plugin.getShardManager().getBalance(player);
            plugin.getDatabaseManager().logTransactionAsync(null, null, playerUuid, playerName, "REDEEM", value, 0, newBal);
            plugin.getDiscordWebhookManager().logTransaction("REDEEM", null, playerName, value);
        } else {
            // Player quit while verification was running; balance was credited safely to DB/cache
            plugin.getDatabaseManager().logTransactionAsync(null, null, playerUuid, playerName, "REDEEM", value, 0, -1);
            plugin.getDiscordWebhookManager().logTransaction("REDEEM", null, playerName, value);
        }
    }

    // 3. Block Shard Items in Anvils
    @EventHandler(priority = EventPriority.HIGH)
    public void onAnvilPrepare(PrepareAnvilEvent event) {
        for (ItemStack item : event.getInventory().getContents()) {
            if (item != null && ItemUtil.getShardItemValue(plugin, item) > 0) {
                event.setResult(null);
                return;
            }
        }
    }

    // 4. Block Shard Items in Crafting Grid
    @EventHandler(priority = EventPriority.HIGH)
    public void onCraftPrepare(PrepareItemCraftEvent event) {
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (item != null && ItemUtil.getShardItemValue(plugin, item) > 0) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }
}

