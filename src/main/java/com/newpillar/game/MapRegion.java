package com.newpillar.game;

import com.newpillar.NewPillar;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;

public class MapRegion {
   private final NewPillar plugin;
   private final GameManager gameManager;
   private int centerX;
   private int centerY;
   private int centerZ;
   private int radius;
   private int minY;
   private int maxY;
   private final Set<UUID> virtualPlayers = ConcurrentHashMap.newKeySet();
   private final Map<UUID, ArmorStand> virtualPlayerStands = new ConcurrentHashMap<>();

   public MapRegion(NewPillar plugin, GameManager gameManager) {
      this.plugin = plugin;
      this.gameManager = gameManager;
      this.loadConfig();
   }

   private void loadConfig() {
      this.centerX = this.plugin.getConfig().getInt("map_region.center_x", 100);
      this.centerY = this.plugin.getConfig().getInt("map_region.center_y", 3);
      this.centerZ = this.plugin.getConfig().getInt("map_region.center_z", 100);
      this.radius = this.plugin.getConfig().getInt("map_region.radius", 50);
      this.minY = this.plugin.getConfig().getInt("map_region.min_y", -64);
      this.maxY = this.plugin.getConfig().getInt("map_region.max_y", 320);
      
      // 将清理范围增大到长宽100格（半径50）
      if (this.radius != 50) {
         this.plugin.getLogger().info("[地图区域] 将半径从 " + this.radius + " 调整为50（长宽100格）");
         this.radius = 50;
      }
   }

   public Location getCenter() {
      World world = this.getWorld();
      return world != null ? new Location(world, (double)this.centerX, (double)this.centerY, (double)this.centerZ) : null;
   }

   public World getWorld() {
      return (World)Bukkit.getWorlds().get(0);
   }

   public boolean isInRegion(Location loc) {
      if (loc == null) {
         return false;
      } else {
         World world = this.getWorld();
         if (world != null && loc.getWorld().equals(world)) {
            double dx = loc.getX() - (double)this.centerX;
            double dz = loc.getZ() - (double)this.centerZ;
            return dx * dx + dz * dz <= (double)(this.radius * this.radius);
         } else {
            return false;
         }
      }
   }

   public void clearRegionBlocks() {
      World world = this.getWorld();
      if (world != null) {
         this.plugin.getLogger().info("========================================");
         this.plugin.getLogger().info("开始清除地图区域方块...");
         this.plugin.getLogger().info("中心点: (" + this.centerX + ", " + this.centerY + ", " + this.centerZ + ")");
         this.plugin.getLogger().info("半径: " + this.radius);
         this.plugin.getLogger().info("Y范围: " + this.minY + " ~ " + this.maxY);
         int startX = this.centerX - this.radius;
         int endX = this.centerX + this.radius;
         int startZ = this.centerZ - this.radius;
         int endZ = this.centerZ + this.radius;
         this.plugin.getLogger().info("清除范围: X(" + startX + " ~ " + endX + "), Z(" + startZ + " ~ " + endZ + ")");
         int chunkSize = 16;
         AtomicInteger totalChunks = new AtomicInteger(0);
         AtomicInteger completedChunks = new AtomicInteger(0);
         AtomicInteger totalBlocks = new AtomicInteger(0);

         for (int x = startX; x <= endX; x += chunkSize) {
            for (int z = startZ; z <= endZ; z += chunkSize) {
               totalChunks.incrementAndGet();
            }
         }

         int finalTotalChunks = totalChunks.get();

         for (int x = startX; x <= endX; x += chunkSize) {
            for (int z = startZ; z <= endZ; z += chunkSize) {
               int chunkX = x;
               int chunkZ = z;
               Bukkit.getRegionScheduler().execute(this.plugin, world, chunkX, chunkZ, () -> {
                  int blocksCleared = this.clearChunkArea(world, chunkX, chunkZ, chunkSize);
                  totalBlocks.addAndGet(blocksCleared);
                  int completed = completedChunks.incrementAndGet();
                  if (completed % 10 == 0 || completed == finalTotalChunks) {
                     this.plugin.getLogger().info("地图清除进度: " + completed + "/" + finalTotalChunks + " 区块, 已清除 " + totalBlocks.get() + " 个方块");
                  }
               });
            }
         }

         this.plugin.getLogger().info("地图区域清除任务已分发，共 " + finalTotalChunks + " 个区块");
      }
   }

