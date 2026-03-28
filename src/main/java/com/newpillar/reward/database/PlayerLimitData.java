package com.newpillar.reward.database;

import java.util.Calendar;
import java.util.UUID;

/**
 * 玩家金币上限数据
 */
public class PlayerLimitData {
    private final UUID playerUuid;
    private int dailyEarned;
    private int weeklyEarned;
    private long lastResetDay;
    private long lastResetWeek;
    
    public PlayerLimitData(UUID playerUuid) {
        this.playerUuid = playerUuid;
        this.dailyEarned = 0;
        this.weeklyEarned = 0;
        this.lastResetDay = 0;
        this.lastResetWeek = 0;
    }
    
    /**
     * 检查并重置上限（按自然日和自然周）
     */
    public void checkAndResetLimits() {
        Calendar now = Calendar.getInstance();
        
        // 获取当前日期（年月日）
        int currentDay = now.get(Calendar.YEAR) * 10000 + 
                        (now.get(Calendar.MONTH) + 1) * 100 + 
                        now.get(Calendar.DAY_OF_MONTH);
        
        // 获取当前周（年+周数）
        int currentWeek = now.get(Calendar.YEAR) * 100 + now.get(Calendar.WEEK_OF_YEAR);
        
        // 检查日重置
        if (currentDay != lastResetDay) {
            dailyEarned = 0;
            lastResetDay = currentDay;
        }
        
        // 检查周重置
        if (currentWeek != lastResetWeek) {
            weeklyEarned = 0;
            lastResetWeek = currentWeek;
        }
    }
    
    // Getters and Setters
    public UUID getPlayerUuid() {
        return playerUuid;
    }
    
    public int getDailyEarned() {
        return dailyEarned;
    }
    
    public void setDailyEarned(int dailyEarned) {
        this.dailyEarned = dailyEarned;
    }
    
    public int getWeeklyEarned() {
        return weeklyEarned;
    }
    
    public void setWeeklyEarned(int weeklyEarned) {
        this.weeklyEarned = weeklyEarned;
    }
    
    public long getLastResetDay() {
        return lastResetDay;
    }
    
    public void setLastResetDay(long lastResetDay) {
        this.lastResetDay = lastResetDay;
    }
    
    public long getLastResetWeek() {
        return lastResetWeek;
    }
    
    public void setLastResetWeek(long lastResetWeek) {
        this.lastResetWeek = lastResetWeek;
    }
}
