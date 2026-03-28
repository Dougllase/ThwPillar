package com.newpillar.dialog;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public interface DialogHolder {
   void open(Player var1);

   void onClick(int var1);

   void onClose();

   Inventory getInventory();

   String getTitle();

   int getSize();
}
