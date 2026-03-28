package com.newpillar.reward.limit;

import com.newpillar.NewPillar;
import com.newpillar.reward.database.PlayerLimitData;
import com.newpillar.reward.database.PlayerSessionData;
import com.newpillar.reward.database.RewardDatabaseManager;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 限制检查管理器
 * 统一管理金币上限、消极游戏、击杀有效性等限制
 */
public class LimitCheckManager {
    private final NewPillar plugin;
    private final RewardDatabaseManager databaseManager;
    
    // 配置
    private final int dailyLimit;
    private final int weeklyLimit;
    private final int killMaxPerGame;
    private final int disconnectThreshold;
    private final long disconnectWindowMs;
    private final int negativeGameThreshold;
    private final long negativeGameWindowMs;
    private final long penaltyDurationMs;
    private final int minGameDurationSeconds;
    
    // 本局游戏数据缓存
    private final Map<UUID, PlayerSessionData> sessionDataMap = new ConcurrentHashMap<>();
    
    public LimitCheckManager(NewPillar plugin, RewardDatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        
        // 加载配置
        var config = plugin.getConfig();
        this.dailyLimit = config.getInt("thwreward.limits.daily", 2500);
        this.weeklyLimit = config.getInt("thwreward.limits.weekly", 5000);
        this.killMaxPerGame = config.getInt("thwreward.rewards.kill-max-per-game", 300);
        this.disconnectThreshold = config.getInt("thwreward.anticheat.disconnect-threshold", 3);
        this.disconnectWindowMs = config.getLong("thwreward.anticheat.disconnect-window-minutes", 30) * 60 * 1000;
        this.negativeGameThreshold = config.getInt("thwreward.anticheat.negative-game-threshold", 4);
        this.negativeGameWindowMs = 30 * 60 * 1000; // 30分钟
        this.penaltyDurationMs = 60 * 60 * 1000; // 1小时
        this.minGameDurationSeconds = 2 * 60; // 2分钟
    }
    
    /**
     * 游戏开始，初始化玩家会话数据
     */
    public void onGameStart(int gameId) {
        sessionDataMap.clear();
        plugin.getLogger().info("[限制检查] 游戏 #" + gameId + " 开始，已清空上局数据");
    }
    
    /**
     * 玩家加入游戏
     */
    public void onPlayerJoinGame(Player player) {
        UUID playerUuid = player.getUniqueId();
        
        // 检查是否在断线宽限期内重新加入
        boolean wasInGracePeriod = databaseManager.onPlayerRejoin(playerUuid);
        if (wasInGracePeriod) {
            plugin.getLogger().info("[限制检查] 玩家 " + player.getName() + " 在断线宽限期内重新加入，不计入断线次数");
        }
        
        // 检查是否有恶意断线记录（超过宽限期未重新加入）
        if (databaseManager.hasMaliciousDisconnect(playerUuid)) {
            player.sendMessage("§c§l[警告] §7检测到您在此前的对局中途退出，请不要消极游戏。");
            databaseManager.clearMaliciousDisconnectFlag(playerUuid);
            plugin.getLogger().info("[限制检查] 玩家 " + player.getName() + " 有恶意断线记录，已发送警告");
        }
        
        sessionDataMap.put(playerUuid, new PlayerSessionData(playerUuid, player.getName()));
    }
    
