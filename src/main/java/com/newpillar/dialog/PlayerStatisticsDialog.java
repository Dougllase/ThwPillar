package com.newpillar.dialog;

import com.newpillar.NewPillar;
import com.newpillar.game.GameManager;
import com.newpillar.game.PlayerData;
import com.newpillar.game.enums.PlayerState;
import com.newpillar.game.data.StatisticsSystem;
import com.newpillar.game.data.StatisticsSystem.PlayerStatistics;

import java.util.*;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

public class PlayerStatisticsDialog extends AbstractDialog {
   private int currentPage = 0;
   private String currentLeaderboardType = "kills"; // kills, wins, kd, games
   
   public PlayerStatisticsDialog(NewPillar plugin, DialogManager dialogManager) {
      super(plugin, dialogManager);
   }

   @Override
   public String getTitle() {
      return "§d§l玩家统计信息";
   }

   @Override
   public int getSize() {
      return 54;
   }

   @Override
   protected void initializeItems() {
      this.fillBorder("purple");
      GameManager gameManager = this.plugin.getGameManager();
      StatisticsSystem statsSystem = this.plugin.getStatisticsSystem();
      
      if (gameManager != null && statsSystem != null) {
         PlayerData playerData = gameManager.getPlayerData(this.player.getUniqueId());
         PlayerStatistics myStats = statsSystem.getPlayerStats(this.player.getUniqueId());
         
         // 玩家信息
         this.inventory
            .setItem(
               10,
               this.createItem(
                  Material.PLAYER_HEAD,
                  "§e§l" + this.player.getName(),
                  new String[]{
                     "§7UUID: §f" + this.player.getUniqueId().toString().substring(0, 8) + "...",
                     "§7当前状态: §f" + this.getPlayerStatus(gameManager, this.player.getUniqueId()),
                     "",
                     "§6§l个人数据:",
                     "§7游戏场次: §f" + myStats.getTotalGamesPlayed(),
                     "§7获胜次数: §f" + myStats.getTotalWins(),
                     "§7总击杀: §f" + myStats.getTotalKills(),
                     "§7总死亡: §f" + myStats.getTotalDeaths(),
                     "§7胜率: §f" + String.format("%.1f%%", myStats.getWinRate()),
                     "§7K/D: §f" + String.format("%.2f", myStats.getKDRatio())
                  }
               )
            );
         
         // 本局游戏统计
         this.inventory
            .setItem(
               12,
               this.createItem(
                  Material.GOLDEN_APPLE,
                  "§a§l本局游戏",
                  new String[]{
                     "§7击杀: §f" + myStats.getKills(),
                     "§7死亡: §f" + myStats.getDeaths(),
                     "§7造成伤害: §f" + String.format("%.1f", myStats.getDamageDealt()),
                     "§7受到伤害: §f" + String.format("%.1f", myStats.getDamageTaken()),
                     "§7放置方块: §f" + myStats.getBlocksPlaced(),
                     "§7破坏方块: §f" + myStats.getBlocksBroken()
                  }
               )
            );

         // 排行榜类型选择
         this.inventory.setItem(14, this.createLeaderboardTypeItem("kills", "§c§l击杀榜", Material.DIAMOND_SWORD, "按总击杀数排序"));
         this.inventory.setItem(15, this.createLeaderboardTypeItem("wins", "§6§l胜场榜", Material.GOLD_INGOT, "按胜利次数排序"));
         this.inventory.setItem(16, this.createLeaderboardTypeItem("kd", "§b§lK/D榜", Material.IRON_SWORD, "按K/D比率排序"));
         this.inventory.setItem(17, this.createLeaderboardTypeItem("games", "§a§l场次榜", Material.GRASS_BLOCK, "按游戏场次排序"));
         
         // 显示排行榜（28-43槽位，每行8个，共2行显示16个）
         this.displayLeaderboard(statsSystem);
         
         // 分页按钮
         this.inventory.setItem(45, this.createItem(Material.ARROW, "§e§l上一页", new String[]{"§7点击查看上一页"}));
         this.inventory.setItem(46, this.createItem(Material.ARROW, "§e§l下一页", new String[]{"§7点击查看下一页"}));
         
         // 返回和关闭按钮
         this.inventory.setItem(48, this.createItem(Material.ARROW, "§e§l返回", new String[]{"§7点击返回设置菜单"}));
         this.inventory.setItem(50, this.createItem(Material.BARRIER, "§c§l关闭", new String[]{"§7点击关闭对话框"}));
      }
   }
   
   private void refresh() {
      this.inventory.clear();
      this.initializeItems();
   }
   
   private ItemStack createLeaderboardTypeItem(String type, String name, Material material, String description) {
      boolean isSelected = type.equals(currentLeaderboardType);
      String displayName = (isSelected ? "§a§l✔ " : "§f") + name.substring(4);
      String status = isSelected ? "§a当前选中" : "§7点击选择";
      return this.createItem(material, displayName, new String[]{status, "", description});
   }
   
