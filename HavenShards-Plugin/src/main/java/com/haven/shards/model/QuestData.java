package com.haven.shards.model;

import java.util.UUID;

public class QuestData {

    private final UUID playerUuid;
    private final String questId;
    private int progress;
    private boolean claimed;
    private long lastResetMillis;
    private volatile boolean dirty = false;

    public QuestData(UUID playerUuid, String questId, int progress, boolean claimed, long lastResetMillis) {
        this.playerUuid = playerUuid;
        this.questId = questId;
        this.progress = progress;
        this.claimed = claimed;
        this.lastResetMillis = lastResetMillis;
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public String getQuestId() { return questId; }
    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }
    public boolean isClaimed() { return claimed; }
    public void setClaimed(boolean claimed) { this.claimed = claimed; }
    public long getLastResetMillis() { return lastResetMillis; }
    public void setLastResetMillis(long lastResetMillis) { this.lastResetMillis = lastResetMillis; }
    public boolean isDirty() { return dirty; }
    public void setDirty(boolean dirty) { this.dirty = dirty; }
}
