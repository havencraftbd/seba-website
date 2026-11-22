package com.havencraft.havensync.leaderboardsync;

import okhttp3.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.havencraft.havensync.util.SupabaseUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import com.havencraft.havencore.stats.PlayerStats;
import com.havencraft.havencore.stats.StatisticType;

/**
 * Handles batch upserting leaderboard states to Supabase with exponential backoff
 * and watchdog-safe shutdown execution.
 */
public class LeaderboardHttpClient {

    private final String url;
    private final String apiKey;
    private final String conflictKey;
    private final int maxBatchSize;
    private final Logger logger;
    private final boolean debug;
    private final OkHttpClient client;
    private final Map<String, String> columnMap;

    private static final int MAX_RETRY_QUEUE = 2000;
    private static final long INITIAL_BACKOFF_MS = 5000L;  // 5 seconds base
    private static final long MAX_BACKOFF_MS = 300000L;     // 5 minutes max
    private static final int MAX_SHUTDOWN_CHUNKS = 2;       // Cap shutdown chunks to prevent watchdog hang

    private final java.util.concurrent.ConcurrentLinkedQueue<PlayerStats> retryQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile long backoffUntil = 0L;
    private volatile long lastWarningLogTime = 0L;

    void requeue(List<PlayerStats> items) {
        if (items == null || items.isEmpty()) return;
        if (retryQueue.size() >= MAX_RETRY_QUEUE) {
            logger.severe("[LeaderboardSync] Retry queue full (" + MAX_RETRY_QUEUE + ")! Discarding " + items.size() + " entries to prevent OOM.");
            return;
        }
        for (PlayerStats stats : items) {
            if (stats != null && retryQueue.size() < MAX_RETRY_QUEUE) {
                retryQueue.add(stats);
            }
        }
    }

    public LeaderboardHttpClient(String baseUrl, String tableName, String apiKey, String conflictKey, int connectTimeout, int readTimeout, int maxBatchSize, Logger logger, boolean debug, Map<String, String> columnMap) {
        // Build the full URL: baseUrl + /rest/v1/ + tableName
        // User only needs to provide the project URL (e.g. https://xxx.supabase.co)
        this.url = SupabaseUtil.buildSupabaseUrl(baseUrl, tableName);
        this.apiKey = apiKey;
        this.conflictKey = (conflictKey != null && !conflictKey.isEmpty()) ? conflictKey : "uuid";
        this.maxBatchSize = maxBatchSize;
        this.logger = logger;
        this.debug = debug;
        this.columnMap = columnMap;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(connectTimeout, TimeUnit.MILLISECONDS)
                .readTimeout(readTimeout, TimeUnit.MILLISECONDS)
                .build();
    }

    public boolean isBackedOff() {
        return System.currentTimeMillis() < backoffUntil;
    }

    public void setBackoffForTesting(long backoffUntilMs) {
        this.backoffUntil = backoffUntilMs;
    }

    private void handleSuccess() {
        int prev = consecutiveFailures.getAndSet(0);
        backoffUntil = 0L;
        if (prev > 0) {
            logger.info("[LeaderboardSync] Connection restored to Supabase. Leaderboard sync resumed successfully.");
        }
    }

    private void handleFailure(String reason) {
        int failures = consecutiveFailures.incrementAndGet();
        applyBackoff(failures, -1, reason);
    }

    private void handleHttpError(int code, String retryAfterHeader) {
        int failures = consecutiveFailures.incrementAndGet();
        long retryAfterSec = -1;
        if (code == 429 && retryAfterHeader != null) {
            try {
                retryAfterSec = Long.parseLong(retryAfterHeader.trim());
            } catch (NumberFormatException ignored) {}
        }
        applyBackoff(failures, retryAfterSec, "HTTP " + code);
    }

    private void applyBackoff(int failures, long retryAfterSec, String reason) {
        long delayMs;
        if (retryAfterSec > 0) {
            delayMs = Math.min(retryAfterSec * 1000L, MAX_BACKOFF_MS);
        } else {
            int shift = Math.min(failures - 1, 6);
            delayMs = Math.min(INITIAL_BACKOFF_MS * (1L << shift), MAX_BACKOFF_MS);
        }
        backoffUntil = System.currentTimeMillis() + delayMs;
        logger.warning("[LeaderboardSync] Supabase request failed (" + reason + "). Backing off for " + (delayMs / 1000L) + "s (consecutive failures: " + failures + ").");
    }

