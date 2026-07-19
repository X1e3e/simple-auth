package skylands.online.sLlogin;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import skylands.online.sLlogin.api.ApiServer;
import skylands.online.sLlogin.command.LoginCommands;
import skylands.online.sLlogin.database.DatabaseManager;
import skylands.online.sLlogin.listener.LoginListener;

import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class SLlogin extends JavaPlugin {

    private final Set<UUID> authenticatedPlayers = ConcurrentHashMap.newKeySet();
    private DatabaseManager databaseManager;
    private LoginListener loginListener;
    private ApiServer apiServer;

    @Override
    public void onEnable() {
        // Load default config
        saveDefaultConfig();

        // Initialize database
        databaseManager = new DatabaseManager(this);
        try {
            databaseManager.connect();
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Could not connect to database! Disabling plugin...", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Register listener
        loginListener = new LoginListener(this);
        getServer().getPluginManager().registerEvents(loginListener, this);

        // Register commands
        LoginCommands loginCommands = new LoginCommands(this);
        getCommand("login").setExecutor(loginCommands);
        getCommand("register").setExecutor(loginCommands);
        getCommand("changepassword").setExecutor(loginCommands);
        getCommand("sllogin").setExecutor(loginCommands);
        getCommand("sllogin").setTabCompleter(loginCommands);

        // Start API server
        apiServer = new ApiServer(this);
        apiServer.start();

        getLogger().info("SLlogin has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        // Stop API server
        if (apiServer != null) {
            apiServer.stop();
        }

        // Close database connection
        if (databaseManager != null) {
            databaseManager.close();
        }

        authenticatedPlayers.clear();
        getLogger().info("SLlogin has been disabled.");
    }

    public Set<UUID> getAuthenticatedPlayers() {
        return authenticatedPlayers;
    }

    public boolean isAuthenticated(Player player) {
        return authenticatedPlayers.contains(player.getUniqueId());
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public LoginListener getLoginListener() {
        return loginListener;
    }

    /**
     * Helper to get a message from configuration, applying colors.
     */
    public String getMessage(String path) {
        String msg = getConfig().getString(path);
        if (msg == null) {
            return ChatColor.RED + "Message not found: " + path;
        }
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    /**
     * Reloads plugin configuration, reconnects database, and restarts API server.
     */
    public void reloadPlugin() {
        reloadConfig();

        // Restart API server
        if (apiServer != null) {
            apiServer.stop();
        }
        apiServer = new ApiServer(this);
        apiServer.start();

        // Reconnect database
        if (databaseManager != null) {
            databaseManager.close();
        }
        databaseManager = new DatabaseManager(this);
        try {
            databaseManager.connect();
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Could not reconnect to database during reload!", e);
        }
    }
}
