package com.vcmc.restart;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

public class ConfigManager {

    private final VcmcRestart plugin;
    private List<AnnouncementEntry> announcements;

    public ConfigManager(VcmcRestart plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        announcements = new ArrayList<>();
        List<?> rawList = plugin.getConfig().getList("announcements");
        if (rawList != null) {
            for (Object obj : rawList) {
                if (obj instanceof java.util.Map<?, ?> map) {
                    Object timeObj = map.get("time");
                    Object msgObj = map.get("message");
                    if (timeObj instanceof Number && msgObj instanceof String) {
                        announcements.add(new AnnouncementEntry(
                                ((Number) timeObj).longValue(),
                                (String) msgObj
                        ));
                    }
                }
            }
        }
        // 大きい順（早いアナウンス順）にソート
        announcements.sort((a, b) -> Long.compare(b.timeBeforeRestart(), a.timeBeforeRestart()));
    }

    public boolean isAutoRestartEnabled() {
        return plugin.getConfig().getBoolean("auto-restart-enabled", true);
    }

    public long getRestartIntervalMinutes() {
        return plugin.getConfig().getLong("restart-interval", 360);
    }

    public boolean isScheduledTimesEnabled() {
        return plugin.getConfig().getBoolean("scheduled-times.enabled", false);
    }

    public List<String> getScheduledTimes() {
        return plugin.getConfig().getStringList("scheduled-times.times");
    }

    public List<AnnouncementEntry> getAnnouncements() {
        return announcements;
    }

    public boolean isTitleEnabled() {
        return plugin.getConfig().getBoolean("title.enabled", true);
    }

    public long getTitleShowFrom() {
        return plugin.getConfig().getLong("title.show-from", 30);
    }

    public String getTitleText() {
        return plugin.getConfig().getString("title.title", "&c&lサーバー再起動");
    }

    public String getTitleSubtext() {
        return plugin.getConfig().getString("title.subtitle", "&f%time% 後に再起動します");
    }

    public int getTitleFadeIn() {
        return plugin.getConfig().getInt("title.fade-in", 10);
    }

    public int getTitleStay() {
        return plugin.getConfig().getInt("title.stay", 70);
    }

    public int getTitleFadeOut() {
        return plugin.getConfig().getInt("title.fade-out", 20);
    }

    public String getKickMessage() {
        return plugin.getConfig().getString("kick-message",
                "&c&lサーバーが再起動中です。しばらくしてから再接続してください。");
    }

    public String getRestartBroadcast() {
        return plugin.getConfig().getString("restart-broadcast",
                "&c&l[再起動] &fサーバーを再起動しています...");
    }

    public String getMessage(String key) {
        return plugin.getConfig().getString("messages." + key, "");
    }

    public Component colorize(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }

    public record AnnouncementEntry(long timeBeforeRestart, String message) {}
}
