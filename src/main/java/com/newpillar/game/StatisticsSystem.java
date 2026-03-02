package com.newpillar.game;

import com.newpillar.NewPillar;
import com.newpillar.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

public class StatisticsSystem {
    private final NewPillar plugin;
    private final Map<UUID, PlayerStatistics> playerStats = new HashMap<>();
    
    public StatisticsSystem(NewPillar plugin) {
        this.plugin = plugin;
        loadData();
    }
    
    public void recordKill(Player killer, Player victim) {
        UUID uuid = killer.getUniqueId();
        PlayerStatistics stats = playerStats.computeIfAbsent(uuid, k -> new PlayerStatistics(uuid));
        stats.kills++;
        stats.totalKills++;
        
        // 更新成就系统
        plugin.getAchievementSystem().addKill(killer);
        
        saveData();
    }
    
    public void recordDeath(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerStatistics stats = playerStats.computeIfAbsent(uuid, k -> new PlayerStatistics(uuid));
        stats.deaths++;
        stats.totalDeaths++;
        saveData();
    }
    
    public void recordWin(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerStatistics stats = playerStats.computeIfAbsent(uuid, k -> new PlayerStatistics(uuid));
        stats.totalWins++;
        
        // 更新连胜记录
        stats.currentWinStreak++;
        if (stats.currentWinStreak > stats.highestWinStreak) {
            stats.highestWinStreak = stats.currentWinStreak;
        }
        
        // 更新成就系统
        plugin.getAchievementSystem().addWin(player);
        
        saveData();
    }
    
    public void recordLoss(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerStatistics stats = playerStats.computeIfAbsent(uuid, k -> new PlayerStatistics(uuid));
        // 重置当前连胜
        stats.currentWinStreak = 0;
        saveData();
    }
    
    public void recordGamePlayed(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerStatistics stats = playerStats.computeIfAbsent(uuid, k -> new PlayerStatistics(uuid));
        stats.totalGamesPlayed++;
        saveData();
    }
    
    public void recordDamageDealt(Player player, double damage) {
        UUID uuid = player.getUniqueId();
        PlayerStatistics stats = playerStats.computeIfAbsent(uuid, k -> new PlayerStatistics(uuid));
        stats.damageDealt += damage;
        stats.totalDamageDealt += damage;
        saveData();
    }

    public void recordDamageTaken(Player player, double damage) {
        UUID uuid = player.getUniqueId();
        PlayerStatistics stats = playerStats.computeIfAbsent(uuid, k -> new PlayerStatistics(uuid));
        stats.damageTaken += damage;
        stats.totalDamageTaken += damage;
        saveData();
    }

    public void recordBlockPlaced(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerStatistics stats = playerStats.computeIfAbsent(uuid, k -> new PlayerStatistics(uuid));
        stats.blocksPlaced++;
        stats.totalBlocksPlaced++;
        saveData();
    }

    public void recordBlockBroken(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerStatistics stats = playerStats.computeIfAbsent(uuid, k -> new PlayerStatistics(uuid));
        stats.blocksBroken++;
        stats.totalBlocksBroken++;
        saveData();
    }

    public void recordItemLooted(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerStatistics stats = playerStats.computeIfAbsent(uuid, k -> new PlayerStatistics(uuid));
        stats.itemsLooted++;
        stats.totalItemsLooted++;
        saveData();
    }
    
    public void resetGameStats() {
        for (PlayerStatistics stats : playerStats.values()) {
            stats.resetGameStats();
        }
    }
    
    /**
     * 获取玩家统计数据
     */
    public PlayerStatistics getPlayerStats(UUID uuid) {
        return playerStats.computeIfAbsent(uuid, k -> new PlayerStatistics(uuid));
    }
    
    /**
     * 获取所有玩家统计数据
     */
    public Map<UUID, PlayerStatistics> getAllPlayerStats() {
        return new HashMap<>(playerStats);
    }
    
