package com.newpillar.game;

import com.newpillar.NewPillar;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * EX咖喱棒管理器
 * 管理『EX咖喱棒』物品的冷却、光柱动画和伤害逻辑
 */
public class ExcaliburManager {
    
    private final NewPillar plugin;
    
    // 冷却时间: 1分30秒 = 90秒
    private static final long COOLDOWN_SECONDS = 90;
    private static final long COOLDOWN_MILLIS = COOLDOWN_SECONDS * 1000;
    
    // 技能参数
    private static final double BEAM_HEIGHT = 30.0;
    private static final double BEAM_RADIUS = 2.0;
    private static final double DAMAGE_MIN = 6.0;
    private static final double DAMAGE_MAX = 13.0;
    private static final double KNOCKBACK_STRENGTH = 0.3; // 减小击退效果
    private static final double END_ANGLE = -30.0; // 向下30度
    
    // 冷却记录
    private final Map<UUID, Long> playerCooldowns = new ConcurrentHashMap<>();
    
    // 技能释放中玩家（限制移动）
    private final Set<UUID> castingPlayers = ConcurrentHashMap.newKeySet();
    
    // BossBar冷却显示
    private final Map<UUID, BossBar> playerBossBars = new ConcurrentHashMap<>();
    private final Map<UUID, io.papermc.paper.threadedregions.scheduler.ScheduledTask> bossBarTasks = new ConcurrentHashMap<>();
    
    public ExcaliburManager(NewPillar plugin) {
        this.plugin = plugin;
    }
    
