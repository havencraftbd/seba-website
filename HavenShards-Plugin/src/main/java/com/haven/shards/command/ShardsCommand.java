package com.haven.shards.command;

import com.haven.shards.HavenShards;
import com.haven.shards.gui.ShardsMenuGUI;
import com.haven.shards.manager.DatabaseManager;
import com.haven.shards.model.DailyPayData;
import com.haven.shards.util.ColorUtil;
import com.haven.shards.util.ItemUtil;
import com.haven.shards.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

public class ShardsCommand implements CommandExecutor, TabCompleter, Listener {

    private final HavenShards plugin;
    private final Map<UUID, Long> payCooldownMap = new ConcurrentHashMap<>();
    private final Map<UUID, DailyPayData> dailyPayCache = new ConcurrentHashMap<>();
    private final Cache<UUID, DailyPayData> recentDailyPayCache = CacheBuilder.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(2000)
            .build();
    private final Set<UUID> pendingDailyPayLimitLoad = ConcurrentHashMap.newKeySet();

    public ShardsCommand(HavenShards plugin) {
        this.plugin = plugin;
        String currentDayKey = LocalDate.now(getResetZoneId()).toString();
        for (Player online : Bukkit.getOnlinePlayers()) {
            loadPlayerDailyPayLimit(online.getUniqueId(), currentDayKey);
        }
    }

    private void loadPlayerDailyPayLimit(UUID u, String currentDayKey) {
        pendingDailyPayLimitLoad.add(u);
        plugin.getDatabaseManager().loadDailyPayLimitAsync(u, loaded -> {
            try {
                Player p = Bukkit.getPlayer(u);
                if (p == null || !p.isOnline()) {
                    return;
                }
                DailyPayData recent = recentDailyPayCache.getIfPresent(u);
                if (loaded == null) {
                    // DB error: Preserve existing or recent usage to prevent daily limit bypass
                    if (recent != null && currentDayKey.equals(recent.getDayKey())) {
                        dailyPayCache.put(u, recent);
                    }
                    return;
                }
                if (recent != null && currentDayKey.equals(recent.getDayKey())) {
                    // Merge: Prevent race condition if player quickly relogged before async save committed
                    double maxSent = Math.max(recent.getTotalSent(), loaded.getTotalSent());
                    int maxTx = Math.max(recent.getTransactionCount(), loaded.getTransactionCount());
                    DailyPayData merged = new DailyPayData(u, currentDayKey, maxSent, maxTx);
                    dailyPayCache.put(u, merged);
                    recentDailyPayCache.put(u, merged);
                } else if (currentDayKey.equals(loaded.getDayKey())) {
                    dailyPayCache.put(u, loaded);
                    recentDailyPayCache.put(u, loaded);
                } else if (!dailyPayCache.containsKey(u)) {
                    dailyPayCache.put(u, new DailyPayData(u, currentDayKey, 0.0, 0));
                }
            } finally {
                pendingDailyPayLimitLoad.remove(u);
            }
        });
    }

