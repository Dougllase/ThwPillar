package com.newpillar.game;

import com.newpillar.game.enums.MapType;

import com.newpillar.game.enums.RuleType;

import com.newpillar.game.enums.PlayerState;

import com.newpillar.NewPillar;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * 投票管理器
 * 负责处理游戏规则投票逻辑
 */
public class VoteManager {
    private final NewPillar plugin;
    private final GameManager gameManager;
    
    private List<RuleType> votingRules = new ArrayList<>();
    private Map<UUID, RuleType> playerVotes = new HashMap<>();
    private boolean votingLocked = false;
    private RuleType selectedRule = RuleType.NONE;
    
    // 地图选择
    private MapType selectedMap = MapType.WOOL;
    private boolean mapSelected = false;
    
    public VoteManager(NewPillar plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
    }
    
    /**
     * 初始化规则投票
     */
    public void initRuleVoting() {
        this.votingRules.clear();
        this.playerVotes.clear();
        this.votingLocked = false;
        this.selectedRule = RuleType.NONE;
        
        // 获取所有可用规则（除了NONE）
        List<RuleType> allRules = new ArrayList<>();
        for (RuleType rule : RuleType.values()) {
            if (rule != RuleType.NONE) {
                allRules.add(rule);
            }
        }
        
        // 40%概率将"无规则"作为第一个选项
        if (Math.random() < 0.4) {
            this.votingRules.add(RuleType.NONE);
            // 再从其他规则中随机选择2个
            Collections.shuffle(allRules);
            int count = Math.min(2, allRules.size());
            for (int i = 0; i < count; i++) {
                this.votingRules.add(allRules.get(i));
            }
        } else {
            // 随机选择3个规则（不包含NONE）
            Collections.shuffle(allRules);
            int count = Math.min(3, allRules.size());
            for (int i = 0; i < count; i++) {
                this.votingRules.add(allRules.get(i));
            }
        }
        
        // 广播投票信息
        broadcastVotingInfo();
    }
    
    /**
     * 广播投票信息
     */
    private void broadcastVotingInfo() {
        gameManager.broadcastMessage("");
        gameManager.broadcastMessage("§6§l═══════════════════════════");
        gameManager.broadcastMessage("§e§l        规则投票");
        gameManager.broadcastMessage("");
        for (int i = 0; i < this.votingRules.size(); i++) {
            RuleType rule = this.votingRules.get(i);
            gameManager.broadcastMessage("§" + rule.getColor() + "§l[" + (i + 1) + "] " + rule.getName());
            gameManager.broadcastMessage("§7" + rule.getDescription());
            gameManager.broadcastMessage("");
        }
        gameManager.broadcastMessage("§e使用 §f/vote <编号> §e进行规则投票");
        gameManager.broadcastMessage("§6§l═══════════════════════════");
    }
    
    /**
     * 玩家投票（通过编号）
     */
    public void playerVoteRule(Player player, int voteIndex) {
        // 检查玩家是否在游戏中
        PlayerData data = gameManager.getPlayerData(player.getUniqueId());
        if (data == null || (data.getState() != PlayerState.READY && data.getState() != PlayerState.LOBBY)) {
            player.sendMessage("§c只有已加入游戏的玩家才能投票！");
            return;
        }
        
        if (this.votingLocked) {
            player.sendMessage("§c投票已锁定，无法投票！");
            return;
        }
        
        if (voteIndex < 1 || voteIndex > this.votingRules.size()) {
            player.sendMessage("§c无效的投票编号！");
            return;
        }
        
        RuleType votedRule = this.votingRules.get(voteIndex - 1);
        this.playerVotes.put(player.getUniqueId(), votedRule);
        player.sendMessage("§a你已投票给 §r§l" + votedRule.getName());
    }
    
    /**
     * 执行投票（供 VoteCommand 使用）
     */
    public boolean castVote(Player player, RuleType rule) {
        // 检查玩家是否在游戏中
        PlayerData data = gameManager.getPlayerData(player.getUniqueId());
        if (data == null || (data.getState() != PlayerState.READY && data.getState() != PlayerState.LOBBY)) {
            return false;
        }
        
        if (this.votingLocked) {
            return false;
        }
        
        // 检查该规则是否在投票选项中
        if (!this.votingRules.contains(rule)) {
            return false;
        }
        
        // 检查玩家是否已经投过票
        if (this.playerVotes.containsKey(player.getUniqueId())) {
            return false;
        }
        
        this.playerVotes.put(player.getUniqueId(), rule);
        return true;
    }
    
