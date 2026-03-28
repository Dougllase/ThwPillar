package com.newpillar.game;

import com.newpillar.game.enums.GameStatus;

import com.newpillar.NewPillar;
import com.newpillar.utils.SchedulerUtils;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

/**
 * 平台崩溃管理器
 * 负责处理游戏平台的崩溃逻辑
 * 
 * 简化设计：
 * - 固定180秒倒计时
 * - 从60格缓慢清除到中心
 * - 每3秒清除1格（60格 / 180秒 = 每3秒1格）
 */
public class CollapseManager {
    private final NewPillar plugin;
    private final GameManager gameManager;
    private final com.newpillar.utils.DebugLogger debugLogger;
    
    private ScheduledTask collapseTask;
    private int collapseTimes = 0;
    private int currentRadius = START_RADIUS;  // 当前剩余格数
    private int remainingSeconds = COLLAPSE_DURATION_SECONDS;  // 剩余秒数
    
    // 崩溃配置 - 固定180秒
    private static final int COLLAPSE_DURATION_SECONDS = 180;  // 3分钟
    private static final int START_RADIUS = 60;                // 从60格开始
    private static final int END_RADIUS = 0;                   // 清除到0格
    private static final long TICKS_PER_SHRINK = 60L;          // 每3秒清除1格 (3 * 20 ticks)
    
    public CollapseManager(NewPillar plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.debugLogger = plugin.getDebugLogger();
    }
    
    /**
     * 开始平台崩溃
     * 固定180秒，从60格缓慢清除到中心
     */
    public void startPlatformCollapse(World world) {
        if (this.collapseTask != null) {
            return; // 已经在崩溃中
        }
        
        this.collapseTimes++;
        gameManager.broadcastMessage("§6§l[幸运之柱] §c平台开始崩溃！");
        gameManager.broadcastMessage("§c§l平台将在 §f180秒 §c内完全崩塌！快向中心移动！");
        debugLogger.debug("[CollapseManager] 平台崩溃开始，180秒倒计时，从60格到0格");
        
        Location centerLoc = new Location(world, 0, 0, 0);
        startCollapse(world, centerLoc);
    }
    
    /**
     * 平台崩溃核心逻辑
     * 每3秒清除1格，共60格 = 180秒
     */
    private void startCollapse(World world, Location centerLoc) {
        this.currentRadius = START_RADIUS;
        int[] secondsElapsed = {0};
        
        this.collapseTask = SchedulerUtils.runTimerOnLocation(centerLoc, 1L, TICKS_PER_SHRINK, scheduledTask -> {
            int radius = this.currentRadius;
            
            // 破坏当前半径层的所有方块（整圈）
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    double distance = Math.sqrt(x * x + z * z);
                    // 在当前半径±0.5范围内
                    if (distance >= radius - 0.5 && distance <= radius + 0.5) {
                        for (int y = -64; y <= 320; y++) {
                            world.getBlockAt(x, y, z).setType(Material.AIR);
                        }
                    }
                }
            }
            
            secondsElapsed[0] += 3; // 每3秒执行一次
            this.remainingSeconds = COLLAPSE_DURATION_SECONDS - secondsElapsed[0];
            
            // 只在特定里程碑广播（30秒间隔或每10格），减少聊天栏刷屏
            if (secondsElapsed[0] % 30 == 0 || radius % 10 == 0) {
                gameManager.broadcastMessage("§c§l平台正在崩塌... 剩余: " + radius + "格 (" + this.remainingSeconds + "秒)");
            }
            
            this.currentRadius--;
            
            // 检查是否完全崩溃
            if (this.currentRadius < END_RADIUS || gameManager.getGameStatus() != GameStatus.PLAYING) {
                SchedulerUtils.cancel(scheduledTask);
                debugLogger.debug("[CollapseManager] 平台完全崩溃！");
                gameManager.broadcastMessage("§c§l平台已完全崩溃！");
            }
        }); // 每3秒执行一次
    }
    
    /**
     * 停止崩溃
     */
    public void stop() {
        if (collapseTask != null) {
            collapseTask.cancel();
            collapseTask = null;
        }
    }
    
    /**
     * 重置崩溃次数
     */
    public void reset() {
        this.collapseTimes = 0;
        stop();
    }
    
    /**
     * 获取当前崩溃轮次
     */
    public int getCollapseTimes() {
        return collapseTimes;
    }
    
    /**
     * 检查是否正在平台崩溃阶段
     */
    public boolean isCollapseActive() {
        return collapseTask != null && collapseTimes > 0;
    }
    
    /**
     * 获取当前剩余格数
     */
    public int getCurrentRadius() {
        return currentRadius;
    }
    
    /**
     * 获取剩余秒数
     */
    public int getRemainingSeconds() {
        return remainingSeconds;
    }
}
