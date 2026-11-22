package com.havencraft.havencore.stats;

import com.havencraft.havencore.HavenCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class StatsCommand implements CommandExecutor {

    private final HavenCorePlugin plugin;
    private final StatsGUI statsGUI;

    public StatsCommand(HavenCorePlugin plugin, StatsGUI statsGUI) {
        this.plugin = plugin;
        this.statsGUI = statsGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            statsGUI.openGUI(player, player);
            return true;
        }

        if (args.length == 1) {
            if (!player.hasPermission("havencore.stats.others")) {
                player.sendMessage(ChatColor.RED + "You don't have permission to view other players' stats.");
                return true;
            }

            String targetName = args[0];
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

            if (!target.hasPlayedBefore() && !target.isOnline()) {
                player.sendMessage(ChatColor.RED + "Player not found or has never played before.");
                return true;
            }

            statsGUI.openGUI(player, target);
            return true;
        }

        player.sendMessage(ChatColor.RED + "Usage: /stats [player]");
        return true;
    }
}
