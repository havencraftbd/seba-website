package com.havencraft.havencore.stats;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listens for block breaks, kills, etc., to increment stats in real time.
 */
public class StatsListener implements Listener {

    private final CentralStatsCache cache;

    public StatsListener(CentralStatsCache cache) {
        this.cache = cache;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        cache.handlePlayerJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        cache.handlePlayerQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(org.bukkit.event.block.BlockBreakEvent event) {
        cache.incrementStat(event.getPlayer().getUniqueId(), event.getPlayer().getName(), StatisticType.BLOCKS_BROKEN, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(org.bukkit.event.block.BlockPlaceEvent event) {
        cache.incrementStat(event.getPlayer().getUniqueId(), event.getPlayer().getName(), StatisticType.BLOCKS_PLACED, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(org.bukkit.event.entity.EntityDeathEvent event) {
        if (event.getEntity().getKiller() != null && !(event.getEntity() instanceof org.bukkit.entity.Player)) {
            cache.incrementStat(event.getEntity().getKiller().getUniqueId(), event.getEntity().getKiller().getName(), StatisticType.MOBS_KILLED, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        org.bukkit.entity.Player victim = event.getEntity();
        cache.incrementStat(victim.getUniqueId(), victim.getName(), StatisticType.DEATHS, 1);

        org.bukkit.entity.Player killer = victim.getKiller();
        if (killer != null && killer != victim) {
            cache.incrementStat(killer.getUniqueId(), killer.getName(), StatisticType.PLAYER_KILLS, 1);
        }
    }
}
