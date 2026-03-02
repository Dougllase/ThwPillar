package com.newpillar.game;

import org.bukkit.Location;
import java.util.UUID;

public class PlayerData {
   private final UUID uuid;
   private String playerName = null;
   private int gameId = 0;
   private int playerNumber = 0;
   private int deathCheck = 0;
   private GameManager.PlayerState state = GameManager.PlayerState.LOBBY;
   private Location deathLocation = null;

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
      // 清除deathLocation防止内存泄漏（Location包含World引用）
      this.deathLocation = null;
   }
   
   /**
    * 玩家退出时清理所有数据，防止内存泄漏
    */
   public void cleanup() {
      this.deathLocation = null;
      this.playerName = null;
      this.state = GameManager.PlayerState.LOBBY;
      this.gameId = 0;
      this.playerNumber = 0;
      this.deathCheck = 0;
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

   public GameManager.PlayerState getState() {
      return this.state;
   }

   public void setState(GameManager.PlayerState state) {
      this.state = state;
   }
}
