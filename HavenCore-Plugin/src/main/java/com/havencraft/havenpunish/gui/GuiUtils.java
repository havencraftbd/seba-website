package com.havencraft.havenpunish.gui;

import com.havencraft.havencore.util.TextUtil;
import org.bukkit.Bukkit;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared utility methods for all HavenPunish GUIs.
 */
public final class GuiUtils {

    private GuiUtils() {}

    /** Creates a named item with lore. */
    public static ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(c(name));
            if (lore != null && !lore.isEmpty()) {
                List<String> colored = new ArrayList<>();
                for (String line : lore) colored.add(c(line));
                meta.setLore(colored);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Creates a named item without lore. */
    public static ItemStack createItem(Material material, String name) {
        return createItem(material, name, null);
    }

    /** Creates a filler item (glass pane). */
    public static ItemStack createFiller(Material material) {
        return createItem(material, " ");
    }

    /** Fills all empty slots with filler. */
    public static void fillEmpty(Inventory inv, Material fillerMaterial) {
        ItemStack filler = createFiller(fillerMaterial);
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, filler);
            }
        }
    }

    /** Creates an inventory with a title and holder. Rows clamped between 1-6. */
    public static Inventory createGui(org.bukkit.inventory.InventoryHolder holder, String title, int rows) {
        rows = Math.max(1, Math.min(6, rows));
        return Bukkit.createInventory(holder, rows * 9, c(title));
    }

    /** Creates an inventory with a title. Rows clamped between 1-6. */
    public static Inventory createGui(String title, int rows) {
        return createGui(null, title, rows);
    }

    /**
     * Safely sets an item in the inventory.
     * Returns true if successful, false if slot is out of bounds.
     */
    public static boolean safeSet(Inventory inv, int slot, ItemStack item) {
        if (slot < 0 || slot >= inv.getSize()) return false;
        inv.setItem(slot, item);
        return true;
    }

    /**
     * Translate color codes including hex colors.
     * Delegates to {@link TextUtil#colorize(String)} for centralized color handling.
     */
    public static String c(String text) {
        return TextUtil.colorize(text);
    }

    /**
     * Strips all color codes (including hex) from a colored string.
     * Useful for console output.
     */
    public static String strip(String text) {
        return ChatColor.stripColor(c(text));
    }

    /** Parse material name safely. */
    public static Material mat(String name) {
        if (name == null) return Material.STONE;
        Material m = Material.matchMaterial(name);
        return m != null ? m : Material.STONE;
    }

    /** Plays a configured GUI sound. Supports both Bukkit Sound enum names and Minecraft keys. */
    public static void playSound(org.bukkit.entity.Player player, org.bukkit.configuration.file.FileConfiguration config, String soundKey) {
        if (config == null || player == null) return;
        String soundName = config.getString("sounds." + soundKey);
        if (soundName != null && !soundName.isEmpty()) {
            try {
                org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundName.toUpperCase());
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            } catch (IllegalArgumentException e) {
                try {
                    player.playSound(player.getLocation(), soundName.toLowerCase(), 1.0f, 1.0f);
                } catch (Exception ignored) {}
            }
        }
    }
}
