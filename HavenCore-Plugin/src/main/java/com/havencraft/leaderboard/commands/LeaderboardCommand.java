package com.havencraft.leaderboard.commands;

import com.havencraft.leaderboard.HavenLeaderboard;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class LeaderboardCommand implements CommandExecutor {

    private final HavenLeaderboard plugin;

    public LeaderboardCommand(HavenLeaderboard plugin) {
        this.plugin = plugin;
    }

    private static final String PERM_USE = "leaderboard.use";
    private static final String PERM_RELOAD = "leaderboard.reload";
    
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no-console"));
            return true;
        }

        Player player = (Player) sender;
        
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!player.hasPermission(PERM_RELOAD)) {
                player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                return true;
            }
            plugin.reload();
            player.sendMessage(plugin.getConfigManager().getMessage("reloaded"));
            return true;
        }

        // Open main menu
        if (player.hasPermission(PERM_USE)) {
            plugin.getGuiManager().openMainMenu(player);
        } else {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
        }

        return true;
    }
}
