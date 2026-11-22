package com.havencraft.havenpunish.gui;

import com.havencraft.havenpunish.manager.PunishExecutor;
import com.havencraft.havenpunish.manager.SessionManager;
import com.havencraft.havenpunish.model.PunishSession;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.havencraft.havenpunish.gui.GuiUtils.*;

/**
 * Confirmation GUI — shows a summary of the punishment and confirm/cancel buttons.
 * Final screen before execution.
 *
 * Fully config-driven: summary slot, confirm/cancel/silent slots, materials,
 * names, and lore are all editable from gui.yml.
 */
public class ConfirmGUI {

    private final FileConfiguration guiConfig;
    private final FileConfiguration messagesConfig;
    private final SessionManager sessionManager;
    private final PunishExecutor executor;



    public ConfirmGUI(FileConfiguration guiConfig, FileConfiguration messagesConfig,
                      SessionManager sessionManager, PunishExecutor executor) {
        this.guiConfig = guiConfig;
        this.messagesConfig = messagesConfig;
        this.sessionManager = sessionManager;
        this.executor = executor;
    }

    /**
     * Opens the confirmation GUI for a staff member.
     * Every element is read from gui.yml — nothing is hardcoded.
     */
    public void open(Player staff) {
        PunishSession session = sessionManager.getSession(staff.getUniqueId());
        if (session == null) return;

        String title = c(guiConfig.getString("confirm-menu.title", "&8&l⚖ Confirm Punishment"));
        int rows = guiConfig.getInt("confirm-menu.rows", 3);
        Map<Integer, String> slotMap = new HashMap<>();
        PunishGUIHolder holder = new PunishGUIHolder(PunishGUIHolder.Type.CONFIRM, slotMap);
        Inventory gui = createGui(holder, title, rows);
        holder.setInventory(gui);
        int size = gui.getSize();

        String targetName = session.getTargetName();
        String actionName = session.getAction().getDisplayName();
        String duration = session.getDuration() != null ? session.getDuration() : "Permanent";
        String reason = session.getReason() != null ? session.getReason() : "None";
        String silentStr = session.isSilent() ? "&aYes" : "&cNo";
        String command = "/" + session.buildCommand(staff);

        // ── Summary item (slot from config) ──
        ConfigurationSection summarySec = guiConfig.getConfigurationSection("confirm-menu.summary");
        int summarySlot = summarySec != null ? summarySec.getInt("slot", size - 5) : guiConfig.getInt("confirm-menu.summary-slot", size - 5);
        Material summaryMat = summarySec != null ? mat(summarySec.getString("material", "MAP")) : Material.MAP;
        String summaryName = summarySec != null ? summarySec.getString("name", "&e&l⚖ Punishment Summary") : "&e&l⚖ Punishment Summary";

        List<String> summaryLore = new ArrayList<>();
        if (summarySec != null && !summarySec.getStringList("lore").isEmpty()) {
            for (String line : summarySec.getStringList("lore")) {
                summaryLore.add(line
                        .replace("{player}", targetName)
                        .replace("{action}", actionName)
                        .replace("{duration}", duration)
                        .replace("{reason}", reason)
                        .replace("{silent}", silentStr)
                        .replace("{command}", command));
            }
        } else {
            summaryLore.add("");
            summaryLore.add("&7Target: &f" + targetName);
            summaryLore.add("&7Action: &f" + actionName);
            summaryLore.add("&7Duration: &f" + duration);
            summaryLore.add("&7Reason: &f" + reason);
            summaryLore.add("&7Silent: " + silentStr);
            summaryLore.add("");
            summaryLore.add("&7Command: &8" + command);
        }
        safeSet(gui, summarySlot, createItem(summaryMat, summaryName, summaryLore));

        // ── Confirm button (slot from config) ──
        ConfigurationSection confirmSec = guiConfig.getConfigurationSection("confirm-menu.confirm");
        int confirmSlot = confirmSec != null ? confirmSec.getInt("slot", 11) : guiConfig.getInt("confirm-menu.confirm-slot", 11);
        Material confirmMat = confirmSec != null ? mat(confirmSec.getString("material", "LIME_WOOL")) : Material.LIME_WOOL;
        String confirmName = confirmSec != null ? confirmSec.getString("name", "&a&l✔ CONFIRM") : "&a&l✔ CONFIRM";
        List<String> confirmLore = new ArrayList<>();
        if (confirmSec != null) {
            for (String line : confirmSec.getStringList("lore")) confirmLore.add(line);
        }
        if (confirmLore.isEmpty()) {
            confirmLore.add("&7Execute this punishment.");
            confirmLore.add("");
            confirmLore.add("&aClick to confirm");
        }
        if (safeSet(gui, confirmSlot, createItem(confirmMat, confirmName, confirmLore))) {
            slotMap.put(confirmSlot, "confirm");
        }

        // ── Cancel button (slot from config) ──
        ConfigurationSection cancelSec = guiConfig.getConfigurationSection("confirm-menu.cancel");
        int cancelSlot = cancelSec != null ? cancelSec.getInt("slot", 15) : guiConfig.getInt("confirm-menu.cancel-slot", 15);
        Material cancelMat = cancelSec != null ? mat(cancelSec.getString("material", "RED_WOOL")) : Material.RED_WOOL;
        String cancelName = cancelSec != null ? cancelSec.getString("name", "&c&l✖ CANCEL") : "&c&l✖ CANCEL";
        List<String> cancelLore = new ArrayList<>();
        if (cancelSec != null) {
            for (String line : cancelSec.getStringList("lore")) cancelLore.add(line);
        }
        if (cancelLore.isEmpty()) {
            cancelLore.add("&7Cancel and return to menu.");
            cancelLore.add("");
            cancelLore.add("&cClick to cancel");
        }
        if (safeSet(gui, cancelSlot, createItem(cancelMat, cancelName, cancelLore))) {
            slotMap.put(cancelSlot, "cancel");
        }

        // ── Silent toggle (slot from config) ──
        ConfigurationSection silentSec = guiConfig.getConfigurationSection("confirm-menu.silent");
        int silentSlot = silentSec != null ? silentSec.getInt("slot", 13) : guiConfig.getInt("confirm-menu.silent-slot", 13);

        Material silentMatOn = silentSec != null ? mat(silentSec.getString("material-on", "LIME_DYE")) : Material.LIME_DYE;
        Material silentMatOff = silentSec != null ? mat(silentSec.getString("material-off", "GRAY_DYE")) : Material.GRAY_DYE;
        String silentNameOn = silentSec != null ? silentSec.getString("name-on", "&a&lSilent: ON") : "&a&lSilent: ON";
        String silentNameOff = silentSec != null ? silentSec.getString("name-off", "&7&lSilent: OFF") : "&7&lSilent: OFF";

        Material silentMat = session.isSilent() ? silentMatOn : silentMatOff;
        String silentName = session.isSilent() ? silentNameOn : silentNameOff;

        List<String> silentLore = new ArrayList<>();
        if (silentSec != null) {
            for (String line : silentSec.getStringList("lore")) silentLore.add(line);
        }
        if (silentLore.isEmpty()) {
            silentLore.add("&7When silent, only staff see the");
            silentLore.add("&7punishment broadcast.");
            silentLore.add("");
            silentLore.add("&eClick to toggle");
        }
        if (safeSet(gui, silentSlot, createItem(silentMat, silentName, silentLore))) {
            slotMap.put(silentSlot, "toggle_silent");
        }

        // Fill empty
        if (guiConfig.getBoolean("confirm-menu.filler.enabled", true)) {
            fillEmpty(gui, mat(guiConfig.getString("confirm-menu.filler.material", "BLACK_STAINED_GLASS_PANE")));
        }

        staff.openInventory(gui);
        playSound(staff, guiConfig, "open");
    }

    /**
     * Executes the punishment from the confirmed session.
     * Returns true if executed successfully.
     */
    public boolean executeFromConfirm(Player staff) {
        PunishSession session = sessionManager.getSession(staff.getUniqueId());
        if (session == null) return false;

        boolean success = executor.execute(staff, session);
        if (success) {
            sessionManager.removeSession(staff.getUniqueId());
            String prefix = c(messagesConfig.getString("prefix", "&#92bed8HavenPunish &8• &7"));
            String msg = c(messagesConfig.getString("punishment-executed",
                    "&a✔ Punishment executed successfully!"));
            staff.sendMessage(prefix + msg);
        }
        return success;
    }

    // ━━━━━━━━━ Inventory tracking ━━━━━━━━━

    public boolean isOurGui(Inventory inv) {
        return inv != null && inv.getHolder() instanceof PunishGUIHolder h && h.getType() == PunishGUIHolder.Type.CONFIRM;
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
