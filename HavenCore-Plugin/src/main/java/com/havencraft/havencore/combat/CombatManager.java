package com.havencraft.havencore.combat;

import com.havencraft.havencore.HavenCorePlugin;
import com.havencraft.havencore.util.ConfigLoader;
import com.havencraft.havencore.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages player combat status, timers, configurations, and ActionBar countdown displays.
 */
public class CombatManager {

    private final HavenCorePlugin plugin;
    private final Map<UUID, Long> combatMap = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> lastAttackerMap = new ConcurrentHashMap<>();
    private final Set<UUID> playersWithFlight = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingQuitKills = ConcurrentHashMap.newKeySet();
    private final Set<UUID> combatLoggedPlayers = ConcurrentHashMap.newKeySet();
    private BukkitTask task;

    // Config options
    private boolean enabled;
    private long durationSeconds;
    private List<String> enabledWorlds;
    private boolean killOnLogout;
    private boolean disableFlight;
    private boolean disableElytra;
    private boolean blockTeleportEvents;
    private List<String> blockedCommands;
    private boolean actionbarEnabled;
    private String actionbarFormat;

    // Messages
    private String prefix;
    private String msgTaggedAttacker;
    private String msgTaggedVictim;
    private String msgCommandBlocked;
    private String msgTeleportBlocked;
    private String msgCombatExpired;
    private String msgCombatLogged;

    public CombatManager(HavenCorePlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        FileConfiguration config = ConfigLoader.load(plugin, "combat.yml");

        this.enabled = config.getBoolean("enabled", true);
        this.durationSeconds = config.getLong("duration-seconds", 15);
        this.enabledWorlds = config.getStringList("enabled-worlds");
        this.killOnLogout = config.getBoolean("kill-on-logout", true);
        this.disableFlight = config.getBoolean("disable-flight", true);
        this.disableElytra = config.getBoolean("disable-elytra", true);
        this.blockTeleportEvents = config.getBoolean("block-teleport-events", true);

        List<String> rawCommands = config.getStringList("blocked-commands");
        this.blockedCommands = new ArrayList<>();
        for (String cmd : rawCommands) {
            this.blockedCommands.add(cmd.toLowerCase());
        }

        this.actionbarEnabled = config.getBoolean("display.actionbar-enabled", true);
        this.actionbarFormat = config.getString("display.actionbar-format", "&c⚔ In Combat: &e{time}s &c⚔");

        this.prefix = config.getString("messages.prefix", "&#92bed8HavenCore &8• &7");
        this.msgTaggedAttacker = config.getString("messages.tagged-attacker", "&7You attacked &e{victim}&7! You are now in combat for &c{time}s&7.");
        this.msgTaggedVictim = config.getString("messages.tagged-victim", "&7You were attacked by &e{attacker}&7! You are now in combat for &c{time}s&7.");
        this.msgCommandBlocked = config.getString("messages.command-blocked", "&cYou cannot use teleportation or escape commands while in combat!");
        this.msgTeleportBlocked = config.getString("messages.teleport-blocked", "&cTeleportation is disabled while you are in combat!");
        this.msgCombatExpired = config.getString("messages.combat-expired", "&aYou are no longer in combat.");
        this.msgCombatLogged = config.getString("messages.combat-logged", "&e{player} &ccombat logged and was killed!");
    }

