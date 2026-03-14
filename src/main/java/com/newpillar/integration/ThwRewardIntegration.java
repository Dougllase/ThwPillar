package com.newpillar.integration;

import com.newpillar.NewPillar;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * ThwReward 子插件集成管理器
 * 用于与奖励系统进行通信，子插件缺失时自动降级
 */
public class ThwRewardIntegration {
    
    private final NewPillar plugin;
    private Plugin thwRewardPlugin;
    private Object gameRewardManager;
    private boolean enabled = false;
    
    public ThwRewardIntegration(NewPillar plugin) {
        this.plugin = plugin;
    }
    
    /**
     * 初始化 ThwReward 集成
     */
    public void initialize() {
        try {
            thwRewardPlugin = Bukkit.getPluginManager().getPlugin("ThwNewPillarRewards");
            
            if (thwRewardPlugin == null) {
                plugin.getLogger().info("未检测到 ThwNewPillarRewards 子插件，奖励功能将使用默认逻辑");
                return;
            }
            
            if (!thwRewardPlugin.isEnabled()) {
                plugin.getLogger().warning("ThwNewPillarRewards 子插件未启用");
                return;
            }
            
            // 获取 GameRewardManager
            Class<?> pluginClass = thwRewardPlugin.getClass();
            Method getGameRewardManager = pluginClass.getMethod("getGameRewardManager");
            this.gameRewardManager = getGameRewardManager.invoke(thwRewardPlugin);
            
            if (this.gameRewardManager == null) {
                plugin.getLogger().warning("无法获取 ThwReward 的 GameRewardManager");
                return;
            }
            
            enabled = true;
            plugin.getLogger().info("ThwNewPillarRewards 集成已启用，奖励系统将协同工作");
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "初始化 ThwNewPillarRewards 集成失败: " + e.getMessage());
            enabled = false;
        }
    }
    
    /**
     * 是否已启用
     */
    public boolean isEnabled() {
        if (!enabled || gameRewardManager == null) {
            tryReinitialize();
        }
        return enabled && gameRewardManager != null && thwRewardPlugin != null && thwRewardPlugin.isEnabled();
    }
    
    /**
     * 尝试重新初始化
     */
    private void tryReinitialize() {
        try {
            if (thwRewardPlugin == null || !thwRewardPlugin.isEnabled()) {
                initialize();
            }
        } catch (Exception e) {
            // 忽略
        }
    }
    
    /**
     * 游戏开始
     */
    public void onGameStart(int gameId) {
        if (!isEnabled()) return;
        
        try {
            Class<?> managerClass = gameRewardManager.getClass();
            Method onGameStart = managerClass.getMethod("onGameStart", int.class);
            onGameStart.invoke(gameRewardManager, gameId);
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "调用 ThwReward.onGameStart 失败: " + e.getMessage());
        }
    }
    
    /**
     * 游戏结束
     */
    public void onGameEnd(int gameId, boolean isWin, List<UUID> winners, List<UUID> participants) {
        if (!isEnabled()) return;
        
        try {
            Class<?> managerClass = gameRewardManager.getClass();
            Method onGameEnd = managerClass.getMethod("onGameEnd", int.class, boolean.class, List.class, List.class);
            onGameEnd.invoke(gameRewardManager, gameId, isWin, winners, participants);
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "调用 ThwReward.onGameEnd 失败: " + e.getMessage());
        }
    }
    
    /**
     * 玩家出局（被淘汰）
     */
    public void onPlayerEliminated(Player player) {
        if (!isEnabled()) return;
        
        try {
            Class<?> managerClass = gameRewardManager.getClass();
            Method onPlayerEliminated = managerClass.getMethod("onPlayerEliminated", Player.class);
            onPlayerEliminated.invoke(gameRewardManager, player);
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "调用 ThwReward.onPlayerEliminated 失败: " + e.getMessage());
        }
    }
    
    /**
     * 玩家击杀
     */
    public void onPlayerKill(Player killer, Player victim) {
        if (!isEnabled()) return;
        
        try {
            Class<?> managerClass = gameRewardManager.getClass();
            Method onPlayerKill = managerClass.getMethod("onPlayerKill", Player.class, Player.class);
            onPlayerKill.invoke(gameRewardManager, killer, victim);
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "调用 ThwReward.onPlayerKill 失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取待领取奖励数量（用于显示）
     */
    public int getPendingReward(UUID playerUuid) {
        if (!isEnabled()) return 0;
        
        try {
            Class<?> pluginClass = thwRewardPlugin.getClass();
            Method getRewardSystem = pluginClass.getMethod("getRewardSystem");
            Object rewardSystem = getRewardSystem.invoke(thwRewardPlugin);
            
            if (rewardSystem == null) return 0;
            
            Class<?> rewardSystemClass = rewardSystem.getClass();
            Method getPendingReward = rewardSystemClass.getMethod("getPendingReward", UUID.class);
            Object result = getPendingReward.invoke(rewardSystem, playerUuid);
            
            return result instanceof Integer ? (Integer) result : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
