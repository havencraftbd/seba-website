package com.haven.shards.hook;

import com.haven.shards.HavenShards;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

public class EssentialsHook {

    private final HavenShards plugin;
    private Plugin essentialsPlugin;
    private Method getUserMethod;
    private Method isAfkMethod;

    public EssentialsHook(HavenShards plugin) {
        this.plugin = plugin;
        tryHook();
    }

    public void tryHook() {
        try {
            Plugin ess = Bukkit.getPluginManager().getPlugin("Essentials");
            if (ess != null && ess.isEnabled()) {
                this.essentialsPlugin = ess;
                this.getUserMethod = ess.getClass().getMethod("getUser", Player.class);
                Class<?> userClass = getUserMethod.getReturnType();
                this.isAfkMethod = userClass.getMethod("isAfk");
                plugin.getLogger().info("[HavenShards] Successfully hooked into Essentials / EssentialsX AFK system!");
            }
        } catch (Throwable e) {
            this.essentialsPlugin = null;
        }
    }

    public boolean isAfk(Player player) {
        if (essentialsPlugin == null || player == null || getUserMethod == null || isAfkMethod == null) {
            return false;
        }
        try {
            Object user = getUserMethod.invoke(essentialsPlugin, player);
            if (user != null) {
                Object afkObj = isAfkMethod.invoke(user);
                if (afkObj instanceof Boolean b) {
                    return b;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public boolean isHooked() {
        return essentialsPlugin != null;
    }
}
