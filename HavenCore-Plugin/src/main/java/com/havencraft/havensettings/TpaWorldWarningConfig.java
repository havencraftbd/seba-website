package com.havencraft.havensettings;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Immutable parsed configuration for TPA/TPAHere world confirmation warnings and popups.
 */
public final class TpaWorldWarningConfig {

    private final boolean enabled;
    private final String mode;
    private final boolean tpaPopupEnabled;
    private final boolean tpaHerePopupEnabled;
    private final Map<String, String> worldDisplayNames;

    public TpaWorldWarningConfig(boolean enabled,
                                 String mode,
                                 boolean tpaPopupEnabled,
                                 boolean tpaHerePopupEnabled,
                                 Map<String, String> worldDisplayNames) {
        this.enabled = enabled;
        this.mode = mode;
        this.tpaPopupEnabled = tpaPopupEnabled;
        this.tpaHerePopupEnabled = tpaHerePopupEnabled;
        this.worldDisplayNames = Collections.unmodifiableMap(new HashMap<>(worldDisplayNames));
    }

    public static TpaWorldWarningConfig fromConfig(FileConfiguration config) {
        if (config == null) {
            return new TpaWorldWarningConfig(true, "DIALOG", true, true, Map.of(
                    "world_nether", "NETHER",
                    "world_the_end", "END",
                    "world_end", "END"
            ));
        }

        boolean enabled = config.getBoolean("tpa-world-warning.enabled", true);
        String rawMode = config.getString("tpa-world-warning.mode", "DIALOG");
        String normalizedMode;
        if (rawMode != null && rawMode.trim().equalsIgnoreCase("NOTIFY")) {
            normalizedMode = "NOTIFY";
        } else if (rawMode != null && rawMode.trim().equalsIgnoreCase("CONFIRM")) {
            normalizedMode = "CONFIRM";
        } else {
            normalizedMode = "DIALOG";
        }

        boolean tpaEnabled = config.contains("tpa-world-warning.popups.tpa.enabled")
                ? config.getBoolean("tpa-world-warning.popups.tpa.enabled", true)
                : config.getBoolean("tpa-world-warning.apply-to.tpa", true);

        boolean tpaHereEnabled = config.contains("tpa-world-warning.popups.tpahere.enabled")
                ? config.getBoolean("tpa-world-warning.popups.tpahere.enabled", true)
                : config.getBoolean("tpa-world-warning.apply-to.tpahere", true);

        Map<String, String> worlds = new HashMap<>();
        if (config.isConfigurationSection("tpa-world-warning.worlds")) {
            ConfigurationSection worldsSection = config.getConfigurationSection("tpa-world-warning.worlds");
            if (worldsSection != null) {
                for (String key : worldsSection.getKeys(false)) {
                    String trimmedKey = key.trim();
                    if (trimmedKey.isEmpty()) continue;
                    String display = worldsSection.getString(key + ".display-name", defaultDisplayName(trimmedKey));
                    worlds.put(trimmedKey.toLowerCase(Locale.ENGLISH),
                            (display == null || display.isBlank()) ? defaultDisplayName(trimmedKey) : display);
                }
            }
        } else if (config.isList("tpa-world-warning.worlds")) {
            for (String world : config.getStringList("tpa-world-warning.worlds")) {
                if (world != null && !world.trim().isEmpty()) {
                    String trimmed = world.trim();
                    worlds.put(trimmed.toLowerCase(Locale.ENGLISH), defaultDisplayName(trimmed));
                }
            }
        }

        return new TpaWorldWarningConfig(enabled, normalizedMode, tpaEnabled, tpaHereEnabled, worlds);
    }

    static String defaultDisplayName(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return "";
        }
        String lower = worldName.trim().toLowerCase(Locale.ENGLISH);
        if (lower.equals("world_nether")) {
            return "NETHER";
        }
        if (lower.equals("world_the_end") || lower.equals("world_end")) {
            return "END";
        }
        return worldName.trim().toUpperCase(Locale.ENGLISH);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getMode() {
        return mode;
    }

    public boolean isTpaPopupEnabled() {
        return tpaPopupEnabled;
    }

    public boolean isTpaHerePopupEnabled() {
        return tpaHerePopupEnabled;
    }

    public boolean isWarnedWorld(String worldName) {
        return worldName != null && worldDisplayNames.containsKey(worldName.toLowerCase(Locale.ENGLISH));
    }

    public String getWorldDisplayName(String worldName) {
        if (worldName == null) {
            return "";
        }
        String custom = worldDisplayNames.get(worldName.toLowerCase(Locale.ENGLISH));
        return custom != null ? custom : defaultDisplayName(worldName);
    }
}
