package com.newpillar.game.items;

import com.newpillar.NewPillar;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 物品冷却管理器
 * 统一管理特殊物品的冷却显示和逻辑
 * 支持多个冷却叠加显示
 */
public class ItemCooldownManager {
    
    private final NewPillar plugin;
    
    // 冷却记录: <玩家UUID, <物品ID, 冷却结束时间戳>>
    private final Map<UUID, Map<String, Long>> playerCooldowns = new ConcurrentHashMap<>();
    
    // 每个玩家的多个BossBar: <玩家UUID, <物品ID, BossBar>>
    private final Map<UUID, Map<String, BossBar>> playerBossBars = new ConcurrentHashMap<>();
    
    // BossBar更新任务: <玩家UUID, 任务>
    private final Map<UUID, io.papermc.paper.threadedregions.scheduler.ScheduledTask> bossBarTasks = new ConcurrentHashMap<>();
    
    public ItemCooldownManager(NewPillar plugin) {
        this.plugin = plugin;
    }
    
    /**
     * 检查物品是否处于冷却中
     * @param player 玩家
     * @param itemId 物品ID
     * @param cooldownSeconds 冷却时间（秒）
     * @return true 如果物品在冷却中
     */
    public boolean isOnCooldown(Player player, String itemId, long cooldownSeconds) {
        UUID playerId = player.getUniqueId();
        Map<String, Long> cooldowns = playerCooldowns.get(playerId);
        if (cooldowns == null) {
            return false;
        }
        
        Long endTime = cooldowns.get(itemId);
        if (endTime == null) {
            return false;
        }
        
        return System.currentTimeMillis() < endTime;
    }
    
    /**
     * 获取剩余冷却时间（秒）
     * @param player 玩家
     * @param itemId 物品ID
     * @param cooldownSeconds 冷却时间（秒）
     * @return 剩余冷却时间（秒），0表示冷却完成
     */
    public long getRemainingCooldown(Player player, String itemId, long cooldownSeconds) {
        UUID playerId = player.getUniqueId();
        Map<String, Long> cooldowns = playerCooldowns.get(playerId);
        if (cooldowns == null) {
            return 0;
        }
        
        Long endTime = cooldowns.get(itemId);
        if (endTime == null) {
            return 0;
        }
        
        long remaining = endTime - System.currentTimeMillis();
        return Math.max(0, remaining / 1000);
    }
    
    /**
     * 设置物品冷却（带冷却时间）
     * @param player 玩家
     * @param itemId 物品ID
     * @param cooldownSeconds 冷却时间（秒）
     */
    public void setCooldown(Player player, String itemId, long cooldownSeconds) {
        UUID playerId = player.getUniqueId();
        long endTime = System.currentTimeMillis() + (cooldownSeconds * 1000);
        playerCooldowns.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                .put(itemId, endTime);
    }
    
    /**
     * 设置物品冷却（使用默认冷却时间，兼容旧代码）
     * @param player 玩家
     * @param itemId 物品ID
     */
    public void setCooldown(Player player, String itemId) {
        // 默认使用60秒冷却
        setCooldown(player, itemId, 60);
    }
    
    /**
     * 开始BossBar冷却显示
     * @param player 玩家
     * @param itemId 物品ID
     * @param itemName 物品名称（用于显示）
     * @param cooldownSeconds 冷却时间（秒）
     * @param color BossBar颜色
     */
    public void startCooldownDisplay(Player player, String itemId, String itemName, 
                                     long cooldownSeconds, BossBar.Color color) {
        UUID playerId = player.getUniqueId();
        
        // 存储冷却结束时间
        long endTime = System.currentTimeMillis() + (cooldownSeconds * 1000);
        playerCooldowns.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                .put(itemId, endTime);
        
        // 获取或创建该玩家的BossBar映射
        Map<String, BossBar> playerBars = playerBossBars.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
        
        // 如果该物品已有BossBar，先移除
        BossBar oldBar = playerBars.remove(itemId);
        if (oldBar != null) {
            player.hideBossBar(oldBar);
        }
        
        // 创建新的BossBar
        BossBar bossBar = BossBar.bossBar(
                Component.text(colorToChatCode(color) + "「" + itemName + "」冷却中"),
                1.0f,
                color,
                BossBar.Overlay.PROGRESS
        );
        player.showBossBar(bossBar);
        playerBars.put(itemId, bossBar);
        
        // 如果没有更新任务，创建一个
        if (!bossBarTasks.containsKey(playerId)) {
            startUpdateTask(playerId);
        }
    }
    
