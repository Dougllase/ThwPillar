package com.newpillar.game.map;

import com.newpillar.game.enums.MapType;

import com.newpillar.game.GameManager;

import com.newpillar.NewPillar;
import com.newpillar.utils.SchedulerUtils;
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

      // 清除底座结构中超出柱子高度的部分（防止柱子在笼子上方延伸）
      this.clearExcessPillarBlocks(world, center, template);

      // 放置笼子（必须成功）
      for (MapTemplate.PillarConfig pillar : template.getPillars()) {
         boolean cageSuccess = this.placeCage(world, center, template, pillar, template.getCageMaterial());
         if (!cageSuccess) {
            this.plugin.getLogger().severe("地图生成失败：无法放置笼子结构");
            return;
         }
      }

      // 月球地图：在底座生成完成后寻找并填充战利品箱
      if (template.getMapType() == MapType.MOON) {
         this.findAndFillMoonLootChest(center, template);
      }

      this.plugin.getLogger().info("地图生成完成！");
   }

   /**
    * 寻找并填充月球地图战利品箱
    * 与数据包 moon.json 一致：鞘翅、烟花火箭、金苹果/苹果、末地城宝藏
    * 策略：在底座区域内寻找已有的箱子，然后填充战利品
    */
   private void findAndFillMoonLootChest(Location center, MapTemplate template) {
      World world = center.getWorld();
      if (world == null) return;

      // 计算底座区域（根据玩家数确定底座大小）
      int playerCount = template.getPlayerCount();
      int baseOffsetX, baseOffsetZ, baseSize;
      if (playerCount <= 2) {
         baseOffsetX = -17;
         baseOffsetZ = -17;
         baseSize = 35; // 2人底座大小约为35x35
      } else {
         baseOffsetX = -20;
         baseOffsetZ = -20;
         baseSize = 41; // 多人底座大小约为41x41
      }

      Location baseCorner = center.clone().add(baseOffsetX, 0, baseOffsetZ);
      int baseY = center.getBlockY();

      this.plugin.getLogger().info("[月球地图] 在底座区域寻找战利品箱，底座角落: " + baseCorner + ", 大小: " + baseSize);

      SchedulerUtils.runOnLocation(center, () -> {
         // 在底座区域内寻找箱子
         Location chestLocation = null;
         for (int x = 0; x < baseSize; x++) {
            for (int z = 0; z < baseSize; z++) {
               for (int y = 0; y < 10; y++) { // 在底座高度范围内搜索（0-10格高）
                  Location checkLoc = baseCorner.clone().add(x, y, z);
                  Block block = world.getBlockAt(checkLoc);
                  if (block.getType() == Material.CHEST) {
                     chestLocation = checkLoc;
                     this.plugin.getLogger().info("[月球地图] 找到战利品箱位置: " + chestLocation);
                     break;
                  }
               }
               if (chestLocation != null) break;
            }
            if (chestLocation != null) break;
         }

         if (chestLocation == null) {
            this.plugin.getLogger().warning("[月球地图] 未在底座中找到战利品箱，尝试在默认位置创建");
            // 如果找不到，在默认位置创建箱子（相对于底座中心）
            chestLocation = center.clone().add(-1, 5, -1);
            Block chestBlock = world.getBlockAt(chestLocation);
            chestBlock.setType(Material.CHEST);
         }

         // 填充战利品
         Block chestBlock = world.getBlockAt(chestLocation);
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

            this.plugin.getLogger().info("[月球地图] 战利品箱已填充在位置: " + chestLocation);
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
    * 放置笼子（代码生成）
    * 笼子结构：底部1个玻璃，侧边8个玻璃（2层高，四面各2个），顶部1个封顶
    * 玩家传送到 (pillar.x + 0.5, baseY + pillarHeight + 2, pillar.z + 0.5)
    * 所以笼子围绕取整后的玩家位置生成
    * @return 是否成功放置
    */
   private boolean placeCage(World world, Location center, MapTemplate template, MapTemplate.PillarConfig pillar, Material cageMaterial) {
      int pillarHeight = template.getPillarHeight();
      int baseY = center.getBlockY();

      // 玩家传送位置（使用与getTeleportLocation相同的计算方式）
      int playerX = center.getBlockX() + pillar.x;
      int playerZ = center.getBlockZ() + pillar.z;
      int playerY = baseY + pillarHeight + 2;  // 玩家站立位置

      this.plugin.getLogger().info("放置笼子: 柱子(" + pillar.x + "," + pillar.z + ") 玩家位置: (" + playerX + "," + playerY + "," + playerZ + ")");

      // 底部：1个玻璃（玩家脚下）
      world.getBlockAt(playerX, playerY - 1, playerZ).setType(cageMaterial);

      // 侧边：8个玻璃（2层高，四面各2个）
      // 第1层（playerY）
      world.getBlockAt(playerX - 1, playerY, playerZ).setType(cageMaterial);  // 西
      world.getBlockAt(playerX + 1, playerY, playerZ).setType(cageMaterial);  // 东
      world.getBlockAt(playerX, playerY, playerZ - 1).setType(cageMaterial);  // 北
      world.getBlockAt(playerX, playerY, playerZ + 1).setType(cageMaterial);  // 南
      // 第2层（playerY + 1）
      world.getBlockAt(playerX - 1, playerY + 1, playerZ).setType(cageMaterial);  // 西
      world.getBlockAt(playerX + 1, playerY + 1, playerZ).setType(cageMaterial);  // 东
      world.getBlockAt(playerX, playerY + 1, playerZ - 1).setType(cageMaterial);  // 北
      world.getBlockAt(playerX, playerY + 1, playerZ + 1).setType(cageMaterial);  // 南

      // 顶部：1个封顶（玩家头顶）
      world.getBlockAt(playerX, playerY + 2, playerZ).setType(cageMaterial);

      this.plugin.getLogger().info("笼子放置完成: 柱子(" + pillar.x + "," + pillar.z + ")，共10个玻璃");
      return true;
   }

   private void generatePillar(World world, Location center, MapTemplate template, MapTemplate.PillarConfig pillar, Material material) {
      Location base = pillar.getPillarBaseLocation(center);
      int x = base.getBlockX();
      int z = base.getBlockZ();
      int baseY = center.getBlockY();
      int pillarHeight = template.getPillarHeight();

      // 同步放置柱子方块，确保地图生成完成
      for (int y = 1; y <= pillarHeight; y++) {
         int finalY = baseY + y;
         Block block = world.getBlockAt(x, finalY, z);
         block.setType(material);
      }
   }

   public void openCages(MapTemplate template, Location center) {
      World world = center.getWorld();
      if (world == null) {
         return;
      }

      this.plugin.getLogger().info("打开所有笼子...");
      int pillarHeight = template.getPillarHeight();
      int baseY = center.getBlockY();

      for (MapTemplate.PillarConfig pillar : template.getPillars()) {
         // 玩家传送位置（笼子中心）
         int playerX = center.getBlockX() + pillar.x;
         int playerZ = center.getBlockZ() + pillar.z;
         int playerY = baseY + pillarHeight + 2;  // 玩家站立位置

         // 清除10个笼子方块
         // 底部：1个
         world.getBlockAt(playerX, playerY - 1, playerZ).setType(Material.AIR);
         // 侧边第1层：4个
         world.getBlockAt(playerX - 1, playerY, playerZ).setType(Material.AIR);  // 西
         world.getBlockAt(playerX + 1, playerY, playerZ).setType(Material.AIR);  // 东
         world.getBlockAt(playerX, playerY, playerZ - 1).setType(Material.AIR);  // 北
         world.getBlockAt(playerX, playerY, playerZ + 1).setType(Material.AIR);  // 南
         // 侧边第2层：4个
         world.getBlockAt(playerX - 1, playerY + 1, playerZ).setType(Material.AIR);  // 西
         world.getBlockAt(playerX + 1, playerY + 1, playerZ).setType(Material.AIR);  // 东
         world.getBlockAt(playerX, playerY + 1, playerZ - 1).setType(Material.AIR);  // 北
         world.getBlockAt(playerX, playerY + 1, playerZ + 1).setType(Material.AIR);  // 南
         // 顶部：1个
         world.getBlockAt(playerX, playerY + 2, playerZ).setType(Material.AIR);
      }

      this.plugin.getLogger().info("所有笼子已打开！");
   }

   /**
    * 清除笼子方块（在指定坐标为中心的3x3x3区域内）
    * @param world 世界
    * @param centerX 中心X坐标
    * @param centerY 中心Y坐标（底座高度）
    * @param centerZ 中心Z坐标
    */
   private void clearCageBlocks(World world, int centerX, int centerY, int centerZ) {
      // 同步清除笼子方块（3x3x3区域，从centerY开始向上）
      for (int x = 0; x <= 2; x++) {
         for (int y = 0; y <= 2; y++) {
            for (int z = 0; z <= 2; z++) {
               int finalX = centerX + x;
               int finalY = centerY + y;
               int finalZ = centerZ + z;
               Block block = world.getBlockAt(finalX, finalY, finalZ);
               block.setType(Material.AIR);
            }
         }
      }
   }

   /**
    * 清除底座结构中超出柱子高度的部分
    * 用于处理底座结构文件包含比预期更高柱子的情况（如海洋地图）
    * @param world 世界
    * @param center 地图中心
    * @param template 地图模板
    */
   private void clearExcessPillarBlocks(World world, Location center, MapTemplate template) {
      int pillarHeight = template.getPillarHeight();
      int baseY = center.getBlockY();
      int clearStartY = baseY + pillarHeight + 1;  // 从柱子顶部+1开始清除
      int clearEndY = baseY + 40;  // 清除到40格高度（底座结构的最大高度）

      this.plugin.getLogger().info("清除底座结构中超出柱子高度的部分: 从Y=" + clearStartY + " 到 Y=" + clearEndY);

      for (MapTemplate.PillarConfig pillar : template.getPillars()) {
         int pillarX = center.getBlockX() + pillar.x;
         int pillarZ = center.getBlockZ() + pillar.z;

         // 清除柱子位置上方可能存在的额外方块
         for (int y = clearStartY; y <= clearEndY; y++) {
            Block block = world.getBlockAt(pillarX, y, pillarZ);
            // 只清除基岩和黑曜石（柱子材料）
            if (block.getType() == Material.BEDROCK || block.getType() == Material.OBSIDIAN) {
               block.setType(Material.AIR);
            }
         }
      }

      this.plugin.getLogger().info("底座结构清理完成");
   }

   /**
    * 清除笼子（兼容旧方法，使用Location参数）
    */
   private void clearCage(World world, Location cageLocation) {
      this.clearCageBlocks(world, cageLocation.getBlockX(), cageLocation.getBlockY(), cageLocation.getBlockZ());
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
         SchedulerUtils.runGlobal(() -> {
            for (int x = -radius; x <= radius; x++) {
               for (int z = -radius; z <= radius; z++) {
                  int finalX = centerX + x;
                  int finalZ = centerZ + z;
                  Location blockLoc = new Location(world, (double)finalX, (double)(centerY + finalY), (double)finalZ);
                  SchedulerUtils.runOnLocation(blockLoc, () -> {
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
