package com.havencraft.havencore.stats;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe data container for Player statistics.
 */
public class PlayerStats {

    private final UUID uuid;
    private volatile String name;
    private final Map<StatisticType, Double> values = new ConcurrentHashMap<>();
    private volatile long lastModified;

    private volatile String skinUrl;

    // No-arg constructor for Gson serialization
    public PlayerStats() {
        this.uuid = null;
        this.lastModified = System.currentTimeMillis();
    }

    public PlayerStats(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
        this.lastModified = System.currentTimeMillis();
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<StatisticType, Double> getValues() {
        return values;
    }

    public double getStat(StatisticType type) {
        return values.getOrDefault(type, 0.0);
    }

    public void setStat(StatisticType type, double value) {
        values.put(type, value);
        lastModified = System.currentTimeMillis();
    }
    
    public void incrementStat(StatisticType type, double delta) {
        if (delta == 0) return;
        values.merge(type, delta, Double::sum);
        lastModified = System.currentTimeMillis();
    }

    public String getSkinUrl() {
        return skinUrl;
    }

    public void setSkinUrl(String skinUrl) {
        this.skinUrl = skinUrl;
        lastModified = System.currentTimeMillis();
    }

    public long getLastModified() {
        return lastModified;
    }
}
