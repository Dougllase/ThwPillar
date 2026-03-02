package com.newpillar.database;

import com.newpillar.NewPillar;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.*;
import java.util.UUID;
import java.util.logging.Level;

public class DatabaseManager {
    private final NewPillar plugin;
    private HikariDataSource dataSource;
    private String host;
    private int port;
    private String database;
    private String username;
    private String password;

    public DatabaseManager(NewPillar plugin) {
        this.plugin = plugin;
        loadConfig();
        initialize();
    }

    private void loadConfig() {
        FileConfiguration config = plugin.getConfig();

        // 从配置文件加载MySQL连接信息
        host = config.getString("mysql.host", "localhost");
        port = config.getInt("mysql.port", 3306);
        database = config.getString("mysql.database", "newpillar");
        username = config.getString("mysql.username", "root");
        password = config.getString("mysql.password", "");

        // 如果没有配置，使用默认值并保存
        if (!config.contains("mysql")) {
            config.set("mysql.host", host);
            config.set("mysql.port", port);
            config.set("mysql.database", database);
            config.set("mysql.username", username);
            config.set("mysql.password", password);
            plugin.saveConfig();
        }
    }

    private void initialize() {
        try {
            // 配置HikariCP连接池
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC&autoReconnect=true&characterEncoding=utf8",
                    host, port, database));
            config.setUsername(username);
            config.setPassword(password);
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            
            // 连接池配置
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(5);
            config.setIdleTimeout(300000);
            config.setConnectionTimeout(20000);
            config.setMaxLifetime(1800000);
            
            // 连接测试
            config.setConnectionTestQuery("SELECT 1");
            
            // 创建连接池
            dataSource = new HikariDataSource(config);

            plugin.getLogger().info("MySQL数据库连接池初始化成功！");

