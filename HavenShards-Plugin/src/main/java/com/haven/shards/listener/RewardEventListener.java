package com.haven.shards.listener;

import com.haven.shards.HavenShards;
import com.haven.shards.util.ColorUtil;
import com.haven.shards.util.ItemUtil;
import com.haven.shards.util.SoundUtil;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerFishEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import com.haven.shards.api.event.HavenShardsReloadEvent;

public class RewardEventListener implements Listener {

    private final HavenShards plugin;
    private final Random random = new Random();
    private Set<Material> cachedMiningBlocks = new HashSet<>();
    private Set<Material> cachedFarmingCrops = new HashSet<>();
    private volatile RewardSettings settings;
    private final Map<UUID, Set<String>> claimedAdvancementsCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<UUID> loadedPlayers = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final com.google.common.cache.Cache<String, Long> pkCooldownCache = com.google.common.cache.CacheBuilder.newBuilder()
            .expireAfterWrite(5, java.util.concurrent.TimeUnit.MINUTES)
            .maximumSize(2000)
            .build();

    public static class RewardSettings {
        public boolean pkEnabled; public double pkChance; public double pkAmount; public boolean pkLog; public boolean pkNotify; public String pkMsg; public String pkDisplay;
        public boolean mkEnabled; public double mkChance; public double mkAmount; public boolean mkLog; public boolean mkNotify; public String mkMsg; public String mkDisplay;
        public boolean minEnabled; public double minChance; public double minAmount; public boolean minLog; public boolean minNotify; public String minMsg; public String minDisplay;
        public boolean farmEnabled; public double farmChance; public double farmAmount; public boolean farmLog; public boolean farmNotify; public String farmMsg; public String farmDisplay;
        public boolean fishEnabled; public double fishChance; public double fishAmount; public boolean fishLog; public boolean fishNotify; public String fishMsg; public String fishDisplay;
        public boolean achEnabled; public boolean achLog; public boolean achNotify; public String achMsg; public String achDisplay;
        public String currencySymbol; public String currencyName;
        public Map<String, Double> advancements = new HashMap<>();
    }

    public RewardEventListener(HavenShards plugin) {
        this.plugin = plugin;
        reloadConfigCaches();
    }

