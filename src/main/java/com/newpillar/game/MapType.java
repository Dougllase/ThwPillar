package com.newpillar.game;

import org.bukkit.Material;
import org.bukkit.World.Environment;

public enum MapType {
   WOOL(101, "羊毛", "经典地图，白色羊毛柱子", Material.WHITE_WOOL, Material.WHITE_WOOL, Environment.NORMAL),
   NETHER(102, "地狱", "下界维度地图", Material.NETHERRACK, Material.OBSIDIAN, Environment.NETHER),
   GLASS(103, "玻璃", "玻璃底座，基岩柱子", Material.GLASS, Material.WHITE_STAINED_GLASS, Environment.NORMAL),
   VOID(104, "虚空", "虚空地图，无地板", Material.BARRIER, Material.BARRIER, Environment.NORMAL),
   TNT(105, "TNT", "TNT底座，基岩柱子", Material.TNT, Material.TNT, Environment.NORMAL),
   TRAP_DOOR(106, "活板门", "活板门平台", Material.OAK_TRAPDOOR, Material.OAK_PLANKS, Environment.NORMAL),
   SEA(201, "海洋", "海洋地图，水有毒，钓鱼获取物品", Material.PRISMARINE, Material.SEA_LANTERN, Environment.NORMAL),
   MOON(202, "月球", "月球地图，低重力环境", Material.WHITE_CONCRETE, Material.LIGHT_GRAY_CONCRETE, Environment.NORMAL);

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
