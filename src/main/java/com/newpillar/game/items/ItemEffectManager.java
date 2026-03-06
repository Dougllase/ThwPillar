package com.newpillar.game.items;

import com.newpillar.NewPillar;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.*;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;

public class ItemEffectManager {
    private final NewPillar plugin;
    private final SpecialItemManager specialItemManager;
    private final Random random = new Random();
    private final Map<UUID, ScheduledTask> activeEffects = new HashMap<>();
    private final NamespacedKey rocketBootsKey;
    private final NamespacedKey runningShoesKey;
    
    // 冷却系统: 玩家UUID -> 物品类型 -> 上次使用时间
    private final Map<UUID, Map<SpecialItemManager.SpecialItemType, Long>> cooldowns = new HashMap<>();
    
    // 物品冷却时间配置 (毫秒)
    private final Map<SpecialItemManager.SpecialItemType, Long> cooldownTimes = new HashMap<>();
    
    // BossBar冷却显示: 玩家UUID -> BossBar
    private final Map<UUID, BossBar> cooldownBossBars = new HashMap<>();
    
    // 冷却更新任务
    private ScheduledTask cooldownUpdateTask;

    public ItemEffectManager(NewPillar plugin, SpecialItemManager specialItemManager) {
        this.plugin = plugin;
        this.specialItemManager = specialItemManager;
        this.rocketBootsKey = new NamespacedKey(plugin, "rocket_boots");
        this.runningShoesKey = new NamespacedKey(plugin, "running_shoes");
        
        // 初始化冷却时间配置
        initCooldownConfig();
    }
    
    /**
     * 初始化物品冷却时间配置
     * 根据数据包定义，只有有冷却时间的物品才需要冷却
     */
    private void initCooldownConfig() {
        // 击退棒 - 3秒冷却
        cooldownTimes.put(SpecialItemManager.SpecialItemType.KNOCKBACK_STICK, 3000L);
        
        // 牌 - 10秒冷却
        cooldownTimes.put(SpecialItemManager.SpecialItemType.CARD, 10000L);
        
        // 蓝屏 - 10秒冷却
        cooldownTimes.put(SpecialItemManager.SpecialItemType.BLUE_SCREEN, 10000L);
        
        // 红包 - 3秒冷却
        cooldownTimes.put(SpecialItemManager.SpecialItemType.HONGBAO, 3000L);
        
        // 催眠APP - 30秒冷却
        cooldownTimes.put(SpecialItemManager.SpecialItemType.HYPNOSIS_APP, 30000L);
        
        // EX咖喱棒 - 90秒冷却
        cooldownTimes.put(SpecialItemManager.SpecialItemType.EX_CURRY_STICK, 90000L);
        
        // 时钟 - 15秒冷却
        cooldownTimes.put(SpecialItemManager.SpecialItemType.CLOCK, 15000L);
        
        // 刷怪笼 - 30秒冷却
        cooldownTimes.put(SpecialItemManager.SpecialItemType.SPAWNER, 30000L);
        
        // 布鲁斯 - 30秒冷却
        cooldownTimes.put(SpecialItemManager.SpecialItemType.BRUCE, 30000L);
        
        // 让你飞起来 - 5秒冷却
        cooldownTimes.put(SpecialItemManager.SpecialItemType.FLY_MACE, 5000L);
        
        // 皮鞋 - 1分钟播报冷却
        cooldownTimes.put(SpecialItemManager.SpecialItemType.PIXIE, 60000L);
        
        // 砸瓦鲁多 - 1分30秒冷却
        cooldownTimes.put(SpecialItemManager.SpecialItemType.THE_WORLD, 90000L);
        
        // 护盾发生器 - 1分钟冷却
        cooldownTimes.put(SpecialItemManager.SpecialItemType.SHIELD_GENERATOR, 60000L);
        
        // 注意：以下物品在数据包中无冷却定义
        // KNOCKBACK_STICK, SPEAR, CARD, IRON_SWORD, MEOW_AXE, BIG_FLAME_ROD
        // ROCKET_BOOTS, RUNNING_SHOES, FEATHER, INVISIBLE_SAND, GEDIao
        // GRAVITY_BOOTS, LIFE_STEAL_SWORD, POISON_DAGGER
    }
    
