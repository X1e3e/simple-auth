package skylands.online.sLlogin.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.entity.Player;
import skylands.online.sLlogin.SLlogin;
import skylands.online.sLlogin.database.DatabaseManager;
import skylands.online.sLlogin.util.PasswordHasher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.logging.Level;

public final class ApiServer {

    private final SLlogin plugin;
    private HttpServer server;
    private java.util.concurrent.ExecutorService executor;

    public ApiServer(SLlogin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("api.enabled", false)) {
            return;
        }

        int port = plugin.getConfig().getInt("api.port", 8080);
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/api/change-password", new ChangePasswordHandler());
            // Use executor for handle requests concurrently
            executor = Executors.newFixedThreadPool(2);
            server.setExecutor(executor);
            server.start();
            plugin.getLogger().info("Embedded HTTP API started on port " + port);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to start HTTP API on port " + port, e);
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(1); // 1 second delay
            plugin.getLogger().info("Embedded HTTP API stopped.");
        }
        if (executor != null) {
            executor.shutdown();
        }
    }

    private class ChangePasswordHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                // Enforce POST method
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendResponse(exchange, 405, false, "Method Not Allowed. Use POST.");
                    return;
                }

                Map<String, String> params = getParams(exchange);
                
                // Auth check
                String configKey = plugin.getConfig().getString("api.key", "change-me-to-a-very-secure-secret-key");
                String requestKey = exchange.getRequestHeaders().getFirst("X-API-Key");
                if (requestKey == null) {
                    requestKey = params.get("key");
                }

                if (requestKey == null || !requestKey.equals(configKey)) {
                    sendResponse(exchange, 401, false, "Unauthorized: Invalid API Key.");
                    return;
                }

                // Input validation
                String username = params.get("username");
                String newPassword = params.get("password");

                if (username == null || username.trim().isEmpty() || newPassword == null || newPassword.trim().isEmpty()) {
                    sendResponse(exchange, 400, false, "Bad Request: Missing username or password.");
                    return;
                }

                // Check if player exists in database
                DatabaseManager.PlayerData data = plugin.getDatabaseManager().getPlayerData(username);
                if (data == null) {
                    sendResponse(exchange, 404, false, "Player not registered.");
                    return;
                }

                // Length validation
                int minLength = plugin.getConfig().getInt("password.min-length", 6);
                if (newPassword.length() < minLength) {
                    sendResponse(exchange, 400, false, "Bad Request: Password must be at least " + minLength + " characters long.");
                    return;
                }

                // Hash password
                String algorithm = plugin.getConfig().getString("password.algorithm", "SHA256");
                String salt = PasswordHasher.generateSalt();
                String hash = PasswordHasher.hash(newPassword, salt, algorithm);

                // Update database
                boolean updated = plugin.getDatabaseManager().updatePlayerPassword(username, hash, salt);

                if (updated) {
                    sendResponse(exchange, 200, true, "Password updated successfully.");
                    
                    // Notify player if online (must run on main thread)
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        Player onlinePlayer = plugin.getServer().getPlayerExact(username);
                        if (onlinePlayer != null && onlinePlayer.isOnline()) {
                            onlinePlayer.sendMessage(plugin.getMessage("messages.password-changed"));
                        }
                    });
                } else {
                    sendResponse(exchange, 500, false, "Internal Server Error: Failed to update password in database.");
                }

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error handling API request", e);
                sendResponse(exchange, 500, false, "Internal Server Error: " + e.getMessage());
            }
        }

        private Map<String, String> getParams(HttpExchange exchange) throws IOException {
            Map<String, String> params = new HashMap<>();
            
            // Query parameters
            String query = exchange.getRequestURI().getRawQuery();
            parseQueryParams(query, params);
            
            // Post body url-encoded parameters
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            if (contentType != null && contentType.contains("application/x-www-form-urlencoded")) {
                try (InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
                     BufferedReader br = new BufferedReader(isr)) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                    parseQueryParams(sb.toString(), params);
                }
            }
            return params;
        }

        private void parseQueryParams(String query, Map<String, String> params) {
            if (query == null || query.isEmpty()) {
                return;
            }
            for (String param : query.split("&")) {
                String[] pair = param.split("=", 2);
                if (pair.length > 0) {
                    try {
                        String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8.name());
                        String value = pair.length > 1 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8.name()) : "";
                        params.put(key, value);
                    } catch (UnsupportedEncodingException ignored) {}
                }
            }
        }

        private void sendResponse(HttpExchange exchange, int statusCode, boolean success, String message) throws IOException {
            String json = String.format("{\"success\": %b, \"message\": \"%s\"}", success, message);
            byte[] responseBytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
    }
}
