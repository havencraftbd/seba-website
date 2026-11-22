package com.havencraft.leaderboard.listeners;

import com.havencraft.leaderboard.HavenLeaderboard;
import com.havencraft.havencore.stats.StatisticType;
import com.havencraft.leaderboard.gui.LeaderboardHolder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class InventoryListener implements Listener {

    private final HavenLeaderboard plugin;

    public InventoryListener(HavenLeaderboard plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof LeaderboardHolder)) return;
        
        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getInventory().getSize()) return;
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;

        Player player = (Player) event.getWhoClicked();
        LeaderboardHolder holder = (LeaderboardHolder) event.getInventory().getHolder();

        plugin.getGuiManager().playSound(player, "gui-click");

        if (holder.getGuiName().equals("main")) {
            // Check slot to determine which category clicked
            // Iterate config to find category
            if (plugin.getConfigManager().getMainGui().getConfigurationSection("items") == null) {
                return;
            }
            for (String key : plugin.getConfigManager().getMainGui().getConfigurationSection("items").getKeys(false)) {
                int slot = plugin.getConfigManager().getMainGui().getInt("items." + key + ".slot");
                if (event.getRawSlot() == slot) {
                    StatisticType type = StatisticType.fromString(key);
                    if (type != null) {
                        if (plugin.getRankingManager().isEnabled(type)) {
                            plugin.getGuiManager().openLeaderboardMenu(player, type, 1);
                        } else {
                            player.sendMessage(plugin.getConfigManager().getMessage("category-disabled"));
                        }
                    }
                    return;
                }
            }
        } else {
            // We are in a leaderboard category view
            org.bukkit.configuration.file.FileConfiguration config = plugin.getConfigManager().getGuiConfig(holder.getStatType().name().toLowerCase());
            int slot = event.getRawSlot();
            int size = event.getInventory().getSize();
            
            int prevSlot = config.getInt("buttons.previous.slot", size - 8);
            int nextSlot = config.getInt("buttons.next.slot", size - 1);
            int backSlot = config.getInt("buttons.back.slot", size - 9);
            int closeSlot = config.getInt("buttons.close.slot", size - 5);

            if (slot == prevSlot) {
                if (holder.getPage() > 1) {
                    plugin.getGuiManager().openLeaderboardMenu(player, holder.getStatType(), holder.getPage() - 1);
                }
            } else if (slot == nextSlot) {
                int maxPerPage = 45; // Same as GUIManager
                int totalPlayers = plugin.getRankingManager().getTopPlayers(holder.getStatType()).size();
                if (totalPlayers > holder.getPage() * maxPerPage) {
                    plugin.getGuiManager().openLeaderboardMenu(player, holder.getStatType(), holder.getPage() + 1);
                }
            } else if (slot == backSlot) {
                plugin.getGuiManager().openMainMenu(player);
            } else if (slot == closeSlot) {
                player.closeInventory();
            }
        }
    }

    @EventHandler
    public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof LeaderboardHolder) {
            if (event.getPlayer() instanceof Player player) {
                plugin.getGuiManager().playSound(player, "gui-close");
            }
        }
    }
}
