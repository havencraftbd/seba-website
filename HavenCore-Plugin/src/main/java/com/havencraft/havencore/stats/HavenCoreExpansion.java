package com.havencraft.havencore.stats;

import com.havencraft.havencore.HavenCorePlugin;
import com.havencraft.leaderboard.data.PlayerStat;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class HavenCoreExpansion extends PlaceholderExpansion {

    private final HavenCorePlugin plugin;

    public HavenCoreExpansion(HavenCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "havencore";
    }

    @Override
    public @NotNull String getAuthor() {
        return "HavenCraft";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true; 
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";

        PlayerStats stats = plugin.getStatsCache().getStats(player.getUniqueId());
        if (stats == null) return "0";

        // Handle generic placeholders like %havencore_kills%, %havencore_kills_rank%
        String[] parts = params.split("_");
        if (parts.length == 0) return null;

        boolean isRank = params.endsWith("_rank");
        String statName = params;
        if (isRank) {
            statName = params.substring(0, params.length() - 5);
        }

        StatisticType type = null;
        switch (statName) {
            case "kills": type = StatisticType.PLAYER_KILLS; break;
            case "deaths": type = StatisticType.DEATHS; break;
            case "blocks_broken": type = StatisticType.BLOCKS_BROKEN; break;
            case "blocks_placed": type = StatisticType.BLOCKS_PLACED; break;
            case "mobs_killed": type = StatisticType.MOBS_KILLED; break;
            case "money": type = StatisticType.MONEY; break;
            case "playtime": type = StatisticType.PLAYTIME; break;
            case "shards": type = StatisticType.SHARDS; break;
            case "kdr":
                double kills = stats.getStat(StatisticType.PLAYER_KILLS);
                double deaths = stats.getStat(StatisticType.DEATHS);
                if (deaths == 0) return String.valueOf((long) kills);
                return String.format("%.2f", kills / deaths);
        }

        if (type != null) {
            if (isRank) {
                if (plugin.getLeaderboardModule() != null && plugin.getLeaderboardModule().getRankingManager() != null) {
                    int rank = plugin.getLeaderboardModule().getRankingManager().getPlayerRank(type, player.getUniqueId());
                    if (rank > 0) {
                        return String.valueOf(rank);
                    }
                }
                return "150+"; // Default fallback if unranked or out of cache
            } else {
                double val = stats.getStat(type);
                if (type == StatisticType.PLAYTIME) {
                    long totalSeconds = (long) val;
                    long days = totalSeconds / 86400;
                    long hours = (totalSeconds % 86400) / 3600;
                    long minutes = (totalSeconds % 3600) / 60;
                    if (days > 0) return days + "d " + hours + "h";
                    else if (hours > 0) return hours + "h " + minutes + "m";
                    else return minutes + "m";
                } else if (val == (long) val) {
                    return String.valueOf((long) val);
                } else {
                    return String.valueOf(val);
                }
            }
        }

        return null; // Let PAPI handle if unknown placeholder
    }
}
