package com.havencraft.havencore.security;

import com.havencraft.havencore.HavenCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Safe, zero-overhead reflection hook for BetterTeams API.
 * Provides comprehensive team management, team home lookup, and tab-completion support
 * without requiring BetterTeams as a hard compile-time dependency.
 */
public class BetterTeamsHook {

    private final HavenCorePlugin havenPlugin;
    private final boolean available;
    private Method getTeamByOfflinePlayerMethod;
    private Method getTeamByNameMethod;
    private Method getTeamByStringMethod;
    private Method getTeamByTeamUuidMethod;
    private Method getTeamNameMethod;
    private Method getTeamIdMethod;
    private Method getTeamHomeMethod;
    private Method setTeamHomeMethod;
    private Method delTeamHomeMethod;
    private Method getTeamManagerMethod;

    private final Set<String> cachedTeamNames = new ConcurrentSkipListSet<>(String.CASE_INSENSITIVE_ORDER);
    private final AtomicBoolean isScanningDisk = new AtomicBoolean(false);
    private volatile long lastDiskScanTime = 0L;
    private static final long DISK_SCAN_COOLDOWN_MS = 60_000L;

    public BetterTeamsHook() {
        this(null);
    }

    public BetterTeamsHook(HavenCorePlugin havenPlugin) {
        this.havenPlugin = havenPlugin != null ? havenPlugin : (HavenCorePlugin) Bukkit.getPluginManager().getPlugin("HavenCore");
        Plugin plugin = Bukkit.getPluginManager().getPlugin("BetterTeams");
        boolean loaded = false;

        if (plugin != null && plugin.isEnabled()) {
            try {
                Class<?> teamClass = Class.forName("com.booksaw.betterTeams.Team");

                // In BetterTeams, Team.getTeam(OfflinePlayer) is the method to lookup team by player
                try {
                    getTeamByOfflinePlayerMethod = teamClass.getMethod("getTeam", OfflinePlayer.class);
                } catch (NoSuchMethodException ignored) {
                    getTeamByOfflinePlayerMethod = null;
                }

                // In BetterTeams, Team.getTeamByName(String) is the method to lookup strictly by team name
                try {
                    getTeamByNameMethod = teamClass.getMethod("getTeamByName", String.class);
                } catch (NoSuchMethodException ignored) {
                    getTeamByNameMethod = null;
                }

                // Fallback: Team.getTeam(String)
                try {
                    getTeamByStringMethod = teamClass.getMethod("getTeam", String.class);
                } catch (NoSuchMethodException ignored) {
                    getTeamByStringMethod = null;
                }

                // In BetterTeams, Team.getTeam(UUID) looks up by TEAM UUID (NOT player UUID)
                try {
                    getTeamByTeamUuidMethod = teamClass.getMethod("getTeam", UUID.class);
                } catch (NoSuchMethodException ignored) {
                    getTeamByTeamUuidMethod = null;
                }

                // In BetterTeams, Team.getName()
                try {
                    getTeamNameMethod = teamClass.getMethod("getName");
                } catch (NoSuchMethodException ignored) {
                    getTeamNameMethod = null;
                }

                // In BetterTeams, Team.getID() returns UUID
                try {
                    getTeamIdMethod = teamClass.getMethod("getID");
                } catch (NoSuchMethodException ignored) {
                    try {
                        getTeamIdMethod = teamClass.getMethod("getId");
                    } catch (NoSuchMethodException ignored2) {
                        for (Method m : teamClass.getMethods()) {
                            if (UUID.class.isAssignableFrom(m.getReturnType()) && m.getParameterCount() == 0) {
                                String mName = m.getName().toLowerCase(Locale.ROOT);
                                if (mName.contains("id") || mName.contains("uuid")) {
                                    getTeamIdMethod = m;
                                    break;
                                }
                            }
                        }
                    }
                }

                // In BetterTeams, Team.getTeamManager()
                try {
                    getTeamManagerMethod = teamClass.getMethod("getTeamManager");
                } catch (NoSuchMethodException ignored) {
                    getTeamManagerMethod = null;
                }

                // In BetterTeams, Team.getTeamHome()
                try {
                    getTeamHomeMethod = teamClass.getMethod("getTeamHome");
                } catch (NoSuchMethodException ignored) {
                    // Dynamically scan for getTeamHome / getHome / location
                    for (Method m : teamClass.getMethods()) {
                        if (Location.class.isAssignableFrom(m.getReturnType()) && m.getParameterCount() == 0) {
                            String name = m.getName().toLowerCase(Locale.ROOT);
                            if (name.contains("home") || name.contains("location")) {
                                getTeamHomeMethod = m;
                                break;
                            }
                        }
                    }
                }

                // In BetterTeams, Team.setTeamHome(Location)
                try {
                    setTeamHomeMethod = teamClass.getMethod("setTeamHome", Location.class);
                } catch (NoSuchMethodException ignored) {
                    for (Method m : teamClass.getMethods()) {
                        if (m.getParameterCount() == 1 && Location.class.isAssignableFrom(m.getParameterTypes()[0])) {
                            String name = m.getName().toLowerCase(Locale.ROOT);
                            if (name.contains("set") && (name.contains("home") || name.contains("location"))) {
                                setTeamHomeMethod = m;
                                break;
                            }
                        }
                    }
                }

                // In BetterTeams, Team.deleteTeamHome() / delTeamHome()
                try {
                    delTeamHomeMethod = teamClass.getMethod("deleteTeamHome");
                } catch (NoSuchMethodException ignored) {
                    try {
                        delTeamHomeMethod = teamClass.getMethod("delTeamHome");
                    } catch (NoSuchMethodException ignored2) {
                        for (Method m : teamClass.getMethods()) {
                            if (m.getParameterCount() == 0) {
                                String name = m.getName().toLowerCase(Locale.ROOT);
                                if ((name.contains("del") || name.contains("remove") || name.contains("delete")) && name.contains("home")) {
                                    delTeamHomeMethod = m;
                                    break;
                                }
                            }
                        }
                    }
                }

                loaded = (getTeamByOfflinePlayerMethod != null || getTeamByNameMethod != null || getTeamByStringMethod != null) && getTeamNameMethod != null;
            } catch (Throwable ignored) {
                loaded = false;
            }
        }

        this.available = loaded;

        if (this.available) {
            triggerAsyncDiskScan();
        }
    }