    /**
     * 从数据库加载玩家统计数据
     */
    public void loadPlayerData(UUID uuid) {
        DatabaseManager db = plugin.getDatabaseManager();
        if (db == null) return;
        
        DatabaseManager.PlayerStatisticsData data = db.loadStatistics(uuid);
        PlayerStatistics stats = playerStats.computeIfAbsent(uuid, k -> new PlayerStatistics(uuid));
        
        stats.totalKills = data.getTotalKills();
        stats.totalDeaths = data.getTotalDeaths();
        stats.totalWins = data.getTotalWins();
        stats.totalGamesPlayed = data.getTotalGamesPlayed();
        stats.damageDealt = data.getDamageDealt();
        stats.damageTaken = data.getDamageTaken();
        stats.blocksPlaced = data.getBlocksPlaced();
        stats.blocksBroken = data.getBlocksBroken();
        stats.itemsLooted = data.getItemsLooted();
        
        // 加载新增统计项
        stats.highestWinStreak = data.getHighestWinStreak();
        stats.currentWinStreak = data.getCurrentWinStreak();
        stats.totalDamageDealt = data.getTotalDamageDealt();
        stats.totalDamageTaken = data.getTotalDamageTaken();
        stats.totalBlocksBroken = data.getTotalBlocksBroken();
        stats.totalBlocksPlaced = data.getTotalBlocksPlaced();
        stats.totalItemsLooted = data.getTotalItemsLooted();
        
        plugin.getLogger().info("已从数据库加载玩家 " + uuid + " 的统计数据");
    }
    
    public void showStatistics(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerStatistics stats = playerStats.getOrDefault(uuid, new PlayerStatistics(uuid));
        
        player.sendMessage("§6§l========== 个人统计 ==========");
        player.sendMessage("");
        player.sendMessage("§e§l本局游戏:");
        player.sendMessage("§7击杀: §f" + stats.kills);
        player.sendMessage("§7死亡: §f" + stats.deaths);
        player.sendMessage("§7造成伤害: §f" + String.format("%.1f", stats.damageDealt));
        player.sendMessage("§7受到伤害: §f" + String.format("%.1f", stats.damageTaken));
        player.sendMessage("§7放置方块: §f" + stats.blocksPlaced);
        player.sendMessage("§7破坏方块: §f" + stats.blocksBroken);
        player.sendMessage("§7获得物品: §f" + stats.itemsLooted);
        player.sendMessage("");
        player.sendMessage("§e§l历史总计:");
        player.sendMessage("§7游戏场次: §f" + stats.totalGamesPlayed);
        player.sendMessage("§7总击杀: §f" + stats.totalKills);
        player.sendMessage("§7总死亡: §f" + stats.totalDeaths);
        player.sendMessage("§7胜利次数: §f" + stats.totalWins);
        player.sendMessage("§7K/D比率: §f" + String.format("%.2f", stats.getKDRatio()));
        player.sendMessage("§7胜率: §f" + String.format("%.1f%%", stats.getWinRate()));
        player.sendMessage("§6§l==============================");
    }
    
