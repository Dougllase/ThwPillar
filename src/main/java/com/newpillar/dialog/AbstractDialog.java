package com.newpillar.dialog;

import com.newpillar.NewPillar;
import java.util.Arrays;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public abstract class AbstractDialog implements DialogHolder {
   protected final NewPillar plugin;
   protected final DialogManager dialogManager;
   protected Inventory inventory;
   protected Player player;

   public AbstractDialog(NewPillar plugin, DialogManager dialogManager) {
      this.plugin = plugin;
      this.dialogManager = dialogManager;
   }

   @Override
   public void open(Player player) {
      this.player = player;
      this.inventory = Bukkit.createInventory(null, this.getSize(), this.getTitle());
      this.initializeItems();
      player.openInventory(this.inventory);
   }

   @Override
   public Inventory getInventory() {
      return this.inventory;
   }

   @Override
   public void onClose() {
   }

   protected abstract void initializeItems();

   protected ItemStack createItem(Material material, String name, String... lore) {
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
         meta.setDisplayName(name);
         if (lore.length > 0) {
            meta.setLore(Arrays.asList(lore));
         }

         item.setItemMeta(meta);
      }

      return item;
   }

   protected ItemStack createGlassPane(String color) {
      String var3 = color.toLowerCase();

      Material material = switch (var3) {
         case "red" -> Material.RED_STAINED_GLASS_PANE;
         case "green" -> Material.GREEN_STAINED_GLASS_PANE;
         case "blue" -> Material.BLUE_STAINED_GLASS_PANE;
         case "yellow" -> Material.YELLOW_STAINED_GLASS_PANE;
         case "purple" -> Material.PURPLE_STAINED_GLASS_PANE;
         case "orange" -> Material.ORANGE_STAINED_GLASS_PANE;
         case "white" -> Material.WHITE_STAINED_GLASS_PANE;
         case "black" -> Material.BLACK_STAINED_GLASS_PANE;
         case "gray" -> Material.GRAY_STAINED_GLASS_PANE;
         case "light_gray" -> Material.LIGHT_GRAY_STAINED_GLASS_PANE;
         case "lime" -> Material.LIME_STAINED_GLASS_PANE;
         case "cyan" -> Material.CYAN_STAINED_GLASS_PANE;
         case "light_blue" -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;
         case "pink" -> Material.PINK_STAINED_GLASS_PANE;
         case "magenta" -> Material.MAGENTA_STAINED_GLASS_PANE;
         case "brown" -> Material.BROWN_STAINED_GLASS_PANE;
         default -> Material.GLASS_PANE;
      };
      return this.createItem(material, "§r");
   }

   protected void fillBorder(String color) {
      ItemStack glass = this.createGlassPane(color);
      int size = this.getSize();
      int rows = size / 9;

      for (int i = 0; i < 9; i++) {
         this.inventory.setItem(i, glass);
         this.inventory.setItem(size - 9 + i, glass);
      }

      for (int i = 0; i < rows; i++) {
         this.inventory.setItem(i * 9, glass);
         this.inventory.setItem(i * 9 + 8, glass);
      }
   }

   protected void backToSettings() {
      Bukkit.getRegionScheduler().execute(this.plugin, this.player.getLocation(), () -> this.dialogManager.openSettingsMenu(this.player));
   }

   protected void close() {
      this.player.closeInventory();
   }

   protected void sendMessage(String message) {
      this.player.sendMessage(message);
   }
}
