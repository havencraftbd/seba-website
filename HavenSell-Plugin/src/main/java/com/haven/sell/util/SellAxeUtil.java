package com.haven.sell.util;

import com.haven.sell.HavenSell;
import com.haven.sell.config.ConfigManager;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Handles creation, identification, metadata parsing, and modification of Sell Axes.
 */
public final class SellAxeUtil {

    private static NamespacedKey idKey;
    private static NamespacedKey usesKey;
    private static NamespacedKey expiryKey;

    public static void init(HavenSell plugin) {
        idKey = new NamespacedKey(plugin, "sellaxe_id");
        usesKey = new NamespacedKey(plugin, "sellaxe_uses");
        expiryKey = new NamespacedKey(plugin, "sellaxe_expiry");
    }

    /**
     * Builds a custom Sell Axe item stack with specified uses and expiry.
     *
     * @param plugin     The plugin instance
     * @param uses       Number of uses (-1 for unlimited)
     * @param expiryMs   Expiry timestamp in milliseconds (-1 for never)
     * @return The constructed ItemStack
     */
    public static ItemStack createSellAxe(HavenSell plugin, int uses, long expiryMs) {
        ConfigManager config = plugin.getConfigManager();
        String matName = config.getSellAxeMaterial();
        Material material = Material.matchMaterial(matName);
        if (material == null) {
            material = Material.DIAMOND_AXE;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(idKey, PersistentDataType.STRING, "sell_axe");
        pdc.set(usesKey, PersistentDataType.INTEGER, uses);
        pdc.set(expiryKey, PersistentDataType.LONG, expiryMs);

        // Apply display name and lore with placeholders
        updateItemDisplay(plugin, meta, uses, expiryMs);

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Checks if the given ItemStack is a valid Sell Axe.
     */
    public static boolean isSellAxe(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(idKey, PersistentDataType.STRING) && 
               "sell_axe".equals(pdc.get(idKey, PersistentDataType.STRING));
    }

    /**
     * Retrieves the remaining uses on the Sell Axe.
     */
    public static int getRemainingUses(ItemStack item) {
        if (!isSellAxe(item)) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Integer uses = pdc.get(usesKey, PersistentDataType.INTEGER);
        return uses != null ? uses : 0;
    }

    /**
     * Retrieves the expiry timestamp on the Sell Axe.
     */
    public static long getExpiryTime(ItemStack item) {
        if (!isSellAxe(item)) return -1L;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return -1L;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Long expiry = pdc.get(expiryKey, PersistentDataType.LONG);
        return expiry != null ? expiry : -1L;
    }

    /**
     * Checks if the Sell Axe is expired.
     */
    public static boolean isExpired(ItemStack item) {
        long expiry = getExpiryTime(item);
        if (expiry <= 0) return false; // Never expires
        return System.currentTimeMillis() > expiry;
    }

    /**
     * Decrements the remaining uses of the Sell Axe in a player's hand by 1.
     * If uses drop to 0, it removes/breaks the axe and sends a notification.
     *
     * @param player The player holding the axe
     * @param item   The Sell Axe item stack
     * @param plugin The plugin instance
     */
    public static void decrementUsesAndCheckBreak(Player player, ItemStack item, HavenSell plugin) {
        if (!isSellAxe(item)) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Integer uses = pdc.get(usesKey, PersistentDataType.INTEGER);
        if (uses == null || uses == -1) return; // Unlimited uses, do nothing

        int newUses = uses - 1;
        if (newUses <= 0) {
            // Axe breaks
            player.getInventory().setItemInMainHand(null);
            player.sendMessage(plugin.getMessagesManager().getMessage("sell-axe-broken"));
            // Play item break sound
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
        } else {
            // Update uses and redisplay
            pdc.set(usesKey, PersistentDataType.INTEGER, newUses);
            long expiry = pdc.get(expiryKey, PersistentDataType.LONG) != null ? pdc.get(expiryKey, PersistentDataType.LONG) : -1L;
            updateItemDisplay(plugin, meta, newUses, expiry);
            item.setItemMeta(meta);
        }
    }

    /**
     * Helper to update the custom name and lore of the Sell Axe based on remaining uses and expiry.
     */
    private static void updateItemDisplay(HavenSell plugin, ItemMeta meta, int uses, long expiryMs) {
        ConfigManager config = plugin.getConfigManager();

        // 1. Resolve placeholders
        String usesStr = uses == -1 ? config.getSellAxeUnlimitedUsesText() : String.valueOf(uses);
        String expiryStr = config.getSellAxeNeverExpireText();
        if (expiryMs > 0) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(config.getSellAxeDateFormat());
                expiryStr = sdf.format(new Date(expiryMs));
            } catch (Exception e) {
                expiryStr = "Invalid Date";
            }
        }

        // 2. Set custom name
        String nameFormat = config.getSellAxeName();
        String finalName = nameFormat.replace("{uses}", usesStr).replace("{expiry}", expiryStr);
        meta.setDisplayName(ConfigManager.colorize(finalName));

        // 3. Set custom lore
        List<String> loreFormat = config.getSellAxeLore();
        List<String> finalLore = new ArrayList<>();
        for (String line : loreFormat) {
            String processed = line.replace("{uses}", usesStr).replace("{expiry}", expiryStr);
            finalLore.add(ConfigManager.colorize(processed));
        }
        meta.setLore(finalLore);
    }
}
