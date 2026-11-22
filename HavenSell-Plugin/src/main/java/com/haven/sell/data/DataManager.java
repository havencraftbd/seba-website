package com.haven.sell.data;

import java.util.UUID;

/**
 * Interface for player data storage operations.
 * Implementations can use YAML flat files or MySQL.
 */
public interface DataManager {

    /**
     * Loads player data from storage.
     *
     * @param uuid       The player's UUID
     * @param playerName The player's name (used for YAML filenames)
     * @return The loaded PlayerData, or a new empty one if not found
     */
    PlayerData loadPlayerData(UUID uuid, String playerName);

    /**
     * Loads player data by name only (for offline player lookup).
     * Used by admin history command.
     *
     * @param playerName The player name to search for
     * @return PlayerData if found, or null if no data exists
     */
    PlayerData loadPlayerDataByName(String playerName);

    /**
     * Saves player data to storage asynchronously.
     *
     * @param data The player data to save
     */
    void savePlayerData(PlayerData data);

    /**
     * Adds a sell record to the player's statistics and saves.
     *
     * @param uuid       The player's UUID
     * @param playerName The player's name
     * @param itemCount  Number of items sold
     * @param amount     Money earned
     */
    default void addSellRecord(UUID uuid, String playerName, int itemCount, double amount) {
        addSellRecord(uuid, playerName, itemCount, amount, null);
    }

    /**
     * Adds a sell record to the player's statistics and saves.
     *
     * @param uuid         The player's UUID
     * @param playerName   The player's name
     * @param itemCount    Number of items sold
     * @param amount       Money earned
     * @param materialName Material name of the primary sold item (for history display), or null
     */
    void addSellRecord(UUID uuid, String playerName, int itemCount, double amount, String materialName);

    /**
     * Unloads player data from memory (clears from cache).
     *
     * @param uuid The player's UUID
     */
    void unloadPlayerData(UUID uuid);

    /**
     * Shuts down the data manager and releases resources.
     */
    void shutdown();
}
