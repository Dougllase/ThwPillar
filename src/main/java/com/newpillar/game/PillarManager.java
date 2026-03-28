package com.newpillar.game;

import com.newpillar.game.enums.MapType;

import com.newpillar.NewPillar;
import com.newpillar.utils.SchedulerUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public class PillarManager {
   private final NewPillar plugin;
   private final GameManager gameManager;
   private final Map<Integer, List<PillarManager.PillarPosition>> pillarLayouts = new HashMap<>();
   private List<PillarManager.PillarPosition> currentPillars = new ArrayList<>();
   private MapType currentMapType = MapType.WOOL;

   public PillarManager(NewPillar plugin, GameManager gameManager) {
      this.plugin = plugin;
      this.gameManager = gameManager;
      this.initPillarLayouts();
   }

   public void setMapType(MapType mapType) {
      this.currentMapType = mapType;
      this.plugin.getLogger().info("地图类型已设置为: " + mapType.getDisplayName());
   }

   public MapType getMapType() {
      return this.currentMapType;
   }

   private void initPillarLayouts() {
      this.pillarLayouts
         .put(2, new ArrayList<>(Arrays.asList(new PillarManager.PillarPosition(5, 5, 90.0F), new PillarManager.PillarPosition(-5, -5, -90.0F))));
      this.pillarLayouts
         .put(
            3,
            new ArrayList<>(
               Arrays.asList(
                  new PillarManager.PillarPosition(5, 5, 90.0F),
                  new PillarManager.PillarPosition(-5, -5, -90.0F),
                  new PillarManager.PillarPosition(5, -5, 0.0F)
               )
            )
         );
      this.pillarLayouts
         .put(
            4,
            new ArrayList<>(
               Arrays.asList(
                  new PillarManager.PillarPosition(5, 5, 90.0F),
                  new PillarManager.PillarPosition(-5, -5, -90.0F),
                  new PillarManager.PillarPosition(5, -5, 0.0F),
                  new PillarManager.PillarPosition(-5, 5, 180.0F)
               )
            )
         );
      this.pillarLayouts
         .put(
            5,
            new ArrayList<>(
               Arrays.asList(
                  new PillarManager.PillarPosition(8, 8, 135.0F),
                  new PillarManager.PillarPosition(-8, -8, -45.0F),
                  new PillarManager.PillarPosition(8, -8, 45.0F),
                  new PillarManager.PillarPosition(-8, 8, -135.0F),
                  new PillarManager.PillarPosition(0, 0, 0.0F)
               )
            )
         );

      for (int i = 6; i <= 12; i++) {
         this.pillarLayouts.put(i, this.generateCircleLayout(i, 10));
      }
   }

   private List<PillarManager.PillarPosition> generateCircleLayout(int count, int radius) {
      List<PillarManager.PillarPosition> positions = new ArrayList<>();

      for (int i = 0; i < count; i++) {
         double angle = (Math.PI * 2) * (double)i / (double)count;
         int x = (int)((double)radius * Math.cos(angle));
         int z = (int)((double)radius * Math.sin(angle));
         float yaw = (float)Math.toDegrees(angle) + 90.0F;
         positions.add(new PillarManager.PillarPosition(x, z, yaw));
      }

      return positions;
   }

   private Location getCenterLocation() {
      return this.gameManager.getMapRegion().getCenter();
   }

   public void generatePillars(int playerCount) {
      World world = this.gameManager.getGameWorld();
      if (world != null) {
         Location center = this.getCenterLocation();
         if (center != null) {
            int layoutSize = Math.min(playerCount, 12);
            this.currentPillars = this.pillarLayouts.getOrDefault(layoutSize, this.pillarLayouts.get(2));
            this.generateFloor(world, center);

            for (int i = 0; i < this.currentPillars.size(); i++) {
               PillarManager.PillarPosition pos = this.currentPillars.get(i);
               int pillarX = center.getBlockX() + pos.x;
               int pillarZ = center.getBlockZ() + pos.z;
               int baseY = center.getBlockY();
               this.generatePillarColumn(world, pillarX, pillarZ, baseY);
               this.generateCage(world, pillarX, baseY + 40, pillarZ);
            }
         }
      }
   }

   private void generateFloor(World world, Location center) {
      int centerX = center.getBlockX();
      int centerY = center.getBlockY();
      int centerZ = center.getBlockZ();
      if (this.currentMapType != MapType.VOID) {
         Material floorMaterial = this.currentMapType.getFloorMaterial();

         // 地板生成范围：长宽100格（半径50）
         for (int x = -50; x <= 50; x++) {
            for (int z = -50; z <= 50; z++) {
               int finalX = centerX + x;
               int finalZ = centerZ + z;
               Location blockLoc = new Location(world, (double)finalX, (double)centerY, (double)finalZ);
               SchedulerUtils.runOnLocation(blockLoc, () -> {
                  Block block = world.getBlockAt(finalX, centerY, finalZ);
                  block.setType(floorMaterial);
               });
            }
         }
      }
   }

   private void generatePillarColumn(World world, int x, int z, int baseY) {
      Material pillarMaterial = this.currentMapType.getPillarMaterial();

      for (int y = 1; y <= 39; y++) {
         int finalY = baseY + y;
         Location blockLoc = new Location(world, (double)x, (double)finalY, (double)z);
         SchedulerUtils.runOnLocation(blockLoc, () -> {
            Block block = world.getBlockAt(x, finalY, z);
            block.setType(pillarMaterial);
         });
      }
   }

   private void generateCage(World world, int centerX, int centerY, int centerZ) {
      Material cageMaterial = this.getCageMaterial();

      for (int x = -1; x <= 1; x++) {
         for (int y = 0; y <= 2; y++) {
            for (int z = -1; z <= 1; z++) {
               if (x != 0 || y < 1 || y > 2 || z != 0) {
                  int finalX = centerX + x;
                  int finalY = centerY + y;
                  int finalZ = centerZ + z;
                  Location blockLoc = new Location(world, (double)finalX, (double)finalY, (double)finalZ);
                  SchedulerUtils.runOnLocation(blockLoc, () -> {
                     Block block = world.getBlockAt(finalX, finalY, finalZ);
                     block.setType(cageMaterial);
                  });
               }
            }
         }
      }
   }

   private Material getCageMaterial() {
      // 所有地图的笼子材质都使用玻璃
      return Material.GLASS;
   }

   public void teleportPlayers() {
      Set<UUID> readyPlayers = this.gameManager.getReadyPlayers();
      Location center = this.getCenterLocation();
      if (center != null) {
         // 复制柱子列表并随机打乱，实现随机出生位置
         List<PillarManager.PillarPosition> shuffledPillars = new ArrayList<>(this.currentPillars);
         Collections.shuffle(shuffledPillars);
         
         int index = 0;
         for (UUID uuid : readyPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
               if (index >= shuffledPillars.size()) {
                  break;
               }

               PillarManager.PillarPosition pos = shuffledPillars.get(index);
               PlayerData data = this.gameManager.getPlayerData(uuid);
               if (data != null) {
                  Location tpLoc = new Location(
                     this.gameManager.getGameWorld(),
                     (double)(center.getBlockX() + pos.x) + 0.5,
                     (double)(center.getBlockY() + 41),
                     (double)(center.getBlockZ() + pos.z) + 0.5,
                     pos.yaw,
                     0.0F
                  );
                  player.teleportAsync(tpLoc);
                  index++;
               }
            }
         }
      }
   }

   public void clearAllCages() {
      World world = this.gameManager.getGameWorld();
      if (world != null) {
         Location center = this.getCenterLocation();
         if (center != null) {
            int centerX = center.getBlockX();
            int centerY = center.getBlockY();
            int centerZ = center.getBlockZ();

            for (PillarManager.PillarPosition pos : this.currentPillars) {
               for (int x = -1; x <= 1; x++) {
                  for (int y = 0; y <= 2; y++) {
                     for (int z = -1; z <= 1; z++) {
                        int finalX = centerX + pos.x + x;
                        int finalY = centerY + 40 + y;
                        int finalZ = centerZ + pos.z + z;
                        Location blockLoc = new Location(world, (double)finalX, (double)finalY, (double)finalZ);
                        SchedulerUtils.runOnLocation(blockLoc, () -> {
                           Block block = world.getBlockAt(finalX, finalY, finalZ);
                           if (block.getType() == Material.GLASS || block.getType() == Material.IRON_BARS) {
                              block.setType(Material.AIR);
                           }
                        });
                     }
                  }
               }
            }
         }
      }
   }

   public void reset() {
      this.currentPillars.clear();
   }

   public List<PillarManager.PillarPosition> getCurrentPillars() {
      return new ArrayList<>(this.currentPillars);
   }

   public List<PillarManager.PillarPosition> getPillarLayout(int count) {
      int layoutSize = Math.min(count, 12);
      return this.pillarLayouts.getOrDefault(layoutSize, this.pillarLayouts.get(2));
   }

   public static class PillarPosition {
      public final int x;
      public final int z;
      public final float yaw;

      public PillarPosition(int x, int z, float yaw) {
         this.x = x;
         this.z = z;
         this.yaw = yaw;
      }
   }
}
