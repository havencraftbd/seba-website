package com.haven.sell.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * POJO representing a player's sell statistics and transaction history.
 */
public class PlayerData {

    private final UUID uuid;
    private final String playerName;
    private int totalItemsSold;
    private double totalMoneyEarned;

    /** Ordered list of sell entries, oldest first. */
    private final List<SellEntry> history;

    /** Max number of history entries to keep per player. */
    public static final int MAX_HISTORY = 50;

    public PlayerData(UUID uuid, String playerName) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.totalItemsSold = 0;
        this.totalMoneyEarned = 0.0;
        this.history = new ArrayList<>();
    }

    public PlayerData(UUID uuid, String playerName, int totalItemsSold, double totalMoneyEarned, List<SellEntry> history) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.totalItemsSold = totalItemsSold;
        this.totalMoneyEarned = totalMoneyEarned;
        this.history = history != null ? new ArrayList<>(history) : new ArrayList<>();
    }

    // ━━━━━━━━━━━━━━━ Getters ━━━━━━━━━━━━━━━

    public UUID getUuid() { return uuid; }
    public String getPlayerName() { return playerName; }
    public int getTotalItemsSold() { return totalItemsSold; }
    public double getTotalMoneyEarned() { return totalMoneyEarned; }

    /** Returns an unmodifiable view of the sell history (oldest first). */
    public List<SellEntry> getHistory() {
        return Collections.unmodifiableList(history);
    }

    // ━━━━━━━━━━━━━━━ Mutators ━━━━━━━━━━━━━━━

    /**
     * Adds to the sell statistics and appends a history entry.
     */
    public void addSellRecord(int itemCount, double moneyEarned) {
        addSellRecord(itemCount, moneyEarned, (String) null);
    }

    /**
     * Adds to the sell statistics and appends a history entry with material tracking.
     */
    public void addSellRecord(int itemCount, double moneyEarned, String materialName) {
        this.totalItemsSold += itemCount;
        this.totalMoneyEarned += moneyEarned;

        // Append history entry
        history.add(new SellEntry(itemCount, moneyEarned, materialName));

        // Trim to max size (remove oldest entries)
        while (history.size() > MAX_HISTORY) {
            history.remove(0);
        }
    }

    public void setTotalItemsSold(int totalItemsSold) { this.totalItemsSold = totalItemsSold; }
    public void setTotalMoneyEarned(double totalMoneyEarned) { this.totalMoneyEarned = totalMoneyEarned; }
}
