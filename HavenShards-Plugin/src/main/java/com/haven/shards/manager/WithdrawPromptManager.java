package com.haven.shards.manager;

import com.haven.shards.HavenShards;
import com.haven.shards.model.DailyWithdrawData;
import com.haven.shards.util.ColorUtil;
import com.haven.shards.util.ItemUtil;
import com.haven.shards.util.SoundUtil;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

public class WithdrawPromptManager implements Listener {

    private final HavenShards plugin;
    private final Map<UUID, Long> withdrawCooldownMap = new ConcurrentHashMap<>();
    private final Map<UUID, DailyWithdrawData> dailyWithdrawCache = new ConcurrentHashMap<>();
    private final Cache<UUID, DailyWithdrawData> recentDailyWithdrawCache = CacheBuilder.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(2000)
            .build();
    private final Set<UUID> pendingDailyLimitLoad = ConcurrentHashMap.newKeySet();

    public WithdrawPromptManager(HavenShards plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        String currentDayKey = LocalDate.now(getResetZoneId()).toString();
        for (Player online : Bukkit.getOnlinePlayers()) {
            loadPlayerDailyWithdrawLimit(online.getUniqueId(), currentDayKey);
        }
    }

    private void loadPlayerDailyWithdrawLimit(UUID u, String currentDayKey) {
        pendingDailyLimitLoad.add(u);
        plugin.getDatabaseManager().loadDailyWithdrawLimitAsync(u, loaded -> {
            try {
                Player p = Bukkit.getPlayer(u);
                if (p == null || !p.isOnline()) {
                    return;
                }
                DailyWithdrawData recent = recentDailyWithdrawCache.getIfPresent(u);
                if (loaded == null) {
                    // DB error: Preserve existing or recent usage to prevent daily limit bypass
                    if (recent != null && currentDayKey.equals(recent.getDayKey())) {
                        dailyWithdrawCache.put(u, recent);
                    }
                    return;
                }
                if (recent != null && currentDayKey.equals(recent.getDayKey())) {
                    // Merge: Prevent race condition if player quickly relogged before async save committed
                    double maxWithdrawn = Math.max(recent.getTotalWithdrawn(), loaded.getTotalWithdrawn());
                    int maxCount = Math.max(recent.getWithdrawCount(), loaded.getWithdrawCount());
                    DailyWithdrawData merged = new DailyWithdrawData(u, currentDayKey, maxWithdrawn, maxCount);
                    dailyWithdrawCache.put(u, merged);
                    recentDailyWithdrawCache.put(u, merged);
                } else if (currentDayKey.equals(loaded.getDayKey())) {
                    dailyWithdrawCache.put(u, loaded);
                    recentDailyWithdrawCache.put(u, loaded);
                } else if (!dailyWithdrawCache.containsKey(u)) {
                    dailyWithdrawCache.put(u, new DailyWithdrawData(u, currentDayKey, 0.0, 0));
                }
            } finally {
                pendingDailyLimitLoad.remove(u);
            }
        });
    }

    private ZoneId getResetZoneId() {
        String timezone = plugin.getConfig().getString("physical-item.daily-limits.reset-timezone", "Asia/Dhaka");
        try {
            return ZoneId.of(timezone);
        } catch (Exception e) {
            return ZoneId.of("Asia/Dhaka");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        String currentDayKey = LocalDate.now(getResetZoneId()).toString();
        loadPlayerDailyWithdrawLimit(uuid, currentDayKey);
    }

    public void startPrompt(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();

        if (pendingDailyLimitLoad.contains(uuid)) {
            String loadingMsg = plugin.getMessageManager().getMessage("withdraw-loading-limit");
            if (loadingMsg == null || loadingMsg.isEmpty()) {
                loadingMsg = "&cYour daily limit data is loading, please wait a moment...";
            }
            player.sendMessage(plugin.getPrefix() + ColorUtil.colorize(loadingMsg));
            SoundUtil.playSound(plugin, player, "no-permission");
            return;
        }

        if (!plugin.getConfig().getBoolean("physical-item.enabled", true)
                || !plugin.getConfig().getBoolean("physical-item.withdraw-enabled", true)) {
            player.sendMessage(plugin.getPrefix() + plugin.getMessageManager().getMessage("withdraw-disabled"));
            SoundUtil.playSound(plugin, player, "no-permission");
            return;
        }

        player.closeInventory();
        SoundUtil.playSound(plugin, player, "admin-set");

        ItemStack item = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.colorize("&eEnter Amount"));
            item.setItemMeta(meta);
        }