    public void showLeaderboard(Player player, String type) {
        List<Map.Entry<UUID, PlayerStatistics>> sorted = new ArrayList<>(playerStats.entrySet());
        
        switch (type.toLowerCase()) {
            case "kills" -> sorted.sort((a, b) -> Integer.compare(b.getValue().totalKills, a.getValue().totalKills));
            case "wins" -> sorted.sort((a, b) -> Integer.compare(b.getValue().totalWins, a.getValue().totalWins));
            case "kd" -> sorted.sort((a, b) -> Double.compare(b.getValue().getKDRatio(), a.getValue().getKDRatio()));
            case "games" -> sorted.sort((a, b) -> Integer.compare(b.getValue().totalGamesPlayed, a.getValue().totalGamesPlayed));
            default -> sorted.sort((a, b) -> Integer.compare(b.getValue().totalKills, a.getValue().totalKills));
        }
        
        player.sendMessage("§6§l========== 排行榜 ==========");
        player.sendMessage("§7类型: §f" + getTypeName(type));
        player.sendMessage("");
        
        int rank = 1;
        for (int i = 0; i < Math.min(10, sorted.size()); i++) {
            Map.Entry<UUID, PlayerStatistics> entry = sorted.get(i);
            Player target = Bukkit.getPlayer(entry.getKey());
            String name = target != null ? target.getName() : "未知玩家";
            PlayerStatistics stats = entry.getValue();
            
            String value = switch (type.toLowerCase()) {
                case "kills" -> String.valueOf(stats.totalKills);
                case "wins" -> String.valueOf(stats.totalWins);
                case "kd" -> String.format("%.2f", stats.getKDRatio());
                case "games" -> String.valueOf(stats.totalGamesPlayed);
                default -> String.valueOf(stats.totalKills);
            };
            
            String color = switch (rank) {
                case 1 -> "§6";
                case 2 -> "§7";
                case 3 -> "§c";
                default -> "§f";
            };
            
            player.sendMessage(color + "#" + rank + " §f" + name + " §7- " + value);
            rank++;
        }
        
        player.sendMessage("§6§l==============================");
    }
    
    private String getTypeName(String type) {
        return switch (type.toLowerCase()) {
            case "kills" -> "击杀数";
            case "wins" -> "胜利数";
            case "kd" -> "K/D比率";
            case "games" -> "游戏场次";
            default -> "击杀数";
        };
    }
    
    private void loadData() {
        // 从配置文件加载数据（兼容旧版本）
        if (plugin.getConfig().contains("statistics")) {
            for (String uuidStr : plugin.getConfig().getConfigurationSection("statistics").getKeys(false)) {
                UUID uuid = UUID.fromString(uuidStr);
                PlayerStatistics stats = new PlayerStatistics(uuid);
                
                String path = "statistics." + uuidStr;
                stats.totalKills = plugin.getConfig().getInt(path + ".totalKills", 0);
                stats.totalDeaths = plugin.getConfig().getInt(path + ".totalDeaths", 0);
                stats.totalWins = plugin.getConfig().getInt(path + ".totalWins", 0);
                stats.totalGamesPlayed = plugin.getConfig().getInt(path + ".totalGamesPlayed", 0);
                stats.damageDealt = plugin.getConfig().getDouble(path + ".damageDealt", 0);
                stats.damageTaken = plugin.getConfig().getDouble(path + ".damageTaken", 0);
                stats.blocksPlaced = plugin.getConfig().getInt(path + ".blocksPlaced", 0);
                stats.blocksBroken = plugin.getConfig().getInt(path + ".blocksBroken", 0);
                stats.itemsLooted = plugin.getConfig().getInt(path + ".itemsLooted", 0);
                
                playerStats.put(uuid, stats);
            }
        }
    }
    
