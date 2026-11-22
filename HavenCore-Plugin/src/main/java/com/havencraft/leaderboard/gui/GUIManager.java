package com.havencraft.leaderboard.gui;

import com.havencraft.leaderboard.HavenLeaderboard;
import com.havencraft.leaderboard.data.PlayerStat;
import com.havencraft.havencore.HavenCorePlugin;
import com.havencraft.havencore.stats.PlayerStats;
import com.havencraft.havencore.stats.StatisticType;
import com.havencraft.havencore.util.TextUtil;
import org.bukkit.Bukkit;

import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

public class GUIManager {

    private final HavenLeaderboard plugin;

    public GUIManager(HavenLeaderboard plugin) {
        this.plugin = plugin;
    }

    public void openMainMenu(Player player) {
        FileConfiguration config = plugin.getConfigManager().getMainGui();
        String title = TextUtil.colorize(config.getString("title", "Leaderboards"));
        int size = config.getInt("size", 27);

        LeaderboardHolder holder = new LeaderboardHolder("main");
        Inventory inv = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inv);

        // 1. Fill background first
        ConfigurationSection background = config.getConfigurationSection("background");
        if (background != null) {
            ItemStack bgItem = makeItem(background, null);
            List<Integer> slots = background.getIntegerList("slots");
            for (int slot : slots) {
                if (slot >= 0 && slot < size) {
                    inv.setItem(slot, bgItem);
                }
            }
        }

