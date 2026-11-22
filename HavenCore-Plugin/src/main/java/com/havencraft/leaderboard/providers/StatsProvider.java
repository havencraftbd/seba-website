package com.havencraft.leaderboard.providers;

import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;

public class StatsProvider {

    public static int getStatistic(OfflinePlayer player, Statistic stat) {
        try {
            if (player.isOnline() && player.getPlayer() != null) {
                return player.getPlayer().getStatistic(stat);
            }
        } catch (IllegalArgumentException | NoSuchMethodError e) {
            // Player hasn't played before or stat doesn't exist for them
        }
        return 0;
    }
}