   private int clearChunkArea(World world, int startX, int startZ, int chunkSize) {
      int endX = Math.min(startX + chunkSize - 1, this.centerX + this.radius);
      int endZ = Math.min(startZ + chunkSize - 1, this.centerZ + this.radius);
      int actualStartX = Math.max(startX, this.centerX - this.radius);
      int actualStartZ = Math.max(startZ, this.centerZ - this.radius);
      int chunkX = startX >> 4;
      int chunkZ = startZ >> 4;
      world.getChunkAt(chunkX, chunkZ);
      int blocksCleared = 0;
      Map<Material, Integer> blockCounts = new HashMap<>();

      for (int x = actualStartX; x <= endX; x++) {
         for (int z = actualStartZ; z <= endZ; z++) {
            double dx = (double)(x - this.centerX);
            double dz = (double)(z - this.centerZ);
            if (!(dx * dx + dz * dz > (double)(this.radius * this.radius))) {
               for (int y = this.minY; y <= this.maxY; y++) {
                  Block block = world.getBlockAt(x, y, z);
                  Material type = block.getType();
                  if (type != Material.AIR) {
                     block.setType(Material.AIR, false);
                     blocksCleared++;
                     blockCounts.merge(type, 1, Integer::sum);
                  }
               }
            }
         }
      }

      if (blocksCleared > 0) {
         StringBuilder sb = new StringBuilder();
         sb.append("区块(").append(chunkX).append(", ").append(chunkZ).append(") 清除了 ").append(blocksCleared).append(" 个方块: ");
         blockCounts.entrySet()
            .stream()
            .sorted(Entry.<Material, Integer>comparingByValue().reversed())
            .limit(5L)
            .forEach(e -> sb.append(e.getKey()).append("(").append(e.getValue()).append(") "));
         this.plugin.getLogger().info(sb.toString());
      }

      return blocksCleared;
   }

   public void createVirtualPlayers(int count, int totalPlayers) {
      this.clearVirtualPlayers();
      World world = this.getWorld();
      if (world != null) {
         this.plugin.getLogger().info("创建 " + count + " 个虚拟玩家 (总玩家数: " + totalPlayers + ")...");
         List<PillarManager.PillarPosition> pillars = this.gameManager.getPillarManager().getPillarLayout(totalPlayers);
         int realPlayerCount = this.gameManager.getReadyPlayers().size();

         for (int i = 0; i < count; i++) {
            int index = i;
            int pillarIndex = realPlayerCount + i;
            if (pillarIndex >= pillars.size()) {
               this.plugin.getLogger().warning("虚拟玩家 #" + (i + 1) + " 超出柱子数量范围！");
               break;
            }

            PillarManager.PillarPosition pillar = pillars.get(pillarIndex);
            Location cageLoc = new Location(
               world, (double)(this.centerX + pillar.x) + 0.5, (double)(this.centerY + 31), (double)(this.centerZ + pillar.z) + 0.5
            );
            Bukkit.getRegionScheduler().execute(this.plugin, cageLoc, () -> {
               ArmorStand stand = (ArmorStand)world.spawnEntity(cageLoc, EntityType.ARMOR_STAND);
               stand.setVisible(true);
               stand.setCustomNameVisible(true);
               stand.setCustomName("§e虚拟玩家 #" + (index + 1));
               stand.setGravity(false);
               stand.setCanMove(false);
               stand.setCanPickupItems(false);
               stand.setBasePlate(false);
               stand.setArms(true);
               stand.setRotation(pillar.yaw, 0.0F);
               UUID virtualId = UUID.randomUUID();
               this.virtualPlayers.add(virtualId);
               this.virtualPlayerStands.put(virtualId, stand);
               this.spawnTestMarkerParticles(cageLoc);
            });
         }

         this.plugin.getLogger().info("虚拟玩家创建完成！");
      }
   }

   private List<PillarManager.PillarPosition> generateTestPillars(int count) {
      List<PillarManager.PillarPosition> positions = new ArrayList<>();
      int radius = Math.max(10, count * 3);

      for (int i = 0; i < count; i++) {
         double angle = (Math.PI * 2) * (double)i / (double)count;
         int x = (int)((double)radius * Math.cos(angle));
         int z = (int)((double)radius * Math.sin(angle));
         float yaw = (float)Math.toDegrees(angle) + 90.0F;
         positions.add(new PillarManager.PillarPosition(x, z, yaw));
      }

      return positions;
   }

   public List<PillarManager.PillarPosition> getPillarPositions(int count) {
      return this.generateTestPillars(count);
   }

   private void spawnTestMarkerParticles(Location loc) {
      World world = loc.getWorld();
      if (world != null) {
         Bukkit.getRegionScheduler().runAtFixedRate(this.plugin, loc, task -> {
            if (!this.virtualPlayers.isEmpty()) {
               world.spawnParticle(Particle.HAPPY_VILLAGER, loc.clone().add(0.5, 1.0, 0.5), 5, 0.5, 0.5, 0.5, 0.0);
            } else {
               task.cancel();
            }
         }, 1L, 20L);
      }
   }

   public void clearVirtualPlayers() {
      this.plugin.getLogger().info("清除 " + this.virtualPlayers.size() + " 个虚拟玩家...");

      for (ArmorStand stand : this.virtualPlayerStands.values()) {
         if (stand != null && !stand.isDead()) {
            Bukkit.getRegionScheduler().execute(this.plugin, stand.getLocation(), () -> {
               if (!stand.isDead()) {
                  stand.remove();
               }
            });
         }
      }

      this.virtualPlayerStands.clear();
      this.virtualPlayers.clear();
      this.plugin.getLogger().info("虚拟玩家已清除！");
   }

   public int getVirtualPlayerCount() {
      return this.virtualPlayers.size();
   }

   public Set<UUID> getVirtualPlayers() {
      return new HashSet<>(this.virtualPlayers);
   }
}
