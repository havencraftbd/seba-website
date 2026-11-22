package com.havencraft.havencore.security;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Command executor for /opauth <password>.
 * Allows authorized administrators to authenticate with password to gain OP status safely.
 */
public class OpAuthCommand implements CommandExecutor, TabCompleter {

    private final OpSecurityManager securityManager;

    public OpAuthCommand(OpSecurityManager securityManager) {
        this.securityManager = securityManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!securityManager.isEnabled()) {
            securityManager.sendPrefixedMessage(sender, securityManager.getOpauthDisabled());
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Console already has full administrative access.");
            return true;
        }

        if (!player.hasPermission("havencore.admin.opauth")) {
            securityManager.sendPrefixedMessage(player, "&cYou do not have permission to execute this command.");
            return true;
        }

        if (!securityManager.isUseOpPassword()) {
            securityManager.sendPrefixedMessage(player, securityManager.getOpauthDisabled());
            return true;
        }

        if (!securityManager.isAllowedOpIssuer(player.getName())) {
            securityManager.sendPrefixedMessage(player, "&cYou are not authorized to use this command.");
            securityManager.logSecurity("&cUnauthorized player &e" + player.getName() + " &ctried to execute /opauth.");
            return true;
        }

        if (args.length < 1) {
            securityManager.sendPrefixedMessage(player, securityManager.getOpauthUsage());
            return true;
        }

        java.util.UUID uuid = player.getUniqueId();

        // 1. Check Lockout Status
        if (securityManager.isLockedOut(uuid)) {
            long remaining = securityManager.getRemainingLockoutSeconds(uuid);
            securityManager.sendPrefixedMessage(player, securityManager.getOpauthLockedOut().replace("{seconds}", String.valueOf(remaining)));
            return true;
        }

        // 2. Check Rate Limit / Spam Protection
        if (securityManager.isRateLimited(uuid)) {
            securityManager.sendPrefixedMessage(player, securityManager.getOpauthRateLimited());
            return true;
        }

        String inputPassword = args[0];
        if (securityManager.verifyOpPassword(inputPassword)) {
            securityManager.resetFailedAttempts(uuid);
            player.setOp(true);
            securityManager.sendPrefixedMessage(player, securityManager.getOpauthSuccess());
            securityManager.logSecurity(securityManager.getOpauthSuccessLog().replace("{player}", player.getName()));
        } else {
            int attempts = securityManager.recordFailedAttempt(uuid);
            securityManager.sendPrefixedMessage(player, securityManager.getOpauthWrongPassword());
            if (attempts >= OpSecurityManager.MAX_FAILED_ATTEMPTS) {
                securityManager.logSecurity("&cPlayer &e" + player.getName() + " &chas been locked out from /opauth due to excessive failed attempts!");
            } else {
                securityManager.logSecurity("&cPlayer &e" + player.getName() + " &cfailed /opauth password verification (Attempt " + attempts + "/" + OpSecurityManager.MAX_FAILED_ATTEMPTS + ").");
            }
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return Collections.emptyList();
    }
}
