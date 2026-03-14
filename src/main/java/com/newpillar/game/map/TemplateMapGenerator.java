package com.newpillar.game.map;

import com.newpillar.game.enums.MapType;

import com.newpillar.game.GameManager;

import com.newpillar.NewPillar;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

public class TemplateMapGenerator {
   private final NewPillar plugin;
   private final GameManager gameManager;
   private final com.newpillar.utils.StructureTemplate structureTemplate;

   public TemplateMapGenerator(NewPillar plugin, GameManager gameManager) {
      this.plugin = plugin;
      this.gameManager = gameManager;
      this.structureTemplate = plugin.getStructureTemplate();
   }

   public void generateMap(MapTemplate template, Location center) {
      World world = center.getWorld();
      if (world == null) {
         this.plugin.getLogger().severe("无法生成地图：世界为空");
         return;
      }

      this.plugin.getLogger().info("生成地图: " + template.getMapType().getDisplayName() + " (玩家数: " + template.getPlayerCount() + ")");

      // 放置底座结构（必须成功）
      boolean baseSuccess = this.placePillarBase(template, center);
      if (!baseSuccess) {
         this.plugin.getLogger().severe("地图生成失败：无法放置底座结构");
         return;
      }

      // 生成柱子
      for (MapTemplate.PillarConfig pillar : template.getPillars()) {
         this.generatePillar(world, center, template, pillar, template.getPillarMaterial());
      }

      // 放置笼子（必须成功）
      for (MapTemplate.PillarConfig pillar : template.getPillars()) {
         boolean cageSuccess = this.placeCage(world, center, template, pillar, template.getCageMaterial());
         if (!cageSuccess) {
            this.plugin.getLogger().severe("地图生成失败：无法放置笼子结构");
            return;
         }
      }

      // 月球地图：在底座中心放置战利品箱
      if (template.getMapType() == MapType.MOON) {
         this.placeMoonLootChest(center);
      }

      this.plugin.getLogger().info("地图生成完成！");
   }

   /**
    * 放置月球地图战利品箱
    * 与数据包 moon.json 一致：鞘翅、烟花火箭、金苹果/苹果、末地城宝藏
    * 位置：(-1, 5, -1)
    */
   private void placeMoonLootChest(Location center) {
      World world = center.getWorld();
      if (world == null) return;

      // 箱子位置：(-1, 5, -1)
      Location chestLocation = center.clone().add(-1, 5, -1);

      this.plugin.getLogger().info("[月球地图] 在位置 " + chestLocation + " 放置战利品箱");

      Bukkit.getRegionScheduler().execute(this.plugin, chestLocation, () -> {
         Block chestBlock = world.getBlockAt(chestLocation);
         chestBlock.setType(Material.CHEST);

         if (chestBlock.getState() instanceof Chest chest) {
            Inventory inv = chest.getInventory();
            Random random = new Random();

            // 获取随机空槽位列表
            java.util.List<Integer> emptySlots = getRandomEmptySlots(inv, 27);
            int slotIndex = 0;

            // Pool 1: 鞘翅 (必定)
            if (slotIndex < emptySlots.size()) {
               inv.setItem(emptySlots.get(slotIndex++), new ItemStack(Material.ELYTRA));
            }

            // Pool 2: 烟花火箭 4-10个 (分散到多个格子)
            int fireworkCount = 4 + random.nextInt(7); // 4-10
            // 每格最多放3个，分散放置
            while (fireworkCount > 0 && slotIndex < emptySlots.size()) {
               int count = Math.min(3, fireworkCount);
               inv.setItem(emptySlots.get(slotIndex++), new ItemStack(Material.FIREWORK_ROCKET, count));
               fireworkCount -= count;
            }

            // Pool 3: 金苹果或普通苹果
            if (slotIndex < emptySlots.size()) {
               if (random.nextBoolean()) {
                  inv.setItem(emptySlots.get(slotIndex++), new ItemStack(Material.GOLDEN_APPLE));
               } else {
                  int appleCount = 1 + random.nextInt(5); // 1-5
                  inv.setItem(emptySlots.get(slotIndex++), new ItemStack(Material.APPLE, appleCount));
               }
            }

            // Pool 4: 末地城宝藏战利品 (简化实现)
            // 添加一些末地城常见物品
            Material[] endCityLoot = {
               Material.DIAMOND, Material.IRON_INGOT, Material.GOLD_INGOT,
               Material.EMERALD, Material.SADDLE, Material.IRON_HORSE_ARMOR,
               Material.GOLDEN_HORSE_ARMOR, Material.DIAMOND_HORSE_ARMOR
            };
            for (int i = 0; i < 3 && slotIndex < emptySlots.size(); i++) {
               Material loot = endCityLoot[random.nextInt(endCityLoot.length)];
               int count = 1 + random.nextInt(3);
               inv.setItem(emptySlots.get(slotIndex++), new ItemStack(loot, count));
            }

            this.plugin.getLogger().info("[月球地图] 战利品箱已填充");
         }
      });
   }

