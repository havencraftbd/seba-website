package com.havencraft.havencore.security;

import com.havencraft.havencore.HavenCorePlugin;
import com.havencraft.havencore.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance, zero-overhead OP Security Manager.
 * Prevents account hijacking and unauthorized OP delegation on cracked Minecraft servers.
 */
public class OpSecurityManager {

    private final HavenCorePlugin plugin;
    
    private boolean enabled;
    private boolean deopOnQuit;
    private boolean deopOnJoin;
    private boolean restrictOpCommand;
    private boolean restrictDeopCommand;
    private boolean blockConsoleOp;
    private boolean restrictConsoleOpTargets;
    private final Set<String> allowedOpIssuers = new HashSet<>();
    private boolean useOpPassword;
    private String opPassword;
    private boolean logEvents;

    private final Map<UUID, Integer> failedAttempts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lockoutUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastAttemptTime = new ConcurrentHashMap<>();

    public static final int MAX_FAILED_ATTEMPTS = 3;
    public static final long LOCKOUT_DURATION_MS = 5 * 60 * 1000L; // 5 minutes
    public static final long ATTEMPT_COOLDOWN_MS = 2000L; // 2 seconds

    private String prefix;
    private String deopOnJoinNotice;
    private String deopOnQuitLog;
    private String deopOnJoinLog;
    private String opCommandBlocked;
    private String deopCommandBlocked;
    private String opCommandBlockedLog;
    private String consoleOpBlockedLog;
    private String consoleOpUnauthorizedTargetLog;
    private String opauthDisabled;
    private String opauthWrongPassword;
    private String opauthSuccess;
    private String opauthSuccessLog;
    private String opauthUsage;
    private String opauthLockedOut;
    private String opauthRateLimited;

    public OpSecurityManager(HavenCorePlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        FileConfiguration config = plugin.getConfig();
        this.enabled = config.getBoolean("op-security.enabled", true);
        this.deopOnQuit = config.getBoolean("op-security.deop-on-quit", true);
        this.deopOnJoin = config.getBoolean("op-security.deop-on-join", true);
        this.restrictOpCommand = config.getBoolean("op-security.restrict-op-command", true);
        this.restrictDeopCommand = config.getBoolean("op-security.restrict-deop-command", false);
        this.blockConsoleOp = config.getBoolean("op-security.block-console-op", false);
        this.restrictConsoleOpTargets = config.getBoolean("op-security.restrict-console-op-targets", true);
        
        this.allowedOpIssuers.clear();
        if (config.isList("op-security.allowed-op-issuers")) {
            List<String> allowedList = config.getStringList("op-security.allowed-op-issuers");
            for (String name : allowedList) {
                if (name != null && !name.trim().isEmpty()) {
                    this.allowedOpIssuers.add(name.trim().toLowerCase(Locale.ROOT));
                }
            }
        } else if (config.isString("op-security.allowed-op-issuers")) {
            String singleName = config.getString("op-security.allowed-op-issuers");
            if (singleName != null && !singleName.trim().isEmpty()) {
                this.allowedOpIssuers.add(singleName.trim().toLowerCase(Locale.ROOT));
            }
        }

        this.useOpPassword = config.getBoolean("op-security.use-op-password", true);
        this.opPassword = config.getString("op-security.op-password", "ChangeThisSecretPassword123!");
        this.logEvents = config.getBoolean("op-security.log-events", true);

        if (this.enabled && this.useOpPassword && "ChangeThisSecretPassword123!".equals(this.opPassword)) {
            plugin.getLogger().warning("[OP-Security] WARNING: 'op-password' is set to default in config.yml! Please change it for security!");
        }

        // Messages
        this.prefix = plugin.getGlobalPrefix();
        this.deopOnJoinNotice = config.getString("op-security.messages.deop-on-join-notice", "&cYour OP status was automatically removed for security on join.");
        this.deopOnQuitLog = config.getString("op-security.messages.deop-on-quit-log", "&cPlayer &e{player} &cwas de-OPed upon leaving the server.");
        this.deopOnJoinLog = config.getString("op-security.messages.deop-on-join-log", "&cPlayer &e{player} &cwas de-OPed upon joining the server.");
        this.opCommandBlocked = config.getString("op-security.messages.op-command-blocked", "&cYou are not authorized to use the /op command.");
        this.deopCommandBlocked = config.getString("op-security.messages.deop-command-blocked", "&cYou are not authorized to use the /deop command.");
        this.opCommandBlockedLog = config.getString("op-security.messages.op-command-blocked-log", "&cPlayer &e{player} &ctried to execute: &f{command}");
        this.consoleOpBlockedLog = config.getString("op-security.messages.console-op-blocked-log", "&cThe /op command is blocked.");
        this.consoleOpUnauthorizedTargetLog = config.getString("op-security.messages.console-op-unauthorized-target-log", "&cCannot grant OP to &e{player}&c! Access denied.");
        this.opauthDisabled = config.getString("op-security.messages.opauth-disabled", "&cThe /opauth command system is currently disabled.");
        this.opauthWrongPassword = config.getString("op-security.messages.opauth-wrong-password", "&cIncorrect security password.");
        this.opauthSuccess = config.getString("op-security.messages.opauth-success", "&aSecurity authentication successful! You have been granted OP status.");
        this.opauthSuccessLog = config.getString("op-security.messages.opauth-success-log", "&aPlayer &e{player} &cauthenticated using /opauth and gained OP.");
        this.opauthUsage = config.getString("op-security.messages.opauth-usage", "&cUsage: /opauth <password>");
        this.opauthLockedOut = config.getString("op-security.messages.opauth-locked-out", "&cToo many failed attempts! You are locked out for {seconds} seconds.");
        this.opauthRateLimited = config.getString("op-security.messages.opauth-rate-limited", "&cPlease wait a moment before trying again.");
    }

