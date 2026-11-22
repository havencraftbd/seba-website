package com.haven.sell.gui;

import com.haven.sell.HavenSell;
import com.haven.sell.config.ConfigManager;
import com.haven.sell.config.PriceManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.haven.sell.util.MessageUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Category-based price browse GUI.
 * <p>
 * Flow: /price or /worth → Category Menu → Item List (paginated, high→low).
 * All data comes from the in-memory PriceManager — no YAML re-reads at runtime.
 */
public class BrowseGui implements Listener {

    private final HavenSell plugin;

    // ── Config ──
    private final File configFile;
    private YamlConfiguration config;

    // ── Session tracking ──
    private final Set<Inventory> activeInventories = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<UUID, PlayerSession> sessions = new ConcurrentHashMap<>();

    // ── Category data (loaded from config) ──
    private final List<CategoryDef> categories = new ArrayList<>();

    // ── Item list constants ──
    private static final int NAV_ROW_SLOTS = 9;
    private static final int SLOT_BACK = 0;   // relative to nav row start
    private static final int SLOT_PREV = 3;
    private static final int SLOT_INFO = 4;
    private static final int SLOT_NEXT = 5;
    private static final int SLOT_CLOSE = 8;

    public BrowseGui(HavenSell plugin) {
        this.plugin = plugin;
        File guiDir = new File(plugin.getDataFolder(), "gui");
        if (!guiDir.exists()) guiDir.mkdirs();
        this.configFile = new File(guiDir, "price-gui.yml");
        loadConfig(true);
    }

    // ━━━━━━━━━━━━━━━ Config Loading ━━━━━━━━━━━━━━━