   /**
    * 获取随机的空槽位列表
    * @param inv 箱子背包
    * @param count 需要的槽位数量
    * @return 随机排序的槽位列表
    */
   private java.util.List<Integer> getRandomEmptySlots(Inventory inv, int count) {
      java.util.List<Integer> slots = new java.util.ArrayList<>();
      for (int i = 0; i < inv.getSize() && slots.size() < count; i++) {
         slots.add(i);
      }
      java.util.Collections.shuffle(slots, new Random());
      return slots;
   }

   /**
    * 放置底座结构
    * @return 是否成功放置
    */
   private boolean placePillarBase(MapTemplate template, Location center) {
      if (template.getMapType() == MapType.VOID) {
         // 虚空地图不需要底座
         return true;
      }

      int playerCount = template.getPlayerCount();
      int offsetX;
      int offsetZ;
      if (playerCount <= 2) {
         offsetX = -17;
         offsetZ = -17;
      } else {
         offsetX = -20;
         offsetZ = -20;
      }

      Location baseLocation = center.clone().add((double)offsetX, 0.0, (double)offsetZ);
      String mapName = template.getMapType().name().toLowerCase();
      String baseStructureName;
      if (playerCount <= 2) {
         baseStructureName = "pillar/" + mapName + "/2";
      } else if (template.getMapType() == MapType.SEA && playerCount <= 5) {
         baseStructureName = "pillar/" + mapName + "/5";
      } else {
         baseStructureName = "pillar/" + mapName + "/9";
      }

      this.plugin.getLogger().info("放置底座结构: " + baseStructureName + " 在位置 " + baseLocation);

      // 检查结构是否存在
      if (!this.structureTemplate.hasStructure(baseStructureName)) {
         this.plugin.getLogger().severe("找不到底座结构文件: " + baseStructureName);
         this.plugin.getLogger().severe("请确保结构文件存在于 plugins/NewPillar/structures/" + baseStructureName + ".nbt");
         return false;
      }

      boolean success = this.structureTemplate.placeStructure(baseStructureName, baseLocation);
      if (success) {
         this.plugin.getLogger().info("已放置底座结构: " + baseStructureName);
      } else {
         this.plugin.getLogger().severe("放置底座结构失败: " + baseStructureName);
      }
      return success;
   }

   /**
    * 放置笼子
    * @return 是否成功放置
    */
   private boolean placeCage(World world, Location center, MapTemplate template, MapTemplate.PillarConfig pillar, Material cageMaterial) {
      int cageX = center.getBlockX() + pillar.x - 1;
      int cageZ = center.getBlockZ() + pillar.z - 1;
      int pillarHeight = template.getPillarHeight();
      int cageY = center.getBlockY() + pillarHeight + 1;  // 柱子高度 + 1 = 笼子位置
      Location cageLocation = new Location(world, (double)cageX, (double)cageY, (double)cageZ);

      this.plugin.getLogger().fine("放置笼子: 柱子(" + pillar.x + "," + pillar.z + ") -> 笼子(" + cageX + "," + cageZ + "), 高度: " + cageY);

      // 检查结构是否存在
      if (!this.structureTemplate.hasStructure("cage")) {
         this.plugin.getLogger().severe("找不到笼子结构文件: cage");
         this.plugin.getLogger().severe("请确保结构文件存在于 plugins/NewPillar/structures/cage.nbt");
         return false;
      }

      boolean success = this.structureTemplate.placeStructure("cage", cageLocation);
      if (!success) {
         this.plugin.getLogger().severe("放置笼子结构失败: cage");
      }
      return success;
   }

