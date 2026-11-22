package com.havencraft.havenpunish.model;

/**
 * All supported punishment types. Each maps to a LiteBans command.
 */
public enum PunishAction {

    MUTE("mute", "Mute", false),
    TEMPMUTE("tempmute", "TempMute", true),
    BAN("ban", "Ban", false),
    TEMPBAN("tempban", "TempBan", true),
    IPBAN("ipban", "IP-Ban", false),
    WARN("warn", "Warn", false),
    KICK("kick", "Kick", false);

    private final String command;
    private final String displayName;
    private final boolean requiresDuration;

    PunishAction(String command, String displayName, boolean requiresDuration) {
        this.command = command;
        this.displayName = displayName;
        this.requiresDuration = requiresDuration;
    }

    /** The LiteBans command name (e.g. "tempban"). */
    public String getCommand() {
        return command;
    }

    /** Human-readable name for GUIs. */
    public String getDisplayName() {
        return displayName;
    }

    /** Whether this action needs a duration picker (temp punishments). */
    public boolean requiresDuration() {
        return requiresDuration;
    }

    /** The permission node required to use this action. */
    public String getPermission() {
        return switch (this) {
            case BAN, TEMPBAN -> "punish.ban";
            case MUTE, TEMPMUTE -> "punish.mute";
            case IPBAN -> "punish.ipban";
            case WARN -> "punish.warn";
            case KICK -> "punish.kick";
        };
    }
}
