package com.haven.sell.listener;

import com.haven.sell.HavenSell;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final HavenSell plugin;

    public PlayerJoinListener(HavenSell plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getDataManager().loadPlayerData(player.getUniqueId(), player.getName());
        });
    }
}
