package com.newpillar.utils;

import com.newpillar.NewPillar;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

public class StructureTemplate {
   private final NewPillar plugin;
   private final Map<String, List<StructureParser.StructureBlock>> structureCache;
   private final File structuresFolder;

   public StructureTemplate(NewPillar plugin) {
      this.plugin = plugin;
      this.structureCache = new HashMap<>();
      this.structuresFolder = new File(plugin.getDataFolder(), "structures");
      if (!this.structuresFolder.exists()) {
         this.structuresFolder.mkdirs();
      }
      
      // 从插件资源中释放默认结构文件
      this.extractDefaultStructures();
   }

   /**
    * 从插件资源中提取默认结构文件到外部目录
    */
   private void extractDefaultStructures() {
      String[] defaultStructures = {
         "cage",
         "cage_empty",
         "rvb_cage_empty",
         "pillar/wool/2",
         "pillar/wool/9",
         "pillar/wool/red",
         "pillar/wool/blue",
         "pillar/nether/2",
         "pillar/nether/9",
         "pillar/nether/red",
         "pillar/nether/blue",
         "pillar/glass/2",
         "pillar/glass/9",
         "pillar/glass/red",
         "pillar/glass/blue",
         "pillar/void/2",
         "pillar/void/9",
         "pillar/void/red",
         "pillar/void/blue",
         "pillar/tnt/2",
         "pillar/tnt/9",
         "pillar/tnt/red",
         "pillar/tnt/blue",
         "pillar/trap_door/2",
         "pillar/trap_door/9",
         "pillar/trap_door/red",
         "pillar/trap_door/blue",
         "pillar/sea/2",
         "pillar/sea/5",
         "pillar/sea/red",
         "pillar/sea/blue",
         "pillar/moon/2",
         "pillar/moon/9",
         "pillar/moon/red",
         "pillar/moon/blue"
      };

      for (String structureName : defaultStructures) {
         this.extractStructure(structureName);
      }
   }

   /**
    * 从插件资源中提取单个结构文件
    */
   private void extractStructure(String name) {
      try {
         String resourcePath = "structures/" + name + ".nbt";
         InputStream is = this.plugin.getResource(resourcePath);
         if (is == null) {
            return;
         }

         // 创建子目录
         File targetFile = new File(this.structuresFolder, name + ".nbt");
         File parentDir = targetFile.getParentFile();
         if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
         }

         // 如果文件已存在，不覆盖（允许用户自定义）
         if (!targetFile.exists()) {
            Files.copy(is, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            this.plugin.getLogger().info("已释放默认结构文件: " + name);
         }
         is.close();
      } catch (Exception e) {
         this.plugin.getLogger().warning("释放结构文件失败: " + name + " - " + e.getMessage());
      }
   }