        ItemStack backItem = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = backItem.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(ColorUtil.colorize("&c« Back to Menu"));
            backMeta.setLore(Collections.singletonList(ColorUtil.colorize("&7Click to return to Shards Menu")));
            backItem.setItemMeta(backMeta);
        }

        new AnvilGUI.Builder()
            .plugin(plugin)
            .title("Withdraw Shards")
            .itemLeft(item)
            .itemRight(backItem)
            .text("10")
            .onClick((slot, stateSnapshot) -> {
                if (slot == AnvilGUI.Slot.INPUT_RIGHT) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        new com.haven.shards.gui.ShardsMenuGUI(plugin, stateSnapshot.getPlayer()).open(stateSnapshot.getPlayer());
                    });
                    return Collections.singletonList(AnvilGUI.ResponseAction.close());
                }

                if (slot != AnvilGUI.Slot.OUTPUT) {
                    return Collections.emptyList();
                }

                String text = stateSnapshot.getText().trim();
                
                if (text.equalsIgnoreCase("cancel") || text.equalsIgnoreCase("back")) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        new com.haven.shards.gui.ShardsMenuGUI(plugin, stateSnapshot.getPlayer()).open(stateSnapshot.getPlayer());
                    });
                    return Collections.singletonList(AnvilGUI.ResponseAction.close());
                }

                try {
                    double amount = Double.parseDouble(text);
                    if (amount <= 0 || Double.isNaN(amount) || Double.isInfinite(amount)) {
                        throw new NumberFormatException();
                    }

                    boolean success = executeWithdrawal(stateSnapshot.getPlayer(), amount);
                    if (success) {
                        return Collections.singletonList(AnvilGUI.ResponseAction.close());
                    } else {
                        return Collections.singletonList(AnvilGUI.ResponseAction.close());
                    }
                } catch (NumberFormatException e) {
                    SoundUtil.playSound(plugin, stateSnapshot.getPlayer(), "invalid-amount");
                    return Collections.singletonList(
                            AnvilGUI.ResponseAction.replaceInputText("Invalid Amount")
                    );
                }
            })
            .open(player);
    }

    public boolean executeWithdrawal(Player player, double amount) {
        if (player == null) return false;
        UUID uuid = player.getUniqueId();

        if (pendingDailyLimitLoad.contains(uuid)) {
            String loadingMsg = plugin.getMessageManager().getMessage("withdraw-loading-limit");
            if (loadingMsg == null || loadingMsg.isEmpty()) {
                loadingMsg = "&cYour daily limit data is loading, please wait a moment...";
            }
            player.sendMessage(plugin.getPrefix() + ColorUtil.colorize(loadingMsg));
            SoundUtil.playSound(plugin, player, "no-permission");
            return false;
        }

        if (plugin.getConfig().getBoolean("physical-item.block-creative-mode", true) && player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            player.sendMessage(plugin.getPrefix() + plugin.getMessageManager().getMessage("withdraw-creative-blocked"));
            SoundUtil.playSound(plugin, player, "no-permission");
            return false;
        }

        if (!plugin.getConfig().getBoolean("physical-item.enabled", true)
                || !plugin.getConfig().getBoolean("physical-item.withdraw-enabled", true)) {
            player.sendMessage(plugin.getPrefix() + plugin.getMessageManager().getMessage("withdraw-disabled"));
            SoundUtil.playSound(plugin, player, "no-permission");
            return false;
        }

        String symbol = plugin.getConfig().getString("currency.symbol", "✨");
        String currency = plugin.getConfig().getString("currency.name", "Shards");

        // 1. Cooldown Check
        long now = System.currentTimeMillis();
        int cooldownSec = plugin.getConfig().getInt("physical-item.cooldown-seconds", 5);
        Long lastWithdraw = withdrawCooldownMap.get(uuid);
        if (cooldownSec > 0 && lastWithdraw != null && (now - lastWithdraw) < (cooldownSec * 1000L)) {
            long remainingSec = Math.max(1, ((cooldownSec * 1000L) - (now - lastWithdraw) + 999) / 1000);
            String cdMsg = plugin.getMessageManager().getMessage("withdraw-cooldown");
            player.sendMessage(plugin.getPrefix() + ColorUtil.colorize(cdMsg.replace("%seconds%", String.valueOf(remainingSec))));
            return false;
        }

        // 2. Minimum Amount Check
        double minWithdraw = plugin.getConfig().getDouble("physical-item.minimum-withdraw", 1.0);
        if (amount < minWithdraw) {
            String minMsg = plugin.getMessageManager().getMessage("withdraw-minimum-not-met");
            player.sendMessage(plugin.getPrefix() + ColorUtil.colorize(minMsg
                    .replace("%min%", ItemUtil.formatMoney(minWithdraw))
                    .replace("%symbol%", symbol)
                    .replace("%currency%", currency)
            ));
            SoundUtil.playSound(plugin, player, "invalid-amount");
            return false;
        }

        // 3. Maximum Amount Check
        double maxWithdraw = plugin.getConfig().getDouble("physical-item.maximum-withdraw", 2000.0);
        if (maxWithdraw > 0 && amount > maxWithdraw) {
            String maxMsg = plugin.getMessageManager().getMessage("withdraw-maximum-exceeded");
            player.sendMessage(plugin.getPrefix() + ColorUtil.colorize(maxMsg
                    .replace("%max%", ItemUtil.formatMoney(maxWithdraw))
                    .replace("%symbol%", symbol)
                    .replace("%currency%", currency)
            ));
            SoundUtil.playSound(plugin, player, "invalid-amount");
            return false;
        }

        // 4. Daily Withdraw Limits Check (In-Memory & BD Time Reset)
        String currentDayKey = LocalDate.now(getResetZoneId()).toString();
        boolean dailyLimitsEnabled = plugin.getConfig().getBoolean("physical-item.daily-limits.enabled", true);
        double maxDailyAmount = plugin.getConfig().getDouble("physical-item.daily-limits.max-daily-amount", 3000.0);
        int maxDailyWithdraws = plugin.getConfig().getInt("physical-item.daily-limits.max-daily-withdraws", 5);
        String limitBypassPerm = plugin.getConfig().getString("physical-item.daily-limits.bypass-permission", "havenshards.withdraw.limit.bypass");

        DailyWithdrawData wData = dailyWithdrawCache.computeIfAbsent(uuid, k -> new DailyWithdrawData(k, currentDayKey, 0.0, 0));
        if (!currentDayKey.equals(wData.getDayKey())) {
            wData.resetForNewDay(currentDayKey);
        }

        boolean hasLimitBypass = limitBypassPerm != null && !limitBypassPerm.isEmpty() && player.hasPermission(limitBypassPerm);
        if (dailyLimitsEnabled && !hasLimitBypass) {
            if (maxDailyWithdraws > 0 && wData.getWithdrawCount() >= maxDailyWithdraws) {
                String timezone = plugin.getConfig().getString("physical-item.daily-limits.reset-timezone", "Asia/Dhaka");
                String msg = plugin.getMessageManager().getMessage("withdraw-daily-count-limit-reached");
                player.sendMessage(plugin.getPrefix() + ColorUtil.colorize(msg
                        .replace("%limit%", String.valueOf(maxDailyWithdraws))
                        .replace("%timezone%", timezone)
                ));
                SoundUtil.playSound(plugin, player, "no-permission");
                return false;
            }

            if (maxDailyAmount > 0 && (wData.getTotalWithdrawn() + amount) > maxDailyAmount) {
                double remaining = Math.max(0.0, maxDailyAmount - wData.getTotalWithdrawn());
                String msg = plugin.getMessageManager().getMessage("withdraw-daily-amount-limit-reached");
                player.sendMessage(plugin.getPrefix() + ColorUtil.colorize(msg
                        .replace("%limit%", ItemUtil.formatMoney(maxDailyAmount))
                        .replace("%remaining%", ItemUtil.formatMoney(remaining))
                        .replace("%symbol%", symbol)
                        .replace("%currency%", currency)
                ));
                SoundUtil.playSound(plugin, player, "invalid-amount");
                return false;
            }
        }

        // 5. Minting Fee / Tax Check
        boolean taxEnabled = plugin.getConfig().getBoolean("physical-item.tax.enabled", false);
        double taxPercent = plugin.getConfig().getDouble("physical-item.tax.percentage", 2.0);
        String taxBypassPerm = plugin.getConfig().getString("physical-item.tax.bypass-permission", "havenshards.withdraw.notax");

        double calculatedTax = 0.0;
        if (taxEnabled && taxPercent > 0 && (taxBypassPerm == null || taxBypassPerm.isEmpty() || !player.hasPermission(taxBypassPerm))) {
            calculatedTax = (amount * taxPercent) / 100.0;
        }
        final double taxAmount = calculatedTax;
        final double totalDeduct = amount + taxAmount;

        if (!plugin.getShardManager().hasBalance(player, totalDeduct)) {
            if (taxAmount > 0) {
                String msg = plugin.getMessageManager().getMessage("withdraw-tax-insufficient");
                player.sendMessage(plugin.getPrefix() + ColorUtil.colorize(msg
                        .replace("%total_required%", ItemUtil.formatMoney(totalDeduct))
                        .replace("%amount%", ItemUtil.formatMoney(amount))
                        .replace("%tax_percent%", ItemUtil.formatMoney(taxPercent))
                        .replace("%tax_amount%", ItemUtil.formatMoney(taxAmount))
                        .replace("%symbol%", symbol)
                        .replace("%currency%", currency)
                ));
            } else {
                player.sendMessage(plugin.getPrefix() + plugin.getMessageManager().getMessage("insufficient-funds"));
            }
            SoundUtil.playSound(plugin, player, "insufficient-funds");
            return false;
        }

        // 6. Inventory Check
        ItemStack shardItem = ItemUtil.createShardItem(plugin, amount);
        String fullAction = plugin.getConfig().getString("physical-item.full-inventory-action", "DROP_ON_GROUND").toUpperCase();
        boolean isFull = ItemUtil.isInventoryFull(player, shardItem);

        if (isFull && (fullAction.contains("BLOCK") || fullAction.contains("PREVENT"))) {
            player.sendMessage(plugin.getPrefix() + plugin.getMessageManager().getMessage("inventory-full"));
            SoundUtil.playSound(plugin, player, "insufficient-funds");
            return false;
        }

        // 7. Deduct Balance
        boolean removed = plugin.getShardManager().removeBalance(player, totalDeduct);
        if (!removed) {
            player.sendMessage(plugin.getPrefix() + plugin.getMessageManager().getMessage("insufficient-funds"));
            SoundUtil.playSound(plugin, player, "insufficient-funds");
            return false;
        }

        // 8. Mint Item (Dispatched only after verified inventory and successful balance deduction)
        String itemUuid = ItemUtil.getShardItemId(plugin, shardItem);
        if (itemUuid != null && !itemUuid.isEmpty()) {
            plugin.getDatabaseManager().mintItemUuidAsync(itemUuid, player.getUniqueId(), amount);
        }

        // Record cooldown & daily limits
        withdrawCooldownMap.put(uuid, now);
        wData.recordWithdraw(amount);
        recentDailyWithdrawCache.put(uuid, wData);
        plugin.getDatabaseManager().saveDailyWithdrawLimitAsync(uuid, currentDayKey, wData.getTotalWithdrawn(), wData.getWithdrawCount());

        Map<Integer, ItemStack> leftover = player.getInventory().addItem(shardItem);
        if (!leftover.isEmpty()) {
            for (ItemStack item : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
        }

        double newBal = plugin.getShardManager().getBalance(player);
        plugin.getDatabaseManager().logTransactionAsync(uuid, player.getName(), null, "PHYSICAL_ITEM", "WITHDRAW", amount, taxAmount, newBal, 0);
        plugin.getDiscordWebhookManager().logTransaction("WITHDRAW", player.getName(), "PHYSICAL_ITEM", amount, taxAmount);

        String msgWithd = plugin.getMessageManager().getMessage("withdraw-success");
        player.sendMessage(plugin.getPrefix() + msgWithd
                .replace("%symbol%", symbol)
                .replace("%amount%", ItemUtil.formatMoney(amount))
                .replace("%currency%", currency)
        );

        if (taxAmount > 0) {
            String taxMsg = plugin.getMessageManager().getMessage("withdraw-tax-deducted");
            player.sendMessage(plugin.getPrefix() + ColorUtil.colorize(taxMsg
                    .replace("%tax_percent%", ItemUtil.formatMoney(taxPercent))
                    .replace("%tax_amount%", ItemUtil.formatMoney(taxAmount))
                    .replace("%symbol%", symbol)
                    .replace("%currency%", currency)
            ));
        }

        SoundUtil.playSound(plugin, player, "withdraw-shard");
        return true;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        withdrawCooldownMap.remove(uuid);
        pendingDailyLimitLoad.remove(uuid);
        dailyWithdrawCache.remove(uuid);
    }

    public void onDisable() {
        org.bukkit.event.HandlerList.unregisterAll(this);
        withdrawCooldownMap.clear();
        pendingDailyLimitLoad.clear();
        dailyWithdrawCache.clear();
    }
}
