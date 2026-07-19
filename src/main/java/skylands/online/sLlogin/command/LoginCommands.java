package skylands.online.sLlogin.command;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import skylands.online.sLlogin.SLlogin;
import skylands.online.sLlogin.database.DatabaseManager;
import skylands.online.sLlogin.util.PasswordHasher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LoginCommands implements CommandExecutor, TabCompleter {

    private final SLlogin plugin;
    private final Map<UUID, Integer> loginAttempts = new HashMap<>();

    public LoginCommands(SLlogin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmdName = command.getName().toLowerCase();

        if (cmdName.equals("login")) {
            return handleLogin(sender, args);
        } else if (cmdName.equals("register")) {
            return handleRegister(sender, args);
        } else if (cmdName.equals("changepassword")) {
            return handleChangePassword(sender, args);
        } else if (cmdName.equals("sllogin")) {
            return handleAdminCommand(sender, args);
        }

        return false;
    }

    private boolean handleLogin(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be executed by players.");
            return true;
        }

        UUID uuid = player.getUniqueId();
        if (plugin.isAuthenticated(player)) {
            player.sendMessage(plugin.getMessage("messages.already-logged-in"));
            return true;
        }

        DatabaseManager.PlayerData data = plugin.getDatabaseManager().getPlayerData(player.getName());
        if (data == null) {
            player.sendMessage(plugin.getMessage("messages.not-registered"));
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(plugin.getMessage("messages.invalid-args"));
            return true;
        }

        String password = args[0];
        String algorithm = plugin.getConfig().getString("password.algorithm", "SHA256");

        if (PasswordHasher.verify(password, data.getPasswordHash(), data.getSalt(), algorithm)) {
            // Success
            loginAttempts.remove(uuid);
            plugin.getLoginListener().completeAuth(player);
            
            String ip = player.getAddress().getAddress().getHostAddress();
            plugin.getDatabaseManager().updatePlayerLogin(player.getName(), ip);
            
            player.sendMessage(plugin.getMessage("messages.login-success"));
        } else {
            // Failure
            int maxAttempts = plugin.getConfig().getInt("security.max-attempts", 3);
            int attempts = loginAttempts.getOrDefault(uuid, 0) + 1;
            loginAttempts.put(uuid, attempts);

            if (attempts >= maxAttempts) {
                loginAttempts.remove(uuid);
                player.kickPlayer(plugin.getMessage("messages.kick-attempts"));
            } else {
                player.sendMessage(String.format(plugin.getMessage("messages.wrong-password"), maxAttempts - attempts));
            }
        }
        return true;
    }

    private boolean handleRegister(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be executed by players.");
            return true;
        }

        if (plugin.isAuthenticated(player)) {
            player.sendMessage(plugin.getMessage("messages.already-logged-in"));
            return true;
        }

        DatabaseManager.PlayerData data = plugin.getDatabaseManager().getPlayerData(player.getName());
        if (data != null) {
            player.sendMessage(plugin.getMessage("messages.already-registered"));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(plugin.getMessage("messages.welcome-register"));
            return true;
        }

        String password = args[0];
        String confirmPassword = args[1];

        if (!password.equals(confirmPassword)) {
            player.sendMessage(plugin.getMessage("messages.passwords-dont-match"));
            return true;
        }

        int minLength = plugin.getConfig().getInt("password.min-length", 6);
        if (password.length() < minLength) {
            player.sendMessage(String.format(plugin.getMessage("messages.password-too-short"), minLength));
            return true;
        }

        String algorithm = plugin.getConfig().getString("password.algorithm", "SHA256");
        String salt = PasswordHasher.generateSalt();
        String hash = PasswordHasher.hash(password, salt, algorithm);
        String ip = player.getAddress().getAddress().getHostAddress();

        boolean registered = plugin.getDatabaseManager().registerPlayer(player.getName(), hash, salt, ip);
        if (registered) {
            plugin.getLoginListener().completeAuth(player);
            player.sendMessage(plugin.getMessage("messages.register-success"));
        } else {
            player.sendMessage(ChatColor.RED + "Произошла ошибка при регистрации. Пожалуйста, попробуйте позже.");
        }
        return true;
    }

    private boolean handleChangePassword(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be executed by players.");
            return true;
        }

        if (!plugin.isAuthenticated(player)) {
            player.sendMessage(plugin.getMessage("messages.must-login"));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Использование: /changepassword <старый_пароль> <новый_пароль>");
            return true;
        }

        String oldPassword = args[0];
        String newPassword = args[1];

        DatabaseManager.PlayerData data = plugin.getDatabaseManager().getPlayerData(player.getName());
        if (data == null) {
            player.sendMessage(plugin.getMessage("messages.not-registered"));
            return true;
        }

        String algorithm = plugin.getConfig().getString("password.algorithm", "SHA256");
        if (!PasswordHasher.verify(oldPassword, data.getPasswordHash(), data.getSalt(), algorithm)) {
            player.sendMessage(ChatColor.RED + "Неверный старый пароль!");
            return true;
        }

        int minLength = plugin.getConfig().getInt("password.min-length", 6);
        if (newPassword.length() < minLength) {
            player.sendMessage(String.format(plugin.getMessage("messages.password-too-short"), minLength));
            return true;
        }

        String salt = PasswordHasher.generateSalt();
        String hash = PasswordHasher.hash(newPassword, salt, algorithm);

        boolean updated = plugin.getDatabaseManager().updatePlayerPassword(player.getName(), hash, salt);
        if (updated) {
            player.sendMessage(plugin.getMessage("messages.password-changed"));
        } else {
            player.sendMessage(ChatColor.RED + "Произошла ошибка при смене пароля.");
        }
        return true;
    }

    private boolean handleAdminCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("sllogin.admin")) {
            sender.sendMessage(plugin.getMessage("messages.no-permission"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.GOLD + "=== SLlogin Admin Commands ===");
            sender.sendMessage(ChatColor.YELLOW + "/sllogin reload " + ChatColor.WHITE + "- Перезагрузить плагин");
            sender.sendMessage(ChatColor.YELLOW + "/sllogin unregister <игрок> " + ChatColor.WHITE + "- Удалить регистрацию игрока");
            sender.sendMessage(ChatColor.YELLOW + "/sllogin forcesetpassword <игрок> <пароль> " + ChatColor.WHITE + "- Принудительно установить пароль");
            return true;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("reload")) {
            plugin.reloadPlugin();
            sender.sendMessage(ChatColor.GREEN + "[SLlogin] Конфигурация перезагружена.");
            return true;
        } else if (sub.equals("unregister")) {
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Использование: /sllogin unregister <игрок>");
                return true;
            }
            String targetName = args[1];
            boolean deleted = plugin.getDatabaseManager().unregisterPlayer(targetName);
            if (deleted) {
                sender.sendMessage(ChatColor.GREEN + "Игрок " + targetName + " успешно удален из базы.");
                Player target = plugin.getServer().getPlayerExact(targetName);
                if (target != null && target.isOnline()) {
                    plugin.getAuthenticatedPlayers().remove(target.getUniqueId());
                    target.sendMessage(plugin.getMessage("messages.welcome-register"));
                    // We can force them to re-register
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        // Clear tasks first
                        plugin.getLoginListener().cancelTasks(target.getUniqueId());
                        // Apply blindness again
                        if (plugin.getConfig().getBoolean("security.apply-blindness", true)) {
                            target.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS, 20 * 999, 1, false, false));
                        }
                        // Re-trigger the auth session helper manually if needed, or kick them. Let's just kick them to log back in.
                        target.kickPlayer(ChatColor.RED + "Ваша регистрация была удалена администратором.");
                    });
                }
            } else {
                sender.sendMessage(plugin.getMessage("messages.player-not-found"));
            }
            return true;
        } else if (sub.equals("forcesetpassword")) {
            if (args.length < 3) {
                sender.sendMessage(ChatColor.RED + "Использование: /sllogin forcesetpassword <игрок> <новый_пароль>");
                return true;
            }
            String targetName = args[1];
            String newPassword = args[2];

            DatabaseManager.PlayerData data = plugin.getDatabaseManager().getPlayerData(targetName);
            if (data == null) {
                sender.sendMessage(plugin.getMessage("messages.player-not-found"));
                return true;
            }

            int minLength = plugin.getConfig().getInt("password.min-length", 6);
            if (newPassword.length() < minLength) {
                sender.sendMessage(String.format(plugin.getMessage("messages.password-too-short"), minLength));
                return true;
            }

            String algorithm = plugin.getConfig().getString("password.algorithm", "SHA256");
            String salt = PasswordHasher.generateSalt();
            String hash = PasswordHasher.hash(newPassword, salt, algorithm);

            boolean updated = plugin.getDatabaseManager().updatePlayerPassword(targetName, hash, salt);
            if (updated) {
                sender.sendMessage(ChatColor.GREEN + "Пароль игрока " + targetName + " успешно изменен.");
                Player target = plugin.getServer().getPlayerExact(targetName);
                if (target != null && target.isOnline()) {
                    target.sendMessage(plugin.getMessage("messages.password-changed-by-admin"));
                }
            } else {
                sender.sendMessage(ChatColor.RED + "Не удалось изменить пароль.");
            }
            return true;
        }

        sender.sendMessage(ChatColor.RED + "Неизвестная подкоманда. Используйте /sllogin для помощи.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String cmdName = command.getName().toLowerCase();
        if (cmdName.equals("sllogin") && sender.hasPermission("sllogin.admin")) {
            if (args.length == 1) {
                List<String> list = new ArrayList<>();
                list.add("reload");
                list.add("unregister");
                list.add("forcesetpassword");
                return filterList(list, args[0]);
            } else if (args.length == 2 && (args[0].equalsIgnoreCase("unregister") || args[0].equalsIgnoreCase("forcesetpassword"))) {
                List<String> players = new ArrayList<>();
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    players.add(p.getName());
                }
                return filterList(players, args[1]);
            }
        }
        return Collections.emptyList();
    }

    private List<String> filterList(List<String> list, String input) {
        List<String> result = new ArrayList<>();
        for (String s : list) {
            if (s.toLowerCase().startsWith(input.toLowerCase())) {
                result.add(s);
            }
        }
        return result;
    }
}