   /**
    * 加载结构文件
    * 加载顺序：1. 外部目录 2. 插件资源
    */
   public boolean loadStructure(String name) {
      if (this.structureCache.containsKey(name)) {
         return true;
      }

      try {
         File nbtFile = null;

         // 1. 首先尝试从外部 structures 目录加载（允许用户自定义）
         File externalFile = new File(this.structuresFolder, name + ".nbt");
         if (externalFile.exists()) {
            nbtFile = externalFile;
            this.plugin.getLogger().fine("从外部目录加载结构: " + name);
         }

         // 2. 如果外部不存在，尝试从插件资源加载
         if (nbtFile == null) {
            String resourcePath = "structures/" + name + ".nbt";
            InputStream is = this.plugin.getResource(resourcePath);
            if (is != null) {
               // 创建临时文件
               nbtFile = File.createTempFile("structure_", ".nbt");
               nbtFile.deleteOnExit();
               Files.copy(is, nbtFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
               is.close();
               this.plugin.getLogger().fine("从插件资源加载结构: " + name);
            }
         }

         // 3. 如果都找不到，返回失败
         if (nbtFile == null || !nbtFile.exists()) {
            this.plugin.getLogger().warning("找不到结构文件: " + name);
            return false;
         }

         // 解析结构文件
         List<StructureParser.StructureBlock> blocks = StructureParser.parseStructureFile(nbtFile);
         if (blocks.isEmpty()) {
            this.plugin.getLogger().warning("结构文件为空或解析失败: " + name);
            return false;
         }

         this.structureCache.put(name, blocks);
         this.plugin.getLogger().info("已加载结构: " + name + " (" + blocks.size() + " 个方块)");
         return true;

      } catch (Exception e) {
         this.plugin.getLogger().warning("加载结构失败: " + name + " - " + e.getMessage());
         e.printStackTrace();
         return false;
      }
   }

   /**
    * 检查结构是否存在（可用于预览可用结构）
    */
   public boolean hasStructure(String name) {
      // 检查缓存
      if (this.structureCache.containsKey(name)) {
         return true;
      }
      
      // 检查外部文件
      File externalFile = new File(this.structuresFolder, name + ".nbt");
      if (externalFile.exists()) {
         return true;
      }
      
      // 检查插件资源
      String resourcePath = "structures/" + name + ".nbt";
      return this.plugin.getResource(resourcePath) != null;
   }

   /**
    * 获取所有可用的结构名称列表
    */
   public List<String> getAvailableStructures() {
      List<String> structures = new ArrayList<>();
      
      // 从外部目录获取
      if (this.structuresFolder.exists()) {
         this.collectStructureNames(this.structuresFolder, "", structures);
      }
      
      return structures;
   }

   private void collectStructureNames(File dir, String prefix, List<String> result) {
      File[] files = dir.listFiles();
      if (files == null) return;
      
      for (File file : files) {
         if (file.isDirectory()) {
            String newPrefix = prefix.isEmpty() ? file.getName() : prefix + "/" + file.getName();
            this.collectStructureNames(file, newPrefix, result);
         } else if (file.getName().endsWith(".nbt")) {
            String name = file.getName().replace(".nbt", "");
            String fullName = prefix.isEmpty() ? name : prefix + "/" + name;
            if (!result.contains(fullName)) {
               result.add(fullName);
            }
         }
      }
   }

   public void clearCache() {
      this.structureCache.clear();
      this.plugin.getLogger().info("结构缓存已清除");
   }

   public boolean placeStructure(String name, Location location) {
      return this.placeStructure(name, location, null);
   }

   /**
    * 放置结构（带完成回调）
    * @param name 结构名称
    * @param location 放置位置
    * @param onComplete 完成回调（可为null）
    * @return 是否成功开始放置
    */
   public boolean placeStructure(String name, Location location, Runnable onComplete) {
      if (!this.loadStructure(name)) {
         return false;
      }
      
      List<StructureParser.StructureBlock> blocks = this.structureCache.get(name);
      if (blocks == null || blocks.isEmpty()) {
         return false;
      }

      World world = location.getWorld();
      if (world == null) {
         return false;
      }

      // 使用同步方式放置方块，确保地图生成完成后再继续
      for (StructureParser.StructureBlock block : blocks) {
         int x = location.getBlockX() + block.x;
         int y = location.getBlockY() + block.y;
         int z = location.getBlockZ() + block.z;
         Block b = world.getBlockAt(x, y, z);
         b.setType(block.material);
      }

      // 如果有回调，延迟执行以确保方块放置完成
      if (onComplete != null) {
         Bukkit.getRegionScheduler().runDelayed(this.plugin, location, scheduledTask -> {
            onComplete.run();
         }, 2L);
      }

      return true;
   }

   public boolean placeStructure(String name, Location location, int offsetX, int offsetY, int offsetZ) {
      Location offsetLoc = location.clone().add((double)offsetX, (double)offsetY, (double)offsetZ);
      return this.placeStructure(name, offsetLoc);
   }

   public int[] getStructureSize(String name) {
      if (!this.loadStructure(name)) {
         return null;
      }
      
      List<StructureParser.StructureBlock> blocks = this.structureCache.get(name);
      if (blocks == null || blocks.isEmpty()) {
         return null;
      }

      int minX = Integer.MAX_VALUE;
      int minY = Integer.MAX_VALUE;
      int minZ = Integer.MAX_VALUE;
      int maxX = Integer.MIN_VALUE;
      int maxY = Integer.MIN_VALUE;
      int maxZ = Integer.MIN_VALUE;

      for (StructureParser.StructureBlock block : blocks) {
         minX = Math.min(minX, block.x);
         minY = Math.min(minY, block.y);
         minZ = Math.min(minZ, block.z);
         maxX = Math.max(maxX, block.x);
         maxY = Math.max(maxY, block.y);
         maxZ = Math.max(maxZ, block.z);
      }

      return new int[]{maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1};
   }

   public List<String> getCachedStructures() {
      return new ArrayList<>(this.structureCache.keySet());
   }

   public File getStructuresFolder() {
      return this.structuresFolder;
   }
}
