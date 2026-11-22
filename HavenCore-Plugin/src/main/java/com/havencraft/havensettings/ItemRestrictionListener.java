package com.havencraft.havensettings;

import com.havencraft.havencore.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ItemRestrictionListener implements Listener {

    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private boolean enabled;
    private String message;
    private Set<Material> restrictedItems;

    public ItemRestrictionListener(org.bukkit.plugin.java.JavaPlugin plugin, FileConfiguration config) {
        this.plugin = plugin;
        loadConfig(config);
    }

    public void loadConfig(FileConfiguration config) {
        this.enabled = config.getBoolean("restricted-items.enabled", false);
        this.message = TextUtil.colorize( 
            config.getString("restricted-items.message", "&cYou do not have permission to use this item!"));
        
        this.restrictedItems = new HashSet<>();
        List<String> items = config.getStringList("restricted-items.items");
        for (String itemStr : items) {
            try {
                Material mat = Material.valueOf(itemStr.toUpperCase());
                restrictedItems.add(mat);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material in restricted-items: " + itemStr);
            }
        }
    }

    private boolean isRestricted(Material material) {
        return restrictedItems.contains(material);
    }

    private boolean hasPermission(Player player, Material material) {
        if (player.isOp()) return true;
        return player.hasPermission("havensettings.use." + material.name().toLowerCase());
    }

    private void handleRestriction(Player player, org.bukkit.event.Cancellable event) {
        event.setCancelled(true);
        if (message != null && !message.isEmpty()) {
            player.sendMessage(message);
        }
    }

    @EventHandler
    public void onToggleGlide(EntityToggleGlideEvent event) {
        if (!enabled || !event.isGliding()) return;
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        ItemStack chestplate = player.getInventory().getChestplate();

        if (chestplate != null && chestplate.getType() == Material.ELYTRA) {
            if (isRestricted(Material.ELYTRA) && !hasPermission(player, Material.ELYTRA)) {
                handleRestriction(player, event);
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!enabled) return;
        if (!event.hasItem()) return;

        ItemStack item = event.getItem();
        if (item == null) return;

        Player player = event.getPlayer();
        if (isRestricted(item.getType()) && !hasPermission(player, item.getType())) {
            handleRestriction(player, event);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!enabled) return;
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        
        // If they are clicking an armor slot or using shift click
        if (event.getSlotType() == InventoryType.SlotType.ARMOR) {
            ItemStack cursor = event.getCursor();
            if (cursor != null && isRestricted(cursor.getType()) && !hasPermission(player, cursor.getType())) {
                handleRestriction(player, event);
            }
        } else if (event.isShiftClick()) {
            ItemStack currentItem = event.getCurrentItem();
            if (currentItem != null && isRestricted(currentItem.getType()) && !hasPermission(player, currentItem.getType())) {
                // To be safe, if it's an armor piece and shift clicked, we cancel it if restricted
                String typeName = currentItem.getType().name();
                if (typeName.endsWith("_HELMET") || typeName.endsWith("_CHESTPLATE") || 
                    typeName.endsWith("_LEGGINGS") || typeName.endsWith("_BOOTS") || 
                    typeName.equals("ELYTRA")) {
                    handleRestriction(player, event);
                }
            }
        }
    }
}
