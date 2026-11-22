package com.haven.shards.hook;

import com.haven.shards.HavenShards;
import com.haven.shards.manager.DatabaseManager;
import com.haven.shards.util.ItemUtil;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class PAPIExpansion extends PlaceholderExpansion {

    private final HavenShards plugin;

    private PlaceholderExpansion aliasExpansion;

    public PAPIExpansion(HavenShards plugin) {
        this.plugin = plugin;
    }

    public void registerAll() {
        register();

        // Register alias expansion for %shards_...%
        this.aliasExpansion = new PlaceholderExpansion() {
            @Override public @NotNull String getIdentifier() { return "shards"; }
            @Override public @NotNull String getAuthor() { return "HavenTeam"; }
            @Override public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }
            @Override public boolean persist() { return true; }
            @Override
            public String onRequest(OfflinePlayer player, @NotNull String params) {
                return parsePlaceholder(player, params);
            }
        };
        this.aliasExpansion.register();
    }

    public void unregisterAll() {
        try {
            unregister();
        } catch (Exception ignored) {}
        if (aliasExpansion != null) {
            try {
                aliasExpansion.unregister();
            } catch (Exception ignored) {}
            aliasExpansion = null;
        }
    }

    @Override
    public @NotNull String getIdentifier() {
        return "havenshards";
    }

    @Override
    public @NotNull String getAuthor() {
        return "HavenTeam";
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
        return parsePlaceholder(player, params);
    }

    private String parsePlaceholder(OfflinePlayer player, String params) {
        String paramLower = params.toLowerCase();

        switch (paramLower) {
            case "balance" -> {
                if (player == null) return "0.0";
                double bal = player.isOnline()
                        ? plugin.getShardManager().getBalance(player.getUniqueId())
                        : plugin.getDatabaseManager().getOfflineBalanceFast(player.getUniqueId());
                return String.valueOf(bal);
            }
            case "balance_formatted" -> {
                if (player == null) return "0";
                double bal = player.isOnline()
                        ? plugin.getShardManager().getBalance(player.getUniqueId())
                        : plugin.getDatabaseManager().getOfflineBalanceFast(player.getUniqueId());
                boolean compact = plugin.getConfig().getBoolean("currency.compact-formatted", true);
                return compact ? ItemUtil.formatCompact(bal) : ItemUtil.formatMoney(bal);
            }
            case "balance_compact" -> {
                if (player == null) return "0";
                double bal = player.isOnline()
                        ? plugin.getShardManager().getBalance(player.getUniqueId())
                        : plugin.getDatabaseManager().getOfflineBalanceFast(player.getUniqueId());
                return ItemUtil.formatCompact(bal);
            }
            case "balance_comma", "balance_full" -> {
                if (player == null) return "0";
                double bal = player.isOnline()
                        ? plugin.getShardManager().getBalance(player.getUniqueId())
                        : plugin.getDatabaseManager().getOfflineBalanceFast(player.getUniqueId());
                return ItemUtil.formatMoney(bal);
            }
            case "currency_name" -> {
                return plugin.getConfig().getString("currency.name", "Shards");
            }
            case "currency_symbol" -> {
                return plugin.getConfig().getString("currency.symbol", "✨");
            }
            case "booster", "booster_multiplier" -> {
                return ItemUtil.formatMoney(plugin.getBoosterManager().getMultiplier());
            }
            case "booster_active" -> {
                return String.valueOf(plugin.getBoosterManager().isBoosterActive());
            }
            case "booster_remaining", "booster_time_left" -> {
                return plugin.getBoosterManager().getFormattedRemainingTime();
            }
            case "rank_multiplier" -> {
                if (player == null || !player.isOnline()) return "1.0";
                return ItemUtil.formatMoney(plugin.getShardManager().getRankMultiplier(player.getPlayer()));
            }
            case "total_multiplier" -> {
                if (player == null || !player.isOnline()) return ItemUtil.formatMoney(plugin.getBoosterManager().getMultiplier());
                return ItemUtil.formatMoney(plugin.getShardManager().getTotalMultiplier(player.getPlayer()));
            }
            case "playtime_minutes" -> {
                if (player == null) return "0";
                return String.valueOf(plugin.getPlaytimeRewardManager().getPlaytimeMinutes(player.getUniqueId()));
            }
            case "playtime_remaining" -> {
                if (player == null) return "0";
                int intervalMins = plugin.getConfig().getInt("playtime-rewards.interval-minutes", 10);
                int currentMins = plugin.getPlaytimeRewardManager().getPlaytimeMinutes(player.getUniqueId());
                return String.valueOf(Math.max(0, intervalMins - currentMins));
            }
            case "playtime_progress_percent" -> {
                if (player == null) return "0";
                int intervalMins = plugin.getConfig().getInt("playtime-rewards.interval-minutes", 10);
                int currentMins = plugin.getPlaytimeRewardManager().getPlaytimeMinutes(player.getUniqueId());
                double percent = Math.min(100.0, ((double) currentMins / intervalMins) * 100.0);
                return String.valueOf((int) percent);
            }
            case "playtime_progress_bar" -> {
                if (player == null) return "□□□□□□□□□□";
                int intervalMins = plugin.getConfig().getInt("playtime-rewards.interval-minutes", 10);
                int currentMins = plugin.getPlaytimeRewardManager().getPlaytimeMinutes(player.getUniqueId());
                int totalBars = 10;
                int filledBars = (int) Math.round(((double) currentMins / intervalMins) * totalBars);
                filledBars = Math.min(totalBars, Math.max(0, filledBars));
                StringBuilder sb = new StringBuilder("&a");
                for (int i = 0; i < filledBars; i++) sb.append("■");
                sb.append("&7");
                for (int i = filledBars; i < totalBars; i++) sb.append("□");
                return com.haven.shards.util.ColorUtil.colorize(sb.toString());
            }
            case "daily_streak" -> {
                if (player == null) return "0";
                return String.valueOf(plugin.getDailyRewardManager().getStreak(player.getUniqueId()));
            }
            case "daily_can_claim" -> {
                if (player == null) return "false";
                return String.valueOf(plugin.getDailyRewardManager().canClaim(player.getUniqueId()));
            }
            case "daily_remaining" -> {
                if (player == null) return "0s";
                long remSec = plugin.getDailyRewardManager().getRemainingMillis(player.getUniqueId()) / 1000L;
                return com.haven.shards.util.TimeUtil.formatTime(remSec);
            }
        }

        // Leaderboard Top 10 Placeholders: top_name_X, top_balance_X, top_balance_formatted_X
        if (paramLower.startsWith("top_")) {
            String sub = paramLower.substring(4);
            List<DatabaseManager.TopEntry> topList = plugin.getDatabaseManager().getCachedTopEntries();

            if (sub.startsWith("name_")) {
                try {
                    int rank = Integer.parseInt(sub.substring(5));
                    if (rank >= 1 && rank <= topList.size()) {
                        return topList.get(rank - 1).getName();
                    }
                    return "---";
                } catch (NumberFormatException ignored) {}
            } else if (sub.startsWith("balance_compact_")) {
                try {
                    int rank = Integer.parseInt(sub.substring(16));
                    if (rank >= 1 && rank <= topList.size()) {
                        return ItemUtil.formatCompact(topList.get(rank - 1).getBalance());
                    }
                    return "0";
                } catch (NumberFormatException ignored) {}
            } else if (sub.startsWith("balance_comma_") || sub.startsWith("balance_full_")) {
                try {
                    int rank = Integer.parseInt(sub.substring(sub.indexOf('_', 8) + 1));
                    if (rank >= 1 && rank <= topList.size()) {
                        return ItemUtil.formatMoney(topList.get(rank - 1).getBalance());
                    }
                    return "0";
                } catch (NumberFormatException ignored) {}
            } else if (sub.startsWith("balance_formatted_")) {
                try {
                    int rank = Integer.parseInt(sub.substring(18));
                    if (rank >= 1 && rank <= topList.size()) {
                        boolean compact = plugin.getConfig().getBoolean("currency.compact-formatted", true);
                        double bal = topList.get(rank - 1).getBalance();
                        return compact ? ItemUtil.formatCompact(bal) : ItemUtil.formatMoney(bal);
                    }
                    return "0";
                } catch (NumberFormatException ignored) {}
            } else if (sub.startsWith("balance_")) {
                try {
                    int rank = Integer.parseInt(sub.substring(8));
                    if (rank >= 1 && rank <= topList.size()) {
                        return String.valueOf(topList.get(rank - 1).getBalance());
                    }
                    return "0.0";
                } catch (NumberFormatException ignored) {}
            }
        }

        return null;
    }
}

