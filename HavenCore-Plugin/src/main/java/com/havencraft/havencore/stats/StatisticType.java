package com.havencraft.havencore.stats;

public enum StatisticType {
    MONEY("Money"),
    PLAYTIME("Playtime"),
    PLAYER_KILLS("Player Kills"),
    DEATHS("Deaths"),
    BLOCKS_BROKEN("Blocks Broken"),
    BLOCKS_PLACED("Blocks Placed"),
    MOBS_KILLED("Mob Kills"),
    SHARDS("Shards"); // Optional

    private final String displayName;

    StatisticType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isIncremental() {
        return this != MONEY && this != SHARDS;
    }

    public static StatisticType fromString(String name) {
        for (StatisticType type : values()) {
            if (type.name().equalsIgnoreCase(name)) {
                return type;
            }
        }
        return null;
    }
}
