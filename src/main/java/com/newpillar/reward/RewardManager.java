package com.newpillar.reward;

import com.newpillar.NewPillar;
import com.newpillar.reward.database.RewardDatabaseManager;
import com.newpillar.reward.limit.LimitCheckManager;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 奖励管理器
 * 负责游戏内奖励消息发送、限制检查和招人功能
 */
public class RewardManager {
    
    private final NewPillar plugin;
    private RewardDatabaseManager databaseManager;
    private LimitCheckManager limitCheckManager;
    
    // 本局游戏奖励数据缓存
    private final Map<UUID, GameSessionData> sessionDataMap = new ConcurrentHashMap<>();
    
    // 游戏状态
    private int currentGameId = 0;
    private boolean gameRunning = false;
    private long gameStartTime = 0;
    
    // 配置值
    private int winReward;
    private int lossReward;
    private int killReward;
    private int killMaxPerGame;
    private int dailyLimit;
    private int weeklyLimit;
    
    // 招人系统
    private RecruitmentManager recruitmentManager;
    
    public RewardManager(NewPillar plugin) {
        this.plugin = plugin;
        loadConfig();
        
        // 初始化数据库管理器
        this.databaseManager = new RewardDatabaseManager(plugin);
        
        // 检查数据库连接
        if (!this.databaseManager.isConnected()) {
            plugin.getLogger().severe("========================================");
            plugin.getLogger().severe("[奖励系统] 数据库连接失败！");
            plugin.getLogger().severe("[奖励系统] 奖励限制功能将不可用！");
            plugin.getLogger().severe("========================================");
        } else {
            // 初始化限制检查管理器
            this.limitCheckManager = new LimitCheckManager(plugin, databaseManager);
            plugin.getLogger().info("[奖励系统] 限制检查系统已初始化");
        }
        
        this.recruitmentManager = new RecruitmentManager(plugin);
    }
    
    private void loadConfig() {
        var config = plugin.getConfig();
        // 从配置中获取奖励配置
        this.winReward = config.getInt("thwreward.rewards.win", 100);
        this.lossReward = config.getInt("thwreward.rewards.loss", 50);
        this.killReward = config.getInt("thwreward.rewards.kill", 50);
        this.killMaxPerGame = config.getInt("thwreward.rewards.kill-max-per-game", 300);
        this.dailyLimit = config.getInt("thwreward.limits.daily", 2500);
        this.weeklyLimit = config.getInt("thwreward.limits.weekly", 5000);
    }
    
    /**
     * 游戏开始
     */
    public void onGameStart(int gameId) {
        this.currentGameId = gameId;
        this.gameRunning = true;
        this.gameStartTime = System.currentTimeMillis();
        this.sessionDataMap.clear();
        
        plugin.getLogger().info("[奖励系统] 游戏 #" + gameId + " 开始，奖励系统已激活");
        
        // 启动限制检查系统
        if (limitCheckManager != null) {
            limitCheckManager.onGameStart(gameId);
        }
        
        // 启动招人系统
        recruitmentManager.onGameStart(gameId);
        
        // 提醒所有在线玩家金币上限状态
        checkAndNotifyLimitsForAllPlayers();
    }
    
