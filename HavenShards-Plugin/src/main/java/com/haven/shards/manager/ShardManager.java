package com.haven.shards.manager;

import com.haven.shards.HavenShards;
import com.haven.shards.api.event.HavenShardsReloadEvent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class ShardManager implements Listener {

    private final HavenShards plugin;
    private final DatabaseManager databaseManager;
    private final Map<UUID, Double> balanceCache = new ConcurrentHashMap<>();
    private final AtomicReference<Set<UUID>> dirtyBalances = new AtomicReference<>(ConcurrentHashMap.newKeySet());
    private final Set<UUID> loadingPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, AtomicInteger> pendingOperations = new ConcurrentHashMap<>();
    private final Set<UUID> pendingUnloadPlayers = ConcurrentHashMap.newKeySet();
    private BukkitTask autoSaveTask;

    public record RankGroup(String permission, double multiplier) {}
    private volatile List<RankGroup> cachedRankGroups = Collections.emptyList();
    private volatile boolean rankMultipliersEnabled = false;
    private volatile double maxTotalMultiplier = 5.0;

    public ShardManager(HavenShards plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        reloadRankMultipliers();
        startAutoSaveTicker();
    }

    private void startAutoSaveTicker() {
        // Periodic dirty batch save every 30 seconds (600 ticks)
        this.autoSaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::saveDirtyBalances, 600L, 600L);
    }

    public void startPendingOperation(UUID uuid) {
        if (uuid == null) return;
        pendingOperations.computeIfAbsent(uuid, k -> new AtomicInteger(0)).incrementAndGet();
    }

    public void finishPendingOperation(UUID uuid) {
        if (uuid == null) return;
        AtomicInteger count = pendingOperations.get(uuid);
        if (count != null && count.decrementAndGet() <= 0) {
            pendingOperations.remove(uuid);
            if (pendingUnloadPlayers.remove(uuid)) {
                // Player quit while async operation was in-flight. Flush balance cleanly now!
                Double balance = balanceCache.remove(uuid);
                dirtyBalances.get().remove(uuid);
                if (balance != null) {
                    if (plugin.isEnabled()) {
                        databaseManager.savePlayerBalanceAsync(uuid, balance);
                    } else {
                        databaseManager.savePlayerBalanceSync(uuid, balance);
                    }
                }
            }
        }
    }

    public boolean hasPendingOperations(UUID uuid) {
        if (uuid == null) return false;
        AtomicInteger count = pendingOperations.get(uuid);
        return count != null && count.get() > 0;
    }

    public void loadPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        pendingUnloadPlayers.remove(uuid);
        loadingPlayers.add(uuid);
        databaseManager.updatePlayerNameAsync(uuid, player.getName());
        databaseManager.loadPlayerBalanceAsync(uuid, balance -> {
            Player online = Bukkit.getPlayer(uuid);
            if (online == null || !online.isOnline()) {
                Double earlyDelta = balanceCache.remove(uuid);
                loadingPlayers.remove(uuid);
                dirtyBalances.get().remove(uuid);
                if (earlyDelta != null && earlyDelta != 0.0) {
                    double maxBal = plugin.getConfig().getDouble("max-balance", 1000000000.0);
                    double finalBal = Math.min(Math.round((balance + earlyDelta) * 100.0) / 100.0, maxBal);
                    if (plugin.isEnabled()) {
                        databaseManager.savePlayerBalanceAsync(uuid, finalBal);
                    } else {
                        databaseManager.savePlayerBalanceSync(uuid, finalBal);
                    }
                } else {
                    databaseManager.cacheOfflineBalance(uuid, balance);
                }
                return;
            }
            double maxBal = plugin.getConfig().getDouble("max-balance", 1000000000.0);
            balanceCache.merge(uuid, balance, (earlyDelta, dbBal) -> {
                dirtyBalances.get().add(uuid);
                return Math.min(Math.round((dbBal + earlyDelta) * 100.0) / 100.0, maxBal);
            });
            loadingPlayers.remove(uuid);
        });
    }

    public void unloadPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        boolean stillLoading = loadingPlayers.remove(uuid);

        if (hasPendingOperations(uuid)) {
            // Player quit while an async balance operation is in-flight!
            // Retain the balance in memory so in-flight callbacks can apply their updates safely.
            pendingUnloadPlayers.add(uuid);
            return;
        }

        Double balance = balanceCache.remove(uuid);
        dirtyBalances.get().remove(uuid);

        if (stillLoading) {
            // Player quit before database balance load finished.
            // If there was an early balance delta, keep it so loadPlayer callback can merge & save it.
            if (balance != null) {
                balanceCache.put(uuid, balance);
            }
            return;
        }

        if (balance != null) {
            // ALWAYS save to database on player disconnect to eliminate race condition with saveDirtyBalances!
            if (plugin.isEnabled()) {
                databaseManager.savePlayerBalanceAsync(uuid, balance);
            } else {
                databaseManager.savePlayerBalanceSync(uuid, balance);
            }
        }
    }

    public boolean isLoaded(UUID uuid) {
        return !loadingPlayers.contains(uuid) && balanceCache.containsKey(uuid) && !pendingUnloadPlayers.contains(uuid);
    }

    public void saveDirtyBalances() {
        Set<UUID> toSave = this.dirtyBalances.getAndSet(ConcurrentHashMap.newKeySet());
        if (toSave == null || toSave.isEmpty()) return;

        Map<UUID, Double> batch = new HashMap<>();
        for (UUID uuid : toSave) {
            Double balance = balanceCache.get(uuid);
            if (balance != null) {
                batch.put(uuid, balance);
            }
        }

        if (!batch.isEmpty()) {
            databaseManager.savePlayerBalancesBatchSync(batch);
        }
    }

    public void saveAllOnlinePlayers() {
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
        }
        if (!balanceCache.isEmpty()) {
            databaseManager.savePlayerBalancesBatchSync(new HashMap<>(balanceCache));
        }
        dirtyBalances.get().clear();
    }

    public double getBalance(UUID uuid) {
        Double cached = balanceCache.get(uuid);
        if (cached != null) return cached;
        return 0.0;
    }

    public double getBalance(Player player) {
        return getBalance(player.getUniqueId());
    }

    public boolean hasBalance(Player player, double amount) {
        return getBalance(player) >= amount;
    }

    public void addBalance(UUID uuid, double amount) {
        if (amount <= 0 || Double.isNaN(amount) || Double.isInfinite(amount)) return;
        double roundedAmount = Math.round(amount * 100.0) / 100.0;
        double maxBal = plugin.getConfig().getDouble("max-balance", 1000000000.0);

        // Guard against memory pollution for offline/unloaded players
        if (!balanceCache.containsKey(uuid) && !loadingPlayers.contains(uuid)) {
            // Player is fully offline or unloaded. Route directly to safe atomic database modification:
            databaseManager.modifyOfflineBalanceAsync(uuid, roundedAmount, null);
            return;
        }

        balanceCache.merge(uuid, roundedAmount, (current, add) -> Math.min(Math.round((current + add) * 100.0) / 100.0, maxBal));
        dirtyBalances.get().add(uuid);
    }

    public void addBalance(Player player, double amount) {
        addBalance(player.getUniqueId(), amount);
    }

    public boolean removeBalance(UUID uuid, double amount) {
        if (amount <= 0 || Double.isNaN(amount) || Double.isInfinite(amount)) return false;
        double roundedAmount = Math.round(amount * 100.0) / 100.0;

        if (!balanceCache.containsKey(uuid)) {
            return false;
        }

        AtomicBoolean success = new AtomicBoolean(false);
        balanceCache.compute(uuid, (k, current) -> {
            if (current == null) {
                success.set(false);
                return null;
            }
            if (current < roundedAmount) {
                success.set(false);
                return current;
            }
            success.set(true);
            return Math.round((current - roundedAmount) * 100.0) / 100.0;
        });

        if (success.get()) {
            dirtyBalances.get().add(uuid);
        }
        return success.get();
    }

    public com.haven.shards.model.WithdrawalResult withdrawIfEnough(org.bukkit.OfflinePlayer target, double amount) {
        if (target == null || amount <= 0 || Double.isNaN(amount) || Double.isInfinite(amount)) {
            double currentBal = target != null ? (target.isOnline() ? getBalance(target.getUniqueId()) : databaseManager.getOfflineBalanceSync(target.getUniqueId())) : 0.0;
            return new com.haven.shards.model.WithdrawalResult(false, currentBal);
        }
        double roundedAmount = Math.round(amount * 100.0) / 100.0;
        if (target.isOnline() && target.getPlayer() != null) {
            AtomicBoolean success = new AtomicBoolean(false);
            double newBalance = balanceCache.compute(target.getUniqueId(), (k, current) -> {
                double cur = (current != null) ? current : 0.0;
                if (cur < roundedAmount) {
                    success.set(false);
                    return cur;
                }
                success.set(true);
                return Math.round((cur - roundedAmount) * 100.0) / 100.0;
            });
            if (success.get()) {
                dirtyBalances.get().add(target.getUniqueId());
            }
            return new com.haven.shards.model.WithdrawalResult(success.get(), newBalance);
        } else {
            return databaseManager.withdrawOfflineSync(target.getUniqueId(), roundedAmount);
        }
    }

    public double depositPlayerSync(org.bukkit.OfflinePlayer target, double amount) {
        if (target == null || amount <= 0 || Double.isNaN(amount) || Double.isInfinite(amount)) return 0.0;
        double roundedAmount = Math.round(amount * 100.0) / 100.0;
        if (target.isOnline() && target.getPlayer() != null) {
            addBalance(target.getPlayer(), roundedAmount);
            return getBalance(target.getPlayer());
        } else {
            return databaseManager.depositOfflineSync(target.getUniqueId(), roundedAmount);
        }
    }

    public boolean removeBalance(Player player, double amount) {
        return removeBalance(player.getUniqueId(), amount);
    }

    public void setBalance(UUID uuid, double amount) {
        if (amount < 0 || Double.isNaN(amount) || Double.isInfinite(amount)) return;
        double maxBal = plugin.getConfig().getDouble("max-balance", 1000000000.0);
        double newBal = Math.round(Math.min(Math.max(0.0, amount), maxBal) * 100.0) / 100.0;

        Player online = Bukkit.getPlayer(uuid);
        if (online != null && online.isOnline() && balanceCache.containsKey(uuid)) {
            balanceCache.put(uuid, newBal);
            dirtyBalances.get().add(uuid);
        } else {
            if (plugin.isEnabled()) {
                databaseManager.savePlayerBalanceAsync(uuid, newBal);
            } else {
                databaseManager.savePlayerBalanceSync(uuid, newBal);
            }
        }
    }

    public void setBalance(Player player, double amount) {
        setBalance(player.getUniqueId(), amount);
    }

    public void modifyOfflineBalance(OfflinePlayer target, double delta, java.util.function.Consumer<Boolean> callback) {
        if (target == null || Double.isNaN(delta) || Double.isInfinite(delta) || delta == 0) {
            if (callback != null) callback.accept(false);
            return;
        }
        double roundedDelta = Math.round(delta * 100.0) / 100.0;
        if (target.isOnline() && target.getPlayer() != null) {
            if (roundedDelta > 0) {
                addBalance(target.getPlayer(), roundedDelta);
                if (callback != null) callback.accept(true);
            } else if (roundedDelta < 0) {
                boolean success = removeBalance(target.getPlayer(), Math.abs(roundedDelta));
                if (callback != null) callback.accept(success);
            } else {
                if (callback != null) callback.accept(true);
            }
        } else {
            databaseManager.modifyOfflineBalanceAsync(target.getUniqueId(), roundedDelta, callback);
        }
    }

    public void getBalanceAsync(OfflinePlayer target, java.util.function.Consumer<Double> callback) {
        if (target.isOnline() && target.getPlayer() != null) {
            double bal = getBalance(target.getPlayer());
            if (callback != null) callback.accept(bal);
        } else {
            databaseManager.loadPlayerBalanceAsync(target.getUniqueId(), callback);
        }
    }

    public void reloadRankMultipliers() {
        this.rankMultipliersEnabled = plugin.getConfig().getBoolean("rank-multipliers.enabled", false);
        this.maxTotalMultiplier = plugin.getConfig().getDouble("rank-multipliers.max-total-multiplier", 5.0);
        List<RankGroup> list = new ArrayList<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("rank-multipliers.groups");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String perm = section.getString(key + ".permission");
                double mult = section.getDouble(key + ".multiplier", 1.0);
                if (perm != null && !perm.isEmpty() && mult > 0.0) {
                    list.add(new RankGroup(perm, mult));
                }
            }
        }
        this.cachedRankGroups = Collections.unmodifiableList(list);
    }

    @EventHandler
    public void onReload(HavenShardsReloadEvent event) {
        reloadRankMultipliers();
    }

    public double getRankMultiplier(Player player) {
        if (player == null || !rankMultipliersEnabled) {
            return 1.0;
        }

        double highestMult = 1.0;
        for (RankGroup group : cachedRankGroups) {
            if (player.hasPermission(group.permission())) {
                if (group.multiplier() > highestMult) {
                    highestMult = group.multiplier();
                }
            }
        }
        return highestMult;
    }

    public double getTotalMultiplier(Player player) {
        double globalBooster = plugin.getBoosterManager().getMultiplier();
        double rankMultiplier = getRankMultiplier(player);
        double total = globalBooster * rankMultiplier;
        return maxTotalMultiplier > 0 ? Math.min(total, maxTotalMultiplier) : total;
    }
}
