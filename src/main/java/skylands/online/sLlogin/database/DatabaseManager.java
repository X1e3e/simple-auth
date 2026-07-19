package skylands.online.sLlogin.database;

import org.bukkit.configuration.file.FileConfiguration;
import skylands.online.sLlogin.SLlogin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;

public final class DatabaseManager {

    private final SLlogin plugin;
    private Connection connection;
    private String type;

    public DatabaseManager(SLlogin plugin) {
        this.plugin = plugin;
    }

    public synchronized void connect() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            return;
        }

        FileConfiguration config = plugin.getConfig();
        type = config.getString("database.type", "sqlite").toLowerCase();

        if (type.equals("mysql")) {
            String host = config.getString("database.mysql.host", "localhost");
            int port = config.getInt("database.mysql.port", 3306);
            String database = config.getString("database.mysql.database", "sllogin");
            String username = config.getString("database.mysql.username", "root");
            String password = config.getString("database.mysql.password", "");
            boolean useSSL = config.getBoolean("database.mysql.useSSL", false);

            String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=%b&allowPublicKeyRetrieval=true&autoReconnect=true&connectTimeout=3000&socketTimeout=3000",
                    host, port, database, useSSL);
            
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } catch (ClassNotFoundException e) {
                // Fallback to older driver if new one is not found
                try {
                    Class.forName("com.mysql.jdbc.Driver");
                } catch (ClassNotFoundException ignored) {}
            }

            connection = DriverManager.getConnection(url, username, password);
            plugin.getLogger().info("Connected to MySQL database.");
        } else {
            // SQLite (default)
            String fileName = config.getString("database.sqlite.file", "database.db");
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }
            File dbFile = new File(dataFolder, fileName);
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();

            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException ignored) {}

            connection = DriverManager.getConnection(url);
            plugin.getLogger().info("Connected to SQLite database.");
        }

        createTables();
    }

    private void createTables() throws SQLException {
        String query = "CREATE TABLE IF NOT EXISTS players (" +
                "username VARCHAR(64) PRIMARY KEY, " +
                "password_hash VARCHAR(255) NOT NULL, " +
                "salt VARCHAR(64) NOT NULL, " +
                "last_ip VARCHAR(45), " +
                "last_login BIGINT, " +
                "registered_at BIGINT" +
                ");";
        try (Statement statement = getConnection().createStatement()) {
            statement.execute(query);
        }
    }

    public synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connect();
        }
        return connection;
    }

    public synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("Database connection closed.");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error closing database connection", e);
        }
    }

    /**
     * Data holder class for Player credentials and session info.
     */
    public static class PlayerData {
        private final String username;
        private final String passwordHash;
        private final String salt;
        private final String lastIp;
        private final long lastLogin;
        private final long registeredAt;

        public PlayerData(String username, String passwordHash, String salt, String lastIp, long lastLogin, long registeredAt) {
            this.username = username;
            this.passwordHash = passwordHash;
            this.salt = salt;
            this.lastIp = lastIp;
            this.lastLogin = lastLogin;
            this.registeredAt = registeredAt;
        }

        public String getUsername() { return username; }
        public String getPasswordHash() { return passwordHash; }
        public String getSalt() { return salt; }
        public String getLastIp() { return lastIp; }
        public long getLastLogin() { return lastLogin; }
        public long getRegisteredAt() { return registeredAt; }
    }

    /**
     * Gets a player's data from the database.
     * Username is compared in lower case to avoid duplicates and bypasses.
     */
    public synchronized PlayerData getPlayerData(String username) {
        String query = "SELECT * FROM players WHERE LOWER(username) = LOWER(?)";
        try (PreparedStatement ps = getConnection().prepareStatement(query)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new PlayerData(
                            rs.getString("username"),
                            rs.getString("password_hash"),
                            rs.getString("salt"),
                            rs.getString("last_ip"),
                            rs.getLong("last_login"),
                            rs.getLong("registered_at")
                    );
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load player data for: " + username, e);
        }
        return null;
    }

    /**
     * Registers a new player.
     */
    public synchronized boolean registerPlayer(String username, String passwordHash, String salt, String ip) {
        String query = "INSERT INTO players (username, password_hash, salt, last_ip, last_login, registered_at) VALUES (?, ?, ?, ?, ?, ?)";
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = getConnection().prepareStatement(query)) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ps.setString(3, salt);
            ps.setString(4, ip);
            ps.setLong(5, now);
            ps.setLong(6, now);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to register player: " + username, e);
            return false;
        }
    }

    /**
     * Updates an existing player's password.
     */
    public synchronized boolean updatePlayerPassword(String username, String passwordHash, String salt) {
        String query = "UPDATE players SET password_hash = ?, salt = ? WHERE LOWER(username) = LOWER(?)";
        try (PreparedStatement ps = getConnection().prepareStatement(query)) {
            ps.setString(1, passwordHash);
            ps.setString(2, salt);
            ps.setString(3, username);
            int affected = ps.executeUpdate();
            return affected > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update password for player: " + username, e);
            return false;
        }
    }

    /**
     * Updates an existing player's login IP and login timestamp.
     */
    public synchronized void updatePlayerLogin(String username, String ip) {
        String query = "UPDATE players SET last_ip = ?, last_login = ? WHERE LOWER(username) = LOWER(?)";
        try (PreparedStatement ps = getConnection().prepareStatement(query)) {
            ps.setString(1, ip);
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, username);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update login data for player: " + username, e);
        }
    }

    /**
     * Unregisters/deletes a player's account.
     */
    public synchronized boolean unregisterPlayer(String username) {
        String query = "DELETE FROM players WHERE LOWER(username) = LOWER(?)";
        try (PreparedStatement ps = getConnection().prepareStatement(query)) {
            ps.setString(1, username);
            int affected = ps.executeUpdate();
            return affected > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to unregister player: " + username, e);
            return false;
        }
    }
}
