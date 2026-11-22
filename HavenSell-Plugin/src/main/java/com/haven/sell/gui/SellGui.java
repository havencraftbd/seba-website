package com.haven.sell.gui;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.haven.sell.HavenSell;
import com.haven.sell.config.ConfigManager;
import com.haven.sell.config.GuiConfig;
import com.haven.sell.data.PlayerData;
import com.haven.sell.util.NumberFormatter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the creation and tracking of sell GUI inventories.
 * Tracks active sessions to prevent duplication exploits.
 */
public class SellGui {

    private final HavenSell plugin;

    /** Player UUIDs with the sell GUI open - anti-dupe protection. */
    private final Set<UUID> activeSessions = ConcurrentHashMap.newKeySet();

    /** Inventory references belonging to the sell GUI. */
    private final Set<Inventory> sellInventories = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public SellGui(HavenSell plugin) {
        this.plugin = plugin;
    }

    // ━━━━━━━━━━━━━━━ Open / Refresh ━━━━━━━━━━━━━━━

    public void openGui(Player player) {
        if (activeSessions.contains(player.getUniqueId())) return;

        GuiConfig guiConfig = plugin.getGuiConfig();
        int size = guiConfig.getSize();

        Inventory gui = Bukkit.createInventory(null, size, guiConfig.getTitle());

        // Fill bottom row with filler
        if (guiConfig.isFillerEnabled()) {
            ItemStack filler = createItem(guiConfig.getFillerMaterial(), guiConfig.getFillerName(), null);
            int bottomRowStart = size - 9;
            for (int i = bottomRowStart; i < size; i++) gui.setItem(i, filler);
        }

        if (guiConfig.isCloseEnabled()) {
            setCloseButton(gui, guiConfig);
        }
        if (guiConfig.isBalanceEnabled()) {
            setBalanceHead(gui, player, guiConfig);
        }

        if (!plugin.getConfigManager().isSellOnClose() && guiConfig.isSellEnabled()) {
            gui.setItem(guiConfig.getSellSlot(), createItem(guiConfig.getSellMaterial(), guiConfig.getSellName(), guiConfig.getSellLore()));
        }

        activeSessions.add(player.getUniqueId());
        sellInventories.add(gui);

        player.openInventory(gui);
        playSound(player, plugin.getConfigManager().getSound("gui-open"));
    }

    // ━━━━━━━━━━━━━━━ Button Builders ━━━━━━━━━━━━━━━

    private void setCloseButton(Inventory gui, GuiConfig config) {
        gui.setItem(config.getCloseSlot(), createItem(config.getCloseMaterial(), config.getCloseName(), config.getCloseLore()));
    }

    private void setBalanceHead(Inventory gui, Player player, GuiConfig config) {
        Material material = config.getBalanceMaterial();
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        if (material == Material.PLAYER_HEAD && meta instanceof SkullMeta skullMeta) {
            applyLocalPlayerSkin(skullMeta, player);
        }
        meta.setDisplayName(config.getBalanceName());

        PlayerData data = plugin.getDataManager().loadPlayerData(player.getUniqueId(), player.getName());
        double balance = plugin.getEconomyManager().getBalance(player);

        ConfigManager cm = plugin.getConfigManager();
        boolean abbrev = cm.isAbbreviationsEnabled();
        List<String> suffixes = cm.getAbbreviationSuffixes();

        // Format balance and total earned using abbreviation system
        String balanceFormatted = NumberFormatter.format(balance, abbrev, suffixes);
        String earnedFormatted  = NumberFormatter.format(data.getTotalMoneyEarned(), abbrev, suffixes);

        List<String> lore = config.getBalanceLore(
                balanceFormatted,
                data.getTotalItemsSold(),
                earnedFormatted,
                cm.getCurrencySymbol()
        );
        meta.setLore(lore);
        item.setItemMeta(meta);
        gui.setItem(config.getBalanceSlot(), item);
    }

    // ━━━━━━━━━━━━━━━ Session Management ━━━━━━━━━━━━━━━

    public boolean isSellGui(Inventory inventory)   { return sellInventories.contains(inventory); }
    public boolean hasActiveSession(UUID uuid)       { return activeSessions.contains(uuid); }

    public void removeSession(UUID uuid, Inventory inventory) {
        activeSessions.remove(uuid);
        sellInventories.remove(inventory);
    }

    public Set<Integer> getControlSlots() {
        int size = plugin.getGuiConfig().getSize();
        int bottomRowStart = size - 9;
        Set<Integer> slots = new HashSet<>();
        for (int i = bottomRowStart; i < size; i++) slots.add(i);
        return slots;
    }

    public void closeAllGuis() {
        for (UUID uuid : activeSessions) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) p.closeInventory();
        }
        activeSessions.clear();
        sellInventories.clear();
    }

    // ━━━━━━━━━━━━━━━ Utility ━━━━━━━━━━━━━━━

    private static void applyLocalPlayerSkin(SkullMeta skullMeta, Player player) {
        if (skullMeta == null || player == null) return;
        try {
            PlayerProfile playerProfile = player.getPlayerProfile();
            if (playerProfile.hasTextures()) {
                for (ProfileProperty property : playerProfile.getProperties()) {
                    if ("textures".equalsIgnoreCase(property.getName())
                            && property.getValue() != null
                            && !property.getValue().isEmpty()) {
                        PlayerProfile safeProfile = Bukkit.createProfile(UUID.randomUUID());
                        safeProfile.setProperty(new ProfileProperty("textures", property.getValue(), property.getSignature()));
                        skullMeta.setPlayerProfile(safeProfile);
                        return;
                    }
                }
                skullMeta.setPlayerProfile(playerProfile);
            }
        } catch (Throwable ignored) {
            // Never fall back to setOwningPlayer() — prevents Mojang HTTP 429 on cracked/offline servers
        }
    }

    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void playSound(Player player, String soundName) {
        if (soundName == null || soundName.isEmpty()) return;
        try {
            org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException ignored) {}
    }
}
