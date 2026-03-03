package com.newpillar.database;

import com.newpillar.NewPillar;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.*;
import java.util.UUID;
import java.util.logging.Level;

public class DatabaseManager {
    private final NewPillar plugin;
    private Connection connection;
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
            // 加载MySQL驱动
            Class.forName("com.mysql.cj.jdbc.Driver");

            // 建立数据库连接
            String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC&autoReconnect=true",
                    host, port, database);
            connection = DriverManager.getConnection(url, username, password);

            plugin.getLogger().info("MySQL数据库连接成功！");

            // 创建表结构
            createTables();

        } catch (ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "MySQL驱动加载失败: " + e.getMessage(), e);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "MySQL数据库连接失败: " + e.getMessage(), e);
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

        try (Statement stmt = connection.createStatement()) {
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

        try (PreparedStatement checkStmt = connection.prepareStatement(checkSql)) {
            checkStmt.setString(1, tableName);
            checkStmt.setString(2, columnName);
            ResultSet rs = checkStmt.executeQuery();

            if (rs.next() && rs.getInt(1) == 0) {
                // 列不存在，添加它
                String alterSql = "ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDefinition;
                try (Statement alterStmt = connection.createStatement()) {
                    alterStmt.execute(alterSql);
                    plugin.getLogger().info("数据库表 '" + tableName + "' 添加列 '" + columnName + "' 成功！");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "检查/添加列 '" + columnName + "' 失败: " + e.getMessage(), e);
        }
    }

    public Connection getConnection() {
        if (connection == null) {
            return null;
        }
        try {
            if (connection.isClosed() || !connection.isValid(5)) {
                String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC&autoReconnect=true",
                        host, port, database);
                connection = DriverManager.getConnection(url, username, password);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "重新连接MySQL数据库失败: " + e.getMessage(), e);
        }
        return connection;
    }

    // 保存玩家成就
    public void saveAchievement(UUID playerUuid, String achievementId) {
        String sql = """
            INSERT IGNORE INTO player_achievements (player_uuid, achievement_id)
            VALUES (?, ?)
            """;

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, playerUuid.toString());
            pstmt.setString(2, achievementId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "保存成就失败: " + e.getMessage(), e);
        }
    }

    // 检查玩家是否拥有成就
    public boolean hasAchievement(UUID playerUuid, String achievementId) {
        String sql = "SELECT 1 FROM player_achievements WHERE player_uuid = ? AND achievement_id = ?";

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, playerUuid.toString());
            pstmt.setString(2, achievementId);
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "查询成就失败: " + e.getMessage(), e);
            return false;
        }
    }

    // 获取玩家所有成就
    public java.util.Set<String> getPlayerAchievements(UUID playerUuid) {
        java.util.Set<String> achievements = new java.util.HashSet<>();
        String sql = "SELECT achievement_id FROM player_achievements WHERE player_uuid = ?";

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, playerUuid.toString());
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                achievements.add(rs.getString("achievement_id"));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "获取成就列表失败: " + e.getMessage(), e);
        }

        return achievements;
    }

    // 保存玩家统计数据
    public void saveStatistics(UUID playerUuid, int totalKills, int totalDeaths, int totalWins,
                               int totalGamesPlayed, double damageDealt, double damageTaken,
                               int blocksPlaced, int blocksBroken, int itemsLooted,
                               int highestWinStreak, int currentWinStreak, double totalDamageDealt,
                               double totalDamageTaken, int totalBlocksBroken, int totalBlocksPlaced,
                               int totalItemsLooted) {
        String sql = """
            INSERT INTO player_statistics (player_uuid, total_kills, total_deaths, total_wins,
                total_games_played, damage_dealt, damage_taken, blocks_placed, blocks_broken, items_looted,
                highest_win_streak, current_win_streak, total_damage_dealt, total_damage_taken,
                total_blocks_broken, total_blocks_placed, total_items_looted)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, playerUuid.toString());
            pstmt.setInt(2, totalKills);
            pstmt.setInt(3, totalDeaths);
            pstmt.setInt(4, totalWins);
            pstmt.setInt(5, totalGamesPlayed);
            pstmt.setDouble(6, damageDealt);
            pstmt.setDouble(7, damageTaken);
            pstmt.setInt(8, blocksPlaced);
            pstmt.setInt(9, blocksBroken);
            pstmt.setInt(10, itemsLooted);
            pstmt.setInt(11, highestWinStreak);
            pstmt.setInt(12, currentWinStreak);
            pstmt.setDouble(13, totalDamageDealt);
            pstmt.setDouble(14, totalDamageTaken);
            pstmt.setInt(15, totalBlocksBroken);
            pstmt.setInt(16, totalBlocksPlaced);
            pstmt.setInt(17, totalItemsLooted);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "保存统计数据失败: " + e.getMessage(), e);
        }
    }

    // 加载玩家统计数据
    public PlayerStatisticsData loadStatistics(UUID playerUuid) {
        String sql = "SELECT * FROM player_statistics WHERE player_uuid = ?";

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, playerUuid.toString());
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return new PlayerStatisticsData(
                    rs.getInt("total_kills"),
                    rs.getInt("total_deaths"),
                    rs.getInt("total_wins"),
                    rs.getInt("total_games_played"),
                    rs.getDouble("damage_dealt"),
                    rs.getDouble("damage_taken"),
                    rs.getInt("blocks_placed"),
                    rs.getInt("blocks_broken"),
                    rs.getInt("items_looted"),
                    rs.getInt("highest_win_streak"),
                    rs.getInt("current_win_streak"),
                    rs.getDouble("total_damage_dealt"),
                    rs.getDouble("total_damage_taken"),
                    rs.getInt("total_blocks_broken"),
                    rs.getInt("total_blocks_placed"),
                    rs.getInt("total_items_looted")
                );
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "加载统计数据失败: " + e.getMessage(), e);
        }

        return new PlayerStatisticsData(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("MySQL数据库连接已关闭！");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "关闭MySQL数据库连接失败: " + e.getMessage(), e);
        }
    }

    // 统计数据传输对象
    public record PlayerStatisticsData(
        int totalKills,
        int totalDeaths,
        int totalWins,
        int totalGamesPlayed,
        double damageDealt,
        double damageTaken,
        int blocksPlaced,
        int blocksBroken,
        int itemsLooted,
        int highestWinStreak,
        int currentWinStreak,
        double totalDamageDealt,
        double totalDamageTaken,
        int totalBlocksBroken,
        int totalBlocksPlaced,
        int totalItemsLooted
    ) {}
}
