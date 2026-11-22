package com.havencraft.havencore.security;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerCommandEvent;

/**
 * Event listener enforcing automatic De-OP on Join/Leave and restricting /op and /deop commands.
 * Runs instantly with O(1) performance and ZERO impact on TPS/CPU/RAM.
 */
public class OpSecurityListener implements Listener {

    private final OpSecurityManager securityManager;

    public OpSecurityListener(OpSecurityManager securityManager) {
        this.securityManager = securityManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerQuit(PlayerQuitEvent event) {
        handlePlayerLeave(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerKick(PlayerKickEvent event) {
        handlePlayerLeave(event.getPlayer());
    }

    private void handlePlayerLeave(Player player) {
        if (!securityManager.isEnabled() || !securityManager.isDeopOnQuit()) {
            return;
        }

        if (isPlayerOpOrInOpsJson(player)) {
            securityManager.deopPlayer(player);
            securityManager.logSecurity(securityManager.getDeopOnQuitLog().replace("{player}", player.getName()));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!securityManager.isEnabled() || !securityManager.isDeopOnJoin()) {
            return;
        }

        Player player = event.getPlayer();
        if (isPlayerOpOrInOpsJson(player)) {
            securityManager.deopPlayer(player);
            securityManager.sendPrefixedMessage(player, securityManager.getDeopOnJoinNotice());
            securityManager.logSecurity(securityManager.getDeopOnJoinLog().replace("{player}", player.getName()));
        }
    }

    public static class ParsedCommand {
        private final String baseCommand;
        private final String[] args;

        public ParsedCommand(String baseCommand, String[] args) {
            this.baseCommand = baseCommand;
            this.args = args;
        }

        public String getBaseCommand() {
            return baseCommand;
        }

        public String[] getArgs() {
            return args;
        }
    }

    /**
     * Iteratively unwraps command chains (such as /execute ... run <cmd>) to inspect the terminal command.
     */
    public static ParsedCommand parseTerminalCommand(String fullCommand) {
        if (fullCommand == null) return null;
        String cmd = fullCommand.trim();
        while (cmd.startsWith("/")) {
            cmd = cmd.substring(1).trim();
        }
        if (cmd.isEmpty()) return null;

        while (true) {
            String[] tokens = cmd.split("\\s+");
            if (tokens.length == 0 || tokens[0].isEmpty()) return null;

            String label = tokens[0].toLowerCase(java.util.Locale.ROOT);
            if (label.startsWith("minecraft:")) {
                label = label.substring(10);
            }

            if (label.equals("execute")) {
                int runIndex = -1;
                for (int i = 1; i < tokens.length; i++) {
                    if (tokens[i].equalsIgnoreCase("run")) {
                        runIndex = i;
                        break;
                    }
                }
                if (runIndex != -1 && runIndex + 1 < tokens.length) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = runIndex + 1; i < tokens.length; i++) {
                        if (sb.length() > 0) sb.append(" ");
                        sb.append(tokens[i]);
                    }
                    cmd = sb.toString().trim();
                    continue;
                } else {
                    return new ParsedCommand("execute", java.util.Arrays.copyOfRange(tokens, 1, tokens.length));
                }
            }

            String cleanLabel = tokens[0].toLowerCase(java.util.Locale.ROOT);
            if (cleanLabel.startsWith("minecraft:")) {
                cleanLabel = cleanLabel.substring(10);
            }
            String[] args = tokens.length > 1 ? java.util.Arrays.copyOfRange(tokens, 1, tokens.length) : new String[0];
            return new ParsedCommand(cleanLabel, args);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (!securityManager.isEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        ParsedCommand parsed = parseTerminalCommand(event.getMessage());
        if (parsed == null) {
            return;
        }

        String commandLabel = parsed.getBaseCommand();
        String[] parts = parsed.getArgs();

        boolean isOpCmd = commandLabel.equals("op");
        boolean isDeopCmd = commandLabel.equals("deop");

        if (isOpCmd && securityManager.isRestrictOpCommand()) {
            if (!securityManager.isAllowedOpIssuer(player.getName())) {
                event.setCancelled(true);
                securityManager.sendPrefixedMessage(player, securityManager.getOpCommandBlocked());
                securityManager.logSecurity(securityManager.getOpCommandBlockedLog()
                        .replace("{player}", player.getName())
                        .replace("{command}", "/" + event.getMessage().trim()));
            } else if (securityManager.isRestrictConsoleOpTargets() && parts.length >= 1) {
                String targetPlayer = parts[0];
                if (!securityManager.isAllowedOpIssuer(targetPlayer)) {
                    event.setCancelled(true);
                    securityManager.sendPrefixedMessage(player, "&cCannot grant OP to &e" + targetPlayer + "&c! Access denied.");
                    securityManager.logSecurity("&cPlayer &e" + player.getName() + " &ctried to OP unauthorized target: &f" + targetPlayer);
                }
            }
        } else if (isDeopCmd && securityManager.isRestrictDeopCommand()) {
            if (!securityManager.isAllowedOpIssuer(player.getName())) {
                event.setCancelled(true);
                securityManager.sendPrefixedMessage(player, securityManager.getDeopCommandBlocked());
                securityManager.logSecurity(securityManager.getOpCommandBlockedLog()
                        .replace("{player}", player.getName())
                        .replace("{command}", "/" + event.getMessage().trim()));
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        if (!securityManager.isEnabled()) {
            return;
        }

        ParsedCommand parsed = parseTerminalCommand(event.getCommand());
        if (parsed == null) {
            return;
        }

        String commandLabel = parsed.getBaseCommand();
        String[] parts = parsed.getArgs();

        boolean isOpCmd = commandLabel.equals("op");

        if (!isOpCmd) {
            return;
        }

        if (securityManager.isBlockConsoleOp()) {
            event.setCancelled(true);
            securityManager.logSecurity(securityManager.getConsoleOpBlockedLog());
            return;
        }

        if (securityManager.isRestrictConsoleOpTargets()) {
            if (parts.length >= 1) {
                String targetPlayer = parts[0];
                if (!securityManager.isAllowedOpIssuer(targetPlayer)) {
                    event.setCancelled(true);
                    securityManager.logSecurity(securityManager.getConsoleOpUnauthorizedTargetLog()
                            .replace("{player}", targetPlayer));
                }
            }
        }
    }

    private boolean isPlayerOpOrInOpsJson(Player player) {
        if (player == null) return false;
        if (player.isOp()) return true;
        try {
            for (OfflinePlayer op : Bukkit.getOperators()) {
                if (op == null) continue;
                boolean matchName = op.getName() != null && op.getName().equalsIgnoreCase(player.getName());
                boolean matchUuid = op.getUniqueId() != null && op.getUniqueId().equals(player.getUniqueId());
                if (matchName || matchUuid) {
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }
}