    public void reloadConfigCaches() {
        this.cachedMiningBlocks = plugin.getConfig().getStringList("gameplay-rewards.mining.allowed-blocks")
                .stream().map(s -> {
                    try { return Material.valueOf(s.toUpperCase()); }
                    catch (Exception e) { return null; }
                }).filter(Objects::nonNull).collect(Collectors.toSet());

        this.cachedFarmingCrops = plugin.getConfig().getStringList("gameplay-rewards.farming.allowed-crops")
                .stream().map(s -> {
                    try { return Material.valueOf(s.toUpperCase()); }
                    catch (Exception e) { return null; }
                }).filter(Objects::nonNull).collect(Collectors.toSet());

        Set<Material> allTrackable = new HashSet<>(this.cachedMiningBlocks);
        allTrackable.addAll(this.cachedFarmingCrops);
        for (Material m : Material.values()) {
            String name = m.name();
            if (name.endsWith("_ORE") || name.equals("ANCIENT_DEBRIS")) {
                allTrackable.add(m);
            }
        }
        com.haven.shards.util.BlockTracker.setTrackableMaterials(allTrackable);

        RewardSettings s = new RewardSettings();
        
        s.currencySymbol = plugin.getConfig().getString("currency.symbol", "✨");
        s.currencyName = plugin.getConfig().getString("currency.name", "Shards");

        s.pkEnabled = plugin.getConfig().getBoolean("gameplay-rewards.player-kills.enabled", true);
        s.pkChance = plugin.getConfig().getDouble("gameplay-rewards.player-kills.chance", 25.0);
        s.pkAmount = plugin.getConfig().getDouble("gameplay-rewards.player-kills.amount", 10.0);
        s.pkLog = plugin.getConfig().getBoolean("gameplay-rewards.player-kills.log-history", true);
        s.pkNotify = plugin.getConfig().getBoolean("gameplay-rewards.player-kills.notify", true);
        s.pkMsg = plugin.getConfig().getString("gameplay-rewards.player-kills.message", "&a+%amount% ✨ Shards &7earned for defeating a player!");
        s.pkDisplay = plugin.getConfig().getString("gameplay-rewards.player-kills.display-type", "CHAT");

        s.mkEnabled = plugin.getConfig().getBoolean("gameplay-rewards.mob-kills.enabled", true);
        s.mkChance = plugin.getConfig().getDouble("gameplay-rewards.mob-kills.chance", 5.0);
        s.mkAmount = plugin.getConfig().getDouble("gameplay-rewards.mob-kills.amount", 2.0);
        s.mkLog = plugin.getConfig().getBoolean("gameplay-rewards.mob-kills.log-history", true);
        s.mkNotify = plugin.getConfig().getBoolean("gameplay-rewards.mob-kills.notify", false);
        s.mkMsg = plugin.getConfig().getString("gameplay-rewards.mob-kills.message", "&a+%amount% ✨ Shards &7earned for mob kill!");
        s.mkDisplay = plugin.getConfig().getString("gameplay-rewards.mob-kills.display-type", "CHAT");

        s.minEnabled = plugin.getConfig().getBoolean("gameplay-rewards.mining.enabled", true);
        s.minChance = plugin.getConfig().getDouble("gameplay-rewards.mining.chance", 3.0);
        s.minAmount = plugin.getConfig().getDouble("gameplay-rewards.mining.amount", 1.0);
        s.minLog = plugin.getConfig().getBoolean("gameplay-rewards.mining.log-history", false);
        s.minNotify = plugin.getConfig().getBoolean("gameplay-rewards.mining.notify", false);
        s.minMsg = plugin.getConfig().getString("gameplay-rewards.mining.message", "&a+%amount% ✨ Shards &7earned for mining ore!");
        s.minDisplay = plugin.getConfig().getString("gameplay-rewards.mining.display-type", "CHAT");

        s.farmEnabled = plugin.getConfig().getBoolean("gameplay-rewards.farming.enabled", true);
        s.farmChance = plugin.getConfig().getDouble("gameplay-rewards.farming.chance", 3.0);
        s.farmAmount = plugin.getConfig().getDouble("gameplay-rewards.farming.amount", 0.5);
        s.farmLog = plugin.getConfig().getBoolean("gameplay-rewards.farming.log-history", false);
        s.farmNotify = plugin.getConfig().getBoolean("gameplay-rewards.farming.notify", true);
        s.farmMsg = plugin.getConfig().getString("gameplay-rewards.farming.message", "&a+%amount% ✨ Shards &7earned for harvesting crops!");
        s.farmDisplay = plugin.getConfig().getString("gameplay-rewards.farming.display-type", "CHAT");

        s.fishEnabled = plugin.getConfig().getBoolean("gameplay-rewards.fishing.enabled", true);
        s.fishChance = plugin.getConfig().getDouble("gameplay-rewards.fishing.chance", 8.0);
        s.fishAmount = plugin.getConfig().getDouble("gameplay-rewards.fishing.amount", 1.5);
        s.fishLog = plugin.getConfig().getBoolean("gameplay-rewards.fishing.log-history", true);
        s.fishNotify = plugin.getConfig().getBoolean("gameplay-rewards.fishing.notify", true);
        s.fishMsg = plugin.getConfig().getString("gameplay-rewards.fishing.message", "&a+%amount% ✨ Shards &7earned for catching a fish!");
        s.fishDisplay = plugin.getConfig().getString("gameplay-rewards.fishing.display-type", "CHAT");

        s.achEnabled = plugin.getConfig().getBoolean("achievement-rewards.enabled", true);
        s.achLog = plugin.getConfig().getBoolean("achievement-rewards.log-history", true);
        s.achNotify = plugin.getConfig().getBoolean("achievement-rewards.notify", true);
        s.achMsg = plugin.getConfig().getString("achievement-rewards.message", "&a+%amount% ✨ Shards &7earned for completing achievement: &e%achievement%&7!");
        s.achDisplay = plugin.getConfig().getString("achievement-rewards.display-type", "CHAT");

        ConfigurationSection achSec = plugin.getConfig().getConfigurationSection("achievement-rewards.advancements");
        if (achSec != null) {
            for (String key : achSec.getKeys(false)) {
                s.advancements.put(key, achSec.getDouble(key, 0.0));
            }
        }

        this.settings = s;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        RewardSettings s = this.settings;

        // Player Kill Reward
        if (event.getEntity() instanceof Player victim) {
            if (victim.equals(killer)) return;
            if (!s.pkEnabled) return;

            // Anti-alt / anti-farming checks:
            if (killer.getAddress() != null && victim.getAddress() != null
                    && killer.getAddress().getAddress().equals(victim.getAddress().getAddress())) {
                return;
            }
            String pairKey = killer.getUniqueId() + ":" + victim.getUniqueId();
            if (pkCooldownCache.getIfPresent(pairKey) != null) {
                return;
            }
            pkCooldownCache.put(pairKey, System.currentTimeMillis());

            if (random.nextDouble() * 100.0 <= s.pkChance) {
                double amount = s.pkAmount * plugin.getShardManager().getTotalMultiplier(killer);
                plugin.getShardManager().addBalance(killer, amount);
                SoundUtil.playSound(plugin, killer, "player-kill-reward");

                if (s.pkLog) {
                    double newBal = plugin.getShardManager().getBalance(killer);
                    plugin.getDatabaseManager().logTransactionAsync(null, null, killer.getUniqueId(), killer.getName(), "PLAYER_KILL", amount, 0, newBal);
                }

                if (s.pkNotify) {
                    sendRewardNotify(killer, amount, s.pkMsg, s.pkDisplay);
                }
            }
            return;
        }

        // Mob Kill Reward
        if (event.getEntity() instanceof Monster monster) {
            if (!s.mkEnabled) return;
            // Block spawner mob grinding farm exploit
            if (monster.fromMobSpawner()) return;

            if (random.nextDouble() * 100.0 <= s.mkChance) {
                double amount = s.mkAmount * plugin.getShardManager().getTotalMultiplier(killer);
                plugin.getShardManager().addBalance(killer, amount);
                SoundUtil.playSound(plugin, killer, "mob-kill-reward");

                if (s.mkLog) {
                    double newBal = plugin.getShardManager().getBalance(killer);
                    plugin.getDatabaseManager().logTransactionAsync(null, null, killer.getUniqueId(), killer.getName(), "MOB_KILL", amount, 0, newBal);
                }

                if (s.mkNotify) {
                    sendRewardNotify(killer, amount, s.mkMsg, s.mkDisplay);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(org.bukkit.event.block.BlockPlaceEvent event) {
        Material mat = event.getBlock().getType();
        // Only track ores and crops that reward shards to completely eliminate CPU overhead!
        if (com.haven.shards.util.BlockTracker.isTrackable(mat)) {
            com.haven.shards.util.BlockTracker.markPlaced(plugin, event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(org.bukkit.event.block.BlockPistonExtendEvent event) {
        com.haven.shards.util.BlockTracker.handlePistonMove(plugin, event.getBlocks(), event.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(org.bukkit.event.block.BlockPistonRetractEvent event) {
        com.haven.shards.util.BlockTracker.handlePistonMove(plugin, event.getBlocks(), event.getDirection().getOppositeFace());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(org.bukkit.event.entity.EntityExplodeEvent event) {
        for (Block b : event.blockList()) {
            if (com.haven.shards.util.BlockTracker.isTrackable(b.getType())) {
                com.haven.shards.util.BlockTracker.removePlaced(plugin, b);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(org.bukkit.event.block.BlockExplodeEvent event) {
        for (Block b : event.blockList()) {
            if (com.haven.shards.util.BlockTracker.isTrackable(b.getType())) {
                com.haven.shards.util.BlockTracker.removePlaced(plugin, b);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBurn(org.bukkit.event.block.BlockBurnEvent event) {
        if (com.haven.shards.util.BlockTracker.isTrackable(event.getBlock().getType())) {
            com.haven.shards.util.BlockTracker.removePlaced(plugin, event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Material blockType = event.getBlock().getType();
        if (com.haven.shards.util.BlockTracker.isTrackable(blockType)) {
            if (com.haven.shards.util.BlockTracker.isPlaced(plugin, event.getBlock())) {
                com.haven.shards.util.BlockTracker.removePlaced(plugin, event.getBlock());
                return; // Block was placed by a player, skip all mining/farming rewards!
            }
        }

        RewardSettings s = this.settings;

        // 1. Mining Reward
        if (s.minEnabled && cachedMiningBlocks.contains(blockType)) {
            if (random.nextDouble() * 100.0 <= s.minChance) {
                Player player = event.getPlayer();
                double amount = s.minAmount * plugin.getShardManager().getTotalMultiplier(player);
                plugin.getShardManager().addBalance(player, amount);
                SoundUtil.playSound(plugin, player, "mining-reward");

                if (s.minLog) {
                    double newBal = plugin.getShardManager().getBalance(player);
                    plugin.getDatabaseManager().logTransactionAsync(null, null, player.getUniqueId(), player.getName(), "MINING", amount, 0, newBal);
                }

                if (s.minNotify) {
                    sendRewardNotify(player, amount, s.minMsg, s.minDisplay);
                }
            }
            return;
        }

        // 2. Farming Reward
        if (s.farmEnabled && cachedFarmingCrops.contains(blockType)) {
            // Check if crop is fully grown (if Ageable)
            if (event.getBlock().getBlockData() instanceof Ageable ageable) {
                if (ageable.getAge() != ageable.getMaximumAge()) return;
            }

            if (random.nextDouble() * 100.0 <= s.farmChance) {
                Player player = event.getPlayer();
                double amount = s.farmAmount * plugin.getShardManager().getTotalMultiplier(player);
                plugin.getShardManager().addBalance(player, amount);
                SoundUtil.playSound(plugin, player, "farming-reward");

                if (s.farmLog) {
                    double newBal = plugin.getShardManager().getBalance(player);
                    plugin.getDatabaseManager().logTransactionAsync(null, null, player.getUniqueId(), player.getName(), "FARMING", amount, 0, newBal);
                }

                if (s.farmNotify) {
                    sendRewardNotify(player, amount, s.farmMsg, s.farmDisplay);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        RewardSettings s = this.settings;
        if (!s.fishEnabled) return;

        if (random.nextDouble() * 100.0 <= s.fishChance) {
            Player player = event.getPlayer();
            double amount = s.fishAmount * plugin.getShardManager().getTotalMultiplier(player);
            plugin.getShardManager().addBalance(player, amount);
            SoundUtil.playSound(plugin, player, "fishing-reward");

            if (s.fishLog) {
                double newBal = plugin.getShardManager().getBalance(player);
                plugin.getDatabaseManager().logTransactionAsync(null, null, player.getUniqueId(), player.getName(), "FISHING", amount, 0, newBal);
            }

            if (s.fishNotify) {
                sendRewardNotify(player, amount, s.fishMsg, s.fishDisplay);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        loadedPlayers.remove(uuid);
        plugin.getDatabaseManager().loadClaimedAdvancementsAsync(uuid, set -> {
            if (event.getPlayer().isOnline()) {
                Set<String> existing = claimedAdvancementsCache.get(uuid);
                if (existing != null) {
                    set.addAll(existing);
                }
                claimedAdvancementsCache.put(uuid, set);
                loadedPlayers.add(uuid);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        claimedAdvancementsCache.remove(uuid);
        loadedPlayers.remove(uuid);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAdvancementDone(PlayerAdvancementDoneEvent event) {
        RewardSettings s = this.settings;
        if (!s.achEnabled) return;

        org.bukkit.advancement.Advancement adv = event.getAdvancement();
        if (adv == null || adv.getKey() == null) return;

        String key = adv.getKey().getKey();
        String fullKey = adv.getKey().toString();

        if (key.startsWith("recipes/") || fullKey.startsWith("minecraft:recipes/")) return;

        String shortKey = key.contains("/") ? key.substring(key.lastIndexOf('/') + 1) : key;

        double amount = 0.0;
        String matchKey = null;
        if (s.advancements.containsKey(shortKey)) {
            amount = s.advancements.get(shortKey);
            matchKey = shortKey;
        } else if (s.advancements.containsKey(key)) {
            amount = s.advancements.get(key);
            matchKey = key;
        } else if (s.advancements.containsKey(fullKey)) {
            amount = s.advancements.get(fullKey);
            matchKey = fullKey;
        }

        if (amount <= 0 || matchKey == null) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!loadedPlayers.contains(uuid)) {
            final double fAmount = amount;
            final String fMatchKey = matchKey;
            final String fKey = key;
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && loadedPlayers.contains(uuid)) {
                    processAdvancementReward(player, fMatchKey, fAmount, fKey);
                }
            }, 10L);
            return;
        }

        processAdvancementReward(player, matchKey, amount, key);
    }

    private void processAdvancementReward(Player player, String matchKey, double amount, String key) {
        if (!player.isOnline()) return;
        UUID uuid = player.getUniqueId();

        Set<String> claimed = claimedAdvancementsCache.computeIfAbsent(uuid, k -> java.util.concurrent.ConcurrentHashMap.newKeySet());
        if (!claimed.add(matchKey)) {
            return; // Already rewarded!
        }
        plugin.getDatabaseManager().saveClaimedAdvancementAsync(uuid, matchKey);

        RewardSettings s = this.settings;
        double multiplier = plugin.getShardManager().getTotalMultiplier(player);
        double totalReward = amount * multiplier;

        plugin.getShardManager().addBalance(player, totalReward);
        SoundUtil.playSound(plugin, player, "achievement-reward");

        if (s.achLog) {
            double newBal = plugin.getShardManager().getBalance(player);
            plugin.getDatabaseManager().logTransactionAsync(null, null, player.getUniqueId(), player.getName(), "ACHIEVEMENT", totalReward, 0, newBal);
        }

        if (s.achNotify) {
            String rawTitle = key.contains("/") ? key.substring(key.lastIndexOf('/') + 1) : key;
            rawTitle = rawTitle.replace('_', ' ');
            String title = rawTitle.isEmpty() ? key : Character.toUpperCase(rawTitle.charAt(0)) + rawTitle.substring(1);

            String msg = ColorUtil.colorize(s.achMsg
                    .replace("%amount%", ItemUtil.formatMoney(totalReward))
                    .replace("%achievement%", title)
                    .replace("%symbol%", s.currencySymbol)
                    .replace("%currency%", s.currencyName)
            );
            com.haven.shards.util.NotificationUtil.sendNotification(plugin, player, msg, s.achDisplay, "CHAT");
        }
    }

    private void sendRewardNotify(Player player, double amount, String rawMsg, String displayType) {
        RewardSettings s = this.settings;
        String msg = ColorUtil.colorize(rawMsg
                .replace("%symbol%", s.currencySymbol)
                .replace("%amount%", ItemUtil.formatMoney(amount))
                .replace("%currency%", s.currencyName)
        );
        com.haven.shards.util.NotificationUtil.sendNotification(plugin, player, msg, displayType, "CHAT");
    }

    @EventHandler
    public void onReload(HavenShardsReloadEvent event) {
        reloadConfigCaches();
    }
}
