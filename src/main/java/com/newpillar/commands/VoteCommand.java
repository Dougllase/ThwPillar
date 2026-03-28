package com.newpillar.commands;

import com.newpillar.NewPillar;
import com.newpillar.game.GameManager;
import com.newpillar.game.enums.GameStatus;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /vote 命令 - 用于规则投票（GUI版本）
 */
public class VoteCommand implements CommandExecutor, TabCompleter {
    private final NewPillar plugin;

    public VoteCommand(NewPillar plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // 返回空列表，不提供自动补全
        return new ArrayList<>();
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

        // 直接打开投票GUI（不再处理数字参数）
        this.plugin.getVoteGUI().openVoteGUI(player);
        return true;
    }
}
