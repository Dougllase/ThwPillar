package com.newpillar.game;

import com.newpillar.game.enums.GameStatus;

import com.newpillar.NewPillar;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.function.Consumer;

/**
 * 边界收缩管理器
 * 负责处理游戏边界的收缩逻辑
 */
public class BorderManager {
    private final NewPillar plugin;
    private final GameManager gameManager;
    private final com.newpillar.utils.DebugLogger debugLogger;
    
    private int borderTimer = 0;
    private int borderShrinkCountdown = 0;
    private ScheduledTask borderCountdownTask = null;
    private ScheduledTask borderShrinkTask;
    private boolean firstShrinkBroadcasted; // 标记是否已经播报过首次收缩，默认为false
    private boolean collapseTriggered = false; // 标记是否已触发崩溃，防止重复触发
    
    // 配置参数
    private static final double INITIAL_SIZE = 80.0;
    private static final double MIN_SIZE = 10.0;  // 最小边界10格
    private static final long SHRINK_DURATION = 30L; // 30秒动画
    
    // 动态计算的参数
    private double shrinkAmount; // 每轮收缩大小
    private int shrinkInterval;  // 收缩间隔（秒）
    private int totalShrinkRounds; // 总收缩轮数
    
    public BorderManager(NewPillar plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.debugLogger = plugin.getDebugLogger();
        
        // 计算缩圈参数
        calculateShrinkParameters();
    }
    
    /**
     * 计算缩圈参数
     * 根据配置的总时长自动计算每轮收缩大小和间隔
     */
    private void calculateShrinkParameters() {
        // 获取全缩圈流程时长（分钟），最少5分钟
        int totalDurationMinutes = Math.max(5, plugin.getConfig().getInt("border.total_shrink_duration_minutes", 5));
        int totalDurationSeconds = totalDurationMinutes * 60;
        
        // 目标轮数：3-4轮
        int targetRounds = plugin.getConfig().getInt("border.target_rounds", 4);
        targetRounds = Math.max(3, Math.min(4, targetRounds)); // 限制在3-4轮
        
        // 计算总收缩量
        double totalShrink = INITIAL_SIZE - MIN_SIZE; // 80 - 10 = 70格
        
        // 计算每轮收缩大小
        this.shrinkAmount = totalShrink / targetRounds;
        
        // 计算每轮间隔（包括收缩动画时间）
        // 总时长 = 首次等待 + (轮数-1) * 间隔 + 动画时间
        // 简化：间隔 = (总时长 - 动画时间) / 轮数
        this.shrinkInterval = (totalDurationSeconds - (int)SHRINK_DURATION) / targetRounds;
        this.shrinkInterval = Math.max(30, this.shrinkInterval); // 最少30秒间隔
        
        this.totalShrinkRounds = targetRounds;
        this.borderTimer = shrinkInterval; // 首次收缩等待时间 = 间隔时间
        
        debugLogger.debug("[BorderManager] 缩圈参数计算完成: " +
                "总时长=" + totalDurationMinutes + "分钟, " +
                "轮数=" + targetRounds + ", " +
                "每轮收缩=" + String.format("%.1f", shrinkAmount) + "格, " +
                "间隔=" + shrinkInterval + "秒");
    }
    
    /**
     * 初始化边界
     */
    public void initBorder(World world, Location center) {
        org.bukkit.WorldBorder worldBorder = world.getWorldBorder();
        worldBorder.setCenter(center);
        worldBorder.setSize(INITIAL_SIZE);
        worldBorder.setDamageAmount(1.0);
        worldBorder.setDamageBuffer(0);
        worldBorder.setWarningDistance(0);
        collapseTriggered = false; // 重置触发标记
        debugLogger.debug("[BorderManager] 边界已初始化: 中心(" + center.getBlockX() + ", " + center.getBlockZ() + "), 大小: " + INITIAL_SIZE);
    }
    
    /**
     * 开始边界收缩流程
     */
    public void startBorderShrink(World world) {
        // 如果已经有正在运行的倒计时任务，先取消它
        if (this.borderCountdownTask != null) {
            this.borderCountdownTask.cancel();
            this.borderCountdownTask = null;
        }

        this.borderShrinkCountdown = this.borderTimer;
        this.firstShrinkBroadcasted = false; // 重置首次收缩标记
        this.collapseTriggered = false; // 重置崩溃触发标记
        Location centerLoc = world.getWorldBorder().getCenter();

        debugLogger.debug("[BorderManager] 开始边界收缩倒计时: " + this.borderShrinkCountdown + "秒, firstShrinkBroadcasted重置为false");

        this.borderCountdownTask = Bukkit.getRegionScheduler().runAtFixedRate(
            plugin, centerLoc, new Consumer<ScheduledTask>() {
                @Override
                public void accept(ScheduledTask scheduledTask) {
                    if (gameManager.getGameStatus() != GameStatus.PLAYING) {
                        scheduledTask.cancel();
                        return;
                    }
                    
                    // Made in Heaven时间加速支持
                    int multiplier = gameManager.getTimeAccelerationMultiplier();
                    borderShrinkCountdown -= multiplier;
                    
                    if (borderShrinkCountdown <= 0) {
                        scheduledTask.cancel();
                        runBorderShrinkCycle(world);
                    }
                }
            }, 20L, 20L
        );
    }
    
