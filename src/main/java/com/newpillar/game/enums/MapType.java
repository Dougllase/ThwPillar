package com.newpillar.game.enums;

import org.bukkit.Material;
import org.bukkit.World.Environment;

public enum MapType {
   WOOL(101, "羊毛", "经典地图，白色羊毛柱子", Material.WHITE_WOOL, Material.WHITE_WOOL, Environment.NORMAL),
   NETHER(102, "地狱", "下界特色地图，§7§m中心的岩浆真的会有人去跳吗？§r", Material.NETHERRACK, Material.OBSIDIAN, Environment.NETHER),
   GLASS(103, "玻璃", "是玻璃栈道吗？", Material.GLASS, Material.WHITE_STAINED_GLASS, Environment.NORMAL),
   VOID(104, "虚空", "没有底座，掉落即出局", Material.BARRIER, Material.BARRIER, Environment.NORMAL),
   TNT(105, "TNT", "小心爆炸！", Material.TNT, Material.TNT, Environment.NORMAL),
   TRAP_DOOR(106, "活板门", "非常意味不明的地图", Material.OAK_TRAPDOOR, Material.OAK_PLANKS, Environment.NORMAL),
   SEA(201, "海洋", "海洋地图，水有毒，钓鱼获取物品", Material.PRISMARINE, Material.SEA_LANTERN, Environment.NORMAL),
   MOON(202, "月球", "月球地图，重力较低，并且底下似乎有些什么...", Material.WHITE_CONCRETE, Material.LIGHT_GRAY_CONCRETE, Environment.NORMAL);

   private final int id;
   private final String displayName;
   private final String description;
   private final Material pillarMaterial;
   private final Material floorMaterial;
   private final Environment environment;

   private MapType(int id, String displayName, String description, Material pillarMaterial, Material floorMaterial, Environment environment) {
      this.id = id;
      this.displayName = displayName;
      this.description = description;
      this.pillarMaterial = pillarMaterial;
      this.floorMaterial = floorMaterial;
      this.environment = environment;
   }

   public int getId() {
      return this.id;
   }

   public String getDisplayName() {
      return this.displayName;
   }

   public String getDescription() {
      return this.description;
   }

   public Material getPillarMaterial() {
      return this.pillarMaterial;
   }

   public Material getFloorMaterial() {
      return this.floorMaterial;
   }

   public Environment getEnvironment() {
      return this.environment;
   }

   public static MapType getById(int id) {
      for (MapType type : values()) {
         if (type.id == id) {
            return type;
         }
      }

      return WOOL;
   }

   public static MapType getByName(String name) {
      for (MapType type : values()) {
         if (type.name().equalsIgnoreCase(name) || type.displayName.equalsIgnoreCase(name)) {
            return type;
         }
      }

      return WOOL;
   }
}
