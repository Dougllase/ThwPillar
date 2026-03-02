package com.newpillar.game;

import com.newpillar.NewPillar;
import java.util.HashMap;
import java.util.Map;

public class MapTemplateManager {
   private final NewPillar plugin;
   private final Map<String, MapTemplate> templateCache;

   public MapTemplateManager(NewPillar plugin) {
      this.plugin = plugin;
      this.templateCache = new HashMap<>();
   }

   public MapTemplate getTemplate(MapType mapType, int playerCount) {
      String key = mapType.name() + "_" + playerCount;
      if (this.templateCache.containsKey(key)) {
         return this.templateCache.get(key);
      } else {
         MapTemplate template = new MapTemplate(mapType, playerCount);
         this.templateCache.put(key, template);
         return template;
      }
   }

   public void clearCache() {
      this.templateCache.clear();
   }

   public int[] getSupportedPlayerCounts() {
      return new int[]{2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};
   }

   public boolean isPlayerCountSupported(int count) {
      return count >= 2 && count <= 12;
   }

   public int getClosestSupportedCount(int actualCount) {
      if (actualCount < 2) {
         return 2;
      } else {
         return actualCount > 12 ? 12 : actualCount;
      }
   }
}