   private void generatePillar(World world, Location center, MapTemplate template, MapTemplate.PillarConfig pillar, Material material) {
      Location base = pillar.getPillarBaseLocation(center);
      int x = base.getBlockX();
      int z = base.getBlockZ();
      int baseY = center.getBlockY();
      int pillarHeight = template.getPillarHeight();

      for (int y = 1; y <= pillarHeight; y++) {
         int finalY = baseY + y;
         Location blockLoc = new Location(world, (double)x, (double)finalY, (double)z);
         Bukkit.getRegionScheduler().execute(this.plugin, blockLoc, () -> {
            Block block = world.getBlockAt(x, finalY, z);
            block.setType(material);
         });
      }
   }

   public void openCages(MapTemplate template, Location center) {
      World world = center.getWorld();
      if (world == null) {
         return;
      }

      this.plugin.getLogger().info("打开所有笼子...");
      int pillarHeight = template.getPillarHeight();

      for (MapTemplate.PillarConfig pillar : template.getPillars()) {
         int cageX = center.getBlockX() + pillar.x - 1;
         int cageZ = center.getBlockZ() + pillar.z - 1;
         int cageY = center.getBlockY() + pillarHeight + 1;  // 柱子高度 + 1 = 笼子位置
         Location cageLocation = new Location(world, (double)cageX, (double)cageY, (double)cageZ);

         // 尝试放置空笼子结构
         if (this.structureTemplate.hasStructure("cage_empty")) {
            boolean success = this.structureTemplate.placeStructure("cage_empty", cageLocation);
            if (!success) {
               this.plugin.getLogger().warning("放置空笼子结构失败，使用代码清除");
               this.clearCage(world, cageLocation);
            }
         } else {
            // 如果没有 cage_empty 结构，直接清除
            this.clearCage(world, cageLocation);
         }
      }

      this.plugin.getLogger().info("所有笼子已打开！");
   }

   private void clearCage(World world, Location cageLocation) {
      int centerX = cageLocation.getBlockX();
      int centerY = cageLocation.getBlockY();
      int centerZ = cageLocation.getBlockZ();

      for (int x = 0; x <= 2; x++) {
         for (int y = 0; y <= 2; y++) {
            for (int z = 0; z <= 2; z++) {
               int finalX = centerX + x;
               int finalY = centerY + y;
               int finalZ = centerZ + z;
               Location blockLoc = new Location(world, (double)finalX, (double)finalY, (double)finalZ);
               Bukkit.getRegionScheduler().execute(this.plugin, blockLoc, () -> {
                  Block block = world.getBlockAt(finalX, finalY, finalZ);
                  block.setType(Material.AIR);
               });
            }
         }
      }
   }

   public void clearMap(Location center, int radius) {
      World world = center.getWorld();
      if (world == null) {
         return;
      }

      int centerX = center.getBlockX();
      int centerY = center.getBlockY();
      int centerZ = center.getBlockZ();
      this.plugin.getLogger().info("清除地图区域...");

      for (int y = -70; y <= 110; y += 10) {
         int finalY = y;
         Bukkit.getGlobalRegionScheduler().execute(this.plugin, () -> {
            for (int x = -radius; x <= radius; x++) {
               for (int z = -radius; z <= radius; z++) {
                  int finalX = centerX + x;
                  int finalZ = centerZ + z;
                  Location blockLoc = new Location(world, (double)finalX, (double)(centerY + finalY), (double)finalZ);
                  Bukkit.getRegionScheduler().execute(this.plugin, blockLoc, () -> {
                     Block block = world.getBlockAt(finalX, centerY + finalY, finalZ);
                     if (block.getType() != Material.AIR) {
                        block.setType(Material.AIR);
                     }
                  });
               }
            }
         });
      }

      this.plugin.getLogger().info("地图区域清除完成！");
   }
}
