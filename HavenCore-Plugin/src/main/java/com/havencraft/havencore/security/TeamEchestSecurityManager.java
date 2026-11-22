package com.havencraft.havencore.security;

import com.havencraft.havencore.HavenCorePlugin;
import com.havencraft.havencore.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * High-Performance, Zero-Overhead Team EnderChest Anti-Dupe & Security Manager.
 * 
 * Features:
 * - Single-Viewer Lock: Enforces strict single-viewer access to shared virtual team chests,
 *   completely eliminating race-condition and concurrent shift-click item duplication exploits.
 * - Multi-Viewer Click Interceptor: Real-time fail-safe in InventoryClick and InventoryDrag events.
 * - Open Flood / Macro Cooldown: Prevents rapid packet spamming / automated macro exploits.
 * - Auto-Close on Action: Closes GUI on teleport, combat damage, world change, kick, leave, and quit.
 * - Real-time Team Membership Validation: Intercepts clicks if player is no longer in the team.
 * - Automated Punishment Dispatcher: Executes console commands (tempban/ban/kick) when violation occurs.
 */
public class TeamEchestSecurityManager {

    private final HavenCorePlugin plugin;
    private BetterTeamsHook betterTeamsHook;

    private boolean enabled;
    private boolean singleViewerLock;
    private boolean clickProtection;
    private boolean cooldownEnabled;
    private long cooldownMillis;
    private boolean closeOnTeleport;
    private boolean closeOnDamage;
    private boolean closeOnWorldChange;
    private boolean logEvents;

    // Team Kick & Membership Validation
    private boolean closeOnTeamKick;
    private boolean closeOnTeamLeave;
    private boolean closeOnTeamDisband;
    private boolean validateTeamMembership;

    // Punishment settings
    private boolean punishmentEnabled;
    private List<String> punishmentCommands = new ArrayList<>();
    private boolean notifyStaff;
    private String staffPermission;

    private List<String> matchedKeywords = new ArrayList<>();

    // Messages
    private String prefix;
    private String alreadyInUseMessage;
    private String cooldownMessage;
    private String conflictDetectedMessage;
    private String closedOnCombatMessage;
    private String notInTeamMessage;
    private String punishReason;
    private String staffAlertMessage;

    // Cooldown tracking: Player UUID -> Last open epoch millis
    private final Map<UUID, Long> openCooldownMap = new ConcurrentHashMap<>();

    // Open chest tracking: Player UUID -> Team name at open time
    private final Map<UUID, String> openChestTeamMap = new ConcurrentHashMap<>();

    // Open chest tracking: Player UUID -> Team UUID at open time
    private final Map<UUID, UUID> openChestTeamUuidMap = new ConcurrentHashMap<>();

    // Prevent duplicate punishment executions for rapid clicks
    private final Set<UUID> processingPunish = ConcurrentHashMap.newKeySet();

    public TeamEchestSecurityManager(HavenCorePlugin plugin) {
        this.plugin = plugin;
        this.betterTeamsHook = plugin.getBetterTeamsHook();
        reload();
    }

