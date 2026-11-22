package com.havencraft.havenpunish.gui;

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
 * Duration selection GUI — shown only for TEMPBAN and TEMPMUTE.
 * Screen 2 of the punishment flow (skipped for permanent punishments).
 *
 * Fully config-driven: all presets have their own slot, material, display name,
 * and lore. Add/remove presets from gui.yml without touching code.
 */
public class DurationGUI {

    private final FileConfiguration guiConfig;
    private final SessionManager sessionManager;



    public DurationGUI(FileConfiguration guiConfig, SessionManager sessionManager) {
        this.guiConfig = guiConfig;
        this.sessionManager = sessionManager;
    }

    /**
     * Opens the duration selection GUI for a staff member.
     * Every element is read from gui.yml — nothing is hardcoded.
     */
    public void open(Player staff) {
        PunishSession session = sessionManager.getSession(staff.getUniqueId());
        if (session == null) return;

        String title = c(guiConfig.getString("duration-menu.title", "&8&l⏱ Select Duration"));
        int rows = guiConfig.getInt("duration-menu.rows", 4);
        Map<Integer, String> slotMap = new HashMap<>();
        PunishGUIHolder holder = new PunishGUIHolder(PunishGUIHolder.Type.DURATION, slotMap);
        Inventory gui = createGui(holder, title, rows);
        holder.setInventory(gui);

        int size = gui.getSize();

        // ── Info item (slot from config, default: bottom row center) ──
        int infoSlot = guiConfig.getInt("duration-menu.info.slot", size - 5);
        Material infoMat = mat(guiConfig.getString("duration-menu.info.material", "CLOCK"));
        String infoName = guiConfig.getString("duration-menu.info.name", "&e&l⏱ Select Duration");

        List<String> infoLore = new ArrayList<>();
        for (String line : guiConfig.getStringList("duration-menu.info.lore")) {
            infoLore.add(line
                    .replace("{action}", session.getAction().getDisplayName())
                    .replace("{player}", session.getTargetName()));
        }
        // Fallback if config has no lore
        if (infoLore.isEmpty()) {
            infoLore.add("&7Punishment: &f" + session.getAction().getDisplayName());
            infoLore.add("&7Target: &f" + session.getTargetName());
            infoLore.add("");
            infoLore.add("&7Choose a duration above.");
        }
        safeSet(gui, infoSlot, createItem(infoMat, infoName, infoLore));

        // ── Duration presets (each has its own slot in config) ──
        ConfigurationSection presets = guiConfig.getConfigurationSection("duration-menu.presets");
        if (presets != null) {
            for (String key : presets.getKeys(false)) {
                ConfigurationSection ps = presets.getConfigurationSection(key);
                if (ps == null) continue;

                int slot = ps.getInt("slot", -1);
                if (slot < 0 || slot >= rows * 9) continue;

                String value = ps.getString("value", key);
                Material material = mat(ps.getString("material", "CLOCK"));
                String display = ps.getString("display", key);

                List<String> lore = new ArrayList<>();
                for (String line : ps.getStringList("lore")) {
                    lore.add(line.replace("{value}", value));
                }
                if (lore.isEmpty()) {
                    lore.add("&7Duration: &f" + value);
                    lore.add("");
                    lore.add("&eClick to select");
                }

                if (safeSet(gui, slot, createItem(material, display, lore))) {
                    slotMap.put(slot, "duration:" + value);
                }
            }
        }

        // ── Custom duration button (slot from config) ──
        ConfigurationSection customSec = guiConfig.getConfigurationSection("duration-menu.custom");
        if (customSec != null) {
            int customSlot = customSec.getInt("slot", size - 6);
            Material customMat = mat(customSec.getString("material", "NAME_TAG"));
            String customName = customSec.getString("name", "&b&l✏ Custom Duration");
            List<String> customLore = new ArrayList<>();
            for (String line : customSec.getStringList("lore")) customLore.add(line);
            if (customLore.isEmpty()) {
                customLore.add("&7Type a custom duration in chat.");
                customLore.add("&7Examples: &f10m&7, &f2h&7, &f5d");
                customLore.add("");
                customLore.add("&eClick to type");
            }
            if (safeSet(gui, customSlot, createItem(customMat, customName, customLore))) {
                slotMap.put(customSlot, "custom_duration");
            }
        }

        // ── Back button (slot from config) ──
        ConfigurationSection backSec = guiConfig.getConfigurationSection("duration-menu.back");
        int backSlot = backSec != null ? backSec.getInt("slot", size - 4) : guiConfig.getInt("duration-menu.back-slot", size - 4);
        Material backMat = backSec != null ? mat(backSec.getString("material", "ARROW")) : Material.ARROW;
        String backName = backSec != null ? backSec.getString("name", "&c&l← Back") : "&c&l← Back";
        if (safeSet(gui, backSlot, createItem(backMat, backName))) {
            slotMap.put(backSlot, "back");
        }

        // Fill empty
        if (guiConfig.getBoolean("duration-menu.filler.enabled", true)) {
            fillEmpty(gui, mat(guiConfig.getString("duration-menu.filler.material", "BLACK_STAINED_GLASS_PANE")));
        }

        staff.openInventory(gui);
        playSound(staff, guiConfig, "open");
    }

    // ━━━━━━━━━ Inventory tracking ━━━━━━━━━

    public boolean isOurGui(Inventory inv) {
        return inv != null && inv.getHolder() instanceof PunishGUIHolder h && h.getType() == PunishGUIHolder.Type.DURATION;
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
