package com.vcmc.restart.commands;

import com.vcmc.restart.ConfigManager;
import com.vcmc.restart.RestartManager;
import com.vcmc.restart.VcmcRestart;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class RestartCommand implements CommandExecutor, TabCompleter {

    private final VcmcRestart plugin;
    private final RestartManager restartManager;
    private final ConfigManager config;

    public RestartCommand(VcmcRestart plugin, RestartManager restartManager, ConfigManager config) {
        this.plugin = plugin;
        this.restartManager = restartManager;
        this.config = config;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("vcmc.restart.admin")) {
            sender.sendMessage(config.colorize(config.getMessage("no-permission")));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(config.colorize(config.getMessage("usage")));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "now" -> handleNow(sender);
            case "cancel" -> handleCancel(sender);
            case "status" -> handleStatus(sender);
            case "settime" -> handleSetTime(sender, args);
            case "reload" -> handleReload(sender);
            default -> sender.sendMessage(config.colorize(config.getMessage("usage")));
        }
        return true;
    }

    private void handleNow(CommandSender sender) {
        sender.sendMessage(config.colorize(config.getMessage("restarting-now")));
        plugin.getLogger().info(sender.getName() + " が手動再起動を実行しました。");
        // 3秒後に再起動（アナウンスを見せる余裕を持たせる）
        restartManager.scheduleRestartIn(3);
    }

    private void handleCancel(CommandSender sender) {
        if (!restartManager.isScheduled()) {
            sender.sendMessage(config.colorize(config.getMessage("no-restart-scheduled")));
            return;
        }
        restartManager.cancelRestart();
        sender.sendMessage(config.colorize(config.getMessage("cancelled")));
        plugin.getLogger().info(sender.getName() + " が再起動をキャンセルしました。");
    }

    private void handleStatus(CommandSender sender) {
        if (!restartManager.isScheduled()) {
            sender.sendMessage(config.colorize(config.getMessage("status-disabled")));
            return;
        }
        long remaining = restartManager.getRemainingSeconds();
        String msg = config.getMessage("status-scheduled")
                .replace("%time%", RestartManager.formatTime(remaining))
                .replace("%datetime%", restartManager.getNextRestartDatetime());
        sender.sendMessage(config.colorize(msg));
    }

    private void handleSetTime(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(config.colorize(config.getMessage("usage")));
            return;
        }
        int minutes;
        try {
            minutes = Integer.parseInt(args[1]);
            if (minutes <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            sender.sendMessage(config.colorize(config.getMessage("settime-invalid")));
            return;
        }
        restartManager.scheduleRestartIn(minutes * 60L);
        String msg = config.getMessage("settime-success")
                .replace("%minutes%", String.valueOf(minutes));
        sender.sendMessage(config.colorize(msg));
        plugin.getLogger().info(sender.getName() + " が " + minutes + " 分後の再起動をスケジュールしました。");
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadPlugin();
        sender.sendMessage(config.colorize(config.getMessage("reload-success")));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("vcmc.restart.admin")) return List.of();

        if (args.length == 1) {
            return List.of("now", "cancel", "status", "settime", "reload")
                    .stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("settime")) {
            return List.of("30", "60", "120", "360");
        }
        return List.of();
    }
}
