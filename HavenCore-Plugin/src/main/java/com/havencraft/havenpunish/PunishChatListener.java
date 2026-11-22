package com.havencraft.havenpunish;

import com.havencraft.havenpunish.gui.ConfirmGUI;
import com.havencraft.havenpunish.gui.DurationGUI;
import com.havencraft.havenpunish.gui.GuiUtils;
import com.havencraft.havenpunish.gui.ReasonGUI;
import com.havencraft.havenpunish.manager.SessionManager;
import com.havencraft.havenpunish.model.PunishSession;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Listens for chat messages from staff who are in "chat input" mode
 * (custom duration or custom reason).
 */
public class PunishChatListener implements Listener {

    private final JavaPlugin plugin;
    private final SessionManager sessionManager;
    private final DurationGUI durationGUI;
    private final ReasonGUI reasonGUI;
    private final ConfirmGUI confirmGUI;

    public PunishChatListener(JavaPlugin plugin, SessionManager sessionManager,
                               DurationGUI durationGUI, ReasonGUI reasonGUI, ConfirmGUI confirmGUI) {
        this.plugin = plugin;
        this.sessionManager = sessionManager;
        this.durationGUI = durationGUI;
        this.reasonGUI = reasonGUI;
        this.confirmGUI = confirmGUI;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player staff = event.getPlayer();
        PunishSession session = sessionManager.getSession(staff.getUniqueId());
        if (session == null || session.getPendingChatInput() == null) return;

        // If the session has been idle for more than 60 seconds, expire the pending chat input
        if (System.currentTimeMillis() - session.getLastActivityTime() > 60000L) {
            session.setPendingChatInput(null);
            sessionManager.removeSession(staff.getUniqueId());
            return;
        }

        // Cancel the chat message so it's not broadcast
        event.setCancelled(true);

        String message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

        // Handle cancel
        if (message.equalsIgnoreCase("cancel")) {
            session.setPendingChatInput(null);
            staff.sendMessage(GuiUtils.c("&c✖ Cancelled."));
            // Re-open the appropriate GUI on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!staff.isOnline()) return;
                if (session.getAction() == null) return;
                if (session.getAction().requiresDuration() && session.getDuration() == null) {
                    durationGUI.open(staff);
                } else {
                    reasonGUI.open(staff);
                }
            });
            return;
        }

        PunishSession.ChatInputType inputType = session.getPendingChatInput();
        session.setPendingChatInput(null);

        switch (inputType) {
            case CUSTOM_DURATION -> {
                // Validate duration format (basic check)
                if (!isValidDuration(message)) {
                    staff.sendMessage(GuiUtils.c("&c✖ Invalid duration format! Use: &f1m, 2h, 3d, 1w"));
                    staff.sendMessage(GuiUtils.c("&7Type a duration or &c'cancel'&7 to go back."));
                    session.setPendingChatInput(PunishSession.ChatInputType.CUSTOM_DURATION);
                    return;
                }
                session.setDuration(message);
                staff.sendMessage(GuiUtils.c("&a✔ Duration set: &f" + message));
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (staff.isOnline()) reasonGUI.open(staff);
                });
            }
            case CUSTOM_REASON -> {
                if (message.isEmpty()) {
                    staff.sendMessage(GuiUtils.c("&c✖ Reason cannot be empty!"));
                    session.setPendingChatInput(PunishSession.ChatInputType.CUSTOM_REASON);
                    return;
                }
                session.setReason(message);
                staff.sendMessage(GuiUtils.c("&a✔ Reason set: &f" + message));
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (staff.isOnline()) confirmGUI.open(staff);
                });
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(org.bukkit.event.player.PlayerCommandPreprocessEvent event) {
        Player staff = event.getPlayer();
        PunishSession session = sessionManager.getSession(staff.getUniqueId());
        if (session != null && session.getPendingChatInput() != null) {
            session.setPendingChatInput(null);
            sessionManager.removeSession(staff.getUniqueId());
            staff.sendMessage(GuiUtils.c("&c✖ Punishment session cancelled because you ran a command."));
        }
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        sessionManager.removeSession(event.getPlayer().getUniqueId());
    }

    /**
     * Basic duration format validation.
     * Accepts patterns like: 5m, 30m, 1h, 6h, 1d, 7d, 1w, 30d, etc.
     */
    private boolean isValidDuration(String input) {
        return input.matches("^\\d{1,4}[smhdwSMHDW]$");
    }
}
