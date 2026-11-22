package com.havencraft.leaderboard.data;

import java.util.UUID;

public class PlayerStat {
    private final UUID uuid;
    private final String name;
    private final double value;
    private final String formattedValue;
    private String skinUrl;

    public PlayerStat(UUID uuid, String name, double value, String formattedValue, String skinUrl) {
        this.uuid = uuid;
        this.name = name;
        this.value = value;
        this.formattedValue = formattedValue;
        this.skinUrl = skinUrl;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public double getValue() {
        return value;
    }

    public String getFormattedValue() {
        return formattedValue;
    }

    public String getSkinUrl() {
        return skinUrl;
    }

    public void setSkinUrl(String skinUrl) {
        this.skinUrl = skinUrl;
    }
}
