package com.havencraft.havenpunish;

import com.havencraft.havenpunish.gui.*;
import com.havencraft.havenpunish.manager.SessionManager;
import com.havencraft.havenpunish.model.PunishAction;
import com.havencraft.havenpunish.model.PunishSession;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import org.bukkit.configuration.file.FileConfiguration;
import java.util.Map;

/**
 * Central click handler for all HavenPunish GUIs.
 * Routes clicks to the appropriate GUI handler based on which GUI is open.
 */
public class PunishClickListener implements Listener {

    private final JavaPlugin plugin;
    private final FileConfiguration guiConfig;
    private final SessionManager sessionManager;
    private final PunishMainGUI mainGUI;
    private final DurationGUI durationGUI;
    private final ReasonGUI reasonGUI;
    private final ConfirmGUI confirmGUI;

    public PunishClickListener(JavaPlugin plugin, FileConfiguration guiConfig, SessionManager sessionManager,
                                PunishMainGUI mainGUI, DurationGUI durationGUI,
                                ReasonGUI reasonGUI, ConfirmGUI confirmGUI) {
        this.plugin = plugin;
        this.guiConfig = guiConfig;
        this.sessionManager = sessionManager;
        this.mainGUI = mainGUI;
        this.durationGUI = durationGUI;
        this.reasonGUI = reasonGUI;
        this.confirmGUI = confirmGUI;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player staff)) return;
        Inventory top = event.getView().getTopInventory();

        // ━━━━━ Main Menu ━━━━━
        if (mainGUI.isOurGui(top)) {
            event.setCancelled(true);
            if (event.getRawSlot() >= 0 && event.getRawSlot() < top.getSize()) {
                Map<Integer, String> slotMap = mainGUI.getSlotMap(top);
                if (slotMap != null && slotMap.containsKey(event.getRawSlot())) {
                    playSound(staff, "click");
                }
            }
            handleMainMenuClick(staff, top, event.getRawSlot());
            return;
        }

        // ━━━━━ Duration Menu ━━━━━
        if (durationGUI.isOurGui(top)) {
            event.setCancelled(true);
            if (event.getRawSlot() >= 0 && event.getRawSlot() < top.getSize()) {
                Map<Integer, String> slotMap = durationGUI.getSlotMap(top);
                if (slotMap != null && slotMap.containsKey(event.getRawSlot())) {
                    playSound(staff, "click");
                }
            }
            handleDurationClick(staff, top, event.getRawSlot());
            return;
        }

        // ━━━━━ Reason Menu ━━━━━
        if (reasonGUI.isOurGui(top)) {
            event.setCancelled(true);
            if (event.getRawSlot() >= 0 && event.getRawSlot() < top.getSize()) {
                Map<Integer, String> slotMap = reasonGUI.getSlotMap(top);
                if (slotMap != null && slotMap.containsKey(event.getRawSlot())) {
                    playSound(staff, "click");
                }
            }
            handleReasonClick(staff, top, event.getRawSlot());
            return;
        }

        // ━━━━━ Confirm Menu ━━━━━
        if (confirmGUI.isOurGui(top)) {
            event.setCancelled(true);
            if (event.getRawSlot() >= 0 && event.getRawSlot() < top.getSize()) {
                Map<Integer, String> slotMap = confirmGUI.getSlotMap(top);
                if (slotMap != null && slotMap.containsKey(event.getRawSlot())) {
                    playSound(staff, "click");
                }
            }
            handleConfirmClick(staff, top, event.getRawSlot());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top != null && top.getHolder() instanceof PunishGUIHolder) {
            for (int slot : event.getRawSlots()) {
                if (slot < top.getSize()) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        Inventory inv = event.getView().getTopInventory();
        boolean wasOurs = mainGUI.isOurGui(inv) || durationGUI.isOurGui(inv) || reasonGUI.isOurGui(inv) || confirmGUI.isOurGui(inv);
        if (wasOurs && event.getPlayer() instanceof Player player) {
            playSound(player, "close");
        }
        mainGUI.unregister(inv);
        durationGUI.unregister(inv);
        reasonGUI.unregister(inv);
        confirmGUI.unregister(inv);
    }

    // ━━━━━━━━━━━━━━━ Main Menu Handler ━━━━━━━━━━━━━━━

    private void handleMainMenuClick(Player staff, Inventory inv, int rawSlot) {
        if (rawSlot < 0 || rawSlot >= inv.getSize()) return;
        Map<Integer, String> slotMap = mainGUI.getSlotMap(inv);
        if (slotMap == null) return;
        String key = slotMap.get(rawSlot);
        if (key == null) return;

        if ("close".equals(key)) {
            sessionManager.removeSession(staff.getUniqueId());
            staff.closeInventory();
            return;
        }

        // Resolve punishment action
        PunishAction action = mainGUI.resolveAction(key);
        if (action == null) return;

        PunishSession session = sessionManager.getSession(staff.getUniqueId());
        if (session == null) return;

        session.setAction(action);

        // Next step: if action requires duration → DurationGUI, else → ReasonGUI
        if (action.requiresDuration()) {
            openNextTick(staff, () -> durationGUI.open(staff));
        } else {
            openNextTick(staff, () -> reasonGUI.open(staff));
        }
    }

    // ━━━━━━━━━━━━━━━ Duration Handler ━━━━━━━━━━━━━━━

    private void handleDurationClick(Player staff, Inventory inv, int rawSlot) {
        if (rawSlot < 0 || rawSlot >= inv.getSize()) return;
        Map<Integer, String> slotMap = durationGUI.getSlotMap(inv);
        if (slotMap == null) return;
        String key = slotMap.get(rawSlot);
        if (key == null) return;

        PunishSession session = sessionManager.getSession(staff.getUniqueId());
        if (session == null) return;

        if ("back".equals(key)) {
            openNextTick(staff, () -> mainGUI.open(staff, session.getTargetName(), session.getTargetUuid()));
            return;
        }

        if ("custom_duration".equals(key)) {
            // Switch to chat input mode
            session.setPendingChatInput(PunishSession.ChatInputType.CUSTOM_DURATION);
            staff.closeInventory();
            staff.sendMessage(GuiUtils.c("&e&l⏱ &eType a duration in chat &7(e.g. 10m, 2h, 5d)&e:"));
            staff.sendMessage(GuiUtils.c("&7Type &c'cancel' &7to go back."));
            return;
        }

        if (key.startsWith("duration:")) {
            String duration = key.substring("duration:".length());
            session.setDuration(duration);
            openNextTick(staff, () -> reasonGUI.open(staff));
        }
    }

    // ━━━━━━━━━━━━━━━ Reason Handler ━━━━━━━━━━━━━━━

    private void handleReasonClick(Player staff, Inventory inv, int rawSlot) {
        if (rawSlot < 0 || rawSlot >= inv.getSize()) return;
        Map<Integer, String> slotMap = reasonGUI.getSlotMap(inv);
        if (slotMap == null) return;
        String key = slotMap.get(rawSlot);
        if (key == null) return;

        PunishSession session = sessionManager.getSession(staff.getUniqueId());
        if (session == null) return;

        if ("back".equals(key)) {
            if (session.getAction().requiresDuration()) {
                openNextTick(staff, () -> durationGUI.open(staff));
            } else {
                openNextTick(staff, () -> mainGUI.open(staff, session.getTargetName(), session.getTargetUuid()));
            }
            return;
        }

        if (key.startsWith("reason:")) {
            String reasonId = key.substring("reason:".length());

            // Check if this is the "custom" reason (empty text → chat input)
            if (reasonGUI.isCustomReason(reasonId)) {
                session.setPendingChatInput(PunishSession.ChatInputType.CUSTOM_REASON);
                staff.closeInventory();
                staff.sendMessage(GuiUtils.c("&e&l📝 &eType your reason in chat:"));
                staff.sendMessage(GuiUtils.c("&7Type &c'cancel' &7to go back."));
                return;
            }

            String reasonText = reasonGUI.resolveReason(reasonId);
            if (reasonText != null) {
                session.setReason(reasonText);
                openNextTick(staff, () -> confirmGUI.open(staff));
            }
        }
    }

    // ━━━━━━━━━━━━━━━ Confirm Handler ━━━━━━━━━━━━━━━

    private void handleConfirmClick(Player staff, Inventory inv, int rawSlot) {
        if (rawSlot < 0 || rawSlot >= inv.getSize()) return;
        Map<Integer, String> slotMap = confirmGUI.getSlotMap(inv);
        if (slotMap == null) return;
        String key = slotMap.get(rawSlot);
        if (key == null) return;

        PunishSession session = sessionManager.getSession(staff.getUniqueId());
        if (session == null) return;

        switch (key) {
            case "confirm" -> {
                staff.closeInventory();
                confirmGUI.executeFromConfirm(staff);
            }
            case "cancel" -> {
                openNextTick(staff, () -> mainGUI.open(staff, session.getTargetName(), session.getTargetUuid()));
            }
            case "toggle_silent" -> {
                session.setSilent(!session.isSilent());
                openNextTick(staff, () -> confirmGUI.open(staff));
            }
        }
    }

    // ━━━━━━━━━━━━━━━ Utility ━━━━━━━━━━━━━━━

    /**
     * Opens a GUI on the next tick to avoid Bukkit's inventory modification restrictions.
     */
    private void openNextTick(Player player, Runnable action) {
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) action.run();
        }, 1L);
    }

    private void playSound(Player player, String soundKey) {
        String soundName = guiConfig.getString("sounds." + soundKey);
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
