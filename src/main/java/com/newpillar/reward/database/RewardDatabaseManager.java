package com.newpillar.reward.database;

import com.newpillar.NewPillar;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 奖励数据库管理器
 * 与ThwReward共用同一个数据库，但管理游戏过程中的实时数据
 */
public class RewardDatabaseManager {
    private final NewPillar plugin;
    private HikariDataSource dataSource;
    private boolean connected = false;
    
    // 缓存（游戏过程中使用，游戏结束清空）
    private final Map<UUID, PlayerSessionData> sessionDataCache = new ConcurrentHashMap<>();
    
    public RewardDatabaseManager(NewPillar plugin) {
        this.plugin = plugin;
        initialize();
    }
    
    public boolean isConnected() {
        return connected && dataSource != null && !dataSource.isClosed();
    }
    
    private void initialize() {
        var config = plugin.getConfig();
        
        // 从ThwReward的配置路径读取（如果存在）
        String host = config.getString("thwreward.mysql.host", config.getString("mysql.host", "localhost"));
        int port = config.getInt("thwreward.mysql.port", config.getInt("mysql.port", 3306));
        String database = config.getString("thwreward.mysql.database", config.getString("mysql.database", "thwreward"));
        String username = config.getString("thwreward.mysql.username", config.getString("mysql.username", "root"));
        String password = config.getString("thwreward.mysql.password", config.getString("mysql.password", ""));
        
        try {
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC&autoReconnect=true&allowPublicKeyRetrieval=true",
                    host, port, database));
            hikariConfig.setUsername(username);
            hikariConfig.setPassword(password);
            hikariConfig.setMaximumPoolSize(10);
            hikariConfig.setMinimumIdle(3);
            hikariConfig.setConnectionTimeout(30000);
            hikariConfig.setIdleTimeout(600000);
            hikariConfig.setMaxLifetime(1800000);
            hikariConfig.setConnectionTestQuery("SELECT 1");
            
            dataSource = new HikariDataSource(hikariConfig);
            
