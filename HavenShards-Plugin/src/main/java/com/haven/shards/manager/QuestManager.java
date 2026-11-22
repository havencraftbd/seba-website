package com.haven.shards.manager;

import com.haven.shards.HavenShards;
import com.haven.shards.model.QuestData;
import com.haven.shards.util.ColorUtil;
import com.haven.shards.util.ItemUtil;
import com.haven.shards.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import com.haven.shards.api.event.HavenShardsReloadEvent;

public class QuestManager implements Listener {

    public static final long DAY_MILLIS = 24 * 60 * 60 * 1000L;

    public static class QuestDefinition {
        private final String id;
        private final String title;
        private final String type; // MINE_BLOCKS, KILL_MOBS, KILL_PLAYERS, CATCH_FISH, HARVEST_CROPS, PLAYTIME
        private final int target;
        private final double reward;
        private final String material;
        private final int customModelData;
        private final List<String> allowedMaterials;
        private final Set<String> allowedMaterialsSet;
        private final int slot;

        public QuestDefinition(String id, String title, String type, int target, double reward, String material, int customModelData, List<String> allowedMaterials, int slot) {
            this.id = id;
            this.title = title;
            this.type = type.toUpperCase();
            this.target = Math.max(1, target);
            this.reward = reward;
            this.material = material;
            this.customModelData = customModelData;
            this.allowedMaterials = allowedMaterials != null ? allowedMaterials : Collections.emptyList();
            if (this.allowedMaterials.isEmpty()) {
                this.allowedMaterialsSet = Collections.emptySet();
            } else {
                this.allowedMaterialsSet = this.allowedMaterials.stream()
                        .map(String::toUpperCase)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
            }
            this.slot = slot;
        }

        public String getId() { return id; }
        public String getTitle() { return title; }
        public String getType() { return type; }
        public int getTarget() { return target; }
        public double getReward() { return reward; }
        public String getMaterial() { return material; }
        public int getCustomModelData() { return customModelData; }
        public List<String> getAllowedMaterials() { return allowedMaterials; }
        public Set<String> getAllowedMaterialsSet() { return allowedMaterialsSet; }
        public int getSlot() { return slot; }
    }

    private final HavenShards plugin;
    private final Map<UUID, Map<String, QuestData>> playerCache = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> activeQuestTypes = new ConcurrentHashMap<>();
    private volatile boolean dailyQuestsEnabled = true;
    private volatile Set<String> configuredQuestTypes = Collections.emptySet();
    private volatile List<QuestDefinition> cachedDefinitions = null;
    private volatile long cachedMidnightMillis = 0L;
    private volatile long midnightCacheExpiresAt = 0L;

    public QuestManager(HavenShards plugin) {
        this.plugin = plugin;
        reloadQuestDefinitions();
    }

