package com.haven.shards.util;

import net.md_5.bungee.api.ChatColor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static boolean miniMessageSupported;

    static {
        try {
            Class.forName("net.kyori.adventure.text.minimessage.MiniMessage");
            Class.forName("net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer");
            miniMessageSupported = true;
        } catch (ClassNotFoundException e) {
            miniMessageSupported = false;
        }
    }

    public static String colorize(String message) {
        if (message == null || message.isEmpty()) return "";

        // 1. MiniMessage / Adventure API Support (<gradient:...>, <rainbow>, <red>, etc.)
        if (miniMessageSupported && message.contains("<") && message.contains(">")) {
            try {
                // Convert legacy &#HEX to <#HEX> for MiniMessage compatibility
                Matcher matcher = HEX_PATTERN.matcher(message);
                StringBuilder sb = new StringBuilder();
                while (matcher.find()) {
                    matcher.appendReplacement(sb, "<#" + matcher.group(1) + ">");
                }
                matcher.appendTail(sb);
                String processed = sb.toString();

                // Convert legacy & color codes to MiniMessage tags if present (avoid § section symbols)
                if (processed.contains("&")) {
                    processed = convertLegacyToMiniMessage(processed);
                }

                net.kyori.adventure.text.Component component = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(processed);
                return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(component);
            } catch (Exception ignored) {
                // Fallback to legacy parsing if MiniMessage deserialize fails
            }
        }

        // 2. Standard Legacy & HEX Color Code Parsing Fallback (&#00E5FF, &a, &l, etc.)
        return parseLegacyColors(message);
    }

    private static final Pattern LEGACY_CODE_PATTERN = Pattern.compile("&([0-9a-fA-Fk-oK-OrR])");

    private static String convertLegacyToMiniMessage(String input) {
        Matcher matcher = LEGACY_CODE_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            char code = Character.toLowerCase(matcher.group(1).charAt(0));
            String tag = switch (code) {
                case '0' -> "<black>";
                case '1' -> "<dark_blue>";
                case '2' -> "<dark_green>";
                case '3' -> "<dark_aqua>";
                case '4' -> "<dark_red>";
                case '5' -> "<dark_purple>";
                case '6' -> "<gold>";
                case '7' -> "<gray>";
                case '8' -> "<dark_gray>";
                case '9' -> "<blue>";
                case 'a' -> "<green>";
                case 'b' -> "<aqua>";
                case 'c' -> "<red>";
                case 'd' -> "<light_purple>";
                case 'e' -> "<yellow>";
                case 'f' -> "<white>";
                case 'k' -> "<obfuscated>";
                case 'l' -> "<bold>";
                case 'm' -> "<strikethrough>";
                case 'n' -> "<underlined>";
                case 'o' -> "<italic>";
                case 'r' -> "<reset>";
                default -> matcher.group(0);
            };
            matcher.appendReplacement(sb, tag);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String parseLegacyColors(String message) {
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuilder builder = new StringBuilder();

        while (matcher.find()) {
            String hexCode = matcher.group(1);
            matcher.appendReplacement(builder, ChatColor.of("#" + hexCode).toString());
        }
        matcher.appendTail(builder);

        return ChatColor.translateAlternateColorCodes('&', builder.toString());
    }
}
