package com.vcmc.restart;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class RestartManager {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final VcmcRestart plugin;
    private final ConfigManager config;

    private final AtomicReference<ScheduledTask> countdownTask = new AtomicReference<>();
    private final AtomicLong remainingSeconds = new AtomicLong(-1);
    // AtomicBoolean で cancelRestart / executeRestart の競合を防ぐ
    private final AtomicBoolean scheduled = new AtomicBoolean(false);
    private volatile int nextAnnouncementIndex = 0;

    public RestartManager(VcmcRestart plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void scheduleRestart() {
        long delaySeconds = calculateNextRestartDelay();
        if (delaySeconds <= 0) {
            plugin.getLogger().warning("再起動遅延の計算に失敗しました。自動再起動をスキップします。");
            return;
        }
        scheduleRestartIn(delaySeconds);
    }

    public void scheduleRestartIn(long seconds) {
        cancelRestart();

        remainingSeconds.set(seconds);

        // スケジュール秒数より大きい timeBeforeRestart のアナウンスはスキップ
        // (例: 3秒後再起動の場合、3600秒前アナウンスは無関係なので飛ばす)
        List<ConfigManager.AnnouncementEntry> announcements = config.getAnnouncements();
        nextAnnouncementIndex = 0;
        for (int i = 0; i < announcements.size(); i++) {
            if (announcements.get(i).timeBeforeRestart() >= seconds) {
                nextAnnouncementIndex = i + 1;
            } else {
                break;
            }
        }

        scheduled.set(true);

        plugin.getLogger().info(String.format("再起動を %s 後にスケジュールしました。", formatTime(seconds)));

        ScheduledTask task = Bukkit.getAsyncScheduler().runAtFixedRate(
                plugin,
                t -> tick(),
                1L,
                1L,
                TimeUnit.SECONDS
        );
        countdownTask.set(task);
    }

    private void tick() {
        long remaining = remainingSeconds.decrementAndGet();

        // キャンセル済み（負の値）の場合は何もしない
        if (remaining < 0) return;

        if (remaining == 0) {
            executeRestart();
            return;
        }

        List<ConfigManager.AnnouncementEntry> announcements = config.getAnnouncements();
        while (nextAnnouncementIndex < announcements.size()) {
            ConfigManager.AnnouncementEntry entry = announcements.get(nextAnnouncementIndex);
            if (remaining <= entry.timeBeforeRestart()) {
                broadcast(config.colorize(entry.message()));
                nextAnnouncementIndex++;
            } else {
                break;
            }
        }

        if (config.isTitleEnabled() && remaining <= config.getTitleShowFrom()) {
            showTitle(remaining);
        }
    }

    public void executeRestart() {
        // compareAndSet で二重実行・キャンセル後の誤実行を防ぐ
        if (!scheduled.compareAndSet(true, false)) return;
        cancelRestartTask();

        broadcast(config.colorize(config.getRestartBroadcast()));

        Bukkit.getGlobalRegionScheduler().run(plugin, t -> {
            Component kickMsg = config.colorize(config.getKickMessage());
            for (Player player : new ArrayList<>(Bukkit.getOnlinePlayers())) {
                player.kick(kickMsg);
            }
        });

        // spigot().restart() は Folia のスケジューラスレッドから呼ぶと
        // System.exit() のシャットダウンフックがそのスレッド自身をjoinしようとしてデッドロックする。
        // そのため独立した素のJavaスレッドから呼び出す。
        Thread restartThread = new Thread(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            Bukkit.getServer().spigot().restart();
        }, "vcmc-restart-thread");
        restartThread.setDaemon(false);
        restartThread.start();
    }

    public void cancelRestart() {
        cancelRestartTask();
        remainingSeconds.set(-1);
        scheduled.set(false);
    }

    private void cancelRestartTask() {
        ScheduledTask task = countdownTask.getAndSet(null);
        if (task != null && task.getExecutionState() != ScheduledTask.ExecutionState.CANCELLED) {
            task.cancel();
        }
    }

    public boolean isScheduled() {
        return scheduled.get();
    }

    public long getRemainingSeconds() {
        return remainingSeconds.get();
    }

    private long calculateNextRestartDelay() {
        List<Long> candidates = new ArrayList<>();

        long intervalMinutes = config.getRestartIntervalMinutes();
        if (intervalMinutes > 0) {
            candidates.add(intervalMinutes * 60L);
        }

        if (config.isScheduledTimesEnabled()) {
            List<String> times = config.getScheduledTimes();
            LocalDateTime now = LocalDateTime.now();
            for (String timeStr : times) {
                try {
                    LocalTime target = LocalTime.parse(timeStr, TIME_FORMAT);
                    LocalDateTime nextRun = now.toLocalDate().atTime(target);
                    if (!nextRun.isAfter(now)) {
                        nextRun = nextRun.plusDays(1);
                    }
                    long secs = Duration.between(now, nextRun).getSeconds();
                    candidates.add(secs);
                } catch (DateTimeParseException e) {
                    plugin.getLogger().warning("無効な時刻フォーマット: " + timeStr + " (HH:mm 形式で入力してください)");
                }
            }
        }

        if (candidates.isEmpty()) {
            return -1;
        }

        return candidates.stream().mapToLong(Long::longValue).min().orElse(-1);
    }

    // Bukkit.getServer().broadcast() はコンソールにも送信するため、
    // getConsoleSender().sendMessage() を別途呼ぶと二重になる
    private void broadcast(Component message) {
        Bukkit.getServer().broadcast(message);
    }

    private void showTitle(long remaining) {
        String timeStr = formatTime(remaining);
        Component titleComp = config.colorize(config.getTitleText());
        Component subComp = config.colorize(config.getTitleSubtext().replace("%time%", timeStr));

        Title.Times titleTimes = Title.Times.times(
                Duration.ofMillis(config.getTitleFadeIn() * 50L),
                Duration.ofMillis(config.getTitleStay() * 50L),
                Duration.ofMillis(config.getTitleFadeOut() * 50L)
        );
        Title title = Title.title(titleComp, subComp, titleTimes);

        Bukkit.getGlobalRegionScheduler().run(plugin, t -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.showTitle(title);
            }
        });
    }

    public static String formatTime(long totalSeconds) {
        if (totalSeconds <= 0) return "0秒";

        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (hours > 0) sb.append(hours).append("時間");
        if (minutes > 0) sb.append(minutes).append("分");
        if (seconds > 0 || sb.isEmpty()) sb.append(seconds).append("秒");
        return sb.toString();
    }

    public String getNextRestartDatetime() {
        long remaining = remainingSeconds.get();
        if (remaining <= 0) return "不明";
        LocalDateTime next = LocalDateTime.now().plusSeconds(remaining);
        return next.format(DateTimeFormatter.ofPattern("MM/dd HH:mm:ss"));
    }
}