    /**
     * Safely De-OPs a player in-game and completely purges all matching entries
     * from Bukkit's operator list (ops.json) by both Name (case-insensitive) and UUID.
     * Guaranteed thread-safe by executing setOp synchronously on the primary server thread.
     */
    public void deopPlayer(Player player) {
        if (player == null) return;

        Runnable deopTask = () -> {
            if (player.isOp()) {
                player.setOp(false);
            }

            try {
                Set<OfflinePlayer> opsToRemove = new HashSet<>();
                for (OfflinePlayer op : Bukkit.getOperators()) {
                    if (op == null) continue;
                    boolean matchName = op.getName() != null && op.getName().equalsIgnoreCase(player.getName());
                    boolean matchUuid = op.getUniqueId() != null && op.getUniqueId().equals(player.getUniqueId());
                    if (matchName || matchUuid) {
                        opsToRemove.add(op);
                    }
                }
                for (OfflinePlayer op : opsToRemove) {
                    if (op.isOp()) {
                        op.setOp(false);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[OP-Security] Exception while purging ops.json for " + player.getName() + ": " + e.getMessage());
            }
        };

        if (Bukkit.isPrimaryThread()) {
            deopTask.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, deopTask);
        }
    }

    public boolean isLockedOut(UUID uuid) {
        if (uuid == null) return false;
        Long until = lockoutUntil.get(uuid);
        if (until == null) return false;
        if (System.currentTimeMillis() < until) {
            return true;
        }
        lockoutUntil.remove(uuid);
        failedAttempts.remove(uuid);
        return false;
    }

    public long getRemainingLockoutSeconds(UUID uuid) {
        if (uuid == null) return 0L;
        Long until = lockoutUntil.get(uuid);
        if (until == null) return 0L;
        long remaining = (until - System.currentTimeMillis()) / 1000L;
        return Math.max(0L, remaining);
    }

    public boolean isRateLimited(UUID uuid) {
        if (uuid == null) return false;
        Long last = lastAttemptTime.get(uuid);
        long now = System.currentTimeMillis();
        lastAttemptTime.put(uuid, now);
        return last != null && (now - last < ATTEMPT_COOLDOWN_MS);
    }

    public int recordFailedAttempt(UUID uuid) {
        if (uuid == null) return 0;
        int attempts = failedAttempts.compute(uuid, (k, v) -> (v == null) ? 1 : v + 1);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            lockoutUntil.put(uuid, System.currentTimeMillis() + LOCKOUT_DURATION_MS);
        }
        return attempts;
    }

