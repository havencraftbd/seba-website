package com.havencraft.havensettings;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;
import com.havencraft.havencore.util.TextUtil;

import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the /settings command — opens the settings GUI.
 * Supports /settings reload for admins.
 */
public class SettingsCommand implements CommandExecutor, TabCompleter {

    private final HavenSettings module;
    private final SettingsGUI settingsGUI;
    private final FileConfiguration messagesConfig;

    public SettingsCommand(HavenSettings module, SettingsGUI settingsGUI, FileConfiguration messagesConfig) {
        this.module = module;
        this.settingsGUI = settingsGUI;
        this.messagesConfig = messagesConfig;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        String pfx = TextUtil.colorize(messagesConfig.getString("prefix", ""));

        // /settings reload
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("settings.reload")) {
                sender.sendMessage(pfx + TextUtil.colorize(messagesConfig.getString("no-permission", "&cNo permission.")));
                return true;
            }
            module.reload();
            sender.sendMessage(pfx + TextUtil.colorize(messagesConfig.getString("reload-success", "&aHavenSettings reloaded successfully!")));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(pfx + TextUtil.colorize(messagesConfig.getString("no-console", "&cThis command is for players only.")));
            return true;
        }

        if (args.length > 0) {
            if (!player.hasPermission("settings.admin") && !player.isOp()) {
                player.sendMessage(pfx + TextUtil.colorize(messagesConfig.getString("no-permission", "&cNo permission.")));
                return true;
            }
            OfflinePlayer target = org.bukkit.Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                target = org.bukkit.Bukkit.getOfflinePlayer(args[0]);
            }
            if (target == null || (!target.isOnline() && !target.hasPlayedBefore())) {
                player.sendMessage(pfx + TextUtil.colorize(messagesConfig.getString("player-not-found", "&cPlayer not found.")));
                return true;
            }
            settingsGUI.openSettings(player, target);
            return true;
        }

        if (!player.hasPermission("settings.use")) {
            player.sendMessage(pfx + TextUtil.colorize(messagesConfig.getString("no-permission", "&cNo permission.")));
            return true;
        }

        settingsGUI.openSettings(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission("settings.reload") && "reload".startsWith(args[0].toLowerCase())) {
                completions.add("reload");
            }
            if (sender.hasPermission("settings.admin") || sender.isOp()) {
                for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                        completions.add(p.getName());
                    }
                }
            }
        }
        return completions;
    }
}
