package com.havencraft.havencore.adminhome;

import java.util.UUID;

/**
 * Stores the context session for an administrator browsing or managing player homes or team homes.
 */
public class AdminSession {
    private final UUID adminUuid;
    private final UUID targetUuid;
    private final String targetName;
    private String currentHome;

    // Team Home Support
    private boolean isTeamHome;
    private String teamName;
    private Object teamObject;
    private boolean directTeamMode;

    public AdminSession(UUID adminUuid, UUID targetUuid, String targetName) {
        this.adminUuid = adminUuid;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.isTeamHome = false;
        this.directTeamMode = false;
    }

    public AdminSession(UUID adminUuid, String teamName, Object teamObject) {
        this.adminUuid = adminUuid;
        this.targetUuid = null;
        this.targetName = teamName;
        this.teamName = teamName;
        this.teamObject = teamObject;
        this.currentHome = teamName;
        this.isTeamHome = true;
        this.directTeamMode = true;
    }

    public UUID getAdminUuid() {
        return adminUuid;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public String getTargetName() {
        return targetName;
    }

    public String getCurrentHome() {
        return currentHome;
    }

    public void setCurrentHome(String currentHome) {
        this.currentHome = currentHome;
    }

    public boolean isTeamHome() {
        return isTeamHome;
    }

    public void setTeamHome(boolean teamHome) {
        this.isTeamHome = teamHome;
    }

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public Object getTeamObject() {
        return teamObject;
    }

    public void setTeamObject(Object teamObject) {
        this.teamObject = teamObject;
    }

    public boolean isDirectTeamMode() {
        return directTeamMode;
    }

    public void setDirectTeamMode(boolean directTeamMode) {
        this.directTeamMode = directTeamMode;
    }
}
