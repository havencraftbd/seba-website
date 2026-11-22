package com.haven.shards.util;

import com.haven.shards.HavenShards;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;

public class NotificationUtil {

    public static void sendNotification(HavenShards plugin, Player player, String message, String displayType) {
        sendNotification(plugin, player, message, displayType, "CHAT");
    }

    public static void sendNotification(HavenShards plugin, Player player, String message, String displayType, String defaultDisplayType) {
        if (player == null || !player.isOnline() || message == null || message.isEmpty()) {
            return;
        }

        String type = (displayType != null && !displayType.trim().isEmpty()) ? displayType : defaultDisplayType;

        if ("ACTION_BAR".equalsIgnoreCase(type) || "ACTIONBAR".equalsIgnoreCase(type)) {
            try {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
                return;
            } catch (Throwable ignored) {
                // Fallback to chat if action bar is unsupported
            }
        }

        player.sendMessage(plugin.getPrefix() + message);
    }
}
