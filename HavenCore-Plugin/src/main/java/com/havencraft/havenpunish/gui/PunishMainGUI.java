package com.havencraft.havenpunish.gui;

import com.havencraft.havenpunish.manager.SessionManager;
import com.havencraft.havenpunish.model.PunishAction;
import com.havencraft.havenpunish.model.PunishSession;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.havencraft.havenpunish.gui.GuiUtils.*;

/**
 * Main punishment menu — shows all available punishment types for a target player.
 * Screen 1 of the punishment flow.
 */
public class PunishMainGUI {

    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final FileConfiguration guiConfig;
    private final SessionManager sessionManager;



    public PunishMainGUI(org.bukkit.plugin.java.JavaPlugin plugin, FileConfiguration guiConfig, SessionManager sessionManager) {
        this.plugin = plugin;
        this.guiConfig = guiConfig;
        this.sessionManager = sessionManager;
    }

    /**
     * Opens the main punishment GUI for a staff member targeting a specific player.
     */
    public void open(Player staff, String targetName, UUID targetUuid) {
        // Create or replace the session
        sessionManager.createSession(staff.getUniqueId(), targetName, targetUuid);

        String title = c(guiConfig.getString("main-menu.title", "&8&l⚖ Punishment Menu — {player}"))
                .replace("{player}", targetName);
        int rows = guiConfig.getInt("main-menu.rows", 3);
        Map<Integer, String> slotMap = new HashMap<>();
        PunishGUIHolder holder = new PunishGUIHolder(PunishGUIHolder.Type.MAIN, slotMap);
        Inventory gui = createGui(holder, title, rows);
        holder.setInventory(gui);

        // Fill with glass panes
        if (guiConfig.getBoolean("main-menu.filler.enabled", true)) {
            fillEmpty(gui, mat(guiConfig.getString("main-menu.filler.material", "BLACK_STAINED_GLASS_PANE")));
        }

        // Target player head
        int headSlot = guiConfig.getInt("main-menu.target-head.slot", 4);
        Material headMat = mat(guiConfig.getString("main-menu.target-head.material", "PLAYER_HEAD"));
        ItemStack head = new ItemStack(headMat);

        // Fetch skin asynchronously to prevent lag spikes
        if (headMat == Material.PLAYER_HEAD && guiConfig.getBoolean("main-menu.target-head.fetch-skin", false)) {
            org.bukkit.profile.PlayerProfile profile = Bukkit.createPlayerProfile(targetUuid, targetName);
            profile.update().thenAcceptAsync(updatedProfile -> {
                if (staff.isOnline() && staff.getOpenInventory().getTopInventory() == gui) {
                    ItemStack currentItem = gui.getItem(headSlot);
                    if (currentItem != null && currentItem.getType() == Material.PLAYER_HEAD) {
                        SkullMeta skullMeta = (SkullMeta) currentItem.getItemMeta();
                        if (skullMeta != null) {
                            skullMeta.setOwnerProfile(updatedProfile);
                            currentItem.setItemMeta(skullMeta);
                        }
                    }
                }
            }, runnable -> Bukkit.getScheduler().runTask(plugin, runnable));
        }

        org.bukkit.inventory.meta.ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(c(guiConfig.getString("main-menu.target-head.name", "&e&l{player}"))
                    .replace("{player}", targetName));

            List<String> headLore = new ArrayList<>();
            for (String line : guiConfig.getStringList("main-menu.target-head.lore")) {
                headLore.add(c(line
                        .replace("{player}", targetName)
                        .replace("{uuid}", targetUuid.toString())));
            }
            meta.setLore(headLore);
            head.setItemMeta(meta);
        }
        safeSet(gui, headSlot, head);

        // Add punishment type buttons
        ConfigurationSection buttons = guiConfig.getConfigurationSection("main-menu.buttons");
        if (buttons != null) {
            for (String key : buttons.getKeys(false)) {
                ConfigurationSection btn = buttons.getConfigurationSection(key);
                if (btn == null) continue;

                // Check if staff has permission for this action
                String perm = btn.getString("permission", "");
                if (!perm.isEmpty() && !staff.hasPermission(perm)) continue;

                int slot = btn.getInt("slot", 0);
                Material material = mat(btn.getString("material", "STONE"));
                String name = btn.getString("name", key);

                List<String> lore = new ArrayList<>();
                for (String line : btn.getStringList("lore")) {
                    lore.add(line.replace("{player}", targetName));
                }

                if (safeSet(gui, slot, createItem(material, name, lore))) {
                    slotMap.put(slot, key);
                }
            }
        }

        // Close button
        int closeSlot = guiConfig.getInt("main-menu.close.slot", rows * 9 - 5);
        if (safeSet(gui, closeSlot, createItem(
                mat(guiConfig.getString("main-menu.close.material", "BARRIER")),
                guiConfig.getString("main-menu.close.name", "&c&lClose")
        ))) {
            slotMap.put(closeSlot, "close");
        }

        // Fill remaining empty slots
        if (guiConfig.getBoolean("main-menu.filler.enabled", true)) {
            fillEmpty(gui, mat(guiConfig.getString("main-menu.filler.material", "BLACK_STAINED_GLASS_PANE")));
        }

        staff.openInventory(gui);
        playSound(staff, guiConfig, "open");
    }

    /**
     * Resolves a clicked button key to a PunishAction, or null for special keys.
     */
    public PunishAction resolveAction(String key) {
        return switch (key) {
            case "mute" -> PunishAction.MUTE;
            case "tempmute" -> PunishAction.TEMPMUTE;
            case "ban" -> PunishAction.BAN;
            case "tempban" -> PunishAction.TEMPBAN;
            case "ipban" -> PunishAction.IPBAN;
            case "warn" -> PunishAction.WARN;
            case "kick" -> PunishAction.KICK;
            default -> null;
        };
    }

    // ━━━━━━━━━ Inventory tracking ━━━━━━━━━

    public boolean isOurGui(Inventory inv) {
        return inv != null && inv.getHolder() instanceof PunishGUIHolder h && h.getType() == PunishGUIHolder.Type.MAIN;
    }

    public Map<Integer, String> getSlotMap(Inventory inv) {
        if (inv != null && inv.getHolder() instanceof PunishGUIHolder h) {
            return h.getSlotMap();
        }
        return Collections.emptyMap();
    }

    public void unregister(Inventory inv) {
        // No-op: PunishGUIHolder automatically garbage collected with Inventory, eliminating memory leaks
    }

    public void closeAll() {
        // No-op
    }
}
