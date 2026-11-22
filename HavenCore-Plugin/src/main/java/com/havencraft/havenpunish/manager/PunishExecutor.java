package com.havencraft.havenpunish.manager;

import com.havencraft.havenpunish.model.PunishSession;
import com.havencraft.havenpunish.gui.GuiUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Executes punishment commands via Bukkit.dispatchCommand().
 * Handles cooldown enforcement to prevent spam-clicking.
 *
 * Commands are dispatched as the CONSOLE to bypass any LiteBans permission 
 * errors ("You don't have permission!") caused by missing nodes or desyncs.
 * 
 * To ensure proper attribution in Discord and the LiteBans history, we use
 * LiteBans' built-in `--sender` and `--sender-uuid` command flags.
 */
public class PunishExecutor implements Listener {

    private final JavaPlugin plugin;
    private final SessionManager sessionManager;
    private final FileConfiguration messagesConfig;
    private final int cooldownSeconds;

    /** Tracks last punishment time per staff UUID to enforce cooldown. */
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public PunishExecutor(JavaPlugin plugin, SessionManager sessionManager, FileConfiguration messagesConfig, int cooldownSeconds) {
        this.plugin = plugin;
        this.sessionManager = sessionManager;
        this.messagesConfig = messagesConfig;
        this.cooldownSeconds = cooldownSeconds;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        cooldowns.remove(uuid);
        sessionManager.removeSession(uuid);
    }

    /**
     * Executes the punishment. Returns true if successful, false if on cooldown.
     */
    public boolean execute(Player staff, PunishSession session) {
        UUID staffUuid = staff.getUniqueId();

        String prefix = GuiUtils.c(messagesConfig.getString("prefix", "&#92bed8HavenPunish &8• &7"));
        String consolePrefix = messagesConfig.getString("console-prefix", "HavenPunish •");

        // Check cooldown
        if (isOnCooldown(staffUuid)) {
            long remaining = getRemainingCooldown(staffUuid);
            String cooldownMsg = GuiUtils.c(messagesConfig.getString("cooldown",
                    "&7⏱ Please wait &f{remaining}s &7before issuing another punishment.")
                    .replace("{remaining}", String.valueOf(remaining)));
            staff.sendMessage(prefix + cooldownMsg);
            return false;
        }

        // Build the command, passing the staff member to inject --sender flags
        String command = session.buildCommand(staff);
        
        plugin.getLogger().info("[" + consolePrefix + "] " + staff.getName() + " executed via GUI: /" + command);

        // Dispatch as CONSOLE
        // LiteBans will process the --sender flag and attribute it to the staff member!
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);

        // Set cooldown
        cooldowns.put(staffUuid, System.currentTimeMillis());

        return true;
    }

    /**
     * Checks if a staff member is currently on cooldown.
     */
    public boolean isOnCooldown(UUID staffUuid) {
        Long lastTime = cooldowns.get(staffUuid);
        if (lastTime == null) return false;
        return (System.currentTimeMillis() - lastTime) < (cooldownSeconds * 1000L);
    }

    /**
     * Returns remaining cooldown seconds.
     */
    public long getRemainingCooldown(UUID staffUuid) {
        Long lastTime = cooldowns.get(staffUuid);
        if (lastTime == null) return 0;
        long elapsed = System.currentTimeMillis() - lastTime;
        long remaining = (cooldownSeconds * 1000L) - elapsed;
        return Math.max(0, remaining / 1000);
    }
}