    public void startTask() {
        stopTask();
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stopTask() {
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
        for (UUID uuid : playersWithFlight) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.setAllowFlight(true);
            }
        }
        playersWithFlight.clear();
        combatMap.clear();
        lastAttackerMap.clear();
        pendingQuitKills.clear();
    }

    private void tick() {
        if (!enabled || combatMap.isEmpty()) return;

        long now = System.currentTimeMillis();

        for (Map.Entry<UUID, Long> entry : combatMap.entrySet()) {
            UUID uuid = entry.getKey();
            long expireTime = entry.getValue();
            Player player = Bukkit.getPlayer(uuid);

            if (player == null || !player.isOnline()) {
                if (expireTime <= now) {
                    combatMap.remove(uuid);
                    lastAttackerMap.remove(uuid);
                    playersWithFlight.remove(uuid);
                }
                continue;
            }

            if (expireTime <= now) {
                combatMap.remove(uuid);
                lastAttackerMap.remove(uuid);
                restoreFlightIfApplicable(player);
                TextUtil.sendMessage(player, prefix + msgCombatExpired);
            } else if (actionbarEnabled && isWorldEnabled(player.getWorld().getName())) {
                long remainingSec = (expireTime - now + 999L) / 1000L;
                String formattedMsg = actionbarFormat.replace("{time}", String.valueOf(remainingSec));
                player.sendActionBar(TextUtil.parseLegacy(formattedMsg));
            }
        }
    }

    public boolean isWorldEnabled(String worldName) {
        if (enabledWorlds == null || enabledWorlds.isEmpty()) return true;
        for (String w : enabledWorlds) {
            if (w.equalsIgnoreCase(worldName)) {
                return true;
            }
        }
        return false;
    }

    public boolean isInCombat(Player player) {
        if (!enabled || player == null) return false;
        if (!isWorldEnabled(player.getWorld().getName())) return false;

        Long expireTime = combatMap.get(player.getUniqueId());
        if (expireTime == null) return false;

        if (expireTime <= System.currentTimeMillis()) {
            combatMap.remove(player.getUniqueId());
            lastAttackerMap.remove(player.getUniqueId());
            restoreFlightIfApplicable(player);
            return false;
        }
        return true;
    }

    public long getRemainingSeconds(Player player) {
        if (!isInCombat(player)) return 0;
        Long expireTime = combatMap.get(player.getUniqueId());
        if (expireTime == null) return 0;
        long diff = expireTime - System.currentTimeMillis();
        return diff > 0 ? (diff + 999L) / 1000L : 0;
    }

    public void tagPlayer(Player victim, Player attacker) {
        if (!enabled) return;

        long newExpire = System.currentTimeMillis() + (durationSeconds * 1000L);

        if (victim != null && isWorldEnabled(victim.getWorld().getName())) {
            if (!victim.hasPermission("havencore.combat.bypass")) {
                if (attacker != null && !attacker.equals(victim)) {
                    lastAttackerMap.put(victim.getUniqueId(), attacker.getUniqueId());
                }
                boolean wasInCombat = isInCombat(victim);
                combatMap.put(victim.getUniqueId(), newExpire);

                if (disableFlight && victim.getAllowFlight()) {
                    if (victim.getGameMode() != GameMode.CREATIVE && victim.getGameMode() != GameMode.SPECTATOR) {
                        playersWithFlight.add(victim.getUniqueId());
                    }
                    victim.setFlying(false);
                    victim.setAllowFlight(false);
                }
                if (disableElytra && victim.isGliding()) {
                    victim.setGliding(false);
                }

                if (!wasInCombat && attacker != null) {
                    String msg = msgTaggedVictim.replace("{attacker}", attacker.getName()).replace("{time}", String.valueOf(durationSeconds));
                    TextUtil.sendMessage(victim, prefix + msg);
                }
            }
        }

        if (attacker != null && isWorldEnabled(attacker.getWorld().getName())) {
            if (!attacker.hasPermission("havencore.combat.bypass")) {
                boolean wasInCombat = isInCombat(attacker);
                combatMap.put(attacker.getUniqueId(), newExpire);

                if (disableFlight && attacker.getAllowFlight()) {
                    if (attacker.getGameMode() != GameMode.CREATIVE && attacker.getGameMode() != GameMode.SPECTATOR) {
                        playersWithFlight.add(attacker.getUniqueId());
                    }
                    attacker.setFlying(false);
                    attacker.setAllowFlight(false);
                }
                if (disableElytra && attacker.isGliding()) {
                    attacker.setGliding(false);
                }

                if (!wasInCombat && victim != null) {
                    String msg = msgTaggedAttacker.replace("{victim}", victim.getName()).replace("{time}", String.valueOf(durationSeconds));
                    TextUtil.sendMessage(attacker, prefix + msg);
                }
            }
        }
    }

    public void removeTag(Player player) {
        if (player != null) {
            combatMap.remove(player.getUniqueId());
            lastAttackerMap.remove(player.getUniqueId());
            restoreFlightIfApplicable(player);
        }
    }

    private void restoreFlightIfApplicable(Player player) {
        if (player != null && player.isOnline() && playersWithFlight.remove(player.getUniqueId())) {
            player.setAllowFlight(true);
        }
    }

    public boolean isCommandBlocked(String commandLine) {
        if (commandLine == null || blockedCommands == null || blockedCommands.isEmpty()) {
            return false;
        }

        String cleaned = commandLine.trim().toLowerCase();
        if (!cleaned.startsWith("/")) {
            cleaned = "/" + cleaned;
        }

        String[] parts = cleaned.split("\\s+");
        String mainCmd = parts[0];

        // Strip namespace (e.g. /essentials:spawn -> /spawn)
        if (mainCmd.contains(":")) {
            mainCmd = "/" + mainCmd.substring(mainCmd.indexOf(":") + 1);
            if (parts.length > 1) {
                cleaned = mainCmd + cleaned.substring(parts[0].length());
            } else {
                cleaned = mainCmd;
            }
        }

        for (String blocked : blockedCommands) {
            String b = blocked.trim().toLowerCase();
            if (!b.startsWith("/")) {
                b = "/" + b;
            }

            // Exact match or sub-command match (e.g. "/team home" matching "/team home tp")
            if (cleaned.equals(b) || cleaned.startsWith(b + " ") || mainCmd.equals(b)) {
                return true;
            }
        }
        return false;
    }

    // Getters and Helper Methods
    public HavenCorePlugin getPlugin() {
        return plugin;
    }

    public Player getLastAttacker(Player player) {
        if (player == null) return null;
        UUID attackerUuid = lastAttackerMap.get(player.getUniqueId());
        if (attackerUuid == null) return null;
        Player attacker = Bukkit.getPlayer(attackerUuid);
        return (attacker != null && attacker.isOnline()) ? attacker : null;
    }

    public UUID getLastAttackerUuid(UUID victimUuid) {
        if (victimUuid == null) return null;
        return lastAttackerMap.get(victimUuid);
    }

    public boolean isPendingQuitKill(UUID uuid) {
        return uuid != null && pendingQuitKills.contains(uuid);
    }

    public void addPendingQuitKill(UUID uuid) {
        if (uuid != null) pendingQuitKills.add(uuid);
    }

    public void removePendingQuitKill(UUID uuid) {
        if (uuid != null) pendingQuitKills.remove(uuid);
    }

    public boolean isCombatLogged(UUID uuid) {
        return uuid != null && combatLoggedPlayers.contains(uuid);
    }

    public void markCombatLogged(UUID uuid) {
        if (uuid != null) combatLoggedPlayers.add(uuid);
    }

    public void removeCombatLogged(UUID uuid) {
        if (uuid != null) combatLoggedPlayers.remove(uuid);
    }

    public boolean isEnabled() { return enabled; }
    public boolean isKillOnLogout() { return killOnLogout; }
    public boolean isDisableElytra() { return disableElytra; }
    public boolean isBlockTeleportEvents() { return blockTeleportEvents; }
    public String getPrefix() { return prefix; }
    public String getMsgCommandBlocked() { return msgCommandBlocked; }
    public String getMsgTeleportBlocked() { return msgTeleportBlocked; }
    public String getMsgCombatLogged() { return msgCombatLogged; }
}
