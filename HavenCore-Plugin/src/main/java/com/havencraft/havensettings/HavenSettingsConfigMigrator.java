package com.havencraft.havensettings;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Migrates legacy (list-based) or ConfigUpdater-corrupted HavenSettings/config.yml files
 * to the new tpa-world-warning map schema before ConfigLoader/ConfigUpdater runs.
 */
public final class HavenSettingsConfigMigrator {

    private HavenSettingsConfigMigrator() {
    }

    public static boolean migrateIfNeeded(File configFile, Logger logger) {
        if (configFile == null || !configFile.isFile()) {
            return false;
        }

        String rawContent;
        try {
            rawContent = Files.readString(configFile.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            if (logger != null) {
                logger.warning("[Settings] Could not inspect config.yml for migration: " + e.getMessage());
            }
            return false;
        }

        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(rawContent);
            if (!needsSchemaMigration(yaml)) {
                return false;
            }
            applySchemaMigration(yaml, List.of());
            yaml.save(configFile);
            if (logger != null) {
                logger.info("[Settings] Migrated legacy HavenSettings/config.yml tpa-world-warning layout.");
            }
            return true;
        } catch (InvalidConfigurationException parseFailure) {
            RepairResult repaired = repairCorruptedWorldsBlock(rawContent);
            if (!repaired.modified) {
                return false;
            }
            YamlConfiguration repairedYaml = new YamlConfiguration();
            try {
                repairedYaml.loadFromString(repaired.content);
                applySchemaMigration(repairedYaml, repaired.salvagedWorlds);
                repairedYaml.save(configFile);
                if (logger != null) {
                    logger.info("[Settings] Repaired and migrated HavenSettings/config.yml tpa-world-warning layout.");
                }
                return true;
            } catch (InvalidConfigurationException | IOException repairFailure) {
                if (logger != null) {
                    logger.warning("[Settings] Automatic repair of HavenSettings/config.yml did not succeed: "
                            + repairFailure.getMessage());
                }
                return false;
            }
        } catch (IOException saveFailure) {
            if (logger != null) {
                logger.warning("[Settings] Failed to save migrated HavenSettings/config.yml: " + saveFailure.getMessage());
            }
            return false;
        }
    }

