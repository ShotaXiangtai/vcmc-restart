package com.vcmc.restart;

import com.vcmc.restart.commands.RestartCommand;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class VcmcRestart extends JavaPlugin {

    private ConfigManager configManager;
    private RestartManager restartManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        configManager = new ConfigManager(this);
        restartManager = new RestartManager(this, configManager);

        PluginCommand command = getCommand("vcrestart");
        if (command != null) {
            RestartCommand restartCommand = new RestartCommand(this, restartManager, configManager);
            command.setExecutor(restartCommand);
            command.setTabCompleter(restartCommand);
        }

        if (configManager.isAutoRestartEnabled()) {
            restartManager.scheduleRestart();
            getLogger().info("自動再起動を有効化しました。");
        } else {
            getLogger().info("自動再起動は無効です。");
        }

        getLogger().info("vcmc-restart が有効になりました。");
    }

    @Override
    public void onDisable() {
        if (restartManager != null) {
            restartManager.cancelRestart();
        }
        getLogger().info("vcmc-restart が無効になりました。");
    }

    public void reloadPlugin() {
        reloadConfig();
        configManager.reload();
        restartManager.cancelRestart();
        if (configManager.isAutoRestartEnabled()) {
            restartManager.scheduleRestart();
        }
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public RestartManager getRestartManager() {
        return restartManager;
    }
}
