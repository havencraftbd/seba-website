package com.havencraft.havencore.security;

import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.Locale;
import java.util.UUID;

/**
 * Event listener intercepting and preventing all known team enderchest dupe vectors,
 * unauthorized access after being kicked, and stale GUI exploits.
 */
public class TeamEchestSecurityListener implements Listener {

    private final TeamEchestSecurityManager manager;

    public TeamEchestSecurityListener(TeamEchestSecurityManager manager) {
        this.manager = manager;
    }

    /**
     * Intercepts inventory opening to enforce single-viewer lock, cooldowns, and records team session.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!manager.isEnabled()) {
            return;
        }

        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        if (!manager.isTeamEchest(event.getView())) {
            return;
        }

        // 1. Check Anti-Spam / Flood Cooldown
        if (manager.checkCooldown(player)) {
            event.setCancelled(true);
            manager.sendPrefixedMessage(player, manager.getCooldownMessage());
            return;
        }

        // 2. Enforce Single-Viewer Lock
        if (manager.isSingleViewerLock()) {
            HumanEntity conflictingViewer = manager.getConflictingViewer(event.getInventory(), player);
            if (conflictingViewer != null) {
                event.setCancelled(true);
                String msg = manager.getAlreadyInUseMessage().replace("{player}", conflictingViewer.getName());
                manager.sendPrefixedMessage(player, msg);
                manager.logSecurity("Blocked concurrent team echest open for '" + player.getName()
                        + "' (currently viewed by '" + conflictingViewer.getName() + "').");
                return;
            }
        }

        // 3. Record open session team
        manager.recordOpenTeam(player.getUniqueId(), player);
    }

    /**
     * Cleans up open session tracking when inventory is closed.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        manager.removeOpenTeam(event.getPlayer().getUniqueId());
    }

    /**
     * Real-time click protection fail-safe:
     * 1. Multi-viewer race condition interception
     * 2. Post-kick / Non-member unauthorized access interception & auto-punish
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!manager.isEnabled()) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!manager.isTeamEchest(event.getView())) {
            return;
        }

        // 1. Multi-viewer Conflict Fail-Safe
        if (manager.isClickProtection() && event.getInventory().getViewers().size() > 1) {
            event.setCancelled(true);
            player.closeInventory();
            manager.sendPrefixedMessage(player, manager.getConflictDetectedMessage());
            manager.logSecurity("Multiple viewers detected during click on team echest! Cancelled click and force-closed for '"
                    + player.getName() + "'.");
            return;
        }

        // 2. Team Membership Verification Fail-Safe (Post-Kick Protection)
        // Only triggers if the inventory was tracked as an open shared team chest
        if (manager.isValidateTeamMembership() && manager.getBetterTeamsHook().isAvailable()) {
            UUID openedTeamUuid = manager.getOpenedTeamUuid(player.getUniqueId());
            String openedTeam = manager.getOpenedTeam(player.getUniqueId());

            if (openedTeamUuid != null || openedTeam != null) {
                Object currentTeamObj = manager.getBetterTeamsHook().getTeam(player);
                boolean inSameTeam = false;

                if (currentTeamObj != null) {
                    if (openedTeamUuid != null) {
                        UUID currentTeamUuid = manager.getBetterTeamsHook().getTeamUuid(currentTeamObj);
                        if (openedTeamUuid.equals(currentTeamUuid)) {
                            inSameTeam = true;
                        }
                    }
                    if (!inSameTeam && openedTeam != null) {
                        String currentTeamName = manager.getBetterTeamsHook().getTeamName(currentTeamObj);
                        if (openedTeam.equalsIgnoreCase(currentTeamName)) {
                            inSameTeam = true;
                        }
                    }
                }

                // If player was kicked/left or team was disbanded and is no longer in THAT team:
                if (!inSameTeam) {
                    event.setCancelled(true);
                    player.closeInventory();
                    manager.removeOpenTeam(player.getUniqueId());
                    manager.sendPrefixedMessage(player, manager.getNotInTeamMessage());
                    manager.logSecurity("Cancelled unauthorized team echest click and safely closed GUI for '"
                            + player.getName() + "' (no longer in team).");
                    return;
                }
            }
        }
    }

    /**
     * Real-time drag protection fail-safe:
     * 1. Multi-viewer race condition interception
     * 2. Post-kick / Non-member unauthorized access interception & safe closure
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!manager.isEnabled()) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!manager.isTeamEchest(event.getView())) {
            return;
        }

        // 1. Multi-viewer Conflict Fail-Safe
        if (manager.isClickProtection() && event.getInventory().getViewers().size() > 1) {
            event.setCancelled(true);
            player.closeInventory();
            manager.sendPrefixedMessage(player, manager.getConflictDetectedMessage());
            manager.logSecurity("Multiple viewers detected during drag on team echest! Cancelled drag and force-closed for '"
                    + player.getName() + "'.");
            return;
        }

        // 2. Team Membership Verification Fail-Safe (Post-Kick Protection)
        if (manager.isValidateTeamMembership() && manager.getBetterTeamsHook().isAvailable()) {
            UUID openedTeamUuid = manager.getOpenedTeamUuid(player.getUniqueId());
            String openedTeam = manager.getOpenedTeam(player.getUniqueId());

            if (openedTeamUuid != null || openedTeam != null) {
                Object currentTeamObj = manager.getBetterTeamsHook().getTeam(player);
                boolean inSameTeam = false;

                if (currentTeamObj != null) {
                    if (openedTeamUuid != null) {
                        UUID currentTeamUuid = manager.getBetterTeamsHook().getTeamUuid(currentTeamObj);
                        if (openedTeamUuid.equals(currentTeamUuid)) {
                            inSameTeam = true;
                        }
                    }
                    if (!inSameTeam && openedTeam != null) {
                        String currentTeamName = manager.getBetterTeamsHook().getTeamName(currentTeamObj);
                        if (openedTeam.equalsIgnoreCase(currentTeamName)) {
                            inSameTeam = true;
                        }
                    }
                }

                if (!inSameTeam) {
                    event.setCancelled(true);
                    player.closeInventory();
                    manager.removeOpenTeam(player.getUniqueId());
                    manager.sendPrefixedMessage(player, manager.getNotInTeamMessage());
                    manager.logSecurity("Cancelled unauthorized team echest drag and safely closed GUI for '"
                            + player.getName() + "' (no longer in team).");
                    return;
                }
            }
        }
    }

    /**
     * Intercepts player commands to instantly close GUI on team kick / leave / disband.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (!manager.isEnabled()) return;
        processTeamCommand(event.getMessage(), event.getPlayer());
    }

    /**
     * Intercepts server / console commands to instantly close GUI on team kick / disband.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onServerCommand(ServerCommandEvent event) {
        if (!manager.isEnabled()) return;
        processTeamCommand(event.getCommand(), null);
    }

    /**
     * Helper to parse team management commands and force-close GUIs safely.
     * Defers check to the next tick after BetterTeams processes the command to prevent griefing.
     */
    private void processTeamCommand(String fullCommand, Player executor) {
        if (fullCommand == null || fullCommand.trim().isEmpty()) return;

        String cmd = fullCommand.trim();
        while (cmd.startsWith("/")) {
            cmd = cmd.substring(1).trim();
        }

        String[] parts = cmd.split("\\s+");
        if (parts.length < 2) return;

        String base = parts[0].toLowerCase(Locale.ROOT);
        if (base.startsWith("minecraft:") || base.startsWith("betterteams:")) {
            base = base.substring(base.indexOf(':') + 1);
        }

        // Check if it's a team command (team, tm, t, teama, betterteams)
        if (!base.equals("team") && !base.equals("tm") && !base.equals("t") && !base.equals("teama") && !base.equals("betterteams")) {
            return;
        }

        String sub = parts[1].toLowerCase(Locale.ROOT);

        // Run validation on the NEXT tick after BetterTeams has processed permissions and execution
        Bukkit.getScheduler().runTask(manager.getPlugin(), () -> {
            // 1. Kick Command: /team kick <player>
            if (sub.equals("kick") && parts.length >= 3 && manager.isCloseOnTeamKick()) {
                String targetName = parts[2];
                Player target = Bukkit.getPlayerExact(targetName);
                if (target == null) {
                    target = Bukkit.getPlayer(targetName);
                }
                if (target != null && target.isOnline()) {
                    UUID openedTeamUuid = manager.getOpenedTeamUuid(target.getUniqueId());
                    String openedTeam = manager.getOpenedTeam(target.getUniqueId());

                    if (openedTeamUuid != null || openedTeam != null) {
                        Object currentTeam = manager.getBetterTeamsHook().getTeam(target);
                        boolean stillInTeam = false;

                        if (currentTeam != null) {
                            if (openedTeamUuid != null && openedTeamUuid.equals(manager.getBetterTeamsHook().getTeamUuid(currentTeam))) {
                                stillInTeam = true;
                            } else if (openedTeam != null && openedTeam.equalsIgnoreCase(manager.getBetterTeamsHook().getTeamName(currentTeam))) {
                                stillInTeam = true;
                            }
                        }

                        if (!stillInTeam && target.getOpenInventory() != null && manager.isTeamEchest(target.getOpenInventory())) {
                            target.closeInventory();
                            manager.removeOpenTeam(target.getUniqueId());
                            manager.sendPrefixedMessage(target, manager.getNotInTeamMessage());
                            manager.logSecurity("Force-closed team echest for kicked player '" + target.getName() + "'.");
                        }
                    }
                }
            }

            // 2. Leave Command: /team leave
            else if (sub.equals("leave") && executor != null && manager.isCloseOnTeamLeave()) {
                if (executor.isOnline()) {
                    Object currentTeam = manager.getBetterTeamsHook().getTeam(executor);
                    if (currentTeam == null && executor.getOpenInventory() != null && manager.isTeamEchest(executor.getOpenInventory())) {
                        executor.closeInventory();
                        manager.removeOpenTeam(executor.getUniqueId());
                        manager.sendPrefixedMessage(executor, manager.getNotInTeamMessage());
                    }
                }
            }

            // 3. Disband Command: /team disband
            else if (sub.equals("disband") && manager.isCloseOnTeamDisband()) {
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    UUID openedTeamUuid = manager.getOpenedTeamUuid(onlinePlayer.getUniqueId());
                    String openedTeam = manager.getOpenedTeam(onlinePlayer.getUniqueId());

                    if (openedTeamUuid != null || openedTeam != null) {
                        Object currentTeam = manager.getBetterTeamsHook().getTeam(onlinePlayer);
                        if (currentTeam == null) {
                            if (onlinePlayer.getOpenInventory() != null && manager.isTeamEchest(onlinePlayer.getOpenInventory())) {
                                onlinePlayer.closeInventory();
                                manager.removeOpenTeam(onlinePlayer.getUniqueId());
                                manager.sendPrefixedMessage(onlinePlayer, manager.getNotInTeamMessage());
                                manager.logSecurity("Force-closed team echest for member '" + onlinePlayer.getName() + "' after team was disbanded.");
                            }
                        }
                    }
                }
            }
        });
    }

    /**
     * Forcibly closes team chest if player teleports.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!manager.isEnabled() || !manager.isCloseOnTeleport()) {
            return;
        }

        Player player = event.getPlayer();
        if (player.getOpenInventory() != null && manager.isTeamEchest(player.getOpenInventory())) {
            player.closeInventory();
        }
    }

    /**
     * Forcibly closes team chest if player takes damage (combat interrupt).
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!manager.isEnabled() || !manager.isCloseOnDamage()) {
            return;
        }

        if (event.getEntity() instanceof Player player) {
            if (player.getOpenInventory() != null && manager.isTeamEchest(player.getOpenInventory())) {
                player.closeInventory();
                manager.sendPrefixedMessage(player, manager.getClosedOnCombatMessage());
            }
        }
    }

    /**
     * Forcibly closes team chest if player changes worlds.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        if (!manager.isEnabled() || !manager.isCloseOnWorldChange()) {
            return;
        }

        Player player = event.getPlayer();
        if (player.getOpenInventory() != null && manager.isTeamEchest(player.getOpenInventory())) {
            player.closeInventory();
        }
    }

    /**
     * Cleans up player memory on quit.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        manager.cleanupPlayer(event.getPlayer().getUniqueId());
    }

    /**
     * Cleans up player memory on kick.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerKick(PlayerKickEvent event) {
        manager.cleanupPlayer(event.getPlayer().getUniqueId());
    }
}