            // 创建表结构
            createTables();

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "MySQL数据库连接池初始化失败: " + e.getMessage(), e);
        }
    }

    private void createTables() throws SQLException {
        // 玩家成就表
        String achievementsTable = """
            CREATE TABLE IF NOT EXISTS player_achievements (
                id INT PRIMARY KEY AUTO_INCREMENT,
                player_uuid VARCHAR(36) NOT NULL,
                achievement_id VARCHAR(64) NOT NULL,
                unlocked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                UNIQUE KEY unique_achievement (player_uuid, achievement_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;

        // 玩家统计表
        String statisticsTable = """
            CREATE TABLE IF NOT EXISTS player_statistics (
                player_uuid VARCHAR(36) PRIMARY KEY,
                total_kills INT DEFAULT 0,
                total_deaths INT DEFAULT 0,
                total_wins INT DEFAULT 0,
                total_games_played INT DEFAULT 0,
                damage_dealt DOUBLE DEFAULT 0,
                damage_taken DOUBLE DEFAULT 0,
                blocks_placed INT DEFAULT 0,
                blocks_broken INT DEFAULT 0,
                items_looted INT DEFAULT 0,
                highest_win_streak INT DEFAULT 0,
                current_win_streak INT DEFAULT 0,
                total_damage_dealt DOUBLE DEFAULT 0,
                total_damage_taken DOUBLE DEFAULT 0,
                total_blocks_broken INT DEFAULT 0,
                total_blocks_placed INT DEFAULT 0,
                total_items_looted INT DEFAULT 0,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;

        // 玩家游戏数据表（本局游戏）
        String gameStatsTable = """
            CREATE TABLE IF NOT EXISTS player_game_stats (
                id INT PRIMARY KEY AUTO_INCREMENT,
                player_uuid VARCHAR(36) NOT NULL,
                game_session VARCHAR(64) NOT NULL,
                kills INT DEFAULT 0,
                deaths INT DEFAULT 0,
                damage_dealt DOUBLE DEFAULT 0,
                damage_taken DOUBLE DEFAULT 0,
                blocks_placed INT DEFAULT 0,
                blocks_broken INT DEFAULT 0,
                items_looted INT DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_player_session (player_uuid, game_session)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(achievementsTable);
            stmt.execute(statisticsTable);
            stmt.execute(gameStatsTable);
            plugin.getLogger().info("MySQL数据库表结构初始化完成！");
        }

        // 检查和添加缺失的列（用于表结构更新）
        checkAndAddMissingColumns();
    }

    /**
     * 检查并添加缺失的数据库列
     * 用于在插件更新时自动升级表结构
     */
    private void checkAndAddMissingColumns() {
        // 检查并添加 player_statistics 表的缺失列
        checkAndAddColumn("player_statistics", "highest_win_streak", "INT DEFAULT 0");
        checkAndAddColumn("player_statistics", "current_win_streak", "INT DEFAULT 0");
        checkAndAddColumn("player_statistics", "total_damage_dealt", "DOUBLE DEFAULT 0");
        checkAndAddColumn("player_statistics", "total_damage_taken", "DOUBLE DEFAULT 0");
        checkAndAddColumn("player_statistics", "total_blocks_broken", "INT DEFAULT 0");
        checkAndAddColumn("player_statistics", "total_blocks_placed", "INT DEFAULT 0");
        checkAndAddColumn("player_statistics", "total_items_looted", "INT DEFAULT 0");
    }

    /**
     * 检查并添加单个列
     */
    private void checkAndAddColumn(String tableName, String columnName, String columnDefinition) {
        String checkSql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                         "WHERE TABLE_NAME = ? AND COLUMN_NAME = ? AND TABLE_SCHEMA = DATABASE()";

        try (Connection conn = getConnection();
             PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
            checkStmt.setString(1, tableName);
            checkStmt.setString(2, columnName);
            ResultSet rs = checkStmt.executeQuery();

            if (rs.next() && rs.getInt(1) == 0) {
                // 列不存在，添加它
                String alterSql = "ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDefinition;
                try (Statement alterStmt = conn.createStatement()) {
                    alterStmt.execute(alterSql);
                    plugin.getLogger().info("数据库表 '" + tableName + "' 添加列 '" + columnName + "' 成功！");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "检查/添加列 '" + columnName + "' 失败: " + e.getMessage(), e);
        }
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("数据库连接池未初始化或已关闭");
        }
        return dataSource.getConnection();
    }

    // 保存玩家成就
    public void saveAchievement(UUID playerUuid, String achievementId) {
        String sql = """
            INSERT IGNORE INTO player_achievements (player_uuid, achievement_id)
            VALUES (?, ?)
            """;

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, playerUuid.toString());
            pstmt.setString(2, achievementId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "保存成就失败: " + e.getMessage(), e);
        }
    }

    // 获取玩家成就列表
    public java.util.Set<String> getPlayerAchievements(UUID playerUuid) {
        java.util.Set<String> achievements = new java.util.HashSet<>();
        String sql = "SELECT achievement_id FROM player_achievements WHERE player_uuid = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, playerUuid.toString());
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                achievements.add(rs.getString("achievement_id"));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "获取玩家成就失败: " + e.getMessage(), e);
        }

        return achievements;
    }

    // 保存玩家统计
    public void saveStatistics(UUID playerUuid, PlayerStatisticsData data) {
        String sql = """
            INSERT INTO player_statistics (
                player_uuid, total_kills, total_deaths, total_wins, total_games_played,
                damage_dealt, damage_taken, blocks_placed, blocks_broken, items_looted,
                highest_win_streak, current_win_streak, total_damage_dealt, total_damage_taken,
                total_blocks_broken, total_blocks_placed, total_items_looted
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                total_kills = VALUES(total_kills),
                total_deaths = VALUES(total_deaths),
                total_wins = VALUES(total_wins),
                total_games_played = VALUES(total_games_played),
                damage_dealt = VALUES(damage_dealt),
                damage_taken = VALUES(damage_taken),
                blocks_placed = VALUES(blocks_placed),
                blocks_broken = VALUES(blocks_broken),
                items_looted = VALUES(items_looted),
                highest_win_streak = VALUES(highest_win_streak),
                current_win_streak = VALUES(current_win_streak),
                total_damage_dealt = VALUES(total_damage_dealt),
                total_damage_taken = VALUES(total_damage_taken),
                total_blocks_broken = VALUES(total_blocks_broken),
                total_blocks_placed = VALUES(total_blocks_placed),
                total_items_looted = VALUES(total_items_looted)
            """;

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, playerUuid.toString());
            pstmt.setInt(2, data.getTotalKills());
            pstmt.setInt(3, data.getTotalDeaths());
            pstmt.setInt(4, data.getTotalWins());
            pstmt.setInt(5, data.getTotalGamesPlayed());
            pstmt.setDouble(6, data.getDamageDealt());
            pstmt.setDouble(7, data.getDamageTaken());
            pstmt.setInt(8, data.getBlocksPlaced());
            pstmt.setInt(9, data.getBlocksBroken());
            pstmt.setInt(10, data.getItemsLooted());
            pstmt.setInt(11, data.getHighestWinStreak());
            pstmt.setInt(12, data.getCurrentWinStreak());
            pstmt.setDouble(13, data.getTotalDamageDealt());
            pstmt.setDouble(14, data.getTotalDamageTaken());
            pstmt.setInt(15, data.getTotalBlocksBroken());
            pstmt.setInt(16, data.getTotalBlocksPlaced());
            pstmt.setInt(17, data.getTotalItemsLooted());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "保存玩家统计失败: " + e.getMessage(), e);
        }
    }

    // 获取玩家统计（别名方法，兼容旧代码）
    public PlayerStatisticsData loadStatistics(UUID playerUuid) {
        return getStatistics(playerUuid);
    }

    // 获取玩家统计
    public PlayerStatisticsData getStatistics(UUID playerUuid) {
        String sql = "SELECT * FROM player_statistics WHERE player_uuid = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, playerUuid.toString());
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                PlayerStatisticsData data = new PlayerStatisticsData();
                data.setTotalKills(rs.getInt("total_kills"));
                data.setTotalDeaths(rs.getInt("total_deaths"));
                data.setTotalWins(rs.getInt("total_wins"));
                data.setTotalGamesPlayed(rs.getInt("total_games_played"));
                data.setDamageDealt(rs.getDouble("damage_dealt"));
                data.setDamageTaken(rs.getDouble("damage_taken"));
                data.setBlocksPlaced(rs.getInt("blocks_placed"));
                data.setBlocksBroken(rs.getInt("blocks_broken"));
                data.setItemsLooted(rs.getInt("items_looted"));
                data.setHighestWinStreak(rs.getInt("highest_win_streak"));
                data.setCurrentWinStreak(rs.getInt("current_win_streak"));
                data.setTotalDamageDealt(rs.getDouble("total_damage_dealt"));
                data.setTotalDamageTaken(rs.getDouble("total_damage_taken"));
                data.setTotalBlocksBroken(rs.getInt("total_blocks_broken"));
                data.setTotalBlocksPlaced(rs.getInt("total_blocks_placed"));
                data.setTotalItemsLooted(rs.getInt("total_items_looted"));
                return data;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "获取玩家统计失败: " + e.getMessage(), e);
        }

        return new PlayerStatisticsData();
    }

    // 关闭数据库连接池
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("MySQL数据库连接池已关闭");
        }
    }

    // 玩家统计数据类
    public static class PlayerStatisticsData {
        private int totalKills = 0;
        private int totalDeaths = 0;
        private int totalWins = 0;
        private int totalGamesPlayed = 0;
        private double damageDealt = 0;
        private double damageTaken = 0;
        private int blocksPlaced = 0;
        private int blocksBroken = 0;
        private int itemsLooted = 0;
        private int highestWinStreak = 0;
        private int currentWinStreak = 0;
        private double totalDamageDealt = 0;
        private double totalDamageTaken = 0;
        private int totalBlocksBroken = 0;
        private int totalBlocksPlaced = 0;
        private int totalItemsLooted = 0;

        // Getters and Setters
        public int getTotalKills() { return totalKills; }
        public void setTotalKills(int totalKills) { this.totalKills = totalKills; }
        public int getTotalDeaths() { return totalDeaths; }
        public void setTotalDeaths(int totalDeaths) { this.totalDeaths = totalDeaths; }
        public int getTotalWins() { return totalWins; }
        public void setTotalWins(int totalWins) { this.totalWins = totalWins; }
        public int getTotalGamesPlayed() { return totalGamesPlayed; }
        public void setTotalGamesPlayed(int totalGamesPlayed) { this.totalGamesPlayed = totalGamesPlayed; }
        public double getDamageDealt() { return damageDealt; }
        public void setDamageDealt(double damageDealt) { this.damageDealt = damageDealt; }
        public double getDamageTaken() { return damageTaken; }
        public void setDamageTaken(double damageTaken) { this.damageTaken = damageTaken; }
        public int getBlocksPlaced() { return blocksPlaced; }
        public void setBlocksPlaced(int blocksPlaced) { this.blocksPlaced = blocksPlaced; }
        public int getBlocksBroken() { return blocksBroken; }
        public void setBlocksBroken(int blocksBroken) { this.blocksBroken = blocksBroken; }
        public int getItemsLooted() { return itemsLooted; }
        public void setItemsLooted(int itemsLooted) { this.itemsLooted = itemsLooted; }
        public int getHighestWinStreak() { return highestWinStreak; }
        public void setHighestWinStreak(int highestWinStreak) { this.highestWinStreak = highestWinStreak; }
        public int getCurrentWinStreak() { return currentWinStreak; }
        public void setCurrentWinStreak(int currentWinStreak) { this.currentWinStreak = currentWinStreak; }
        public double getTotalDamageDealt() { return totalDamageDealt; }
        public void setTotalDamageDealt(double totalDamageDealt) { this.totalDamageDealt = totalDamageDealt; }
        public double getTotalDamageTaken() { return totalDamageTaken; }
        public void setTotalDamageTaken(double totalDamageTaken) { this.totalDamageTaken = totalDamageTaken; }
        public int getTotalBlocksBroken() { return totalBlocksBroken; }
        public void setTotalBlocksBroken(int totalBlocksBroken) { this.totalBlocksBroken = totalBlocksBroken; }
        public int getTotalBlocksPlaced() { return totalBlocksPlaced; }
        public void setTotalBlocksPlaced(int totalBlocksPlaced) { this.totalBlocksPlaced = totalBlocksPlaced; }
        public int getTotalItemsLooted() { return totalItemsLooted; }
        public void setTotalItemsLooted(int totalItemsLooted) { this.totalItemsLooted = totalItemsLooted; }
    }
}