    /**
     * 启动更新任务
     */
    private void startUpdateTask(UUID playerId) {
        io.papermc.paper.threadedregions.scheduler.ScheduledTask task = 
                Bukkit.getAsyncScheduler().runAtFixedRate(plugin, scheduledTask -> {
                    Player player = Bukkit.getPlayer(playerId);
                    if (player == null || !player.isOnline()) {
                        scheduledTask.cancel();
                        cleanupPlayer(playerId);
                        return;
                    }
                    
                    Map<String, BossBar> playerBars = playerBossBars.get(playerId);
                    Map<String, Long> cooldowns = playerCooldowns.get(playerId);
                    
                    if (playerBars == null || playerBars.isEmpty()) {
                        scheduledTask.cancel();
                        bossBarTasks.remove(playerId);
                        return;
                    }
                    
                    long now = System.currentTimeMillis();
                    List<String> toRemove = new ArrayList<>();
                    
                    for (Map.Entry<String, BossBar> entry : playerBars.entrySet()) {
                        String itemId = entry.getKey();
                        BossBar bossBar = entry.getValue();
                        Long endTime = cooldowns != null ? cooldowns.get(itemId) : null;
                        
                        if (endTime == null) {
                            toRemove.add(itemId);
                            continue;
                        }
                        
                        long remaining = endTime - now;
                        
                        if (remaining <= 0) {
                            // 冷却完成
                            bossBar.color(BossBar.Color.GREEN);
                            bossBar.progress(1.0f);
                            bossBar.name(Component.text("§a「" + getItemNameFromBar(bossBar) + "」已就绪！"));
                            
                            toRemove.add(itemId);
                            
                            // 2秒后移除
                            Bukkit.getAsyncScheduler().runDelayed(plugin, t -> {
                                removeBossBar(playerId, itemId);
                            }, 2L, TimeUnit.SECONDS);
                            
                            continue;
                        }
                        
                        // 更新进度
                        String itemName = getItemNameFromBar(bossBar);
                        long totalCooldown = getTotalCooldown(playerId, itemId);
                        if (totalCooldown > 0) {
                            float progress = (float) remaining / totalCooldown;
                            bossBar.progress(Math.max(0.0f, Math.min(1.0f, progress)));
                            
                            // 格式化剩余时间
                            long remainingSeconds = remaining / 1000;
                            long minutes = remainingSeconds / 60;
                            long seconds = remainingSeconds % 60;
                            String timeStr = minutes > 0 ? 
                                    String.format("%d:%02d", minutes, seconds) : 
                                    String.format("%d秒", seconds);
                            
                            // 根据进度动态调整颜色
                            BossBar.Color currentColor;
                            String chatCode;
                            if (progress > 0.6f) {
                                currentColor = BossBar.Color.RED;
                                chatCode = "§c";
                            } else if (progress > 0.3f) {
                                currentColor = BossBar.Color.PINK;
                                chatCode = "§d";
                            } else {
                                currentColor = BossBar.Color.GREEN;
                                chatCode = "§a";
                            }
                            
                            bossBar.color(currentColor);
                            bossBar.name(Component.text(chatCode + "「" + itemName + "」冷却中: §f" + timeStr));
                        }
                    }
                    
                    // 清理已完成的冷却
                    for (String itemId : toRemove) {
                        if (cooldowns != null) {
                            cooldowns.remove(itemId);
                        }
                    }
                    
                }, 0L, 500L, TimeUnit.MILLISECONDS);
        
        bossBarTasks.put(playerId, task);
    }
    
    /**
     * 从BossBar名称中提取物品名称
     */
    private String getItemNameFromBar(BossBar bossBar) {
        String name = bossBar.name().toString();
        // 提取「」之间的内容
        int start = name.indexOf('「');
        int end = name.indexOf('」');
        if (start >= 0 && end > start) {
            return name.substring(start + 1, end);
        }
        return "物品";
    }
    
    /**
     * 获取总冷却时间（用于计算进度）
     */
    private long getTotalCooldown(UUID playerId, String itemId) {
        // 默认返回60秒，实际应该从配置或首次设置获取
        // 这里简化处理，根据剩余时间估算
        Map<String, Long> cooldowns = playerCooldowns.get(playerId);
        if (cooldowns != null && cooldowns.containsKey(itemId)) {
            Long endTime = cooldowns.get(itemId);
            // 如果是刚开始的冷却，假设标准冷却时间为30-60秒
            long remaining = endTime - System.currentTimeMillis();
            // 根据剩余时间反推总时间（向上取整到最近的10秒）
            long total = ((remaining / 10000) + 1) * 10000;
            return Math.max(total, remaining);
        }
        return 60000; // 默认60秒
    }
    
    /**
     * 移除指定物品的BossBar
     */
    private void removeBossBar(UUID playerId, String itemId) {
        Map<String, BossBar> playerBars = playerBossBars.get(playerId);
        if (playerBars != null) {
            BossBar bossBar = playerBars.remove(itemId);
            if (bossBar != null) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    player.hideBossBar(bossBar);
                }
            }
            
            // 如果该玩家没有更多BossBar，清理任务
            if (playerBars.isEmpty()) {
                io.papermc.paper.threadedregions.scheduler.ScheduledTask task = bossBarTasks.remove(playerId);
                if (task != null) {
                    task.cancel();
                }
            }
        }
        
        // 清理冷却记录
        Map<String, Long> cooldowns = playerCooldowns.get(playerId);
        if (cooldowns != null) {
            cooldowns.remove(itemId);
        }
    }
    
    /**
     * 移除玩家的所有BossBar
     */
    private void removeAllBossBars(UUID playerId) {
        Map<String, BossBar> playerBars = playerBossBars.remove(playerId);
        if (playerBars != null) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                for (BossBar bossBar : playerBars.values()) {
                    player.hideBossBar(bossBar);
                }
            }
        }
        
        // 取消任务
        io.papermc.paper.threadedregions.scheduler.ScheduledTask task = bossBarTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }
    
    /**
     * BossBar颜色转聊天代码
     */
    private String colorToChatCode(BossBar.Color color) {
        return switch (color) {
            case PINK -> "§d";
            case BLUE -> "§9";
            case RED -> "§c";
            case GREEN -> "§a";
            case YELLOW -> "§e";
            case PURPLE -> "§5";
            case WHITE -> "§f";
        };
    }
    
    /**
     * 清理玩家数据
     */
    public void cleanupPlayer(UUID playerId) {
        playerCooldowns.remove(playerId);
        removeAllBossBars(playerId);
    }
    
    /**
     * 插件关闭时清理
     */
    public void shutdown() {
        for (io.papermc.paper.threadedregions.scheduler.ScheduledTask task : bossBarTasks.values()) {
            task.cancel();
        }
        bossBarTasks.clear();
        
        for (UUID playerId : new HashSet<>(playerBossBars.keySet())) {
            removeAllBossBars(playerId);
        }
        playerBossBars.clear();
        playerCooldowns.clear();
    }
}
