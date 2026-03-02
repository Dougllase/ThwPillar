package com.newpillar.utils;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

public class StructureParser {
   public static List<StructureParser.StructureBlock> parseStructureFile(File file) {
      List<StructureParser.StructureBlock> blocks = new ArrayList<>();

      try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(file))))) {
         byte tagType = dis.readByte();
         if (tagType != 10) {
            throw new IOException("Invalid NBT root tag type: " + tagType);
         }

         String rootName = readString(dis);
         Map<String, Object> root = readCompound(dis);
         int[] size = parseIntArray(root.get("size"));
         if (size == null || size.length != 3) {
            throw new IOException("Invalid or missing size");
         }

         int sizeX = size[0];
         int sizeY = size[1];
         int sizeZ = size[2];
         System.out.println("[StructureParser] Structure size: [" + sizeX + ", " + sizeY + ", " + sizeZ + "]");
         List<Map<String, Object>> palette = (List<Map<String, Object>>)root.get("palette");
         if (palette == null) {
            throw new IOException("Missing palette");
         }

         List<Map<String, Object>> blockList = (List<Map<String, Object>>)root.get("blocks");
         if (blockList == null) {
            throw new IOException("Missing blocks");
         }

         for (Map<String, Object> blockData : blockList) {
            int[] pos = parseIntArray(blockData.get("pos"));
            if (pos != null && pos.length == 3) {
               int state = ((Number)blockData.get("state")).intValue();
               Map<String, Object> stateData = palette.get(state);
               String blockName = (String)stateData.get("Name");
               Material material = parseMaterial(blockName);
               if (material != null) {
                  BlockData blockDataObj = null;

                  try {
                     blockDataObj = material.createBlockData();
                  } catch (Exception var22) {
                  }

                  blocks.add(new StructureParser.StructureBlock(pos[0], pos[1], pos[2], material, blockDataObj));
               }
            }
         }

         System.out.println("[StructureParser] Parsed " + blocks.size() + " blocks");
      } catch (Exception var24) {
         System.err.println("[StructureParser] Error parsing structure file: " + var24.getMessage());
         var24.printStackTrace();
      }

      return blocks;
   }

   private static int[] parseIntArray(Object obj) {
      if (obj instanceof int[]) {
         return (int[])obj;
      } else if (!(obj instanceof List<?> list)) {
         return null;
      } else {
         int[] result = new int[list.size()];

         for (int i = 0; i < list.size(); i++) {
            result[i] = ((Number)list.get(i)).intValue();
         }

         return result;
      }
   }

   private static Material parseMaterial(String blockName) {
      if (blockName == null) {
         return null;
      } else {
         String name = blockName.replace("minecraft:", "").toUpperCase();
         Map<String, String> nameMapping = new HashMap<>();
         nameMapping.put("GRASS_BLOCK", "GRASS_BLOCK");
         nameMapping.put("DIRT", "DIRT");
         nameMapping.put("STONE", "STONE");
         nameMapping.put("COBBLESTONE", "COBBLESTONE");
         nameMapping.put("PLANKS", "OAK_PLANKS");
         nameMapping.put("LOG", "OAK_LOG");
         nameMapping.put("LEAVES", "OAK_LEAVES");
         nameMapping.put("GLASS", "GLASS");
         nameMapping.put("WOOL", "WHITE_WOOL");
         nameMapping.put("PRISMARINE", "PRISMARINE");
         nameMapping.put("PRISMARINE_BRICKS", "PRISMARINE_BRICKS");
         nameMapping.put("SEA_LANTERN", "SEA_LANTERN");
         nameMapping.put("NETHERRACK", "NETHERRACK");
         nameMapping.put("OBSIDIAN", "OBSIDIAN");
         nameMapping.put("TNT", "TNT");
         nameMapping.put("SAND", "SAND");
         nameMapping.put("WHITE_CONCRETE", "WHITE_CONCRETE");
         nameMapping.put("LIGHT_GRAY_CONCRETE", "LIGHT_GRAY_CONCRETE");
         nameMapping.put("BARRIER", "BARRIER");
         nameMapping.put("OAK_TRAPDOOR", "OAK_TRAPDOOR");
         nameMapping.put("OAK_PLANKS", "OAK_PLANKS");
         nameMapping.put("NETHER_BRICK_FENCE", "NETHER_BRICK_FENCE");

         try {
            return Material.valueOf(name);
         } catch (IllegalArgumentException var7) {
            String mappedName = nameMapping.get(name);
            if (mappedName != null) {
               try {
                  return Material.valueOf(mappedName);
               } catch (IllegalArgumentException var6) {
               }
            }

            return null;
         }
      }
   }

   private static String readString(DataInputStream dis) throws IOException {
      short length = dis.readShort();
      if (length < 0) {
         throw new IOException("Invalid string length: " + length);
      } else {
         byte[] bytes = new byte[length];
         dis.readFully(bytes);
         return new String(bytes, "UTF-8");
      }
   }

   private static Map<String, Object> readCompound(DataInputStream dis) throws IOException {
      Map<String, Object> map = new HashMap<>();

      while (true) {
         byte type = dis.readByte();
         if (type == 0) {
            return map;
         }

         String name = readString(dis);
         Object value = readTag(dis, type);
         map.put(name, value);
      }
   }

   private static Object readTag(DataInputStream dis, byte type) throws IOException {
      switch (type) {
         case 1:
            return dis.readByte();
         case 2:
            return dis.readShort();
         case 3:
            return dis.readInt();
         case 4:
            return dis.readLong();
         case 5:
            return dis.readFloat();
         case 6:
            return dis.readDouble();
         case 7:
            int byteLen = dis.readInt();
            byte[] bytes = new byte[byteLen];
            dis.readFully(bytes);
            return bytes;
         case 8:
            return readString(dis);
         case 9:
            return readList(dis);
         case 10:
            return readCompound(dis);
         case 11:
            int intLen = dis.readInt();
            int[] ints = new int[intLen];

            for (int i = 0; i < intLen; i++) {
               ints[i] = dis.readInt();
            }

            return ints;
         case 12:
            int longLen = dis.readInt();
            long[] longs = new long[longLen];

            for (int i = 0; i < longLen; i++) {
               longs[i] = dis.readLong();
            }

            return longs;
         default:
            throw new IOException("Unknown tag type: " + type);
      }
   }

   private static List<Object> readList(DataInputStream dis) throws IOException {
      List<Object> list = new ArrayList<>();
      byte elementType = dis.readByte();
      int length = dis.readInt();

      for (int i = 0; i < length; i++) {
         list.add(readTag(dis, elementType));
      }

      return list;
   }

   public static class StructureBlock {
      public final int x;
      public final int y;
      public final int z;
      public final Material material;
      public final BlockData blockData;

      public StructureBlock(int x, int y, int z, Material material, BlockData blockData) {
         this.x = x;
         this.y = y;
         this.z = z;
         this.material = material;
         this.blockData = blockData;
      }
   }
}
