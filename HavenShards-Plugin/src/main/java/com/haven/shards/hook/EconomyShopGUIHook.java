package com.haven.shards.hook;

import com.haven.shards.HavenShards;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

public class EconomyShopGUIHook implements Listener {

    private final HavenShards plugin;

    public EconomyShopGUIHook(HavenShards plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings("unchecked")
    public static void tryHook(HavenShards plugin) {
        Plugin esgPlugin = Bukkit.getPluginManager().getPlugin("EconomyShopGUI");
        if (esgPlugin == null) {
            esgPlugin = Bukkit.getPluginManager().getPlugin("EconomyShopGUI-Premium");
        }
        if (esgPlugin == null || !esgPlugin.isEnabled()) {
            return;
        }

        try {
            Class<?> eventClass = null;
            String[] possibleClasses = {
                "me.gy44.economyshopgui.api.events.CustomEconomyTransactionEvent",
                "me.gy44.economyshopgui.api.events.PreCustomEconomyTransactionEvent",
                "me.graphenemc.economyshopgui.api.events.CustomEconomyTransactionEvent"
            };

            for (String className : possibleClasses) {
                try {
                    eventClass = Class.forName(className, false, esgPlugin.getClass().getClassLoader());
                    if (eventClass != null) break;
                } catch (ClassNotFoundException ignored) {}
            }

            if (eventClass == null || !Event.class.isAssignableFrom(eventClass)) {
                plugin.getLogger().fine("[HavenShards] EconomyShopGUI CustomEconomyTransactionEvent class not found.");
                return;
            }

            EconomyShopGUIHook hook = new EconomyShopGUIHook(plugin);
            EventExecutor executor = (listener, event) -> hook.handleTransaction(event);

            Bukkit.getPluginManager().registerEvent(
                (Class<? extends Event>) eventClass,
                hook,
                EventPriority.NORMAL,
                executor,
                plugin
            );
            plugin.getLogger().info("[HavenShards] Successfully hooked into EconomyShopGUI for 'SHARDS' currency via EventExecutor!");
        } catch (Throwable e) {
            plugin.getLogger().warning("[HavenShards] Could not hook into EconomyShopGUI: " + e.getMessage());
        }
    }

    public void handleTransaction(Object event) {
        if (event == null) return;
        try {
            Method getCurrencyMethod = event.getClass().getMethod("getCurrency");
            String currency = (String) getCurrencyMethod.invoke(event);
            if (currency == null) return;

            String curUpper = currency.toUpperCase();
            if (!curUpper.contains("SHARD")) {
                return;
            }

            Method getPlayerMethod = event.getClass().getMethod("getPlayer");
            Player player = (Player) getPlayerMethod.invoke(event);
            if (player == null) return;

            Method getPriceMethod = event.getClass().getMethod("getPrice");
            double price = (Double) getPriceMethod.invoke(event);

            Method getTransactionTypeMethod = event.getClass().getMethod("getTransactionType");
            Object type = getTransactionTypeMethod.invoke(event);
            String typeStr = type != null ? type.toString() : "BUY";

            Method setResultMethod = null;
            for (Method m : event.getClass().getMethods()) {
                if (m.getName().equals("setTransactionResult") || m.getName().equals("setResult")) {
                    setResultMethod = m;
                    break;
                }
            }

            if (typeStr.equalsIgnoreCase("BUY") || typeStr.contains("BUY")) {
                if (plugin.getShardManager().hasBalance(player, price)) {
                    boolean removed = plugin.getShardManager().removeBalance(player, price);
                    if (removed) {
                        setEnumResult(setResultMethod, event, "SUCCESS");
                        double newBal = plugin.getShardManager().getBalance(player);
                        plugin.getDatabaseManager().logTransactionAsync(player.getUniqueId(), player.getName(), null, "ECONOMY_SHOP", "SHOP_BUY", price, newBal, 0);
                        plugin.getDiscordWebhookManager().logTransaction("SHOP_BUY", player.getName(), "EconomyShopGUI", price);
                    } else {
                        setEnumResult(setResultMethod, event, "INSUFFICIENT_FUNDS");
                    }
                } else {
                    setEnumResult(setResultMethod, event, "INSUFFICIENT_FUNDS");
                }
            } else if (typeStr.equalsIgnoreCase("SELL") || typeStr.contains("SELL")) {
                plugin.getShardManager().addBalance(player, price);
                setEnumResult(setResultMethod, event, "SUCCESS");
                double newBal = plugin.getShardManager().getBalance(player);
                plugin.getDatabaseManager().logTransactionAsync(null, "ECONOMY_SHOP", player.getUniqueId(), player.getName(), "SHOP_SELL", price, 0, newBal);
                plugin.getDiscordWebhookManager().logTransaction("SHOP_SELL", "EconomyShopGUI", player.getName(), price);
            }
        } catch (Throwable e) {
            plugin.getLogger().warning("[HavenShards] Error handling EconomyShopGUI transaction: " + e.getMessage());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void setEnumResult(Method setResultMethod, Object event, String enumName) {
        if (setResultMethod == null) return;
        try {
            Class<?> paramType = setResultMethod.getParameterTypes()[0];
            if (paramType.isEnum()) {
                Object enumVal = Enum.valueOf((Class<Enum>) paramType, enumName);
                setResultMethod.invoke(event, enumVal);
            }
        } catch (Throwable e) {
            plugin.getLogger().warning("[HavenShards] Error setting EconomyShopGUI result: " + e.getMessage());
        }
    }
}
