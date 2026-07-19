package skylands.online.sLlogin.listener;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import skylands.online.sLlogin.SLlogin;
import skylands.online.sLlogin.database.DatabaseManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class LoginListener implements Listener {

    private final SLlogin plugin;
    private final Map<UUID, BukkitTask> reminderTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> timeoutTasks = new HashMap<>();

    public LoginListener(SLlogin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String ip = player.getAddress().getAddress().getHostAddress();

        plugin.getAuthenticatedPlayers().remove(uuid);

        // Check if player is a premium (licensed) user
        boolean premiumAutoLogin = plugin.getConfig().getBoolean("security.premium-auto-login", true);
        if (premiumAutoLogin) {
            UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + player.getName()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (!uuid.equals(offlineUuid)) {
                // Verified Premium UUID (e.g. proxy authenticated this connection)
                plugin.getAuthenticatedPlayers().add(uuid);
                player.sendMessage(plugin.getMessage("messages.premium-login-success"));
                return;
            }
        }

        // Check if player is registered
        DatabaseManager.PlayerData data = plugin.getDatabaseManager().getPlayerData(player.getName());

        if (data != null) {
            // Check IP auto-login
            boolean ipAutologinEnabled = plugin.getConfig().getBoolean("ip-autologin.enabled", true);
            if (ipAutologinEnabled && ip.equals(data.getLastIp())) {
                long sessionHours = plugin.getConfig().getLong("ip-autologin.session-hours", 72);
                long sessionMs = sessionHours * 60L * 60L * 1000L;
                if (System.currentTimeMillis() - data.getLastLogin() < sessionMs) {
                    // Auto login
                    plugin.getAuthenticatedPlayers().add(uuid);
                    plugin.getDatabaseManager().updatePlayerLogin(player.getName(), ip);
                    player.sendMessage(plugin.getMessage("messages.auto-login-success"));
                    return;
                }
            }

            // Normal login required
            player.sendMessage(plugin.getMessage("messages.welcome-login"));
            startAuthSession(player, false);
        } else {
            // Registration required
            player.sendMessage(plugin.getMessage("messages.welcome-register"));
            startAuthSession(player, true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        plugin.getAuthenticatedPlayers().remove(uuid);
        cancelTasks(uuid);
    }

    private void startAuthSession(Player player, boolean register) {
        UUID uuid = player.getUniqueId();

        // Apply blindness effect if enabled
        if (plugin.getConfig().getBoolean("security.apply-blindness", true)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * 999, 1, false, false));
        }

        // Schedule reminder message every 5 seconds
        BukkitTask reminder = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (player.isOnline() && !plugin.isAuthenticated(player)) {
                if (register) {
                    player.sendMessage(plugin.getMessage("messages.welcome-register"));
                } else {
                    player.sendMessage(plugin.getMessage("messages.welcome-login"));
                }
            }
        }, 100L, 100L); // 5 seconds
        reminderTasks.put(uuid, reminder);

        // Schedule kick timeout
        long timeoutSec = plugin.getConfig().getLong("security.timeout-seconds", 60);
        BukkitTask timeout = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && !plugin.isAuthenticated(player)) {
                player.kickPlayer(plugin.getMessage("messages.kick-timeout"));
            }
        }, timeoutSec * 20L);
        timeoutTasks.put(uuid, timeout);
    }

    public void completeAuth(Player player) {
        UUID uuid = player.getUniqueId();
        plugin.getAuthenticatedPlayers().add(uuid);
        cancelTasks(uuid);
        player.removePotionEffect(PotionEffectType.BLINDNESS);
    }

    public void cancelTasks(UUID uuid) {
        BukkitTask reminder = reminderTasks.remove(uuid);
        if (reminder != null) {
            reminder.cancel();
        }
        BukkitTask timeout = timeoutTasks.remove(uuid);
        if (timeout != null) {
            timeout.cancel();
        }
    }

    // --- RESTRICTIONS FOR UNAUTHENTICATED PLAYERS ---

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!plugin.isAuthenticated(player)) {
            Location from = event.getFrom();
            Location to = event.getTo();
            // Allow looking around (rotation), block actual coordinate changes
            if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
                event.setTo(from.setDirection(to.getDirection()));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!plugin.isAuthenticated(player)) {
            event.setCancelled(true);
            player.sendMessage(plugin.getMessage("messages.must-login"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!plugin.isAuthenticated(player)) {
            String message = event.getMessage().toLowerCase();
            if (!message.startsWith("/login") && !message.startsWith("/register")) {
                event.setCancelled(true);
                player.sendMessage(plugin.getMessage("messages.must-login"));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.isAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!plugin.isAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!plugin.isAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (!plugin.isAuthenticated(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            if (!plugin.isAuthenticated(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
        if (!plugin.isAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (!plugin.isAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            if (!plugin.isAuthenticated(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player player) {
            if (!plugin.isAuthenticated(player)) {
                event.setCancelled(true);
                event.setTarget(null);
            }
        }
    }
}
