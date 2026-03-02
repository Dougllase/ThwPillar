package com.newpillar.game;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

public class StructureTemplate {
   private final String name;
   private final List<StructureTemplate.BlockInfo> blocks;
   private int sizeX;
   private int sizeY;
   private int sizeZ;

   public StructureTemplate(String name) {
      this.name = name;
      this.blocks = new ArrayList<>();
   }

   public void addBlock(int x, int y, int z, Material material) {
      this.blocks.add(new StructureTemplate.BlockInfo(x, y, z, material));
      this.sizeX = Math.max(this.sizeX, x + 1);
      this.sizeY = Math.max(this.sizeY, y + 1);
      this.sizeZ = Math.max(this.sizeZ, z + 1);
   }

   public void placeAt(Location origin) {
      for (StructureTemplate.BlockInfo block : this.blocks) {
         Location loc = origin.clone().add((double)block.x, (double)block.y, (double)block.z);
         Block targetBlock = loc.getBlock();
         targetBlock.setType(block.material);
      }
   }

   public int getSizeX() {
      return this.sizeX;
   }

   public int getSizeY() {
      return this.sizeY;
   }

   public int getSizeZ() {
      return this.sizeZ;
   }

   public String getName() {
      return this.name;
   }

   public List<StructureTemplate.BlockInfo> getBlocks() {
      return this.blocks;
   }

   public static class BlockInfo {
      public final int x;
      public final int y;
      public final int z;
      public final Material material;

      public BlockInfo(int x, int y, int z, Material material) {
         this.x = x;
         this.y = y;
         this.z = z;
         this.material = material;
      }
   }
}
