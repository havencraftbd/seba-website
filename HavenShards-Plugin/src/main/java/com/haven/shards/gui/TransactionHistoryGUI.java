package com.haven.shards.gui;

import com.haven.shards.HavenShards;
import com.haven.shards.model.TransactionEntry;
import com.haven.shards.util.ColorUtil;
import com.haven.shards.util.ItemUtil;
import com.haven.shards.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TransactionHistoryGUI implements InventoryHolder, Listener {

    private static Listener activeListener = null;
    private static final com.google.common.cache.Cache<UUID, Long> clickDebounce = com.google.common.cache.CacheBuilder.newBuilder()
            .expireAfterWrite(1, java.util.concurrent.TimeUnit.SECONDS)
            .maximumSize(50)
            .build();
    private final HavenShards plugin;
    private final Inventory inventory;
    private final OfflinePlayer targetPlayer;
    private final List<TransactionEntry> transactions;
    private int currentPage;
    private final int totalPages;

    public TransactionHistoryGUI(HavenShards plugin) {
        this.plugin = plugin;
        this.inventory = null;
        this.targetPlayer = null;
        this.transactions = new ArrayList<>();
        this.currentPage = 1;
        this.totalPages = 1;
    }

    public static synchronized void registerListener(HavenShards plugin) {
        if (activeListener != null) {
            HandlerList.unregisterAll(activeListener);
        }
        activeListener = new TransactionHistoryGUI(plugin);
        Bukkit.getPluginManager().registerEvents(activeListener, plugin);
    }

    public static synchronized void unregisterListener() {
        if (activeListener != null) {
            HandlerList.unregisterAll(activeListener);
            activeListener = null;
        }
        clickDebounce.invalidateAll();
    }

    private static boolean isDebounced(UUID uuid) {
        long now = System.currentTimeMillis();
        Long last = clickDebounce.getIfPresent(uuid);
        if (last != null && (now - last) < 200L) {
            return true;
        }
        clickDebounce.put(uuid, now);
        return false;
    }

    public TransactionHistoryGUI(HavenShards plugin, OfflinePlayer targetPlayer, List<TransactionEntry> transactions, int page) {
        this.plugin = plugin;
        this.targetPlayer = targetPlayer;
        this.transactions = transactions;
        this.currentPage = Math.max(1, page);

        FileConfiguration histConfig = plugin.getGuiManager().getHistoryConfig();

        int rows = Math.max(1, Math.min(6, histConfig.getInt("rows", 6)));
        int invSize = rows * 9;
        int itemsPerPage = Math.max(1, invSize - 9);
        this.totalPages = Math.max(1, (int) Math.ceil((double) transactions.size() / itemsPerPage));

        String targetName = targetPlayer != null && targetPlayer.getName() != null ? targetPlayer.getName() : "Player";
        String rawTitle = histConfig.getString("title", "&#00E5FF&lHistory &#888888» &f%target% &8(%page%/%total_pages%)");
        String title = ColorUtil.colorize(rawTitle
                .replace("%target%", targetName)
                .replace("%page%", String.valueOf(currentPage))
                .replace("%total_pages%", String.valueOf(totalPages))
        );
        this.inventory = Bukkit.createInventory(this, invSize, title);

        buildGUI();
    }

    private void buildGUI() {
        FileConfiguration histConfig = plugin.getGuiManager().getHistoryConfig();
        String symbol = plugin.getConfig().getString("currency.symbol", "✨");
        String currency = plugin.getConfig().getString("currency.name", "Shards");

        int invSize = inventory.getSize();
        int itemsPerPage = Math.max(1, invSize - 9);
        int startIndex = (currentPage - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, transactions.size());

        int slot = 0;
        for (int i = startIndex; i < endIndex; i++) {
            TransactionEntry entry = transactions.get(i);
            inventory.setItem(slot++, createTransactionItem(entry, symbol, currency, histConfig));
        }

        // Background Glass Pane
        String bgMatStr = histConfig.getString("background.material", "BLACK_STAINED_GLASS_PANE");
        if (bgMatStr != null && !bgMatStr.equalsIgnoreCase("AIR") && !bgMatStr.equalsIgnoreCase("NONE")) {
            Material bgMat;
            try {
                bgMat = Material.valueOf(bgMatStr.toUpperCase());
            } catch (Exception e) {
                bgMat = Material.BLACK_STAINED_GLASS_PANE;
            }

            if (bgMat != Material.AIR) {
                ItemStack emptyPane = new ItemStack(bgMat);
                ItemMeta paneMeta = emptyPane.getItemMeta();
                if (paneMeta != null) {
                    paneMeta.setDisplayName(ColorUtil.colorize(histConfig.getString("background.name", " ")));
                    emptyPane.setItemMeta(paneMeta);
                }
                for (int i = slot; i < invSize; i++) {
                    inventory.setItem(i, emptyPane);
                }
            }
        }

        // Previous Page Button
        int prevSlot = histConfig.getInt("buttons.previous-page.slot", 45);
        if (currentPage > 1 && prevSlot >= 0 && prevSlot < inventory.getSize()) {
            Material pMat;
            try {
                pMat = Material.valueOf(histConfig.getString("buttons.previous-page.material", "ARROW").toUpperCase());
            } catch (Exception e) {
                pMat = Material.ARROW;
            }
            ItemStack prev = new ItemStack(pMat);
            ItemMeta meta = prev.getItemMeta();
            if (meta != null) {
                String nameRaw = histConfig.getString("buttons.previous-page.name", "&e◀ Previous Page (%page%)");
                meta.setDisplayName(ColorUtil.colorize(nameRaw.replace("%page%", String.valueOf(currentPage - 1))));
                int cmd = histConfig.getInt("buttons.previous-page.custom-model-data", 0);
                if (cmd > 0) meta.setCustomModelData(cmd);
                prev.setItemMeta(meta);
            }
            inventory.setItem(prevSlot, prev);
        }

        // Info Button
        int infoSlot = histConfig.getInt("buttons.info.slot", 50);
        if (infoSlot >= 0 && infoSlot < inventory.getSize()) {
            Material iMat;
            try {
                iMat = Material.valueOf(histConfig.getString("buttons.info.material", "BOOK").toUpperCase());
            } catch (Exception e) {
                iMat = Material.BOOK;
            }
            ItemStack info = new ItemStack(iMat);
            ItemMeta infoMeta = info.getItemMeta();
            if (infoMeta != null) {
                String nameRaw = histConfig.getString("buttons.info.name", "&b&lHistory Summary");
                String targetName = targetPlayer != null && targetPlayer.getName() != null ? targetPlayer.getName() : "Unknown";
                infoMeta.setDisplayName(ColorUtil.colorize(nameRaw.replace("%target%", targetName)));

                int cmd = histConfig.getInt("buttons.info.custom-model-data", 0);
                if (cmd > 0) infoMeta.setCustomModelData(cmd);

                List<String> rawLore = histConfig.getStringList("buttons.info.lore");
                List<String> lore = new ArrayList<>();
                for (String l : rawLore) {
                    lore.add(ColorUtil.colorize(l
                            .replace("%target%", targetName)
                            .replace("%page%", String.valueOf(currentPage))
                            .replace("%total_pages%", String.valueOf(totalPages))
                            .replace("%total_records%", String.valueOf(transactions.size()))
                    ));
                }
                infoMeta.setLore(lore);
                info.setItemMeta(infoMeta);
            }
            inventory.setItem(infoSlot, info);
        }

        // Next Page Button
        int nextSlot = histConfig.getInt("buttons.next-page.slot", 53);
        if (currentPage < totalPages && nextSlot >= 0 && nextSlot < inventory.getSize()) {
            Material nMat;
            try {
                nMat = Material.valueOf(histConfig.getString("buttons.next-page.material", "ARROW").toUpperCase());
            } catch (Exception e) {
                nMat = Material.ARROW;
            }
            ItemStack next = new ItemStack(nMat);
            ItemMeta meta = next.getItemMeta();
            if (meta != null) {
                String nameRaw = histConfig.getString("buttons.next-page.name", "&eNext Page (%page%) ▶");
                meta.setDisplayName(ColorUtil.colorize(nameRaw.replace("%page%", String.valueOf(currentPage + 1))));
                int cmd = histConfig.getInt("buttons.next-page.custom-model-data", 0);
                if (cmd > 0) meta.setCustomModelData(cmd);
                next.setItemMeta(meta);
            }
            inventory.setItem(nextSlot, next);
        }

        // Back Button
        int backSlot = histConfig.getInt("buttons.back.slot", 48);
        if (backSlot >= 0 && backSlot < inventory.getSize()) {
            Material bMat;
            try {
                bMat = Material.valueOf(histConfig.getString("buttons.back.material", "RED_STAINED_GLASS_PANE").toUpperCase());
            } catch (Exception e) {
                bMat = Material.RED_STAINED_GLASS_PANE;
            }
            ItemStack back = new ItemStack(bMat);
            ItemMeta backMeta = back.getItemMeta();
            if (backMeta != null) {
                backMeta.setDisplayName(ColorUtil.colorize(histConfig.getString("buttons.back.name", "&c&l« Back to Dashboard")));
                int cmd = histConfig.getInt("buttons.back.custom-model-data", 0);
                if (cmd > 0) backMeta.setCustomModelData(cmd);
                back.setItemMeta(backMeta);
            }
            inventory.setItem(backSlot, back);
        }

        // Close Button
        int closeSlot = histConfig.getInt("buttons.close.slot", 49);
        if (closeSlot >= 0 && closeSlot < inventory.getSize()) {
            Material cMat;
            try {
                cMat = Material.valueOf(histConfig.getString("buttons.close.material", "BARRIER").toUpperCase());
            } catch (Exception e) {
                cMat = Material.BARRIER;
            }
            ItemStack close = new ItemStack(cMat);
            ItemMeta closeMeta = close.getItemMeta();
            if (closeMeta != null) {
                closeMeta.setDisplayName(ColorUtil.colorize(histConfig.getString("buttons.close.name", "&c&lClose")));
                int cmd = histConfig.getInt("buttons.close.custom-model-data", 0);
                if (cmd > 0) closeMeta.setCustomModelData(cmd);
                close.setItemMeta(closeMeta);
            }
            inventory.setItem(closeSlot, close);
        }
    }

    private ItemStack createTransactionItem(TransactionEntry entry, String symbol, String currency, FileConfiguration histConfig) {
        String typeKey = entry.getType().toUpperCase();
        String path = "icons." + typeKey;
        if (!histConfig.contains(path)) {
            path = "icons.DEFAULT";
        }

        String matStr = histConfig.getString(path + ".material", "CLOCK");
        Material mat;
        try {
            mat = Material.valueOf(matStr.toUpperCase());
        } catch (Exception e) {
            mat = Material.CLOCK;
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String rawName = histConfig.getString(path + ".name", "&f&l%type% &8(#%id%)");
            meta.setDisplayName(ColorUtil.colorize(rawName
                    .replace("%type%", entry.getType())
                    .replace("%id%", String.valueOf(entry.getId()))
            ));

            int cmd = histConfig.getInt(path + ".custom-model-data", 0);
            if (cmd > 0) meta.setCustomModelData(cmd);

            String sender = entry.getSenderName() != null ? entry.getSenderName() : "SYSTEM";
            String receiver = entry.getReceiverName() != null ? entry.getReceiverName() : "SYSTEM";

            List<String> rawLore = histConfig.getStringList(path + ".lore");
            List<String> lore = new ArrayList<>();
            for (String l : rawLore) {
                String line = l
                        .replace("%id%", String.valueOf(entry.getId()))
                        .replace("%timestamp%", entry.getTimestamp())
                        .replace("%sender%", sender)
                        .replace("%receiver%", receiver)
                        .replace("%amount%", ItemUtil.formatMoney(entry.getAmount()))
                        .replace("%tax%", ItemUtil.formatMoney(entry.getTaxAmount()))
                        .replace("%symbol%", symbol)
                        .replace("%currency%", currency)
                        .replace("%type%", entry.getType());
                lore.add(ColorUtil.colorize(line));
            }
            if (entry.getTaxAmount() > 0 && !rawLore.toString().contains("%tax%")) {
                lore.add(ColorUtil.colorize("&7Fee/Tax: &c-" + symbol + " " + ItemUtil.formatMoney(entry.getTaxAmount()) + " " + currency));
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    public void setPage(Player player, int newPage) {
        this.currentPage = Math.max(1, Math.min(totalPages, newPage));
        if (inventory != null) {
            inventory.clear();
            buildGUI();
        }
        try {
            FileConfiguration histConfig = plugin.getGuiManager().getHistoryConfig();
            String targetName = targetPlayer != null && targetPlayer.getName() != null ? targetPlayer.getName() : "Player";
            String rawTitle = histConfig.getString("title", "&#00E5FF&lHistory &#888888» &f%target% &8(%page%/%total_pages%)");
            String title = ColorUtil.colorize(rawTitle
                    .replace("%target%", targetName)
                    .replace("%page%", String.valueOf(currentPage))
                    .replace("%total_pages%", String.valueOf(totalPages))
            );
            player.getOpenInventory().setTitle(title);
        } catch (Throwable ignored) {
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof TransactionHistoryGUI gui) {
            event.setCancelled(true);

            if (!(event.getWhoClicked() instanceof Player player)) return;
            int slot = event.getRawSlot();

            FileConfiguration histConfig = gui.plugin.getGuiManager().getHistoryConfig();
            int prevSlot = histConfig.getInt("buttons.previous-page.slot", 45);
            int nextSlot = histConfig.getInt("buttons.next-page.slot", 53);
            int backSlot = histConfig.getInt("buttons.back.slot", 48);
            int closeSlot = histConfig.getInt("buttons.close.slot", 49);

            if (slot == backSlot) { // Back to Dashboard
                SoundUtil.playSound(gui.plugin, player, "admin-set");
                new ShardsMenuGUI(gui.plugin, player).open(player);
            } else if (slot == closeSlot) {
                player.closeInventory();
            } else if (slot == prevSlot && gui.currentPage > 1) { // Prev Page
                if (isDebounced(player.getUniqueId())) return;
                SoundUtil.playSound(gui.plugin, player, "admin-set");
                gui.setPage(player, gui.currentPage - 1);
            } else if (slot == nextSlot && gui.currentPage < gui.totalPages) { // Next Page
                if (isDebounced(player.getUniqueId())) return;
                SoundUtil.playSound(gui.plugin, player, "admin-set");
                gui.setPage(player, gui.currentPage + 1);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof TransactionHistoryGUI) {
            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot < event.getInventory().getSize()) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

