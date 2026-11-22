package com.haven.sell.config;

import com.haven.sell.HavenSell;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.util.logging.Level;

/**
 * Manages the MySQL configuration from mysql.yml.
 */
public class MySQLConfig {

    private final HavenSell plugin;
    private final File configFile;
    private YamlConfiguration config;

    public MySQLConfig(HavenSell plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "mysql.yml");
        loadConfig(true);
    }

    /**
     * Loads the MySQL config from disk.
     */
    private void loadConfig(boolean update) {
        if (!configFile.exists()) {
            saveDefaultConfig();
        } else if (update) {
            // Auto-updater: merge new defaults preserving comments
            try {
                com.haven.sell.util.ConfigUpdater.update(plugin, "mysql.yml", configFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to update mysql.yml with new defaults", e);
            }
        }

        config = YamlConfiguration.loadConfiguration(configFile);
    }

    private void saveDefaultConfig() {
        try (InputStream in = plugin.getResource("mysql.yml")) {
            if (in == null) return;
            try (OutputStream out = new java.io.FileOutputStream(configFile)) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = in.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save default mysql.yml", e);
        }
    }

    /**
     * Reloads the MySQL configuration.
     */
    public void reload() {
        loadConfig(false);
    }

    // ━━━━━━━━━━━━━━━ Getters ━━━━━━━━━━━━━━━

    public boolean isEnabled() {
        return config.getBoolean("enabled", false);
    }

    public String getHost() {
        return config.getString("host", "localhost");
    }

    public int getPort() {
        return config.getInt("port", 3306);
    }

    public String getDatabase() {
        return config.getString("database", "havensell");
    }

    public String getUsername() {
        return config.getString("username", "root");
    }

    public String getPassword() {
        return config.getString("password", "password");
    }

    public String getTable() {
        return config.getString("table", "havensell_playerdata");
    }

    public int getMaxConnections() {
        return config.getInt("pool.max-connections", 10);
    }

    public int getConnectionTimeout() {
        return config.getInt("pool.connection-timeout", 5000);
    }

    /**
     * Builds the JDBC connection URL.
     */
    public String getJdbcUrl() {
        return "jdbc:mysql://" + getHost() + ":" + getPort() + "/" + getDatabase()
                + "?useSSL=false&autoReconnect=true&allowPublicKeyRetrieval=true";
    }
}
