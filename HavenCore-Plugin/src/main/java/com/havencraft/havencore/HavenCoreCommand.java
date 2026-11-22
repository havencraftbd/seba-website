package com.havencraft.havencore;

import com.havencraft.havencore.util.TextUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * /havencore reload — Reloads all HavenCore module configs.
 */
public class HavenCoreCommand implements CommandExecutor {

    private final HavenCorePlugin plugin;

    public HavenCoreCommand(HavenCorePlugin plugin) {
        this.plugin = plugin;
    }

    private String getPrefix() {
        return plugin.getGlobalPrefix();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String prefix = getPrefix();
        if (args.length == 0) {
            TextUtil.sendMessage(sender, prefix + "&6HavenCore &7v" + plugin.getDescription().getVersion());
            TextUtil.sendMessage(sender, prefix + "&eUsage: &f/havencore reload");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("havencore.reload")) {
                TextUtil.sendMessage(sender, prefix + "&cYou don't have permission to do this.");
                return true;
            }

            long start = System.currentTimeMillis();

            // Reload main config
            plugin.reloadConfig();

            // Reload all modules
            plugin.reloadAllModules();

            long elapsed = System.currentTimeMillis() - start;
            TextUtil.sendMessage(sender, prefix + "&a✔ &fHavenCore configs reloaded! &7(" + elapsed + "ms)");
            return true;
        }

        TextUtil.sendMessage(sender, prefix + "&cUnknown sub-command. Use: /havencore reload");
        return true;
    }
}
