package com.haven.shards.model;

import java.util.UUID;

public class DailyRewardData {

    private final UUID playerUuid;
    private long lastClaimMillis;
    private int streak;

    public DailyRewardData(UUID playerUuid, long lastClaimMillis, int streak) {
        this.playerUuid = playerUuid;
        this.lastClaimMillis = lastClaimMillis;
        this.streak = streak;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public long getLastClaimMillis() {
        return lastClaimMillis;
    }

    public void setLastClaimMillis(long lastClaimMillis) {
        this.lastClaimMillis = lastClaimMillis;
    }

    public int getStreak() {
        return streak;
    }

    public void setStreak(int streak) {
        this.streak = streak;
    }
}