            // 测试连接
            try (Connection conn = dataSource.getConnection()) {
                if (conn.isValid(5)) {
                    connected = true;
                    plugin.getLogger().info("[奖励数据库] MySQL连接池初始化成功！");
                } else {
                    throw new SQLException("数据库连接测试失败");
                }
            }
            
        } catch (Exception e) {
            connected = false;
            plugin.getLogger().severe("[奖励数据库] MySQL连接池初始化失败: " + e.getMessage());
        }
    }
    
    public Connection getConnection() throws SQLException {
        if (!isConnected()) {
            throw new SQLException("数据库未连接");
        }
        return dataSource.getConnection();
    }
    
    // ==================== 金币上限相关 ====================
    
    /**
     * 获取玩家金币上限数据
     */
    public PlayerLimitData getPlayerLimitData(UUID playerUuid) {
        PlayerLimitData data = new PlayerLimitData(playerUuid);
        
        String sql = "SELECT daily_earned, weekly_earned, last_reset_day, last_reset_week FROM player_rewards WHERE player_uuid = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, playerUuid.toString());
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                data.setDailyEarned(rs.getInt("daily_earned"));
                data.setWeeklyEarned(rs.getInt("weekly_earned"));
                data.setLastResetDay(rs.getLong("last_reset_day"));
                data.setLastResetWeek(rs.getLong("last_reset_week"));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[奖励数据库] 获取玩家金币上限数据失败: " + e.getMessage());
        }
        
        return data;
    }
    
    /**
     * 检查是否可以获取金币
     */
    public boolean canEarnCoins(UUID playerUuid, int amount, int dailyLimit, int weeklyLimit) {
        PlayerLimitData data = getPlayerLimitData(playerUuid);
        data.checkAndResetLimits();
        
        return data.getDailyEarned() + amount <= dailyLimit && 
               data.getWeeklyEarned() + amount <= weeklyLimit;
    }
    
    /**
     * 添加金币（更新数据库）
     */
    public int addCoins(UUID playerUuid, int amount, int dailyLimit, int weeklyLimit) {
        PlayerLimitData data = getPlayerLimitData(playerUuid);
        data.checkAndResetLimits();
        
        int canAdd = Math.min(amount, dailyLimit - data.getDailyEarned());
        canAdd = Math.min(canAdd, weeklyLimit - data.getWeeklyEarned());
        
        if (canAdd <= 0) {
            return 0;
        }
        
        String sql = """
            INSERT INTO player_rewards (player_uuid, daily_earned, weekly_earned, last_reset_day, last_reset_week)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
            daily_earned = daily_earned + VALUES(daily_earned),
            weekly_earned = weekly_earned + VALUES(weekly_earned),
            last_reset_day = VALUES(last_reset_day),
            last_reset_week = VALUES(last_reset_week)
            """;
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, playerUuid.toString());
            pstmt.setInt(2, canAdd);
            pstmt.setInt(3, canAdd);
            pstmt.setLong(4, data.getLastResetDay());
            pstmt.setLong(5, data.getLastResetWeek());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[奖励数据库] 添加金币失败: " + e.getMessage());
            return 0;
        }
        
        return canAdd;
    }
    
    /**
     * 扣除金币（用于提前退出）
     */
    public boolean deductCoins(UUID playerUuid, int amount) {
        String sql = """
            UPDATE player_rewards
            SET daily_earned = GREATEST(0, daily_earned - ?),
                weekly_earned = GREATEST(0, weekly_earned - ?)
            WHERE player_uuid = ?
            """;
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, amount);
            pstmt.setInt(2, amount);
            pstmt.setString(3, playerUuid.toString());
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().warning("[奖励数据库] 扣除金币失败: " + e.getMessage());
            return false;
        }
    }
    
    // ==================== 消极游戏检测相关 ====================
    
    /**
     * 记录游戏历史
     */
    public void recordGameHistory(UUID playerUuid, int gameId, long gameStartTime, 
                                   long gameEndTime, String deathCause, boolean isNegativeGame) {
        String sql = """
            INSERT INTO game_history (player_uuid, game_id, game_start_time, game_end_time, 
                                     duration_seconds, death_cause, is_negative_game)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        
        int durationSeconds = (int) ((gameEndTime - gameStartTime) / 1000);
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, playerUuid.toString());
            pstmt.setInt(2, gameId);
            pstmt.setLong(3, gameStartTime);
            pstmt.setLong(4, gameEndTime);
            pstmt.setInt(5, durationSeconds);
            pstmt.setString(6, deathCause);
            pstmt.setBoolean(7, isNegativeGame);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[奖励数据库] 记录游戏历史失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取30分钟内的消极游戏次数
     */
    public int getNegativeGameCount(UUID playerUuid, long windowMs) {
        String sql = """
            SELECT COUNT(*) as count FROM game_history
            WHERE player_uuid = ? AND game_start_time > ? AND is_negative_game = TRUE
            """;
        
        long cutoffTime = System.currentTimeMillis() - windowMs;
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, playerUuid.toString());
            pstmt.setLong(2, cutoffTime);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt("count");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[奖励数据库] 获取消极游戏次数失败: " + e.getMessage());
        }
        
        return 0;
    }
    
    /**
     * 添加惩罚记录
     */
    public void addPenalty(UUID playerUuid, long penaltyStartTime, long penaltyEndTime, 
                           int negativeGameCount, String reason) {
        String sql = """
            INSERT INTO negative_game_penalties 
            (player_uuid, penalty_start_time, penalty_end_time, negative_game_count, reason)
            VALUES (?, ?, ?, ?, ?)
            """;
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, playerUuid.toString());
            pstmt.setLong(2, penaltyStartTime);
            pstmt.setLong(3, penaltyEndTime);
            pstmt.setInt(4, negativeGameCount);
            pstmt.setString(5, reason);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[奖励数据库] 添加惩罚记录失败: " + e.getMessage());
        }
    }
    
    /**
     * 检查是否在惩罚期
     */
    public boolean isPenaltyActive(UUID playerUuid) {
        String sql = """
            SELECT penalty_end_time FROM negative_game_penalties
            WHERE player_uuid = ? AND penalty_end_time > ?
            ORDER BY penalty_end_time DESC LIMIT 1
            """;
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, playerUuid.toString());
            pstmt.setLong(2, System.currentTimeMillis());
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            plugin.getLogger().warning("[奖励数据库] 检查惩罚状态失败: " + e.getMessage());
        }
        
        return false;
    }
    
    // ==================== 断线检测相关 ====================
    
    // 断线保持时间：3分钟（180秒）
    private static final long DISCONNECT_GRACE_PERIOD_MS = 3 * 60 * 1000;
    
    /**
     * 记录断线
     */
    public void recordDisconnect(UUID playerUuid) {
        String sql = """
            INSERT INTO player_rewards (player_uuid, disconnect_count, last_disconnect, disconnect_time)
            VALUES (?, 1, ?, ?)
            ON DUPLICATE KEY UPDATE
            disconnect_count = CASE 
                WHEN last_disconnect > ? THEN disconnect_count + 1 
                ELSE 1 
            END,
            last_disconnect = ?,
            disconnect_time = ?
            """;
        
        long now = System.currentTimeMillis();
        long windowStart = now - (30 * 60 * 1000); // 30分钟窗口
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, playerUuid.toString());
            pstmt.setLong(2, now);
            pstmt.setLong(3, now);
            pstmt.setLong(4, windowStart);
            pstmt.setLong(5, now);
            pstmt.setLong(6, now);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[奖励数据库] 记录断线失败: " + e.getMessage());
        }
    }
    
    /**
     * 玩家重新加入，检查是否在宽限期内
     * @return true 如果在宽限期内（不计入断线），false 如果超过宽限期
     */
    public boolean onPlayerRejoin(UUID playerUuid) {
        String sql = """
            SELECT disconnect_time, disconnect_count FROM player_rewards
            WHERE player_uuid = ?
            """;
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, playerUuid.toString());
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                long disconnectTime = rs.getLong("disconnect_time");
                long now = System.currentTimeMillis();
                
                // 如果在3分钟宽限期内重新加入
                if (disconnectTime > 0 && (now - disconnectTime) < DISCONNECT_GRACE_PERIOD_MS) {
                    // 清除断线记录（重新加入不计入断线）
                    clearDisconnectRecord(playerUuid);
                    return true;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[奖励数据库] 检查断线重连失败: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * 清除断线记录
     */
    private void clearDisconnectRecord(UUID playerUuid) {
        String sql = """
            UPDATE player_rewards
            SET disconnect_count = 0, disconnect_time = 0
            WHERE player_uuid = ?
            """;
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, playerUuid.toString());
            pstmt.executeUpdate();
            plugin.getLogger().info("[奖励数据库] 玩家 " + playerUuid + " 在宽限期内重新加入，断线记录已清除");
        } catch (SQLException e) {
            plugin.getLogger().warning("[奖励数据库] 清除断线记录失败: " + e.getMessage());
        }
    }
    
    /**
     * 检查玩家是否有恶意断线记录（用于发送警告）
     */
    public boolean hasMaliciousDisconnect(UUID playerUuid) {
        String sql = """
            SELECT disconnect_count, last_disconnect FROM player_rewards
            WHERE player_uuid = ? AND disconnect_count > 0
            """;
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, playerUuid.toString());
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                int count = rs.getInt("disconnect_count");
                long lastDisconnect = rs.getLong("last_disconnect");
                long now = System.currentTimeMillis();
                
                // 如果有断线记录且最后一次断线在30分钟内
                if (count > 0 && (now - lastDisconnect) < (30 * 60 * 1000)) {
                    return true;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[奖励数据库] 检查恶意断线失败: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * 清除恶意断线标记（发送警告后调用）
     */
    public void clearMaliciousDisconnectFlag(UUID playerUuid) {
        String sql = """
            UPDATE player_rewards
            SET disconnect_count = GREATEST(0, disconnect_count - 1)
            WHERE player_uuid = ?
            """;
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, playerUuid.toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[奖励数据库] 清除恶意断线标记失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取断线次数
     */
    public int getDisconnectCount(UUID playerUuid, long windowMs) {
        String sql = """
            SELECT disconnect_count, last_disconnect FROM player_rewards
            WHERE player_uuid = ?
            """;
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, playerUuid.toString());
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                long lastDisconnect = rs.getLong("last_disconnect");
                long windowStart = System.currentTimeMillis() - windowMs;
                
                // 如果最后一次断线在窗口内，返回计数
                if (lastDisconnect > windowStart) {
                    return rs.getInt("disconnect_count");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[奖励数据库] 获取断线次数失败: " + e.getMessage());       }
        
        return 0;
    }
    
    // ==================== 待领取奖励相关 ====================
    
    /**
     * 添加待领取奖励
     */
    public void addPendingReward(UUID playerUuid, int killRewards, int victoryReward, 
                                  String details, long gameStartTime, boolean isWin) {
        String sql = """
            INSERT INTO pending_rewards 
            (player_uuid, kill_rewards, victory_reward, details, created_at, is_win, claimed)
            VALUES (?, ?, ?, ?, ?, ?, FALSE)
            """;
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, playerUuid.toString());
            pstmt.setInt(2, killRewards);
            pstmt.setInt(3, victoryReward);
            pstmt.setString(4, details);
            pstmt.setLong(5, gameStartTime);
            pstmt.setBoolean(6, isWin);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[奖励数据库] 添加待领取奖励失败: " + e.getMessage());
        }
    }
    
    /**
     * 移除待领取奖励（用于提前退出扣除）
     */
    public boolean removePendingKillRewards(UUID playerUuid, int amount, long gameStartTime) {
        String sql = """
            UPDATE pending_rewards
            SET kill_rewards = GREATEST(0, kill_rewards - ?)
            WHERE player_uuid = ? AND created_at = ? AND claimed = FALSE
            """;
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, amount);
            pstmt.setString(2, playerUuid.toString());
            pstmt.setLong(3, gameStartTime);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().warning("[奖励数据库] 移除待领取奖励失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 关闭连接池
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("[奖励数据库] 连接池已关闭");
        }
    }
}
