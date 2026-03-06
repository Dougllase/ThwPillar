package com.newpillar.game.events.eventtypes;

import com.newpillar.game.events.EventSystem;
import com.newpillar.game.enums.EventType;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Random;

/**
 * 国王游戏事件
 */
public class KingGameEvent implements GameEvent {
    
    private final Random random = new Random();
    private Player currentKing = null;
    
    @Override
    public String getName() {
        return "KING_GAME";
    }
    
    @Override
    public void start(EventSystem eventSystem, World world) {
        List<Player> players = eventSystem.getInGamePlayers();
        if (players.isEmpty()) return;
        
        // 随机选择国王
        this.currentKing = players.get(this.random.nextInt(players.size()));
        eventSystem.setCurrentKing(this.currentKing);
        
        // 广播消息
        Bukkit.broadcastMessage("§6§l[国王游戏] §e" + this.currentKing.getName() + " 成为了国王！");
        Bukkit.broadcastMessage("§c击杀国王可以获得奖励！");
        
        // 给国王效果
        this.currentKing.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 60, 0));
        this.currentKing.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 20 * 60, 0));
        
        // 播放音效
        for (Player player : players) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
        }
    }
    
    @Override
    public void end(EventSystem eventSystem, World world) {
        if (this.currentKing != null && this.currentKing.isOnline()) {
            Bukkit.broadcastMessage("§6§l[国王游戏] §e国王游戏结束！");
            
            // 结算国王奖励
            eventSystem.settleKingGame(this.currentKing);
        }
        eventSystem.setCurrentKing(null);
        this.currentKing = null;
    }
    
    @Override
    public int getDuration() {
        return EventType.KING_GAME.getDuration();
    }
    
    @Override
    public boolean canForceEnd() {
        return true;
    }
    
    public Player getCurrentKing() {
        return this.currentKing;
    }
}
