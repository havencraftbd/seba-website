package com.haven.sell.command;

import com.haven.sell.HavenSell;
import com.haven.sell.config.ConfigManager;
import com.haven.sell.config.PriceManager;
import com.haven.sell.data.PlayerData;
import com.haven.sell.util.MessageUtil;
import com.haven.sell.util.ContainerCalculator;
import com.haven.sell.util.NumberFormatter;
import com.haven.sell.util.SellAxeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Handles the /sell command and its subcommands:
 *   /sell               → opens sell GUI
 *   /sell hand           → sells the item in hand
 *   /sell all            → sells all sellable items in inventory
 *   /sell price          → checks the price of item in hand
 *   /sell reload         → reloads all configs (admin)
 *   /sell history        → opens own history GUI
 *   /sell history <p>    → opens target player's history GUI (admin)
 */
public class SellCommand implements CommandExecutor, TabCompleter {

    private final HavenSell plugin;
    private final java.util.Map<UUID, Long> sellAllCooldowns = new java.util.concurrent.ConcurrentHashMap<>();

    public SellCommand(HavenSell plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            return handleSellOpen(sender);
        }

        String sub = args[0].toLowerCase();
        return switch (sub) {
            case "reload"  -> handleReload(sender);
            case "history" -> handleHistory(sender, args);
            case "hand"    -> handleSellHand(sender);
            case "all"     -> handleSellAll(sender);
            case "price"   -> handleSellPrice(sender);
            case "giveaxe", "givewand" -> handleGiveAxe(sender, args);
            default -> {
                String usage = sender.hasPermission("havensell.admin")
                        ? "&cUnknown subcommand. Usage: /sell [hand|all|price|reload|history|giveaxe]"
                        : "&cUnknown subcommand. Usage: /sell [hand|all|price|history]";
                MessageUtil.sendMessage(sender, plugin.getMessagesManager().getPrefix() + usage);
                yield true;
            }
        };
    }

    // ━━━━━━━━━━━━━━━ /sell ━━━━━━━━━━━━━━━

    private boolean handleSellOpen(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, plugin.getMessagesManager().getMessage("players-only"));
            return true;
        }
        // Global sell toggle — checked before permission so the message is clear
        if (!plugin.getConfigManager().isSellEnabled()) {
            MessageUtil.sendMessage(player, plugin.getMessagesManager().getMessage("sell-disabled"));
            return true;
        }
        if (!player.hasPermission("havensell.use")) {
            MessageUtil.sendMessage(player, plugin.getMessagesManager().getMessage("no-permission"));
            return true;
        }
        if (plugin.getEconomyManager().getEconomy() == null) {
            MessageUtil.sendMessage(player, plugin.getMessagesManager().getMessage("economy-error"));
            return true;
        }
        plugin.getSellGui().openGui(player);
        return true;
    }

    // ━━━━━━━━━━━━━━━ /sell price ━━━━━━━━━━━━━━━

    private boolean handleSellPrice(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, plugin.getMessagesManager().getMessage("players-only"));
            return true;
        }
        if (!player.hasPermission("havensell.use")) {
            MessageUtil.sendMessage(player, plugin.getMessagesManager().getMessage("no-permission"));
            return true;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR) {
            MessageUtil.sendMessage(player, plugin.getMessagesManager().getMessage("price-no-item"));
            return true;
        }

        // Use precise item for lookup (allows potions and enchanted books)
        PriceManager priceManager = plugin.getPriceManager();
        ConfigManager configManager = plugin.getConfigManager();
        double price = priceManager.getPrice(hand);

        if (price <= 0) {
            MessageUtil.sendMessage(player, plugin.getMessagesManager().getMessage("price-no-value"));
            return true;
        }

        String symbol = configManager.getCurrencySymbol();
        String formattedPrice = configManager.formatMoney(price);
        int amount = hand.getAmount();
        double totalValue = price * amount;
        String formattedTotal = configManager.formatMoney(totalValue);

        // Send price info
        String prefix = plugin.getMessagesManager().getPrefix();
        MessageUtil.sendMessage(player, prefix + plugin.getMessagesManager().getRawMessage("price-per-item")
                .replace("{symbol}", symbol)
                .replace("{price}", formattedPrice));

        if (amount > 1) {
            MessageUtil.sendMessage(player, prefix + plugin.getMessagesManager().getRawMessage("price-stack")
                    .replace("{symbol}", symbol)
                    .replace("{total}", formattedTotal)
                    .replace("{amount}", String.valueOf(amount)));
        }

        return true;
    }

    // ━━━━━━━━━━━━━━━ /sell hand ━━━━━━━━━━━━━━━

    private boolean handleSellHand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, plugin.getMessagesManager().getMessage("players-only"));
            return true;
        }
        if (!plugin.getConfigManager().isSellEnabled()) {
            MessageUtil.sendMessage(player, plugin.getMessagesManager().getMessage("sell-disabled"));
            return true;
        }
        if (!player.hasPermission("havensell.use")) {
            MessageUtil.sendMessage(player, plugin.getMessagesManager().getMessage("no-permission"));
            return true;
        }
        if (plugin.getEconomyManager().getEconomy() == null) {
            MessageUtil.sendMessage(player, plugin.getMessagesManager().getMessage("economy-error"));
            return true;
        }

        Runnable onConfirm = () -> {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getType() == Material.AIR) {
                MessageUtil.sendMessage(player, plugin.getMessagesManager().getMessage("no-items"));
                return;
            }

            // Skip custom items
            if (hasCustomData(hand)) {
                MessageUtil.sendMessage(player, plugin.getMessagesManager().getMessage("no-items"));
                return;
            }

            PriceManager priceManager = plugin.getPriceManager();
            ConfigManager configManager = plugin.getConfigManager();

            double totalMoney = 0.0;
            int totalItems = 0;
            String primaryMaterial = null;
            double highestValue = 0.0;

            // Handle containers (shulker/bundle)
            if (configManager.isShulkerSupportEnabled() && ContainerCalculator.isShulkerBox(hand.getType())) {
                ContainerCalculator.ContainerResult result = ContainerCalculator.processShulkerBox(hand, priceManager);
                totalMoney += result.money;
                totalItems += result.itemsSold;
                
                if (result.money > highestValue) {
                    highestValue = result.money;
                    primaryMaterial = result.primaryMaterial != null ? result.primaryMaterial : hand.getType().name();
                }

                if (totalItems > 0 || result.toReturn.isEmpty()) {
                    player.getInventory().setItemInMainHand(null);
                }

                for (ItemStack returned : result.toReturn) {
                    giveOrDrop(player, returned);
                }
            } else if (ContainerCalculator.isBundle(hand.getType())) {
                ContainerCalculator.ContainerResult result = ContainerCalculator.processBundle(hand, priceManager);
                totalMoney += result.money;
                totalItems += result.itemsSold;
                
                if (result.money > highestValue) {
                    highestValue = result.money;
                    primaryMaterial = result.primaryMaterial != null ? result.primaryMaterial : hand.getType().name();
                }

                if (totalItems > 0 || result.toReturn.isEmpty()) {
                    player.getInventory().setItemInMainHand(null);
                }

                for (ItemStack returned : result.toReturn) {
                    giveOrDrop(player, returned);
                }
            } else {
                double price = priceManager.getPrice(hand);
                if (price <= 0) {
                    MessageUtil.sendMessage(player, plugin.getMessagesManager().getMessage("no-items"));
                    return;
                }
                double itemTotal = price * hand.getAmount();
                totalMoney = itemTotal;
                totalItems = hand.getAmount();
                primaryMaterial = hand.getType().name();
                player.getInventory().setItemInMainHand(null);
            }

            if (totalItems > 0) {
                finalizeSale(player, totalMoney, totalItems, primaryMaterial);
            } else {
                MessageUtil.sendMessage(player, plugin.getMessagesManager().getMessage("no-items"));
            }
        };

        plugin.getConfirmGui().openConfirmGui(player, plugin.getGuiConfig().getConfirmTitleHand(), onConfirm);
        return true;
    }

    // ━━━━━━━━━━━━━━━ /sell all ━━━━━━━━━━━━━━━

    private boolean handleSellAll(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, plugin.getMessagesManager().getMessage("players-only"));
            return true;
        }
        if (!plugin.getConfigManager().isSellEnabled()) {
            MessageUtil.sendMessage(player, plugin.getMessagesManager().getMessage("sell-disabled"));
            return true;
        }
        if (!player.hasPermission("havensell.use")) {
            MessageUtil.sendMessage(player, plugin.getMessagesManager().getMessage("no-permission"));
            return true;
        }
        if (plugin.getEconomyManager().getEconomy() == null) {
            MessageUtil.sendMessage(player, plugin.getMessagesManager().getMessage("economy-error"));
            return true;
        }

        // Cooldown check for /sell all
        UUID uuid = player.getUniqueId();
        int cooldownSec = plugin.getConfigManager().getSellAllCooldownSeconds();
        if (cooldownSec > 0 && !player.hasPermission("havensell.bypass.cooldown") && !player.hasPermission("havensell.admin")) {
            long lastUse = sellAllCooldowns.getOrDefault(uuid, 0L);
            long now = System.currentTimeMillis();
            long diffMs = now - lastUse;
            long cooldownMs = cooldownSec * 1000L;
            if (diffMs < cooldownMs) {
                int remainingSec = (int) Math.ceil((cooldownMs - diffMs) / 1000.0);
                String msg = plugin.getMessagesManager().getMessage("sell-all-cooldown")
                        .replace("{seconds}", String.valueOf(remainingSec));
                MessageUtil.sendMessage(player, msg);
                return true;
            }
        }

        sellAllCooldowns.put(uuid, System.currentTimeMillis());

        Runnable onConfirm = () -> {
            PriceManager priceManager = plugin.getPriceManager();
            ConfigManager configManager = plugin.getConfigManager();
            boolean shulkerEnabled = configManager.isShulkerSupportEnabled();

            double totalMoney = 0.0;
            int totalItems = 0;
            String primaryMaterial = null;
            double highestValue = 0.0;

            ItemStack[] contents = player.getInventory().getContents();
            for (int i = 0; i < contents.length; i++) {
                ItemStack item = contents[i];
                if (item == null || item.getType() == Material.AIR) continue;

                // Skip armor slots (36-39) and offhand (40)
                if (i >= 36) continue;

                // Skip custom items
                if (hasCustomData(item)) continue;

                // Containers (Shulker/Bundle)
                if (shulkerEnabled && ContainerCalculator.isShulkerBox(item.getType())) {
                    ContainerCalculator.ContainerResult result = ContainerCalculator.processShulkerBox(item, priceManager);
                    totalMoney += result.money;
                    totalItems += result.itemsSold;
                    
                    if (result.money > highestValue) {
                        highestValue = result.money;
                        primaryMaterial = result.primaryMaterial != null ? result.primaryMaterial : item.getType().name();
                    }

                    player.getInventory().setItem(i, null);

                    for (ItemStack returned : result.toReturn) {
                        giveOrDrop(player, returned);
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

                    player.getInventory().setItem(i, null);

                    for (ItemStack returned : result.toReturn) {
                        giveOrDrop(player, returned);
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
                    
                    player.getInventory().setItem(i, null);
                }
            }

            if (totalItems > 0) {
                finalizeSale(player, totalMoney, totalItems, primaryMaterial);
            } else {
                MessageUtil.sendMessage(player, plugin.getMessagesManager().getMessage("no-items"));
            }
        };

        plugin.getConfirmGui().openConfirmGui(player, plugin.getGuiConfig().getConfirmTitleAll(), onConfirm);
        return true;
    }

    // ━━━━━━━━━━━━━━━ /sell reload ━━━━━━━━━━━━━━━

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("havensell.admin")) {
            MessageUtil.sendMessage(sender, plugin.getMessagesManager().getMessage("no-permission"));
            return true;
        }
        try {
            plugin.reloadAllConfigs();
            MessageUtil.sendMessage(sender, plugin.getMessagesManager().getMessage("reload-success"));
        } catch (Exception e) {
            MessageUtil.sendMessage(sender, plugin.getMessagesManager().getPrefix()
                    + "&cReload failed! Check console for details.");
            plugin.getLogger().severe("Failed to reload configurations: " + e.getMessage());
        }
        return true;
    }

    // ━━━━━━━━━━━━━━━ /sell history [player] ━━━━━━━━━━━━━━━

    private boolean handleHistory(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, plugin.getMessagesManager().getMessage("players-only"));
            return true;
        }

        // /sell history → view own
        if (args.length == 1) {
            if (!player.hasPermission("havensell.use")) {
                MessageUtil.sendMessage(player, plugin.getMessagesManager().getMessage("no-permission"));
                return true;
            }
            plugin.getHistoryGui().openOwnHistory(player);
            return true;
        }

        // /sell history <player> → admin view
        if (!player.hasPermission("havensell.admin")) {
            MessageUtil.sendMessage(player, plugin.getMessagesManager().getMessage("no-permission"));
            return true;
        }

        String targetName = args[1];

        // Run async to avoid blocking main thread for offline player lookups
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // Try online first
            Player online = Bukkit.getPlayerExact(targetName);
            if (online != null) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        plugin.getHistoryGui().openOtherHistory(player, online.getName(), online.getUniqueId()));
                return;
            }

            // Try data manager (covers YAML files and MySQL cache)
            PlayerData data = plugin.getDataManager().loadPlayerDataByName(targetName);
            if (data != null) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        plugin.getHistoryGui().openOtherHistory(player, data.getPlayerName(), data.getUuid()));
                return;
            }

            // Try local usercache only — never triggers a Mojang HTTP lookup on cracked/offline servers
            OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(targetName);
            if (offline != null && offline.hasPlayedBefore()) {
                UUID offlineUuid = offline.getUniqueId();
                Bukkit.getScheduler().runTask(plugin, () ->
                        plugin.getHistoryGui().openOtherHistory(player, targetName, offlineUuid));
                return;
            }

            // Not found
            Bukkit.getScheduler().runTask(plugin, () ->
                    MessageUtil.sendMessage(player, plugin.getMessagesManager().getMessage("player-not-found")
                            .replace("{player}", targetName)));
        });

        return true;
    }

    // ━━━━━━━━━━━━━━━ Shared Helpers ━━━━━━━━━━━━━━━

    private void finalizeSale(Player player, double totalMoney, int totalItems, String primaryMaterial) {
        ConfigManager configManager = plugin.getConfigManager();

        boolean depositSuccess = plugin.getEconomyManager().deposit(player, totalMoney);
        if (depositSuccess) {
            plugin.getDataManager().addSellRecord(player.getUniqueId(), player.getName(), totalItems, totalMoney, primaryMaterial);
            String formattedMoney = configManager.formatMoney(totalMoney);
            MessageUtil.sendSellFeedback(plugin, player, formattedMoney, totalItems);
            playSound(player, configManager.getSound("sell-success"));
        } else {
            plugin.getLogger().warning("Economy deposit failed for " + player.getName() + " — Amount: " + totalMoney);
            MessageUtil.sendMessage(player, plugin.getMessagesManager().getMessage("economy-error"));
        }
    }

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
        if (!meta.getEnchants().isEmpty()) return true;
        if (meta.hasLore()) return true;
        return false;
    }

    private void giveOrDrop(Player player, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;
        var overflow = player.getInventory().addItem(item.clone());
        for (ItemStack dropped : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), dropped);
        }
    }

    private void playSound(Player player, String soundName) {
        if (soundName == null || soundName.isEmpty()) return;
        try {
            org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException ignored) {}
    }

    // ━━━━━━━━━━━━━━━ /sell giveaxe <player> [uses/duration] [duration] ━━━━━━━━━━━━━━━

    private boolean handleGiveAxe(CommandSender sender, String[] args) {
        if (!sender.hasPermission("havensell.admin")) {
            MessageUtil.sendMessage(sender, plugin.getMessagesManager().getMessage("no-permission"));
            return true;
        }

        if (args.length < 2) {
            MessageUtil.sendMessage(sender, plugin.getMessagesManager().getPrefix() 
                    + "&cUsage: /sell giveaxe <player> [uses/duration] [duration]");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            MessageUtil.sendMessage(sender, plugin.getMessagesManager().getMessage("player-not-found")
                    .replace("{player}", args[1]));
            return true;
        }

        int uses = -1; // -1 represents unlimited
        long expiry = -1L; // -1 represents never expires

        // Parse arguments based on length
        if (args.length == 3) {
            String arg2 = args[2].toLowerCase();
            long duration = parseDuration(arg2);
            if (duration > 0) {
                // E.g. /sell giveaxe player 1d -> unlimited uses, expires in 1 day
                uses = -1;
                expiry = System.currentTimeMillis() + duration;
            } else if (arg2.equals("unlimited")) {
                // E.g. /sell giveaxe player unlimited -> unlimited uses, no expiration
                uses = -1;
                expiry = -1L;
            } else {
                // E.g. /sell giveaxe player 50 -> 50 uses, no expiration
                try {
                    uses = Integer.parseInt(arg2);
                    if (uses < -1) {
                        MessageUtil.sendMessage(sender, plugin.getMessagesManager().getPrefix() + "&cUses cannot be negative!");
                        return true;
                    }
                } catch (NumberFormatException e) {
                    MessageUtil.sendMessage(sender, plugin.getMessagesManager().getPrefix() 
                            + "&cInvalid uses or duration: &f" + arg2);
                    return true;
                }
            }
        } else if (args.length >= 4) {
            String arg2 = args[2].toLowerCase();
            String arg3 = args[3].toLowerCase();

            // Parse uses
            if (arg2.equals("unlimited")) {
                uses = -1;
            } else {
                try {
                    uses = Integer.parseInt(arg2);
                    if (uses < -1) {
                        MessageUtil.sendMessage(sender, plugin.getMessagesManager().getPrefix() + "&cUses cannot be negative!");
                        return true;
                    }
                } catch (NumberFormatException e) {
                    MessageUtil.sendMessage(sender, plugin.getMessagesManager().getPrefix() 
                            + "&cInvalid uses count: &f" + arg2);
                    return true;
                }
            }

            // Parse duration
            long duration = parseDuration(arg3);
            if (duration <= 0) {
                MessageUtil.sendMessage(sender, plugin.getMessagesManager().getPrefix() 
                        + "&cInvalid duration: &f" + arg3);
                return true;
            }
            expiry = System.currentTimeMillis() + duration;
        }

        // Create the Sell Axe and give it to the player
        ItemStack sellAxe = SellAxeUtil.createSellAxe(plugin, uses, expiry);
        var overflow = target.getInventory().addItem(sellAxe);
        for (ItemStack extra : overflow.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), extra);
        }

        // Format parameters for feedback messages
        ConfigManager config = plugin.getConfigManager();
        String usesStr = uses == -1 ? config.getSellAxeUnlimitedUsesText() : String.valueOf(uses);
        String expiryStr = config.getSellAxeNeverExpireText();
        if (expiry > 0) {
            try {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(config.getSellAxeDateFormat());
                expiryStr = sdf.format(new Date(expiry));
            } catch (Exception ignored) {}
        }

        // Send messages
        String adminMsg = plugin.getMessagesManager().getMessage("sell-axe-given")
                .replace("{player}", target.getName())
                .replace("{uses}", usesStr)
                .replace("{expiry}", expiryStr);
        MessageUtil.sendMessage(sender, adminMsg);

        String targetMsg = plugin.getMessagesManager().getMessage("sell-axe-received")
                .replace("{uses}", usesStr)
                .replace("{expiry}", expiryStr);
        MessageUtil.sendMessage(target, targetMsg);

        // Play standard receiving levelup sound
        playSound(target, config.getSellAxeSound());

        return true;
    }

    /**
     * Parses duration strings (e.g. 1d, 12h, 30m, 60s) into milliseconds.
     */
    private long parseDuration(String input) {
        if (input == null || input.isEmpty()) return -1L;
        input = input.toLowerCase();
        try {
            if (input.endsWith("d")) {
                long days = Long.parseLong(input.replace("d", ""));
                return days * 24 * 60 * 60 * 1000L;
            } else if (input.endsWith("h")) {
                long hours = Long.parseLong(input.replace("h", ""));
                return hours * 60 * 60 * 1000L;
            } else if (input.endsWith("m")) {
                long minutes = Long.parseLong(input.replace("m", ""));
                return minutes * 60 * 1000L;
            } else if (input.endsWith("s")) {
                long seconds = Long.parseLong(input.replace("s", ""));
                return seconds * 1000L;
            }
        } catch (NumberFormatException ignored) {}
        return -1L;
    }

    // ━━━━━━━━━━━━━━━ Tab Complete ━━━━━━━━━━━━━━━

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            if (sender.hasPermission("havensell.use")) {
                completions.add("hand");
                completions.add("all");
                completions.add("price");
                completions.add("history");
            }
            if (sender.hasPermission("havensell.admin")) {
                completions.add("reload");
                completions.add("giveaxe");
            }

            String input = args[0].toLowerCase();
            completions.removeIf(s -> !s.startsWith(input));
            return completions;
        }

        // Suggest player names
        if (args.length == 2 && sender.hasPermission("havensell.admin")) {
            if (args[0].equalsIgnoreCase("history") || args[0].equalsIgnoreCase("giveaxe") || args[0].equalsIgnoreCase("givewand")) {
                List<String> names = new ArrayList<>();
                String input = args[1].toLowerCase();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(input)) names.add(p.getName());
                }
                return names;
            }
        }

        // Giveaxe arguments autocomplete
        if (args.length == 3 && (args[0].equalsIgnoreCase("giveaxe") || args[0].equalsIgnoreCase("givewand")) && sender.hasPermission("havensell.admin")) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("unlimited");
            suggestions.add("50");
            suggestions.add("100");
            suggestions.add("1d");
            suggestions.add("1h");
            suggestions.add("30m");

            String input = args[2].toLowerCase();
            suggestions.removeIf(s -> !s.startsWith(input));
            return suggestions;
        }

        if (args.length == 4 && (args[0].equalsIgnoreCase("giveaxe") || args[0].equalsIgnoreCase("givewand")) && sender.hasPermission("havensell.admin")) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("1d");
            suggestions.add("12h");
            suggestions.add("1h");
            suggestions.add("30m");
            suggestions.add("1m");

            String input = args[3].toLowerCase();
            suggestions.removeIf(s -> !s.startsWith(input));
            return suggestions;
        }

        return Collections.emptyList();
    }
}