    private void saveData() {
        // 保存到数据库
        DatabaseManager db = plugin.getDatabaseManager();
        if (db != null) {
            for (Map.Entry<UUID, PlayerStatistics> entry : playerStats.entrySet()) {
                UUID uuid = entry.getKey();
                PlayerStatistics stats = entry.getValue();
                
                // 创建PlayerStatisticsData对象
                DatabaseManager.PlayerStatisticsData data = new DatabaseManager.PlayerStatisticsData();
                data.setTotalKills(stats.totalKills);
                data.setTotalDeaths(stats.totalDeaths);
                data.setTotalWins(stats.totalWins);
                data.setTotalGamesPlayed(stats.totalGamesPlayed);
                data.setDamageDealt(stats.damageDealt);
                data.setDamageTaken(stats.damageTaken);
                data.setBlocksPlaced(stats.blocksPlaced);
                data.setBlocksBroken(stats.blocksBroken);
                data.setItemsLooted(stats.itemsLooted);
                data.setHighestWinStreak(stats.highestWinStreak);
                data.setCurrentWinStreak(stats.currentWinStreak);
                data.setTotalDamageDealt(stats.totalDamageDealt);
                data.setTotalDamageTaken(stats.totalDamageTaken);
                data.setTotalBlocksBroken(stats.totalBlocksBroken);
                data.setTotalBlocksPlaced(stats.totalBlocksPlaced);
                data.setTotalItemsLooted(stats.totalItemsLooted);
                
                db.saveStatistics(uuid, data);
            }
        }

        // 同时保存到配置文件（作为备份）
        for (Map.Entry<UUID, PlayerStatistics> entry : playerStats.entrySet()) {
            String uuidStr = entry.getKey().toString();
            PlayerStatistics stats = entry.getValue();
            String path = "statistics." + uuidStr;

            plugin.getConfig().set(path + ".totalKills", stats.totalKills);
            plugin.getConfig().set(path + ".totalDeaths", stats.totalDeaths);
            plugin.getConfig().set(path + ".totalWins", stats.totalWins);
            plugin.getConfig().set(path + ".totalGamesPlayed", stats.totalGamesPlayed);
            plugin.getConfig().set(path + ".damageDealt", stats.damageDealt);
            plugin.getConfig().set(path + ".damageTaken", stats.damageTaken);
            plugin.getConfig().set(path + ".blocksPlaced", stats.blocksPlaced);
            plugin.getConfig().set(path + ".blocksBroken", stats.blocksBroken);
            plugin.getConfig().set(path + ".itemsLooted", stats.itemsLooted);

            // 保存新增统计项
            plugin.getConfig().set(path + ".highestWinStreak", stats.highestWinStreak);
            plugin.getConfig().set(path + ".currentWinStreak", stats.currentWinStreak);
            plugin.getConfig().set(path + ".totalDamageDealt", stats.totalDamageDealt);
            plugin.getConfig().set(path + ".totalDamageTaken", stats.totalDamageTaken);
            plugin.getConfig().set(path + ".totalBlocksBroken", stats.totalBlocksBroken);
            plugin.getConfig().set(path + ".totalBlocksPlaced", stats.totalBlocksPlaced);
            plugin.getConfig().set(path + ".totalItemsLooted", stats.totalItemsLooted);
        }

        plugin.saveConfig();
    }
    
    /**
     * 同步指定玩家的统计信息到数据库
     */
    public void syncPlayerToDatabase(UUID uuid) {
        PlayerStatistics stats = playerStats.get(uuid);
        if (stats == null) return;

        DatabaseManager db = plugin.getDatabaseManager();
        if (db != null) {
            // 创建PlayerStatisticsData对象
            DatabaseManager.PlayerStatisticsData data = new DatabaseManager.PlayerStatisticsData();
            data.setTotalKills(stats.totalKills);
            data.setTotalDeaths(stats.totalDeaths);
            data.setTotalWins(stats.totalWins);
            data.setTotalGamesPlayed(stats.totalGamesPlayed);
            data.setDamageDealt(stats.damageDealt);
            data.setDamageTaken(stats.damageTaken);
            data.setBlocksPlaced(stats.blocksPlaced);
            data.setBlocksBroken(stats.blocksBroken);
            data.setItemsLooted(stats.itemsLooted);
            data.setHighestWinStreak(stats.highestWinStreak);
            data.setCurrentWinStreak(stats.currentWinStreak);
            data.setTotalDamageDealt(stats.totalDamageDealt);
            data.setTotalDamageTaken(stats.totalDamageTaken);
            data.setTotalBlocksBroken(stats.totalBlocksBroken);
            data.setTotalBlocksPlaced(stats.totalBlocksPlaced);
            data.setTotalItemsLooted(stats.totalItemsLooted);
            
            db.saveStatistics(uuid, data);
            plugin.getLogger().info("玩家 " + uuid + " 的统计信息已同步到数据库");
        }

        // 同时保存到配置文件
        String uuidStr = uuid.toString();
        String path = "statistics." + uuidStr;

        plugin.getConfig().set(path + ".totalKills", stats.totalKills);
        plugin.getConfig().set(path + ".totalDeaths", stats.totalDeaths);
        plugin.getConfig().set(path + ".totalWins", stats.totalWins);
        plugin.getConfig().set(path + ".totalGamesPlayed", stats.totalGamesPlayed);
        plugin.getConfig().set(path + ".damageDealt", stats.damageDealt);
        plugin.getConfig().set(path + ".damageTaken", stats.damageTaken);
        plugin.getConfig().set(path + ".blocksPlaced", stats.blocksPlaced);
        plugin.getConfig().set(path + ".blocksBroken", stats.blocksBroken);
        plugin.getConfig().set(path + ".itemsLooted", stats.itemsLooted);

        // 保存新增统计项
        plugin.getConfig().set(path + ".highestWinStreak", stats.highestWinStreak);
        plugin.getConfig().set(path + ".currentWinStreak", stats.currentWinStreak);
        plugin.getConfig().set(path + ".totalDamageDealt", stats.totalDamageDealt);
        plugin.getConfig().set(path + ".totalDamageTaken", stats.totalDamageTaken);
        plugin.getConfig().set(path + ".totalBlocksBroken", stats.totalBlocksBroken);
        plugin.getConfig().set(path + ".totalBlocksPlaced", stats.totalBlocksPlaced);
        plugin.getConfig().set(path + ".totalItemsLooted", stats.totalItemsLooted);
        plugin.saveConfig();
    }
    