    /**
     * 锁定投票并选择规则
     */
    public void lockVotingAndSelectRule(RuleSystem ruleSystem) {
        this.votingLocked = true;
        
        // 统计票数
        Map<RuleType, Integer> voteCount = new HashMap<>();
        for (RuleType rule : this.votingRules) {
            voteCount.put(rule, 0);
        }
        
        for (RuleType vote : this.playerVotes.values()) {
            voteCount.put(vote, voteCount.getOrDefault(vote, 0) + 1);
        }
        
        // 找出票数最多的规则
        int maxVotes = -1;
        List<RuleType> topRules = new ArrayList<>();
        
        for (Map.Entry<RuleType, Integer> entry : voteCount.entrySet()) {
            if (entry.getValue() > maxVotes) {
                maxVotes = entry.getValue();
                topRules.clear();
                topRules.add(entry.getKey());
            } else if (entry.getValue() == maxVotes) {
                topRules.add(entry.getKey());
            }
        }
        
        // 如果有多个最高票，随机选择一个
        if (topRules.isEmpty() || maxVotes == 0) {
            // 无人投票，随机选择
            this.selectedRule = this.votingRules.get((int) (Math.random() * this.votingRules.size()));
            gameManager.broadcastMessage("§6§l[幸运之柱] §e无人投票，随机选择规则: §r§l" + this.selectedRule.getName());
        } else {
            this.selectedRule = topRules.get((int) (Math.random() * topRules.size()));
            gameManager.broadcastMessage("§6§l[幸运之柱] §e投票结束！本局规则: §r§l" + this.selectedRule.getName());
        }
        
        // 设置规则
        ruleSystem.setRule(this.selectedRule);
    }
    
    /**
     * 随机选择并公告地图
     */
    public MapType selectAndAnnounceMap() {
        // 随机选择地图
        MapType[] allMaps = MapType.values();
        this.selectedMap = allMaps[(int) (Math.random() * allMaps.length)];
        this.mapSelected = true;
        
        // 公告地图信息
        gameManager.broadcastMessage("");
        gameManager.broadcastMessage("§6§l═══════════════════════════");
        gameManager.broadcastMessage("§e§l        本局地图");
        gameManager.broadcastMessage("");
        gameManager.broadcastMessage("§6§l" + this.selectedMap.getDisplayName());
        gameManager.broadcastMessage("§7" + this.selectedMap.getDescription());
        gameManager.broadcastMessage("§6§l═══════════════════════════");
        
        return this.selectedMap;
    }
    
    /**
     * 重置投票状态
     */
    public void reset() {
        this.votingRules.clear();
        this.playerVotes.clear();
        this.votingLocked = false;
        this.selectedRule = RuleType.NONE;
        this.mapSelected = false;
    }
    
    /**
     * 检查是否正在进行投票
     */
    public boolean isVotingActive() {
        return !this.votingRules.isEmpty() && !this.votingLocked;
    }
    
    /**
     * 获取投票选项中的规则
     */
    public RuleType getVotingRule(int index) {
        if (index < 0 || index >= this.votingRules.size()) {
            return null;
        }
        return this.votingRules.get(index);
    }
    
    /**
     * 获取当前投票规则列表
     */
    public List<RuleType> getVotingRules() {
        return new ArrayList<>(this.votingRules);
    }
    
    /**
     * 获取已选择的规则
     */
    public RuleType getSelectedRule() {
        return this.selectedRule;
    }
    
    /**
     * 获取已选择的地图
     */
    public MapType getSelectedMap() {
        return this.selectedMap;
    }
    
    /**
     * 检查地图是否已选择
     */
    public boolean isMapSelected() {
        return this.mapSelected;
    }
    
    /**
     * 检查投票是否已锁定
     */
    public boolean isVotingLocked() {
        return this.votingLocked;
    }
}
