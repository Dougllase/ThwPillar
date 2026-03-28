package com.newpillar.database;

import com.newpillar.NewPillar;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
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
    private boolean useSQLite;

    public DatabaseManager(NewPillar plugin) {
        this.plugin = plugin;
        loadConfig();
        initialize();
    }

    private void loadConfig() {
        FileConfiguration config = plugin.getConfig();

        // 从配置文件加载数据库类型和连接信息
        useSQLite = config.getBoolean("database.use-sqlite", false);
        host = config.getString("mysql.host", "localhost");
        port = config.getInt("mysql.port", 3306);
        database = config.getString("mysql.database", "newpillar");
        username = config.getString("mysql.username", "root");
        password = config.getString("mysql.password", "");

        // 如果没有配置，使用默认值并保存
        if (!config.contains("database")) {
            config.set("database.use-sqlite", useSQLite);
            plugin.saveConfig();
        }
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
            if (useSQLite) {
                initializeSQLite();
            } else {
                initializeMySQL();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "数据库连接池初始化失败: " + e.getMessage(), e);
            // 如果MySQL失败，尝试使用SQLite
            if (!useSQLite) {
                plugin.getLogger().info("尝试回退到SQLite...");
                try {
                    initializeSQLite();
                } catch (SQLException ex) {
                    plugin.getLogger().log(Level.SEVERE, "SQLite初始化也失败: " + ex.getMessage(), ex);
                }
            }
        }
    }

    private void initializeMySQL() throws SQLException {
        // 配置HikariCP连接池
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC&autoReconnect=true&allowPublicKeyRetrieval=true",
                host, port, database));
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setConnectionTestQuery("SELECT 1");
        config.setLeakDetectionThreshold(60000);
        
        dataSource = new HikariDataSource(config);
        
        plugin.getLogger().info("MySQL数据库连接池初始化成功！");
        createTablesMySQL();
    }

    private void initializeSQLite() throws SQLException {
        // 配置SQLite连接池
        HikariConfig config = new HikariConfig();
        String dbPath = plugin.getDataFolder().getAbsolutePath() + "/database.db";
        config.setJdbcUrl("jdbc:sqlite:" + dbPath);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setConnectionTestQuery("SELECT 1");
        
        dataSource = new HikariDataSource(config);
        useSQLite = true;
        
        plugin.getLogger().info("SQLite数据库连接池初始化成功！");
        createTablesSQLite();
    }

    private void createTablesMySQL() throws SQLException {
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
                player_name VARCHAR(32) DEFAULT NULL,
                total_kills INT DEFAULT 0,
                total_deaths INT DEFAULT 0,
                total_wins INT DEFAULT 0,
                total_games_played INT DEFAULT 0,
                damage_dealt DOUBLE DEFAULT 0,
                damage_taken DOUBLE DEFAULT 0,
                blocks_placed INT DEFAULT 0,
                blocks_broken INT DEFAULT 0,
                items_looted INT DEFAULT 0,
                mob_kills INT DEFAULT 0,
                highest_win_streak INT DEFAULT 0,
                current_win_streak INT DEFAULT 0,
                total_damage_dealt DOUBLE DEFAULT 0,
                total_damage_taken DOUBLE DEFAULT 0,
                total_blocks_broken INT DEFAULT 0,
                total_blocks_placed INT DEFAULT 0,
                total_items_looted INT DEFAULT 0,
                total_mob_kills INT DEFAULT 0,
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

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(achievementsTable);
            stmt.execute(statisticsTable);
            stmt.execute(gameStatsTable);
            plugin.getLogger().info("MySQL数据库表结构初始化完成！");
        }

        // 检查和添加缺失的列（用于表结构更新）
        checkAndAddMissingColumns();
    }

    private void createTablesSQLite() throws SQLException {
        // SQLite版本的玩家成就表
        String achievementsTable = """
            CREATE TABLE IF NOT EXISTS player_achievements (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                player_uuid TEXT NOT NULL,
                achievement_id TEXT NOT NULL,
                unlocked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                UNIQUE(player_uuid, achievement_id)
            )
            """;

        // SQLite版本的玩家统计表
        String statisticsTable = """
            CREATE TABLE IF NOT EXISTS player_statistics (
                player_uuid TEXT PRIMARY KEY,
                player_name TEXT DEFAULT NULL,
                total_kills INTEGER DEFAULT 0,
                total_deaths INTEGER DEFAULT 0,
                total_wins INTEGER DEFAULT 0,
                total_games_played INTEGER DEFAULT 0,
                damage_dealt REAL DEFAULT 0,
                damage_taken REAL DEFAULT 0,
                blocks_placed INTEGER DEFAULT 0,
                blocks_broken INTEGER DEFAULT 0,
                items_looted INTEGER DEFAULT 0,
                mob_kills INTEGER DEFAULT 0,
                highest_win_streak INTEGER DEFAULT 0,
                current_win_streak INTEGER DEFAULT 0,
                total_damage_dealt REAL DEFAULT 0,
                total_damage_taken REAL DEFAULT 0,
                total_blocks_broken INTEGER DEFAULT 0,
                total_blocks_placed INTEGER DEFAULT 0,
                total_items_looted INTEGER DEFAULT 0,
                total_mob_kills INTEGER DEFAULT 0,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;

        // SQLite版本的玩家游戏数据表
        String gameStatsTable = """
            CREATE TABLE IF NOT EXISTS player_game_stats (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                player_uuid TEXT NOT NULL,
                game_session TEXT NOT NULL,
                kills INTEGER DEFAULT 0,
                deaths INTEGER DEFAULT 0,
                damage_dealt REAL DEFAULT 0,
                damage_taken REAL DEFAULT 0,
                blocks_placed INTEGER DEFAULT 0,
                blocks_broken INTEGER DEFAULT 0,
                items_looted INTEGER DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;

        // SQLite索引
        String createIndex = "CREATE INDEX IF NOT EXISTS idx_player_session ON player_game_stats(player_uuid, game_session)";

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(achievementsTable);
            stmt.execute(statisticsTable);
            stmt.execute(gameStatsTable);
            stmt.execute(createIndex);
            plugin.getLogger().info("SQLite数据库表结构初始化完成！");
        }
    }

    /**
     * 检查并添加缺失的数据库列
     * 用于在插件更新时自动升级表结构（仅MySQL）
     */
    private void checkAndAddMissingColumns() {
        if (useSQLite) {
            return; // SQLite不需要动态添加列，所有列已在创建表时定义
        }
        
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
        try (Connection conn = dataSource.getConnection()) {
            String checkSql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                             "WHERE TABLE_NAME = ? AND COLUMN_NAME = ? AND TABLE_SCHEMA = DATABASE()";

            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
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
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "检查/添加列 '" + columnName + "' 失败: " + e.getMessage(), e);
        }
    }

    public Connection getConnection() {
        if (dataSource == null) {
            plugin.getLogger().warning("数据库连接池尚未初始化，跳过数据库操作");
            return null;
        }
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "获取数据库连接失败: " + e.getMessage(), e);
            return null;
        }
    }

    // 保存玩家成就
    public void saveAchievement(UUID playerUuid, String achievementId) {
        String sql = """
            INSERT IGNORE INTO player_achievements (player_uuid, achievement_id)
            VALUES (?, ?)
            """;

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (conn == null) {
                plugin.getLogger().warning("无法保存成就到数据库：数据库连接为空");
                return;
            }
            pstmt.setString(1, playerUuid.toString());
            pstmt.setString(2, achievementId);
            pstmt.executeUpdate();
            plugin.getLogger().info("成就已保存到数据库: " + achievementId);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "保存成就失败: " + e.getMessage(), e);
        }
    }

    // 检查玩家是否拥有成就
    public boolean hasAchievement(UUID playerUuid, String achievementId) {
        String sql = "SELECT 1 FROM player_achievements WHERE player_uuid = ? AND achievement_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (conn == null) {
                return false;
            }
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

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (conn == null) {
                return achievements;
            }
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

    // 保存玩家统计数据（带玩家名字）
    public void saveStatistics(UUID playerUuid, String playerName, int totalKills, int totalDeaths, int totalWins,
                               int totalGamesPlayed, double damageDealt, double damageTaken,
                               int blocksPlaced, int blocksBroken, int itemsLooted, int mobKills,
                               int highestWinStreak, int currentWinStreak, double totalDamageDealt,
                               double totalDamageTaken, int totalBlocksBroken, int totalBlocksPlaced,
                               int totalItemsLooted, int totalMobKills) {
        String sql = """
            INSERT INTO player_statistics (player_uuid, player_name, total_kills, total_deaths, total_wins,
                total_games_played, damage_dealt, damage_taken, blocks_placed, blocks_broken, items_looted, mob_kills,
                highest_win_streak, current_win_streak, total_damage_dealt, total_damage_taken,
                total_blocks_broken, total_blocks_placed, total_items_looted, total_mob_kills)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                player_name = VALUES(player_name),
                total_kills = VALUES(total_kills),
                total_deaths = VALUES(total_deaths),
                total_wins = VALUES(total_wins),
                total_games_played = VALUES(total_games_played),
                damage_dealt = VALUES(damage_dealt),
                damage_taken = VALUES(damage_taken),
                blocks_placed = VALUES(blocks_placed),
                blocks_broken = VALUES(blocks_broken),
                items_looted = VALUES(items_looted),
                mob_kills = VALUES(mob_kills),
                highest_win_streak = VALUES(highest_win_streak),
                current_win_streak = VALUES(current_win_streak),
                total_damage_dealt = VALUES(total_damage_dealt),
                total_damage_taken = VALUES(total_damage_taken),
                total_blocks_broken = VALUES(total_blocks_broken),
                total_blocks_placed = VALUES(total_blocks_placed),
                total_items_looted = VALUES(total_items_looted),
                total_mob_kills = VALUES(total_mob_kills)
            """;

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (conn == null) {
                plugin.getLogger().warning("无法保存统计数据到数据库：数据库连接为空");
                return;
            }
            pstmt.setString(1, playerUuid.toString());
            pstmt.setString(2, playerName);
            pstmt.setInt(3, totalKills);
            pstmt.setInt(4, totalDeaths);
            pstmt.setInt(5, totalWins);
            pstmt.setInt(6, totalGamesPlayed);
            pstmt.setDouble(7, damageDealt);
            pstmt.setDouble(8, damageTaken);
            pstmt.setInt(9, blocksPlaced);
            pstmt.setInt(10, blocksBroken);
            pstmt.setInt(11, itemsLooted);
            pstmt.setInt(12, mobKills);
            pstmt.setInt(13, highestWinStreak);
            pstmt.setInt(14, currentWinStreak);
            pstmt.setDouble(15, totalDamageDealt);
            pstmt.setDouble(16, totalDamageTaken);
            pstmt.setInt(17, totalBlocksBroken);
            pstmt.setInt(18, totalBlocksPlaced);
            pstmt.setInt(19, totalItemsLooted);
            pstmt.setInt(20, totalMobKills);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "保存统计数据失败: " + e.getMessage(), e);
        }
    }

    // 加载玩家统计数据
    public PlayerStatisticsData loadStatistics(UUID playerUuid) {
        String sql = "SELECT * FROM player_statistics WHERE player_uuid = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (conn == null) {
                return new PlayerStatisticsData(null, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            }
            pstmt.setString(1, playerUuid.toString());
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return new PlayerStatisticsData(
                    rs.getString("player_name"),
                    rs.getInt("total_kills"),
                    rs.getInt("total_deaths"),
                    rs.getInt("total_wins"),
                    rs.getInt("total_games_played"),
                    rs.getDouble("damage_dealt"),
                    rs.getDouble("damage_taken"),
                    rs.getInt("blocks_placed"),
                    rs.getInt("blocks_broken"),
                    rs.getInt("items_looted"),
                    rs.getInt("mob_kills"),
                    rs.getInt("highest_win_streak"),
                    rs.getInt("current_win_streak"),
                    rs.getDouble("total_damage_dealt"),
                    rs.getDouble("total_damage_taken"),
                    rs.getInt("total_blocks_broken"),
                    rs.getInt("total_blocks_placed"),
                    rs.getInt("total_items_looted"),
                    rs.getInt("total_mob_kills")
                );
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "加载统计数据失败: " + e.getMessage(), e);
        }

        return new PlayerStatisticsData(null, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    // 加载所有玩家统计数据
    public Map<UUID, PlayerStatisticsData> loadAllStatistics() {
        Map<UUID, PlayerStatisticsData> allStats = new HashMap<>();
        String sql = "SELECT * FROM player_statistics";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            if (conn == null) {
                return allStats;
            }

            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                PlayerStatisticsData data = new PlayerStatisticsData(
                    rs.getString("player_name"),
                    rs.getInt("total_kills"),
                    rs.getInt("total_deaths"),
                    rs.getInt("total_wins"),
                    rs.getInt("total_games_played"),
                    rs.getDouble("damage_dealt"),
                    rs.getDouble("damage_taken"),
                    rs.getInt("blocks_placed"),
                    rs.getInt("blocks_broken"),
                    rs.getInt("items_looted"),
                    rs.getInt("mob_kills"),
                    rs.getInt("highest_win_streak"),
                    rs.getInt("current_win_streak"),
                    rs.getDouble("total_damage_dealt"),
                    rs.getDouble("total_damage_taken"),
                    rs.getInt("total_blocks_broken"),
                    rs.getInt("total_blocks_placed"),
                    rs.getInt("total_items_looted"),
                    rs.getInt("total_mob_kills")
                );
                allStats.put(uuid, data);
            }
            plugin.getLogger().info("已从数据库加载 " + allStats.size() + " 条玩家统计记录");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "加载所有统计数据失败: " + e.getMessage(), e);
        }

        return allStats;
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("MySQL数据库连接池已关闭！");
        }
    }

    /**
     * 检查数据库是否已连接
     * @return 是否已连接
     */
    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }

    // 统计数据传输对象
    public record PlayerStatisticsData(
        String playerName,  // 玩家名字
        int totalKills,
        int totalDeaths,
        int totalWins,
        int totalGamesPlayed,
        double damageDealt,
        double damageTaken,
        int blocksPlaced,
        int blocksBroken,
        int itemsLooted,
        int mobKills,
        int highestWinStreak,
        int currentWinStreak,
        double totalDamageDealt,
        double totalDamageTaken,
        int totalBlocksBroken,
        int totalBlocksPlaced,
        int totalItemsLooted,
        int totalMobKills
    ) {}
}
