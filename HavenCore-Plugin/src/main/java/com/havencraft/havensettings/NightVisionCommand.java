package com.havencraft.havensettings;

import com.havencraft.havencore.util.TextUtil;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class NightVisionCommand implements CommandExecutor {

    private static final String PERMISSION = "settings.nv";

    private final SettingsManager settingsManager;
    private final SettingsGUI settingsGUI;
    private final FileConfiguration messagesConfig;

    public NightVisionCommand(SettingsManager settingsManager, SettingsGUI settingsGUI, FileConfiguration messagesConfig) {
        this.settingsManager = settingsManager;
        this.settingsGUI = settingsGUI;
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

        boolean enabled = settingsManager.toggleNightVision(player.getUniqueId());
        if (enabled) {
            settingsGUI.applyNV(player);
            player.sendMessage(prefix + TextUtil.colorize(messagesConfig.getString("night-vision-applied", "&aNight vision enabled.")));
        } else {
            settingsGUI.removeNV(player);
            player.sendMessage(prefix + TextUtil.colorize(messagesConfig.getString("night-vision-removed", "&cNight vision disabled.")));
        }
        return true;
    }


}
