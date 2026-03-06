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
    
    // 配置参数
    private static final double INITIAL_SIZE = 100.0;
    private static final double MIN_SIZE = 30.0;
    private static final double SHRINK_AMOUNT = 10.0;
    private static final long SHRINK_DURATION = 15L; // 15秒动画
    private static final int SHRINK_INTERVAL = 20; // 20秒间隔
    
    public BorderManager(NewPillar plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.debugLogger = plugin.getDebugLogger();
        this.borderTimer = plugin.getConfig().getInt("timers.border_time", 51);
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
        debugLogger.debug("[BorderManager] 边界已初始化: 中心(" + center.getBlockX() + ", " + center.getBlockZ() + "), 大小: " + INITIAL_SIZE);
    }
    
    /**
     * 开始边界收缩流程
     */
    public void startBorderShrink(World world) {
        this.borderShrinkCountdown = this.borderTimer;
        Location centerLoc = world.getWorldBorder().getCenter();
        
        debugLogger.debug("[BorderManager] 开始边界收缩倒计时: " + this.borderShrinkCountdown + "秒");
        
        this.borderCountdownTask = Bukkit.getRegionScheduler().runAtFixedRate(
            plugin, centerLoc, new Consumer<ScheduledTask>() {
                @Override
                public void accept(ScheduledTask scheduledTask) {
                    if (gameManager.getGameStatus() != GameStatus.PLAYING) {
                        scheduledTask.cancel();
                        return;
                    }
                    
                    borderShrinkCountdown--;
                    
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
        
        if (currentSize >= MIN_SIZE) {
            double newSize = Math.max(currentSize - SHRINK_AMOUNT, MIN_SIZE);
            worldBorder.setSize(newSize, SHRINK_DURATION);
            gameManager.broadcastMessage("§6§l[幸运之柱] §c边界开始收缩！当前大小: " + (int)currentSize + " -> " + (int)newSize);
            debugLogger.debug("[BorderManager] 收缩: " + currentSize + " -> " + newSize);
            
            this.borderShrinkCountdown = SHRINK_INTERVAL;
            
            this.borderCountdownTask = Bukkit.getRegionScheduler().runAtFixedRate(
                plugin, centerLoc, new Consumer<ScheduledTask>() {
                    @Override
                    public void accept(ScheduledTask scheduledTask) {
                        if (gameManager.getGameStatus() != GameStatus.PLAYING) {
                            scheduledTask.cancel();
                            return;
                        }
                        
                        borderShrinkCountdown--;
                        
                        if (borderShrinkCountdown <= 0) {
                            scheduledTask.cancel();
                            if (gameManager.getGameStatus() == GameStatus.PLAYING) {
                                runBorderShrinkCycle(world);
                            }
                        }
                    }
                }, 20L, 20L
            );
        } else {
            // 边界达到最小值，触发平台崩溃
            debugLogger.debug("[BorderManager] 边界达到最小值，触发平台崩溃");
            gameManager.triggerPlatformCollapse();
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
