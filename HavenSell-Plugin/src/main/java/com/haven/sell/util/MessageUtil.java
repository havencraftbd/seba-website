package com.haven.sell.util;

import com.haven.sell.HavenSell;

import com.haven.sell.config.ConfigManager;
import com.haven.sell.config.MessagesManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;

/**
 * Utility class for sending formatted messages, titles, and feedback.
 */
public final class MessageUtil {

    private static HavenSell pluginInstance;

    private MessageUtil() {}

    /**
     * Initializes MessageUtil with the plugin instance so console logs can use
     * the configurable prefix from messages.yml.
     */
    public static void init(HavenSell plugin) {
        pluginInstance = plugin;
    }

    /**
     * Returns the configurable prefix for console messages.
     * Falls back to a default if the plugin hasn't been initialized yet.
     */
    private static String getConsolePrefix() {
        if (pluginInstance != null && pluginInstance.getMessagesManager() != null) {
            return pluginInstance.getMessagesManager().getPrefix();
        }
        return MessagesManager.colorize("&#92bed8HavenSell &7• ");
    }

    /**
     * Sends a color-coded message to a CommandSender.
     */
    public static void sendMessage(CommandSender sender, String message) {
        if (sender == null || message == null || message.isEmpty()) return;
        sender.sendMessage(MessagesManager.colorize(message));
    }

    // ━━━━━━━━━━━━━━━ Console Logging ━━━━━━━━━━━━━━━

    public static void logInfo(String message) {
        Bukkit.getConsoleSender().sendMessage(getConsolePrefix() + MessagesManager.colorize("&b" + message));
    }

    public static void logWarning(String message) {
        Bukkit.getConsoleSender().sendMessage(getConsolePrefix() + MessagesManager.colorize("&eWARNING: &7" + message));
    }

    public static void logError(String message) {
        Bukkit.getConsoleSender().sendMessage(getConsolePrefix() + MessagesManager.colorize("&cERROR: &7" + message));
    }

    public static void logSevere(String message, Throwable t) {
        Bukkit.getConsoleSender().sendMessage(getConsolePrefix() + MessagesManager.colorize("&4SEVERE: &c" + message));
        if (t != null) {
            t.printStackTrace();
        }
    }

    /**
     * Sends the full sell feedback to a player (title + chat message).
     *
     * @param plugin         The plugin instance
     * @param player         The player to send feedback to
     * @param formattedMoney The already-formatted money string (e.g. "10K" or "1500.00")
     * @param itemCount      The number of items sold
     */
    public static void sendSellFeedback(HavenSell plugin, Player player, String formattedMoney, int itemCount) {
        ConfigManager config = plugin.getConfigManager();
        MessagesManager msg = plugin.getMessagesManager();
        String symbol = config.getCurrencySymbol();

        // Title message
        if (config.isTitleEnabled()) {
            String title = msg.getRawMessage("sell-success-title")
                    .replace("{money}", formattedMoney)
                    .replace("{symbol}", symbol);
            String subtitle = msg.getRawMessage("sell-success-subtitle")
                    .replace("{amount}", String.valueOf(itemCount));

            player.sendTitle(title, subtitle,
                    config.getTitleFadeIn(), config.getTitleStay(), config.getTitleFadeOut());
        }

        // Chat message
        String chatMsg = msg.getMessage("sell-success")
                .replace("{money}", formattedMoney)
                .replace("{symbol}", symbol)
                .replace("{amount}", String.valueOf(itemCount));
        player.sendMessage(chatMsg);
    }
}