    private void loadConfig(boolean update) {
        if (!configFile.exists()) {
            saveDefaultConfig();
        } else if (update) {
            // Auto-updater: merge new defaults preserving comments
            try {
                com.haven.sell.util.ConfigUpdater.update(plugin, "gui/price-gui.yml", configFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to update price-gui.yml with new defaults", e);
            }
        }

        config = YamlConfiguration.loadConfiguration(configFile);
        loadCategories();
    }

    private void saveDefaultConfig() {
        try (InputStream in = plugin.getResource("gui/price-gui.yml")) {
            if (in == null) return;
            try (java.io.OutputStream out = new FileOutputStream(configFile)) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = in.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save default price-gui.yml", e);
        }
    }

    /**
     * Parses categories from the config. Validates materials, slots, and file existence.
     */
    private void loadCategories() {
        categories.clear();

        ConfigurationSection catSection = config.getConfigurationSection("categories");
        if (catSection == null) {
            MessageUtil.logWarning("No 'categories' section found in price-gui.yml");
            return;
        }

        File priceDir = plugin.getPriceManager().getPriceDir();
        int guiSize = getCategorySize();

        for (String key : catSection.getKeys(false)) {
            ConfigurationSection cs = catSection.getConfigurationSection(key);
            if (cs == null) continue;

            String fileName = cs.getString("file");
            if (fileName == null || fileName.isEmpty()) {
                MessageUtil.logWarning("Category '" + key + "' has no file defined, skipping.");
                continue;
            }

            // Check if the price file exists on disk
            File priceFile = new File(priceDir, fileName);
            if (!priceFile.exists()) {
                MessageUtil.logWarning("Category '" + key + "' references missing file '" + fileName + "', skipping.");
                continue;
            }

            String matName = cs.getString("material", "STONE");
            Material material = Material.matchMaterial(matName);
            if (material == null) {
                MessageUtil.logWarning("Category '" + key + "' has invalid material '" + matName + "', skipping.");
                continue;
            }

            int slot = cs.getInt("slot", -1);
            if (slot < 0 || slot >= guiSize) {
                MessageUtil.logWarning("Category '" + key + "' has invalid slot " + slot + " (max " + (guiSize - 1) + "), skipping.");
                continue;
            }

            String name = ConfigManager.colorize(cs.getString("name", "&f" + key));
            List<String> lore = cs.getStringList("lore");
            List<String> coloredLore = new ArrayList<>();
            for (String line : lore) {
                coloredLore.add(ConfigManager.colorize(line));
            }

            // Count items from the cached price map for this file
            Map<String, Double> filePrices = plugin.getPriceManager().getPricesForFile(fileName);
            coloredLore.add("");
            coloredLore.add(ConfigManager.colorize("&7Items: &f" + filePrices.size()));
            coloredLore.add(ConfigManager.colorize("&eClick to browse"));

            categories.add(new CategoryDef(key, fileName, material, name, coloredLore, slot));
        }

        MessageUtil.logInfo("Loaded &f" + categories.size() + " &bprice categories.");
    }

    public void reload() {
        loadConfig(false);
    }

    /**
     * Rebuilds category item counts after a price reload.
     */
    public void rebuildSortedPrices() {
        loadCategories();
    }

    // ━━━━━━━━━━━━━━━ Open Category Menu ━━━━━━━━━━━━━━━

    public void open(Player player) {
        openCategoryMenu(player);
    }

    private void openCategoryMenu(Player player) {
        int size = getCategorySize();
        String title = getCategoryTitle();

        Inventory gui = Bukkit.createInventory(null, size, title);

        // Fill with filler
        if (isCategoryFillerEnabled()) {
            ItemStack filler = createItem(getCategoryFillerMaterial(), getCategoryFillerName(), null);
            for (int i = 0; i < size; i++) gui.setItem(i, filler);
        }

        // Place category icons
        for (CategoryDef cat : categories) {
            if (cat.slot >= 0 && cat.slot < size) {
                gui.setItem(cat.slot, createItem(cat.material, cat.name, cat.lore));
            }
        }

        sessions.put(player.getUniqueId(), new PlayerSession(SessionView.CATEGORIES, null, 0));
        activeInventories.add(gui);
        player.openInventory(gui);
    }

    // ━━━━━━━━━━━━━━━ Open Item List ━━━━━━━━━━━━━━━

    private void openItemList(Player player, CategoryDef category, int page) {
        Map<String, Double> filePrices = plugin.getPriceManager().getPricesForFile(category.file);
        if (filePrices.isEmpty()) {
            MessageUtil.sendMessage(player, plugin.getMessagesManager().getPrefix()
                    + "&cNo items found in this category.");
            return;
        }

        // Sort high → low
        List<Map.Entry<String, Double>> sorted = new ArrayList<>(filePrices.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        int itemRows = getItemRows();
        int guiSize = itemRows * 9;
        int itemSlots = (itemRows - 1) * 9; // last row = navigation
        int maxPage = Math.max(0, (sorted.size() - 1) / itemSlots);

        if (page < 0) page = 0;
        if (page > maxPage) page = maxPage;

        // Build title with page info
        String rawTitle = getItemTitle().replace("{category}",
                ConfigManager.colorize(category.name));
        // Strip existing color codes from category name to keep title clean
        String title = rawTitle + " &8(" + (page + 1) + "/" + (maxPage + 1) + ")";
        title = ConfigManager.colorize(title);

        // Truncate title to 32 chars for legacy compatibility
        if (title.length() > 32) {
            title = title.substring(0, 32);
        }

        Inventory gui = Bukkit.createInventory(null, guiSize, title);

        // Fill item slots
        ConfigManager cm = plugin.getConfigManager();
        String symbol = cm.getCurrencySymbol();

        int startIndex = page * itemSlots;
        for (int slot = 0; slot < itemSlots && (startIndex + slot) < sorted.size(); slot++) {
            Map.Entry<String, Double> entry = sorted.get(startIndex + slot);
            String itemKey = entry.getKey();
            double price = entry.getValue();

            ItemStack display = plugin.getPriceManager().buildDisplayItem(itemKey);
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                String prettyName = plugin.getPriceManager().getPrettyName(itemKey);
                meta.setDisplayName(ConfigManager.colorize("&f" + prettyName));

                String formattedPrice = cm.formatMoney(price);
                List<String> lore = new ArrayList<>();
                lore.add(ConfigManager.colorize("&7Sell Price: &a" + symbol + formattedPrice));
                lore.add("");
                lore.add(ConfigManager.colorize("&8Use &f/sell hand &8to sell"));
                meta.setLore(lore);
                display.setItemMeta(meta);
            }
            gui.setItem(slot, display);
        }

        // Navigation row (last row)
        int navRowStart = (itemRows - 1) * 9;
        if (isCategoryFillerEnabled()) {
            ItemStack navFiller = createItem(getCategoryFillerMaterial(), getCategoryFillerName(), null);
            for (int i = navRowStart; i < guiSize; i++) gui.setItem(i, navFiller);
        }

        // Back button
        gui.setItem(navRowStart + SLOT_BACK, createItem(Material.DARK_OAK_DOOR,
                "&e⬅ Back to Categories", Arrays.asList("&7Return to the category menu")));

        // Previous page
        if (page > 0) {
            gui.setItem(navRowStart + SLOT_PREV, createItem(Material.ARROW,
                    "&a◀ Previous Page", Arrays.asList("&7Page " + page)));
        }

        // Info
        gui.setItem(navRowStart + SLOT_INFO, createItem(Material.BOOK,
                "&6&l" + ConfigManager.colorize(category.name),
                Arrays.asList(
                        "&7Total items: &f" + sorted.size(),
                        "&7Page: &f" + (page + 1) + "&7/&f" + (maxPage + 1)
                )));

        // Next page
        if (page < maxPage) {
            gui.setItem(navRowStart + SLOT_NEXT, createItem(Material.ARROW,
                    "&a▶ Next Page", Arrays.asList("&7Page " + (page + 2))));
        }

        // Close
        gui.setItem(navRowStart + SLOT_CLOSE, createItem(Material.BARRIER,
                "&c&lClose", Arrays.asList("&7Click to close")));

        sessions.put(player.getUniqueId(), new PlayerSession(SessionView.ITEMS, category, page));
        activeInventories.add(gui);
        player.openInventory(gui);
    }

    // ━━━━━━━━━━━━━━━ Event Handlers ━━━━━━━━━━━━━━━

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory topInventory = event.getView().getTopInventory();
        if (!activeInventories.contains(topInventory)) return;

        // Block all item movement — read-only GUI
        event.setCancelled(true);

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= topInventory.getSize()) return;

