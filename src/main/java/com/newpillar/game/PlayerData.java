package com.newpillar.game;

import com.newpillar.game.enums.PlayerState;

import org.bukkit.Location;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

public class PlayerData {
   private final UUID uuid;
   private String playerName = null;
   private int gameId = 0;
   private int playerNumber = 0;
   private int deathCheck = 0;
   private PlayerState state = PlayerState.LOBBY;
   private Location deathLocation = null;
   
   // 最近伤害记录：攻击者UUID -> 伤害时间戳
   private final Map<UUID, Long> recentDamageMap = new HashMap<>();
   // 伤害记录有效期（毫秒）：20秒
   public static final long DAMAGE_RECORD_EXPIRY = 20000;

   public PlayerData(UUID uuid) {
      this.uuid = uuid;
   }

   public String getPlayerName() {
      return this.playerName;
   }

   public void setPlayerName(String playerName) {
      this.playerName = playerName;
   }

   public void reset() {
      this.deathCheck = 0;
      this.deathLocation = null;
   }

   public Location getDeathLocation() {
      return this.deathLocation;
   }

   public void setDeathLocation(Location location) {
      this.deathLocation = location;
   }

   public UUID getUuid() {
      return this.uuid;
   }

   public int getGameId() {
      return this.gameId;
   }

   public void setGameId(int gameId) {
      this.gameId = gameId;
   }

   public int getPlayerNumber() {
      return this.playerNumber;
   }

   public void setPlayerNumber(int playerNumber) {
      this.playerNumber = playerNumber;
   }

   public int getDeathCheck() {
      return this.deathCheck;
   }

   public void setDeathCheck(int deathCheck) {
      this.deathCheck = deathCheck;
   }

   public PlayerState getState() {
      return this.state;
   }

   public void setState(PlayerState state) {
      this.state = state;
   }
   
   /**
    * 记录受到的伤害
    * @param attackerUuid 攻击者UUID
    */
   public void recordDamage(UUID attackerUuid) {
      recentDamageMap.put(attackerUuid, System.currentTimeMillis());
   }
   
   /**
    * 获取最近20秒内对玩家造成伤害的攻击者
    * @return 攻击者UUID，如果没有则返回null
    */
   public UUID getRecentAttacker() {
      long now = System.currentTimeMillis();
      UUID recentAttacker = null;
      long mostRecentTime = 0;
      
      // 清理过期记录并找到最近的攻击者
      recentDamageMap.entrySet().removeIf(entry -> (now - entry.getValue()) > DAMAGE_RECORD_EXPIRY);
      
      for (Map.Entry<UUID, Long> entry : recentDamageMap.entrySet()) {
         if (entry.getValue() > mostRecentTime) {
            mostRecentTime = entry.getValue();
            recentAttacker = entry.getKey();
         }
      }
      
      return recentAttacker;
   }
   
   /**
    * 清除所有伤害记录
    */
   public void clearDamageRecords() {
      recentDamageMap.clear();
   }
}
