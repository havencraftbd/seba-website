package com.haven.shards.model;

import java.util.UUID;

public class DailyPayData {

    private final UUID playerUuid;
    private String dayKey;
    private double totalSent;
    private int transactionCount;

    public DailyPayData(UUID playerUuid, String dayKey, double totalSent, int transactionCount) {
        this.playerUuid = playerUuid;
        this.dayKey = dayKey;
        this.totalSent = totalSent;
        this.transactionCount = transactionCount;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getDayKey() {
        return dayKey;
    }

    public void setDayKey(String dayKey) {
        this.dayKey = dayKey;
    }

    public double getTotalSent() {
        return totalSent;
    }

    public void setTotalSent(double totalSent) {
        this.totalSent = totalSent;
    }

    public int getTransactionCount() {
        return transactionCount;
    }

    public void setTransactionCount(int transactionCount) {
        this.transactionCount = transactionCount;
    }

    public void recordTransaction(double amount) {
        this.totalSent += amount;
        this.transactionCount += 1;
    }

    public void resetForNewDay(String newDayKey) {
        this.dayKey = newDayKey;
        this.totalSent = 0.0;
        this.transactionCount = 0;
    }
}