        UUID uuid = player.getUniqueId();
        PlayerSession session = sessions.get(uuid);
        if (session == null) return;

        if (session.view == SessionView.CATEGORIES) {
            handleCategoryClick(player, rawSlot);
        } else if (session.view == SessionView.ITEMS) {
            handleItemListClick(player, rawSlot, session);
        }
    }

    private void handleCategoryClick(Player player, int rawSlot) {
        // Find which category was clicked
        for (CategoryDef cat : categories) {
            if (cat.slot == rawSlot) {
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, () -> openItemList(player, cat, 0), 1L);
                return;
            }
        }
    }

    private void handleItemListClick(Player player, int rawSlot, PlayerSession session) {
        int itemRows = getItemRows();
        int guiSize = itemRows * 9;
        int navRowStart = (itemRows - 1) * 9;

        // Only handle navigation row clicks
        if (rawSlot < navRowStart || rawSlot >= guiSize) return;

        int navSlot = rawSlot - navRowStart;

        if (navSlot == SLOT_BACK) {
            // Back to categories
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> openCategoryMenu(player), 1L);
        } else if (navSlot == SLOT_PREV && session.page > 0) {
            CategoryDef cat = session.category;
            int newPage = session.page - 1;
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> openItemList(player, cat, newPage), 1L);
        } else if (navSlot == SLOT_NEXT) {
            CategoryDef cat = session.category;
            int newPage = session.page + 1;
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> openItemList(player, cat, newPage), 1L);
        } else if (navSlot == SLOT_CLOSE) {
            player.closeInventory();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        Inventory topInventory = event.getView().getTopInventory();
        if (!activeInventories.contains(topInventory)) return;

        activeInventories.remove(topInventory);
        sessions.remove(player.getUniqueId());
    }

    // ━━━━━━━━━━━━━━━ Config Accessors ━━━━━━━━━━━━━━━

    private String getCategoryTitle() {
        return ConfigManager.colorize(config.getString("title", "&8&lPrice Guide"));
    }

    private int getCategoryRows() {
        return Math.max(1, Math.min(6, config.getInt("rows", 4)));
    }

    private int getCategorySize() {
        return getCategoryRows() * 9;
    }

    private boolean isCategoryFillerEnabled() {
        return config.getBoolean("filler.enabled", true);
    }

    private Material getCategoryFillerMaterial() {
        String mat = config.getString("filler.material", "BLACK_STAINED_GLASS_PANE");
        Material m = Material.matchMaterial(mat);
        return m != null ? m : Material.BLACK_STAINED_GLASS_PANE;
    }

    private String getCategoryFillerName() {
        return ConfigManager.colorize(config.getString("filler.name", " "));
    }

    private String getItemTitle() {
        return config.getString("item-title", "&8&l{category}");
    }

    private int getItemRows() {
        return Math.max(2, Math.min(6, config.getInt("item-rows", 6)));
    }

    // ━━━━━━━━━━━━━━━ Helpers ━━━━━━━━━━━━━━━

    public boolean isBrowseGui(Inventory inventory) {
        return activeInventories.contains(inventory);
    }

    public void closeAll() {
        for (UUID uuid : sessions.keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) p.closeInventory();
        }
        activeInventories.clear();
        sessions.clear();
    }

    private String prettifyMaterialName(String name) {
        String[] parts = name.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ConfigManager.colorize(name));
            if (lore != null && !lore.isEmpty()) {
                List<String> coloredLore = new ArrayList<>();
                for (String line : lore) {
                    coloredLore.add(ConfigManager.colorize(line));
                }
                meta.setLore(coloredLore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    // ━━━━━━━━━━━━━━━ Inner Types ━━━━━━━━━━━━━━━

    private enum SessionView {
        CATEGORIES, ITEMS
    }

    private static class PlayerSession {
        final SessionView view;
        final CategoryDef category;
        final int page;

        PlayerSession(SessionView view, CategoryDef category, int page) {
            this.view = view;
            this.category = category;
            this.page = page;
        }
    }

    /**
     * Immutable definition of a category loaded from config.
     */
    private static class CategoryDef {
        final String key;
        final String file;
        final Material material;
        final String name;
        final List<String> lore;
        final int slot;

        CategoryDef(String key, String file, Material material, String name, List<String> lore, int slot) {
            this.key = key;
            this.file = file;
            this.material = material;
            this.name = name;
            this.lore = lore;
            this.slot = slot;
        }
    }
}
