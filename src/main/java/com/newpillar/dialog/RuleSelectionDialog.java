package com.newpillar.dialog;

import com.newpillar.NewPillar;
import com.newpillar.game.enums.RuleType;
import org.bukkit.Material;

public class RuleSelectionDialog extends AbstractDialog {
   public RuleSelectionDialog(NewPillar plugin, DialogManager dialogManager) {
      super(plugin, dialogManager);
   }

   @Override
   public String getTitle() {
      return "§6§l游戏规则选择";
   }

   @Override
   public int getSize() {
      return 27;
   }

   @Override
   protected void initializeItems() {
      this.fillBorder("gray");
      
      // 规则选择项
      this.inventory.setItem(10, this.createItem(Material.PUFFERFISH, "§d§l小小的也很可爱❤", 
         new String[]{"§7效果: §f玩家尺寸缩小为原来的1/3", "§e点击选择此规则"}));
      this.inventory.setItem(11, this.createItem(Material.SLIME_BLOCK, "§6§l大！大！大！", 
         new String[]{"§7效果: §f玩家尺寸增大为原来的3/2", "§e点击选择此规则"}));
      this.inventory.setItem(12, this.createItem(Material.FOX_SPAWN_EGG, "§b§l我的伙伴", 
         new String[]{"§7效果: §f获得一只狐狸", "§7狐狸存活时提供力量和生命回复", "§e点击选择此规则"}));
      this.inventory.setItem(13, this.createItem(Material.DIAMOND_SWORD, "§c§l一击必杀！", 
         new String[]{"§7效果: §f玩家攻击伤害变成40", "§e点击选择此规则"}));
      this.inventory.setItem(14, this.createItem(Material.CHEST, "§a§l背包交换",
         new String[]{"§7效果: §f随机事件固定为背包交换", "§e点击选择此规则"}));
      this.inventory.setItem(15, this.createItem(Material.ENDER_PEARL, "§5§l虚空的仁慈",
         new String[]{"§7效果: §f掉落虚空将被向上传送60格", "§e点击选择此规则"}));
      
      // 随机规则和关闭
      this.inventory.setItem(19, this.createItem(Material.COMPASS, "§e§l随机规则", 
         new String[]{"§7随机选择一种规则", "§e点击随机选择"}));
      this.inventory.setItem(20, this.createItem(Material.BARRIER, "§c§l无规则", 
         new String[]{"§7不启用任何规则", "§e点击取消规则"}));
      this.inventory.setItem(22, this.createItem(Material.ARROW, "§7§l返回", 
         new String[]{"§7返回设置菜单"}));
   }

   @Override
   public void onClick(int slot) {
      RuleType selectedRule = RuleType.NONE;
      String ruleName = "无规则";
      
      switch (slot) {
         case 10:
            selectedRule = RuleType.SMALL_CUTE;
            ruleName = "小小的也很可爱❤";
            break;
         case 11:
            selectedRule = RuleType.BIG;
            ruleName = "大！大！大！";
            break;
         case 12:
            selectedRule = RuleType.MY_PARTNER;
            ruleName = "我的伙伴";
            break;
         case 13:
            selectedRule = RuleType.PUNCH;
            ruleName = "一击必杀！";
            break;
         case 14:
            selectedRule = RuleType.INV_EXCHANGE;
            ruleName = "背包交换";
            break;
         case 15:
            selectedRule = RuleType.VOID_MERCY;
            ruleName = "虚空的仁慈";
            break;
         case 19:
            selectedRule = RuleType.getRandom();
            ruleName = selectedRule.getName();
            break;
         case 20:
            selectedRule = RuleType.NONE;
            ruleName = "无规则";
            break;
         case 22:
            this.close();
            this.dialogManager.openSettingsMenu(this.player);
            return;
         default:
            return;
      }
      
      // 设置规则
      this.plugin.getGameManager().getRuleSystem().setRule(selectedRule);
      this.sendMessage("§a已设置游戏规则: §e" + ruleName);
      this.close();
   }
}