   private void displayLeaderboard(StatisticsSystem statsSystem) {
      List<Map.Entry<UUID, PlayerStatistics>> sorted = new ArrayList<>(statsSystem.getAllPlayerStats().entrySet());
      
      // 排序
      switch (currentLeaderboardType.toLowerCase()) {
         case "kills" -> sorted.sort((a, b) -> Integer.compare(b.getValue().getTotalKills(), a.getValue().getTotalKills()));
         case "wins" -> sorted.sort((a, b) -> Integer.compare(b.getValue().getTotalWins(), a.getValue().getTotalWins()));
         case "kd" -> sorted.sort((a, b) -> Double.compare(b.getValue().getKDRatio(), a.getValue().getKDRatio()));
         case "games" -> sorted.sort((a, b) -> Integer.compare(b.getValue().getTotalGamesPlayed(), a.getValue().getTotalGamesPlayed()));
         default -> sorted.sort((a, b) -> Integer.compare(b.getValue().getTotalKills(), a.getValue().getTotalKills()));
      }
      
      // 计算分页
      int itemsPerPage = 16; // 28-43槽位
      int startIndex = currentPage * itemsPerPage;
      int endIndex = Math.min(startIndex + itemsPerPage, sorted.size());
      
      // 清空排行榜区域
      for (int i = 28; i <= 43; i++) {
         this.inventory.setItem(i, null);
      }
      
      // 显示排行榜条目
      int slot = 28;
      for (int i = startIndex; i < endIndex && slot <= 43; i++) {
         Map.Entry<UUID, PlayerStatistics> entry = sorted.get(i);
         UUID uuid = entry.getKey();
         PlayerStatistics stats = entry.getValue();
         
         Player target = Bukkit.getPlayer(uuid);
         String name = target != null ? target.getName() : "未知玩家";
         boolean isOnline = target != null && target.isOnline();
         
         // 获取排名颜色
         int rank = i + 1;
         String rankColor = switch (rank) {
            case 1 -> "§6";
            case 2 -> "§7";
            case 3 -> "§c";
            default -> "§f";
         };
         
         // 获取数值
         String value = switch (currentLeaderboardType.toLowerCase()) {
            case "kills" -> "击杀: " + stats.getTotalKills();
            case "wins" -> "胜场: " + stats.getTotalWins();
            case "kd" -> "K/D: " + String.format("%.2f", stats.getKDRatio());
            case "games" -> "场次: " + stats.getTotalGamesPlayed();
            default -> "击杀: " + stats.getTotalKills();
         };
         
         // 创建玩家头颅
         ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
         SkullMeta meta = (SkullMeta) skull.getItemMeta();
         if (meta != null) {
            meta.setDisplayName(rankColor + "#" + rank + " §f" + name + (isOnline ? " §a●" : " §7●"));
            meta.setLore(Arrays.asList(
               "§7" + value,
               "§7胜率: §f" + String.format("%.1f%%", stats.getWinRate()),
               "§7K/D: §f" + String.format("%.2f", stats.getKDRatio())
            ));
            if (target != null) {
               meta.setOwningPlayer(target);
            }
            skull.setItemMeta(meta);
         }
         
         this.inventory.setItem(slot, skull);
         
         // 移动到下一个槽位（跳过边框）
         slot++;
         if (slot == 36) slot = 37; // 跳过左边框
         if (slot == 44) break; // 到达右边框
      }
   }

   private String getPlayerStatus(GameManager gameManager, UUID uuid) {
      PlayerData playerData = gameManager.getPlayerData(uuid);
      if (playerData == null) {
         return "未知";
      } else {
         PlayerState state = playerData.getState();

         return switch (state) {
            case LOBBY -> "大厅";
            case READY -> "准备中";
            case INGAME -> "游戏中";
            case SPECTATOR -> "旁观者";
            case OUT -> "已出局";
         };
      }
   }

   @Override
   public void onClick(int slot) {
      switch (slot) {
         case 14 -> {
            currentLeaderboardType = "kills";
            currentPage = 0;
            this.refresh();
         }
         case 15 -> {
            currentLeaderboardType = "wins";
            currentPage = 0;
            this.refresh();
         }
         case 16 -> {
            currentLeaderboardType = "kd";
            currentPage = 0;
            this.refresh();
         }
         case 17 -> {
            currentLeaderboardType = "games";
            currentPage = 0;
            this.refresh();
         }
         case 45 -> {
            // 上一页
            if (currentPage > 0) {
               currentPage--;
               this.refresh();
            }
         }
         case 46 -> {
            // 下一页
            currentPage++;
            this.refresh();
         }
         case 48 -> this.dialogManager.openSettingsMenu(this.player);
         case 50 -> this.dialogManager.closeDialog(this.player);
      }
   }
}
