package com.havencraft.leaderboard.providers;

import com.havencraft.leaderboard.HavenLeaderboard;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

public class VaultProvider {
    private Economy econ = null;

    public VaultProvider(HavenLeaderboard plugin) {
        if (!setupEconomy(plugin)) {
            plugin.getJavaPlugin().getLogger().severe("Vault dependency found but Economy provider not registered.");
        }
    }

    private boolean setupEconomy(HavenLeaderboard plugin) {
        if (plugin.getJavaPlugin().getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = plugin.getJavaPlugin().getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    public double getBalance(OfflinePlayer player) {
        if (econ == null) return 0.0;
        return econ.getBalance(player);
    }


    public String format(double amount) {
        if (econ == null) return String.valueOf(amount);
        return econ.format(amount);
    }
    
    public boolean isEnabled() {
        return econ != null;
    }
}