    public void resetFailedAttempts(UUID uuid) {
        if (uuid == null) return;
        failedAttempts.remove(uuid);
        lockoutUntil.remove(uuid);
    }

    /**
     * Constant-time password verification supporting plain-text and SHA-256 hashes.
     */
    public boolean verifyOpPassword(String inputPassword) {
        if (inputPassword == null || this.opPassword == null) return false;

        String expected = this.opPassword.trim();
        if (expected.startsWith("sha256:")) {
            String hashTarget = expected.substring(7).trim();
            String hashedInput = sha256Hex(inputPassword);
            return MessageDigest.isEqual(hashTarget.getBytes(StandardCharsets.UTF_8), hashedInput.getBytes(StandardCharsets.UTF_8));
        }

        if (expected.length() == 64 && expected.matches("^[a-fA-F0-9]{64}$")) {
            String hashedInput = sha256Hex(inputPassword);
            return MessageDigest.isEqual(expected.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8), hashedInput.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
        }

        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), inputPassword.getBytes(StandardCharsets.UTF_8));
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isDeopOnQuit() {
        return deopOnQuit;
    }

    public boolean isDeopOnJoin() {
        return deopOnJoin;
    }

    public boolean isRestrictOpCommand() {
        return restrictOpCommand;
    }

    public boolean isRestrictDeopCommand() {
        return restrictDeopCommand;
    }

    public boolean isBlockConsoleOp() {
        return blockConsoleOp;
    }

    public boolean isRestrictConsoleOpTargets() {
        return restrictConsoleOpTargets;
    }

    public boolean isAllowedOpIssuer(String playerName) {
        if (playerName == null) return false;
        return allowedOpIssuers.contains(playerName.toLowerCase());
    }

    public boolean isUseOpPassword() {
        return useOpPassword;
    }

    public String getOpPassword() {
        return opPassword;
    }

    public boolean isLogEvents() {
        return logEvents;
    }

    public void sendPrefixedMessage(CommandSender sender, String message) {
        if (sender == null || message == null || message.isEmpty()) return;
        TextUtil.sendMessage(sender, prefix + message);
    }

    public String getDeopOnJoinNotice() {
        return deopOnJoinNotice;
    }

    public String getDeopOnQuitLog() {
        return deopOnQuitLog;
    }

    public String getDeopOnJoinLog() {
        return deopOnJoinLog;
    }

    public String getOpCommandBlocked() {
        return opCommandBlocked;
    }

    public String getDeopCommandBlocked() {
        return deopCommandBlocked;
    }

    public String getOpCommandBlockedLog() {
        return opCommandBlockedLog;
    }

    public String getConsoleOpBlockedLog() {
        return consoleOpBlockedLog;
    }

    public String getConsoleOpUnauthorizedTargetLog() {
        return consoleOpUnauthorizedTargetLog;
    }

    public String getOpauthDisabled() {
        return opauthDisabled;
    }

    public String getOpauthWrongPassword() {
        return opauthWrongPassword;
    }

    public String getOpauthSuccess() {
        return opauthSuccess;
    }

    public String getOpauthSuccessLog() {
        return opauthSuccessLog;
    }

    public String getOpauthUsage() {
        return opauthUsage;
    }

    public String getOpauthLockedOut() {
        return opauthLockedOut;
    }

    public String getOpauthRateLimited() {
        return opauthRateLimited;
    }

    public void logSecurity(String message) {
        if (logEvents && message != null && !message.isEmpty()) {
            sendPrefixedMessage(Bukkit.getConsoleSender(), message);
        }
    }
}
