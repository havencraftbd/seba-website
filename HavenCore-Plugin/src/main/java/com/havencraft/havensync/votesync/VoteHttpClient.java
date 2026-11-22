package com.havencraft.havensync.votesync;

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

/**
 * Dedicated OkHttpClient for pushing batched votes with exponential backoff
 * and watchdog-safe shutdown execution.
 */
public class VoteHttpClient {

    private final String url;
    private final String apiKey;
    private final Logger logger;
    private final boolean debug;
    private final OkHttpClient client;
    private final Map<String, String> columnMap;

    private static final int MAX_RETRY_QUEUE = 2000;
    private static final long INITIAL_BACKOFF_MS = 5000L;
    private static final long MAX_BACKOFF_MS = 300000L;

    private final java.util.concurrent.ConcurrentLinkedQueue<VoteData> retryQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile long backoffUntil = 0L;
    private volatile long lastWarningLogTime = 0L;

    void requeue(List<VoteData> items) {
        if (items == null || items.isEmpty()) return;
        if (retryQueue.size() >= MAX_RETRY_QUEUE) {
            logger.severe("[VoteSync] Retry queue full (" + MAX_RETRY_QUEUE + ")! Discarding " + items.size() + " entries to prevent OOM.");
            return;
        }
        for (VoteData v : items) {
            if (v != null && retryQueue.size() < MAX_RETRY_QUEUE) {
                retryQueue.add(v);
            }
        }
    }

    public VoteHttpClient(String baseUrl, String tableName, String apiKey, int connectTimeout, int readTimeout, Logger logger, boolean debug, Map<String, String> columnMap) {
        // Build the full URL: baseUrl + /rest/v1/ + tableName
        // User only needs to provide the project URL (e.g. https://xxx.supabase.co)
        this.url = SupabaseUtil.buildSupabaseUrl(baseUrl, tableName);
        this.apiKey = apiKey;
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
            logger.info("[VoteSync] Connection restored to Supabase. Vote sync resumed successfully.");
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
        logger.warning("[VoteSync] Supabase request failed (" + reason + "). Backing off for " + (delayMs / 1000L) + "s (consecutive failures: " + failures + ").");
    }

    private RequestBody buildJsonBody(List<VoteData> toSend) {
        String colPlayerUuid = columnMap.getOrDefault("player_uuid", "player_uuid");
        String colUsername = columnMap.getOrDefault("username", "username");
        String colSite = columnMap.getOrDefault("site", "site");

        JsonArray batch = new JsonArray();
        for (VoteData v : toSend) {
            JsonObject p = new JsonObject();
            if (v.getPlayerUuid() != null) {
                p.addProperty(colPlayerUuid, v.getPlayerUuid().toString());
            }
            p.addProperty(colUsername, v.getUsername());
            p.addProperty(colSite, v.getSite());
            batch.add(p);
        }

        return RequestBody.create(batch.toString(), MediaType.parse("application/json"));
    }

    public void pushBatchData(List<VoteData> snapshot) {
        long now = System.currentTimeMillis();
        if (now < backoffUntil) {
            if (now - lastWarningLogTime > 30000L) {
                lastWarningLogTime = now;
                long remainingSec = Math.max(1, (backoffUntil - now) / 1000L);
                logger.warning("[VoteSync] Sync paused due to backoff (" + consecutiveFailures.get() + " consecutive failures). Resuming in " + remainingSec + "s.");
            }
            if (snapshot != null && !snapshot.isEmpty()) {
                requeue(snapshot);
            }
            return;
        }

        List<VoteData> toSend = new ArrayList<>();
        VoteData failedVote;
        while ((failedVote = retryQueue.poll()) != null) {
            toSend.add(failedVote);
        }
        if (snapshot != null) {
            toSend.addAll(snapshot);
        }

        if (toSend.isEmpty()) return;

        RequestBody body = buildJsonBody(toSend);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=minimal")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                logger.warning("[VoteSync] Failed to post batched votes: " + e.getMessage() + ". Requeuing " + toSend.size() + " votes.");
                requeue(toSend);
                handleFailure(e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (response) {
                    if (response.isSuccessful()) {
                        if (debug) logger.info("[VoteSync] Successfully pushed " + toSend.size() + " votes.");
                        handleSuccess();
                    } else {
                        String bodyString = response.body() != null ? response.body().string() : "empty body";
                        int code = response.code();
                        logger.warning("[VoteSync] Upsert fail HTTP " + code + " - " + bodyString + ". Requeuing " + toSend.size() + " votes.");
                        requeue(toSend);
                        handleHttpError(code, response.header("Retry-After"));
                    }
                } catch (Exception e) {
                    logger.warning("[VoteSync] Exception reading response: " + e.getMessage() + ". Requeuing " + toSend.size() + " votes.");
                    requeue(toSend);
                    handleFailure(e.getMessage());
                }
            }
        });
    }

    public void pushBatchDataSync(List<VoteData> snapshot) {
        if (System.currentTimeMillis() < backoffUntil) {
            logger.warning("[VoteSync] Supabase is unreachable (backed off). Skipping synchronous shutdown flush to prevent server freeze.");
            return;
        }

        List<VoteData> toSend = new ArrayList<>();
        VoteData failedVote;
        while ((failedVote = retryQueue.poll()) != null) {
            toSend.add(failedVote);
        }
        if (snapshot != null) {
            toSend.addAll(snapshot);
        }

        if (toSend.isEmpty()) return;

        RequestBody body = buildJsonBody(toSend);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=minimal")
                .post(body)
                .build();

        // Fast-failing client with 1.0s timeout to prevent server shutdown freeze
        OkHttpClient shutdownClient = client.newBuilder()
                .connectTimeout(1000, TimeUnit.MILLISECONDS)
                .readTimeout(1000, TimeUnit.MILLISECONDS)
                .writeTimeout(1000, TimeUnit.MILLISECONDS)
                .callTimeout(1200, TimeUnit.MILLISECONDS)
                .build();

        try (Response response = shutdownClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                if (debug) logger.info("[VoteSync] Successfully flushed " + toSend.size() + " votes synchronously on shutdown.");
            } else {
                String bodyString = response.body() != null ? response.body().string() : "empty body";
                logger.warning("[VoteSync] Synchronous flush failed: HTTP " + response.code() + " - " + bodyString);
                requeue(toSend);
                handleHttpError(response.code(), response.header("Retry-After"));
            }
        } catch (IOException e) {
            logger.warning("[VoteSync] Synchronous flush error: " + e.getMessage());
            requeue(toSend);
            handleFailure(e.getMessage());
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