    private OfflinePlayer resolveTarget(String name) {
        if (name == null || name.trim().isEmpty()) return null;
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null) {
            return cached;
        }
        UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return Bukkit.getOfflinePlayer(offlineUuid);
    }

    private ZoneId getResetZoneId() {
        String timezone = plugin.getConfig().getString("pay.daily-limits.reset-timezone", "Asia/Dhaka");
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
        // Purge any stale day entries across midnight
        dailyPayCache.entrySet().removeIf(e -> !currentDayKey.equals(e.getValue().getDayKey()));
        loadPlayerDailyPayLimit(uuid, currentDayKey);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        payCooldownMap.remove(uuid);
        pendingDailyPayLimitLoad.remove(uuid);
        dailyPayCache.remove(uuid);
    }

    @EventHandler
    public void onReload(com.haven.shards.api.event.HavenShardsReloadEvent event) {
        payCooldownMap.clear();
        pendingDailyPayLimitLoad.clear();
        dailyPayCache.clear();
        String currentDayKey = LocalDate.now(getResetZoneId()).toString();
        for (Player online : Bukkit.getOnlinePlayers()) {
            loadPlayerDailyPayLimit(online.getUniqueId(), currentDayKey);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String symbol = plugin.getConfig().getString("currency.symbol", "✨");
        String currency = plugin.getConfig().getString("currency.name", "Shards");

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.getPrefix() + "Console usage: /shards [give|take|set|booster|reload]");
                return true;
            }
            if (plugin.getGeyserHook() != null && plugin.getGeyserHook().openBedrockDashboard(player)) {
                return true;
            }
            new ShardsMenuGUI(plugin, player).open(player);
            return true;
        }

        String sub = args[0].toLowerCase(java.util.Locale.ROOT);
        switch (sub) {
            case "quests", "quest" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(plugin.getPrefix() + plugin.getMessageManager().getMessage("console-only-player"));
                    return true;
                }
                if (!plugin.getConfig().getBoolean("daily-quests.enabled", true)) {
                    player.sendMessage(plugin.getPrefix() + plugin.getMessageManager().getMessage("quests-disabled"));
                    return true;
                }
                new com.haven.shards.gui.DailyQuestsGUI(plugin, player).open(player);
            }

            case "daily", "reward", "rewards" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(plugin.getPrefix() + plugin.getMessageManager().getMessage("console-only-player"));
                    return true;
                }
                if (!plugin.getConfig().getBoolean("daily-rewards.enabled", true)) {
                    player.sendMessage(plugin.getPrefix() + plugin.getMessageManager().getMessage("daily-disabled"));
                    return true;
                }
                new com.haven.shards.gui.DailyRewardGUI(plugin, player).open(player);
            }

            case "menu", "gui" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(plugin.getPrefix() + plugin.getMessageManager().getMessage("console-only-player"));
                    return true;
                }
                if (plugin.getGeyserHook() != null && plugin.getGeyserHook().openBedrockDashboard(player)) {
                    return true;
                }
                new ShardsMenuGUI(plugin, player).open(player);
            }

            case "balance", "bal" -> {
                if (args.length == 1) {
                    if (sender instanceof Player player) {
                        double bal = plugin.getShardManager().getBalance(player);
                        String rawMsg = plugin.getMessageManager().getMessage("balance");
                        player.sendMessage(plugin.getPrefix() + rawMsg
                                .replace("%symbol%", symbol)
                                .replace("%amount%", ItemUtil.formatMoney(bal))
                                .replace("%currency%", currency));
                    } else {
                        sender.sendMessage(plugin.getPrefix() + "Usage: /shards balance <player>");
                    }
                    return true;
                }

                OfflinePlayer target = resolveTarget(args[1]);
                if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
                    String rawMsg = plugin.getMessageManager().getMessage("player-not-found");
                    sender.sendMessage(plugin.getPrefix() + rawMsg.replace("%player%", args[1]));
                    if (sender instanceof Player p)
                        SoundUtil.playSound(plugin, p, "player-not-found");
                    return true;
                }

                if (target.isOnline() && target.getPlayer() != null) {
                    double bal = plugin.getShardManager().getBalance(target.getPlayer());
                    String rawMsg = plugin.getMessageManager().getMessage("balance-other");
                    sender.sendMessage(plugin.getPrefix() + rawMsg
                            .replace("%player%", target.getName() != null ? target.getName() : args[1])
                            .replace("%symbol%", symbol)
                            .replace("%amount%", ItemUtil.formatMoney(bal))
                            .replace("%currency%", currency));
                } else {
                    plugin.getDatabaseManager().loadPlayerBalanceAsync(target.getUniqueId(), bal -> {
                        String rawMsg = plugin.getMessageManager().getMessage("balance-other");
                        sender.sendMessage(plugin.getPrefix() + rawMsg
                                .replace("%player%", target.getName() != null ? target.getName() : args[1])
                                .replace("%symbol%", symbol)
                                .replace("%amount%", ItemUtil.formatMoney(bal))
                                .replace("%currency%", currency));
                    });
                }
            }

            case "pay" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(plugin.getPrefix() + plugin.getMessageManager().getMessage("console-only-player"));
                    return true;
                }
                if (!plugin.getConfig().getBoolean("pay.enabled", true)) {
                    player.sendMessage(plugin.getPrefix() + plugin.getMessageManager().getMessage("pay-disabled"));
                    SoundUtil.playSound(plugin, player, "no-permission");
                    return true;
                }
                if (args.length < 3) {
                    String usageMsg = plugin.getMessageManager().getMessage("pay-usage");
                    player.sendMessage(plugin.getPrefix() + usageMsg.replace("%label%", label));
                    SoundUtil.playSound(plugin, player, "invalid-amount");
                    return true;
                }

                long now = System.currentTimeMillis();
                int cooldownSeconds = plugin.getConfig().getInt("pay.cooldown-seconds", 3);
                Long lastPay = payCooldownMap.get(player.getUniqueId());
                if (lastPay != null && (now - lastPay) < (cooldownSeconds * 1000L)) {
                    long remainingSec = Math.max(1, ((cooldownSeconds * 1000L) - (now - lastPay) + 999) / 1000);
                    String cdMsg = plugin.getMessageManager().getMessage("pay-cooldown");
                    player.sendMessage(plugin.getPrefix() + cdMsg.replace("%seconds%", String.valueOf(remainingSec)));
                    return true;
                }

                if (pendingDailyPayLimitLoad.contains(player.getUniqueId())) {
                    String loadingMsg = plugin.getMessageManager().getMessage("pay-loading-limit");
                    if (loadingMsg == null || loadingMsg.isEmpty()) {
                        loadingMsg = "&cYour daily payment limit data is loading, please wait a moment...";
                    }
                    player.sendMessage(plugin.getPrefix() + ColorUtil.colorize(loadingMsg));
                    SoundUtil.playSound(plugin, player, "no-permission");
                    return true;
                }

                OfflinePlayer target = resolveTarget(args[1]);
                if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
                    String rawMsg = plugin.getMessageManager().getMessage("player-not-found");
                    player.sendMessage(plugin.getPrefix() + rawMsg.replace("%player%", args[1]));
                    SoundUtil.playSound(plugin, player, "player-not-found");
                    return true;
                }
                if (target.getUniqueId().equals(player.getUniqueId())) {
                    player.sendMessage(plugin.getPrefix() + plugin.getMessageManager().getMessage("cannot-pay-self"));
                    SoundUtil.playSound(plugin, player, "invalid-amount");
                    return true;
                }
                double amount;
                try {
                    amount = Double.parseDouble(args[2]);
                    if (amount <= 0 || !Double.isFinite(amount))
                        throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    player.sendMessage(plugin.getPrefix() + plugin.getMessageManager().getMessage("invalid-amount"));
                    SoundUtil.playSound(plugin, player, "invalid-amount");
                    return true;
                }

                double minPay = plugin.getConfig().getDouble("pay.minimum-amount", 1.0);
                if (amount < minPay) {
                    String minPayMsg = plugin.getMessageManager().getMessage("pay-minimum-not-met");
                    player.sendMessage(plugin.getPrefix() + minPayMsg
                            .replace("%min%", ItemUtil.formatMoney(minPay))
                            .replace("%symbol%", symbol)
                            .replace("%currency%", currency));
                    SoundUtil.playSound(plugin, player, "invalid-amount");
                    return true;
                }

                // Daily Payment Limit Checks (100% In-Memory & Timezone-Safe)
                String currentDayKey = LocalDate.now(getResetZoneId()).toString();
                boolean dailyLimitsEnabled = plugin.getConfig().getBoolean("pay.daily-limits.enabled", true);
                double maxDailyAmount = plugin.getConfig().getDouble("pay.daily-limits.max-daily-amount", 50000.0);
                int maxDailyTx = plugin.getConfig().getInt("pay.daily-limits.max-daily-transactions", 10);
                String limitBypassPerm = plugin.getConfig().getString("pay.daily-limits.bypass-permission", "havenshards.pay.limit.bypass");

                DailyPayData payData = dailyPayCache.computeIfAbsent(player.getUniqueId(), k -> new DailyPayData(k, currentDayKey, 0.0, 0));
                if (!currentDayKey.equals(payData.getDayKey())) {
                    payData.resetForNewDay(currentDayKey);
                }

                boolean hasLimitBypass = limitBypassPerm != null && !limitBypassPerm.isEmpty() && player.hasPermission(limitBypassPerm);

                if (dailyLimitsEnabled && !hasLimitBypass) {
                    if (maxDailyTx > 0 && payData.getTransactionCount() >= maxDailyTx) {
                        String msg = plugin.getMessageManager().getMessage("pay-daily-tx-limit-reached");
                        String timezone = plugin.getConfig().getString("pay.daily-limits.reset-timezone", "Asia/Dhaka");
                        player.sendMessage(plugin.getPrefix() + ColorUtil.colorize(msg
                                .replace("%limit%", String.valueOf(maxDailyTx))
                                .replace("%timezone%", timezone)));
                        SoundUtil.playSound(plugin, player, "no-permission");
                        return true;
                    }

                    if (maxDailyAmount > 0 && (payData.getTotalSent() + amount) > maxDailyAmount) {
                        double remaining = Math.max(0.0, maxDailyAmount - payData.getTotalSent());
                        String msg = plugin.getMessageManager().getMessage("pay-daily-amount-limit-reached");
                        player.sendMessage(plugin.getPrefix() + ColorUtil.colorize(msg
                                .replace("%limit%", ItemUtil.formatMoney(maxDailyAmount))
                                .replace("%remaining%", ItemUtil.formatMoney(remaining))
                                .replace("%symbol%", symbol)
                                .replace("%currency%", currency)));
                        SoundUtil.playSound(plugin, player, "invalid-amount");
                        return true;
                    }
                }

                boolean taxEnabled = plugin.getConfig().getBoolean("pay.tax.enabled", true);
                double taxPercentage = plugin.getConfig().getDouble("pay.tax.percentage", 5.0);
                String taxMode = plugin.getConfig().getString("pay.tax.mode", "CHARGE_SENDER").toUpperCase();
                String bypassPerm = plugin.getConfig().getString("pay.tax.bypass-permission", "havenshards.pay.notax");

                double calculatedTax = 0.0;
                if (taxEnabled && taxPercentage > 0
                        && (bypassPerm == null || bypassPerm.isEmpty() || !player.hasPermission(bypassPerm))) {
                    calculatedTax = (amount * taxPercentage) / 100.0;
                }
                final double taxAmount = calculatedTax;

                double totalDeductFromSender;
                double targetReceivedAmount;

                if (taxMode.equals("CHARGE_SENDER")) {
                    // bKash Style: Sender pays (amount + tax), receiver gets full amount
                    totalDeductFromSender = amount + taxAmount;
                    targetReceivedAmount = amount;
                } else {
                    // Standard Style: Sender pays amount, tax is deducted from receiver's cut
                    totalDeductFromSender = amount;
                    targetReceivedAmount = Math.max(0.0, amount - taxAmount);
                }

                final double finalDeduct = totalDeductFromSender;
                final double finalReceived = targetReceivedAmount;

                // Check recipient capacity against max-balance to prevent money destruction
                double maxBal = plugin.getConfig().getDouble("max-balance", 1000000000.0);
                double targetCurrentBal = target.isOnline() && target.getPlayer() != null 
                        ? plugin.getShardManager().getBalance(target.getPlayer()) 
                        : plugin.getDatabaseManager().getOfflineBalanceFast(target.getUniqueId());
                if (targetCurrentBal >= maxBal) {
                    player.sendMessage(plugin.getPrefix() + "§cThis player has already reached the maximum balance limit (" + ItemUtil.formatMoney(maxBal) + " " + currency + ")!");
                    SoundUtil.playSound(plugin, player, "invalid-amount");
                    return true;
                }
                if (targetCurrentBal + finalReceived > maxBal) {
                    double maxCanReceive = Math.max(0.0, maxBal - targetCurrentBal);
                    player.sendMessage(plugin.getPrefix() + "§cThis payment would exceed recipient's max balance! Maximum they can receive is §e" + ItemUtil.formatMoney(maxCanReceive) + " " + currency + "§c.");
                    SoundUtil.playSound(plugin, player, "invalid-amount");
                    return true;
                }

                if (!plugin.getShardManager().hasBalance(player, finalDeduct)) {
                    if (taxMode.equals("CHARGE_SENDER") && taxAmount > 0) {
                        String msg = plugin.getMessageManager().getMessage("pay-tax-insufficient");
                        player.sendMessage(plugin.getPrefix() + ColorUtil.colorize(msg
                                .replace("%total_required%", ItemUtil.formatMoney(finalDeduct))
                                .replace("%amount%", ItemUtil.formatMoney(amount))
                                .replace("%tax_percent%", ItemUtil.formatMoney(taxPercentage))
                                .replace("%tax_amount%", ItemUtil.formatMoney(taxAmount))
                                .replace("%symbol%", symbol)
                                .replace("%currency%", currency)));
                    } else {
                        player.sendMessage(
                                plugin.getPrefix() + plugin.getMessageManager().getMessage("insufficient-funds"));
                    }
                    SoundUtil.playSound(plugin, player, "insufficient-funds");
                    return true;
                }

                payCooldownMap.put(player.getUniqueId(), now);

                UUID senderUuid = player.getUniqueId();
                String senderName = player.getName();

                boolean removed = plugin.getShardManager().removeBalance(player, finalDeduct);
                if (!removed) {
                    player.sendMessage(
                            plugin.getPrefix() + plugin.getMessageManager().getMessage("insufficient-funds"));
                    SoundUtil.playSound(plugin, player, "insufficient-funds");
                    return true;
                }

                double senderBalAfter = plugin.getShardManager().getBalance(player);

                plugin.getShardManager().modifyOfflineBalance(target, finalReceived, success -> {
                    if (Boolean.TRUE.equals(success)) {
                        // Record daily limits usage on success
                        payData.recordTransaction(amount);
                        recentDailyPayCache.put(senderUuid, payData);
                        plugin.getDatabaseManager().saveDailyPayLimitAsync(senderUuid, currentDayKey, payData.getTotalSent(), payData.getTransactionCount());

                        String msgSent = plugin.getMessageManager().getMessage("pay-success");
                        player.sendMessage(plugin.getPrefix() + msgSent
                                .replace("%symbol%", symbol)
                                .replace("%amount%", ItemUtil.formatMoney(amount))
                                .replace("%currency%", currency)
                                .replace("%player%", target.getName() != null ? target.getName() : args[1]));

                        if (taxAmount > 0) {
                            String taxMsg = plugin.getMessageManager().getMessage("pay-tax-deducted");
                            player.sendMessage(plugin.getPrefix() + ColorUtil.colorize(taxMsg
                                    .replace("%tax_percent%", ItemUtil.formatMoney(taxPercentage))
                                    .replace("%tax_amount%", ItemUtil.formatMoney(taxAmount))
                                    .replace("%symbol%", symbol)
                                    .replace("%currency%", currency)));
                        }

                        SoundUtil.playSound(plugin, player, "pay-sent");

                        if (target.isOnline() && target.getPlayer() != null) {
                            Player targetPlayer = target.getPlayer();
                            String msgRecv = plugin.getMessageManager().getMessage("pay-received");
                            targetPlayer.sendMessage(plugin.getPrefix() + msgRecv
                                    .replace("%symbol%", symbol)
                                    .replace("%amount%", ItemUtil.formatMoney(finalReceived))
                                    .replace("%currency%", currency)
                                    .replace("%player%", senderName));
                            SoundUtil.playSound(plugin, targetPlayer, "pay-received");
                        }

                        plugin.getShardManager().getBalanceAsync(target, targetBalAfter -> {
                            plugin.getDatabaseManager().logTransactionAsync(
                                    senderUuid, senderName,
                                    target.getUniqueId(), target.getName() != null ? target.getName() : args[1],
                                    "PAY", amount, taxAmount, senderBalAfter,
                                    targetBalAfter != null ? targetBalAfter : 0.0);
                        });

                        plugin.getDiscordWebhookManager().logTransaction("PAY", senderName,
                                target.getName() != null ? target.getName() : args[1], amount, taxAmount);
                    } else {
                        plugin.getShardManager().modifyOfflineBalance(Bukkit.getOfflinePlayer(senderUuid), finalDeduct,
                                refundSuccess -> {
                                    if (Boolean.TRUE.equals(refundSuccess)) {
                                        player.sendMessage(plugin.getPrefix() + plugin.getMessageManager().getMessage("pay-db-error"));
                                    } else {
                                        player.sendMessage(plugin.getPrefix() + "§cPayment failed due to database error. Please contact an administrator.");
                                    }
                                });
                    }
                });
                return true;
            }

            case "withdraw" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(plugin.getPrefix() + plugin.getMessageManager().getMessage("console-only-player"));
                    return true;
                }
                if (!plugin.getConfig().getBoolean("physical-item.enabled", true)
                        || !plugin.getConfig().getBoolean("physical-item.withdraw-enabled", true)) {
                    player.sendMessage(plugin.getPrefix() + plugin.getMessageManager().getMessage("withdraw-disabled"));
                    SoundUtil.playSound(plugin, player, "no-permission");
                    return true;
                }
                if (args.length < 2) {
                    String usageMsg = plugin.getMessageManager().getMessage("withdraw-usage");
                    player.sendMessage(plugin.getPrefix() + usageMsg.replace("%label%", label));
                    SoundUtil.playSound(plugin, player, "invalid-amount");
                    return true;
                }
                double amount;
                try {
                    amount = Double.parseDouble(args[1]);
                    if (amount <= 0 || Double.isNaN(amount) || Double.isInfinite(amount))
                        throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    player.sendMessage(plugin.getPrefix() + plugin.getMessageManager().getMessage("invalid-amount"));
                    SoundUtil.playSound(plugin, player, "invalid-amount");
                    return true;
                }

                plugin.getWithdrawPromptManager().executeWithdrawal(player, amount);
                return true;
            }

            case "deposit", "redeem", "claim" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(plugin.getPrefix() + plugin.getMessageManager().getMessage("console-only-player"));
                    return true;
                }
                if (!plugin.getConfig().getBoolean("physical-item.enabled", true)
                        || !plugin.getConfig().getBoolean("physical-item.deposit-enabled", true)) {
                    player.sendMessage(plugin.getPrefix() + plugin.getMessageManager().getMessage("deposit-disabled"));
                    SoundUtil.playSound(plugin, player, "no-permission");
                    return true;
                }
                if (plugin.getConfig().getBoolean("physical-item.block-creative-mode", true) && player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
                    player.sendMessage(plugin.getPrefix() + plugin.getMessageManager().getMessage("deposit-creative-blocked"));
                    SoundUtil.playSound(plugin, player, "no-permission");
                    return true;
                }

                ItemStack item = player.getInventory().getItemInMainHand();
                if (item.getType().isAir()) {
                    player.sendMessage(plugin.getPrefix() + plugin.getMessageManager().getMessage("no-item-in-hand"));
                    SoundUtil.playSound(plugin, player, "invalid-amount");
                    return true;
                }

                double unitValue = ItemUtil.getShardItemValue(plugin, item);
                if (unitValue <= 0) {
                    player.sendMessage(plugin.getPrefix() + plugin.getMessageManager().getMessage("no-item-in-hand"));
                    SoundUtil.playSound(plugin, player, "invalid-amount");
                    return true;
                }

                String itemUuid = ItemUtil.getShardItemId(plugin, item);
                if (itemUuid == null || itemUuid.isEmpty()) {
                    player.sendMessage(plugin.getPrefix() + plugin.getMessageManager().getMessage("security-invalid-item"));
                    SoundUtil.playSound(plugin, player, "no-permission");
                    return true;
                }

                int stackAmount = item.getAmount();
                final double totalDeposit = unitValue * stackAmount;
                final ItemStack itemCopy = item.clone();
                final UUID playerUuid = player.getUniqueId();
                final String playerName = player.getName();
                player.getInventory().setItemInMainHand(null);

                // Track in-flight async verification so unloadPlayer retains balance safely
                plugin.getShardManager().startPendingOperation(playerUuid);

                // Anti-Dupe Ledger Verification
                plugin.getDatabaseManager().claimItemUuidAsync(itemUuid, playerUuid, totalDeposit,
                        result -> {
                            try {
                                if (result == 1) { // Success
                                    processDepositSuccess(playerUuid, playerName, totalDeposit, symbol, currency);
                                } else if (result == 2) { // Duplicate / Counterfeit
                                    Player p = Bukkit.getPlayer(playerUuid);
                                    if (p != null && p.isOnline()) {
                                        p.sendMessage(plugin.getPrefix() + plugin.getMessageManager().getMessage("security-invalid-item"));
                                        SoundUtil.playSound(plugin, p, "no-permission");
                                    }

                                    String adminAlert = plugin.getMessageManager().getMessage("security-admin-alert").replace("%player%", playerName);
                                    for (Player op : Bukkit.getOnlinePlayers()) {
                                        if (op.hasPermission("havenshards.admin")) {
                                            op.sendMessage(plugin.getPrefix() + adminAlert);
                                        }
                                    }

                                    plugin.getDiscordWebhookManager().logDupeAttempt(playerName, itemUuid, totalDeposit);
                                    // Fraudulent/Duplicated item is confiscated and NOT refunded
                                } else { // Database or network error (<= 0)
                                    Player p = Bukkit.getPlayer(playerUuid);
                                    if (p != null && p.isOnline()) {
                                        p.sendMessage(plugin.getPrefix() + plugin.getMessageManager().getMessage("pay-db-error"));
                                        SoundUtil.playSound(plugin, p, "no-permission");
                                        ItemUtil.giveOrDropItem(p, itemCopy);
                                    } else {
                                        // Player quit while verification was running; save to pending refunds so item is never lost!
                                        plugin.getDatabaseManager().savePendingRefundAsync(playerUuid, itemCopy);
                                    }
                                }
                            } finally {
                                plugin.getShardManager().finishPendingOperation(playerUuid);
                            }
                        });
            }

            case "history", "logs" -> {
                OfflinePlayer target = sender instanceof Player p ? p : null;
                if (args.length >= 2) {
                    if (!sender.hasPermission("havenshards.admin")) {
                        sender.sendMessage(plugin.getPrefix() + plugin.getMessageManager().getMessage("no-permission"));
                        if (sender instanceof Player p)
                            SoundUtil.playSound(plugin, p, "no-permission");
                        return true;
                    }
                    OfflinePlayer argTarget = resolveTarget(args[1]);
                    if (argTarget != null && (argTarget.hasPlayedBefore() || argTarget.isOnline())) {
                        target = argTarget;
                    }
                }
                if (target == null) {
                    String usageMsg = plugin.getMessageManager().getMessage("history-usage");
                    sender.sendMessage(plugin.getPrefix() + usageMsg.replace("%label%", label));
                    return true;
                }

                int defaultLimit = plugin.getConfig().getInt("security.history-default-limit", 15);
                int maxLimit = plugin.getConfig().getInt("security.history-max-limit", 50);
                int fetchLimit = defaultLimit;

                if (args.length >= 3) {
                    try {
                        int req = Integer.parseInt(args[2]);
                        if (req > 0)
                            fetchLimit = Math.min(req, maxLimit);
                    } catch (NumberFormatException ignored) {
                    }
                }

                final int finalLimit = fetchLimit;
                final OfflinePlayer finalTarget = target;

                plugin.getDatabaseManager().getTransactionsAsync(target.getUniqueId(), finalLimit, list -> {
                    if (sender instanceof Player player) {
                        new com.haven.shards.gui.TransactionHistoryGUI(plugin, finalTarget, list, 1).open(player);
                    } else {
                        String targetName = finalTarget.getName() != null ? finalTarget.getName() : "Player";
                        sender.sendMessage(ColorUtil.colorize("&b&lTransaction History for &f" + targetName
                                + " &8(&7Last " + finalLimit + "&8)&b&l:"));
                        if (list.isEmpty()) {
                            sender.sendMessage(ColorUtil.colorize("&7No transaction history recorded yet."));
                            return;
                        }
                        for (com.haven.shards.model.TransactionEntry entry : list) {
                            String sName = entry.getSenderName() != null ? entry.getSenderName() : "SYSTEM";
                            String rName = entry.getReceiverName() != null ? entry.getReceiverName() : "SYSTEM";
                            sender.sendMessage(ColorUtil.colorize("&8[#" + entry.getId() + "] &e" + entry.getType()
                                     + " &8| &f" + sName + " ➔ " + rName + " &8: &a" + symbol + " "
                                     + ItemUtil.formatMoney(entry.getAmount())));
                        }
                    }
                });
            }

            case "booster" -> {
                if (!sender.hasPermission("havenshards.admin")) {
                    sender.sendMessage(plugin.getPrefix() + plugin.getMessageManager().getMessage("no-permission"));
                    if (sender instanceof Player p)
                        SoundUtil.playSound(plugin, p, "no-permission");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(plugin.getPrefix() + plugin.getMessageManager().getMessage("booster-usage"));
                    return true;
                }
                if (args[1].equalsIgnoreCase("off") || args[1].equalsIgnoreCase("stop")) {
                    plugin.getBoosterManager().stopBooster();
                    sender.sendMessage(plugin.getPrefix() + plugin.getMessageManager().getMessage("booster-off"));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(plugin.getPrefix() + plugin.getMessageManager().getMessage("booster-usage"));
                    return true;
                }
                double mult;
                int mins;
                try {
                    mult = Double.parseDouble(args[1]);
                    mins = com.haven.shards.util.TimeUtil.parseDurationToMinutes(args[2]);
                    if (mult < 1.0 || !Double.isFinite(mult) || mins <= 0)
                        throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    sender.sendMessage(plugin.getPrefix() + plugin.getMessageManager().getMessage("booster-usage"));
                    return true;
                }
                plugin.getBoosterManager().setBooster(mult, mins);
            }

            case "top", "leaderboard" -> {
                int page = 1;
                if (args.length > 1) {
                    try {
                        page = Integer.parseInt(args[1]);
                    } catch (Exception ignored) {
                    }
                }
                handleTopCommand(sender, page, label);
            }

            case "give" -> {
                if (!sender.hasPermission("havenshards.admin")) {
                    sender.sendMessage(plugin.getPrefix() + plugin.getMessageManager().getMessage("no-permission"));
                    if (sender instanceof Player p)
                        SoundUtil.playSound(plugin, p, "no-permission");
                    return true;
                }
                if (args.length < 3) {
                    String usageMsg = plugin.getMessageManager().getMessage("give-usage");
                    sender.sendMessage(plugin.getPrefix() + usageMsg.replace("%label%", label));
                    if (sender instanceof Player p)
                        SoundUtil.playSound(plugin, p, "invalid-amount");
                    return true;
                }

                OfflinePlayer target = resolveTarget(args[1]);
                if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
                    String msg = plugin.getMessageManager().getMessage("player-not-found");
                    sender.sendMessage(plugin.getPrefix() + msg.replace("%player%", args[1]));
                    if (sender instanceof Player p)
                        SoundUtil.playSound(plugin, p, "player-not-found");
                    return true;
                }
                double amount;
                try {
                    amount = Double.parseDouble(args[2]);
                    if (amount <= 0 || !Double.isFinite(amount))
                        throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    sender.sendMessage(plugin.getPrefix() + plugin.getMessageManager().getMessage("invalid-amount"));
                    if (sender instanceof Player p)
                        SoundUtil.playSound(plugin, p, "invalid-amount");
                    return true;
                }

                plugin.getShardManager().modifyOfflineBalance(target, amount, success -> {
                    String targetName = target.getName() != null ? target.getName() : args[1];
                    if (!Boolean.TRUE.equals(success)) {
                        String msgFail = plugin.getMessageManager().getMessage("admin-give-failed");
                        if (msgFail == null || msgFail.isEmpty() || msgFail.equals("admin-give-failed")) {
                            msgFail = "&cFailed to give shards to %player% (database error or balance cap reached).";
                        }
                        sender.sendMessage(plugin.getPrefix() + ColorUtil.colorize(msgFail.replace("%player%", targetName)));
                        if (sender instanceof Player p)
                            SoundUtil.playSound(plugin, p, "invalid-amount");
                        return;
                    }
                    String msgGive = plugin.getMessageManager().getMessage("admin-give");
                    sender.sendMessage(plugin.getPrefix()
                            + msgGive.replace("%symbol%", symbol).replace("%amount%", ItemUtil.formatMoney(amount))
                                    .replace("%currency%", currency).replace("%player%", targetName));
                    if (sender instanceof Player p)
                        SoundUtil.playSound(plugin, p, "admin-give");
                    if (target.isOnline() && target.getPlayer() != null) {
                        String msgReceived = plugin.getMessageManager().getMessage("admin-give-received");
                        if (msgReceived != null && !msgReceived.isEmpty()) {
                            target.getPlayer().sendMessage(plugin.getPrefix() 
                                    + msgReceived.replace("%symbol%", symbol)
                                                 .replace("%amount%", ItemUtil.formatMoney(amount))
                                                 .replace("%currency%", currency));
                        }
                        SoundUtil.playSound(plugin, target.getPlayer(), "pay-received");
                    }

                    // Audit Logging
                    plugin.getShardManager().getBalanceAsync(target, targetBalAfter -> {
                        plugin.getDatabaseManager().logTransactionAsync(sender instanceof Player p ? p.getUniqueId() : null,
                                sender.getName(), target.getUniqueId(), targetName, "ADMIN_GIVE", amount,
                                0.0, targetBalAfter != null ? targetBalAfter : 0.0);
                    });
                    plugin.getDiscordWebhookManager().logTransaction("ADMIN_GIVE", sender.getName(), targetName,
                            amount);
                });
            }

            case "take" -> {
                if (!sender.hasPermission("havenshards.admin")) {
                    sender.sendMessage(plugin.getPrefix() + plugin.getMessageManager().getMessage("no-permission"));
                    if (sender instanceof Player p)
                        SoundUtil.playSound(plugin, p, "no-permission");
                    return true;
                }
                if (args.length < 3) {
                    String usageMsg = plugin.getMessageManager().getMessage("take-usage");
                    sender.sendMessage(plugin.getPrefix() + usageMsg.replace("%label%", label));
                    if (sender instanceof Player p)
                        SoundUtil.playSound(plugin, p, "invalid-amount");
                    return true;
                }

                OfflinePlayer target = resolveTarget(args[1]);
                if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
                    String msg = plugin.getMessageManager().getMessage("player-not-found");
                    sender.sendMessage(plugin.getPrefix() + msg.replace("%player%", args[1]));
                    if (sender instanceof Player p)
                        SoundUtil.playSound(plugin, p, "player-not-found");
                    return true;
                }
                double amount;
                try {
                    amount = Double.parseDouble(args[2]);
                    if (amount <= 0 || !Double.isFinite(amount))
                        throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    sender.sendMessage(plugin.getPrefix() + plugin.getMessageManager().getMessage("invalid-amount"));
                    if (sender instanceof Player p)
                        SoundUtil.playSound(plugin, p, "invalid-amount");
                    return true;
                }

                plugin.getShardManager().modifyOfflineBalance(target, -amount, success -> {
                    String targetName = target.getName() != null ? target.getName() : args[1];
                    if (!Boolean.TRUE.equals(success)) {
                        String msgFail = plugin.getMessageManager().getMessage("admin-take-failed");
                        if (msgFail == null || msgFail.isEmpty() || msgFail.equals("admin-take-failed")) {
                            msgFail = "&cFailed to take shards from %player% (insufficient balance or database error).";
                        }
                        sender.sendMessage(plugin.getPrefix() + ColorUtil.colorize(msgFail.replace("%player%", targetName)));
                        if (sender instanceof Player p)
                            SoundUtil.playSound(plugin, p, "insufficient-funds");
                        return;
                    }
                    String msgTake = plugin.getMessageManager().getMessage("admin-take");
                    sender.sendMessage(plugin.getPrefix()
                            + msgTake.replace("%symbol%", symbol).replace("%amount%", ItemUtil.formatMoney(amount))
                                    .replace("%currency%", currency).replace("%player%", targetName));
                    if (sender instanceof Player p)
                        SoundUtil.playSound(plugin, p, "admin-take");

                    if (target.isOnline() && target.getPlayer() != null) {
                        String msgReceived = plugin.getMessageManager().getMessage("admin-take-received");
                        if (msgReceived != null && !msgReceived.isEmpty()) {
                            target.getPlayer().sendMessage(plugin.getPrefix() 
                                    + msgReceived.replace("%symbol%", symbol)
                                                 .replace("%amount%", ItemUtil.formatMoney(amount))
                                                 .replace("%currency%", currency));
                        }
                    }

                    // Audit Logging
                    plugin.getShardManager().getBalanceAsync(target, targetBalAfter -> {
                        plugin.getDatabaseManager().logTransactionAsync(sender instanceof Player p ? p.getUniqueId() : null,
                                sender.getName(), target.getUniqueId(), targetName, "ADMIN_TAKE", amount,
                                0.0, targetBalAfter != null ? targetBalAfter : 0.0);
                    });
                    plugin.getDiscordWebhookManager().logTransaction("ADMIN_TAKE", sender.getName(), targetName,
                            amount);
                });
            }

            case "set" -> {
                if (!sender.hasPermission("havenshards.admin")) {
                    sender.sendMessage(plugin.getPrefix() + plugin.getMessageManager().getMessage("no-permission"));
                    if (sender instanceof Player p)
                        SoundUtil.playSound(plugin, p, "no-permission");
                    return true;
                }
                if (args.length < 3) {
                    String usageMsg = plugin.getMessageManager().getMessage("set-usage");
                    sender.sendMessage(plugin.getPrefix() + usageMsg.replace("%label%", label));
                    if (sender instanceof Player p)
                        SoundUtil.playSound(plugin, p, "invalid-amount");
                    return true;
                }

                OfflinePlayer target = resolveTarget(args[1]);
                if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
                    String msg = plugin.getMessageManager().getMessage("player-not-found");
                    sender.sendMessage(plugin.getPrefix() + msg.replace("%player%", args[1]));
                    if (sender instanceof Player p)
                        SoundUtil.playSound(plugin, p, "player-not-found");
                    return true;
                }
                double amount;
                try {
                    amount = Double.parseDouble(args[2]);
                    if (amount < 0 || !Double.isFinite(amount))
                        throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    sender.sendMessage(plugin.getPrefix() + plugin.getMessageManager().getMessage("invalid-amount"));
                    if (sender instanceof Player p)
                        SoundUtil.playSound(plugin, p, "invalid-amount");
                    return true;
                }

                double maxBal = plugin.getConfig().getDouble("max-balance", 1000000000.0);
                amount = Math.round(Math.min(Math.max(0.0, amount), maxBal) * 100.0) / 100.0;

                if (target.isOnline() && target.getPlayer() != null) {
                    plugin.getShardManager().setBalance(target.getPlayer(), amount);
                    String msgReceived = plugin.getMessageManager().getMessage("admin-set-received");
                    if (msgReceived != null && !msgReceived.isEmpty()) {
                        target.getPlayer().sendMessage(plugin.getPrefix() 
                                + msgReceived.replace("%symbol%", symbol)
                                             .replace("%amount%", ItemUtil.formatMoney(amount))
                                             .replace("%currency%", currency));
                    }
                } else {
                    plugin.getDatabaseManager().savePlayerBalanceAsync(target.getUniqueId(), amount);
                }
                String targetName = target.getName() != null ? target.getName() : args[1];
                String msgSet = plugin.getMessageManager().getMessage("admin-set");
                sender.sendMessage(plugin.getPrefix()
                        + msgSet.replace("%symbol%", symbol).replace("%amount%", ItemUtil.formatMoney(amount))
                                .replace("%currency%", currency).replace("%player%", targetName));
                if (sender instanceof Player p)
                    SoundUtil.playSound(plugin, p, "admin-set");

                // Audit Logging
                plugin.getDatabaseManager().logTransactionAsync(sender instanceof Player p ? p.getUniqueId() : null,
                        sender.getName(), target.getUniqueId(), targetName, "ADMIN_SET", amount, 0, amount);
                plugin.getDiscordWebhookManager().logTransaction("ADMIN_SET", sender.getName(), targetName, amount);
            }

            case "reload" -> {
                if (!sender.hasPermission("havenshards.reload")) {
                    sender.sendMessage(plugin.getPrefix() + plugin.getMessageManager().getMessage("no-permission"));
                    if (sender instanceof Player p)
                        SoundUtil.playSound(plugin, p, "no-permission");
                    return true;
                }
                plugin.reloadConfig();
                com.haven.shards.util.ItemUtil.reloadPattern(plugin);

                // Fire custom reload event so all managers/listeners can reload their caches
                org.bukkit.Bukkit.getPluginManager().callEvent(new com.haven.shards.api.event.HavenShardsReloadEvent());

                sender.sendMessage(plugin.getPrefix() + plugin.getMessageManager().getMessage("reload-success"));
                if (sender instanceof Player p)
                    SoundUtil.playSound(plugin, p, "reload-success");
            }

            default -> {
                List<String> usage = plugin.getMessageManager().getMessageList("usage");
                for (String u : usage) {
                    sender.sendMessage(u);
                }
            }
        }

        return true;
    }

    private void processDepositSuccess(UUID playerUuid, String playerName, double totalDeposit, String symbol, String currency) {
        plugin.getShardManager().addBalance(playerUuid, totalDeposit);
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline() && plugin.getShardManager().isLoaded(playerUuid)) {
            String msgDep = plugin.getMessageManager().getMessage("deposit-success");
            player.sendMessage(plugin.getPrefix() + msgDep.replace("%symbol%", symbol)
                    .replace("%amount%", ItemUtil.formatMoney(totalDeposit)).replace("%currency%", currency));
            SoundUtil.playSound(plugin, player, "redeem-shard");

            // Audit Logging
            double newBal = plugin.getShardManager().getBalance(player);
            plugin.getDatabaseManager().logTransactionAsync(null, null, playerUuid, playerName, "DEPOSIT",
                    totalDeposit, 0, newBal);
            plugin.getDiscordWebhookManager().logTransaction("DEPOSIT", null, playerName, totalDeposit);
        } else {
            // Player quit while async claim was processing
            plugin.getDatabaseManager().logTransactionAsync(null, null, playerUuid, playerName, "DEPOSIT",
                    totalDeposit, 0, -1);
            plugin.getDiscordWebhookManager().logTransaction("DEPOSIT", null, playerName, totalDeposit);
        }
    }

    /**
     * STRICT ONLINE-ONLY TAB COMPLETION:
     * Offline player names are NEVER exposed in tab completion.
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        boolean isShard = alias != null && alias.equalsIgnoreCase("shard");

        if (args.length == 1) {
            if (isShard) {
                // /shard <tab> -> Player subcommands
                completions.add("menu");
                completions.add("daily");
                completions.add("quests");
                completions.add("balance");
                completions.add("pay");
                completions.add("withdraw");
                completions.add("deposit");
                completions.add("history");
                completions.add("top");
            } else {
                // /shards <tab> -> Admin subcommands (and general if not admin)
                if (sender.hasPermission("havenshards.reload")) {
                    completions.add("reload");
                }
                if (sender.hasPermission("havenshards.admin")) {
                    completions.add("give");
                    completions.add("take");
                    completions.add("set");
                    completions.add("booster");
                    completions.add("history");
                }
                completions.add("menu");
                completions.add("daily");
                completions.add("quests");
                completions.add("balance");
                completions.add("pay");
                completions.add("withdraw");
                completions.add("deposit");
                completions.add("top");
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(java.util.Locale.ROOT);
            boolean isAdmin = sender.hasPermission("havenshards.admin");
            if (sub.equals("pay") || (sub.equals("balance") && (sender.hasPermission("havenshards.balance.others") || isAdmin))
                    || ((sub.equals("history") || sub.equals("logs") || sub.equals("give") || sub.equals("take") || sub.equals("set")) && isAdmin)) {
                // STRICTLY ONLINE PLAYERS ONLY
                for (Player p : Bukkit.getOnlinePlayers()) {
                    completions.add(p.getName());
                }
            } else if (sub.equals("booster") && isAdmin) {
                completions.add("2.0");
                completions.add("3.0");
                completions.add("off");
            }
        } else if (args.length == 3) {
            String sub = args[0].toLowerCase(java.util.Locale.ROOT);
            if (sub.equals("booster") && sender.hasPermission("havenshards.admin")) {
                completions.add("30m");
                completions.add("1h");
                completions.add("12h");
                completions.add("1d");
            } else if (sub.equals("history") || sub.equals("logs")) {
                completions.add("10");
                completions.add("20");
                completions.add("50");
            }
        }
        return completions.stream()
                .filter(s -> s.toLowerCase(java.util.Locale.ROOT).startsWith(args[args.length - 1].toLowerCase(java.util.Locale.ROOT)))
                .collect(Collectors.toList());
    }

    private void handleTopCommand(CommandSender sender, int pageInput, String label) {
        int page = Math.max(1, pageInput);
        int pageSize = 10;

        String symbol = plugin.getConfig().getString("currency.symbol", "✨");
        String currency = plugin.getConfig().getString("currency.name", "Shards");

        double totalServerShards = plugin.getDatabaseManager().getCachedTotalBalance();
        List<DatabaseManager.TopEntry> topList = plugin.getDatabaseManager().getCachedTopEntries();

        if (topList.isEmpty()) {
            sender.sendMessage(plugin.getPrefix() + plugin.getMessageManager().getMessage("top-empty"));
            return;
        }

        int totalItems = topList.size();
        int totalPages = (int) Math.ceil((double) totalItems / pageSize);
        if (totalPages < 1)
            totalPages = 1;
        int currentPage = Math.min(page, totalPages);

        int startIndex = (currentPage - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, totalItems);

        sender.sendMessage(ColorUtil.colorize("&8&m----&r &b&lTop " + currency + " &8&m--&r &ePage " + currentPage + "/" + totalPages + " &8&m----"));
        sender.sendMessage(ColorUtil.colorize("&6Server Total: &a" + symbol + " " + ItemUtil.formatMoney(totalServerShards)));

        for (int i = startIndex; i < endIndex; i++) {
            DatabaseManager.TopEntry entry = topList.get(i);
            int rank = i + 1;
            String name = entry.getName();

            String rankColor = switch (rank) {
                case 1 -> "&#FFD700" + rank + "."; // Gold
                case 2 -> "&#C0C0C0" + rank + "."; // Silver
                case 3 -> "&#CD7F32" + rank + "."; // Bronze
                default -> "&e" + rank + ".";
            };

            sender.sendMessage(ColorUtil.colorize(rankColor + " &f" + name + "&8, &a" + symbol + " "
                    + ItemUtil.formatMoney(entry.getBalance())));
        }

        if (currentPage < totalPages) {
            sender.sendMessage(ColorUtil.colorize("&7Type &e/" + label + (label.contains("top") ? "" : " top") + " "
                    + (currentPage + 1) + " &7to view next page."));
        }
    }
}
