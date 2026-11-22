package com.havencraft.havencore.stats;

import com.havencraft.havencore.HavenCorePlugin;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import com.havencraft.havencore.util.ConfigLoader;
import com.havencraft.havencore.util.TextUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.Sound;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class StatsGUI implements Listener {

    private final HavenCorePlugin plugin;
    private FileConfiguration guiConfig;
    private File guiFile;

    public StatsGUI(HavenCorePlugin plugin) {
        this.plugin = plugin;
        reloadConfig();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void reloadConfig() {
        guiConfig = ConfigLoader.load(plugin, "Stats/gui.yml");
    }

    public void openGUI(Player viewer, OfflinePlayer target) {
        String title = guiConfig.getString("title", "&8Player Stats");
        int size = guiConfig.getInt("size", 45);
        StatsGUIHolder holder = new StatsGUIHolder();
        Inventory inv = Bukkit.createInventory(holder, size, TextUtil.parseLegacy(title));
        holder.setInventory(inv);

        int headSlot = guiConfig.getInt("player_head_slot", -1);
        if (headSlot >= 0 && headSlot < size) {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                boolean headApplied = false;
                if (target.isOnline() && target.getPlayer() != null) {
                    org.bukkit.profile.PlayerProfile pProfile = target.getPlayer().getPlayerProfile();
                    if (pProfile.getTextures().getSkin() != null) {
                        meta.setOwnerProfile(pProfile);
                        headApplied = true;
                    }
                }
                if (!headApplied && target.getUniqueId() != null) {
                    PlayerStats raw = plugin.getStatsCache().getStats(target.getUniqueId());
                    if (raw != null) {
                        if (raw.getSkinUrl() == null || raw.getSkinUrl().isEmpty()) {
                            plugin.getStatsCache().resolveOfflineSkin(target.getUniqueId(), target.getName(), raw);
                        }
                        String skinUrl = raw.getSkinUrl();
                        if (skinUrl != null && !skinUrl.isEmpty() && !skinUrl.equals("none")) {
                            try {
                                org.bukkit.profile.PlayerProfile profile = Bukkit.createPlayerProfile(java.util.UUID.randomUUID(), target.getName());
                                org.bukkit.profile.PlayerTextures textures = profile.getTextures();
                                textures.setSkin(java.net.URI.create(skinUrl).toURL());
                                profile.setTextures(textures);
                                meta.setOwnerProfile(profile);
                                headApplied = true;
                            } catch (Exception ignored) {}
                        }
                    }
                }
                meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + target.getName() + "'s Stats");
                head.setItemMeta(meta);
            }
            inv.setItem(headSlot, head);
        }

        ConfigurationSection itemsSec = guiConfig.getConfigurationSection("items");
        if (itemsSec != null) {
            for (String key : itemsSec.getKeys(false)) {
                ConfigurationSection itemSec = itemsSec.getConfigurationSection(key);
                if (itemSec == null) continue;

                Material mat = Material.matchMaterial(itemSec.getString("material", "STONE"));
                if (mat == null) mat = Material.STONE;
                
                ItemStack item = new ItemStack(mat);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    String name = itemSec.getString("name");
                    if (name != null) {
                        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', PlaceholderAPI.setPlaceholders(target, name)));
                    }
                    List<String> lore = itemSec.getStringList("lore");
                    if (!lore.isEmpty()) {
                        List<String> coloredLore = new ArrayList<>();
                        for (String line : lore) {
                            coloredLore.add(ChatColor.translateAlternateColorCodes('&', PlaceholderAPI.setPlaceholders(target, line)));
                        }
                        meta.setLore(coloredLore);
                    }
                    item.setItemMeta(meta);
                }

                if (itemSec.contains("slot")) {
                    int slot = itemSec.getInt("slot");
                    if (slot >= 0 && slot < size) {
                        inv.setItem(slot, item);
                    }
                } else if (itemSec.contains("slots")) {
                    List<Integer> slots = itemSec.getIntegerList("slots");
                    for (int slot : slots) {
                        if (slot >= 0 && slot < size) {
                            inv.setItem(slot, item);
                        }
                    }
                }
            }
        }

        viewer.openInventory(inv);
        playSound(viewer, "open");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof StatsGUIHolder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getView().getTopInventory().getSize()) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        if (clicked.getType() == Material.BARRIER) {
            playSound(player, "close");
            player.closeInventory();
        } else {
            playSound(player, "click");
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof StatsGUIHolder) {
            event.setCancelled(true);
        }
    }

    private void playSound(Player player, String soundKey) {
        String soundName = guiConfig.getString("sounds." + soundKey);
        if (soundName != null && !soundName.isEmpty()) {
            try {
                Sound sound = Sound.valueOf(soundName.toUpperCase());
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            } catch (IllegalArgumentException e) {
                try {
                    player.playSound(player.getLocation(), soundName.toLowerCase(), 1.0f, 1.0f);
                } catch (Exception ignored) {}
            }
        }
    }
}