    /**
     * Reloads configuration settings from config.yml.
     */
    public void reload() {
        this.betterTeamsHook = plugin.getBetterTeamsHook();
        FileConfiguration config = plugin.getConfig();

        this.enabled = config.getBoolean("team-echest-security.enabled", true);
        this.singleViewerLock = config.getBoolean("team-echest-security.single-viewer-lock", true);
        this.clickProtection = config.getBoolean("team-echest-security.click-protection", true);
        this.cooldownEnabled = config.getBoolean("team-echest-security.cooldown.enabled", true);
        this.cooldownMillis = config.getLong("team-echest-security.cooldown.cooldown-ms", 750L);
        this.closeOnTeleport = config.getBoolean("team-echest-security.close-on-teleport", true);
        this.closeOnDamage = config.getBoolean("team-echest-security.close-on-damage", true);
        this.closeOnWorldChange = config.getBoolean("team-echest-security.close-on-world-change", true);
        this.logEvents = config.getBoolean("team-echest-security.log-events", true);

        // Kick / Leave / Membership validation
        this.closeOnTeamKick = config.getBoolean("team-echest-security.close-on-team-kick", true);
        this.closeOnTeamLeave = config.getBoolean("team-echest-security.close-on-team-leave", true);
        this.closeOnTeamDisband = config.getBoolean("team-echest-security.close-on-team-disband", true);
        this.validateTeamMembership = config.getBoolean("team-echest-security.validate-team-membership", true);

        // Punishment settings
        this.punishmentEnabled = config.getBoolean("team-echest-security.punishment.enabled", true);
        this.punishmentCommands = config.getStringList("team-echest-security.punishment.commands");
        if (this.punishmentCommands.isEmpty()) {
            this.punishmentCommands = Collections.singletonList("tempban %player% 3d %reason%");
        }
        this.notifyStaff = config.getBoolean("team-echest-security.punishment.notify-staff", true);
        this.staffPermission = config.getString("team-echest-security.punishment.staff-permission", "havencore.security.notify");

        List<String> rawKeywords = config.getStringList("team-echest-security.matched-titles");
        if (rawKeywords.isEmpty()) {
            rawKeywords = Arrays.asList("team enderchest", "team chest", "team echest", "team's chest", "team's enderchest", "[team]");
        }
        this.matchedKeywords = new ArrayList<>();
        for (String k : rawKeywords) {
            String stripped = stripColorsAndFormatting(k);
            // Sanitize: do not allow generic "enderchest" or "ender chest" to prevent false positives on personal vanilla echests
            if (!stripped.isEmpty() && !stripped.equals("enderchest") && !stripped.equals("ender chest") && !stripped.equals("ender_chest")) {
                this.matchedKeywords.add(stripped);
            }
        }

        // Load Messages
        this.prefix = plugin.getGlobalPrefix();
        this.alreadyInUseMessage = config.getString(
                "team-echest-security.messages.already-in-use",
                "&cTeam member &e{player} &cis currently viewing the team chest! Please wait until they finish."
        );
        this.cooldownMessage = config.getString(
                "team-echest-security.messages.cooldown",
                "&cPlease wait a moment before opening the team chest again."
        );
        this.conflictDetectedMessage = config.getString(
                "team-echest-security.messages.conflict-detected",
                "&c[Security] Multiple viewers detected on shared chest. Access suspended for safety."
        );
        this.closedOnCombatMessage = config.getString(
                "team-echest-security.messages.closed-on-combat",
                "&cYour team chest was closed due to taking damage."
        );
        this.notInTeamMessage = config.getString(
                "team-echest-security.messages.not-in-team",
                "&c[Security] You are no longer in this team! Access to team chest is blocked."
        );
        this.punishReason = config.getString(
                "team-echest-security.messages.punish-reason",
                "Attempting to steal/dupe items from Team EnderChest after being kicked."
        );
        this.staffAlertMessage = config.getString(
                "team-echest-security.messages.staff-alert",
                "&c&l[SECURITY ALERT] &e{player} &7tried to take items from team chest after being kicked! Auto-punished."
        );

        // Clean cache
        this.openCooldownMap.clear();
        this.openChestTeamMap.clear();
        this.openChestTeamUuidMap.clear();
        this.processingPunish.clear();

        if (logEvents && betterTeamsHook.isAvailable()) {
            plugin.getLogger().info("[TeamEchest-AntiDupe] BetterTeams hook successfully connected.");
        }
    }

