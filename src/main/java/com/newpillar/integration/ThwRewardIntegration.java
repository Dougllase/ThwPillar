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
            thwRewardPlugin = Bukkit.getPluginManager().getPlugin("ThwReward");
            
            if (thwRewardPlugin == null) {
                plugin.getLogger().info("未检测到 ThwReward 子插件，奖励功能将使用默认逻辑");
                return;
            }
            
            if (!thwRewardPlugin.isEnabled()) {
                plugin.getLogger().warning("ThwReward 子插件未启用");
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
            // 如果插件未加载、未启用，或者 gameRewardManager 为空，都重新初始化
            if (thwRewardPlugin == null || !thwRewardPlugin.isEnabled() || gameRewardManager == null) {
                plugin.getLogger().info("[调试] 尝试重新初始化 ThwReward 集成...");
                initialize();
                plugin.getLogger().info("[调试] 重新初始化完成，enabled=" + enabled);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "[调试] 重新初始化失败: " + e.getMessage());
        }
    }
    
    /**
     * 游戏开始
     */
    public void onGameStart(int gameId) {
        plugin.getLogger().info("[调试] ThwRewardIntegration.onGameStart 被调用, gameId=" + gameId + ", enabled=" + enabled);
        
        if (!isEnabled()) {
            plugin.getLogger().warning("[调试] ThwReward 未启用，无法通知游戏开始");
            return;
        }
        
        try {
            Class<?> managerClass = gameRewardManager.getClass();
            Method onGameStart = managerClass.getMethod("onGameStart", int.class);
            onGameStart.invoke(gameRewardManager, gameId);
            plugin.getLogger().info("[调试] 已通知 ThwReward 游戏开始: " + gameId);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[调试] 调用 ThwReward.onGameStart 失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 游戏结束
     */
    public void onGameEnd(int gameId, boolean isWin, List<UUID> winners, List<UUID> participants) {
        plugin.getLogger().info("[调试] ThwRewardIntegration.onGameEnd 被调用, gameId=" + gameId + ", enabled=" + enabled);
        
        if (!isEnabled()) {
            plugin.getLogger().warning("[调试] ThwReward 未启用，无法通知游戏结束");
            return;
        }
        
        try {
            Class<?> managerClass = gameRewardManager.getClass();
            Method onGameEnd = managerClass.getMethod("onGameEnd", int.class, boolean.class, List.class, List.class);
            onGameEnd.invoke(gameRewardManager, gameId, isWin, winners, participants);
            plugin.getLogger().info("[调试] 已通知 ThwReward 游戏结束: " + gameId);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[调试] 调用 ThwReward.onGameEnd 失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 玩家出局（被淘汰）
     */
    public void onPlayerEliminated(Player player) {
        onPlayerEliminated(player, null);
    }
    
    /**
     * 玩家出局（被淘汰）- 带死亡原因
     * @param player 玩家
     * @param deathCause 死亡原因（用于消极游戏检测）
     */
    public void onPlayerEliminated(Player player, String deathCause) {
        if (!isEnabled()) return;
        
        try {
            Class<?> managerClass = gameRewardManager.getClass();
            Method onPlayerEliminated = managerClass.getMethod("onPlayerEliminated", Player.class, String.class);
            onPlayerEliminated.invoke(gameRewardManager, player, deathCause);
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "调用 ThwReward.onPlayerEliminated 失败: " + e.getMessage());
        }
    }
    
    /**
     * 玩家击杀 - 直接调用 ThwReward 处理击杀奖励
     * 在 playerOut 之前调用，确保游戏结束前奖励被正确记录
     */
    public void onPlayerKill(Player killer, Player victim) {
        if (!isEnabled()) {
            plugin.getLogger().log(Level.FINE, "ThwReward 未启用，跳过击杀奖励: " + killer.getName() + " -> " + victim.getName());
            return;
        }
        
        try {
            Class<?> managerClass = gameRewardManager.getClass();
            Method onPlayerKill = managerClass.getMethod("onPlayerKill", Player.class, Player.class);
            onPlayerKill.invoke(gameRewardManager, killer, victim);
            plugin.getLogger().info("[调试] 已通知 ThwReward 击杀奖励: " + killer.getName() + " -> " + victim.getName());
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[调试] 调用 ThwReward.onPlayerKill 失败: " + e.getMessage());
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
    
    /**
     * 玩家提前退出游戏（未出局就退出）
     * 扣除所有本局收益
     */
    public void onPlayerQuitEarly(Player player) {
        if (!isEnabled()) return;
        
        try {
            Class<?> managerClass = gameRewardManager.getClass();
            // ThwReward 中使用的是 onPlayerQuit 方法名
            Method onPlayerQuit = managerClass.getMethod("onPlayerQuit", Player.class);
            onPlayerQuit.invoke(gameRewardManager, player);
            plugin.getLogger().info("[调试] 已通知 ThwReward 玩家提前退出: " + player.getName());
        } catch (NoSuchMethodException e) {
            // ThwReward 旧版本没有此方法，降级处理
            plugin.getLogger().fine("ThwReward 版本不支持 onPlayerQuit，跳过处理");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[调试] 调用 ThwReward.onPlayerQuit 失败: " + e.getMessage());
        }
    }
    
    /**
     * 广播游戏结束消息到其他服务器（生存服）
     * 使用主插件的 MessageServiceIntegration 发送，避免重复发送给本地玩家
     * @param message 要广播的消息
     * @param targetPlayers 目标玩家名列表（上一局游玩的玩家），null表示发送给所有玩家
     */
    public void broadcastGameEndToOtherServers(String message, List<String> targetPlayers) {
        // 使用主插件的 MessageServiceIntegration 发送跨服消息
        // 这样只会发送给其他服务器，不会重复发送给本地玩家
        var messageServiceIntegration = plugin.getMessageServiceIntegration();
        if (messageServiceIntegration != null && messageServiceIntegration.isEnabled()) {
            // 只发送给其他服务器，不发送本地消息（本地消息已经在 GameManager 中发送过了）
            messageServiceIntegration.sendToOtherServers(message, null, targetPlayers);
            plugin.getLogger().info("已发送游戏结束消息到其他服务器，目标玩家: " + targetPlayers);
        } else {
            plugin.getLogger().warning("MessageServiceIntegration 未启用，无法发送跨服游戏结束消息");
        }
    }
}
