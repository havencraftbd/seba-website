package com.haven.shards.util;

import com.haven.shards.HavenShards;
import com.haven.shards.manager.SoundManager;
import org.bukkit.entity.Player;

public class SoundUtil {

    public static void playSound(HavenShards plugin, Player player, String key) {
        if (player == null || !player.isOnline() || key == null || plugin == null) return;

        SoundManager sm = plugin.getSoundManager();
        if (sm == null) return;

        SoundManager.CachedSound cs = sm.getCachedSound(key);
        if (cs == null || !cs.enabled()) return;

        if (cs.bukkitSound() != null) {
            player.playSound(player.getLocation(), cs.bukkitSound(), cs.volume(), cs.pitch());
        } else if (cs.customSound() != null) {
            player.playSound(player.getLocation(), cs.customSound(), cs.volume(), cs.pitch());
        }
    }
}
