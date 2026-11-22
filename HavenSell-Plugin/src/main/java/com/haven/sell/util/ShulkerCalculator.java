package com.haven.sell.util;

import com.haven.sell.config.PriceManager;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Backward-compatible wrapper around ContainerCalculator for Shulker Box operations.
 * All logic now lives in ContainerCalculator.
 */
public final class ShulkerCalculator {

    private ShulkerCalculator() {}

    /** Returns true if the given material is any colour of shulker box. */
    public static boolean isShulkerBox(Material material) {
        return ContainerCalculator.isShulkerBox(material);
    }

    /**
     * Backward-compatible result class. Delegates to ContainerCalculator.ContainerResult.
     */
    public static class ShulkerResult {
        public double money = 0.0;
        public int itemsSold = 0;
        public final java.util.List<ItemStack> toReturn = new java.util.ArrayList<>();
        public String primaryMaterial = null;
    }

    /**
     * Processes a Shulker Box item stack.
     * Delegates to ContainerCalculator.processShulkerBox.
     */
    public static ShulkerResult processShulkerBox(ItemStack item, PriceManager priceManager) {
        ContainerCalculator.ContainerResult cr = ContainerCalculator.processShulkerBox(item, priceManager);
        ShulkerResult result = new ShulkerResult();
        result.money = cr.money;
        result.itemsSold = cr.itemsSold;
        result.toReturn.addAll(cr.toReturn);
        result.primaryMaterial = cr.primaryMaterial;
        return result;
    }
}
