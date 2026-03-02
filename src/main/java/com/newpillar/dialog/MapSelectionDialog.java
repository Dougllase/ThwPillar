package com.newpillar.dialog;

import com.newpillar.NewPillar;
import com.newpillar.game.GameManager;
import com.newpillar.game.MapType;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class MapSelectionDialog extends AbstractDialog {
   public MapSelectionDialog(NewPillar plugin, DialogManager dialogManager) {
      super(plugin, dialogManager);
   }

   @Override
   public String getTitle() {
      return "§a§l选择游戏地图";
   }

   @Override
   public int getSize() {
      return 36;
   }

   @Override
   protected void initializeItems() {
      this.fillBorder("green");
      GameManager gameManager = this.plugin.getGameManager();
      MapType currentMap = gameManager.getCurrentMapType();
      this.inventory.setItem(10, this.createMapItem(MapType.WOOL, currentMap == MapType.WOOL));
      this.inventory.setItem(11, this.createMapItem(MapType.SEA, currentMap == MapType.SEA));
      this.inventory.setItem(12, this.createMapItem(MapType.NETHER, currentMap == MapType.NETHER));
      this.inventory.setItem(13, this.createMapItem(MapType.VOID, currentMap == MapType.VOID));
      this.inventory.setItem(19, this.createMapItem(MapType.GLASS, currentMap == MapType.GLASS));
      this.inventory.setItem(20, this.createMapItem(MapType.TNT, currentMap == MapType.TNT));
      this.inventory.setItem(21, this.createMapItem(MapType.TRAP_DOOR, currentMap == MapType.TRAP_DOOR));
      this.inventory.setItem(22, this.createMapItem(MapType.MOON, currentMap == MapType.MOON));
      this.inventory.setItem(30, this.createItem(Material.ARROW, "§e§l返回", new String[]{"§7点击返回设置菜单"}));
      this.inventory.setItem(32, this.createItem(Material.BARRIER, "§c§l关闭", new String[]{"§7点击关闭对话框"}));
   }

   private ItemStack createMapItem(MapType type, boolean isCurrent) {
      Material material = this.getMaterialForMap(type);
      String name = (isCurrent ? "§a§l✔ " : "§f") + type.getDisplayName();
      String status = isCurrent ? "§a当前选中" : "§7点击选择";
      return this.createItem(
         material, name, new String[]{status, "§8" + type.getDescription(), "", "§7地板: §f" + type.getFloorMaterial(), "§7柱子: §f基岩", "§7笼子: §f玻璃"}
      );
   }

   private Material getMaterialForMap(MapType type) {
      return switch (type) {
         case WOOL -> Material.WHITE_WOOL;
         case SEA -> Material.WATER_BUCKET;
         case NETHER -> Material.NETHERRACK;
         case VOID -> Material.BARRIER;
         case GLASS -> Material.GLASS;
         case TNT -> Material.TNT;
         case TRAP_DOOR -> Material.OAK_TRAPDOOR;
         case MOON -> Material.END_STONE;
      };
   }

   @Override
   public void onClick(int slot) {
      GameManager gameManager = this.plugin.getGameManager();
      switch (slot) {
         case 10:
            this.selectMap(MapType.WOOL);
            break;
         case 11:
            this.selectMap(MapType.SEA);
            break;
         case 12:
            this.selectMap(MapType.NETHER);
            break;
         case 13:
            this.selectMap(MapType.VOID);
         case 14:
         case 15:
         case 16:
         case 17:
         case 18:
         case 23:
         case 24:
         case 25:
         case 26:
         case 27:
         case 28:
         case 29:
         case 31:
         default:
            break;
         case 19:
            this.selectMap(MapType.GLASS);
            break;
         case 20:
            this.selectMap(MapType.TNT);
            break;
         case 21:
            this.selectMap(MapType.TRAP_DOOR);
            break;
         case 22:
            this.selectMap(MapType.MOON);
            break;
         case 30:
            this.backToSettings();
            break;
         case 32:
            this.close();
      }
   }

   private void selectMap(MapType type) {
      GameManager gameManager = this.plugin.getGameManager();
      gameManager.setCurrentMapType(type);
      this.sendMessage("§a地图已设置为: §f" + type.getDisplayName());
      this.sendMessage("§7" + type.getDescription());
      this.close();
      this.dialogManager.openMapSelection(this.player);
   }
}
