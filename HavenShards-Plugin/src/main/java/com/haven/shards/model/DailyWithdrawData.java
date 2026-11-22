package com.haven.shards.model;

import java.util.UUID;

public class DailyWithdrawData {

    private final UUID playerUuid;
    private String dayKey;
    private double totalWithdrawn;
    private int withdrawCount;

    public DailyWithdrawData(UUID playerUuid, String dayKey, double totalWithdrawn, int withdrawCount) {
        this.playerUuid = playerUuid;
        this.dayKey = dayKey;
        this.totalWithdrawn = totalWithdrawn;
        this.withdrawCount = withdrawCount;
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

    public double getTotalWithdrawn() {
        return totalWithdrawn;
    }

    public void setTotalWithdrawn(double totalWithdrawn) {
        this.totalWithdrawn = totalWithdrawn;
    }

    public int getWithdrawCount() {
        return withdrawCount;
    }

    public void setWithdrawCount(int withdrawCount) {
        this.withdrawCount = withdrawCount;
    }

    public void recordWithdraw(double amount) {
        this.totalWithdrawn += amount;
        this.withdrawCount += 1;
    }

    public void resetForNewDay(String newDayKey) {
        this.dayKey = newDayKey;
        this.totalWithdrawn = 0.0;
        this.withdrawCount = 0;
    }
}