    /**
     * 综合检查：玩家是否可以获取击杀奖励
     * @return 检查结果对象，包含是否可以获取以及原因
     */
    public CheckResult canEarnKillReward(Player killer, Player victim) {
        UUID killerUuid = killer.getUniqueId();
        UUID victimUuid = victim.getUniqueId();
        
        // 1. 检查金币上限
        PlayerLimitData limitData = databaseManager.getPlayerLimitData(killerUuid);
        limitData.checkAndResetLimits();
        
        if (limitData.getDailyEarned() >= dailyLimit) {
            return CheckResult.fail("§c你已达到今日金币获取上限（" + dailyLimit + "），无法继续获得金币！");
        }
        if (limitData.getWeeklyEarned() >= weeklyLimit) {
            return CheckResult.fail("§c你已达到本周金币获取上限（" + weeklyLimit + "），无法继续获得金币！");
        }
        
        // 2. 检查消极游戏惩罚
        if (databaseManager.isPenaltyActive(killerUuid)) {
            return CheckResult.fail("§c你因消极游戏被冻结奖励，暂时无法获得金币！");
        }
        
        // 3. 检查断线次数
        int disconnectCount = databaseManager.getDisconnectCount(killerUuid, disconnectWindowMs);
        if (disconnectCount >= disconnectThreshold) {
            return CheckResult.fail("§c你断线次数过多（" + disconnectCount + "次），暂时无法获得金币！");
        }
        
        // 4. 检查本局击杀上限
        PlayerSessionData sessionData = sessionDataMap.get(killerUuid);
        if (sessionData == null) {
            sessionData = new PlayerSessionData(killerUuid, killer.getName());
            sessionDataMap.put(killerUuid, sessionData);
        }
        
        if (sessionData.getKillCount() >= killMaxPerGame) {
            return CheckResult.fail("§c你本局击杀奖励已达上限（" + killMaxPerGame + "次），无法继续获得金币！");
        }
        
        // 5. 检查是否重复击杀同一玩家
        if (!sessionData.recordKill(victimUuid)) {
            return CheckResult.fail("§c你已经击杀过该玩家，无法重复获得金币！");
        }
        
        return CheckResult.success();
    }
    
    /**
     * 记录击杀奖励（更新数据库和缓存）
     */
    public void recordKillReward(Player player, int amount) {
        UUID playerUuid = player.getUniqueId();
        
        // 更新数据库
        int actualAdded = databaseManager.addCoins(playerUuid, amount, dailyLimit, weeklyLimit);
        
        // 更新缓存
        PlayerSessionData sessionData = sessionDataMap.get(playerUuid);
        if (sessionData != null) {
            sessionData.addKillReward(actualAdded);
        }
        
        // 如果实际添加为0，说明已达上限
        if (actualAdded == 0 && amount > 0) {
            player.sendMessage("§c§l[金币上限] §7你已达到金币获取上限，本次击杀无法获得金币！");
        }
    }
    
    /**
     * 记录胜负奖励（实时写入数据库）
     * @param player 玩家
     * @param amount 奖励金额
     * @param isWin 是否胜利
     * @return 实际记录的金额（可能因上限而减少）
     */
    public int recordVictoryReward(Player player, int amount, boolean isWin) {
        UUID playerUuid = player.getUniqueId();
        
        // 更新数据库
        int actualAdded = databaseManager.addCoins(playerUuid, amount, dailyLimit, weeklyLimit);
        
        // 如果实际添加为0，说明已达上限
        if (actualAdded == 0 && amount > 0) {
            player.sendMessage("§c§l[金币上限] §7你已达到金币获取上限，本次胜负奖励无法获得金币！");
        }
        
        return actualAdded;
    }
    
    /**
     * 玩家死亡，记录死亡原因
     */
    public void onPlayerDeath(Player player, String deathCause) {
        PlayerSessionData sessionData = sessionDataMap.get(player.getUniqueId());
        if (sessionData != null) {
            sessionData.setDeathCause(deathCause);
        }
    }
    
    /**
     * 玩家断线
     */
    public void onPlayerDisconnect(Player player) {
        databaseManager.recordDisconnect(player.getUniqueId());
    }
    