    public static boolean hasTpaWarningWorldsSection(File configFile) {
        if (configFile == null || !configFile.isFile()) {
            return false;
        }
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(configFile);
            return yaml.isConfigurationSection("tpa-world-warning.worlds");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean needsSchemaMigration(YamlConfiguration yaml) {
        return yaml.isList("tpa-world-warning.worlds")
                || yaml.isConfigurationSection("tpa-world-warning.apply-to");
    }

    private static void applySchemaMigration(YamlConfiguration yaml, List<String> extraWorlds) {
        if (yaml.isConfigurationSection("tpa-world-warning.apply-to")) {
            if (!yaml.contains("tpa-world-warning.popups.tpa.enabled")) {
                yaml.set("tpa-world-warning.popups.tpa.enabled",
                        yaml.getBoolean("tpa-world-warning.apply-to.tpa", true));
            }
            if (!yaml.contains("tpa-world-warning.popups.tpahere.enabled")) {
                yaml.set("tpa-world-warning.popups.tpahere.enabled",
                        yaml.getBoolean("tpa-world-warning.apply-to.tpahere", true));
            }
            yaml.set("tpa-world-warning.apply-to", null);
        }

        Set<String> worldsToEnsure = new LinkedHashSet<>(extraWorlds);
        if (yaml.isList("tpa-world-warning.worlds")) {
            for (String item : yaml.getStringList("tpa-world-warning.worlds")) {
                if (item != null && !item.isBlank()) {
                    worldsToEnsure.add(item.trim());
                }
            }
            yaml.set("tpa-world-warning.worlds", null);
        }

        if (!worldsToEnsure.isEmpty()) {
            Set<String> existingLower = new LinkedHashSet<>();
            if (yaml.isConfigurationSection("tpa-world-warning.worlds")) {
                for (String key : yaml.getConfigurationSection("tpa-world-warning.worlds").getKeys(false)) {
                    existingLower.add(key.toLowerCase(Locale.ENGLISH));
                }
            }
            for (String worldName : worldsToEnsure) {
                if (!existingLower.contains(worldName.toLowerCase(Locale.ENGLISH))) {
                    yaml.set("tpa-world-warning.worlds." + worldName + ".display-name",
                            TpaWorldWarningConfig.defaultDisplayName(worldName));
                    existingLower.add(worldName.toLowerCase(Locale.ENGLISH));
                }
            }
        }
    }

    private record RepairResult(boolean modified, String content, List<String> salvagedWorlds) {}

    private static RepairResult repairCorruptedWorldsBlock(String rawContent) {
        String[] lines = rawContent.split("\\R", -1);
        List<String> outLines = new ArrayList<>(lines.length);
        List<String> salvagedWorlds = new ArrayList<>();
        boolean modified = false;

        int tpaSectionIndent = -1;
        int worldsIndent = -1;

        for (String line : lines) {
            String trimmed = line.trim();
            boolean isIgnorable = trimmed.isEmpty() || trimmed.startsWith("#");
            int indent = countLeadingSpaces(line);

            if (!isIgnorable) {
                if (tpaSectionIndent == -1) {
                    if (isYamlKey(trimmed, "tpa-world-warning")) {
                        tpaSectionIndent = indent;
                    }
                } else if (indent <= tpaSectionIndent) {
                    tpaSectionIndent = isYamlKey(trimmed, "tpa-world-warning") ? indent : -1;
                    worldsIndent = -1;
                } else if (worldsIndent == -1) {
                    if (isYamlKey(trimmed, "worlds")) {
                        worldsIndent = indent;
                    }
                } else if (indent < worldsIndent
                        || (indent == worldsIndent && !isSequenceItem(trimmed))) {
                    worldsIndent = isYamlKey(trimmed, "worlds") ? indent : -1;
                } else if (isSequenceItem(trimmed)) {
                    String extracted = extractListValue(trimmed);
                    if (!extracted.isEmpty()) {
                        salvagedWorlds.add(extracted);
                    }
                    modified = true;
                    continue;
                }
            }

            outLines.add(line);
        }

        return new RepairResult(modified, String.join("\n", outLines), salvagedWorlds);
    }

    private static boolean isSequenceItem(String trimmedLine) {
        return trimmedLine.startsWith("- ") || trimmedLine.equals("-");
    }

    private static boolean isYamlKey(String trimmedLine, String keyName) {
        String prefix = keyName + ":";
        if (!trimmedLine.startsWith(prefix)) {
            return false;
        }
        if (trimmedLine.length() == prefix.length()) {
            return true;
        }
        char next = trimmedLine.charAt(prefix.length());
        return next == ' ' || next == '\t' || next == '#';
    }

    private static String extractListValue(String trimmedSequenceLine) {
        String value = trimmedSequenceLine.length() > 1 ? trimmedSequenceLine.substring(1).trim() : "";
        int hashIndex = value.indexOf('#');
        if (hashIndex >= 0 && !value.startsWith("\"") && !value.startsWith("'")) {
            value = value.substring(0, hashIndex).trim();
        }
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            value = value.substring(1, value.length() - 1).trim();
        }
        return value;
    }

