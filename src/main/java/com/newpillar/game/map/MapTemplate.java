package com.newpillar.game.map;

import com.newpillar.game.enums.MapType;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;

public class MapTemplate {
   private final MapType mapType;
   private final int playerCount;
   private final List<MapTemplate.PillarConfig> pillars;
   private final Material floorMaterial;
   private final Material pillarMaterial;
   private final Material cageMaterial;

   public MapTemplate(MapType mapType, int playerCount) {
      this.mapType = mapType;
      this.playerCount = playerCount;
      this.pillars = new ArrayList<>();
      this.floorMaterial = mapType.getFloorMaterial();
      this.pillarMaterial = Material.BEDROCK;
      this.cageMaterial = this.getCageMaterialForMap(mapType);
      this.generatePillarConfigs();
   }

   private void generatePillarConfigs() {
      int[][] positions = this.getPositionsForPlayerCount(this.playerCount);
      float[] yaws = this.getYawsForPlayerCount(this.playerCount);

      for (int i = 0; i < this.playerCount && i < positions.length; i++) {
         this.pillars.add(new MapTemplate.PillarConfig(positions[i][0], positions[i][1], yaws[i], i + 1));
      }
   }

   private int[][] getPositionsForPlayerCount(int count) {
      return switch (count) {
         case 2 -> new int[][]{{0, 9}, {0, -9}};
         case 3 -> new int[][]{{0, 9}, {8, -5}, {-8, -5}};
         case 4 -> new int[][]{{7, 7}, {-7, 7}, {-7, -7}, {7, -7}};
         case 5 -> new int[][]{{0, 9}, {9, 0}, {0, -9}, {-9, 0}, {0, 0}};
         case 6 -> new int[][]{{0, 10}, {9, 5}, {9, -5}, {0, -10}, {-9, -5}, {-9, 5}};
         case 7 -> new int[][]{{0, 10}, {8, 6}, {10, -2}, {5, -9}, {-5, -9}, {-10, -2}, {-8, 6}};
         case 8 -> new int[][]{{0, 10}, {7, 7}, {10, 0}, {7, -7}, {0, -10}, {-7, -7}, {-10, 0}, {-7, 7}};
         case 9 -> new int[][]{{0, 10}, {7, 7}, {10, 0}, {7, -7}, {0, -10}, {-7, -7}, {-10, 0}, {-7, 7}, {0, 0}};
         case 10 -> new int[][]{{0, 10}, {6, 8}, {10, 3}, {9, -5}, {3, -10}, {-3, -10}, {-9, -5}, {-10, 3}, {-6, 8}, {0, 0}};
         case 11 -> new int[][]{{0, 10}, {5, 9}, {9, 5}, {10, 0}, {9, -5}, {5, -9}, {0, -10}, {-5, -9}, {-9, -5}, {-10, 0}, {-5, 9}};
         case 12 -> new int[][]{{0, 10}, {5, 9}, {9, 5}, {10, 0}, {9, -5}, {5, -9}, {0, -10}, {-5, -9}, {-9, -5}, {-10, 0}, {-5, 9}, {-9, 5}};
         default -> new int[][]{{0, 0}};
      };
   }

   private float[] getYawsForPlayerCount(int count) {
      float[] yaws = new float[count];

      for (int i = 0; i < count; i++) {
         double angle = (Math.PI * 2) * (double)i / (double)count;
         yaws[i] = (float)Math.toDegrees(angle) + 180.0F;
      }

      return yaws;
   }

   private Material getCageMaterialForMap(MapType type) {
      return switch (type) {
         case WOOL, GLASS, TNT, TRAP_DOOR, SEA, MOON -> Material.GLASS;
         case NETHER -> Material.NETHER_BRICK_FENCE;
         case VOID -> Material.BARRIER;
      };
   }

   public MapType getMapType() {
      return this.mapType;
   }

   public int getPlayerCount() {
      return this.playerCount;
   }

   public List<MapTemplate.PillarConfig> getPillars() {
      return this.pillars;
   }

   public Material getFloorMaterial() {
      return this.floorMaterial;
   }

   public Material getPillarMaterial() {
      return this.pillarMaterial;
   }

   public Material getCageMaterial() {
      return this.cageMaterial;
   }

   public static class PillarConfig {
      public final int x;
      public final int z;
      public final float yaw;
      public final int number;

      public PillarConfig(int x, int z, float yaw, int number) {
         this.x = x;
         this.z = z;
         this.yaw = yaw;
         this.number = number;
      }

      public Location getTeleportLocation(Location center) {
         // 柱子高度为39格 (baseY + 1 到 baseY + 39)
         // 笼子在 baseY + 40
         // 玩家传送到笼子顶部上方1格: baseY + 41
         return new Location(
            center.getWorld(),
            (double)(center.getBlockX() + this.x) + 0.5,
            (double)(center.getBlockY() + 41),
            (double)(center.getBlockZ() + this.z) + 0.5,
            this.yaw,
            0.0F
         );
      }

      public Location getPillarBaseLocation(Location center) {
         return new Location(center.getWorld(), (double)(center.getBlockX() + this.x), (double)center.getBlockY(), (double)(center.getBlockZ() + this.z));
      }

      public Location getCageLocation(Location center) {
         return new Location(center.getWorld(), (double)(center.getBlockX() + this.x), (double)(center.getBlockY() + 40), (double)(center.getBlockZ() + this.z));
      }
   }
}
