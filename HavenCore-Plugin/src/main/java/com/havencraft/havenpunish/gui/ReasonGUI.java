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
 * Reason selection GUI — shows pre-defined reasons from reasons.yml.
 * Screen 3 (or 2 for permanent punishments) of the flow.
 *
 * Fully config-driven: each reason has its own slot, material, display, lore,
 * and reason text. Add/remove reasons from reasons.yml without touching code.
 */
public class ReasonGUI {

    private final FileConfiguration guiConfig;
    private final FileConfiguration reasonsConfig;
    private final SessionManager sessionManager;



    /** Cached reason entries from config — each with explicit slot. */
    private final Map<String, ReasonEntry> reasonMap = new LinkedHashMap<>();

    public record ReasonEntry(String id, int slot, String display, Material material,
                              String reason, List<String> lore) {}

    public ReasonGUI(FileConfiguration guiConfig, FileConfiguration reasonsConfig, SessionManager sessionManager) {
        this.guiConfig = guiConfig;
        this.reasonsConfig = reasonsConfig;
        this.sessionManager = sessionManager;
        loadReasons();
    }

    private void loadReasons() {
        reasonMap.clear();
        ConfigurationSection sec = reasonsConfig.getConfigurationSection("reasons");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            ConfigurationSection rs = sec.getConfigurationSection(id);
            if (rs == null) continue;
            reasonMap.put(id, new ReasonEntry(
                    id,
                    rs.getInt("slot", -1),
                    rs.getString("display", id),
                    mat(rs.getString("material", "PAPER")),
                    rs.getString("reason", id),
                    rs.getStringList("lore")
            ));
        }
    }

    /**
     * Opens the reason selection GUI for a staff member.
     * Every element is read from config — nothing is hardcoded.
     */
    public void open(Player staff) {
        PunishSession session = sessionManager.getSession(staff.getUniqueId());
        if (session == null) return;

        String title = c(guiConfig.getString("reason-menu.title", "&8&l📝 Select Reason"));
        int rows = guiConfig.getInt("reason-menu.rows", 4);
        Map<Integer, String> slotMap = new HashMap<>();
        PunishGUIHolder holder = new PunishGUIHolder(PunishGUIHolder.Type.REASON, slotMap);
        Inventory gui = createGui(holder, title, rows);
        holder.setInventory(gui);
        int size = gui.getSize();

        // ── Info item (slot from config, default: bottom row center) ──
        int infoSlot = guiConfig.getInt("reason-menu.info.slot", size - 5);
        Material infoMat = mat(guiConfig.getString("reason-menu.info.material", "WRITABLE_BOOK"));
        String infoName = guiConfig.getString("reason-menu.info.name", "&e&l📝 Select Reason");

        List<String> infoLore = new ArrayList<>();
        for (String line : guiConfig.getStringList("reason-menu.info.lore")) {
            infoLore.add(line
                    .replace("{action}", session.getAction().getDisplayName())
                    .replace("{player}", session.getTargetName())
                    .replace("{duration}", session.getDuration() != null ? session.getDuration() : "Permanent"));
        }
        if (infoLore.isEmpty()) {
            infoLore.add("&7Action: &f" + session.getAction().getDisplayName());
            infoLore.add("&7Target: &f" + session.getTargetName());
            if (session.getDuration() != null) {
                infoLore.add("&7Duration: &f" + session.getDuration());
            }
            infoLore.add("");
            infoLore.add("&7Choose a reason above.");
        }
        safeSet(gui, infoSlot, createItem(infoMat, infoName, infoLore));

        // ── Reason buttons — each with explicit slot from reasons.yml ──
        // If a reason has slot: -1 (no explicit slot), auto-place in first available
        int autoSlot = 0;
        for (ReasonEntry entry : reasonMap.values()) {
            int slot = entry.slot();

            // Auto-place: find next available slot (skip borders and info slot)
            if (slot < 0) {
                while (autoSlot < size && (gui.getItem(autoSlot) != null || autoSlot == infoSlot)) {
                    autoSlot++;
                }
                if (autoSlot >= size) continue;
                slot = autoSlot;
                autoSlot++;
            }

            if (slot >= size) continue;

            // Build lore
            List<String> lore = new ArrayList<>();
            if (!entry.lore().isEmpty()) {
                // Use config-provided lore
                for (String line : entry.lore()) {
                    lore.add(line.replace("{reason}", entry.reason()));
                }
            } else {
                // Auto-generate lore
                if (!entry.reason().isEmpty()) {
                    lore.add("&7Reason: &f" + entry.reason());
                } else {
                    lore.add("&7Type a custom reason in chat.");
                }
                lore.add("");
                lore.add("&eClick to select");
            }

            if (safeSet(gui, slot, createItem(entry.material(), entry.display(), lore))) {
                slotMap.put(slot, "reason:" + entry.id());
            }
        }

        // ── Back button (slot from config) ──
        ConfigurationSection backSec = guiConfig.getConfigurationSection("reason-menu.back");
        int backSlot = backSec != null ? backSec.getInt("slot", size - 4) : guiConfig.getInt("reason-menu.back-slot", size - 4);
        Material backMat = backSec != null ? mat(backSec.getString("material", "ARROW")) : Material.ARROW;
        String backName = backSec != null ? backSec.getString("name", "&c&l← Back") : "&c&l← Back";
        if (safeSet(gui, backSlot, createItem(backMat, backName))) {
            slotMap.put(backSlot, "back");
        }

        // Fill empty
        if (guiConfig.getBoolean("reason-menu.filler.enabled", true)) {
            fillEmpty(gui, mat(guiConfig.getString("reason-menu.filler.material", "BLACK_STAINED_GLASS_PANE")));
        }

        staff.openInventory(gui);
        playSound(staff, guiConfig, "open");
    }

    /**
     * Resolves a reason ID to its configured reason text.
     */
    public String resolveReason(String id) {
        ReasonEntry entry = reasonMap.get(id);
        return entry != null ? entry.reason() : null;
    }

    /**
     * Checks if a reason ID is the "custom" reason (empty reason text → chat input).
     */
    public boolean isCustomReason(String id) {
        String reason = resolveReason(id);
        return reason != null && reason.isEmpty();
    }

    // ━━━━━━━━━ Inventory tracking ━━━━━━━━━

    public boolean isOurGui(Inventory inv) {
        return inv != null && inv.getHolder() instanceof PunishGUIHolder h && h.getType() == PunishGUIHolder.Type.REASON;
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