    private static int countLeadingSpaces(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    /**
     * Inserts the default {@code buttons.tpa-confirmation} toggle (slot 13) into an existing
     * {@code HavenSettings/gui.yml} if it is missing and slot 13 is currently unoccupied.
     * Preserves all existing button customizations and YAML comments.
     */
    public static boolean migrateGuiIfNeeded(File guiFile, Logger logger) {
        if (guiFile == null || !guiFile.isFile()) {
            return false;
        }

        String rawContent;
        try {
            rawContent = Files.readString(guiFile.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            if (logger != null) {
                logger.warning("[Settings] Could not inspect gui.yml for migration: " + e.getMessage());
            }
            return false;
        }

        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(rawContent);
        } catch (InvalidConfigurationException e) {
            if (logger != null) {
                logger.warning("[Settings] Skipping gui.yml migration because existing YAML is invalid: " + e.getMessage());
            }
            return false;
        }

        ConfigurationSection buttons = yaml.getConfigurationSection("buttons");
        if (buttons == null
                || buttons.isConfigurationSection("tpa-confirmation")
                || isSlotOccupied(buttons, 13)
                || yaml.getInt("close.slot", -1) == 13) {
            return false;
        }

        List<String> blockLines = loadDefaultGuiButtonBlock("tpa-confirmation");
        if (blockLines.isEmpty()) {
            return false;
        }

        String lineSep = rawContent.contains("\r\n") ? "\r\n" : "\n";
        String updatedContent = insertButtonBlockPreservingComments(rawContent, blockLines, "tpa", lineSep);
        if (updatedContent == null) {
            return false;
        }

        YamlConfiguration verify = new YamlConfiguration();
        try {
            verify.loadFromString(updatedContent);
            if (!verify.isConfigurationSection("buttons.tpa-confirmation")) {
                return false;
            }
            Files.writeString(guiFile.toPath(), updatedContent, StandardCharsets.UTF_8);
            if (logger != null) {
                logger.info("[Settings] Added 'tpa-confirmation' toggle button (slot 13) to HavenSettings/gui.yml.");
            }
            return true;
        } catch (InvalidConfigurationException | IOException e) {
            if (logger != null) {
                logger.warning("[Settings] Could not migrate HavenSettings/gui.yml: " + e.getMessage());
            }
            return false;
        }
    }

    private static boolean isSlotOccupied(ConfigurationSection buttons, int targetSlot) {
        for (String key : buttons.getKeys(false)) {
            ConfigurationSection buttonSection = buttons.getConfigurationSection(key);
            if (buttonSection != null && buttonSection.getInt("slot", -1) == targetSlot) {
                return true;
            }
        }
        return false;
    }

    private static List<String> loadDefaultGuiButtonBlock(String buttonKey) {
        try (InputStream stream = HavenSettingsConfigMigrator.class.getClassLoader()
                .getResourceAsStream("HavenSettings/gui.yml")) {
            if (stream == null) {
                return List.of();
            }
            String defaultGui = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return extractButtonBlock(defaultGui, buttonKey);
        } catch (IOException e) {
            return List.of();
        }
    }

    private static List<String> extractButtonBlock(String yamlContent, String buttonKey) {
        String[] lines = yamlContent.split("\\R", -1);
        int buttonsIndent = -1;
        int targetIndent = -1;
        List<String> captured = new ArrayList<>();

        for (String line : lines) {
            String trimmed = line.trim();
            boolean isIgnorable = trimmed.isEmpty() || trimmed.startsWith("#");
            int indent = countLeadingSpaces(line);

            if (buttonsIndent == -1) {
                if (!isIgnorable && isYamlKey(trimmed, "buttons")) {
                    buttonsIndent = indent;
                }
                continue;
            }

            if (targetIndent == -1) {
                if (!isIgnorable && indent <= buttonsIndent) {
                    break;
                }
                if (!isIgnorable && isYamlKey(trimmed, buttonKey)) {
                    targetIndent = indent;
                    captured.add(line);
                }
                continue;
            }

            if (!isIgnorable && indent <= targetIndent) {
                break;
            }
            if (trimmed.startsWith("#") && indent <= targetIndent) {
                break;
            }
            captured.add(line);
        }

        while (!captured.isEmpty() && captured.get(captured.size() - 1).trim().isEmpty()) {
            captured.remove(captured.size() - 1);
        }
        return captured;
    }

    private static String insertButtonBlockPreservingComments(
            String rawContent,
            List<String> sourceBlock,
            String insertBeforeKey,
            String lineSep
    ) {
        String[] lines = rawContent.split("\\R", -1);
        int buttonsLineIdx = -1;
        int buttonsIndent = -1;
        int childIndent = -1;
        int insertBeforeIdx = -1;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            boolean isIgnorable = trimmed.isEmpty() || trimmed.startsWith("#");
            int indent = countLeadingSpaces(line);

            if (buttonsLineIdx == -1) {
                if (!isIgnorable && isYamlKey(trimmed, "buttons")) {
                    buttonsLineIdx = i;
                    buttonsIndent = indent;
                }
                continue;
            }

            if (isIgnorable) {
                continue;
            }
            if (indent <= buttonsIndent) {
                break;
            }
            if (childIndent == -1) {
                childIndent = indent;
            }
            if (indent == childIndent && isYamlKey(trimmed, insertBeforeKey)) {
                insertBeforeIdx = i;
                break;
            }
        }

        if (buttonsLineIdx == -1) {
            return null;
        }

        int targetIndent = childIndent != -1 ? childIndent : buttonsIndent + 2;
        int sourceIndent = countLeadingSpaces(sourceBlock.get(0));
        int insertAt = insertBeforeIdx != -1 ? insertBeforeIdx : buttonsLineIdx + 1;

        List<String> outLines = new ArrayList<>(lines.length + sourceBlock.size());
        for (int i = 0; i < insertAt; i++) {
            outLines.add(lines[i]);
        }
        for (String blockLine : sourceBlock) {
            if (blockLine.trim().isEmpty()) {
                outLines.add("");
            } else {
                int relativeIndent = Math.max(0, countLeadingSpaces(blockLine) - sourceIndent);
                outLines.add(" ".repeat(targetIndent + relativeIndent) + blockLine.trim());
            }
        }
        for (int i = insertAt; i < lines.length; i++) {
            outLines.add(lines[i]);
        }

        return String.join(lineSep, outLines);
    }
}
