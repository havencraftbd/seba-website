package com.havencraft.havensync.votesync;

import com.vexsoftware.votifier.model.Vote;
import com.vexsoftware.votifier.model.VotifierEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

/**
 * Listens for Votifier events and caches them into a thread-safe queue.
 */
public class VoteListener implements Listener {

    private final org.bukkit.plugin.Plugin plugin;
    private final Logger logger;
    private final int duplicateWindowSeconds;
    private final Map<String, Long> recentVotes = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<VoteData> pendingVotes = new ConcurrentLinkedQueue<>();
    private final Map<String, java.util.UUID> resolvedUuidCache = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<MojangRequest> mojangQueue = new ConcurrentLinkedQueue<>();
    private boolean isProcessingQueue = false;

    private static class MojangRequest {
        final String username;
        final String site;
        MojangRequest(String username, String site) {
            this.username = username;
            this.site = site;
        }
    }

    public VoteListener(org.bukkit.plugin.Plugin plugin, Logger logger, int duplicateWindowSeconds) {
        this.plugin = plugin;
        this.logger = logger;
        this.duplicateWindowSeconds = duplicateWindowSeconds;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onVote(VotifierEvent event) {
        Vote vote = event.getVote();
        String username = vote.getUsername();
        String rawSite = vote.getServiceName();

        if (username == null || username.isBlank()) {
            logger.warning("[VoteSync] Empty username received.");
            return;
        }

        String site = normaliseSiteName(rawSite);
        if (duplicateWindowSeconds > 0 && isDuplicate(username, site)) {
            return;
        }

        // Resolve UUID on the main thread first (fast and safe for online/cached players)
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            org.bukkit.entity.Player onlinePlayer = org.bukkit.Bukkit.getPlayerExact(username);
            java.util.UUID uuid = null;
            if (onlinePlayer != null) {
                uuid = onlinePlayer.getUniqueId();
            } else {
                org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayerIfCached(username);
                if (op != null) {
                    uuid = op.getUniqueId();
                } else {
                    // Check local memory cache
                    uuid = resolvedUuidCache.get(username.toLowerCase());
                }
            }
            
            if (uuid != null) {
                pendingVotes.add(new VoteData(uuid, username, site));
            } else {
                // Not cached/online, add to the throttled Mojang queue to resolve asynchronously
                mojangQueue.add(new MojangRequest(username, site));
                startQueueProcessor();
            }
        });
    }

    private void startQueueProcessor() {
        synchronized (mojangQueue) {
            if (isProcessingQueue) return;
            isProcessingQueue = true;
        }
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                while (true) {
                    MojangRequest req = mojangQueue.poll();
                    if (req == null) break;
                    
                    // Double check cache
                    java.util.UUID uuid = resolvedUuidCache.get(req.username.toLowerCase());
                    if (uuid == null) {
                        uuid = fetchUuidFromMojang(req.username);
                        if (uuid == null && !org.bukkit.Bukkit.getOnlineMode()) {
                            uuid = java.util.UUID.nameUUIDFromBytes(("OfflinePlayer:" + req.username).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        }
                        if (uuid != null) {
                            // Prevent cache from growing infinitely
                            if (resolvedUuidCache.size() < 10000) {
                                resolvedUuidCache.put(req.username.toLowerCase(), uuid);
                            }
                        }
                    }
                    
                    if (uuid != null) {
                        pendingVotes.add(new VoteData(uuid, req.username, req.site));
                    } else {
                        logger.warning("[VoteSync] Could not resolve UUID for offline player: " + req.username + ". Vote skipped to prevent database insertion error.");
                    }
                    
                    // Sleep for 1 second to throttle Mojang API requests and prevent rate-limiting
                    try {
                        Thread.sleep(1000L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } finally {
                synchronized (mojangQueue) {
                    isProcessingQueue = false;
                }
            }
        });
    }

    private java.util.UUID fetchUuidFromMojang(String username) {
        try {
            java.net.URL url = new java.net.URL("https://api.mojang.com/users/profiles/minecraft/" + username);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            
            if (conn.getResponseCode() == 200) {
                try (java.io.InputStreamReader reader = new java.io.InputStreamReader(conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8)) {
                    com.google.gson.JsonObject json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                    if (json.has("id")) {
                        String idStr = json.get("id").getAsString();
                        String formatted = idStr.replaceFirst(
                            "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})",
                            "$1-$2-$3-$4-$5"
                        );
                        return java.util.UUID.fromString(formatted);
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Drains the queue destructively for the HTTP client.
     */
    public List<VoteData> drainCache() {
        List<VoteData> snapshot = new ArrayList<>();
        VoteData v;
        while ((v = pendingVotes.poll()) != null) {
            snapshot.add(v);
        }
        return snapshot;
    }

    private String normaliseSiteName(String raw) {
        if (raw == null || raw.isBlank()) return "unknown";
        String cleaned = raw.trim().toLowerCase();
        cleaned = cleaned.replaceAll("\\.(com|org|net|io|gg|co|me|us|uk|de)$", "");
        cleaned = cleaned.replaceAll("\\s+", "");
        return cleaned.isEmpty() ? "unknown" : cleaned;
    }

    private boolean isDuplicate(String username, String site) {
        String key = username.toLowerCase() + ":" + site;
        long now = System.currentTimeMillis();
        long windowMs = duplicateWindowSeconds * 1000L;

        Long last = recentVotes.get(key);
        if (last != null && (now - last) < windowMs) {
            return true;
        }
        recentVotes.put(key, now);
        
        if (recentVotes.size() > 500) {
            recentVotes.entrySet().removeIf(e -> (now - e.getValue()) > windowMs * 2);
        }
        return false;
    }
}
