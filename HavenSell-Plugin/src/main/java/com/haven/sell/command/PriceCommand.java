package com.haven.sell.command;

import com.haven.sell.HavenSell;
import com.haven.sell.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Handles /price and /worth commands.
 * Opens the paginated price browse GUI.
 */
public class PriceCommand implements CommandExecutor, TabCompleter {

    private final HavenSell plugin;

    public PriceCommand(HavenSell plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, plugin.getMessagesManager().getMessage("players-only"));
            return true;
        }

        if (!player.hasPermission("havensell.use")) {
            MessageUtil.sendMessage(player, plugin.getMessagesManager().getMessage("no-permission"));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("hand")) {
            handlePriceHand(player);
            return true;
        }

        plugin.getBrowseGui().open(player);
        return true;
    }

    private void handlePriceHand(Player player) {
        org.bukkit.inventory.ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == org.bukkit.Material.AIR) {
            MessageUtil.sendMessage(player, plugin.getMessagesManager().getMessage("price-no-item"));
            return;
        }

        com.haven.sell.config.PriceManager priceManager = plugin.getPriceManager();
        com.haven.sell.config.ConfigManager configManager = plugin.getConfigManager();
        double price = priceManager.getPrice(hand);

        if (price <= 0) {
            MessageUtil.sendMessage(player, plugin.getMessagesManager().getMessage("price-no-value"));
            return;
        }

        String symbol = configManager.getCurrencySymbol();
        String formattedPrice = configManager.formatMoney(price);
        int amount = hand.getAmount();
        double totalValue = price * amount;
        String formattedTotal = configManager.formatMoney(totalValue);

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
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1 && sender.hasPermission("havensell.use")) {
            List<String> completions = new java.util.ArrayList<>();
            completions.add("hand");
            String input = args[0].toLowerCase();
            completions.removeIf(s -> !s.startsWith(input));
            return completions;
        }
        return Collections.emptyList();
    }
}