    public void pushBatchData(List<PlayerStats> snapshot) {
        long now = System.currentTimeMillis();
        if (now < backoffUntil) {
            if (now - lastWarningLogTime > 30000L) {
                lastWarningLogTime = now;
                long remainingSec = Math.max(1, (backoffUntil - now) / 1000L);
                logger.warning("[LeaderboardSync] Sync paused due to backoff (" + consecutiveFailures.get() + " consecutive failures). Resuming in " + remainingSec + "s.");
            }
            if (snapshot != null && !snapshot.isEmpty()) {
                requeue(snapshot);
            }
            return;
        }

        java.util.Map<java.util.UUID, PlayerStats> uniqueStats = new java.util.LinkedHashMap<>();
        PlayerStats failedStat;
        while ((failedStat = retryQueue.poll()) != null) {
            if (failedStat.getUuid() != null) {
                uniqueStats.put(failedStat.getUuid(), failedStat);
            }
        }
        if (snapshot != null) {
            for (PlayerStats stats : snapshot) {
                if (stats.getUuid() != null) {
                    uniqueStats.put(stats.getUuid(), stats);
                }
            }
        }

        if (uniqueStats.isEmpty()) return;

        List<PlayerStats> chunk = new ArrayList<>();
        for (PlayerStats stats : uniqueStats.values()) {
            chunk.add(stats);
            if (chunk.size() >= maxBatchSize) {
                sendChunk(chunk);
                chunk = new ArrayList<>();
            }
        }

        if (!chunk.isEmpty()) {
            sendChunk(chunk);
        }
    }

    RequestBody buildJsonBody(List<PlayerStats> chunk) {
        String colUuid = columnMap.getOrDefault("uuid", "uuid");
        String colUsername = columnMap.getOrDefault("username", "username");
        String colMoney = columnMap.getOrDefault("money", "money");
        String colPlaytime = columnMap.getOrDefault("playtime_sec", "playtime_sec");
        String colKills = columnMap.getOrDefault("kills", "kills");
        String colDeaths = columnMap.getOrDefault("deaths", "deaths");
        String colBlockBreak = columnMap.getOrDefault("block_break", "block_break");
        String colBlockPlace = columnMap.getOrDefault("block_place", "block_place");
        String colMobsKilled = columnMap.getOrDefault("mobs_killed", "mobs_killed");
        String colShards = columnMap.getOrDefault("shards", "shards");

        JsonArray batch = new JsonArray();
        for (PlayerStats stats : chunk) {
            JsonObject p = new JsonObject();
            p.addProperty(colUuid, stats.getUuid().toString());
            p.addProperty(colUsername, stats.getName() != null ? stats.getName() : "Unknown");
            p.addProperty(colMoney, stats.getStat(StatisticType.MONEY));
            p.addProperty(colPlaytime, (long) stats.getStat(StatisticType.PLAYTIME));
            p.addProperty(colKills, (int) stats.getStat(StatisticType.PLAYER_KILLS));
            p.addProperty(colDeaths, (int) stats.getStat(StatisticType.DEATHS));
            p.addProperty(colBlockBreak, (long) stats.getStat(StatisticType.BLOCKS_BROKEN));
            p.addProperty(colBlockPlace, (long) stats.getStat(StatisticType.BLOCKS_PLACED));
            p.addProperty(colMobsKilled, (int) stats.getStat(StatisticType.MOBS_KILLED));
            p.addProperty(colShards, stats.getStat(StatisticType.SHARDS));

            batch.add(p);
        }
        return RequestBody.create(batch.toString(), MediaType.parse("application/json; charset=utf-8"));
    }