    /**
     * 创建EX咖喱棒物品
     */
    public static ItemStack createExcalibur(NewPillar plugin) {
        ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§l『§e§lEX§6§l咖喱棒』");
            meta.setLore(Arrays.asList(
                "§7传说中能释放金色光柱的圣剑",
                "",
                "§e右键释放: §f召唤金色光柱横扫前方",
                "§7对路径上的敌人造成 §c6-13 §7点伤害",
                "§7并造成小幅击退",
                "",
                "§8冷却时间: 1分30秒"
            ));
            
            // 添加自定义物品ID
            PersistentDataContainer container = meta.getPersistentDataContainer();
            NamespacedKey key = new NamespacedKey(plugin, "item_id");
            container.set(key, PersistentDataType.STRING, "excalibur");
            
            item.setItemMeta(meta);
        }
        return item;
    }
    
    /**
     * 检查物品是否为EX咖喱棒
     */
    public static boolean isExcalibur(NewPillar plugin, ItemStack item) {
        if (item == null || item.getType() != Material.DIAMOND_SWORD) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        
        PersistentDataContainer container = meta.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(plugin, "item_id");
        String itemId = container.get(key, PersistentDataType.STRING);
        return "excalibur".equals(itemId);
    }
    
    /**
     * 使用EX咖喱棒
     */
    public void useExcalibur(Player player) {
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        
        // 检查冷却
        if (playerCooldowns.containsKey(playerId)) {
            long lastUse = playerCooldowns.get(playerId);
            long remaining = COOLDOWN_MILLIS - (currentTime - lastUse);
            if (remaining > 0) {
                long seconds = remaining / 1000;
                player.sendMessage("§c『EX咖喱棒』冷却中，剩余 " + seconds + " 秒");
                return;
            }
        }
        
        // 设置冷却
        playerCooldowns.put(playerId, currentTime);
        
        // 开始BossBar冷却显示
        startBossBarDisplay(player);
        
        // 释放技能
        releaseExcalibur(player);
    }
    
    /**
     * 释放EX咖喱棒技能
     */
    private void releaseExcalibur(Player player) {
        UUID playerId = player.getUniqueId();
        Location playerLoc = player.getLocation();
        World world = player.getWorld();
        
        // 记录玩家释放技能前的位置和视角
        final Location castLocation = playerLoc.clone();
        castingPlayers.add(playerId);
        
        // 播放音效
        world.playSound(playerLoc, Sound.BLOCK_BEACON_POWER_SELECT, 2.0f, 0.5f);
        world.playSound(playerLoc, Sound.ENTITY_WITHER_SPAWN, 1.0f, 2.0f);
        
        // 计算光柱起始位置（玩家前方）
        Vector direction = player.getLocation().getDirection().setY(0).normalize();
        Location beamBase = playerLoc.clone().add(direction.clone().multiply(3));
        
        // 开始光柱动画
        animateBeam(player, beamBase, direction, castLocation);
    }
    
    /**
     * 光柱动画
     */
    private void animateBeam(Player player, Location beamBase, Vector direction, Location castLocation) {
        World world = beamBase.getWorld();
        if (world == null) return;
        
        UUID playerId = player.getUniqueId();
        
        // 光柱起始角度（垂直向上）
        final double[] currentAngle = {90.0};
        final int[] tick = {0};
        
        // 使用区域调度器执行动画 - 加快挥动速度（每tick减少4度，原来2度）
        Bukkit.getRegionScheduler().runAtFixedRate(plugin, beamBase, task -> {
            // 限制玩家移动和视角
            if (player.isOnline() && castingPlayers.contains(playerId)) {
                Location currentLoc = player.getLocation();
                // 锁定位置
                if (currentLoc.getX() != castLocation.getX() || 
                    currentLoc.getZ() != castLocation.getZ()) {
                    player.teleportAsync(new Location(world, 
                        castLocation.getX(), currentLoc.getY(), castLocation.getZ(),
                        castLocation.getYaw(), castLocation.getPitch()));
                } else {
                    // 锁定视角
                    player.setRotation(castLocation.getYaw(), castLocation.getPitch());
                }
            }
            
            if (tick[0] >= 30) { // 1.5秒动画 (30 ticks，原来60 ticks)
                castingPlayers.remove(playerId);
                task.cancel();
                return;
            }
            
            // 计算当前光柱角度
            double angleRad = Math.toRadians(currentAngle[0]);
            double x = Math.cos(angleRad) * direction.getX() * BEAM_HEIGHT;
            double z = Math.cos(angleRad) * direction.getZ() * BEAM_HEIGHT;
            double y = Math.sin(angleRad) * BEAM_HEIGHT;
            
            // 绘制光柱
            drawBeamSegment(world, beamBase, x, y, z);
            
            // 对路径上的实体造成伤害
            if (tick[0] % 3 == 0) { // 每3tick造成一次伤害（更频繁）
                damageEntitiesInBeam(player, beamBase, x, y, z);
            }
            
            // 更新角度（向下旋转）- 加快速度
            currentAngle[0] -= 4.0; // 每tick减少4度（原来2度）
            tick[0]++;
            
            // 检查是否到达结束角度
            if (currentAngle[0] <= END_ANGLE) {
                castingPlayers.remove(playerId);
                task.cancel();
            }
        }, 1L, 1L);
    }
    
    /**
     * 绘制光柱段
     */
    private void drawBeamSegment(World world, Location base, double endX, double endY, double endZ) {
        Location end = base.clone().add(endX, endY, endZ);
        Vector vector = end.toVector().subtract(base.toVector());
        double length = vector.length();
        Vector step = vector.normalize().multiply(0.5);
        
        Location current = base.clone();
        for (double i = 0; i < length; i += 0.5) {
            // 金色粒子效果
            world.spawnParticle(Particle.END_ROD, current, 3, 0.2, 0.2, 0.2, 0);
            world.spawnParticle(Particle.FLAME, current, 2, 0.1, 0.1, 0.1, 0);
            
            // 每隔一段距离添加爆炸粒子
            if (i % 3 == 0) {
                world.spawnParticle(Particle.EXPLOSION, current, 1, 0, 0, 0, 0);
            }
            
            current.add(step);
        }
    }
    
    /**
     * 对光柱路径上的实体造成伤害
     */
    private void damageEntitiesInBeam(Player caster, Location base, double endX, double endY, double endZ) {
        World world = base.getWorld();
        if (world == null) return;
        
        Location end = base.clone().add(endX, endY, endZ);
        Vector vector = end.toVector().subtract(base.toVector());
        double length = vector.length();
        Vector direction = vector.normalize();
        
        // 获取光柱路径上的所有实体
        for (Entity entity : world.getNearbyEntities(base, length, BEAM_HEIGHT, length)) {
            if (!(entity instanceof LivingEntity)) continue;
            if (entity.equals(caster)) continue; // 不伤害施法者
            if (entity instanceof Player && !isEnemy(caster, (Player) entity)) continue;
            
            Location entityLoc = entity.getLocation();
            
            // 检查实体是否在光柱路径上
            if (isEntityInBeamPath(base, end, entityLoc)) {
                LivingEntity target = (LivingEntity) entity;
                
                // 随机伤害 6-13
                double damage = DAMAGE_MIN + Math.random() * (DAMAGE_MAX - DAMAGE_MIN);
                
                // 造成伤害
                Bukkit.getRegionScheduler().execute(plugin, entityLoc, () -> {
                    target.damage(damage, caster);
                    
                    // 击退效果
                    Vector knockback = entityLoc.toVector().subtract(base.toVector()).normalize();
                    knockback.setY(0.3);
                    knockback.multiply(KNOCKBACK_STRENGTH);
                    target.setVelocity(knockback);
                    
                    // 播放受击音效
                    world.playSound(entityLoc, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.5f);
                });
            }
        }
    }
    
    /**
     * 检查实体是否在光柱路径上
     */
    private boolean isEntityInBeamPath(Location start, Location end, Location entityLoc) {
        Vector beamVector = end.toVector().subtract(start.toVector());
        Vector entityVector = entityLoc.toVector().subtract(start.toVector());
        
        double beamLength = beamVector.length();
        double projection = entityVector.dot(beamVector.normalize());
        
        // 检查投影是否在光柱范围内
        if (projection < 0 || projection > beamLength) {
            return false;
        }
        
        // 计算垂直距离
        Vector closestPoint = start.toVector().add(beamVector.normalize().multiply(projection));
        double distance = entityLoc.toVector().subtract(closestPoint).length();
        
        return distance <= BEAM_RADIUS;
    }
    
    /**
     * 判断是否为敌对玩家（队伍功能已禁用，所有玩家都是敌人）
     */
    private boolean isEnemy(Player player1, Player player2) {
        // 队伍功能已禁用，所有其他玩家都是敌人
        return true;
    }
    
    /**
     * 开始BossBar冷却显示
     */
    private void startBossBarDisplay(Player player) {
        UUID playerId = player.getUniqueId();
        
        // 移除之前的BossBar
        if (playerBossBars.containsKey(playerId)) {
            BossBar oldBar = playerBossBars.get(playerId);
            player.hideBossBar(oldBar);
            playerBossBars.remove(playerId);
        }
        
        // 取消之前的任务
        if (bossBarTasks.containsKey(playerId)) {
            bossBarTasks.get(playerId).cancel();
            bossBarTasks.remove(playerId);
        }
        
        // 创建BossBar
        BossBar bossBar = BossBar.bossBar(
            Component.text("§c『EX咖喱棒』冷却中"),
            1.0f,
            BossBar.Color.RED,
            BossBar.Overlay.PROGRESS
        );
        player.showBossBar(bossBar);
        playerBossBars.put(playerId, bossBar);
        
        // 创建更新任务
        io.papermc.paper.threadedregions.scheduler.ScheduledTask task = 
            Bukkit.getAsyncScheduler().runAtFixedRate(plugin, scheduledTask -> {
                if (!player.isOnline()) {
                    scheduledTask.cancel();
                    BossBar bar = playerBossBars.remove(playerId);
                    if (bar != null) {
                        player.hideBossBar(bar);
                    }
                    return;
                }
                
                long remaining = getRemainingCooldown(playerId);
                if (remaining <= 0) {
                    // 冷却完成
                    bossBar.name(Component.text("§a『EX咖喱棒』已就绪！"));
                    bossBar.color(BossBar.Color.GREEN);
                    bossBar.progress(1.0f);
                    
                    // 2秒后移除
                    Bukkit.getAsyncScheduler().runDelayed(plugin, t -> {
                        player.hideBossBar(bossBar);
                        playerBossBars.remove(playerId);
                    }, 2L, TimeUnit.SECONDS);
                    
                    scheduledTask.cancel();
                    bossBarTasks.remove(playerId);
                    return;
                }
                
                // 更新进度
                float progress = (float) remaining / COOLDOWN_SECONDS;
                bossBar.progress(Math.max(0.0f, Math.min(1.0f, progress)));
                
                // 格式化剩余时间
                long minutes = remaining / 60;
                long seconds = remaining % 60;
                String timeStr = minutes > 0 ? 
                    String.format("%d:%02d", minutes, seconds) : 
                    String.format("%d秒", seconds);
                
                bossBar.name(Component.text("§c『EX咖喱棒』冷却中: §f" + timeStr));
                
                // 根据剩余时间改变颜色
                if (progress > 0.6f) {
                    bossBar.color(BossBar.Color.RED);
                } else if (progress > 0.3f) {
                    bossBar.color(BossBar.Color.YELLOW);
                } else {
                    bossBar.color(BossBar.Color.GREEN);
                }
            }, 0L, 500L, TimeUnit.MILLISECONDS);
        
        bossBarTasks.put(playerId, task);
    }
    
    /**
     * 获取剩余冷却时间（秒）
     */
    public long getRemainingCooldown(UUID playerId) {
        if (!playerCooldowns.containsKey(playerId)) {
            return 0;
        }
        
        long lastUse = playerCooldowns.get(playerId);
        long remaining = COOLDOWN_MILLIS - (System.currentTimeMillis() - lastUse);
        return Math.max(0, remaining / 1000);
    }
    
    /**
     * 清理玩家数据
     */
    public void cleanupPlayer(UUID playerId) {
        playerCooldowns.remove(playerId);
        if (bossBarTasks.containsKey(playerId)) {
            bossBarTasks.get(playerId).cancel();
            bossBarTasks.remove(playerId);
        }
        if (playerBossBars.containsKey(playerId)) {
            // 注意：这里无法直接获取Player对象来hideBossBar
            // 玩家退出时会自动清理BossBar
            playerBossBars.remove(playerId);
        }
    }
    
    /**
     * 插件关闭时清理
     */
    public void shutdown() {
        for (io.papermc.paper.threadedregions.scheduler.ScheduledTask task : bossBarTasks.values()) {
            task.cancel();
        }
        bossBarTasks.clear();
        playerBossBars.clear();
        playerCooldowns.clear();
    }
}
