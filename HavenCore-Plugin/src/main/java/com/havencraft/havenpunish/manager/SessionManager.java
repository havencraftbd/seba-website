package com.havencraft.havenpunish.manager;

import com.havencraft.havenpunish.model.PunishSession;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active punishment sessions (one per staff member).
 * All operations are O(1) with ConcurrentHashMap — zero TPS impact.
 */
public class SessionManager {

    /** Key = staff player UUID, Value = their active punishment session. */
    private final Map<UUID, PunishSession> sessions = new ConcurrentHashMap<>();

    /**
     * Creates and stores a new punishment session.
     * Replaces any existing session for this staff member.
     */
    public PunishSession createSession(UUID staffUuid, String targetName, UUID targetUuid) {
        PunishSession session = new PunishSession(staffUuid, targetName, targetUuid);
        sessions.put(staffUuid, session);
        return session;
    }

    /**
     * Gets the active session for a staff member, if any.
     */
    public PunishSession getSession(UUID staffUuid) {
        return sessions.get(staffUuid);
    }

    /**
     * Removes the session for a staff member.
     */
    public void removeSession(UUID staffUuid) {
        sessions.remove(staffUuid);
    }

    /**
     * Checks if a staff member has an active session.
     */
    public boolean hasSession(UUID staffUuid) {
        return sessions.containsKey(staffUuid);
    }

    /**
     * Clears all sessions (used on plugin disable/reload).
     */
    public void clearAll() {
        sessions.clear();
    }
}
