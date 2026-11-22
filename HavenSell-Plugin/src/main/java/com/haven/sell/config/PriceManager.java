package com.haven.sell.config;

import com.haven.sell.HavenSell;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import com.haven.sell.util.MessageUtil;
import com.haven.sell.util.ConfigUpdater;

/**
 * Manages item prices loaded from multiple YAML files in the price/ directory.
 * Uses an EnumMap for maximum lookup performance.
 */
public class PriceManager {

    private final HavenSell plugin;
    private final File priceDir;
    private final Map<String, Double> priceMap;

    /** Per-file price index: file name → {Key → price}. Used by browse GUI categories. */
    private final Map<String, Map<String, Double>> filePriceMap;

    // Default price files to save from resources
    private static final String[] DEFAULT_FILES = {
            "blocks.yml", "ores.yml", "crops.yml", "mobs.yml",
            "fish.yml", "natural.yml", "tools.yml", "potions.yml", "books.yml"
    };

    public PriceManager(HavenSell plugin) {
        this.plugin = plugin;
        this.priceDir = new File(plugin.getDataFolder(), "price");
        this.priceMap = new HashMap<>();
        this.filePriceMap = new HashMap<>();
        loadPrices(true);
    }

    /**
     * Loads all price files from the price/ directory.
     */
    private void loadPrices(boolean update) {
        priceMap.clear();
        filePriceMap.clear();

        // Ensure the price directory exists
        if (!priceDir.exists()) {
            priceDir.mkdirs();
        }

        // Save default price files if they don't exist, and update missing entries on startup
        for (String fileName : DEFAULT_FILES) {
            File file = new File(priceDir, fileName);
            if (!file.exists()) {
                saveDefaultPriceFile(fileName);
            } else if (update) {
                try {
                    ConfigUpdater.update(plugin, "price/" + fileName, file);
                } catch (IOException e) {
                    MessageUtil.logWarning("Could not update price file: " + fileName + " - " + e.getMessage());
                }
            }
        }

        // Load all YAML files in the price directory
        File[] files = priceDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            MessageUtil.logWarning("No price files found in " + priceDir.getPath());
            return;
        }

        int totalPrices = 0;
        for (File file : files) {
            int count = loadPriceFile(file);
            totalPrices += count;
        }

