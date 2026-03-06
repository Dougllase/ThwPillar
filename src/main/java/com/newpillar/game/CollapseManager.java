package com.newpillar.game;

import com.newpillar.NewPillar;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

/**
 * 平台崩溃管理器
 * 负责处理游戏平台的崩溃逻辑（半径缩小模式）
 */
public class CollapseManager {
    private final NewPillar plugin;
    private final GameManager gameManager;
    private final com.newpillar.utils.DebugLogger debugLogger;
    
    private ScheduledTask collapseTask;
    private int collapseTimes = 0;
    
    // 崩溃轮次配置
    private static final int MAX_COLLAPSE_ROUNDS = 4;
    private static final int BLOCKS_PER_TICK = 500;
    
    public CollapseManager(NewPillar plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.debugLogger = plugin.getDebugLogger();
    }
    
    /**
     * 开始平台崩溃（半径缩小模式）
     */
    public void startPlatformCollapse(World world) {
        if (this.collapseTimes >= MAX_COLLAPSE_ROUNDS) {
            return;
        }
        
        this.collapseTimes++;
        gameManager.broadcastMessage("§6§l[幸运之柱] §c平台开始崩溃！第 " + this.collapseTimes + "/" + MAX_COLLAPSE_ROUNDS + " 轮");
        debugLogger.debug("[CollapseManager] 平台崩溃第 " + this.collapseTimes + " 轮");
        
        // 定义每轮的半径范围
        int outerRadius, innerRadius;
        boolean isFinalRound = false;
        
        switch (this.collapseTimes) {
            case 1 -> {
                outerRadius = 100;
                innerRadius = 91;
                gameManager.broadcastMessage("§c§l91格半径外的方块开始消失！");
            }
            case 2 -> {
                outerRadius = 91;
                innerRadius = 61;
                gameManager.broadcastMessage("§c§l61-91格半径的方块开始消失！");
            }
            case 3 -> {
                outerRadius = 61;
                innerRadius = 31;
                gameManager.broadcastMessage("§c§l31-61格半径的方块开始消失！");
            }
            case 4 -> {
                outerRadius = 31;
                innerRadius = 0;
                isFinalRound = true;
                gameManager.broadcastMessage("§c§l最后阶段！平台将从外向内逐格崩溃！");
            }
            default -> { return; }
        }
        
        Location centerLoc = new Location(world, 0, 0, 0);
        
        if (!isFinalRound) {
            startRegularCollapseRound(world, centerLoc, outerRadius, innerRadius);
        } else {
            startFinalCollapseRound(world, centerLoc, outerRadius);
        }
    }
    
    /**
     * 前3轮：常规崩溃（破坏指定半径范围）
     */
    private void startRegularCollapseRound(World world, Location centerLoc, int outerRadius, int innerRadius) {
        int[] currentRadius = {outerRadius};
        final int finalInnerRadius = innerRadius;
        final int finalOuterRadius = outerRadius;
        
        this.collapseTask = Bukkit.getRegionScheduler().runAtFixedRate(plugin, centerLoc, scheduledTask -> {
            int blocksBroken = 0;
            int radius = currentRadius[0];
            
            // 在当前半径层破坏方块（整层Y轴）
            for (int x = -radius; x <= radius && blocksBroken < BLOCKS_PER_TICK; x++) {
                for (int z = -radius; z <= radius && blocksBroken < BLOCKS_PER_TICK; z++) {
                    double distance = Math.sqrt(x * x + z * z);
                    if (distance >= finalInnerRadius && distance < finalOuterRadius) {
                        // 破坏该位置的所有Y层方块
                        for (int y = -64; y <= 320; y++) {
                            world.getBlockAt(x, y, z).setType(Material.AIR);
                        }
                        blocksBroken++;
                    }
                }
            }
            
            currentRadius[0]--;
            
            // 检查是否完成本轮
            if (currentRadius[0] < finalInnerRadius || gameManager.getGameStatus() != GameStatus.PLAYING) {
                scheduledTask.cancel();
                debugLogger.debug("[CollapseManager] 第 " + collapseTimes + " 轮完成");
                
                // 延迟后开始下一轮
                if (collapseTimes < MAX_COLLAPSE_ROUNDS && gameManager.getGameStatus() == GameStatus.PLAYING) {
                    Bukkit.getRegionScheduler().runDelayed(plugin, centerLoc, task -> {
                        if (gameManager.getGameStatus() == GameStatus.PLAYING) {
                            startPlatformCollapse(world);
                        }
                    }, 100L); // 5秒间隔
                }
            }
        }, 0L, 1L);
    }
    
    /**
     * 第4轮：最终崩溃（逐格缩小）
     */
    private void startFinalCollapseRound(World world, Location centerLoc, int startRadius) {
        int[] currentRadius = {startRadius};
        
        this.collapseTask = Bukkit.getRegionScheduler().runAtFixedRate(plugin, centerLoc, scheduledTask -> {
            int radius = currentRadius[0];
            
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
            
            // 每5格广播一次
            if (radius % 5 == 0) {
                gameManager.broadcastMessage("§c§l平台崩溃中... 剩余半径: " + radius + " 格");
            }
            
            currentRadius[0]--;
            
            // 检查是否完全崩溃
            if (currentRadius[0] < 0 || gameManager.getGameStatus() != GameStatus.PLAYING) {
                scheduledTask.cancel();
                debugLogger.debug("[CollapseManager] 平台完全崩溃！");
                gameManager.broadcastMessage("§c§l平台已完全崩溃！");
            }
        }, 0L, 10L); // 每0.5秒缩小一格
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
}
