package com.newpillar.cache;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerCache {
    private final Map<UUID, Long> achievementCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> itemCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> killStreaks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> winStreaks = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> hasReceivedLastWin = new ConcurrentHashMap<>();
    
    public void setAchievementCooldown(UUID uuid, long cooldownEnd) {
        achievementCooldowns.put(uuid, cooldownEnd);
    }
    
    public boolean isAchievementOnCooldown(UUID uuid) {
        Long cooldownEnd = achievementCooldowns.get(uuid);
        return cooldownEnd != null && System.currentTimeMillis() < cooldownEnd;
    }
    
    public void clearAchievementCooldown(UUID uuid) {
        achievementCooldowns.remove(uuid);
    }
    
    public void setItemCooldown(UUID uuid, long cooldownEnd) {
        itemCooldowns.put(uuid, cooldownEnd);
    }
    
    public boolean isItemOnCooldown(UUID uuid) {
        Long cooldownEnd = itemCooldowns.get(uuid);
        return cooldownEnd != null && System.currentTimeMillis() < cooldownEnd;
    }
    
    public void clearItemCooldown(UUID uuid) {
        itemCooldowns.remove(uuid);
    }
    
    public void addKill(UUID uuid) {
        killStreaks.merge(uuid, 1, Integer::sum);
    }
    
    public int getKillStreak(UUID uuid) {
        return killStreaks.getOrDefault(uuid, 0);
    }
    
    public void resetKillStreak(UUID uuid) {
        killStreaks.remove(uuid);
    }
    
    public void addWin(UUID uuid) {
        winStreaks.merge(uuid, 1, Integer::sum);
    }
    
    public int getWinStreak(UUID uuid) {
        return winStreaks.getOrDefault(uuid, 0);
    }
    
    public void resetWinStreak(UUID uuid) {
        winStreaks.remove(uuid);
    }
    
    public void setReceivedLastWin(UUID uuid, boolean received) {
        hasReceivedLastWin.put(uuid, received);
    }
    
    public boolean hasReceivedLastWin(UUID uuid) {
        return hasReceivedLastWin.getOrDefault(uuid, false);
    }
}
