package com.newpillar.reward.database;

import java.util.*;

/**
 * 玩家本局游戏会话数据
 * 游戏过程中使用，游戏结束清空
 */
public class PlayerSessionData {
    private final UUID playerUuid;
    private String playerName;
    private int killCount;
    private int totalKillReward;
    private final Set<UUID> killedPlayers;
    private final Map<UUID, Long> lastKillTime;
    private long gameStartTime;
    private String deathCause;
    
    public PlayerSessionData(UUID playerUuid, String playerName) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.killCount = 0;
        this.totalKillReward = 0;
        this.killedPlayers = new HashSet<>();
        this.lastKillTime = new HashMap<>();
        this.gameStartTime = System.currentTimeMillis();
        this.deathCause = "";
    }
    
    /**
     * 记录击杀
     * @return 是否是有效击杀（不是重复刷同一玩家）
     */
    public boolean recordKill(UUID victimUuid) {
        // 检查是否已击杀过该玩家
        if (killedPlayers.contains(victimUuid)) {
            return false;
        }
        
        killedPlayers.add(victimUuid);
        killCount++;
        lastKillTime.put(victimUuid, System.currentTimeMillis());
        return true;
    }
    
    /**
     * 添加击杀奖励
     */
    public void addKillReward(int amount) {
        totalKillReward += amount;
    }
    
    /**
     * 扣除击杀奖励（用于提前退出）
     */
    public void deductKillReward(int amount) {
        totalKillReward = Math.max(0, totalKillReward - amount);
    }
    
    /**
     * 获取游戏时长（秒）
     */
    public int getGameDurationSeconds() {
        return (int) ((System.currentTimeMillis() - gameStartTime) / 1000);
    }
    
    // Getters and Setters
    public UUID getPlayerUuid() {
        return playerUuid;
    }
    
    public String getPlayerName() {
        return playerName;
    }
    
    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }
    
    public int getKillCount() {
        return killCount;
    }
    
    public int getTotalKillReward() {
        return totalKillReward;
    }
    
    public Set<UUID> getKilledPlayers() {
        return new HashSet<>(killedPlayers);
    }
    
    public String getDeathCause() {
        return deathCause;
    }
    
    public void setDeathCause(String deathCause) {
        this.deathCause = deathCause;
    }
    
    public long getGameStartTime() {
        return gameStartTime;
    }
    
    public void setGameStartTime(long gameStartTime) {
        this.gameStartTime = gameStartTime;
    }
}