    void sendChunk(List<PlayerStats> chunk) {
        RequestBody body = buildJsonBody(chunk);

        // Use the configurable conflict key for upsert resolution
        Request request = new Request.Builder()
                .url(url + "?on_conflict=" + conflictKey)
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                logger.warning("[LeaderboardSync] Batch upsert failed: " + e.getMessage() + ". Requeuing " + chunk.size() + " entries.");
                requeue(chunk);
                handleFailure(e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (response) {
                    if (response.isSuccessful()) {
                        if (debug) logger.info("[LeaderboardSync] Successfully upserted chunk of " + chunk.size() + " entries.");
                        handleSuccess();
                    } else {
                        String bodyString = response.body() != null ? response.body().string() : "empty body";
                        int code = response.code();
                        logger.warning("[LeaderboardSync] Chunk fail HTTP " + code + " - " + bodyString + ". Requeuing " + chunk.size() + " entries.");
                        requeue(chunk);
                        handleHttpError(code, response.header("Retry-After"));
                    }
                } catch (Exception e) {
                    logger.warning("[LeaderboardSync] Generic response read exception: " + e.getMessage() + ". Requeuing " + chunk.size() + " entries.");
                    requeue(chunk);
                    handleFailure(e.getMessage());
                }
            }
        });
    }

    public void pushBatchDataSync(List<PlayerStats> snapshot) {
        if (System.currentTimeMillis() < backoffUntil) {
            logger.warning("[LeaderboardSync] Supabase is unreachable (circuit open/backed off). Skipping synchronous shutdown flush to prevent server freeze.");
            return;
        }

        java.util.Map<java.util.UUID, PlayerStats> uniqueStats = new java.util.LinkedHashMap<>();
        PlayerStats failedStat;
        while ((failedStat = retryQueue.poll()) != null) {
            if (failedStat.getUuid() != null) {
                uniqueStats.put(failedStat.getUuid(), failedStat);
            }
        }
        if (snapshot != null) {
            for (PlayerStats stats : snapshot) {
                if (stats.getUuid() != null) {
                    uniqueStats.put(stats.getUuid(), stats);
                }
            }
        }

        if (uniqueStats.isEmpty()) return;

        List<PlayerStats> chunk = new ArrayList<>();
        int chunksSent = 0;
        for (PlayerStats stats : uniqueStats.values()) {
            chunk.add(stats);
            if (chunk.size() >= maxBatchSize) {
                chunksSent++;
                boolean success = sendChunkSync(chunk);
                chunk = new ArrayList<>();
                if (!success) {
                    logger.warning("[LeaderboardSync] Shutdown chunk flush failed. Aborting remaining chunks to protect server shutdown.");
                    break;
                }
                if (chunksSent >= MAX_SHUTDOWN_CHUNKS) {
                    logger.info("[LeaderboardSync] Reached maximum shutdown sync limit (" + MAX_SHUTDOWN_CHUNKS + " chunks). Remaining data deferred.");
                    break;
                }
            }
        }

        if (!chunk.isEmpty() && chunksSent < MAX_SHUTDOWN_CHUNKS) {
            sendChunkSync(chunk);
        }
    }

    boolean sendChunkSync(List<PlayerStats> chunk) {
        RequestBody body = buildJsonBody(chunk);

        Request request = new Request.Builder()
                .url(url + "?on_conflict=" + conflictKey)
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(body)
                .build();

        // Create a fast-failing client with 1.0s timeout to prevent server shutdown freeze
        OkHttpClient shutdownClient = client.newBuilder()
                .connectTimeout(1000, TimeUnit.MILLISECONDS)
                .readTimeout(1000, TimeUnit.MILLISECONDS)
                .writeTimeout(1000, TimeUnit.MILLISECONDS)
                .callTimeout(1200, TimeUnit.MILLISECONDS)
                .build();

        try (Response response = shutdownClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                if (debug) logger.info("[LeaderboardSync] Successfully upserted chunk of " + chunk.size() + " entries synchronously on shutdown.");
                return true;
            } else {
                String bodyString = response.body() != null ? response.body().string() : "empty body";
                logger.warning("[LeaderboardSync] Synchronous chunk upsert failed: HTTP " + response.code() + " - " + bodyString);
                requeue(chunk);
                handleHttpError(response.code(), response.header("Retry-After"));
                return false;
            }
        } catch (IOException e) {
            logger.warning("[LeaderboardSync] Synchronous chunk upsert error: " + e.getMessage());
            requeue(chunk);
            handleFailure(e.getMessage());
            return false;
        }
    }

    public void forceDisconnect() {
        client.dispatcher().cancelAll();
        client.dispatcher().executorService().shutdownNow();
        try {
            client.dispatcher().executorService().awaitTermination(250, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        client.connectionPool().evictAll();
    }
}
