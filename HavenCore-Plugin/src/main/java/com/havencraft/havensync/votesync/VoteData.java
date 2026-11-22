package com.havencraft.havensync.votesync;

/**
 * Basic container for a pending vote to be batched.
 */
public class VoteData {
    private final java.util.UUID playerUuid;
    private final String username;
    private final String site;

    public VoteData(java.util.UUID playerUuid, String username, String site) {
        this.playerUuid = playerUuid;
        this.username = username;
        this.site = site;
    }

    public java.util.UUID getPlayerUuid() { return playerUuid; }
    public String getUsername() { return username; }
    public String getSite() { return site; }
}