    /**
     * 检查并通知所有在线玩家金币上限状态
     */
    private void checkAndNotifyLimitsForAllPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            checkAndNotifyLimit(player);
        }
    }
    
    /**
     * 检查并通知玩家金币上限状态
     */
    private void checkAndNotifyLimit(Player player) {
        if (databaseManager == null || !databaseManager.isConnected()) {
            // 数据库未连接，使用默认提示
            player.sendMessage("§a§l[金币上限] §7今日还可获取 §a" + dailyLimit + "§7/§c" + dailyLimit + " §7金币");
            return;
        }
        
        var limitData = databaseManager.getPlayerLimitData(player.getUniqueId());
        limitData.checkAndResetLimits();
        
        int dailyEarned = limitData.getDailyEarned();
        int weeklyEarned = limitData.getWeeklyEarned();
        int dailyRemaining = Math.max(0, dailyLimit - dailyEarned);
        int weeklyRemaining = Math.max(0, weeklyLimit - weeklyEarned);
        
        // 检查是否已达上限
        if (dailyEarned >= dailyLimit) {
            player.sendMessage("§c§l[金币上限] §7你已达到今日金币获取上限（§c" + dailyLimit + "§7），本局游戏无法继续累积金币！");
        } else if (weeklyEarned >= weeklyLimit) {
            player.sendMessage("§c§l[金币上限] §7你已达到本周金币获取上限（§c" + weeklyLimit + "§7），本局游戏无法继续累积金币！");
        } else {
            // 显示剩余可获取数量
            player.sendMessage("§a§l[金币上限] §7今日还可获取 §a" + dailyRemaining + "§7/§c" + dailyLimit + " §7金币");
            if (weeklyRemaining < weeklyLimit * 0.2) {
                player.sendMessage("§e§l[金币上限] §7本周剩余: §e" + weeklyRemaining + "§7/§c" + weeklyLimit + " §7金币");
            }
        }
    }
    
    /**
     * 游戏结束
     */
    public void onGameEnd(int gameId, boolean isWin, List<UUID> winners, List<UUID> participants) {
        if (this.currentGameId != gameId) {
            return;
        }

        this.gameRunning = false;
        
        // 记录游戏历史并检查消极游戏
        if (limitCheckManager != null) {
            limitCheckManager.onGameEnd(gameId, gameStartTime, participants);
        }

        for (UUID playerUuid : participants) {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player == null) continue;

            GameSessionData sessionData = sessionDataMap.computeIfAbsent(playerUuid, uuid -> {
                GameSessionData data = new GameSessionData(uuid);
                data.setGameStartTime(gameStartTime);
                return data;
            });
            
            // 跳过已经结算过的玩家（在onPlayerEliminated中已结算）
            if (sessionData.isEliminated()) {
                continue;
            }
            
            // 胜负奖励 - 实时记录到数据库
            boolean isWinner = winners != null && winners.contains(playerUuid);
            int victoryReward = isWinner ? winReward : lossReward;
            int actualVictoryReward = 0;
            
            // 实时记录胜负奖励到数据库（避免重复记录已出局的玩家）
            if (!sessionData.isEliminated() && limitCheckManager != null && victoryReward > 0) {
                actualVictoryReward = limitCheckManager.recordVictoryReward(player, victoryReward, isWinner);
            }
            
            sessionData.setVictoryReward(actualVictoryReward);
            sessionData.setWin(isWinner);

            // 计算总奖励
            int totalReward = sessionData.getKillRewards() + actualVictoryReward;

            // 发送提示
            String winMsg = "§6§l胜利！金币+%amount%";
            String lossMsg = "§7失败。金币+%amount%";
            String msg = isWinner ? winMsg : lossMsg;
            player.sendMessage(msg.replace("%amount%", String.valueOf(actualVictoryReward)));
            player.sendMessage("§6§l本局总计: §e" + totalReward + " §6金币");
            player.sendMessage("§7使用 §f/reward §7查看和领取奖励");
            
            // 实时记录待领取奖励到数据库（避免重复记录已出局的玩家）
            if (!sessionData.isEliminated()) {
                recordPendingRewardToDatabase(playerUuid, sessionData);
            }
        }
        
        // 清理本局数据
        sessionDataMap.clear();
        
        // 重置招人系统
        recruitmentManager.onGameEnd(gameId);
        
        plugin.getLogger().info("[奖励系统] 游戏 #" + gameId + " 结束，奖励消息已发送");
    }
    
    /**
     * 玩家击杀 - 发送击杀奖励消息并进行限制检查
     */
    public void onPlayerKill(Player killer, Player victim) {
        if (!gameRunning) return;
        
        UUID killerUuid = killer.getUniqueId();
        
        // 限制检查
        if (limitCheckManager != null) {
            LimitCheckManager.CheckResult result = limitCheckManager.canEarnKillReward(killer, victim);
            if (!result.isAllowed()) {
                killer.sendMessage(result.getReason());
                return;
            }
        }
        
        // 获取本局数据
        GameSessionData sessionData = sessionDataMap.computeIfAbsent(killerUuid, uuid -> {
            GameSessionData data = new GameSessionData(uuid);
            data.setGameStartTime(gameStartTime);
            return data;
        });
        
        // 检查击杀奖励上限
        if (sessionData.getKillRewards() >= killMaxPerGame) {
            killer.sendMessage("§c本局击杀奖励已达上限！");
            return;
        }
        
        // 计算实际奖励
        int actualReward = Math.min(killReward, killMaxPerGame - sessionData.getKillRewards());
        
        // 记录奖励（更新数据库和缓存）
        if (limitCheckManager != null) {
            limitCheckManager.recordKillReward(killer, actualReward);
        }
        
        // 添加击杀奖励到sessionData
        sessionData.addKillReward(actualReward, victim.getName());
        
        // 发送提示
        String msg = "§a击杀！金币+%amount%";
        killer.sendMessage(msg.replace("%amount%", String.valueOf(actualReward)));
        
        // ActionBar提示
        int currentTotal = sessionData.getTotalReward();
        killer.sendActionBar(net.kyori.adventure.text.Component.text("§6本局: " + currentTotal + " 金币"));
    }
    
    /**
     * 玩家出局（被淘汰）
     * 发送失败奖励消息，并实时记录到数据库
     */
    public void onPlayerEliminated(Player player, String deathCause) {
        if (!gameRunning) return;
        
        UUID playerUuid = player.getUniqueId();
        GameSessionData sessionData = sessionDataMap.computeIfAbsent(playerUuid, uuid -> {
            GameSessionData data = new GameSessionData(uuid);
            data.setGameStartTime(gameStartTime);
            return data;
        });

        // 如果已经处理过（已出局或已退出），不再处理
        if (sessionData.isEliminated() || sessionData.isQuitEarly()) {
            return;
        }

        sessionData.setEliminated(true);

        // 记录死亡原因（用于消极游戏检测）
        if (limitCheckManager != null) {
            limitCheckManager.onPlayerDeath(player, deathCause);
        }

        // 获取失败奖励并实时记录到数据库
        int lossReward = this.lossReward;
        int actualLossReward = 0;
        
        // 实时记录胜负奖励到数据库
        if (limitCheckManager != null && lossReward > 0) {
            actualLossReward = limitCheckManager.recordVictoryReward(player, lossReward, false);
        }
        
        sessionData.setVictoryReward(actualLossReward);
        sessionData.setWin(false);

        // 发送出局提示
        int totalKillRewards = sessionData.getKillRewards();
        int totalReward = totalKillRewards + actualLossReward;
        
        if (actualLossReward > 0) {
            String lossMsg = "§7失败。金币+%amount%";
            player.sendMessage(lossMsg.replace("%amount%", String.valueOf(actualLossReward)));
        }
        
        player.sendMessage("§e你已出局！ §7本局击杀收益: §6" + totalKillRewards + " §7金币");
        player.sendMessage("§6§l本局总计: §e" + totalReward + " §6金币");
        player.sendMessage("§7使用 §f/reward §7查看和领取奖励");
        player.sendMessage("§7你可以随时退出游戏");
        
        // 实时记录待领取奖励到数据库
        recordPendingRewardToDatabase(playerUuid, sessionData);
    }
    
    /**
     * 玩家主动退出游戏（未出局就退出）
     * 扣除所有本局收益
     */
    public void onPlayerQuitEarly(Player player) {
        if (!gameRunning) return;

        UUID playerUuid = player.getUniqueId();
        GameSessionData sessionData = sessionDataMap.get(playerUuid);
        
        // 如果没有数据，或者已经出局/退出，不处理
        if (sessionData == null || sessionData.isEliminated() || sessionData.isQuitEarly()) {
            return;
        }

        // 标记为提前退出
        sessionData.setQuitEarly(true);
        
        // 从数据库扣除击杀收益
        int deductedAmount = 0;
        if (limitCheckManager != null) {
            deductedAmount = limitCheckManager.deductEarlyQuitRewards(player, currentGameId);
        }
        
        // 本地缓存清零
        int lostKillRewards = sessionData.getKillRewards();
        sessionData.clearKillRewards();
        
        // 发送提示
        player.sendMessage("§c你中途退出了游戏！");
        if (lostKillRewards > 0) {
            player.sendMessage("§c本局击杀收益 " + lostKillRewards + " 金币已被扣除！");
        }
        player.sendMessage("§7使用 §f/reward §7查看奖励状态");
        
        plugin.getLogger().info("[奖励系统] 玩家 " + player.getName() + " 提前退出，扣除 " + deductedAmount + " 金币");
    }
    
    /**
     * 玩家加入游戏
     */
    public void onPlayerJoin(Player player) {
        // 初始化玩家会话数据
        if (limitCheckManager != null) {
            limitCheckManager.onPlayerJoinGame(player);
        }
        recruitmentManager.onPlayerJoin(player);
    }
    
    /**
     * 玩家退出游戏
     */
    public void onPlayerQuit(Player player) {
        // 记录断线（用于断线检测）
        if (limitCheckManager != null) {
            limitCheckManager.onPlayerDisconnect(player);
        }
        recruitmentManager.onPlayerQuit(player);
    }
    
    /**
     * 获取招人管理器
     */
    public RecruitmentManager getRecruitmentManager() {
        return recruitmentManager;
    }
    
    /**
     * 获取数据库管理器
     */
    public RewardDatabaseManager getDatabaseManager() {
        return databaseManager;
    }
    
    /**
     * 获取限制检查管理器
     */
    public LimitCheckManager getLimitCheckManager() {
        return limitCheckManager;
    }
    
    /**
     * 获取当前游戏ID
     */
    public int getCurrentGameId() {
        return currentGameId;
    }
    
    /**
     * 检查游戏是否进行中
     */
    public boolean isGameRunning() {
        return gameRunning;
    }
    
    /**
     * 关闭奖励管理器
     */
    public void shutdown() {
        if (databaseManager != null) {
            databaseManager.close();
        }
    }
    
    /**
     * 记录待领取奖励到数据库
     */
    private void recordPendingRewardToDatabase(UUID playerUuid, GameSessionData sessionData) {
        if (databaseManager == null || !databaseManager.isConnected()) {
            return;
        }
        
        int killRewards = sessionData.getKillRewards();
        int victoryReward = sessionData.getVictoryReward();
        
        // 构建击杀明细
        StringBuilder detailsBuilder = new StringBuilder();
        List<String> killDetails = sessionData.getKillDetails();
        for (int i = 0; i < killDetails.size(); i++) {
            detailsBuilder.append(killDetails.get(i));
            if (i < killDetails.size() - 1) {
                detailsBuilder.append(";");
            }
        }
        
        databaseManager.addPendingReward(playerUuid, killRewards, victoryReward, 
                detailsBuilder.toString(), sessionData.getGameStartTime(), sessionData.isWin());
        
        plugin.getLogger().info("[奖励系统] 玩家 " + playerUuid + " 的待领取奖励已记录到数据库: 击杀=" + 
                killRewards + ", 胜负=" + victoryReward);
    }
    
    // ==================== 游戏会话数据 ====================
    
    public static class GameSessionData {
        private final UUID playerUuid;
        private int killRewards;
        private int victoryReward;
        private int totalReward;
        private boolean isWin;
        private boolean eliminated;
        private boolean quitEarly;
        private long gameStartTime;
        private final List<String> killDetails;
        
        public GameSessionData(UUID playerUuid) {
            this.playerUuid = playerUuid;
            this.killRewards = 0;
            this.victoryReward = 0;
            this.totalReward = 0;
            this.isWin = false;
            this.eliminated = false;
            this.quitEarly = false;
            this.killDetails = new ArrayList<>();
        }
        
        public void addKillReward(int amount, String victimName) {
            this.killRewards += amount;
            this.killDetails.add(victimName + "(" + amount + ")");
            updateTotal();
        }
        
        public void clearKillRewards() {
            this.killRewards = 0;
            this.killDetails.clear();
            updateTotal();
        }
        
        public void setVictoryReward(int amount) {
            this.victoryReward = amount;
            updateTotal();
        }
        
        private void updateTotal() {
            this.totalReward = this.killRewards + this.victoryReward;
        }
        
        // Getters
        public UUID getPlayerUuid() { return playerUuid; }
        public int getKillRewards() { return killRewards; }
        public int getVictoryReward() { return victoryReward; }
        public int getTotalReward() { return totalReward; }
        public boolean isWin() { return isWin; }
        public void setWin(boolean win) { isWin = win; }
        public boolean isEliminated() { return eliminated; }
        public void setEliminated(boolean eliminated) { this.eliminated = eliminated; }
        public boolean isQuitEarly() { return quitEarly; }
        public void setQuitEarly(boolean quitEarly) { this.quitEarly = quitEarly; }
        public long getGameStartTime() { return gameStartTime; }
        public void setGameStartTime(long gameStartTime) { this.gameStartTime = gameStartTime; }
        public List<String> getKillDetails() { return new ArrayList<>(killDetails); }
    }
}