    public static class PlayerStatistics {
        private final UUID uuid;
        
        // 本局游戏统计
        int kills = 0;
        int deaths = 0;
        double damageDealt = 0;
        double damageTaken = 0;
        int blocksPlaced = 0;
        int blocksBroken = 0;
        int itemsLooted = 0;
        
        // 历史总计
        int totalKills = 0;
        int totalDeaths = 0;
        int totalWins = 0;
        int totalGamesPlayed = 0;
        
        // 新增统计项
        int highestWinStreak = 0;      // 最高连胜
        int currentWinStreak = 0;      // 当前连胜
        double totalDamageDealt = 0;   // 总造成伤害
        double totalDamageTaken = 0;   // 总受到伤害
        int totalBlocksBroken = 0;     // 总破坏方块
        int totalBlocksPlaced = 0;     // 总放置方块
        int totalItemsLooted = 0;      // 总获取物品
        
        PlayerStatistics(UUID uuid) {
            this.uuid = uuid;
        }
        
        void resetGameStats() {
            kills = 0;
            deaths = 0;
            damageDealt = 0;
            damageTaken = 0;
            blocksPlaced = 0;
            blocksBroken = 0;
            itemsLooted = 0;
        }
        
        public double getKDRatio() {
            return totalDeaths == 0 ? totalKills : (double) totalKills / totalDeaths;
        }
        
        public double getWinRate() {
            return totalGamesPlayed == 0 ? 0 : (double) totalWins / totalGamesPlayed * 100;
        }
        
        // Getters
        public UUID getUuid() { return uuid; }
        public int getKills() { return kills; }
        public int getDeaths() { return deaths; }
        public double getDamageDealt() { return damageDealt; }
        public double getDamageTaken() { return damageTaken; }
        public int getBlocksPlaced() { return blocksPlaced; }
        public int getBlocksBroken() { return blocksBroken; }
        public int getItemsLooted() { return itemsLooted; }
        public int getTotalKills() { return totalKills; }
        public int getTotalDeaths() { return totalDeaths; }
        public int getTotalWins() { return totalWins; }
        public int getTotalGamesPlayed() { return totalGamesPlayed; }
        
        // 新增 Getters
        public int getHighestWinStreak() { return highestWinStreak; }
        public int getCurrentWinStreak() { return currentWinStreak; }
        public double getTotalDamageDealt() { return totalDamageDealt; }
        public double getTotalDamageTaken() { return totalDamageTaken; }
        public int getTotalBlocksBroken() { return totalBlocksBroken; }
        public int getTotalBlocksPlaced() { return totalBlocksPlaced; }
        public int getTotalItemsLooted() { return totalItemsLooted; }
    }
}
