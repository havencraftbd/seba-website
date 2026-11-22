package com.haven.shards.hook;

import com.haven.shards.HavenShards;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class HavenShopHook {

    public static void tryHook(HavenShards plugin) {
        Plugin havenShopPlugin = Bukkit.getPluginManager().getPlugin("HavenShop");
        if (havenShopPlugin == null || !havenShopPlugin.isEnabled()) {
            return;
        }

        try {
            Class<?> providerClass = Class.forName("com.haven.shop.economy.EconomyProvider");
            Object proxyProvider = Proxy.newProxyInstance(
                    providerClass.getClassLoader(),
                    new Class<?>[]{ providerClass },
                    (proxy, method, methodArgs) -> {
                        String methodName = method.getName();
                        switch (methodName) {
                            case "isAvailable" -> { return true; }
                            case "getBalance" -> { return plugin.getShardManager().getBalance((Player) methodArgs[0]); }
                            case "hasBalance" -> { return plugin.getShardManager().hasBalance((Player) methodArgs[0], (double) methodArgs[1]); }
                            case "withdraw" -> {
                                Player p = (Player) methodArgs[0];
                                double amt = (double) methodArgs[1];
                                boolean ok = plugin.getShardManager().removeBalance(p, amt);
                                if (ok) {
                                    double newBal = plugin.getShardManager().getBalance(p);
                                    plugin.getDatabaseManager().logTransactionAsync(p.getUniqueId(), p.getName(), null, "HAVEN_SHOP", "SHOP_BUY", amt, newBal, 0);
                                    plugin.getDiscordWebhookManager().logTransaction("SHOP_BUY", p.getName(), "HavenShop", amt);
                                }
                                return ok;
                            }
                            case "deposit" -> {
                                Player p = (Player) methodArgs[0];
                                double amt = (double) methodArgs[1];
                                plugin.getShardManager().addBalance(p, amt);
                                double newBal = plugin.getShardManager().getBalance(p);
                                plugin.getDatabaseManager().logTransactionAsync(null, "HAVEN_SHOP", p.getUniqueId(), p.getName(), "SHOP_SELL", amt, 0, newBal);
                                plugin.getDiscordWebhookManager().logTransaction("SHOP_SELL", "HavenShop", p.getName(), amt);
                                return true;
                            }
                            case "getCurrencyName" -> { return plugin.getConfig().getString("currency.name", "Shards"); }
                            case "getCurrencySymbol" -> { return plugin.getConfig().getString("currency.symbol", "✨"); }
                        }
                        return null;
                    }
            );

            Method getEconMgr = havenShopPlugin.getClass().getMethod("getEconomyManager");
            Object econManager = getEconMgr.invoke(havenShopPlugin);
            if (econManager != null) {
                Method registerMethod = econManager.getClass().getMethod("registerCustomProvider", String.class, providerClass);
                registerMethod.invoke(econManager, "SHARDS", proxyProvider);
                registerMethod.invoke(econManager, "COINS", proxyProvider);
                registerMethod.invoke(econManager, "HAVENCOIN", proxyProvider);
                plugin.getLogger().info("[HavenShards] Successfully hooked 'SHARDS' into HavenShop via reflection!");
            }
        } catch (Throwable e) {
            plugin.getLogger().fine("[HavenShards] Could not hook into HavenShop: " + e.getMessage());
        }
    }
}
