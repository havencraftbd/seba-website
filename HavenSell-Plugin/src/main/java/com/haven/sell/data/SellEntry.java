package com.haven.sell.data;

import java.time.Instant;

/**
 * Represents a single sell transaction in a player's history.
 */
public class SellEntry {

    private final int itemCount;
    private final double moneyEarned;
    private final long timestamp; // Unix epoch seconds
    private final String materialName; // Material name of the primary item sold (for history GUI display)

    public SellEntry(int itemCount, double moneyEarned, long timestamp) {
        this(itemCount, moneyEarned, timestamp, null);
    }

    public SellEntry(int itemCount, double moneyEarned, long timestamp, String materialName) {
        this.itemCount = itemCount;
        this.moneyEarned = moneyEarned;
        this.timestamp = timestamp;
        this.materialName = materialName;
    }

    public SellEntry(int itemCount, double moneyEarned) {
        this(itemCount, moneyEarned, Instant.now().getEpochSecond(), null);
    }

    public SellEntry(int itemCount, double moneyEarned, String materialName) {
        this(itemCount, moneyEarned, Instant.now().getEpochSecond(), materialName);
    }

    public int getItemCount() {
        return itemCount;
    }

    public double getMoneyEarned() {
        return moneyEarned;
    }

    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Returns the material name of the primary item sold, or null if not recorded.
     * Used by the history GUI to display the actual item icon instead of PAPER.
     */
    public String getMaterialName() {
        return materialName;
    }

    /**
     * Formats the timestamp as a human-readable date/time string.
     */
    public String getFormattedTime() {
        java.time.LocalDateTime dt = java.time.LocalDateTime.ofEpochSecond(
                timestamp, 0, java.time.ZoneOffset.UTC);
        return dt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }
}
