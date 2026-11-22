package com.havencraft.havenpunish.model;

import java.util.UUID;

/**
 * Tracks the in-progress punishment flow for a single staff member.
 * Stored in memory only — no persistence needed.
 */
public class PunishSession {

    private final UUID staffUuid;
    private final String targetName;
    private final UUID targetUuid;

    private volatile PunishAction action;
    private volatile String duration;    // e.g. "7d", null for permanent
    private volatile String reason;      // e.g. "Hacking/Cheating"
    private volatile boolean silent;     // -s flag

    /** Tracks what chat input the staff is expected to type next. */
    private volatile ChatInputType pendingChatInput;
    private volatile long lastActivityTime = System.currentTimeMillis();

    public enum ChatInputType {
        CUSTOM_DURATION,
        CUSTOM_REASON
    }

    public PunishSession(UUID staffUuid, String targetName, UUID targetUuid) {
        this.staffUuid = staffUuid;
        this.targetName = targetName;
        this.targetUuid = targetUuid;
        this.silent = false;
    }

    // ━━━━━━━━━ Getters & Setters ━━━━━━━━━

    public long getLastActivityTime() {
        return lastActivityTime;
    }

    public void updateActivityTime() {
        this.lastActivityTime = System.currentTimeMillis();
    }

    public UUID getStaffUuid() {
        return staffUuid;
    }

    public String getTargetName() {
        return targetName;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public PunishAction getAction() {
        return action;
    }

    public void setAction(PunishAction action) {
        this.action = action;
        updateActivityTime();
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        if (duration != null) {
            this.duration = duration.replaceAll("[^a-zA-Z0-9]", "").trim();
        } else {
            this.duration = null;
        }
        updateActivityTime();
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        if (reason != null) {
            String sanitized = reason.replaceAll("[^a-zA-Z0-9\\s.,!?/()'&_#:-]", "").trim();
            // Disallow command flag injection: strip leading dashes from all tokens (e.g., --sender, -s, -p)
            String[] tokens = sanitized.split("\\s+");
            StringBuilder clean = new StringBuilder();
            for (String token : tokens) {
                if (token.startsWith("-")) {
                    token = token.replaceFirst("^-+", "");
                    if (token.isEmpty()) continue;
                }
                if (!clean.isEmpty()) clean.append(" ");
                clean.append(token);
            }
            this.reason = clean.toString().trim();
            if (this.reason.length() > 100) {
                this.reason = this.reason.substring(0, 100);
            }
        } else {
            this.reason = null;
        }
        updateActivityTime();
    }

    public boolean isSilent() {
        return silent;
    }

    public void setSilent(boolean silent) {
        this.silent = silent;
    }

    public ChatInputType getPendingChatInput() {
        return pendingChatInput;
    }

    public void setPendingChatInput(ChatInputType pendingChatInput) {
        this.pendingChatInput = pendingChatInput;
        updateActivityTime();
    }

    /**
     * Builds the final LiteBans command string.
     * Examples:
     *   /ban PlayerName Hacking
     *   /tempban -s PlayerName 7d Cheating
     *   /tempmute --sender=JU3L_ --sender-uuid=... Zipp_404 15m Spam
     */
    public String buildCommand(org.bukkit.entity.Player staff) {
        // Strict safety guard against wildcard or flag injection
        if (targetName == null || targetName.contains("*") || targetName.startsWith("-") ||
                !targetName.matches("^[a-zA-Z0-9_.][a-zA-Z0-9_.-]{2,19}$")) {
            throw new IllegalStateException("Security violation: Invalid target player name: " + targetName);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(action.getCommand());

        if (silent) {
            sb.append(" -s");
        }

        if (staff != null) {
            String cleanStaffName = staff.getName().replaceAll("[^a-zA-Z0-9_.]", "");
            sb.append(" --sender=").append(cleanStaffName);
            sb.append(" --sender-uuid=").append(staff.getUniqueId().toString());
        }

        sb.append(" ").append(targetName);

        if (action.requiresDuration() && duration != null) {
            String cleanDuration = duration.replaceAll("[^a-zA-Z0-9]", "");
            sb.append(" ").append(cleanDuration);
        }

        if (reason != null && !reason.isEmpty()) {
            sb.append(" ").append(reason);
        }

        return sb.toString();
    }
}
