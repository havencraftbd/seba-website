package com.havencraft.havensettings;


import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

import com.havencraft.havencore.util.TextUtil;

/**
 * Blocks commands when player has the corresponding setting disabled.
 * Also handles public chat filtering based on player settings.
 * Uses PlayerCommandPreprocessEvent at HIGHEST priority to catch before execution.
 */
public class CommandListener implements Listener {

    private final JavaPlugin plugin;
    private final SettingsManager settingsManager;
    private final FileConfiguration messagesConfig;
    private final FileConfiguration customCmdConfig;

    // Lowercase command → setting type
    private static final Map<String, String> BUILT_IN_COMMANDS = new HashMap<>();
    static {
        // Message commands
        BUILT_IN_COMMANDS.put("/msg", "msg");
        BUILT_IN_COMMANDS.put("/tell", "msg");
        BUILT_IN_COMMANDS.put("/whisper", "msg");
        BUILT_IN_COMMANDS.put("/w", "msg");
        BUILT_IN_COMMANDS.put("/m", "msg");
        BUILT_IN_COMMANDS.put("/t", "msg");
        BUILT_IN_COMMANDS.put("/message", "msg");
        BUILT_IN_COMMANDS.put("/pm", "msg");
        BUILT_IN_COMMANDS.put("/emsg", "msg");
        BUILT_IN_COMMANDS.put("/etell", "msg");
        BUILT_IN_COMMANDS.put("/ewhisper", "msg");
        BUILT_IN_COMMANDS.put("/ew", "msg");
        BUILT_IN_COMMANDS.put("/em", "msg");
        BUILT_IN_COMMANDS.put("/et", "msg");
        BUILT_IN_COMMANDS.put("/emessage", "msg");
        BUILT_IN_COMMANDS.put("/epm", "msg");
        
        // Reply commands
        BUILT_IN_COMMANDS.put("/r", "msg");
        BUILT_IN_COMMANDS.put("/reply", "msg");
        BUILT_IN_COMMANDS.put("/er", "msg");
        BUILT_IN_COMMANDS.put("/ereply", "msg");

        // TPA commands
        BUILT_IN_COMMANDS.put("/tpa", "tpa");
        BUILT_IN_COMMANDS.put("/etpa", "tpa");
        BUILT_IN_COMMANDS.put("/tpask", "tpa");
        BUILT_IN_COMMANDS.put("/etpask", "tpa");
        BUILT_IN_COMMANDS.put("/call", "tpa");
        BUILT_IN_COMMANDS.put("/ecall", "tpa");
        BUILT_IN_COMMANDS.put("/tpahere", "tpahere");
        BUILT_IN_COMMANDS.put("/etpahere", "tpahere");

        // Direct Teleport commands (to support blocking /tp when tp toggle is off)
        BUILT_IN_COMMANDS.put("/tp", "tpa");
        BUILT_IN_COMMANDS.put("/etp", "tpa");
        BUILT_IN_COMMANDS.put("/teleport", "tpa");
        BUILT_IN_COMMANDS.put("/eteleport", "tpa");
        BUILT_IN_COMMANDS.put("/tpo", "tpa");
        BUILT_IN_COMMANDS.put("/etpo", "tpa");
        BUILT_IN_COMMANDS.put("/tphere", "tpahere");
        BUILT_IN_COMMANDS.put("/etphere", "tpahere");
        BUILT_IN_COMMANDS.put("/tpohere", "tpahere");
        BUILT_IN_COMMANDS.put("/etpohere", "tpahere");
    }

    // Lowercase command → custom command ID
    private final Map<String, String> customCommandMap = new HashMap<>();
    private final Map<String, String> customCommandPermissions = new HashMap<>();
    private boolean customCommandsEnabled = false;

    public CommandListener(JavaPlugin plugin,
                           SettingsManager settingsManager,
                           FileConfiguration messagesConfig,
                           FileConfiguration customCmdConfig) {
        this.plugin = plugin;
        this.settingsManager = settingsManager;
        this.messagesConfig = messagesConfig;
        this.customCmdConfig = customCmdConfig;
        loadCustomCommands();
    }

