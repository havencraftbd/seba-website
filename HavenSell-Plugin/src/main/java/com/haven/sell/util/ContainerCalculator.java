package com.haven.sell.util;

import com.haven.sell.config.PriceManager;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Unified utility class for calculating the sell value of container items
 * (Shulker Boxes and Bundles).
 *
 * <h3>Shulker Box Behavior:</h3>
 * <ul>
 *   <li><strong>CASE 1 (no price):</strong> Sell all sellable contents, return empty shulker + unsellable items.</li>
 *   <li><strong>CASE 2 (has price):</strong> Sell shulker box + ALL sellable contents inside. Total = box price + content value.</li>
 * </ul>
 *
 * <h3>Bundle Behavior:</h3>
 * <ul>
 *   <li>Sell all sellable contents inside the bundle.</li>
 *   <li>Return unsellable items + the empty bundle to the player.</li>
 * </ul>
 *
 * Safe and dupe-proof: always clones before returning.
 */
public final class ContainerCalculator {

    private ContainerCalculator() {}

    /** All shulker box material names end in _SHULKER_BOX. Build the set once at startup. */
    private static final Set<Material> SHULKER_MATERIALS;

    static {
        SHULKER_MATERIALS = new HashSet<>();
        for (Material mat : Material.values()) {
            if (mat.name().endsWith("_SHULKER_BOX") || mat == Material.SHULKER_BOX) {
                SHULKER_MATERIALS.add(mat);
            }
        }
    }

    /** Returns true if the given material is any colour of shulker box. */
    public static boolean isShulkerBox(Material material) {
        return material != null && SHULKER_MATERIALS.contains(material);
    }

    /** Returns true if the given material is a bundle. */
    public static boolean isBundle(Material material) {
        if (material == null) return false;
        return material == Material.BUNDLE;
    }

    /**
     * Holds the result of processing a container item.
     */
    public static class ContainerResult {
        public double money = 0.0;
        public int itemsSold = 0;
        public final List<ItemStack> toReturn = new ArrayList<>();
        /** Material name of the most valuable item sold (for history display) */
        public String primaryMaterial = null;
    }

    // ━━━━━━━━━━━━━━━ Shulker Box Processing ━━━━━━━━━━━━━━━

