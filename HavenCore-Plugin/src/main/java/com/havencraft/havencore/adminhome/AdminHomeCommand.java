package com.havencraft.havencore.adminhome;

import com.havencraft.havencore.HavenCorePlugin;
import com.havencraft.havencore.security.BetterTeamsHook;
import com.havencraft.havencore.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class AdminHomeCommand implements CommandExecutor, TabCompleter {

    private final HavenCorePlugin plugin;
    private final AdminHomeListener listener;

    public AdminHomeCommand(HavenCorePlugin plugin, AdminHomeListener listener) {
        this.plugin = plugin;
        this.listener = listener;
    }

    private String getPrefix() {
        return plugin.getConfig().getString("admin-home.prefix", plugin.getGlobalPrefix());
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player admin)) {
            TextUtil.sendMessage(sender, getPrefix() + "&cThis command can only be executed by players!");
            return true;
        }

        if (!admin.hasPermission("havencore.admin.ahome")) {
            TextUtil.sendMessage(admin, getPrefix() + "&cYou do not have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            TextUtil.sendMessage(admin, getPrefix() + "&eUsage: &f/ahome <player> &7or &f/ahome team:<teamname>");
            return true;
        }

        String input = args[0].trim();

        // 1. Direct Team Home lookup via "team:<name>"
        if (input.toLowerCase().startsWith("team:")) {
            String teamName = input.substring(5).trim();
            if (teamName.isEmpty()) {
                TextUtil.sendMessage(admin, getPrefix() + "&cPlease specify a valid team name. &eExample: /ahome team:Alpha");
                return true;
            }
            listener.openTeamActionGui(admin, teamName);
            return true;
        }

        // 2. Resolve Player Home lookup first
        String targetName = input;
        UUID targetUuid = null;
        String resolvedName = null;

        // Check online players
        Player onlinePlayer = Bukkit.getPlayerExact(targetName);
        if (onlinePlayer != null) {
            targetUuid = onlinePlayer.getUniqueId();
            resolvedName = onlinePlayer.getName();
        } else {
            // Check in stats cache
            if (plugin.getStatsCache() != null) {
                var stats = plugin.getStatsCache().getAllStats();
                for (var ps : stats) {
                    if (ps.getName() != null && ps.getName().equalsIgnoreCase(targetName)) {
                        targetUuid = ps.getUuid();
                        resolvedName = ps.getName();
                        break;
                    }
                }
            }
            // Check in Bukkit offline player cache
            if (targetUuid == null) {
                OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(targetName);
                if (op != null && (op.hasPlayedBefore() || op.isOnline() || op.getName() != null)) {
                    targetUuid = op.getUniqueId();
                    resolvedName = op.getName();
                }
            }
        }

        // If player found, open player homes GUI (which also displays their team home if they belong to a team)
        if (targetUuid != null) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
            listener.openHomeListGui(admin, target);
            return true;
        }

        // 3. If not a known player, check if input matches a BetterTeams team name
        BetterTeamsHook btHook = plugin.getBetterTeamsHook();
        if (btHook.isAvailable()) {
            Object team = btHook.getTeam(input);
            if (team != null) {
                listener.openTeamActionGui(admin, input);
                return true;
            }
        }

        // 4. Final non-blocking fallback check among server's known offline players
        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            if (offline != null && offline.getName() != null && offline.getName().equalsIgnoreCase(targetName)) {
                if (offline.hasPlayedBefore() || offline.isOnline()) {
                    listener.openHomeListGui(admin, offline);
                    return true;
                }
            }
        }

        TextUtil.sendMessage(admin, getPrefix() + "&c✖ Player or Team not found: &e" + targetName);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("havencore.admin.ahome")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            String token = args[0].toLowerCase();

            // 1. If user typed "team:...", filter only team completions
            BetterTeamsHook btHook = plugin.getBetterTeamsHook();
            if (token.startsWith("team:")) {
                if (btHook.isAvailable()) {
                    for (String tName : btHook.getAllTeamNames()) {
                        String candidate = "team:" + tName;
                        if (candidate.toLowerCase().startsWith(token)) {
                            completions.add(candidate);
                        }
                    }
                }
                return completions;
            }

            // 2. Add online player names
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(token)) {
                    completions.add(p.getName());
                }
            }

            // 3. Add cached offline player names
            if (plugin.getStatsCache() != null) {
                for (var ps : plugin.getStatsCache().getAllStats()) {
                    if (ps.getName() != null && ps.getName().toLowerCase().startsWith(token)) {
                        if (!completions.contains(ps.getName())) {
                            completions.add(ps.getName());
                        }
                    }
                }
            }

            // 4. Add BetterTeams team suggestions formatted as "team:<TeamName>"
            if (btHook.isAvailable()) {
                for (String tName : btHook.getAllTeamNames()) {
                    String candidate = "team:" + tName;
                    if (candidate.toLowerCase().startsWith(token) || tName.toLowerCase().startsWith(token)) {
                        if (!completions.contains(candidate)) {
                            completions.add(candidate);
                        }
                    }
                }
            }

            return completions;
        }

        return Collections.emptyList();
    }
}
