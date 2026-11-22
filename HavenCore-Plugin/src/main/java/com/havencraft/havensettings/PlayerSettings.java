package com.havencraft.havensettings;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * POJO representing a single player's settings.
 */
public class PlayerSettings {

    private final UUID uuid;
    private boolean msgEnabled;
    private boolean tpaEnabled;
    private boolean tpaHereEnabled;
    private boolean tpaConfirmationEnabled;
    private boolean scoreboardEnabled;
    private boolean nightVisionEnabled;
    private boolean publicChatEnabled;
    private boolean publicChatMutedBySettings;

    /** Custom command toggles: commandId → enabled */
    private final Map<String, Boolean> customCommands = new LinkedHashMap<>();

    public PlayerSettings(UUID uuid) {
        this.uuid = uuid;
    }

    // ━━━━━━━━━━━━━━━ Getters / Setters ━━━━━━━━━━━━━━━

    public UUID getUuid() { return uuid; }

    public boolean isMsgEnabled() { return msgEnabled; }
    public void setMsgEnabled(boolean msgEnabled) { this.msgEnabled = msgEnabled; }

    public boolean isTpaEnabled() { return tpaEnabled; }
    public void setTpaEnabled(boolean tpaEnabled) { this.tpaEnabled = tpaEnabled; }

    public boolean isTpaHereEnabled() { return tpaHereEnabled; }
    public void setTpaHereEnabled(boolean tpaHereEnabled) { this.tpaHereEnabled = tpaHereEnabled; }

    public boolean isTpaConfirmationEnabled() { return tpaConfirmationEnabled; }
    public void setTpaConfirmationEnabled(boolean tpaConfirmationEnabled) { this.tpaConfirmationEnabled = tpaConfirmationEnabled; }

    public boolean isScoreboardEnabled() { return scoreboardEnabled; }
    public void setScoreboardEnabled(boolean scoreboardEnabled) { this.scoreboardEnabled = scoreboardEnabled; }

    public boolean isNightVisionEnabled() { return nightVisionEnabled; }
    public void setNightVisionEnabled(boolean nightVisionEnabled) { this.nightVisionEnabled = nightVisionEnabled; }

    public boolean isPublicChatEnabled() { return publicChatEnabled; }
    public void setPublicChatEnabled(boolean publicChatEnabled) { this.publicChatEnabled = publicChatEnabled; }

    public boolean isPublicChatMutedBySettings() { return publicChatMutedBySettings; }
    public void setPublicChatMutedBySettings(boolean publicChatMutedBySettings) { this.publicChatMutedBySettings = publicChatMutedBySettings; }

    // ━━━━━━━━━━━━━━━ Custom Commands ━━━━━━━━━━━━━━━

    public boolean isCustomCommandEnabled(String id) {
        return customCommands.getOrDefault(id, false);
    }

    public void setCustomCommand(String id, boolean enabled) {
        customCommands.put(id, enabled);
    }

    public boolean hasCustomCommand(String id) {
        return customCommands.containsKey(id);
    }

    public Map<String, Boolean> getCustomCommands() {
        return customCommands;
    }
}