    /**
     * 边界收缩周期
     */
    private void runBorderShrinkCycle(World world) {
        org.bukkit.WorldBorder worldBorder = world.getWorldBorder();
        double currentSize = worldBorder.getSize();
        Location centerLoc = worldBorder.getCenter();
        
        // 检查是否已经达到最小值且未触发崩溃
        if (currentSize <= MIN_SIZE + 0.1 && !collapseTriggered) {
            collapseTriggered = true;
            debugLogger.debug("[BorderManager] 边界达到最小值，触发平台崩溃");
            gameManager.broadcastMessage("§6§l[幸运之柱] §c边界已达到最小！平台即将崩溃！");
            gameManager.triggerPlatformCollapse();
            return;
        }
        
        if (currentSize > MIN_SIZE) {
            double newSize = Math.max(currentSize - shrinkAmount, MIN_SIZE);
            worldBorder.setSize(newSize, SHRINK_DURATION);
            
            // 只在首次收缩时播报
            if (!firstShrinkBroadcasted) {
                gameManager.broadcastMessage("§6§l[幸运之柱] §c边界开始收缩！");
                firstShrinkBroadcasted = true;
                debugLogger.debug("[BorderManager] 首次收缩播报已发送，标记设置为true");
            } else {
                debugLogger.debug("[BorderManager] 非首次收缩，跳过播报");
            }
            debugLogger.debug("[BorderManager] 收缩: " + currentSize + " -> " + newSize + 
                    ", 本轮收缩=" + String.format("%.1f", shrinkAmount) + "格");
            
            // 如果下一次收缩会达到或超过最小值，增加等待时间（等待动画完成）
            double nextSize = Math.max(newSize - shrinkAmount, MIN_SIZE);
            int waitTime = shrinkInterval;
            if (newSize <= MIN_SIZE || nextSize <= MIN_SIZE) {
                // 等待收缩动画完成后再检查
                waitTime = (int) SHRINK_DURATION + 5; // 动画时间 + 5秒缓冲
                debugLogger.debug("[BorderManager] 即将达到最小值，增加等待时间到 " + waitTime + " 秒");
            }
            
            this.borderShrinkCountdown = waitTime;
            
            final int finalWaitTime = waitTime;
            this.borderCountdownTask = Bukkit.getRegionScheduler().runAtFixedRate(
                plugin, centerLoc, new Consumer<ScheduledTask>() {
                    @Override
                    public void accept(ScheduledTask scheduledTask) {
                        if (gameManager.getGameStatus() != GameStatus.PLAYING) {
                            scheduledTask.cancel();
                            return;
                        }
                        
                        // Made in Heaven时间加速支持
                        int multiplier = gameManager.getTimeAccelerationMultiplier();
                        borderShrinkCountdown -= multiplier;
                        
                        if (borderShrinkCountdown <= 0) {
                            scheduledTask.cancel();
                            if (gameManager.getGameStatus() == GameStatus.PLAYING) {
                                runBorderShrinkCycle(world);
                            }
                        }
                    }
                }, 20L, 20L
            );
        }
    }
    
    /**
     * 停止边界收缩
     */
    public void stop() {
        if (borderCountdownTask != null) {
            borderCountdownTask.cancel();
            borderCountdownTask = null;
        }
        if (borderShrinkTask != null) {
            borderShrinkTask.cancel();
            borderShrinkTask = null;
        }
    }
    
    /**
     * 重置边界
     */
    public void resetBorder(World world) {
        if (world != null) {
            org.bukkit.WorldBorder worldBorder = world.getWorldBorder();
            worldBorder.setCenter(100, 100);
            worldBorder.setSize(20000.0);
        }
    }
    
    /**
     * 扩展边界（游戏结束时）
     */
    public void expandBorder(World world, Location center) {
        if (world != null) {
            org.bukkit.WorldBorder worldBorder = world.getWorldBorder();
            worldBorder.setCenter(center);
            worldBorder.setSize(10000);
        }
    }
    
    /**
     * 获取剩余倒计时
     */
    public int getBorderShrinkCountdown() {
        return borderShrinkCountdown;
    }
    
    /**
     * 设置初始等待时间
     */
    public void setBorderTimer(int timer) {
        this.borderTimer = timer;
    }
}
