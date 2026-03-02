package com.newpillar.dialog;

import com.newpillar.NewPillar;
import com.newpillar.game.GameManager;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class GameModeDialog extends AbstractDialog {

   public GameModeDialog(NewPillar plugin, DialogManager dialogManager) {
      super(plugin, dialogManager);
   }
   
   private int getCurrentMode() {
      GameManager gameManager = this.plugin.getGameManager();
      if (gameManager == null) return 1;
      return switch (gameManager.getGameMode()) {
         case SOLO -> 1;
         case TEAM -> 2;
         case RED_VS_BLUE -> 3;
      };
   }
   
   private void setGameMode(int mode) {
      GameManager gameManager = this.plugin.getGameManager();
      if (gameManager == null) return;
      GameManager.GameModeType gameMode = switch (mode) {
         case 1 -> GameManager.GameModeType.SOLO;
         case 2 -> GameManager.GameModeType.TEAM;
         case 3 -> GameManager.GameModeType.RED_VS_BLUE;
         default -> GameManager.GameModeType.SOLO;
      };
      gameManager.setGameMode(gameMode);
   }

   @Override
   public String getTitle() {
      return "§b§l选择游戏模式";
   }

   @Override
   public int getSize() {
      return 27;
   }

   @Override
   protected void initializeItems() {
      this.fillBorder("blue");
      this.inventory.setItem(10, this.createModeItem(1, "§a§l个人模式", Material.DIAMOND_SWORD, "§7每个玩家独立作战", "§7最后存活的玩家获胜", "§7适合: §f2-12人"));
      this.inventory.setItem(12, this.createModeItem(2, "§b§l组队模式", Material.SHIELD, "§7玩家分成两队作战", "§7最后存活队伍获胜", "§7适合: §f4-12人", "§c开发中..."));
      this.inventory.setItem(14, this.createModeItem(3, "§c§l红蓝对抗", Material.RED_BANNER, "§7经典红蓝两队对抗", "§7全灭对方队伍获胜", "§7适合: §f2-12人", "§c开发中..."));
      this.inventory.setItem(21, this.createItem(Material.ARROW, "§e§l返回", new String[]{"§7点击返回设置菜单"}));
      this.inventory.setItem(23, this.createItem(Material.BARRIER, "§c§l关闭", new String[]{"§7点击关闭对话框"}));
   }

   private ItemStack createModeItem(int mode, String name, Material material, String... lore) {
      boolean isCurrent = mode == this.getCurrentMode();
      // 去掉名称中的颜色代码前缀（§a§l = 4个字符）
      String cleanName = name.length() > 4 ? name.substring(4) : name;
      String displayName = (isCurrent ? "§a§l✔ " : "§f") + cleanName;
      String status = isCurrent ? "§a当前选中" : "§7点击选择";
      String[] fullLore = new String[lore.length + 2];
      fullLore[0] = status;
      fullLore[1] = "";
      System.arraycopy(lore, 0, fullLore, 2, lore.length);
      return this.createItem(material, displayName, fullLore);
   }

   @Override
   public void onClick(int slot) {
      switch (slot) {
         case 10:
            this.selectMode(1, "个人模式");
            break;
         case 12:
            this.selectMode(2, "组队模式");
            break;
         case 14:
            this.selectMode(3, "红蓝对抗");
            break;
         case 21:
            this.backToSettings();
            break;
         case 23:
            this.close();
            break;
         default:
            break;
      }
   }

   private void selectMode(int mode, String modeName) {
      this.setGameMode(mode);
      this.sendMessage("§a游戏模式已设置为: §f" + modeName);
      // 刷新界面显示新的选中状态
      this.initializeItems();
   }
}
