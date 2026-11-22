package com.haven.shards.listener;

import com.haven.shards.HavenShards;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class QuestEventListener implements Listener {

    private final HavenShards plugin;
    private static final java.util.Set<org.bukkit.Material> CROP_MATERIALS = java.util.EnumSet.of(
            org.bukkit.Material.WHEAT,
            org.bukkit.Material.CARROTS,
            org.bukkit.Material.POTATOES,
            org.bukkit.Material.BEETROOTS,
            org.bukkit.Material.NETHER_WART,
            org.bukkit.Material.COCOA,
            org.bukkit.Material.SWEET_BERRY_BUSH,
            org.bukkit.Material.MELON,
            org.bukkit.Material.PUMPKIN,
            org.bukkit.Material.SUGAR_CANE
    );

    public QuestEventListener(HavenShards plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getQuestManager().loadPlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getQuestManager().unloadPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        java.util.UUID uuid = killer.getUniqueId();
        boolean hasKillPlayers = plugin.getQuestManager().hasActiveQuest(uuid, "KILL_PLAYERS");
        boolean hasKillMobs = plugin.getQuestManager().hasActiveQuest(uuid, "KILL_MOBS");

        if (!hasKillPlayers && !hasKillMobs) return;

        if (event.getEntity() instanceof Player victim) {
            if (hasKillPlayers && !victim.equals(killer)) {
                plugin.getQuestManager().incrementProgress(killer, "KILL_PLAYERS", 1, null);
            }
            return;
        }

        if (hasKillMobs && event.getEntity() instanceof Monster) {
            plugin.getQuestManager().incrementProgress(killer, "KILL_MOBS", 1, null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        java.util.UUID uuid = player.getUniqueId();

        boolean hasMining = plugin.getQuestManager().hasActiveQuest(uuid, "MINE_BLOCKS");
        boolean hasFarming = plugin.getQuestManager().hasActiveQuest(uuid, "HARVEST_CROPS");

        // Fast-path: If player has completed both mining and farming quests, return immediately!
        // ZERO block tracking checks, ZERO PDC chunk reads, ZERO allocations!
        if (!hasMining && !hasFarming) {
            return;
        }

        org.bukkit.Material mat = event.getBlock().getType();
        boolean isCrop = CROP_MATERIALS.contains(mat);

        if (isCrop) {
            if (!hasFarming) return; // Player already finished farming quest!
        } else {
            if (!hasMining) return; // Player already finished mining quest!
        }

        // Only check block tracker if the player actually has an active quest for this action
        if (com.haven.shards.util.BlockTracker.isTrackable(mat)
                && com.haven.shards.util.BlockTracker.isPlaced(plugin, event.getBlock())) {
            return; // Block was placed by a player, skip mining and farming quest progression!
        }

        String matName = mat.name();
        if (isCrop) {
            if (mat == org.bukkit.Material.MELON || mat == org.bukkit.Material.PUMPKIN || mat == org.bukkit.Material.SUGAR_CANE) {
                plugin.getQuestManager().incrementProgress(player, "HARVEST_CROPS", 1, matName);
            } else if (event.getBlock().getBlockData() instanceof Ageable ageable && ageable.getAge() == ageable.getMaximumAge()) {
                plugin.getQuestManager().incrementProgress(player, "HARVEST_CROPS", 1, matName);
            }
        } else {
            plugin.getQuestManager().incrementProgress(player, "MINE_BLOCKS", 1, matName);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            if (plugin.getQuestManager().hasActiveQuest(event.getPlayer().getUniqueId(), "CATCH_FISH")) {
                plugin.getQuestManager().incrementProgress(event.getPlayer(), "CATCH_FISH", 1, null);
            }
        }
    }
}
