package com.newpillar.dialog;

import com.newpillar.game.enums.MapType;

import com.newpillar.NewPillar;
import com.newpillar.game.GameManager;
import org.bukkit.Material;

public class SettingsMenuDialog extends AbstractDialog {
   public SettingsMenuDialog(NewPillar plugin, DialogManager dialogManager) {
      super(plugin, dialogManager);
   }

   @Override
   public String getTitle() {
      return "§6§lNewPillar §e§l设置菜单";
   }

   @Override
   public int getSize() {
      return 27;
   }

   @Override
   protected void initializeItems() {
      this.fillBorder("gray");
      this.inventory.setItem(10, this.createItem(Material.GRASS_BLOCK, "§a§l地图选择", new String[]{"§7点击选择游戏地图", "§7当前地图: §f" + this.getCurrentMapName()}));
      this.inventory.setItem(12, this.createItem(Material.DIAMOND_SWORD, "§b§l游戏模式", new String[]{"§7点击选择游戏模式", "§7当前模式: §f个人模式"}));
      this.inventory.setItem(14, this.createItem(Material.BOOK, "§e§l游戏规则", new String[]{"§7点击设置游戏规则", "§7当前规则: §f" + this.getCurrentRuleName()}));
      this.inventory.setItem(16, this.createItem(Material.PLAYER_HEAD, "§d§l玩家统计", new String[]{"§7点击查看玩家统计数据", "§7包括击杀数、胜利数等"}));
      this.inventory.setItem(22, this.createItem(Material.BARRIER, "§c§l关闭菜单", new String[]{"§7点击关闭设置菜单"}));
   }

   @Override
   public void onClick(int slot) {
      switch (slot) {
         case 10:
            this.close();
            this.dialogManager.openMapSelection(this.player);
         case 11:
         case 13:
         case 15:
         case 17:
         case 18:
         case 19:
         case 20:
         case 21:
         default:
            break;
         case 12:
            this.close();
            this.dialogManager.openGameModeSelection(this.player);
            break;
         case 14:
            this.close();
            this.dialogManager.openRuleSelection(this.player);
            break;
         case 16:
            this.close();
            this.dialogManager.openPlayerStatistics(this.player);
            break;
         case 22:
            this.close();
            this.sendMessage("§7已关闭设置菜单");
      }
   }

   private String getCurrentMapName() {
      GameManager gameManager = this.plugin.getGameManager();
      return gameManager != null ? gameManager.getCurrentMapType().getDisplayName() : "未知";
   }

   private String getCurrentRuleName() {
      GameManager gameManager = this.plugin.getGameManager();
      return gameManager != null && gameManager.getRuleSystem() != null 
         ? gameManager.getRuleSystem().getCurrentRule().getName() 
         : "无规则";
   }
}
