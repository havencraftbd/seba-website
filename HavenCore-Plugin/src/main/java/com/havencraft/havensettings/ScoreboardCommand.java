package com.havencraft.havensettings;

import com.havencraft.havencore.util.TextUtil;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * /sb command — Toggles the TAB scoreboard on/off for the player.
 * State is persisted via HavenSettings so it survives relogs.
 */
public class ScoreboardCommand implements CommandExecutor {

    private static final String PERMISSION = "settings.sb";

    private final SettingsManager settingsManager;
    private final FileConfiguration messagesConfig;

    public ScoreboardCommand(SettingsManager settingsManager, FileConfiguration messagesConfig) {
        this.settingsManager = settingsManager;
        this.messagesConfig = messagesConfig;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        String prefix = TextUtil.colorize(messagesConfig.getString("prefix", ""));

        if (!(sender instanceof Player player)) {
            sender.sendMessage(prefix + TextUtil.colorize(messagesConfig.getString("no-console", "&cThis command is for players only.")));
            return true;
        }

        if (!player.hasPermission(PERMISSION)) {
            player.sendMessage(prefix + TextUtil.colorize(messagesConfig.getString("no-permission", "&cNo permission.")));
            return true;
        }

        boolean enabled = settingsManager.toggleScoreboard(player.getUniqueId());
        player.sendMessage(prefix + TextUtil.colorize(messagesConfig.getString(
                enabled ? "scoreboard-shown" : "scoreboard-hidden",
                enabled ? "&aScoreboard shown." : "&cScoreboard hidden.")));
        return true;
    }


}