    public void reloadQuestDefinitions() {
        this.dailyQuestsEnabled = plugin.getConfig().getBoolean("daily-quests.enabled", true);
        this.midnightCacheExpiresAt = 0L;
        List<QuestDefinition> list = new ArrayList<>();
        Set<String> types = new HashSet<>();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("daily-quests.quests");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                ConfigurationSection qSec = sec.getConfigurationSection(key);
                if (qSec == null || !qSec.getBoolean("enabled", true)) continue;

                String title = qSec.getString("gui_title", qSec.getString("title", key));
                String type = qSec.getString("type", "MINE_BLOCKS").toUpperCase();
                int target = qSec.getInt("target", 100);
                double reward = qSec.getDouble("reward", 50.0);
                String mat = qSec.getString("gui_material", qSec.getString("material", "BOOK"));
                int cmd = qSec.getInt("custom-model-data", 0);
                List<String> allowed = qSec.getStringList("allowed-types");
                int slot = qSec.getInt("slot", -1);

                list.add(new QuestDefinition(key, title, type, target, reward, mat, cmd, allowed, slot));
                types.add(type);
            }
        }
        this.cachedDefinitions = Collections.unmodifiableList(list);
        this.configuredQuestTypes = Collections.unmodifiableSet(types);

        for (UUID uuid : playerCache.keySet()) {
            recalculateActiveQuestTypes(uuid);
        }
    }

    public void loadPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        plugin.getDatabaseManager().loadPlayerQuestsAsync(uuid, map -> {
            Player online = Bukkit.getPlayer(uuid);
            if (online == null || !online.isOnline()) {
                return; // Guard against quick join-quit memory leak
            }
            Map<String, QuestData> current = playerCache.get(uuid);
            long midnight = getMidnightMillis();
            long now = System.currentTimeMillis();
            if (current != null && !current.isEmpty()) {
                for (Map.Entry<String, QuestData> entry : map.entrySet()) {
                    String questId = entry.getKey();
                    QuestData loadedData = entry.getValue();
                    QuestData existingData = current.get(questId);

                    // Reset if the loaded record was from before today's reset
                    if (loadedData.getLastResetMillis() > 0 && loadedData.getLastResetMillis() < midnight) {
                        loadedData.setProgress(0);
                        loadedData.setClaimed(false);
                        loadedData.setLastResetMillis(now);
                    }

                    if (existingData != null) {
                        loadedData.setProgress(loadedData.getProgress() + existingData.getProgress());
                        if (existingData.isClaimed()) loadedData.setClaimed(true);
                        loadedData.setLastResetMillis(Math.max(loadedData.getLastResetMillis(), existingData.getLastResetMillis()));
                    }
                    current.put(questId, loadedData);
                }
            } else {
                for (QuestData loadedData : map.values()) {
                    if (loadedData.getLastResetMillis() > 0 && loadedData.getLastResetMillis() < midnight) {
                        loadedData.setProgress(0);
                        loadedData.setClaimed(false);
                        loadedData.setLastResetMillis(now);
                    }
                }
                playerCache.put(uuid, new ConcurrentHashMap<>(map));
            }
            recalculateActiveQuestTypes(uuid);
        });
    }

    public void unloadPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        activeQuestTypes.remove(uuid);
        Map<String, QuestData> map = playerCache.remove(uuid);
        if (map != null) {
            List<QuestData> dirtyQuests = new ArrayList<>();
            for (QuestData qd : map.values()) {
                if (qd.isDirty()) {
                    dirtyQuests.add(qd);
                    qd.setDirty(false);
                }
            }
            if (!dirtyQuests.isEmpty()) {
                if (plugin.isEnabled()) {
                    plugin.getDatabaseManager().saveQuestDataBatchAsync(dirtyQuests);
                } else {
                    plugin.getDatabaseManager().saveQuestDataBatchSync(dirtyQuests);
                }
            }
        }
    }

    public void saveAllOnlinePlayers() {
        List<QuestData> dirtyQuests = new ArrayList<>();
        for (Map.Entry<UUID, Map<String, QuestData>> entry : playerCache.entrySet()) {
            for (QuestData qd : entry.getValue().values()) {
                if (qd.isDirty()) {
                    dirtyQuests.add(qd);
                    qd.setDirty(false);
                }
            }
        }
        if (!dirtyQuests.isEmpty()) {
            plugin.getDatabaseManager().saveQuestDataBatchSync(dirtyQuests);
        }
    }

    public List<QuestDefinition> getQuestDefinitions() {
        if (cachedDefinitions == null) {
            reloadQuestDefinitions();
        }
        return cachedDefinitions;
    }

    private long getMidnightMillis() {
        long now = System.currentTimeMillis();
        if (now < midnightCacheExpiresAt && cachedMidnightMillis > 0) {
            return cachedMidnightMillis;
        }

        String tzString = plugin.getConfig().getString("daily-quests.reset-timezone", "Asia/Dhaka");
        java.util.TimeZone tz = java.util.TimeZone.getTimeZone(tzString);
        Calendar cal = Calendar.getInstance(tz);
        cal.setTimeInMillis(now);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long todayMidnight = cal.getTimeInMillis();

        cal.add(Calendar.DAY_OF_YEAR, 1);
        long nextMidnight = cal.getTimeInMillis();

        this.cachedMidnightMillis = todayMidnight;
        this.midnightCacheExpiresAt = nextMidnight;
        return todayMidnight;
    }

    public long getNextResetMillis() {
        getMidnightMillis();
        return midnightCacheExpiresAt;
    }

    public QuestData getQuestData(UUID uuid, String questId) {
        Map<String, QuestData> playerMap = playerCache.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        QuestData qd = playerMap.get(questId);
        long now = System.currentTimeMillis();

        if (qd == null) {
            qd = new QuestData(uuid, questId, 0, false, now);
            playerMap.put(questId, qd);
        } else if (qd.getLastResetMillis() > 0 && qd.getLastResetMillis() < getMidnightMillis()) {
            // Global Midnight Reset
            synchronized (qd) {
                qd.setProgress(0);
                qd.setClaimed(false);
                qd.setLastResetMillis(now);
                qd.setDirty(false);
            }
            plugin.getDatabaseManager().saveQuestDataAsync(qd);
        }
        return qd;
    }

    public void recalculateActiveQuestTypes(UUID uuid) {
        if (!dailyQuestsEnabled || uuid == null) {
            activeQuestTypes.remove(uuid);
            return;
        }

        Map<String, QuestData> playerMap = playerCache.get(uuid);
        if (playerMap == null) {
            // Player data not loaded yet, assume configured types are active
            activeQuestTypes.put(uuid, configuredQuestTypes);
            return;
        }

        long midnight = getMidnightMillis();
        long now = System.currentTimeMillis();
        Set<String> active = new HashSet<>();

        for (QuestDefinition def : getQuestDefinitions()) {
            QuestData qd = playerMap.get(def.getId());
            if (qd == null) {
                // Not started yet today
                active.add(def.getType());
            } else {
                // Check if yesterday's quest needs reset
                if (qd.getLastResetMillis() > 0 && qd.getLastResetMillis() < midnight) {
                    synchronized (qd) {
                        qd.setProgress(0);
                        qd.setClaimed(false);
                        qd.setLastResetMillis(now);
                        qd.setDirty(false);
                    }
                    plugin.getDatabaseManager().saveQuestDataAsync(qd);
                    active.add(def.getType());
                } else if (!qd.isClaimed() && qd.getProgress() < def.getTarget()) {
                    active.add(def.getType());
                }
            }
        }

        if (active.isEmpty()) {
            activeQuestTypes.put(uuid, Collections.emptySet());
        } else {
            activeQuestTypes.put(uuid, Collections.unmodifiableSet(active));
        }
    }

    public boolean hasActiveQuest(UUID uuid, String type) {
        if (!dailyQuestsEnabled || uuid == null || type == null) return false;
        checkMidnightReset();

        Set<String> types = activeQuestTypes.get(uuid);
        if (types == null) {
            return configuredQuestTypes.contains(type.toUpperCase());
        }
        return types.contains(type.toUpperCase());
    }

    public void checkMidnightReset() {
        long now = System.currentTimeMillis();
        if (now >= midnightCacheExpiresAt && midnightCacheExpiresAt > 0) {
            getMidnightMillis(); // Recalculates cachedMidnightMillis and next midnight
            for (UUID uuid : playerCache.keySet()) {
                recalculateActiveQuestTypes(uuid);
            }
        }
    }

    public void incrementProgress(Player player, String type, int amount, String materialName) {
        if (player == null || amount <= 0 || !dailyQuestsEnabled) return;

        UUID uuid = player.getUniqueId();
        if (!hasActiveQuest(uuid, type)) return;

        String upperType = type.toUpperCase();
        for (QuestDefinition def : getQuestDefinitions()) {
            if (!def.getType().equalsIgnoreCase(upperType)) continue;

            if (!def.getAllowedMaterialsSet().isEmpty() && materialName != null) {
                if (!def.getAllowedMaterialsSet().contains(materialName.toUpperCase())) {
                    continue;
                }
            }

            QuestData qd = getQuestData(uuid, def.getId());
            if (qd.isClaimed()) continue;

            synchronized (qd) {
                int current = qd.getProgress();
                if (current < def.getTarget()) {
                    int next = Math.min(def.getTarget(), current + amount);
                    qd.setProgress(next);
                    qd.setDirty(true);
                    // Save to DB when quest completed or upon player quit/saveAll
                    if (next >= def.getTarget()) {
                        qd.setDirty(false);
                        plugin.getDatabaseManager().saveQuestDataAsync(qd);
                        recalculateActiveQuestTypes(uuid);
                    }
                }
            }
        }
    }

    public boolean claimQuest(Player player, String questId) {
        if (player == null || questId == null) return false;
        UUID uuid = player.getUniqueId();

        QuestDefinition def = getQuestDefinitions().stream()
                .filter(d -> d.getId().equalsIgnoreCase(questId))
                .findFirst().orElse(null);
        if (def == null) return false;

        QuestData qd = getQuestData(uuid, def.getId());
        double finalReward;
        synchronized (qd) {
            if (qd.isClaimed()) return false;
            if (qd.getProgress() < def.getTarget()) return false;

            double multiplier = plugin.getShardManager().getTotalMultiplier(player);
            finalReward = def.getReward() * multiplier;

            qd.setClaimed(true);
            qd.setDirty(false);
            plugin.getShardManager().addBalance(player, finalReward);
            plugin.getDatabaseManager().saveQuestDataAsync(qd);
        }
        recalculateActiveQuestTypes(uuid);
        SoundUtil.playSound(plugin, player, "daily-reward-claim");

        boolean logHistory = plugin.getConfig().getBoolean("daily-quests.log-history", true);
        if (logHistory) {
            double newBal = plugin.getShardManager().getBalance(player);
            plugin.getDatabaseManager().logTransactionAsync(null, null, uuid, player.getName(), "QUEST", finalReward, 0, newBal);
        }

        if (plugin.getConfig().getBoolean("daily-quests.notify", true)) {
            String rawMsg = plugin.getConfig().getString("daily-quests.claim-message", "&a+%amount% ✨ Shards &7claimed for completing quest: &e%title%&7!");
            String symbol = plugin.getConfig().getString("currency.symbol", "✨");
            String currency = plugin.getConfig().getString("currency.name", "Shards");
            String displayType = plugin.getConfig().getString("daily-quests.display-type", "ACTION_BAR");

            String formattedMsg = ColorUtil.colorize(rawMsg
                    .replace("%amount%", ItemUtil.formatMoney(finalReward))
                    .replace("%title%", ColorUtil.colorize(def.getTitle()))
                    .replace("%symbol%", symbol)
                    .replace("%currency%", currency)
            );
            com.haven.shards.util.NotificationUtil.sendNotification(plugin, player, formattedMsg, displayType, "ACTION_BAR");
        }
        return true;
    }

    @EventHandler
    public void onReload(HavenShardsReloadEvent event) {
        reloadQuestDefinitions();
    }
}
