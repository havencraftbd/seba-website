package com.havencraft.havencore.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized text utility for color code translation using Kyori Adventure API.
 * Supports both {@code &c} / {@code §c} styles and {@code &#RRGGBB} hex colors.
 * Highly optimized for zero object allocation on uncolored strings and caching for repeated lookups.
 */
public final class TextUtil {

    private static final Map<String, String> COLOR_CACHE = new ConcurrentHashMap<>(256);
    private static final int MAX_CACHE_SIZE = 1024;

    private static final LegacyComponentSerializer AMPERSAND_SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private static final LegacyComponentSerializer SECTION_SERIALIZER = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private TextUtil() {}

    /**
     * Fast matcher-free conversion of &#RRGGBB into &x&r&r&g&g&b&b.
     */
    private static String convertHexToBungee(String text) {
        int index = text.indexOf("&#");
        if (index == -1) return text;

        int length = text.length();
        StringBuilder sb = new StringBuilder(length + 32);
        int lastIndex = 0;

        while (index != -1 && index + 7 < length) {
            if (isHexColor(text, index + 2)) {
                sb.append(text, lastIndex, index);
                sb.append("&x");
                for (int i = index + 2; i < index + 8; i++) {
                    sb.append('&').append(text.charAt(i));
                }
                lastIndex = index + 8;
                index = text.indexOf("&#", lastIndex);
            } else {
                index = text.indexOf("&#", index + 2);
            }
        }
        sb.append(text, lastIndex, length);
        return sb.toString();
    }

    private static boolean isHexColor(String text, int start) {
        for (int i = start; i < start + 6; i++) {
            char c = text.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Translates legacy color codes (including hex and section signs) into section-prefixed colorized string.
     *
     * @param text the raw text with color codes
     * @return the colorized string, or empty string if null
     */
    public static String colorize(String text) {
        if (text == null || text.isEmpty()) return "";

        // Fast-path: If string contains no color or hex indicators, return original string instance
        if (text.indexOf('&') == -1 && text.indexOf('§') == -1 && text.indexOf('#') == -1) {
            return text;
        }

        // Check cache for repeated queries (e.g. GUI items, prefixes, lore lines)
        String cached = COLOR_CACHE.get(text);
        if (cached != null) {
            return cached;
        }

        String input = text;
        if (input.indexOf('§') != -1) {
            input = input.replace('§', '&');
        }

        String converted = convertHexToBungee(input);
        Component component = AMPERSAND_SERIALIZER.deserialize(converted);
        String result = SECTION_SERIALIZER.serialize(component);

        if (COLOR_CACHE.size() >= MAX_CACHE_SIZE) {
            COLOR_CACHE.clear();
        }
        COLOR_CACHE.put(text, result);

        return result;
    }

    /**
     * Parses legacy text into a Kyori Adventure Component.
     *
     * @param text the raw text
     * @return the Component
     */
    public static Component parseLegacy(String text) {
        if (text == null || text.isEmpty()) return Component.empty();

        // Fast-path for unstyled text
        if (text.indexOf('&') == -1 && text.indexOf('§') == -1 && text.indexOf('#') == -1) {
            return Component.text(text);
        }

        String input = text;
        if (input.indexOf('§') != -1) {
            input = input.replace('§', '&');
        }

        String converted = convertHexToBungee(input);
        return AMPERSAND_SERIALIZER.deserialize(converted);
    }

    /**
     * Sends a colorized legacy message to a CommandSender using Kyori Adventure Component.
     *
     * @param sender  the recipient
     * @param message the raw message
     */
    public static void sendMessage(CommandSender sender, String message) {
        if (sender == null || message == null) return;
        sender.sendMessage(parseLegacy(message));
    }

    /**
     * Colorizes each string in a list.
     *
     * @param lines the raw lines
     * @return a new list with colorized strings
     */
    public static List<String> colorizeList(List<String> lines) {
        if (lines == null || lines.isEmpty()) return new ArrayList<>(0);
        List<String> result = new ArrayList<>(lines.size());
        for (String line : lines) {
            result.add(colorize(line));
        }
        return result;
    }
}
