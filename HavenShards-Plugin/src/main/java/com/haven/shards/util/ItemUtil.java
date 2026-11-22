package com.haven.shards.util;

import com.haven.shards.HavenShards;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ItemUtil {

    private static final DecimalFormatSymbols DFS = DecimalFormatSymbols.getInstance(Locale.US);
    private static final String[] COMPACT_SUFFIXES = {"", "k", "M", "B", "T", "Q"};

    private static final ThreadLocal<DecimalFormat> formatterLocal = ThreadLocal.withInitial(
            () -> new DecimalFormat("#,##0.##", DFS)
    );
    private static final ThreadLocal<DecimalFormat> compactFmt1Local = ThreadLocal.withInitial(
            () -> new DecimalFormat("#,##0.#", DFS)
    );
    private static final ThreadLocal<DecimalFormat> compactFmt2Local = ThreadLocal.withInitial(
            () -> new DecimalFormat("#,##0.##", DFS)
    );
    private static volatile String cachedPattern = "#,##0.##";
    private static final ThreadLocal<String> threadAppliedPattern = ThreadLocal.withInitial(() -> "#,##0.##");
    private static volatile NamespacedKey shardValueKey;
    private static volatile NamespacedKey shardItemIdKey;

    public static NamespacedKey getShardValueKey(HavenShards plugin) {
        NamespacedKey key = shardValueKey;
        if (key == null) {
            synchronized (ItemUtil.class) {
                key = shardValueKey;
                if (key == null) {
                    shardValueKey = key = new NamespacedKey(plugin != null ? plugin : HavenShards.getInstance(), "shard_value");
                }
            }
        }
        return key;
    }

    public static NamespacedKey getShardItemIdKey(HavenShards plugin) {
        NamespacedKey key = shardItemIdKey;
        if (key == null) {
            synchronized (ItemUtil.class) {
                key = shardItemIdKey;
                if (key == null) {
                    shardItemIdKey = key = new NamespacedKey(plugin != null ? plugin : HavenShards.getInstance(), "shard_item_id");
                }
            }
        }
        return key;
    }

    public static ItemStack createShardItem(HavenShards plugin, double amount) {
        var config = plugin.getConfig();
        String matStr = config.getString("physical-item.material", "AMETHYST_SHARD");
        org.bukkit.Material material;
        try {
            material = org.bukkit.Material.valueOf(matStr.toUpperCase());
        } catch (Exception e) {
            material = org.bukkit.Material.AMETHYST_SHARD;
        }

        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String rawName = config.getString("physical-item.name", "&#00E5FF&l✨ %amount% Shards");
            meta.setDisplayName(ColorUtil.colorize(rawName.replace("%amount%", formatMoney(amount))));

            List<String> rawLore = config.getStringList("physical-item.lore");
            List<String> lore = new ArrayList<>();
            for (String l : rawLore) {
                lore.add(ColorUtil.colorize(l.replace("%amount%", formatMoney(amount))));
            }
            meta.setLore(lore);

            if (config.getBoolean("physical-item.glow", true)) {
                org.bukkit.enchantments.Enchantment unbreaking = org.bukkit.Registry.ENCHANTMENT.get(org.bukkit.NamespacedKey.minecraft("unbreaking"));
                if (unbreaking == null) {
                    unbreaking = org.bukkit.enchantments.Enchantment.getByKey(org.bukkit.NamespacedKey.minecraft("unbreaking"));
                }
                if (unbreaking != null) {
                    meta.addEnchant(unbreaking, 1, true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                }
            }

            int customModelData = config.getInt("physical-item.custom-model-data", 0);
            if (customModelData > 0) {
                meta.setCustomModelData(customModelData);
            }
            // Secure Anti-Dupe PDC Tagging
            NamespacedKey key = getShardValueKey(plugin);
            meta.getPersistentDataContainer().set(key, PersistentDataType.DOUBLE, amount);

            NamespacedKey idKey = getShardItemIdKey(plugin);
            meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, java.util.UUID.randomUUID().toString());

            item.setItemMeta(meta);
        }
        return item;
    }

    public static double getShardItemValue(HavenShards plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0.0;
        ItemMeta meta = item.getItemMeta();
        NamespacedKey key = getShardValueKey(plugin);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(key, PersistentDataType.DOUBLE)) {
            Double val = pdc.get(key, PersistentDataType.DOUBLE);
            return val != null ? val : 0.0;
        }
        return 0.0;
    }

    public static String getShardItemId(HavenShards plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        NamespacedKey idKey = getShardItemIdKey(plugin);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(idKey, PersistentDataType.STRING)) {
            return pdc.get(idKey, PersistentDataType.STRING);
        }
        return null;
    }

    public static void regenerateShardItemId(HavenShards plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            NamespacedKey idKey = getShardItemIdKey(plugin);
            meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, java.util.UUID.randomUUID().toString());
            item.setItemMeta(meta);
        }
    }

    public static boolean isInventoryFull(org.bukkit.entity.Player player, ItemStack itemToAdd) {
        if (player == null || player.getInventory() == null) return false;
        if (player.getInventory().firstEmpty() != -1) {
            return false;
        }
        if (itemToAdd != null) {
            int amountToAdd = itemToAdd.getAmount();
            for (ItemStack item : player.getInventory().getStorageContents()) {
                if (item != null && item.isSimilar(itemToAdd)) {
                    int space = item.getMaxStackSize() - item.getAmount();
                    if (space > 0) {
                        amountToAdd -= space;
                        if (amountToAdd <= 0) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    public static void giveOrDropItem(org.bukkit.entity.Player player, ItemStack item) {
        if (player == null || item == null || item.getAmount() <= 0) return;
        java.util.Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }
    }

    public static String serializeItemStack(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        return java.util.Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    public static ItemStack deserializeItemStack(String base64) {
        if (base64 == null || base64.isEmpty()) return null;
        try {
            byte[] bytes = java.util.Base64.getDecoder().decode(base64);
            return ItemStack.deserializeBytes(bytes);
        } catch (Exception e) {
            return null;
        }
    }

    public static void reloadPattern(HavenShards plugin) {
        if (plugin != null) {
            cachedPattern = plugin.getConfig().getString("currency.format", "#,##0.##");
        }
    }

    public static String formatMoney(double amount) {
        String pattern = cachedPattern;
        DecimalFormat fmt = formatterLocal.get();
        if (!pattern.equals(threadAppliedPattern.get())) {
            try {
                fmt.applyPattern(pattern);
                threadAppliedPattern.set(pattern);
            } catch (Exception e) {
                fmt.applyPattern("#,##0.##");
                threadAppliedPattern.set("#,##0.##");
            }
        }
        return fmt.format(amount);
    }

    public static String formatCompact(double amount) {
        if (Double.isNaN(amount) || Double.isInfinite(amount)) return "0";
        if (amount < 0) return "-" + formatCompact(-amount);
        if (amount < 999.95) {
            return formatMoney(amount);
        }

        int unitIndex = 0;
        double val = amount;
        while (val >= 999.95 && unitIndex < COMPACT_SUFFIXES.length - 1) {
            val /= 1000.0;
            unitIndex++;
        }

        DecimalFormat fmt = (val < 10.0) ? compactFmt2Local.get() : compactFmt1Local.get();
        return fmt.format(val) + COMPACT_SUFFIXES[unitIndex];
    }
}

