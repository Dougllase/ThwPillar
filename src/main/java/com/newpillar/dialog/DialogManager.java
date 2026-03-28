package com.newpillar.dialog;

import com.newpillar.NewPillar;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

public class DialogManager implements Listener {
   private final NewPillar plugin;
   private final Map<UUID, DialogHolder> openDialogs = new HashMap<>();

   public DialogManager(NewPillar plugin) {
      this.plugin = plugin;
      Bukkit.getPluginManager().registerEvents(this, plugin);
   }

   public void openSettingsMenu(Player player) {
      SettingsMenuDialog dialog = new SettingsMenuDialog(this.plugin, this);
      dialog.open(player);
      this.openDialogs.put(player.getUniqueId(), dialog);
   }

   public void openMapSelection(Player player) {
      MapSelectionDialog dialog = new MapSelectionDialog(this.plugin, this);
      dialog.open(player);
      this.openDialogs.put(player.getUniqueId(), dialog);
   }

   public void openGameModeSelection(Player player) {
      GameModeDialog dialog = new GameModeDialog(this.plugin, this);
      dialog.open(player);
      this.openDialogs.put(player.getUniqueId(), dialog);
   }

   public void openPlayerStatistics(Player player) {
      PlayerStatisticsDialog dialog = new PlayerStatisticsDialog(this.plugin, this);
      dialog.open(player);
      this.openDialogs.put(player.getUniqueId(), dialog);
   }

   public void openRuleSelection(Player player) {
      RuleSelectionDialog dialog = new RuleSelectionDialog(this.plugin, this);
      dialog.open(player);
      this.openDialogs.put(player.getUniqueId(), dialog);
   }

   public void closeDialog(Player player) {
      DialogHolder holder = this.openDialogs.remove(player.getUniqueId());
      if (holder != null) {
         player.closeInventory();
      }
   }

   public DialogHolder getOpenDialog(Player player) {
      return this.openDialogs.get(player.getUniqueId());
   }

   @EventHandler
   public void onInventoryClick(InventoryClickEvent event) {
      if (event.getWhoClicked() instanceof Player player) {
         DialogHolder dialog = this.openDialogs.get(player.getUniqueId());
         if (dialog != null) {
            if (event.getInventory().equals(dialog.getInventory())) {
               event.setCancelled(true);
               Bukkit.getRegionScheduler().execute(this.plugin, player.getLocation(), () -> dialog.onClick(event.getSlot()));
            }
         }
      }
   }

   @EventHandler
   public void onInventoryClose(InventoryCloseEvent event) {
      if (event.getPlayer() instanceof Player player) {
         DialogHolder dialog = this.openDialogs.remove(player.getUniqueId());
         if (dialog != null) {
            dialog.onClose();
         }
      }
   }

   public NewPillar getPlugin() {
      return this.plugin;
   }
}
