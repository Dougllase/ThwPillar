package com.newpillar.commands;

import com.newpillar.NewPillar;
import com.newpillar.game.GameManager;
import com.newpillar.game.enums.GameStatus;
import com.newpillar.game.enums.RuleType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /vote 命令 - 用于规则投票
 */
public class VoteCommand implements CommandExecutor, TabCompleter {
    private final NewPillar plugin;

    public VoteCommand(NewPillar plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // 提供1、2、3三个数字选项
            completions.add("1");
            completions.add("2");
            completions.add("3");
        }
        
        return completions;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以使用此命令！");
            return true;
        }

        GameManager gameManager = this.plugin.getGameManager();

        // 检查游戏状态
        if (gameManager.getGameStatus() != GameStatus.LOBBY) {
            player.sendMessage("§c只能在游戏大厅中使用投票命令！");
            return true;
        }

        // 检查是否正在投票
        if (!gameManager.isVotingActive()) {
            player.sendMessage("§c当前没有正在进行的投票！");
            return true;
        }

        // 无参数时打开投票GUI
        if (args.length < 1) {
            this.plugin.getVoteGUI().openVoteGUI(player);
            return true;
        }

        // 解析投票编号
        int voteNumber;
        try {
            voteNumber = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            player.sendMessage("§c请输入有效的数字！");
            return true;
        }

        // 验证编号范围
        if (voteNumber < 1 || voteNumber > 3) {
            player.sendMessage("§c请输入 1-3 之间的数字！");
            return true;
        }

        // 获取对应的规则
        RuleType selectedRule = gameManager.getVotingRule(voteNumber - 1);
        if (selectedRule == null) {
            player.sendMessage("§c无效的投票选项！");
            return true;
        }

        // 执行投票
        boolean success = gameManager.castVote(player, selectedRule);
        if (success) {
            player.sendMessage("§a你已成功投票给: §f" + selectedRule.getName());
        } else {
            player.sendMessage("§c投票失败，你可能已经投过票了！");
        }

        return true;
    }
}