    private void loadCustomCommands() {
        customCommandMap.clear();
        customCommandPermissions.clear();
        customCommandsEnabled = customCmdConfig.getBoolean("enabled", false);
        if (!customCommandsEnabled) return;

        ConfigurationSection section = customCmdConfig.getConfigurationSection("custom-commands");
        if (section == null) return;

        for (String id : section.getKeys(false)) {
            String permission = section.getString(id + ".permission", "").trim();
            if (permission.isEmpty()) {
                continue;
            }
            customCommandPermissions.put(id, permission);
            List<String> commands = section.getStringList(id + ".commands");
            for (String cmd : commands) {
                String normalized = cmd.toLowerCase().trim();
                if (!normalized.startsWith("/")) normalized = "/" + normalized;
                customCommandMap.put(normalized, id);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage().trim();

        // Split arguments by any amount of whitespace (prevents multiple space bypass)
        String[] args = message.split("\\s+");
        if (args.length == 0) return;

        String baseCommand = args[0].toLowerCase();

        // Strip plugin namespace prefix if present (e.g. /essentials:msg -> /msg) to prevent bypasses
        if (baseCommand.contains(":")) {
            int colonIndex = baseCommand.indexOf(":");
            baseCommand = "/" + baseCommand.substring(colonIndex + 1);
        }

        // Intercept toggle commands
        if ((baseCommand.equals("/tpa") || baseCommand.equals("/tpask") || baseCommand.equals("/call") || baseCommand.equals("/etpa") || baseCommand.equals("/etpask") || baseCommand.equals("/ecall")) && args.length > 1 && args[1].equalsIgnoreCase("toggle")) {
            event.setCancelled(true);
            UUID uuid = player.getUniqueId();
            boolean now = settingsManager.toggleTpa(uuid);
            String prefix = TextUtil.colorize(messagesConfig.getString("prefix", ""));
            player.sendMessage(prefix + TextUtil.colorize(messagesConfig.getString(now ? "tpa-enabled" : "tpa-disabled",
                    now ? "&aTPA requests enabled." : "&cTPA requests disabled.")));
            return;
        }

        if ((baseCommand.equals("/tpahere") || baseCommand.equals("/tpah") || baseCommand.equals("/etpahere")) && args.length > 1 && args[1].equalsIgnoreCase("toggle")) {
            event.setCancelled(true);
            UUID uuid = player.getUniqueId();
            boolean now = settingsManager.toggleTpaHere(uuid);
            String prefix = TextUtil.colorize(messagesConfig.getString("prefix", ""));
            player.sendMessage(prefix + TextUtil.colorize(messagesConfig.getString(now ? "tpahere-enabled" : "tpahere-disabled",
                    now ? "&aTPAHere requests enabled." : "&cTPAHere requests disabled.")));
            return;
        }

        // Check built-in commands
        String settingType = BUILT_IN_COMMANDS.get(baseCommand);
        if (settingType != null) {
            UUID uuid = player.getUniqueId();
            boolean senderAllowed = switch (settingType) {
                case "msg" -> settingsManager.isMsgEnabled(uuid);
                case "tpa" -> settingsManager.isTpaEnabled(uuid);
                case "tpahere" -> settingsManager.isTpaHereEnabled(uuid);
                default -> true;
            };

            // Sender Block check: If sender disabled their own setting, block them
            if (!senderAllowed && !player.hasPermission("settings.bypass") && !player.hasPermission("havensettings.bypass")) {
                event.setCancelled(true);
                String msgKey = settingType.equals("msg") ? "msg-blocked" : (settingType.equals("tpahere") ? "tpahere-blocked" : "tpa-blocked");
                String prefix = TextUtil.colorize(messagesConfig.getString("prefix", ""));
                player.sendMessage(prefix + TextUtil.colorize(messagesConfig.getString(msgKey, "&cCommand blocked.")));
                return;
            }

            // Target Block check: Find target and check their setting
            Player target = null;
            if (baseCommand.equals("/r") || baseCommand.equals("/reply") || baseCommand.equals("/er") || baseCommand.equals("/ereply")) {
                com.earth2me.essentials.User essUser = settingsManager.getEssentialsUser(uuid);
                if (essUser != null) {
                    com.earth2me.essentials.messaging.IMessageRecipient replyRecipient = essUser.getReplyRecipient();
                    if (replyRecipient instanceof com.earth2me.essentials.User) {
                        target = ((com.earth2me.essentials.User) replyRecipient).getBase();
                    }
                }
            } else {
                if (args.length > 1) {
                    // For /tp player1 player2, player2 is the target destination
                    if ((baseCommand.equals("/tp") || baseCommand.equals("/etp") || 
                         baseCommand.equals("/teleport") || baseCommand.equals("/eteleport")) && args.length > 2) {
                        target = org.bukkit.Bukkit.getPlayer(args[2]);
                    }
                    if (target == null) {
                        target = org.bukkit.Bukkit.getPlayer(args[1]);
                    }
                }
            }

            if (target != null) {
                boolean targetAllowed = switch (settingType) {
                    case "msg" -> settingsManager.isMsgEnabled(target.getUniqueId());
                    case "tpa" -> settingsManager.isTpaEnabled(target.getUniqueId());
                    case "tpahere" -> settingsManager.isTpaHereEnabled(target.getUniqueId());
                    default -> true;
                };

                if (!targetAllowed) {
                    // Target has it disabled. Block it, UNLESS sender is OP/has bypass.
                    if (!player.hasPermission("settings.bypass") && !player.hasPermission("havensettings.bypass")) {
                        event.setCancelled(true);
                        String prefix = TextUtil.colorize(messagesConfig.getString("prefix", ""));
                        String msgKey = settingType.equals("tpahere") ? "target-tpahere-blocked" : "target-blocked";
                        player.sendMessage(prefix + TextUtil.colorize(messagesConfig.getString(msgKey, "&cThat player has this disabled.")));
                        return;
                    }
                }
            }
        }

        // Check custom commands
        String customId = customCommandsEnabled ? customCommandMap.get(baseCommand) : null;
        if (customId != null) {
            String requiredPermission = customCommandPermissions.get(customId);
            if (requiredPermission == null || requiredPermission.isEmpty() || !player.hasPermission(requiredPermission)) {
                return;
            }
            if (!settingsManager.isCustomCommandEnabled(player.getUniqueId(), customId) && !player.hasPermission("settings.bypass")) {
                event.setCancelled(true);
                String prefix = TextUtil.colorize(messagesConfig.getString("prefix", ""));
                player.sendMessage(prefix + TextUtil.colorize(messagesConfig.getString("custom-blocked", "&cCommand disabled.")));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!settingsManager.isPublicChatEnabled(player.getUniqueId())) {
            event.setCancelled(true);
            String pfx = TextUtil.colorize(messagesConfig.getString("prefix", ""));
            player.sendMessage(pfx + TextUtil.colorize(messagesConfig.getString("public-chat-disabled-cannot-talk", 
                    "&cYou cannot send messages while public chat is disabled in /settings.")));
            return;
        }
        event.viewers().removeIf(audience -> {
            if (audience instanceof Player recipient) {
                return !settingsManager.isPublicChatEnabled(recipient.getUniqueId())
                        && !recipient.getUniqueId().equals(event.getPlayer().getUniqueId());
            }
            return false;
        });
    }

}
