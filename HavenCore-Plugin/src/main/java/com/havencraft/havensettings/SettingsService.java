package com.havencraft.havensettings;

import java.util.UUID;

/**
 * Public API interface for HavenSettings.
 * Other modules can call these methods to check player settings.
 */
public interface SettingsService {

    boolean isMsgEnabled(UUID uuid);

    boolean isTpaEnabled(UUID uuid);

    boolean isTpaHereEnabled(UUID uuid);

    boolean isTpaConfirmationEnabled(UUID uuid);

    boolean isScoreboardEnabled(UUID uuid);

    boolean isNightVisionEnabled(UUID uuid);

    boolean isPublicChatEnabled(UUID uuid);

    boolean isCustomCommandEnabled(UUID uuid, String commandId);
}
