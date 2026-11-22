package com.haven.sell.gui;

import com.haven.sell.HavenSell;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles the confirmation GUI for /sell all and /sell hand.
 */
public class ConfirmGui implements Listener {

    private final HavenSell plugin;

    /** Map of player UUID -> Action to perform on confirm */
    private final Map<UUID, Runnable> pendingActions = new ConcurrentHashMap<>();
    
    /** Set of active confirm inventories */
    private final Map<Inventory, UUID> activeGuis = new ConcurrentHashMap<>();

    public ConfirmGui(HavenSell plugin) {
        this.plugin = plugin;
    }

    /**
     * Opens the confirmation GUI for a player and registers the action to execute on confirmation.
     */
    public void openConfirmGui(Player player, String title, Runnable onConfirm) {
        com.haven.sell.config.GuiConfig guiConfig = plugin.getGuiConfig();
        int size = guiConfig.getConfirmSize();
        Inventory gui = Bukkit.createInventory(null, size, title);

        // Fill background
        if (guiConfig.isConfirmFillerEnabled()) {
            ItemStack filler = createItem(guiConfig.getConfirmFillerMaterial(), guiConfig.getConfirmFillerName(), null);
            for (int i = 0; i < size; i++) {
                gui.setItem(i, filler);
            }
        }

        // Confirm Button
        ItemStack confirmBtn = createItem(guiConfig.getConfirmButtonMaterial(), guiConfig.getConfirmButtonName(), guiConfig.getConfirmButtonLore());
        gui.setItem(guiConfig.getConfirmButtonSlot(), confirmBtn);

        // Cancel Button
        ItemStack cancelBtn = createItem(guiConfig.getCancelButtonMaterial(), guiConfig.getCancelButtonName(), guiConfig.getCancelButtonLore());
        gui.setItem(guiConfig.getCancelButtonSlot(), cancelBtn);

        pendingActions.put(player.getUniqueId(), onConfirm);
        activeGuis.put(gui, player.getUniqueId());

        player.openInventory(gui);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory topInv = event.getView().getTopInventory();
        if (!activeGuis.containsKey(topInv)) return;

        event.setCancelled(true); // Prevent item moving

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= topInv.getSize()) return;

        UUID uuid = player.getUniqueId();
        com.haven.sell.config.GuiConfig guiConfig = plugin.getGuiConfig();

        if (rawSlot == guiConfig.getConfirmButtonSlot()) { // Confirm
            Runnable action = pendingActions.remove(uuid);
            activeGuis.remove(topInv);
            player.closeInventory();
            
            if (action != null) {
                // Run on next tick
                Bukkit.getScheduler().runTask(plugin, action);
            }
        } else if (rawSlot == guiConfig.getCancelButtonSlot()) { // Cancel
            pendingActions.remove(uuid);
            activeGuis.remove(topInv);
            player.closeInventory();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        Inventory topInv = event.getView().getTopInventory();
        if (activeGuis.remove(topInv) != null) {
            pendingActions.remove(event.getPlayer().getUniqueId());
        }
    }

    private ItemStack createItem(Material material, String name, java.util.List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
