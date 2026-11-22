package com.haven.shards.hook;

import com.haven.shards.HavenShards;
import com.haven.shards.util.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

public class GeyserHook {

    private final HavenShards plugin;
    private boolean floodgateAvailable = false;

    private Object floodgateApiInstance;
    private Method isFloodgatePlayerMethod;
    private Method getFloodgatePlayerMethod;

    public GeyserHook(HavenShards plugin) {
        this.plugin = plugin;
        tryHook();
    }

    private void tryHook() {
        if (Bukkit.getPluginManager().isPluginEnabled("floodgate")) {
            try {
                Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                Method getInstanceMethod = apiClass.getMethod("getInstance");
                this.floodgateApiInstance = getInstanceMethod.invoke(null);
                this.isFloodgatePlayerMethod = apiClass.getMethod("isFloodgatePlayer", UUID.class);
                this.getFloodgatePlayerMethod = apiClass.getMethod("getPlayer", UUID.class);
                this.floodgateAvailable = (floodgateApiInstance != null && isFloodgatePlayerMethod != null);

                if (floodgateAvailable) {
                    plugin.getLogger().info("[HavenShards] Successfully hooked into Floodgate (GeyserMC Bedrock Support)!");
                }
            } catch (Throwable ignored) {
                this.floodgateAvailable = false;
            }
        }
    }

    public boolean isBedrockPlayer(Player player) {
        if (!floodgateAvailable || player == null || isFloodgatePlayerMethod == null || floodgateApiInstance == null) {
            return false;
        }
        try {
            Object result = isFloodgatePlayerMethod.invoke(floodgateApiInstance, player.getUniqueId());
            return Boolean.TRUE.equals(result);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public boolean openBedrockDashboard(Player player) {
        if (!isBedrockPlayer(player)) return false;
        if (!plugin.getConfig().getBoolean("geyser-bedrock.use-native-forms", true)) return false;
        if (getFloodgatePlayerMethod == null || floodgateApiInstance == null) return false;

        try {
            Object fPlayer = getFloodgatePlayerMethod.invoke(floodgateApiInstance, player.getUniqueId());
            if (fPlayer == null) return false;

            Class<?> formClass = Class.forName("org.geysermc.cumulus.form.SimpleForm");
            Method builderMethod = formClass.getMethod("builder");
            Object builder = builderMethod.invoke(null);

            double balance = plugin.getShardManager().getBalance(player);
            double multiplier = plugin.getShardManager().getTotalMultiplier(player);
            String symbol = plugin.getConfig().getString("currency.symbol", "✨");
            String currency = plugin.getConfig().getString("currency.name", "Shards");

            String title = "HavenShards Dashboard";
            String content = "Welcome, " + player.getName() + "!\n\n" +
                    "• Balance: " + symbol + " " + ItemUtil.formatMoney(balance) + " " + currency + "\n" +
                    "• Active Multiplier: " + String.format("%.1fx", multiplier) + "\n\n" +
                    "Select an option below:";

            builder.getClass().getMethod("title", String.class).invoke(builder, title);
            builder.getClass().getMethod("content", String.class).invoke(builder, content);
            builder.getClass().getMethod("button", String.class).invoke(builder, "✨ Daily Reward (/shards daily)");
            builder.getClass().getMethod("button", String.class).invoke(builder, "📦 Withdraw 10 Shards");
            builder.getClass().getMethod("button", String.class).invoke(builder, "📊 Transaction History");
            builder.getClass().getMethod("button", String.class).invoke(builder, "❌ Close");

            java.util.function.Consumer<Object> responseConsumer = response -> {
                try {
                    Method clickedMethod = response.getClass().getMethod("clickedButtonId");
                    int buttonId = (int) clickedMethod.invoke(response);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        switch (buttonId) {
                            case 0 -> player.performCommand("shards daily");
                            case 1 -> player.performCommand("shards withdraw 10");
                            case 2 -> player.performCommand("shards history");
                        }
                    });
                } catch (Throwable ignored) {}
            };

            builder.getClass().getMethod("validResultHandler", java.util.function.Consumer.class).invoke(builder, responseConsumer);

            Object builtForm = builder.getClass().getMethod("build").invoke(builder);
            for (Method m : fPlayer.getClass().getMethods()) {
                if (m.getName().equals("sendForm") && m.getParameterCount() == 1) {
                    m.invoke(fPlayer, builtForm);
                    return true;
                }
            }
            return false;
        } catch (Throwable e) {
            // Fallback to Bukkit GUI cleanly
            return false;
        }
    }
}
