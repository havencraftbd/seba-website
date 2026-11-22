package com.havencraft.havenpunish;

import com.havencraft.havenpunish.gui.GuiUtils;
import com.havencraft.havenpunish.gui.PunishMainGUI;
import org.bukkit.Bukkit;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the /punish command.
 * Usage: /punish <player>
 */
public class PunishCommand implements CommandExecutor, TabCompleter {

    private final HavenPunish module;
    private final PunishMainGUI mainGUI;
    private final FileConfiguration messagesConfig;
    private final FileConfiguration config;

    public PunishCommand(HavenPunish module, PunishMainGUI mainGUI,
                         FileConfiguration messagesConfig, FileConfiguration config) {
        this.module = module;
        this.mainGUI = mainGUI;
        this.messagesConfig = messagesConfig;
        this.config = config;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        String pfx = GuiUtils.c(messagesConfig.getString("prefix", "&#92bed8HavenPunish &8• &7"));

        // Reload sub-command
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("punish.reload")) {
                sender.sendMessage(pfx + GuiUtils.c(messagesConfig.getString("no-permission",
                        "&cYou don't have permission.")));
                return true;
            }
            module.reload();
            sender.sendMessage(pfx + GuiUtils.c(messagesConfig.getString("reload-success",
                    "&aHavenPunish reloaded successfully!")));
            return true;
        }

        // Must be a player
        if (!(sender instanceof Player staff)) {
            sender.sendMessage(pfx + GuiUtils.c(messagesConfig.getString("no-console",
                    "&cThis command is for players only.")));
            return true;
        }

        // Permission check
        if (!staff.hasPermission("punish.use")) {
            staff.sendMessage(pfx + GuiUtils.c(messagesConfig.getString("no-permission",
                    "&cYou don't have permission.")));
            return true;
        }

        // Require player argument
        if (args.length == 0) {
            staff.sendMessage(pfx + GuiUtils.c(messagesConfig.getString("usage",
                    "&cUsage: /punish <player>")));
            return true;
        }

        String targetName = args[0];
        // Enforce safe player name validation (Java 3-16 chars, Bedrock optional '.' prefix, cracked hyphens allowed).
        // Strictly disallow wildcards ('*') to prevent LiteBans global bans (/ban *),
        // and disallow names starting with '-' to prevent LiteBans command flag injection.
        if (targetName.contains("*") || targetName.startsWith("-") ||
                !targetName.matches("^[a-zA-Z0-9_.][a-zA-Z0-9_.-]{2,19}$")) {
            staff.sendMessage(pfx + GuiUtils.c("&c✖ Invalid player name format!"));
            return true;
        }

        // Check if LiteBans is present
        if (config.getBoolean("require-litebans", true)) {
            if (Bukkit.getPluginManager().getPlugin("LiteBans") == null) {
                staff.sendMessage(pfx + GuiUtils.c("&cLiteBans is not installed! Punishment system requires LiteBans."));
                return true;
            }
        }

        // Try to find the target player (online first)
        Player targetPlayer = Bukkit.getPlayerExact(targetName);
        java.util.UUID targetUuid;

        if (targetPlayer != null) {
            targetName = targetPlayer.getName(); // Get correct case
            targetUuid = targetPlayer.getUniqueId();

            // Check exempt permission
            String exemptPerm = config.getString("exempt-permission", "punish.exempt");
            if (targetPlayer.hasPermission(exemptPerm)) {
                staff.sendMessage(pfx + GuiUtils.c(messagesConfig.getString("target-exempt",
                        "&cThis player is exempt from punishment.")));
                return true;
            }

            // Can't punish yourself
            if (targetPlayer.getUniqueId().equals(staff.getUniqueId())) {
                staff.sendMessage(pfx + GuiUtils.c(messagesConfig.getString("self-punish",
                        "&cYou cannot punish yourself!")));
                return true;
            }
        } else {
            // Offline player
            if (!config.getBoolean("allow-offline", true)) {
                staff.sendMessage(pfx + GuiUtils.c(messagesConfig.getString("player-offline",
                        "&cThat player is not online.")));
                return true;
            }
            // Use offline player UUID lookup asynchronously to prevent main-thread blocking
            staff.sendMessage(pfx + GuiUtils.c("&7Looking up offline player data..."));
            String finalTargetName = targetName;
            Bukkit.getScheduler().runTaskAsynchronously(module.getJavaPlugin(), () -> {
                @SuppressWarnings("deprecation")
                org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(finalTargetName);
                java.util.UUID targetUuidAsync = offlinePlayer.getUniqueId();
                Bukkit.getScheduler().runTask(module.getJavaPlugin(), () -> {
                    if (staff.isOnline()) {
                        mainGUI.open(staff, finalTargetName, targetUuidAsync);
                    }
                });
            });
            return true;
        }

        // Open the main punishment GUI for online players immediately
        mainGUI.open(staff, targetName, targetUuid);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (!sender.hasPermission("punish.use")) return completions;

        if (args.length == 1) {
            // Suggest online players (excluding self and exempt)
            String partial = args[0].toLowerCase();
            String exemptPerm = config.getString("exempt-permission", "punish.exempt");
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.equals(sender)) continue;
                if (p.hasPermission(exemptPerm)) continue;
                if (p.getName().toLowerCase().startsWith(partial)) {
                    completions.add(p.getName());
                }
            }
        }

        return completions;
    }
}