    /**
     * 启动冷却更新任务
     */
    public void startCooldownUpdateTask() {
        if (cooldownUpdateTask != null) {
            cooldownUpdateTask.cancel();
        }
        
        cooldownUpdateTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            // 更新所有玩家的BossBar冷却显示
            for (UUID uuid : cooldownBossBars.keySet()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    updateCooldownBossBar(player);
                }
            }
        }, 0L, 1L); // 每tick更新一次
    }
    
    /**
     * 停止冷却更新任务
     */
    public void stopCooldownUpdateTask() {
        if (cooldownUpdateTask != null) {
            cooldownUpdateTask.cancel();
            cooldownUpdateTask = null;
        }
        
        // 清理所有BossBar
        for (UUID uuid : cooldownBossBars.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                BossBar bossBar = cooldownBossBars.remove(uuid);
                if (bossBar != null) {
                    bossBar.removeAll();
                }
            }
        }
        cooldownBossBars.clear();
    }
    
    /**
     * 检查物品是否处于冷却中
     */
    private boolean isOnCooldown(Player player, SpecialItemManager.SpecialItemType type) {
        Long cooldownTime = cooldownTimes.get(type);
        if (cooldownTime == null) return false;
        
        Map<SpecialItemManager.SpecialItemType, Long> playerCooldowns = cooldowns.get(player.getUniqueId());
        if (playerCooldowns == null) return false;
        
        Long lastUse = playerCooldowns.get(type);
        if (lastUse == null) return false;
        
        return System.currentTimeMillis() - lastUse < cooldownTime;
    }
    
    /**
     * 获取剩余冷却时间 (秒)
     */
    private int getCooldownRemaining(Player player, SpecialItemManager.SpecialItemType type) {
        Long cooldownTime = cooldownTimes.get(type);
        if (cooldownTime == null) return 0;
        
        Map<SpecialItemManager.SpecialItemType, Long> playerCooldowns = cooldowns.get(player.getUniqueId());
        if (playerCooldowns == null) return 0;
        
        Long lastUse = playerCooldowns.get(type);
        if (lastUse == null) return 0;
        
        long remaining = cooldownTime - (System.currentTimeMillis() - lastUse);
        return (int) Math.ceil(remaining / 1000.0);
    }
    
    /**
     * 更新BossBar冷却显示
     */
    private void updateCooldownBossBar(Player player) {
        UUID uuid = player.getUniqueId();
        BossBar bossBar = cooldownBossBars.get(uuid);
        if (bossBar == null) return;
        
        // 找到剩余冷却时间最长的物品
        int maxRemaining = 0;
        SpecialItemManager.SpecialItemType maxType = null;
        
        Map<SpecialItemManager.SpecialItemType, Long> playerCooldowns = cooldowns.get(uuid);
        if (playerCooldowns != null) {
            for (Map.Entry<SpecialItemManager.SpecialItemType, Long> entry : playerCooldowns.entrySet()) {
                SpecialItemManager.SpecialItemType type = entry.getKey();
                Long lastUse = entry.getValue();
                Long cooldownTime = cooldownTimes.get(type);
                
                if (cooldownTime != null && lastUse != null) {
                    long remaining = cooldownTime - (System.currentTimeMillis() - lastUse);
                    if (remaining > 0) {
                        int remainingSec = (int) Math.ceil(remaining / 1000.0);
                        if (remainingSec > maxRemaining) {
                            maxRemaining = remainingSec;
                            maxType = type;
                        }
                    }
                }
            }
        }
        
        if (maxType != null) {
            // 更新BossBar进度
            long totalTime = cooldownTimes.get(maxType);
            long remaining = totalTime - (System.currentTimeMillis() - playerCooldowns.get(maxType));
            double progress = Math.max(0.0, remaining / (double) totalTime);
            
            bossBar.setProgress((float) progress);
            bossBar.setTitle("冷却中... " + maxRemaining + "s");
        } else {
            // 没有冷却，隐藏BossBar
            bossBar.setProgress(0.0f);
            bossBar.setTitle("");
        }
    }
    
    /**
     * 设置物品冷却并更新BossBar
     */
    private void setCooldown(Player player, SpecialItemManager.SpecialItemType type) {
        cooldowns.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                .put(type, System.currentTimeMillis());
        
        // 创建或更新BossBar
        BossBar bossBar = cooldownBossBars.computeIfAbsent(player.getUniqueId(), k -> 
            Bukkit.createBossBar("冷却中...", BarColor.RED, BarStyle.SOLID));
        bossBar.addPlayer(player);
        bossBar.setVisible(true);
        updateCooldownBossBar(player);
        
        // 同时更新Actionbar显示
        updateCooldownActionbar(player);
    }
    
    /**
     * 更新Actionbar冷却显示
     */
    private void updateCooldownActionbar(Player player) {
        UUID uuid = player.getUniqueId();
        Map<SpecialItemManager.SpecialItemType, Long> playerCooldowns = cooldowns.get(uuid);
        
        if (playerCooldowns == null || playerCooldowns.isEmpty()) {
            player.sendActionBar(Component.empty());
            return;
        }
        
        // 找到剩余冷却时间最长的物品
        int maxRemaining = 0;
        SpecialItemManager.SpecialItemType maxType = null;
        
        for (Map.Entry<SpecialItemManager.SpecialItemType, Long> entry : playerCooldowns.entrySet()) {
            SpecialItemManager.SpecialItemType type = entry.getKey();
            Long lastUse = entry.getValue();
            Long cooldownTime = cooldownTimes.get(type);
            
            if (cooldownTime != null && lastUse != null) {
                long remaining = cooldownTime - (System.currentTimeMillis() - lastUse);
                if (remaining > 0) {
                    int remainingSec = (int) Math.ceil(remaining / 1000.0);
                    if (remainingSec > maxRemaining) {
                        maxRemaining = remainingSec;
                        maxType = type;
                    }
                }
            }
        }
        
        if (maxType != null) {
            player.sendActionBar(Component.text("§c冷却: §6" + maxRemaining + "s")
                    .color(TextColor.color(0xFF5555)));
        } else {
            player.sendActionBar(Component.empty());
        }
    }
    
    /**
     * 清除物品冷却并更新BossBar
     */
    public void clearCooldown(Player player, SpecialItemManager.SpecialItemType type) {
        Map<SpecialItemManager.SpecialItemType, Long> playerCooldowns = cooldowns.get(player.getUniqueId());
        if (playerCooldowns != null) {
            playerCooldowns.remove(type);
            if (playerCooldowns.isEmpty()) {
                cooldowns.remove(player.getUniqueId());
                
                // 清除BossBar
                BossBar bossBar = cooldownBossBars.remove(player.getUniqueId());
                if (bossBar != null) {
                    bossBar.removeAll();
                }
                
                // 清除Actionbar
                player.sendActionBar(Component.empty());
            } else {
                updateCooldownBossBar(player);
                updateCooldownActionbar(player);
            }
        }
    }
    
    /**
     * 检查物品是否受限制（冷却、范围检查等）
     * @return true 如果可以使用, false 如果被限制
     */
    private boolean checkItemRestrictions(Player player, SpecialItemManager.SpecialItemType type) {
        // 检查冷却
        if (isOnCooldown(player, type)) {
            int remaining = getCooldownRemaining(player, type);
            player.sendMessage(ChatColor.RED + "物品冷却中... 还需 " + remaining + " 秒");
            return false;
        }
        
        // 检查范围目标（需要范围内有玩家的物品）
        if (requiresTargetPlayer(type)) {
            boolean hasTarget = false;
            for (Entity entity : player.getNearbyEntities(20, 20, 20)) {
                if (entity instanceof Player && !entity.equals(player)) {
                    hasTarget = true;
                    break;
                }
            }
            
            if (!hasTarget) {
                player.sendMessage(ChatColor.RED + "附近没有其他玩家！");
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 检查物品是否需要范围内有玩家作为目标
     */
    private boolean requiresTargetPlayer(SpecialItemManager.SpecialItemType type) {
        return switch (type) {
            case HYPNOSIS_APP, // 催眠APP
                 BLUE_SCREEN, // 蓝屏
                 PIXIE -> true; // 皮鞋
            default -> false;
        };
    }

    public void onPlayerInteract(Player player, ItemStack item) {
        if (item == null) return;

        SpecialItemManager.SpecialItemType type = specialItemManager.getSpecialItemType(item);
        if (type == null) return;

        // 火箭靴和跑鞋是装备类，不需要右键处理，直接返回让它们正常装备
        if (type == SpecialItemManager.SpecialItemType.ROCKET_BOOTS ||
            type == SpecialItemManager.SpecialItemType.RUNNING_SHOES) {
            return;
        }

        // 皮鞋特殊处理：右键时穿上并触发效果（有播报冷却）
        if (type == SpecialItemManager.SpecialItemType.PIXIE) {
            // 检查播报冷却（1分钟 = 60000毫秒）
            if (!isOnCooldown(player, type)) {
                // 冷却完成，播报并设置冷却
                usePixie(player);
                setCooldown(player, type);
                grantItemAchievement(player, "pixie");
            }
            // 无论是否有冷却，都允许穿上（不取消事件）
            return;
        }

        // 检查物品限制（冷却）
        if (!checkItemRestrictions(player, type)) {
            return;
        }

        // 设置冷却
        setCooldown(player, type);

        // 检查是否需要消耗物品（消耗型物品）
        boolean shouldConsume = isConsumable(type);

        switch (type) {
            case KNOCKBACK_STICK -> {
                useKnockbackStick(player);
                grantItemAchievement(player, "knockback_stick");
            }
            case SPEAR -> {
                // 长矛右键不触发技能，直接返回
                return;
            }
            case BONES_WITHOUT_CHICKEN_FEET -> {
                useBone(player);
                grantItemAchievement(player, "bones_without_chicken_feet");
            }
            case IRON_SWORD -> {
                // 铁剑使用 blocks_attacks 组件实现格挡，无需额外处理
                grantItemAchievement(player, "iron_sword");
            }
            case CARD -> {
                useCard(player);
                grantItemAchievement(player, "yanpai");
            }
            case PIXIE -> {
                // 已在上面的装备类物品检查中处理，这里不会执行
                usePixie(player);
                grantItemAchievement(player, "pixie");
            }
            case ROCKET_BOOTS -> {
                // 已在上面的装备类物品检查中处理，这里不会执行
                // 二段跳功能在 PlayerListener 中通过跳跃事件处理
                grantItemAchievement(player, "rocket_boots");
            }
            case RUNNING_SHOES -> {
                // 已在上面的装备类物品检查中处理，这里不会执行
                grantItemAchievement(player, "running_shoes");
            }
            case BLUE_SCREEN -> {
                useBlueScreen(player);
                grantItemAchievement(player, "blue_screen");
            }
            case HONGBAO -> {
                useHongbao(player);
                grantItemAchievement(player, "hongbao");
            }
            case HYPNOSIS_APP -> {
                useHypnosisApp(player);
                grantItemAchievement(player, "hypnosis_app");
            }
            case CLOCK -> {
                useClock(player);
                grantItemAchievement(player, "clock");
            }
            case SPAWNER -> {
                useSpawner(player);
                grantItemAchievement(player, "spawner");
            }
            case BRUCE -> {
                useBruce(player);
                grantItemAchievement(player, "bruce");
            }
            case WITCH_APPLE -> {
                useWitchApple(player);
                grantItemAchievement(player, "witch_apple");
            }
            case BIG_FLAME_ROD -> grantItemAchievement(player, "big_flame_rod");
            case MEOW_AXE -> grantItemAchievement(player, "meow_axe");
            case FLY_MACE -> {
                useFlyMace(player);
                grantItemAchievement(player, "fly_mace");
            }
            case EX_CURRY_STICK -> {
                useExCurryStick(player);
                grantItemAchievement(player, "ex_curry_stick");
            }
            case INVISIBLE_SAND -> grantItemAchievement(player, "invisible_scarf");
            case FEATHER -> grantItemAchievement(player, "feather");
            case GODLY_PICKAXE -> grantItemAchievement(player, "godly_pickaxe");
            case THE_WORLD -> {
                useTheWorld(player);
                grantItemAchievement(player, "the_world");
            }
            case SHIELD_GENERATOR -> {
                useShieldGenerator(player);
                grantItemAchievement(player, "shield_generator");
            }
        }

        // 消耗物品（从玩家背包中移除一个）
        if (shouldConsume) {
            consumeItem(player, item);
        }
    }
    
    /**
     * 判断物品是否为消耗型
     */
    private boolean isConsumable(SpecialItemManager.SpecialItemType type) {
        return switch (type) {
            case BONES_WITHOUT_CHICKEN_FEET, // 有骨无鸡爪
                 HONGBAO, // 红包
                 WITCH_APPLE, // 女巫苹果
                 CARD, // 牌
                 CLOCK, // 时钟
                 BLUE_SCREEN, // 蓝屏
                 HYPNOSIS_APP, // 催眠APP
                 SPAWNER, // 刷怪笼
                 BRUCE, // 布鲁斯
                 EX_CURRY_STICK, // EX咖喱棒
                 THE_WORLD, // 砸瓦鲁多
                 SHIELD_GENERATOR -> true; // 护盾发生器
            default -> false;
        };
    }

    /**
     * 消耗玩家手中的物品
     */
    private void consumeItem(Player player, ItemStack item) {
        // 减少物品数量
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            // 如果只有一个，从主手移除
            player.getInventory().setItemInMainHand(null);
        }
    }

    /**
     * 授予物品成就
     */
    private void grantItemAchievement(Player player, String itemId) {
        if (plugin.getAchievementSystem() != null) {
            plugin.getAchievementSystem().grantItemAchievement(player, itemId);
        }
    }

    // ==================== 攻击类物品 ====================

    private void useSpear(Player player) {
        // 长矛：突进效果 - 向前冲刺
        player.sendMessage(ChatColor.GOLD + "突进！");
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.0f);

        // 给予短暂的速度和跳跃提升效果
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 2, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 60, 1, false, false));

        // 向前冲刺
        Vector direction = player.getLocation().getDirection().normalize();
        player.setVelocity(direction.multiply(1.5).setY(0.3));
    }

    private void useKnockbackStick(Player player) {
        // 击退棒：击退周围玩家
        player.sendMessage(ChatColor.GOLD + "吃我一棒！");
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.0f);

        // 击退周围玩家2格
        for (Entity entity : player.getNearbyEntities(3, 3, 3)) {
            if (entity instanceof Player && !entity.equals(player)) {
                Vector knockback = entity.getLocation().toVector()
                        .subtract(player.getLocation().toVector())
                        .normalize()
                        .multiply(2.0)
                        .setY(0.5);
                entity.setVelocity(knockback);
                ((Player) entity).sendMessage(ChatColor.RED + "被" + player.getName() + "击退了！");
            }
        }
    }

    private void useBone(Player player) {
        // 有骨无鸡爪：食用效果
        player.setFoodLevel(Math.min(player.getFoodLevel() + 4, 20));
        player.setSaturation(Math.min(player.getSaturation() + 2, 20));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, 1.0f, 1.0f);
        player.sendMessage(ChatColor.GOLD + "嘎嘣脆！");
    }

    private void useCard(Player player) {
        // 牌：播报"我要验牌！"并击退周围玩家1格
        Bukkit.broadcastMessage(ChatColor.GOLD + "【验牌】" + ChatColor.WHITE + player.getName() + ": " + ChatColor.YELLOW + "我要验牌！");
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.0f);

        // 击退周围玩家1格
        for (Entity entity : player.getNearbyEntities(5, 5, 5)) {
            if (entity instanceof Player && !entity.equals(player)) {
                Vector knockback = entity.getLocation().toVector()
                        .subtract(player.getLocation().toVector())
                        .normalize()
                        .multiply(1.0)
                        .setY(0.3);
                entity.setVelocity(knockback);
                ((Player) entity).sendMessage(ChatColor.RED + "被" + player.getName() + "验牌击退！");
            }
        }
    }

    // ==================== 辅助类物品 ====================

    private void applyFeatherEffect(Player player) {
        // 羽毛：缓慢降落
        clearActiveEffects(player);

        ScheduledTask task = Bukkit.getRegionScheduler().runAtFixedRate(plugin, player.getLocation(), scheduledTask -> {
            if (!player.isOnline() || player.isDead()) {
                scheduledTask.cancel();
                return;
            }

            ItemStack mainHand = player.getInventory().getItemInMainHand();
            if (!specialItemManager.isSpecialItem(mainHand) ||
                    specialItemManager.getSpecialItemType(mainHand) != SpecialItemManager.SpecialItemType.FEATHER) {
                scheduledTask.cancel();
                return;
            }

            // 给予缓慢降落效果
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 40, 0, false, false));
        }, 1L, 20L);

        activeEffects.put(player.getUniqueId(), task);
    }

    private void applyInvisibleEffect(Player player) {
        // 隐身沙粒：放在快捷栏获得隐身效果
        clearActiveEffects(player);

        ScheduledTask task = Bukkit.getRegionScheduler().runAtFixedRate(plugin, player.getLocation(), scheduledTask -> {
            if (!player.isOnline() || player.isDead()) {
                scheduledTask.cancel();
                return;
            }

            // 检查快捷栏（0-8槽位）是否有隐身沙粒
            boolean hasInvisibleSand = false;
            for (int i = 0; i < 9; i++) {
                ItemStack item = player.getInventory().getItem(i);
                if (specialItemManager.isSpecialItem(item) &&
                        specialItemManager.getSpecialItemType(item) == SpecialItemManager.SpecialItemType.INVISIBLE_SAND) {
                    hasInvisibleSand = true;
                    break;
                }
            }

            if (!hasInvisibleSand) {
                scheduledTask.cancel();
                player.removePotionEffect(PotionEffectType.INVISIBILITY);
                return;
            }

            // 给予隐身效果
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 40, 0, false, false));
        }, 1L, 20L);

        activeEffects.put(player.getUniqueId(), task);
    }

    private void usePixie(Player player) {
        // 皮鞋：给我擦皮鞋！
        Bukkit.broadcastMessage(ChatColor.GOLD + "【系统】" + ChatColor.WHITE + player.getName() + ": 给我擦皮鞋！");
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);

        // 击退周围玩家0.5格
        for (Entity entity : player.getNearbyEntities(3, 3, 3)) {
            if (entity instanceof Player && !entity.equals(player)) {
                Vector knockback = entity.getLocation().toVector()
                        .subtract(player.getLocation().toVector())
                        .normalize()
                        .multiply(0.5)
                        .setY(0.2);
                entity.setVelocity(knockback);
            }
        }
    }

    // ==================== 特殊类物品 ====================

    private void useBlueScreen(Player player) {
        // 蓝屏：禁锢并冻结目标（像在细雪中一样）
        // 选择最近的玩家作为目标
        Player target = null;
        double minDistance = Double.MAX_VALUE;

        for (Entity entity : player.getNearbyEntities(10, 10, 10)) {
            if (entity instanceof Player && !entity.equals(player)) {
                double distance = entity.getLocation().distance(player.getLocation());
                if (distance < minDistance) {
                    minDistance = distance;
                    target = (Player) entity;
                }
            }
        }

        if (target != null) {
            final Player finalTarget = target;
            player.sendMessage(ChatColor.BLUE + "对 " + target.getName() + " 使用蓝屏！");
            target.sendMessage(ChatColor.BLUE + "" + ChatColor.BOLD + "╔════════════════════════════════╗");
            target.sendMessage(ChatColor.BLUE + "" + ChatColor.BOLD + "║      WINDOWS ERROR             ║");
            target.sendMessage(ChatColor.BLUE + "" + ChatColor.BOLD + "║                                ║");
            target.sendMessage(ChatColor.BLUE + "" + ChatColor.BOLD + "║  :( Your PC ran into a problem ║");
            target.sendMessage(ChatColor.BLUE + "" + ChatColor.BOLD + "║  and needs to restart.         ║");
            target.sendMessage(ChatColor.BLUE + "" + ChatColor.BOLD + "╚════════════════════════════════╝");

            // 禁锢效果：12秒失明、无法移动、无法跳跃
            // 缓慢V（无法移动）
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 240, 4, false, false));
            // 挖掘疲劳（无法挖掘）
            target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 240, 4, false, false));
            // 失明
            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 240, 0, false, false));
            // 虚弱（无法攻击）
            target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 240, 4, false, false));
            // 跳跃提升-128（无法跳跃）
            target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 240, 128, false, false));

            // 播放蓝屏音效
            target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);

            // 12秒后解除，提示恢复
            final Player finalTarget2 = target;
            Bukkit.getRegionScheduler().runDelayed(plugin, target.getLocation(), new java.util.function.Consumer<io.papermc.paper.threadedregions.scheduler.ScheduledTask>() {
                @Override
                public void accept(io.papermc.paper.threadedregions.scheduler.ScheduledTask task) {
                    if (finalTarget2.isOnline()) {
                        finalTarget2.sendMessage(ChatColor.GREEN + "系统已恢复！");
                        finalTarget2.playSound(finalTarget2.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
                    }
                }
            }, 240L);
        } else {
            player.sendMessage(ChatColor.RED + "附近没有其他玩家！");
        }
    }

    private void useHongbao(Player player) {
        // 红包：随机给予金币或物品
        player.sendMessage(ChatColor.GOLD + "新年快乐！红包打开！");
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        // 随机效果
        int reward = random.nextInt(3);
        switch (reward) {
            case 0 -> {
                player.sendMessage(ChatColor.GREEN + "恭喜发财！获得一些经验！");
                player.giveExp(100);
            }
            case 1 -> {
                player.sendMessage(ChatColor.GREEN + "大吉大利！获得一些食物！");
                player.getInventory().addItem(new ItemStack(Material.COOKED_BEEF, 5));
            }
            case 2 -> {
                player.sendMessage(ChatColor.GREEN + "万事如意！获得一些资源！");
                player.getInventory().addItem(new ItemStack(Material.IRON_INGOT, 3));
            }
        }
    }

    private void useHypnosisApp(Player player) {
        // 催眠APP：随机催眠一名玩家
        List<Player> targets = new ArrayList<>();
        for (Entity entity : player.getNearbyEntities(20, 20, 20)) {
            if (entity instanceof Player && !entity.equals(player)) {
                targets.add((Player) entity);
            }
        }

        if (!targets.isEmpty()) {
            Player target = targets.get(random.nextInt(targets.size()));
            player.sendMessage(ChatColor.LIGHT_PURPLE + "对 " + target.getName() + " 使用催眠APP！");
            target.sendMessage(ChatColor.LIGHT_PURPLE + "你被 " + player.getName() + " 催眠了！");

            // 催眠效果：缓慢I 10s，挖掘疲劳III 15s，反胃I 30s（加强效果）
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 0)); // 缓慢I 10s
            target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 300, 2)); // 挖掘疲劳III 15s
            target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 600, 0)); // 反胃I 30s（加强）
        } else {
            player.sendMessage(ChatColor.RED + "附近没有其他玩家！");
        }
    }

    private void useClock(Player player) {
        // 时钟：切换白天/夜晚
        long currentTime = player.getWorld().getTime();
        if (currentTime > 12000) {
            // 当前是夜晚，切换到白天
            player.getWorld().setTime(1000);
            Bukkit.broadcastMessage(ChatColor.YELLOW + player.getName() + " 使用时钟将时间切换到白天！");
        } else {
            // 当前是白天，切换到夜晚
            player.getWorld().setTime(13000);
            Bukkit.broadcastMessage(ChatColor.BLUE + player.getName() + " 使用时钟将时间切换到夜晚！");
        }
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.0f);
    }

    private void useSpawner(Player player) {
        // 刷怪笼：在玩家可触及的最远位置放置刷怪笼并设置参数
        player.sendMessage(ChatColor.RED + "召唤临时刷怪笼！");
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_STONE_PLACE, 1.0f, 1.0f);

        // 在玩家视线方向上寻找最远可放置位置（最远10格，可以凭空放置）
        Location eyeLoc = player.getEyeLocation();
        Vector direction = eyeLoc.getDirection();
        Location targetLoc = null;
        
        // 向前寻找最远10格的位置
        for (int i = 10; i >= 1; i--) {
            Location checkLoc = eyeLoc.clone().add(direction.clone().multiply(i));
            // 可以直接放置，不需要检查是否为空气
            targetLoc = checkLoc;
            break;
        }
        
        // 如果没找到位置，就在玩家面前5格处
        if (targetLoc == null) {
            targetLoc = player.getLocation().add(direction.clone().multiply(5));
        }

        final Location spawnerLoc = targetLoc.clone();
        spawnerLoc.getBlock().setType(Material.SPAWNER);
        
        // 获取刷怪笼状态并设置参数
        if (spawnerLoc.getBlock().getState() instanceof org.bukkit.block.CreatureSpawner spawner) {
            // 设置生成僵尸
            spawner.setSpawnedType(org.bukkit.entity.EntityType.ZOMBIE);
            // 设置生成延迟参数（与数据包一致）
            spawner.setMaxSpawnDelay(200); // MaxSpawnDelay: 200 ticks (10秒)
            spawner.setMinSpawnDelay(20);  // MinSpawnDelay: 20 ticks (1秒)
            spawner.setDelay(100);         // Delay: 100 ticks (5秒)
            spawner.update();
        }

        player.sendMessage(ChatColor.GRAY + "刷怪笼已放置在 " + spawnerLoc.getBlockX() + ", " + spawnerLoc.getBlockY() + ", " + spawnerLoc.getBlockZ());

        // 30秒后移除刷怪笼
        Bukkit.getRegionScheduler().runDelayed(plugin, spawnerLoc, task -> {
            if (spawnerLoc.getBlock().getType() == Material.SPAWNER) {
                spawnerLoc.getBlock().setType(Material.AIR);
                player.sendMessage(ChatColor.GRAY + "临时刷怪笼已消失");
            }
        }, 30L * 20L); // 30秒 = 600 ticks
    }

    private void useBruce(Player player) {
        // 布鲁斯：召唤一只狼
        player.sendMessage(ChatColor.GRAY + "召唤布鲁斯！");
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WOLF_AMBIENT, 1.0f, 1.0f);

        Wolf wolf = (Wolf) player.getWorld().spawnEntity(player.getLocation(), EntityType.WOLF);
        wolf.setCustomName("布鲁斯");
        wolf.setCustomNameVisible(true);
        wolf.setOwner(player);
        wolf.setTamed(true);
        
        // 给狼装备狼铠
        EntityEquipment equipment = wolf.getEquipment();
        if (equipment != null) {
            equipment.setItemInMainHand(new ItemStack(Material.WOLF_ARMOR));
        }
    }

    private void useWitchApple(Player player) {
        // 女巫的红苹果：随机效果
        player.sendMessage(ChatColor.DARK_RED + "吃下女巫的红苹果...");
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EAT, 1.0f, 1.0f);

        // 随机效果：50%几率好效果，50%几率坏效果
        if (random.nextBoolean()) {
            player.sendMessage(ChatColor.GREEN + "你感到充满力量！");
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 1));
            player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 1200, 0));
        } else {
            player.sendMessage(ChatColor.RED + "你感到不适...");
            player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 0));
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 0));
        }
    }

    private void useFlyMace(Player player) {
        // 让你飞起来：数据包实现 - 播放粒子效果和音效，向上冲量
        player.sendMessage(ChatColor.AQUA + "起飞！");
        
        // 播放 gust_emitter_small 粒子效果（在玩家位置）
        player.getWorld().spawnParticle(Particle.GUST, player.getLocation(), 10, 0.5, 0.5, 0.5, 0.1);
        
        // 播放风爆音效
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.0f, 1.0f);

        // 向上发射 (Y轴速度 1.3，与数据包一致)
        Vector velocity = player.getVelocity();
        velocity.setY(1.3);
        player.setVelocity(velocity);
    }

    // ==================== 工具方法 ====================

    private void clearActiveEffects(Player player) {
        ScheduledTask task = activeEffects.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    public void clearAllEffects(Player player) {
        clearActiveEffects(player);
    }
    
    /**
     * 处理玩家手持物品变化
     */
    public void onItemHeld(Player player, ItemStack item) {
        if (item == null) {
            clearActiveEffects(player);
            return;
        }
        
        SpecialItemManager.SpecialItemType type = specialItemManager.getSpecialItemType(item);
        if (type == null) {
            clearActiveEffects(player);
            return;
        }
        
        // 根据手持物品类型应用效果
        switch (type) {
            case FEATHER -> applyFeatherEffect(player);
            case INVISIBLE_SAND -> applyInvisibleEffect(player);
            default -> clearActiveEffects(player);
        }
    }
    
    /**
     * 『EX咖喱棒』技能实现
     * 召唤大光柱，倒下造成伤害并击退
     */
    private void useExCurryStick(Player player) {
        Location playerLoc = player.getLocation();
        World world = player.getWorld();
        
        // 锁定玩家移动和视角
        lockPlayerMovement(player);
        
        // 1. 召唤竖直向上的大光柱
        spawnVerticalLightColumn(world, playerLoc);
        
        // 2. 延迟后光柱倒向玩家方向
        Bukkit.getRegionScheduler().runDelayed(plugin, playerLoc, task -> {
            if (!player.isOnline()) {
                unlockPlayerMovement(player);
                return;
            }
            
            // 计算光柱倒下的方向（以玩家头部为轴心）
            Vector direction = playerLoc.getDirection().normalize();
            direction.setY(0); // 保持水平方向
            
            // 倒向玩家方向（从竖直变为水平）
            animateLightColumnFall(world, playerLoc, direction);
            
            // 3. 延迟后造成伤害
            Bukkit.getRegionScheduler().runDelayed(plugin, playerLoc, task2 -> {
                if (!player.isOnline()) {
                    unlockPlayerMovement(player);
                    return;
                }
                
                // 造成伤害并击退
                dealDamageAndKnockback(player, playerLoc);
                
                // 解锁玩家移动
                unlockPlayerMovement(player);
            }, 20L); // 1秒延迟
        }, 10L); // 0.5秒延迟
    }
    
    // 存储被锁定移动的玩家
    private final Set<UUID> lockedPlayers = new HashSet<>();
    
    /**
     * 锁定玩家移动和视角
     */
    private void lockPlayerMovement(Player player) {
        lockedPlayers.add(player.getUniqueId());
        // 给予缓慢效果使玩家无法移动
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 255, false, false));
        player.sendMessage(ChatColor.GOLD + "正在蓄力...");
    }
    
    /**
     * 解锁玩家移动和视角
     */
    private void unlockPlayerMovement(Player player) {
        lockedPlayers.remove(player.getUniqueId());
        player.removePotionEffect(PotionEffectType.SLOWNESS);
    }
    
    /**
     * 检查玩家是否被锁定移动
     */
    public boolean isPlayerLocked(UUID playerId) {
        return lockedPlayers.contains(playerId);
    }
    
    /**
     * 召唤竖直向上的大光柱
     */
    private void spawnVerticalLightColumn(World world, Location loc) {
        // 从地面到天空的光柱
        for (int y = 0; y < 256; y += 5) {
            world.spawnParticle(Particle.LARGE_SMOKE, loc.getX(), y, loc.getZ(), 1, 0.5, 0.5, 0.5, 1.0);
            world.spawnParticle(Particle.FLAME, loc.getX(), y, loc.getZ(), 1, 0.3, 0.3, 0.3, 0.5);
        }
        
        // 播放音效
        world.playSound(loc, Sound.BLOCK_BEACON_AMBIENT, 1.0f, 1.0f);
    }
    
    /**
     * 光柱倒下的动画
     */
    private void animateLightColumnFall(World world, Location loc, Vector direction) {
        // 从竖直变为水平的动画（1秒）
        for (int i = 0; i < 20; i++) {
            double progress = i / 20.0; // 0.0 to 1.0
            double angle = Math.PI / 2 * progress; // 90度到0度
            
            // 计算光柱方向
            double x = direction.getX() * Math.cos(Math.atan2(direction.getZ(), direction.getX())) * Math.sin(angle);
            double y = Math.cos(angle);
            double z = direction.getZ() * Math.cos(Math.atan2(direction.getZ(), direction.getX())) * Math.sin(angle);
            
            // 绘制光柱路径
            for (double dist = 0; dist < 20; dist += 1) {
                Location particleLoc = loc.clone().add(x * dist, y * dist, z * dist);
                world.spawnParticle(Particle.DUST, particleLoc, 1, 0, 0, 0, new Particle.DustOptions(Color.ORANGE, 1.0f));
            }
        }
        
        // 播放倒下音效
        world.playSound(loc, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 1.0f);
    }
    
    /**
     * 对路径上的敌对实体造成伤害并击退
     */
    private void dealDamageAndKnockback(Player player, Location loc) {
        // 获取路径上的实体（以玩家为中心，半径20格）
        for (Entity entity : player.getNearbyEntities(20, 20, 20)) {
            if (entity instanceof Player target && !target.equals(player)) {
                // 对玩家造成伤害
                target.damage(random.nextDouble() * 7 + 6, player); // 6-13伤害
                
                // 击退效果（约2格）
                Vector knockback = target.getLocation().toVector().subtract(loc.toVector()).normalize();
                knockback.setY(Math.max(0.3, knockback.getY())); // 确保有向上的击退
                target.setVelocity(knockback.multiply(0.6)); // 减小击退距离到约2格
                
                // 播放音效
                target.playSound(target.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
            } else if (entity instanceof org.bukkit.entity.LivingEntity livingEntity) {
                // 对其他生物造成伤害
                livingEntity.damage(random.nextDouble() * 7 + 6, player);
                
                // 击退效果（约2格）
                Vector knockback = livingEntity.getLocation().toVector().subtract(loc.toVector()).normalize();
                knockback.setY(Math.max(0.3, knockback.getY()));
                livingEntity.setVelocity(knockback.multiply(0.6)); // 减小击退距离到约2格
            }
        }
    }
    
    /**
     * 清理所有资源
     */
    public void cleanup() {
        // 取消所有活跃的效果任务
        for (ScheduledTask task : activeEffects.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        activeEffects.clear();
        cooldowns.clear();
    }
    
    // ==================== 新物品效果实现 ====================
    
    /**
     * 砸瓦鲁多 - 冻结周围玩家9秒
     */
    private void useTheWorld(Player player) {
        player.sendMessage(ChatColor.GOLD + "The World!!!");
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BELL_USE, 1.0f, 1.0f);
        
        // 冻结周围10格内的所有玩家9秒（180 ticks）
        for (Entity entity : player.getNearbyEntities(10, 10, 10)) {
            if (entity instanceof Player target && !target.equals(player)) {
                target.sendMessage(ChatColor.GRAY + "时间停止了...");
                // 缓慢255级使玩家无法移动
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 180, 255, false, false));
                // 挖掘疲劳使玩家无法挖掘
                target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 180, 255, false, false));
                // 跳跃提升-128使玩家无法跳跃
                target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 180, 128, false, false));
                // 虚弱使玩家无法攻击
                target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 180, 255, false, false));
            }
        }
        
        // 9秒后提示恢复
        Bukkit.getRegionScheduler().runDelayed(plugin, player.getLocation(), new java.util.function.Consumer<io.papermc.paper.threadedregions.scheduler.ScheduledTask>() {
            @Override
            public void accept(io.papermc.paper.threadedregions.scheduler.ScheduledTask task) {
                player.sendMessage(ChatColor.GREEN + "时间开始流动...");
            }
        }, 180L);
    }
    
    /**
     * 护盾发生器 - 生成5秒可吸收8点生命值的护盾
     */
    private void useShieldGenerator(Player player) {
        player.sendMessage(ChatColor.AQUA + "护盾已生成！");
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f);
        
        // 给予玩家吸收8点生命值（4颗心）
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 100, 3, false, false));
        // 给予玩家抗性提升，小幅提升抗击退能力
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 0, false, false));
        
        // 5秒后提示护盾消失
        Bukkit.getRegionScheduler().runDelayed(plugin, player.getLocation(), new java.util.function.Consumer<io.papermc.paper.threadedregions.scheduler.ScheduledTask>() {
            @Override
            public void accept(io.papermc.paper.threadedregions.scheduler.ScheduledTask task) {
                if (player.isOnline()) {
                    player.sendMessage(ChatColor.GRAY + "护盾已消失");
                    player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.0f);
                }
            }
        }, 100L);
    }
}
