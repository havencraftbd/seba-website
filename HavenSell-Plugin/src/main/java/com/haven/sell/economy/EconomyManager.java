package com.haven.sell.economy;

import com.haven.sell.HavenSell;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.logging.Level;

/**
 * Manages Vault economy integration.
 * Provides thread-safe access to economy operations.
 */
public class EconomyManager {

    private final HavenSell plugin;
    private Economy economy;

    public EconomyManager(HavenSell plugin) {
        this.plugin = plugin;
    }

    /**
     * Sets up the Vault economy hook.
     *
     * @return true if the economy was successfully hooked, false otherwise
     */
    public boolean setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }

        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager()
                .getRegistration(Economy.class);

        if (rsp == null) {
            return false;
        }

        economy = rsp.getProvider();
        return economy != null;
    }

    /**
     * Deposits money into a player's account.
     *
     * @param player The player to deposit to
     * @param amount The amount to deposit
     * @return true if the deposit was successful
     */
    public boolean deposit(Player player, double amount) {
        if (economy == null) {
            plugin.getLogger().warning("Attempted to deposit money but economy is not available!");
            return false;
        }

        if (amount <= 0) {
            return false;
        }

        try {
            EconomyResponse response = economy.depositPlayer(player, amount);
            if (!response.transactionSuccess()) {
                plugin.getLogger().warning("Economy deposit failed for " + player.getName()
                        + ": " + response.errorMessage);
                return false;
            }
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error during economy deposit for " + player.getName(), e);
            return false;
        }
    }

    /**
     * Gets a player's current balance.
     *
     * @param player The player to check
     * @return The player's balance, or 0.0 if economy is unavailable
     */
    public double getBalance(Player player) {
        if (economy == null) {
            return 0.0;
        }

        try {
            return economy.getBalance(player);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error getting balance for " + player.getName(), e);
            return 0.0;
        }
    }

    /**
     * Gets the Vault economy instance.
     */
    public Economy getEconomy() {
        return economy;
    }
}
