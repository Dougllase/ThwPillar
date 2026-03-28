package com.newpillar.utils;

/**
 * 游戏常量类
 * 集中管理所有游戏相关的常量，避免魔法数字分散在代码中
 */
public class GameConstants {
    
    // ==================== 时间常量 ====================
    
    public static final long TICKS_PER_SECOND = 20L;
    public static final long SECONDS_PER_MINUTE = 60L;
    public static final long MINUTES_PER_HOUR = 60L;
    
    // ==================== 延迟时间（ticks） ====================
    
    public static final long DELAY_IMMEDIATE = 1L;
    public static final long DELAY_SHORT = 20L;           // 1秒
    public static final long DELAY_MEDIUM = 60L;          // 3秒
    public static final long DELAY_LONG = 100L;           // 5秒
    public static final long DELAY_VERY_LONG = 240L;      // 12秒
    public static final long DELAY_CELEBRATION = 300L;    // 15秒
    public static final long DELAY_GAME_END = 600L;       // 30秒
    
    // ==================== 玩家数量 ====================
    
    public static final int MIN_PLAYERS = 4;
    public static final int MAX_PLAYERS = 12;
    public static final int AUTO_START_MIN_PLAYERS = 2;
    public static final int JOIN_BROADCAST_MIN = 2;
    public static final int JOIN_BROADCAST_MAX = 11;
    
    // ==================== 冷却时间（毫秒） ====================
    
    public static final long COOLDOWN_SHORT = 30_000L;           // 30秒
    public static final long COOLDOWN_MEDIUM = 60_000L;          // 1分钟
    public static final long COOLDOWN_LONG = 300_000L;           // 5分钟
    public static final long RECRUIT_INTERVAL = 360_000L;        // 6分钟
    public static final long FIRST_RECRUIT_DELAY = 120_000L;     // 2分钟
    public static final long PENDING_EXPIRY = 300_000L;          // 5分钟
    
    // ==================== 游戏时间 ====================
    
    public static final int GAME_DURATION_MINUTES = 10;
    public static final int EVENT_INTERVAL_MINUTES = 2;
    public static final int BORDER_SHRINK_INTERVAL = 3;
    
    // ==================== 属性值 ====================
    
    public static final double BASE_JUMP_STRENGTH = 0.42;
    public static final double BASE_MOVEMENT_SPEED = 0.1;
    public static final double BASE_GRAVITY = 0.08;
    public static final double BASE_SAFE_FALL_DISTANCE = 3.0;
    
    // ==================== 效果持续时间（ticks） ====================
    
    public static final int EFFECT_DURATION_SHORT = 100;      // 5秒
    public static final int EFFECT_DURATION_MEDIUM = 200;     // 10秒
    public static final int EFFECT_DURATION_LONG = 400;       // 20秒
    public static final int EFFECT_DURATION_VERY_LONG = 600;  // 30秒
    
    // ==================== 边界设置 ====================
    
    public static final double BORDER_SIZE_INITIAL = 20000.0;
    public static final double BORDER_SIZE_GAME = 200.0;
    public static final double BORDER_CENTER_X = 100.0;
    public static final double BORDER_CENTER_Z = 100.0;
    
    // ==================== 奖励设置 ====================
    
    public static final int WIN_REWARD_BASE = 100;
    public static final int KILL_REWARD_BASE = 10;
    public static final int PARTICIPATION_REWARD = 5;
    
    // ==================== 成就设置 ====================
    
    public static final int ACHIEVEMENT_COOLDOWN_MS = 5000;
    public static final int KILL_STREAK_THRESHOLD = 3;
    
    // ==================== 数据库 ====================
    
    public static final int DB_CONNECTION_TIMEOUT = 30000;
    public static final int DB_IDLE_TIMEOUT = 600000;
    public static final int DB_MAX_POOL_SIZE = 10;
    public static final int DB_MIN_IDLE = 5;
    
    private GameConstants() {
        // 防止实例化
        throw new UnsupportedOperationException("常量类不能被实例化");
    }
}
