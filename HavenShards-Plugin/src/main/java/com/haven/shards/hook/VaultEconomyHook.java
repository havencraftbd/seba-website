package com.haven.shards.hook;

import com.haven.shards.HavenShards;
import com.haven.shards.util.ItemUtil;
import net.milkbowl.vault.economy.AbstractEconomy;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class VaultEconomyHook extends AbstractEconomy {

    private final HavenShards plugin;
    private static VaultEconomyHook registeredInstance;

    public VaultEconomyHook(HavenShards plugin) {
        this.plugin = plugin;
    }

    public static void register(HavenShards plugin) {
        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            unregister(plugin);
            boolean isPrimary = plugin.getConfig().getBoolean("vault.register-as-primary-economy", false);
            ServicePriority priority = isPrimary ? ServicePriority.Highest : ServicePriority.Low;
            VaultEconomyHook vaultHook = new VaultEconomyHook(plugin);
            registeredInstance = vaultHook;
            Bukkit.getServicesManager().register(Economy.class, vaultHook, plugin, priority);
            plugin.getLogger().info("Registered HavenShards as Vault Economy Provider with priority: " + priority.name());
        }
    }

    public static void unregister(HavenShards plugin) {
        if (registeredInstance != null) {
            try {
                Bukkit.getServicesManager().unregister(Economy.class, registeredInstance);
            } catch (Exception ignored) {}
            registeredInstance = null;
        }
    }

    @Override
    public boolean isEnabled() {
        return plugin.isEnabled();
    }

    @Override
    public String getName() {
        return "HavenShards";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return 2;
    }

    @Override
    public String format(double amount) {
        return ItemUtil.formatMoney(amount);
    }

    @Override
    public String currencyNamePlural() {
        return plugin.getConfig().getString("currency.plural-name", plugin.getConfig().getString("currency.name", "Shards"));
    }

    @Override
    public String currencyNameSingular() {
        return plugin.getConfig().getString("currency.singular-name", "Shard");
    }

    private OfflinePlayer resolvePlayer(String playerName) {
        if (playerName == null || playerName.isEmpty()) return null;
        Player online = Bukkit.getPlayerExact(playerName);
        if (online != null) {
            return online;
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(playerName);
        if (cached != null && (cached.hasPlayedBefore() || cached.isOnline())) {
            return cached;
        }
        @SuppressWarnings("deprecation")
        OfflinePlayer off = Bukkit.getOfflinePlayer(playerName);
        if (off.hasPlayedBefore() || off.isOnline()) {
            return off;
        }
        UUID fromDb = plugin.getDatabaseManager().getPlayerUuidByName(playerName);
        if (fromDb != null) {
            return Bukkit.getOfflinePlayer(fromDb);
        }
        return null;
    }

    @Override
    public boolean hasAccount(String playerName) {
        return resolvePlayer(playerName) != null;
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        if (player == null) return false;
        return player.isOnline() || player.hasPlayedBefore() || plugin.getShardManager().isLoaded(player.getUniqueId())
                || plugin.getDatabaseManager().getPlayerUuidByName(player.getName()) != null;
    }

    @Override
    public double getBalance(String playerName) {
        OfflinePlayer player = resolvePlayer(playerName);
        return getBalance(player);
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        if (player == null) return 0.0;
        UUID uuid = player.getUniqueId();
        if (player.isOnline() || plugin.getShardManager().isLoaded(uuid)) {
            return plugin.getShardManager().getBalance(uuid);
        }
        return plugin.getDatabaseManager().getOfflineBalanceSync(uuid);
    }

    @Override
    public boolean has(String playerName, double amount) {
        return getBalance(playerName) >= amount;
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return getBalance(player) >= amount;
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        OfflinePlayer player = resolvePlayer(playerName);
        if (player == null) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Player does not exist");
        }
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        if (player == null) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Player cannot be null");
        }
        if (amount < 0 || Double.isNaN(amount) || Double.isInfinite(amount)) {
            return new EconomyResponse(0, getBalance(player), EconomyResponse.ResponseType.FAILURE, "Cannot withdraw negative or invalid amount");
        }
        com.haven.shards.model.WithdrawalResult result = plugin.getShardManager().withdrawIfEnough(player, amount);
        if (!result.isSuccess()) {
            return new EconomyResponse(0, result.getBalance(), EconomyResponse.ResponseType.FAILURE, "Insufficient funds");
        }
        return new EconomyResponse(amount, result.getBalance(), EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        OfflinePlayer player = resolvePlayer(playerName);
        if (player == null) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Player does not exist");
        }
        return depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        if (player == null) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Player cannot be null");
        }
        if (amount < 0 || Double.isNaN(amount) || Double.isInfinite(amount)) {
            return new EconomyResponse(0, getBalance(player), EconomyResponse.ResponseType.FAILURE, "Cannot deposit negative or invalid amount");
        }
        double newBalance = plugin.getShardManager().depositPlayerSync(player, amount);
        return new EconomyResponse(amount, newBalance, EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse createBank(String name, String player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "No bank support");
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "No bank support");
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "No bank support");
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "No bank support");
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "No bank support");
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "No bank support");
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "No bank support");
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "No bank support");
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "No bank support");
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "No bank support");
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "No bank support");
    }

    @Override
    public List<String> getBanks() {
        return Collections.emptyList();
    }

    @Override
    public boolean createPlayerAccount(String playerName) {
        return true;
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        return true;
    }

    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return true;
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return true;
    }

    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return true;
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return true;
    }

    @Override
    public double getBalance(String playerName, String world) {
        return getBalance(playerName);
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    @Override
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }
}