        // 2. Fill menu items second so they always take priority and never get overwritten
        ConfigurationSection items = config.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection itemSec = items.getConfigurationSection(key);
                if (itemSec != null) {
                    int slot = itemSec.getInt("slot");
                    if (slot >= 0 && slot < size) {
                        inv.setItem(slot, makeItem(itemSec, player));
                    }
                }
            }
        }

        player.openInventory(inv);
        playSound(player, "gui-open");
    }

    public void openLeaderboardMenu(Player player, StatisticType type, int page) {
        FileConfiguration config = plugin.getConfigManager().getGuiConfig(type.name().toLowerCase());
        
        String title = TextUtil.colorize(config.getString("title", type.getDisplayName() + " Leaderboard"));
        int size = config.getInt("size", 54);

        LeaderboardHolder holder = new LeaderboardHolder(type.name().toLowerCase(), type, page);
        Inventory inv = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inv);
        
        // Fill background
        ConfigurationSection background = config.getConfigurationSection("background");
        if (background != null) {
            ItemStack bgItem = makeItem(background, null);
            List<Integer> slots = background.getIntegerList("slots");
            for (int slot : slots) {
                inv.setItem(slot, bgItem);
            }
        }

        List<PlayerStat> topPlayers = plugin.getRankingManager().getTopPlayers(type);
        int maxPerPage = 45;
        int startIndex = (page - 1) * maxPerPage;
        int endIndex = Math.min(startIndex + maxPerPage, topPlayers.size());

        for (int i = startIndex; i < endIndex; i++) {
            PlayerStat stat = topPlayers.get(i);
            int slot = i - startIndex;
            
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            
            boolean headApplied = false;

            // 1. If player is currently online, check if their profile already has textures
            Player onlinePlayer = stat.getUuid() != null ? Bukkit.getPlayer(stat.getUuid()) : Bukkit.getPlayerExact(stat.getName());
            if (onlinePlayer != null) {
                org.bukkit.profile.PlayerProfile onlineProfile = onlinePlayer.getPlayerProfile();
                if (onlineProfile.getTextures().getSkin() != null) {
                    meta.setOwnerProfile(onlineProfile);
                    headApplied = true;
                    try {
                        java.net.URL skin = onlineProfile.getTextures().getSkin();
                        if (skin != null) {
                            String urlStr = skin.toString();
                            stat.setSkinUrl(urlStr);
                            PlayerStats raw = HavenCorePlugin.getInstance().getStatsCache().getStats(onlinePlayer.getUniqueId());
                            if (raw != null) raw.setSkinUrl(urlStr);
                        }
                    } catch (Exception ignored) {}
                }
            }

            // 2. If not applied, check cached skinUrl from stats
            String skinUrl = stat.getSkinUrl();
            if (!headApplied && (skinUrl == null || skinUrl.isEmpty()) && stat.getUuid() != null) {
                PlayerStats raw = HavenCorePlugin.getInstance().getStatsCache().getStats(stat.getUuid());
                if (raw != null) {
                    if (raw.getSkinUrl() == null || raw.getSkinUrl().isEmpty()) {
                        HavenCorePlugin.getInstance().getStatsCache().resolveOfflineSkin(stat.getUuid(), stat.getName(), raw);
                    }
                    skinUrl = raw.getSkinUrl();
                    stat.setSkinUrl(skinUrl);
                }
            }

            if (!headApplied && skinUrl != null && !skinUrl.isEmpty() && !skinUrl.equals("none")) {
                try {
                    org.bukkit.profile.PlayerProfile profile = Bukkit.createPlayerProfile(java.util.UUID.randomUUID(), stat.getName());
                    org.bukkit.profile.PlayerTextures textures = profile.getTextures();
                    textures.setSkin(java.net.URI.create(skinUrl).toURL());
                    profile.setTextures(textures);
                    meta.setOwnerProfile(profile);
                    headApplied = true;
                } catch (Exception ignored) {}
            }
            
            // 3. Fallback: If !headApplied, DO NOT call meta.setOwnerProfile(...)!
            // In Paper, setting an incomplete PlayerProfile (UUID/name without textures)
            // causes Paper to asynchronously fire requests to Mojang's session server.
            // On offline/cracked servers or for UUID v3, Mojang returns HTTP 429 / 404,
            // spamming console warnings and rate-limiting the server IP.
            // Leaving the player head without an incomplete profile renders the default
            // skull cleanly with zero network lag or HTTP 429 errors.
            
            int rank = i + 1;
            meta.setDisplayName(TextUtil.colorize("&e#" + rank + " &7- &f" + stat.getName()));
            
            List<String> lore = new ArrayList<>();
            if (config.isList("item-lore")) {
                for (String line : config.getStringList("item-lore")) {
                    lore.add(TextUtil.colorize(line.replace("{value}", stat.getFormattedValue())));
                }
            } else if (config.contains("item-lore")) {
                lore.add(TextUtil.colorize(config.getString("item-lore").replace("{value}", stat.getFormattedValue())));
            } else {
                lore.add(TextUtil.colorize("&7" + type.getDisplayName() + ": &e" + stat.getFormattedValue()));
            }
            meta.setLore(lore);
            head.setItemMeta(meta);
            
            inv.setItem(slot, head);
        }

        // Pagination buttons
        boolean hasNext = topPlayers.size() > endIndex;
        boolean hasPrev = page > 1;

        int prevSlot = config.getInt("buttons.previous.slot", size - 8);
        if (hasPrev) {
            ItemStack prev = makeButtonItem(config.getConfigurationSection("buttons.previous"), Material.ARROW, "&aPrevious Page", "&7Go to page " + (page - 1));
            inv.setItem(prevSlot, prev);
        }
        
        int backSlot = config.getInt("buttons.back.slot", size - 9);
        ItemStack back = makeButtonItem(config.getConfigurationSection("buttons.back"), Material.BARRIER, "&cBack to Menu", "&7Click to return");
        inv.setItem(backSlot, back);
        
        int closeSlot = config.getInt("buttons.close.slot", size - 5);
        ItemStack close = makeButtonItem(config.getConfigurationSection("buttons.close"), Material.RED_STAINED_GLASS_PANE, "&4Close", "&7Click to close");
        inv.setItem(closeSlot, close);
        
        int nextSlot = config.getInt("buttons.next.slot", size - 1);
        if (hasNext) {
            ItemStack next = makeButtonItem(config.getConfigurationSection("buttons.next"), Material.ARROW, "&aNext Page", "&7Go to page " + (page + 1));
            inv.setItem(nextSlot, next);
        }

        player.openInventory(inv);
        playSound(player, "gui-open");
    }

    private ItemStack makeItem(ConfigurationSection section, Player player) {
        String materialName = section.getString("material", "STONE");
        Material material = Material.matchMaterial(materialName);
        if (material == null) material = Material.STONE;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return item; // AIR has no ItemMeta
        }

        if (item.getType() == Material.PLAYER_HEAD) {
            String skullOwner = section.getString("skull-owner");
            if (skullOwner != null && !skullOwner.isEmpty()) {
                if (plugin.getPapiProvider() != null && player != null) {
                    skullOwner = plugin.getPapiProvider().parse(player, skullOwner);
                }
                SkullMeta skullMeta = (SkullMeta) meta;
                Player targetPlayer = (player != null && (player.getName().equalsIgnoreCase(skullOwner) || skullOwner.equals("%player_name%")))
                        ? player
                        : Bukkit.getPlayerExact(skullOwner);

                if (targetPlayer != null) {
                    org.bukkit.profile.PlayerProfile pProfile = targetPlayer.getPlayerProfile();
                    if (pProfile.getTextures().getSkin() != null) {
                        skullMeta.setOwnerProfile(pProfile);
                    } else {
                        // Check cached skin in CentralStatsCache
                        PlayerStats raw = HavenCorePlugin.getInstance().getStatsCache().getStats(targetPlayer.getUniqueId());
                        if (raw != null && raw.getSkinUrl() != null && raw.getSkinUrl().startsWith("http")) {
                            try {
                                org.bukkit.profile.PlayerProfile customProfile = Bukkit.createPlayerProfile(java.util.UUID.randomUUID(), targetPlayer.getName());
                                org.bukkit.profile.PlayerTextures textures = customProfile.getTextures();
                                textures.setSkin(java.net.URI.create(raw.getSkinUrl()).toURL());
                                customProfile.setTextures(textures);
                                skullMeta.setOwnerProfile(customProfile);
                            } catch (Exception ignored) {}
                        }
                    }
                }
                // If targetPlayer is offline or has no textures, do NOT set an incomplete profile without textures.
                // Doing so would trigger Paper to asynchronously call Mojang session service and cause HTTP 429 rate limits.
            }
        }

        String name = section.getString("name");
        if (name != null && !name.isEmpty()) {
            if (plugin.getPapiProvider() != null && player != null) {
                name = plugin.getPapiProvider().parse(player, name);
            }
            meta.setDisplayName(TextUtil.colorize(name));
        }

        List<String> lore = section.getStringList("lore");
        if (lore != null && !lore.isEmpty()) {
            List<String> coloredLore = new ArrayList<>();
            for (String line : lore) {
                if (plugin.getPapiProvider() != null && player != null) {
                    line = plugin.getPapiProvider().parse(player, line);
                }
                coloredLore.add(TextUtil.colorize(line));
            }
            meta.setLore(coloredLore);
        }
        item.setItemMeta(meta);

        return item;
    }

    private ItemStack createGuiItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(TextUtil.colorize(name));
        ArrayList<String> colorLore = new ArrayList<>();
        for (String l : lore) {
            colorLore.add(TextUtil.colorize(l));
        }
        meta.setLore(colorLore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeButtonItem(ConfigurationSection section, Material defMat, String defName, String defLore) {
        if (section == null) {
            return createGuiItem(defMat, defName, defLore);
        }
        String materialName = section.getString("material", defMat.name());
        Material material = Material.matchMaterial(materialName);
        if (material == null) material = defMat;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = section.getString("name", defName);
            meta.setDisplayName(TextUtil.colorize(name));
            
            List<String> lore = section.getStringList("lore");
            List<String> coloredLore = new ArrayList<>();
            if (lore == null || lore.isEmpty()) {
                coloredLore.add(TextUtil.colorize(defLore));
            } else {
                for (String l : lore) {
                    coloredLore.add(TextUtil.colorize(l));
                }
            }
            meta.setLore(coloredLore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public void playSound(Player player, String soundKey) {
        String soundName = plugin.getConfigManager().getSounds().getString(soundKey);
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
