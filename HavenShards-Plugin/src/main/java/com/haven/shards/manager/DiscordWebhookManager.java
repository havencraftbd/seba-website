package com.haven.shards.manager;

import com.haven.shards.HavenShards;
import com.haven.shards.api.event.HavenShardsReloadEvent;
import com.haven.shards.util.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class DiscordWebhookManager implements Listener {

    private final HavenShards plugin;
    private final BlockingQueue<WebhookPayload> queue = new LinkedBlockingQueue<>(500);
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    private BukkitTask workerTask = null;
    private volatile long backoffUntil = 0L;

    private record WebhookPayload(String webhookUrl, String jsonPayload) {}

    public DiscordWebhookManager(HavenShards plugin) {
        this.plugin = plugin;
        startWorker();
    }

    public synchronized void startWorker() {
        stopWorker();
        if (plugin.getConfig().getBoolean("discord.enabled", false)) {
            // Buffer worker executes asynchronously every 15 ticks (~750ms).
            // This caps sending at ~1.33 requests/sec, well within Discord's 5 req / 2s limit.
            this.workerTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::processQueue, 15L, 15L);
        }
    }

    public synchronized void stopWorker() {
        if (workerTask != null) {
            workerTask.cancel();
            workerTask = null;
        }
    }

    public void shutdown() {
        stopWorker();
        // Drain up to 3 remaining alerts synchronously before shutdown completes
        int drained = 0;
        WebhookPayload payload;
        while ((payload = queue.poll()) != null && drained < 3) {
            executeWebhookRequest(payload.webhookUrl(), payload.jsonPayload());
            drained++;
        }
        queue.clear();
    }

    @EventHandler
    public void onReload(HavenShardsReloadEvent event) {
        startWorker();
    }

    public void logTransaction(String type, String senderName, String receiverName, double amount) {
        logTransaction(type, senderName, receiverName, amount, 0.0);
    }

    public void logTransaction(String type, String senderName, String receiverName, double amount, double taxAmount) {
        if (!plugin.getConfig().getBoolean("discord.enabled", false)) return;
        String webhookUrl = plugin.getConfig().getString("discord.webhook-url", "");
        if (webhookUrl.isEmpty()) return;

        boolean notify = false;
        int color = 0x00FF88; // Default Green

        switch (type.toUpperCase()) {
            case "PAY" -> {
                notify = plugin.getConfig().getBoolean("discord.notify-pay", true);
                color = 0x00E5FF; // Cyan
            }
            case "WITHDRAW" -> {
                notify = plugin.getConfig().getBoolean("discord.notify-withdraw", true);
                color = 0xFFA500; // Orange
            }
            case "DEPOSIT", "REDEEM" -> {
                notify = plugin.getConfig().getBoolean("discord.notify-deposit", true);
                color = 0x00FF00; // Bright Green
            }
            case "ADMIN_GIVE", "ADMIN_TAKE", "ADMIN_SET" -> {
                notify = plugin.getConfig().getBoolean("discord.notify-admin", true);
                color = 0x9900FF; // Purple
            }
            default -> {
                notify = plugin.getConfig().getBoolean("discord.notify-shop", true);
                color = 0x00FF88;
            }
        }

        if (!notify) return;

        String symbol = plugin.getConfig().getString("currency.symbol", "✨");
        String currency = plugin.getConfig().getString("currency.name", "Shards");

        String title = "💎 Transaction Log: " + type.toUpperCase();
        String taxLine = taxAmount > 0
                ? String.format("\n**Tax / Fee:** `%s %s %s`", symbol, ItemUtil.formatMoney(taxAmount), currency)
                : "";

        String description = String.format("**Sender:** `%s`\n**Receiver:** `%s`\n**Amount:** `%s %s %s`%s",
                senderName != null ? senderName : "SYSTEM",
                receiverName != null ? receiverName : "N/A",
                symbol, ItemUtil.formatMoney(amount), currency,
                taxLine);

        queueWebhookEmbed(webhookUrl, title, description, color);
    }

    public void logDupeAttempt(String playerName, String itemUuid, double amount) {
        if (!plugin.getConfig().getBoolean("discord.enabled", false)) return;
        if (!plugin.getConfig().getBoolean("discord.notify-dupe-attempt", true)) return;
        String webhookUrl = plugin.getConfig().getString("discord.webhook-url", "");
        if (webhookUrl.isEmpty()) return;

        String symbol = plugin.getConfig().getString("currency.symbol", "✨");
        String currency = plugin.getConfig().getString("currency.name", "Shards");

        String title = "⚠️ SECURITY ALERT: DUPLICATE ITEM DETECTED!";
        String description = String.format("**Player:** `%s`\n**Item UUID:** `%s`\n**Attempted Value:** `%s %s %s`",
                playerName, itemUuid, symbol, ItemUtil.formatMoney(amount), currency);

        queueWebhookEmbed(webhookUrl, title, description, 0xFF0000); // Bright Red
    }

    private void queueWebhookEmbed(String webhookUrl, String title, String description, int colorHex) {
        if (!plugin.getConfig().getBoolean("discord.enabled", false)) return;
        if (webhookUrl == null || webhookUrl.isEmpty()) return;

        if (queue.size() >= 500) {
            queue.poll();
            plugin.getLogger().warning("[HavenShards] Discord Webhook buffer queue full (>500). Dropped oldest alert.");
        }

        String jsonPayload = String.format("{"
                + "\"username\": \"HavenShards Security Log\","
                + "\"embeds\": [{"
                + "\"title\": \"%s\","
                + "\"description\": \"%s\","
                + "\"color\": %d,"
                + "\"timestamp\": \"%s\","
                + "\"footer\": {\"text\": \"HavenShards Audit Engine\"}"
                + "}]"
                + "}",
                escapeJson(title),
                escapeJson(description),
                colorHex,
                Instant.now().toString());

        queue.offer(new WebhookPayload(webhookUrl, jsonPayload));
    }

    private void processQueue() {
        if (queue.isEmpty()) return;
        if (System.currentTimeMillis() < backoffUntil) return;
        if (!isProcessing.compareAndSet(false, true)) return;

        try {
            WebhookPayload payload = queue.poll();
            if (payload != null) {
                executeWebhookRequest(payload.webhookUrl(), payload.jsonPayload());
            }
        } finally {
            isProcessing.set(false);
        }
    }

    private void executeWebhookRequest(String webhookUrl, String jsonPayload) {
        HttpURLConnection conn = null;
        try {
            URL url = URI.create(webhookUrl).toURL();
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "HavenShards-AuditBot");
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
                os.flush();
            }

            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                // Success: drain input stream so underlying TCP socket can be cleanly reused by JVM keep-alive pool
                try (InputStream is = conn.getInputStream()) {
                    is.transferTo(OutputStream.nullOutputStream());
                }
            } else {
                // Error: drain error stream to release socket
                try (InputStream es = conn.getErrorStream()) {
                    if (es != null) {
                        es.transferTo(OutputStream.nullOutputStream());
                    }
                }

                if (code == 429) {
                    long retryMillis = 3000L;
                    String retryHeader = conn.getHeaderField("Retry-After");
                    if (retryHeader != null) {
                        try {
                            double seconds = Double.parseDouble(retryHeader);
                            retryMillis = (long) (seconds * 1000L);
                        } catch (NumberFormatException ignored) {}
                    }
                    this.backoffUntil = System.currentTimeMillis() + retryMillis;
                    plugin.getLogger().warning("[HavenShards] Discord Webhook rate-limited (HTTP 429). Pausing queue for " + retryMillis + "ms.");
                } else {
                    plugin.getLogger().warning("[HavenShards] Discord Webhook response code: " + code);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[HavenShards] Failed to send Discord Webhook: " + e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private String escapeJson(String raw) {
        if (raw == null) return "";
        return raw.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
