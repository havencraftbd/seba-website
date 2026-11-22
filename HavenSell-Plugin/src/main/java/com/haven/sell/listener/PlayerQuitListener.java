package com.haven.sell.listener;

import com.haven.sell.HavenSell;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {

    private final HavenSell plugin;

    public PlayerQuitListener(HavenSell plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getDataManager().unloadPlayerData(event.getPlayer().getUniqueId());
    }
}
