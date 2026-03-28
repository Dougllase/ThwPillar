package com.newpillar.commands;

import com.newpillar.NewPillar;
import com.newpillar.reward.database.PlayerLimitData;
import com.newpillar.reward.database.RewardDatabaseManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /reward 命令 - 查询金币获取状态
 */
public class RewardCommand implements CommandExecutor {
    private final NewPillar plugin;
    
    public RewardCommand(NewPillar plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c该命令只能由玩家执行！");
            return true;
        }
        
        RewardDatabaseManager databaseManager = plugin.getRewardManager().getDatabaseManager();
        
        if (databaseManager == null || !databaseManager.isConnected()) {
            player.sendMessage("§c§l[错误] §7数据库连接失败，无法查询金币状态！");
            return true;
        }
        
        // 获取配置值
        int dailyLimit = plugin.getConfig().getInt("thwreward.limits.daily", 2500);
        int weeklyLimit = plugin.getConfig().getInt("thwreward.limits.weekly", 5000);
        
        // 获取玩家金币上限数据
        PlayerLimitData limitData = databaseManager.getPlayerLimitData(player.getUniqueId());
        limitData.checkAndResetLimits();
        
        int dailyEarned = limitData.getDailyEarned();
        int weeklyEarned = limitData.getWeeklyEarned();
        int dailyRemaining = Math.max(0, dailyLimit - dailyEarned);
        int weeklyRemaining = Math.max(0, weeklyLimit - weeklyEarned);
        
        // 发送金币状态信息
        player.sendMessage("");
        player.sendMessage("§6§l========== 金币获取状态 ==========");
        player.sendMessage("");
        
        // 今日金币
        player.sendMessage("§e§l今日金币:");
        player.sendMessage("  §7已获取: §6" + dailyEarned + " §7/ §c" + dailyLimit + " §7金币");
        player.sendMessage("  §7剩余可获取: §a" + dailyRemaining + " §7金币");
        
        // 进度条
        double dailyPercent = (double) dailyEarned / dailyLimit * 100;
        String dailyBar = generateProgressBar(dailyPercent);
        player.sendMessage("  " + dailyBar + " §7(" + String.format("%.1f", dailyPercent) + "%)");
        player.sendMessage("");
        
        // 本周金币
        player.sendMessage("§e§l本周金币:");
        player.sendMessage("  §7已获取: §6" + weeklyEarned + " §7/ §c" + weeklyLimit + " §7金币");
        player.sendMessage("  §7剩余可获取: §a" + weeklyRemaining + " §7金币");
        
        // 进度条
        double weeklyPercent = (double) weeklyEarned / weeklyLimit * 100;
        String weeklyBar = generateProgressBar(weeklyPercent);
        player.sendMessage("  " + weeklyBar + " §7(" + String.format("%.1f", weeklyPercent) + "%)");
        player.sendMessage("");
        
        // 状态提示
        if (dailyEarned >= dailyLimit) {
            player.sendMessage("§c§l注意: §7你已达到今日金币获取上限！");
        } else if (weeklyEarned >= weeklyLimit) {
            player.sendMessage("§c§l注意: §7你已达到本周金币获取上限！");
        } else if (dailyRemaining < dailyLimit * 0.2) {
            player.sendMessage("§e§l提示: §7今日金币即将达到上限！");
        }
        
        // 惩罚状态检查
        if (databaseManager.isPenaltyActive(player.getUniqueId())) {
            player.sendMessage("");
            player.sendMessage("§c§l警告: §7你因消极游戏被冻结奖励！");
        }
        
        player.sendMessage("");
        player.sendMessage("§7§o提示: 在小游戏服获取的金币需要在生存服使用 /reward 领取");
        player.sendMessage("§6§l==================================");
        player.sendMessage("");
        
        return true;
    }
    
    /**
     * 生成进度条
     */
    private String generateProgressBar(double percent) {
        int totalBars = 20;
        int filledBars = (int) (percent / 100.0 * totalBars);
        filledBars = Math.min(filledBars, totalBars);
        
        StringBuilder bar = new StringBuilder("§a");
        for (int i = 0; i < filledBars; i++) {
            bar.append("█");
        }
        bar.append("§7");
        for (int i = filledBars; i < totalBars; i++) {
            bar.append("█");
        }
        
        return bar.toString();
    }
}