    /**
     * Checks if BetterTeams is installed and hooked successfully.
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * Retrieves the Team object for a Player.
     */
    public Object getTeam(Player player) {
        if (!available || player == null) return null;
        try {
            if (getTeamByOfflinePlayerMethod != null) {
                Object team = getTeamByOfflinePlayerMethod.invoke(null, (OfflinePlayer) player);
                cacheTeam(team);
                return team;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Retrieves the Team object for an OfflinePlayer.
     */
    public Object getTeam(OfflinePlayer offlinePlayer) {
        if (!available || offlinePlayer == null) return null;
        try {
            if (getTeamByOfflinePlayerMethod != null) {
                Object team = getTeamByOfflinePlayerMethod.invoke(null, offlinePlayer);
                cacheTeam(team);
                return team;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Retrieves the Team object for a Player UUID.
     * NOTE: In HavenCore, UUIDs passed to this method are player UUIDs.
     */
    public Object getTeam(UUID playerUuid) {
        if (!available || playerUuid == null) return null;
        try {
            OfflinePlayer op = Bukkit.getOfflinePlayer(playerUuid);
            return getTeam(op);
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Retrieves the Team object directly by Team UUID.
     */
    public Object getTeamByTeamUuid(UUID teamUuid) {
        if (!available || teamUuid == null) return null;
        try {
            if (getTeamByTeamUuidMethod != null) {
                Object team = getTeamByTeamUuidMethod.invoke(null, teamUuid);
                cacheTeam(team);
                return team;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Retrieves the Team object by exact or formatted team name.
     */
    public Object getTeam(String teamName) {
        if (!available || teamName == null || teamName.trim().isEmpty()) return null;
        String cleanName = teamName.trim();
        if (cleanName.toLowerCase(Locale.ROOT).startsWith("team:")) {
            cleanName = cleanName.substring(5).trim();
        }
        if (cleanName.isEmpty()) return null;

        try {
            if (getTeamByNameMethod != null) {
                Object team = getTeamByNameMethod.invoke(null, cleanName);
                if (team != null) {
                    cacheTeam(team);
                    return team;
                }
            }
            if (getTeamByStringMethod != null) {
                Object team = getTeamByStringMethod.invoke(null, cleanName);
                if (team != null) {
                    cacheTeam(team);
                    return team;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Retrieves the persistent unique ID (UUID) of a Team.
     */
    public UUID getTeamUuid(Object team) {
        if (team == null || getTeamIdMethod == null) return null;
        try {
            Object res = getTeamIdMethod.invoke(team);
            if (res instanceof UUID uuid) {
                return uuid;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Retrieves the persistent unique ID (UUID) of a player's team.
     */
    public UUID getTeamUuid(Player player) {
        Object team = getTeam(player);
        return getTeamUuid(team);
    }

    /**
     * Retrieves the player's current team name, or null if not in any team.
     */
    public String getTeamName(Player player) {
        Object team = getTeam(player);
        return getTeamName(team);
    }

    /**
     * Retrieves the offline player's team name, or null if not in any team.
     */
    public String getTeamName(OfflinePlayer player) {
        Object team = getTeam(player);
        return getTeamName(team);
    }

    /**
     * Retrieves the team name from a Team object.
     */
    public String getTeamName(Object team) {
        if (team == null || getTeamNameMethod == null) return null;
        try {
            Object nameObj = getTeamNameMethod.invoke(team);
            if (nameObj != null) {
                String name = nameObj.toString();
                if (!name.isEmpty()) {
                    cachedTeamNames.add(name);
                }
                return name;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Checks if the player is currently a member of any team.
     */
    public boolean isInTeam(Player player) {
        return getTeamName(player) != null;
    }

    /**
     * Retrieves the team home Location from a Team object.
     */
    public Location getTeamHome(Object team) {
        if (team == null) return null;
        if (getTeamHomeMethod != null) {
            try {
                Object locObj = getTeamHomeMethod.invoke(team);
                if (locObj instanceof Location loc) {
                    return loc;
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    /**
     * Retrieves the team home Location for an OfflinePlayer.
     */
    public Location getTeamHome(OfflinePlayer player) {
        Object team = getTeam(player);
        return getTeamHome(team);
    }

    /**
     * Retrieves the team home Location by Team Name.
     */
    public Location getTeamHome(String teamName) {
        Object team = getTeam(teamName);
        return getTeamHome(team);
    }

    /**
     * Updates the team's home location to a new location.
     */
    public boolean setTeamHome(Object team, Location location) {
        if (team == null || location == null) return false;
        try {
            if (setTeamHomeMethod != null) {
                setTeamHomeMethod.invoke(team, location);
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * Deletes / unsets the team's home location.
     */
    public boolean delTeamHome(Object team) {
        if (team == null) return false;
        try {
            if (delTeamHomeMethod != null) {
                delTeamHomeMethod.invoke(team);
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * Safely checks if player is a team owner/leader without hard dependencies.
     */
    public boolean isTeamLeader(Player player) {
        if (!available || player == null) return false;
        try {
            Object team = getTeam(player);
            if (team == null) return false;

            // Check if player has leader/owner status reflectively
            for (Method m : team.getClass().getMethods()) {
                if (m.getParameterCount() == 1 && Player.class.isAssignableFrom(m.getParameterTypes()[0])) {
                    String name = m.getName().toLowerCase(Locale.ROOT);
                    if (name.contains("leader") || name.contains("owner") || name.contains("admin")) {
                        try {
                            Object res = m.invoke(team, player);
                            if (res instanceof Boolean b && b) return true;
                        } catch (Throwable ignored) {}
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * Retrieves all online members of a team.
     */
    public List<Player> getOnlineTeamMembers(Object team) {
        List<Player> members = new ArrayList<>();
        if (team == null) return members;

        String targetTeamName = getTeamName(team);
        if (targetTeamName == null) return members;

        for (Player p : Bukkit.getOnlinePlayers()) {
            String pTeamName = getTeamName(p);
            if (targetTeamName.equalsIgnoreCase(pTeamName)) {
                members.add(p);
            }
        }
        return members;
    }

    /**
     * Alias for getOnlineTeamMembers for backward compatibility.
     */
    public List<Player> getOnlineMembers(Object team) {
        return getOnlineTeamMembers(team);
    }

    /**
     * Retrieves a list of all known team names for auto-completion.
     * Pure in-memory and non-blocking — ZERO disk I/O on the main server thread.
     */
    public List<String> getAllTeamNames() {
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        names.addAll(cachedTeamNames);

        // Collect from online players
        for (Player p : Bukkit.getOnlinePlayers()) {
            String tName = getTeamName(p);
            if (tName != null && !tName.isEmpty()) {
                names.add(tName);
                cachedTeamNames.add(tName);
            }
        }

        // Try reflectively querying TeamManager if available
        if (getTeamManagerMethod != null) {
            try {
                Object manager = getTeamManagerMethod.invoke(null);
                if (manager != null) {
                    try {
                        Method cloneMethod = manager.getClass().getMethod("getLoadedTeamListClone");
                        Object res = cloneMethod.invoke(manager);
                        if (res instanceof Map<?, ?> map) {
                            for (Object val : map.values()) {
                                String n = getTeamName(val);
                                if (n != null && !n.isEmpty()) {
                                    names.add(n);
                                    cachedTeamNames.add(n);
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        }

        // Trigger background disk scan if cooldown period has elapsed
        if (System.currentTimeMillis() - lastDiskScanTime > DISK_SCAN_COOLDOWN_MS) {
            triggerAsyncDiskScan();
        }

        return new ArrayList<>(names);
    }

    /**
     * Asynchronously scans BetterTeams data folders in the background to discover offline teams.
     * Guaranteed never to run on or block the main server thread.
     */
    public void triggerAsyncDiskScan() {
        if (havenPlugin == null || !havenPlugin.isEnabled()) return;
        if (!isScanningDisk.compareAndSet(false, true)) return;

        Bukkit.getScheduler().runTaskAsynchronously(havenPlugin, () -> {
            try {
                Plugin btPlugin = Bukkit.getPluginManager().getPlugin("BetterTeams");
                if (btPlugin != null && btPlugin.isEnabled()) {
                    File dataFolder = btPlugin.getDataFolder();
                    Set<String> discovered = new HashSet<>();

                    // 1. Check teamInfo directory (SeparatedYamlStorage)
                    File teamInfoFolder = new File(dataFolder, "teamInfo");
                    if (teamInfoFolder.exists() && teamInfoFolder.isDirectory()) {
                        File[] files = teamInfoFolder.listFiles((dir, name) -> name.endsWith(".yml"));
                        if (files != null) {
                            for (File f : files) {
                                try {
                                    YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
                                    String realName = cfg.getString("name");
                                    if (realName != null && !realName.isEmpty()) {
                                        discovered.add(realName);
                                    }
                                } catch (Throwable ignored) {}
                            }
                        }
                    }

                    // 2. Check teams directory
                    File teamsFolder = new File(dataFolder, "teams");
                    if (teamsFolder.exists() && teamsFolder.isDirectory()) {
                        File[] files = teamsFolder.listFiles((dir, name) -> name.endsWith(".yml"));
                        if (files != null) {
                            for (File f : files) {
                                try {
                                    YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
                                    String realName = cfg.getString("name");
                                    if (realName != null && !realName.isEmpty()) {
                                        discovered.add(realName);
                                    }
                                } catch (Throwable ignored) {}
                            }
                        }
                    }

                    // 3. Check teams.yml / data.yml
                    for (String fileName : new String[]{"teams.yml", "data.yml"}) {
                        File f = new File(dataFolder, fileName);
                        if (f.exists()) {
                            try {
                                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
                                for (String key : cfg.getKeys(false)) {
                                    String realName = cfg.getString(key + ".name");
                                    if (realName != null && !realName.isEmpty()) {
                                        discovered.add(realName);
                                    }
                                }
                            } catch (Throwable ignored) {}
                        }
                    }

                    cachedTeamNames.addAll(discovered);
                    lastDiskScanTime = System.currentTimeMillis();
                }
            } catch (Throwable ignored) {
            } finally {
                isScanningDisk.set(false);
            }
        });
    }

    private void cacheTeam(Object team) {
        if (team != null) {
            String name = getTeamName(team);
            if (name != null && !name.isEmpty()) {
                cachedTeamNames.add(name);
            }
        }
    }
}