    /**
     * 玩家提前退出，扣除本局击杀收益
     */
    public int deductEarlyQuitRewards(Player player, int gameId) {
        UUID playerUuid = player.getUniqueId();
        PlayerSessionData sessionData = sessionDataMap.get(playerUuid);
        
        if (sessionData == null || sessionData.getTotalKillReward() <= 0) {
            return 0;
        }
        
        int killReward = sessionData.getTotalKillReward();
        
        // 1. 从数据库扣除金币上限累计
        databaseManager.deductCoins(playerUuid, killReward);
        
        // 2. 从待领取奖励中移除
        databaseManager.removePendingKillRewards(playerUuid, killReward, sessionData.getGameStartTime());
        
        plugin.getLogger().info("[限制检查] 玩家 " + player.getName() + " 提前退出，扣除击杀收益: " + killReward);
        
        return killReward;
    }
    
    /**
     * 游戏结束，记录游戏历史并检查消极游戏
     */
    public void onGameEnd(int gameId, long gameStartTime, List<UUID> participants) {
        long gameEndTime = System.currentTimeMillis();
        
        for (UUID playerUuid : participants) {
            PlayerSessionData sessionData = sessionDataMap.get(playerUuid);
            if (sessionData == null) continue;
            
            int durationSeconds = sessionData.getGameDurationSeconds();
            String deathCause = sessionData.getDeathCause();
            
            // 判断是否为消极游戏：时长<2分钟 且 死亡原因为摔落或虚空
            boolean isNegativeGame = durationSeconds < minGameDurationSeconds && 
                    (deathCause.equals("FALL") || deathCause.equals("VOID"));
            
            // 记录游戏历史
            databaseManager.recordGameHistory(playerUuid, gameId, gameStartTime, gameEndTime, 
                    deathCause, isNegativeGame);
            
            // 如果是消极游戏，检查是否需要惩罚
            if (isNegativeGame) {
                checkAndApplyPenalty(playerUuid);
            }
        }
        
        // 清空本局数据
        sessionDataMap.clear();
        plugin.getLogger().info("[限制检查] 游戏 #" + gameId + " 结束，已记录游戏历史");
    }
    
    /**
     * 检查并应用消极游戏惩罚
     */
    private void checkAndApplyPenalty(UUID playerUuid) {
        int negativeGameCount = databaseManager.getNegativeGameCount(playerUuid, negativeGameWindowMs);
        
        if (negativeGameCount >= negativeGameThreshold) {
            long now = System.currentTimeMillis();
            long penaltyEndTime = now + penaltyDurationMs;
            
            databaseManager.addPenalty(playerUuid, now, penaltyEndTime, negativeGameCount, 
                    "30分钟内" + negativeGameCount + "局消极游戏");
            
            Player player = plugin.getServer().getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                player.sendMessage("§c§l[惩罚] §7你因消极游戏被冻结奖励1小时！");
            }
            
            plugin.getLogger().info("[限制检查] 玩家 " + playerUuid + " 因消极游戏被惩罚1小时");
        }
    }
    
    /**
     * 获取玩家本局击杀数
     */
    public int getPlayerKillCount(UUID playerUuid) {
        PlayerSessionData sessionData = sessionDataMap.get(playerUuid);
        return sessionData != null ? sessionData.getKillCount() : 0;
    }
    
    /**
     * 获取玩家本局击杀奖励总额
     */
    public int getPlayerKillReward(UUID playerUuid) {
        PlayerSessionData sessionData = sessionDataMap.get(playerUuid);
        return sessionData != null ? sessionData.getTotalKillReward() : 0;
    }
    
    /**
     * 检查结果类
     */
    public static class CheckResult {
        private final boolean allowed;
        private final String reason;
        
        private CheckResult(boolean allowed, String reason) {
            this.allowed = allowed;
            this.reason = reason;
        }
        
        public static CheckResult success() {
            return new CheckResult(true, null);
        }
        
        public static CheckResult fail(String reason) {
            return new CheckResult(false, reason);
        }
        
        public boolean isAllowed() {
            return allowed;
        }
        
        public String getReason() {
            return reason;
        }
    }
}