    /**
     * Checks if the given inventory view represents a Team EnderChest / Shared chest.
     * Guaranteed to exclude personal vanilla/Essentials player enderchests.
     */
    public boolean isTeamEchest(InventoryView view) {
        if (view == null) return false;

        Inventory topInv = view.getTopInventory();
        if (topInv == null) return false;

        // 1. HARD EXCLUSION: Player personal ender chest inventory type
        if (topInv.getType() == org.bukkit.event.inventory.InventoryType.ENDER_CHEST) {
            return false;
        }

        // 2. HARD EXCLUSION: If holder is a Player, it's a personal player inventory/echest
        if (topInv.getHolder() instanceof Player) {
            return false;
        }

        // 3. HARD EXCLUSION: If holder belongs to a custom EnderChest plugin (VariableEnderChests, BetterEnderChests, etc.)
        if (topInv.getHolder() != null) {
            String holderClass = topInv.getHolder().getClass().getName().toLowerCase();
            if (holderClass.contains("enderchest") || holderClass.contains("variable") || holderClass.contains("saif") || holderClass.contains("brianknight")) {
                return false;
            }
        }

        // 4. HARD EXCLUSION: Check against player's own personal Ender Chest
        if (view.getPlayer() instanceof Player p) {
            if (topInv.equals(p.getEnderChest())) {
                return false;
            }
        }

        String title = view.getTitle();
        if (title == null || title.isEmpty()) return false;

        String strippedTitle = stripColorsAndFormatting(title);

        // 5. HARD EXCLUSION: Standard vanilla, Essentials, or VariableEnderChests titles
        if (strippedTitle.equals("ender chest") || strippedTitle.equals("enderchest") || strippedTitle.equals("ender_chest")
                || strippedTitle.endsWith("'s ender chest") || strippedTitle.endsWith("'s enderchest")
                || strippedTitle.contains("variableenderchest") || strippedTitle.contains("betterenderchest")) {
            return false;
        }

        // 6. Must match team chest keywords
        for (String keyword : matchedKeywords) {
            if (strippedTitle.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Strips legacy and hex color codes and formatting for reliable matching.
     */
    private String stripColorsAndFormatting(String input) {
        if (input == null) return "";
        return input.replaceAll("(?i)[&§][0-9a-fk-orx]", "")
                .replaceAll("&#[A-Fa-f0-9]{6}", "")
                .replaceAll("<[^>]*>", "")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    /**
     * Checks if another player is currently viewing this inventory.
     * Returns the conflicting viewer if found, or null if clear.
     */
    public HumanEntity getConflictingViewer(Inventory inventory, Player currentPlayer) {
        if (inventory == null || currentPlayer == null) return null;
        List<HumanEntity> viewers = inventory.getViewers();
        if (viewers == null || viewers.isEmpty()) return null;

        for (HumanEntity viewer : viewers) {
            if (viewer != null && !viewer.getUniqueId().equals(currentPlayer.getUniqueId())) {
                return viewer;
            }
        }
        return null;
    }

    /**
     * Checks and updates open cooldown for a player.
     * Returns true if player is on cooldown, false if allowed.
     */
    public boolean checkCooldown(Player player) {
        if (!cooldownEnabled || cooldownMillis <= 0 || player == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        Long lastOpen = openCooldownMap.get(player.getUniqueId());

        if (lastOpen != null && (now - lastOpen) < cooldownMillis) {
            return true;
        }

        openCooldownMap.put(player.getUniqueId(), now);
        return false;
    }

    /**
     * Tracks the team name and team UUID when a player opens a team echest.
     */
    public void recordOpenTeam(UUID uuid, Player player) {
        if (uuid == null || player == null) return;
        Object team = betterTeamsHook.getTeam(player);
        if (team != null) {
            String teamName = betterTeamsHook.getTeamName(team);
            if (teamName != null) {
                openChestTeamMap.put(uuid, teamName);
            }
            UUID teamUuid = betterTeamsHook.getTeamUuid(team);
            if (teamUuid != null) {
                openChestTeamUuidMap.put(uuid, teamUuid);
            }
        }
    }

    public String getOpenedTeam(UUID uuid) {
        return uuid != null ? openChestTeamMap.get(uuid) : null;
    }

    public UUID getOpenedTeamUuid(UUID uuid) {
        return uuid != null ? openChestTeamUuidMap.get(uuid) : null;
    }

    public void removeOpenTeam(UUID uuid) {
        if (uuid != null) {
            openChestTeamMap.remove(uuid);
            openChestTeamUuidMap.remove(uuid);
        }
    }

    /**
     * Handles unauthorized access (e.g. clicking/taking items after being kicked).
     * Cancels action, closes inventory, executes punishment commands, and alerts staff.
     */
    public void handleUnauthorizedAccess(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();

        // Prevent duplicate spam executions
        if (!processingPunish.add(uuid)) {
            player.closeInventory();
            return;
        }

        // Run sync on Bukkit main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                // 1. Force Close GUI
                player.closeInventory();

                // 2. Send warning message
                sendPrefixedMessage(player, notInTeamMessage);

                // 3. Log to Console
                logSecurity("UNAUTHORIZED ACCESS: Player '" + player.getName()
                        + "' attempted to take items from a Team EChest after being kicked/without team! Executing punishment...");

                // 4. Execute Punishment Commands
                if (punishmentEnabled && punishmentCommands != null && !punishmentCommands.isEmpty()) {
                    for (String rawCmd : punishmentCommands) {
                        if (rawCmd == null || rawCmd.trim().isEmpty()) continue;
                        String formattedCmd = rawCmd
                                .replace("%player%", player.getName())
                                .replace("%reason%", punishReason)
                                .replace("{player}", player.getName())
                                .replace("{reason}", punishReason);

                        if (formattedCmd.startsWith("/")) {
                            formattedCmd = formattedCmd.substring(1);
                        }

                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), formattedCmd);
                    }
                }

                // 5. Alert Staff
                if (notifyStaff && staffAlertMessage != null && !staffAlertMessage.isEmpty()) {
                    String alert = prefix + staffAlertMessage.replace("{player}", player.getName());
                    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                        if (onlinePlayer.hasPermission(staffPermission) || onlinePlayer.isOp()) {
                            TextUtil.sendMessage(onlinePlayer, alert);
                        }
                    }
                }
            } finally {
                // Clean cooldown after 5 seconds
                Bukkit.getScheduler().runTaskLater(plugin, () -> processingPunish.remove(uuid), 100L);
            }
        });
    }

    /**
     * Cleans up player memory on quit/kick.
     */
    public void cleanupPlayer(UUID uuid) {
        if (uuid != null) {
            openCooldownMap.remove(uuid);
            openChestTeamMap.remove(uuid);
            openChestTeamUuidMap.remove(uuid);
            processingPunish.remove(uuid);
        }
    }

    /**
     * Sends a formatted prefixed message to the player.
     */
    public void sendPrefixedMessage(CommandSender sender, String message) {
        if (sender != null && message != null && !message.isEmpty()) {
            TextUtil.sendMessage(sender, prefix + message);
        }
    }

    /**
     * Logs anti-dupe security alerts to server console.
     */
    public void logSecurity(String message) {
        if (logEvents && message != null && !message.isEmpty()) {
            plugin.getLogger().log(Level.WARNING, "[TeamEchest-AntiDupe] " + message);
        }
    }

    // Getters
    public boolean isEnabled() { return enabled; }
    public boolean isSingleViewerLock() { return singleViewerLock; }
    public boolean isClickProtection() { return clickProtection; }
    public boolean isCloseOnTeleport() { return closeOnTeleport; }
    public boolean isCloseOnDamage() { return closeOnDamage; }
    public boolean isCloseOnWorldChange() { return closeOnWorldChange; }
    public boolean isCloseOnTeamKick() { return closeOnTeamKick; }
    public boolean isCloseOnTeamLeave() { return closeOnTeamLeave; }
    public boolean isCloseOnTeamDisband() { return closeOnTeamDisband; }
    public boolean isValidateTeamMembership() { return validateTeamMembership; }
    public boolean isPunishmentEnabled() { return punishmentEnabled; }
    public boolean isLogEvents() { return logEvents; }
    public BetterTeamsHook getBetterTeamsHook() { return betterTeamsHook; }
    public HavenCorePlugin getPlugin() { return plugin; }

    public String getAlreadyInUseMessage() { return alreadyInUseMessage; }
    public String getCooldownMessage() { return cooldownMessage; }
    public String getConflictDetectedMessage() { return conflictDetectedMessage; }
    public String getClosedOnCombatMessage() { return closedOnCombatMessage; }
    public String getNotInTeamMessage() { return notInTeamMessage; }
    public String getPunishReason() { return punishReason; }
    public String getStaffAlertMessage() { return staffAlertMessage; }
}