        MessageUtil.logInfo("Total prices loaded: &f" + totalPrices + " &bitems from &f" + files.length + " &bfiles.");
    }

    /**
     * Loads prices from a single YAML file.
     * Silently skips entries with ':' notation (sub-type items like potions/enchanted books).
     * Batches unknown material warnings into a single summary line.
     */
    private int loadPriceFile(File file) {
        int count = 0;
        Map<String, Double> fileEntries = new HashMap<>();
        List<String> unknownMaterials = new ArrayList<>();
        
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection pricesSection = yaml.getConfigurationSection("prices");

            if (pricesSection == null) {
                MessageUtil.logWarning("No 'prices' section found in " + file.getName());
                return 0;
            }

            for (String key : pricesSection.getKeys(false)) {
                try {
                    // Handle sub-type entries (e.g. POTION:WATER, ENCHANTED_BOOK:PROTECTION_1)
                    if (key.contains(":")) {
                        double price = pricesSection.getDouble(key);
                        if (price < 0) {
                            MessageUtil.logWarning("Negative price for '" + key + "' in " + file.getName() + ", skipping.");
                            continue;
                        }
                        priceMap.put(key, price);
                        fileEntries.put(key, price);
                        count++;
                        continue;
                    }

                    Material material = Material.matchMaterial(key);
                    if (material == null) {
                        unknownMaterials.add(key);
                        continue;
                    }

                    double price = pricesSection.getDouble(key);
                    if (price < 0) {
                        MessageUtil.logWarning("Negative price for '" + key + "' in " + file.getName() + ", skipping.");
                        continue;
                    }

                    priceMap.put(material.name(), price);
                    fileEntries.put(material.name(), price);
                    count++;
                } catch (Exception e) {
                    MessageUtil.logWarning("Error parsing price entry '" + key + "' in " + file.getName() + ": " + e.getMessage());
                }
            }

            // Log summary with actual item names
            if (!unknownMaterials.isEmpty()) {
                MessageUtil.logWarning("Skipped " + unknownMaterials.size() + " unknown materials in " + file.getName() + " (check for typos or version mismatches): &c" + String.join(", ", unknownMaterials));
            }
        } catch (Exception e) {
            MessageUtil.logSevere("Failed to load price file: " + file.getName(), e);
        }

        if (!fileEntries.isEmpty()) {
            filePriceMap.put(file.getName(), Collections.unmodifiableMap(fileEntries));
        }
        return count;
    }

    /**
     * Saves a default price file from the plugin resources.
     */
    private void saveDefaultPriceFile(String fileName) {
        try (InputStream in = plugin.getResource("price/" + fileName)) {
            if (in == null) {
                MessageUtil.logWarning("Default price file not found in resources: " + fileName);
                return;
            }
            File outFile = new File(priceDir, fileName);
            try (OutputStream out = new FileOutputStream(outFile)) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = in.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                }
            }
        } catch (IOException e) {
            MessageUtil.logWarning("Failed to save default price file: " + fileName + " - " + e.getMessage());
        }
    }

    /**
     * Reloads all price files.
     */
    public void reload() {
        loadPrices(false);
    }

    /**
     * Gets the price for a specific item stack, handling sub-types like potions and enchanted books.
     */
    public double getPrice(org.bukkit.inventory.ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return -1.0;
        Material mat = item.getType();
        
        if (mat == Material.POTION || mat == Material.SPLASH_POTION || mat == Material.LINGERING_POTION) {
            if (item.hasItemMeta() && item.getItemMeta() instanceof org.bukkit.inventory.meta.PotionMeta) {
                org.bukkit.inventory.meta.PotionMeta meta = (org.bukkit.inventory.meta.PotionMeta) item.getItemMeta();
                org.bukkit.potion.PotionType type = meta.getBasePotionType();
                if (type != null) {
                    String key = mat.name() + ":" + type.name();
                    if (priceMap.containsKey(key)) {
                        return priceMap.get(key);
                    }
                }
            }
        } else if (mat == Material.ENCHANTED_BOOK) {
            if (item.hasItemMeta()) {
                org.bukkit.inventory.meta.ItemMeta metaRaw = item.getItemMeta();
                Map<org.bukkit.enchantments.Enchantment, Integer> enchants = new HashMap<>();
                
                if (metaRaw instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta) {
                    org.bukkit.inventory.meta.EnchantmentStorageMeta meta = (org.bukkit.inventory.meta.EnchantmentStorageMeta) metaRaw;
                    if (meta.hasStoredEnchants()) {
                        enchants.putAll(meta.getStoredEnchants());
                    }
                }
                
                if (metaRaw.hasEnchants()) {
                    enchants.putAll(metaRaw.getEnchants());
                }
                
                // Count enchants on book for price evaluation
                
                double highestPrice = -1.0;
                for (Map.Entry<org.bukkit.enchantments.Enchantment, Integer> entry : enchants.entrySet()) {
                    String enchName = entry.getKey().getKey().getKey().toUpperCase();
                    int level = entry.getValue();
                    String key1 = mat.name() + ":" + enchName + "_" + level;
                    String key2 = mat.name() + ":" + enchName;
                    
                    double price = -1.0;
                    if (priceMap.containsKey(key1)) {
                        price = priceMap.get(key1);
                    } else if (priceMap.containsKey(key2)) {
                        price = priceMap.get(key2);
                    }
                    
                    if (price > highestPrice) {
                        highestPrice = price;
                    }
                }
                
                if (highestPrice > 0) {
                    return highestPrice;
                }
            }
        }
        
        return priceMap.getOrDefault(mat.name(), -1.0);
    }

    /**
     * Builds a display item from a string key.
     */
    public org.bukkit.inventory.ItemStack buildDisplayItem(String key) {
        if (!key.contains(":")) {
            Material mat = Material.matchMaterial(key);
            if (mat == null || !mat.isItem()) {
                mat = Material.STONE;
            }
            return new org.bukkit.inventory.ItemStack(mat);
        }
        
        String[] parts = key.split(":");
        Material mat = Material.matchMaterial(parts[0]);
        if (mat == null || !mat.isItem()) {
            mat = Material.STONE;
        }
        org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(mat);
        
        try {
            if (mat == Material.POTION || mat == Material.SPLASH_POTION || mat == Material.LINGERING_POTION) {
                org.bukkit.inventory.meta.PotionMeta meta = (org.bukkit.inventory.meta.PotionMeta) item.getItemMeta();
                org.bukkit.potion.PotionType type = org.bukkit.potion.PotionType.valueOf(parts[1]);
                meta.setBasePotionType(type);
                item.setItemMeta(meta);
            } else if (mat == Material.ENCHANTED_BOOK) {
                org.bukkit.inventory.meta.EnchantmentStorageMeta meta = (org.bukkit.inventory.meta.EnchantmentStorageMeta) item.getItemMeta();
                // parts[1] is like PROTECTION_1
                int lastUnderscore = parts[1].lastIndexOf('_');
                String enchName = parts[1];
                int level = 1;
                if (lastUnderscore > 0) {
                    String suffix = parts[1].substring(lastUnderscore + 1);
                    boolean isNumber = true;
                    for (int i = 0; i < suffix.length(); i++) {
                        if (!Character.isDigit(suffix.charAt(i))) {
                            isNumber = false;
                            break;
                        }
                    }
                    if (isNumber && !suffix.isEmpty()) {
                        level = Integer.parseInt(suffix);
                        enchName = parts[1].substring(0, lastUnderscore);
                    }
                }
                
                org.bukkit.enchantments.Enchantment ench = org.bukkit.enchantments.Enchantment.getByKey(org.bukkit.NamespacedKey.minecraft(enchName.toLowerCase()));
                if (ench != null) {
                    meta.addStoredEnchant(ench, level, true);
                    item.setItemMeta(meta);
                }
            }
        } catch (Exception e) {
            MessageUtil.logWarning("Failed to build display item for key: " + key);
        }
        return item;
    }

    /**
     * Gets a user-friendly name for the item key.
     */
    public String getPrettyName(String key) {
        String[] words = key.toLowerCase().replace(":", " ").replace("_", " ").split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" ");
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }

    /**
     * Gets the price for a material. Returns -1 if the material has no price.
     */
    public double getPrice(Material material) {
        return priceMap.getOrDefault(material.name(), -1.0);
    }

    /**
     * Checks if a material has a registered price.
     */
    public boolean hasPriceFor(Material material) {
        return priceMap.containsKey(material.name());
    }

    /**
     * Gets the total number of priced items.
     */
    public int getPriceCount() {
        return priceMap.size();
    }

    /**
     * Gets an unmodifiable view of all prices.
     */
    public Map<String, Double> getAllPrices() {
        return Map.copyOf(priceMap);
    }

    /**
     * Gets the prices loaded from a specific file.
     * Returns an empty map if the file has no valid prices.
     */
    public Map<String, Double> getPricesForFile(String fileName) {
        return filePriceMap.getOrDefault(fileName, Collections.emptyMap());
    }

    /**
     * Gets the price directory path.
     */
    public File getPriceDir() {
        return priceDir;
    }
}
