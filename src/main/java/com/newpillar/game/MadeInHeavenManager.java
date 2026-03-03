package com.newpillar.game;

import com.newpillar.NewPillar;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Made in Heaven 时间加速管理器
 * 实现昼夜快速交替，游戏计时器加速
 */
public class MadeInHeavenManager {
    
    private final NewPillar plugin;
    private final GameManager gameManager;
    private final ItemSystem itemSystem;
    
    // 加速倍数
    public static final double SPEED_MULTIPLIER = 4.0;
    
    // 昼夜轮回时间（游戏刻）- 6秒 = 120 ticks (在20倍速下)
    private static final long DAY_CYCLE_TICKS = 24000L;
    private static final long ACCELERATED_CYCLE_TICKS = 1200L; // 1分钟游戏时间 = 3秒现实时间
    
    // 事件持续时间（现实秒）
    private static final int EVENT_DURATION_SECONDS = 60;
    
    // 状态
    private boolean isActive = false;
    private long originalTimeRate = 1;
    private io.papermc.paper.threadedregions.scheduler.ScheduledTask dayNightTask;
    private io.papermc.paper.threadedregions.scheduler.ScheduledTask countdownTask;
    private long eventStartTime;
    
    public MadeInHeavenManager(NewPillar plugin) {
        this.plugin = plugin;
        this.gameManager = plugin.getGameManager();
        this.itemSystem = plugin.getGameManager().getItemSystem();
    }
    
    /**
     * 启动 Made in Heaven 事件
     */
    public void startEvent() {
        if (isActive) {
            return;
        }
        
        isActive = true;
        eventStartTime = System.currentTimeMillis();
        
        // 向所有玩家显示标题
        Title.Times times = Title.Times.times(
            Duration.ofMillis(500),
            Duration.ofMillis(3000),
            Duration.ofMillis(500)
        );
        
        Title title = Title.title(
            Component.text("メイド・イン・ヘブン")
                .color(TextColor.color(0xFFFF00)), // 黄色
            Component.text("時間は加速します……！！！")
                .color(TextColor.color(0xFFD700)), // 金色
            times
        );
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(title);
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f);
        }
        
        // 广播消息
        Bukkit.broadcastMessage("§6§l[メイド・イン・ヘブン] §e時間は加速します……！！！");
        Bukkit.broadcastMessage("§6『螺旋階段』『カブトムシ』『廃墟街』『イチジク塔』『カブトムシ』『ドレザの道』『カブトムシ』『特異点』『ジョット』『天使』『アジサイ』『カブトムシ』『特異点』『秘密の皇帝』");
        
        // 应用加速效果
        applyAcceleration();
        
        // 启动昼夜快速交替
        startAcceleratedDayNightCycle();
        
        // 启动倒计时
        startCountdown();
        
        plugin.getLogger().info("Made in Heaven 事件已启动！");
    }
    
    /**
     * 停止 Made in Heaven 事件
     */
    public void stopEvent() {
        if (!isActive) {
            return;
        }
        
        isActive = false;
        
        // 取消任务
        if (dayNightTask != null) {
            dayNightTask.cancel();
            dayNightTask = null;
        }
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        
        // 恢复正常时间流速
        removeAcceleration();
        
        // 通知所有玩家
        Bukkit.broadcastMessage("§6§l[メイド・イン・ヘブン] §e時間加速終了、世界は正常に戻りました！");
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_WITHER_DEATH, 1.0f, 0.5f);
        }
        
        plugin.getLogger().info("Made in Heaven 事件已结束！");
    }
    
    /**
     * 应用加速效果
     */
    private void applyAcceleration() {
        // 加速物品系统计时器
        if (itemSystem != null) {
            itemSystem.setSpeedMultiplier(SPEED_MULTIPLIER);
        }
        
        // 加速边界收缩计时器
        gameManager.setSpeedMultiplier(SPEED_MULTIPLIER);
    }
    
    /**
     * 移除加速效果
     */
    private void removeAcceleration() {
        // 恢复物品系统计时器
        if (itemSystem != null) {
            itemSystem.setSpeedMultiplier(1.0);
        }
        
        // 恢复边界收缩计时器
        gameManager.setSpeedMultiplier(1.0);
    }
    
    /**
     * 启动加速的昼夜循环
     * 6秒一次昼夜轮回 = 每tick增加 24000/120 = 200 ticks
     */
    private void startAcceleratedDayNightCycle() {
        World world = gameManager.getGameWorld();
        if (world == null) return;
        
        final long[] currentTime = {world.getTime()};
        
        dayNightTask = Bukkit.getRegionScheduler().runAtFixedRate(plugin, world.getSpawnLocation(), task -> {
            if (!isActive) {
                task.cancel();
                return;
            }
            
            // 每tick增加200游戏刻，实现6秒一个昼夜轮回
            currentTime[0] = (currentTime[0] + 200) % DAY_CYCLE_TICKS;
            final long newTime = currentTime[0];
            
            // 使用GlobalRegionScheduler修改世界时间
            Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
                world.setTime(newTime);
            });
        }, 1L, 1L);
    }
    
    /**
     * 启动倒计时
     */
    private void startCountdown() {
        AtomicInteger remainingSeconds = new AtomicInteger(EVENT_DURATION_SECONDS);
        
        countdownTask = Bukkit.getAsyncScheduler().runAtFixedRate(plugin, task -> {
            if (!isActive) {
                task.cancel();
                return;
            }
            
            int remaining = remainingSeconds.decrementAndGet();
            
            // 每10秒提醒一次
            if (remaining > 0 && remaining % 10 == 0) {
                Bukkit.broadcastMessage("§6§l[メイド・イン・ヘブン] §e時間加速残り " + remaining + " 秒");
            }
            
            // 时间到，结束事件
            if (remaining <= 0) {
                Bukkit.getGlobalRegionScheduler().execute(plugin, () -> stopEvent());
                task.cancel();
            }
        }, 1L, 1L, TimeUnit.SECONDS);
    }
    
    /**
     * 检查事件是否激活
     */
    public boolean isActive() {
        return isActive;
    }
    
    /**
     * 获取剩余时间（秒）
     */
    public int getRemainingTime() {
        if (!isActive) return 0;
        long elapsed = (System.currentTimeMillis() - eventStartTime) / 1000;
        return Math.max(0, EVENT_DURATION_SECONDS - (int) elapsed);
    }
}