    /**
     * Processes a Shulker Box item stack.
     *
     * <p><strong>CASE 2 (FIX):</strong> If the shulker box material has a configured price,
     * the box price is applied AND all sellable contents inside are also sold.
     * Total = (box price × stack amount) + all sellable content value.
     * Nothing is returned to the player (box + contents consumed).</p>
     *
     * <p><strong>CASE 1:</strong> If the shulker box has NO configured price,
     * all sellable items inside are sold. Unsellable items are returned to the player.
     * The empty shulker box itself is also returned to the player.</p>
     *
     * @param item         The shulker box item stack
     * @param priceManager Price lookup table
     * @return ContainerResult containing total money, total items sold, and items to be returned.
     */
    public static ContainerResult processShulkerBox(ItemStack item, PriceManager priceManager) {
        ContainerResult result = new ContainerResult();
        if (item == null || item.getType() == Material.AIR || !isShulkerBox(item.getType())) {
            return result;
        }

        int stackAmount = item.getAmount();
        double boxPrice = priceManager.getPrice(item);
        boolean shulkerHasPrice = boxPrice > 0;

        // ═══════════════════════════════════════════
        // CASE 2: Shulker HAS a price → sell box + ALL sellable contents
        // ═══════════════════════════════════════════
        if (shulkerHasPrice) {
            result.money += boxPrice * stackAmount;
            result.itemsSold += stackAmount;
            result.primaryMaterial = item.getType().name();

            // ── FIX: Also process contents inside the priced shulker ──
            ItemMeta meta = item.getItemMeta();
            if (meta instanceof BlockStateMeta bsm) {
                BlockState blockState = bsm.getBlockState();
                if (blockState instanceof ShulkerBox shulkerBox) {
                    double contentMoney = 0.0;
                    int contentItems = 0;
                    List<ItemStack> unsellableContents = new ArrayList<>();

                    for (ItemStack content : shulkerBox.getInventory().getContents()) {
                        if (content == null || content.getType() == Material.AIR) continue;

                        double price = priceManager.getPrice(content);
                        if (price > 0) {
                            contentMoney += price * content.getAmount();
                            contentItems += content.getAmount();
                        } else {
                            // Unsellable item inside a priced shulker - return to player
                            unsellableContents.add(content.clone());
                        }
                    }

                    // Multiply content value by stack amount
                    result.money += contentMoney * stackAmount;
                    result.itemsSold += contentItems * stackAmount;
                    
                    // Add unsellable items to return list
                    for (int i = 0; i < stackAmount; i++) {
                        for (ItemStack unsellable : unsellableContents) {
                            result.toReturn.add(unsellable.clone());
                        }
                    }
                }
            }

            return result;
        }

        // ═══════════════════════════════════════════
        // CASE 1: Shulker has NO price → sell contents, return empty box
        // (EXISTING WORKING LOGIC — PRESERVED EXACTLY)
        // ═══════════════════════════════════════════

        // Try to read the shulker's inventory
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof BlockStateMeta bsm)) {
            // Can't read contents — just return the box untouched
            result.toReturn.add(item.clone());
            return result;
        }

        BlockState blockState = bsm.getBlockState();
        if (!(blockState instanceof ShulkerBox shulkerBox)) {
            // Not a valid shulker state — return untouched
            result.toReturn.add(item.clone());
            return result;
        }

        // Calculate value of one shulker's contents
        double oneShulkerMoney = 0.0;
        int oneShulkerItemsSold = 0;
        List<ItemStack> oneShulkerUnsellable = new ArrayList<>();
        double highestPrice = 0.0;

        for (ItemStack content : shulkerBox.getInventory().getContents()) {
            if (content == null || content.getType() == Material.AIR) continue;

            double price = priceManager.getPrice(content);
            if (price > 0) {
                oneShulkerMoney += price * content.getAmount();
                oneShulkerItemsSold += content.getAmount();
                double totalItemValue = price * content.getAmount();
                if (totalItemValue > highestPrice) {
                    highestPrice = totalItemValue;
                    result.primaryMaterial = content.getType().name();
                }
            } else {
                // Unsellable item inside the shulker — return it
                oneShulkerUnsellable.add(content.clone());
            }
        }

        // Multiply by stack amount (shulkers normally stack to 1, but handle edge cases)
        result.money += oneShulkerMoney * stackAmount;
        result.itemsSold += oneShulkerItemsSold * stackAmount;

        for (int i = 0; i < stackAmount; i++) {
            // Return unsellable items from inside the shulker
            for (ItemStack unsellable : oneShulkerUnsellable) {
                result.toReturn.add(unsellable.clone());
            }

            // Return the empty shulker box itself
            ItemStack emptyShulker = new ItemStack(item.getType(), 1);
            // Preserve the custom name/color if the shulker had one
            ItemMeta originalMeta = item.getItemMeta();
            if (originalMeta != null && originalMeta.hasDisplayName()) {
                ItemMeta emptyMeta = emptyShulker.getItemMeta();
                if (emptyMeta != null) {
                    emptyMeta.setDisplayName(originalMeta.getDisplayName());
                    emptyShulker.setItemMeta(emptyMeta);
                }
            }
            result.toReturn.add(emptyShulker);
        }

        return result;
    }

    // ━━━━━━━━━━━━━━━ Bundle Processing ━━━━━━━━━━━━━━━

    /**
     * Processes a Bundle item stack.
     *
     * <p>All sellable items inside the bundle are sold.
     * Unsellable items are returned to the player.
     * The empty bundle itself is also returned to the player.</p>
     *
     * @param item         The bundle item stack
     * @param priceManager Price lookup table
     * @return ContainerResult containing total money, total items sold, and items to be returned.
     */
    public static ContainerResult processBundle(ItemStack item, PriceManager priceManager) {
        ContainerResult result = new ContainerResult();
        if (item == null || item.getType() == Material.AIR || !isBundle(item.getType())) {
            return result;
        }

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof BundleMeta bundleMeta)) {
            // Can't read as bundle — return untouched
            result.toReturn.add(item.clone());
            return result;
        }

        List<ItemStack> contents = bundleMeta.getItems();
        double highestPrice = 0.0;

        for (ItemStack content : contents) {
            if (content == null || content.getType() == Material.AIR) continue;

            double price = priceManager.getPrice(content);
            if (price > 0) {
                result.money += price * content.getAmount();
                result.itemsSold += content.getAmount();
                double totalItemValue = price * content.getAmount();
                if (totalItemValue > highestPrice) {
                    highestPrice = totalItemValue;
                    result.primaryMaterial = content.getType().name();
                }
            } else {
                // Unsellable — return to player
                result.toReturn.add(content.clone());
            }
        }

        // Return the empty bundle to the player
        ItemStack emptyBundle = new ItemStack(Material.BUNDLE, 1);
        // Preserve custom name if present
        if (meta.hasDisplayName()) {
            ItemMeta emptyMeta = emptyBundle.getItemMeta();
            if (emptyMeta != null) {
                emptyMeta.setDisplayName(meta.getDisplayName());
                emptyBundle.setItemMeta(emptyMeta);
            }
        }
        result.toReturn.add(emptyBundle);

        return result;
    }
}
