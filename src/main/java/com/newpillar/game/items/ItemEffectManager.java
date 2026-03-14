package com.newpillar.game.items;

import com.newpillar.NewPillar;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
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
        }, 1L, 1L); // 延迟1tick开始，每tick更新一次
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
        // 使用新的冷却系统
        Long cooldownSeconds = getCooldownSeconds(type);
        if (cooldownSeconds != null && cooldownSeconds > 0) {
            String itemId = type.name().toLowerCase();
            String itemName = getItemDisplayName(type);
            
            // 设置冷却
            plugin.getItemCooldownManager().setCooldown(player, itemId);
            
            // 获取BossBar颜色
            net.kyori.adventure.bossbar.BossBar.Color bossBarColor = getBossBarColorForItem(type);
            
            // 显示BossBar冷却
            plugin.getItemCooldownManager().startCooldownDisplay(
                player, itemId, itemName, cooldownSeconds, bossBarColor
            );
        }
    }
    
    /**
     * 获取物品对应的BossBar颜色
     */
    private net.kyori.adventure.bossbar.BossBar.Color getBossBarColorForItem(SpecialItemManager.SpecialItemType type) {
        return switch (type) {
            case BLUE_SCREEN -> net.kyori.adventure.bossbar.BossBar.Color.BLUE;
            case HYPNOSIS_APP -> net.kyori.adventure.bossbar.BossBar.Color.PURPLE;
            case THE_WORLD -> net.kyori.adventure.bossbar.BossBar.Color.PINK;
            case EX_CURRY_STICK -> net.kyori.adventure.bossbar.BossBar.Color.RED;
            case SHIELD_GENERATOR -> net.kyori.adventure.bossbar.BossBar.Color.GREEN;
            case PIXIE -> net.kyori.adventure.bossbar.BossBar.Color.PINK;
            case CARD -> net.kyori.adventure.bossbar.BossBar.Color.WHITE;
            case HONGBAO -> net.kyori.adventure.bossbar.BossBar.Color.RED;
            case CLOCK -> net.kyori.adventure.bossbar.BossBar.Color.PINK;
            case BRUCE -> net.kyori.adventure.bossbar.BossBar.Color.GREEN;
            case FLY_MACE -> net.kyori.adventure.bossbar.BossBar.Color.BLUE;
            case KNOCKBACK_STICK -> net.kyori.adventure.bossbar.BossBar.Color.BLUE;
            default -> net.kyori.adventure.bossbar.BossBar.Color.WHITE;
        };
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
     * 获取物品显示名称
     */
    private String getItemDisplayName(SpecialItemManager.SpecialItemType type) {
        return switch (type) {
            case KNOCKBACK_STICK -> "击退棒";
            case SPEAR -> "长♂矛";
            case BONES_WITHOUT_CHICKEN_FEET -> "有骨无鸡爪";
            case IRON_SWORD -> "铁剑";
            case CARD -> "牌";
            case MEOW_AXE -> "喵人斧";
            case BIG_FLAME_ROD -> "大火杆";
            case ROCKET_BOOTS -> "火箭靴";
            case RUNNING_SHOES -> "跑鞋";
            case FEATHER -> "羽毛";
            case INVISIBLE_SAND -> "隐身沙";
            case GRAVITY_BOOTS -> "重力靴";
            case LIFE_STEAL_SWORD -> "吸血剑";
            case POISON_DAGGER -> "淬毒匕首";
            case SPECIAL_BOW -> "神弓";
            case SPECIAL_CROSSBOW -> "神弩";
            case PIXIE -> "皮鞋";
            case BLUE_SCREEN -> "蓝屏";
            case HONGBAO -> "红包";
            case HYPNOSIS_APP -> "催眠APP";
            case EX_CURRY_STICK -> "EX咖喱棒";
            case THE_WORLD -> "砸瓦鲁多";
            case SHIELD_GENERATOR -> "护盾发生器";
            case CLOCK -> "时钟";
            case BRUCE -> "布鲁斯";
            case WITCH_APPLE -> "女巫的红苹果";
            case FLY_MACE -> "让你飞起来";
            default -> "未知物品";
        };
    }

    /**
     * 检查物品是否受限制（冷却、范围检查等）
     * @return true 如果可以使用, false 如果被限制
     */
    private boolean checkItemRestrictions(Player player, SpecialItemManager.SpecialItemType type) {
        // 获取物品冷却时间（秒）
        Long cooldownSeconds = getCooldownSeconds(type);
        String itemName = getItemDisplayName(type);
        String itemId = type.name().toLowerCase();
        
        // 检查冷却（使用新的冷却系统）
        // 注意：自己管理冷却的物品（蓝屏、催眠APP等）在这里跳过冷却检查
        if (cooldownSeconds != null && cooldownSeconds > 0 && !managesOwnCooldown(type)) {
            if (plugin.getItemCooldownManager().isOnCooldown(player, itemId, cooldownSeconds)) {
                long remaining = plugin.getItemCooldownManager().getRemainingCooldown(player, itemId, cooldownSeconds);
                player.sendMessage(ChatColor.RED + "「" + itemName + "」冷却中，剩余 " + remaining + " 秒");
                return false;
            }
        }
        
        // 检查范围目标（需要范围内有玩家的物品）
        // 注意：自己管理目标的物品（蓝屏、催眠APP等）在这里跳过目标检查
        if (requiresTargetPlayer(type) && !managesOwnTarget(type)) {
            Player target = findNearestPlayer(player, 20);
            
            if (target == null) {
                player.sendMessage(ChatColor.RED + "附近没有其他玩家！无法使用" + itemName + "。");
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 检查物品是否自己管理冷却（在useXxx方法中处理）
     */
    private boolean managesOwnCooldown(SpecialItemManager.SpecialItemType type) {
        return switch (type) {
            case BLUE_SCREEN,    // 蓝屏
                 HYPNOSIS_APP,  // 催眠APP
                 THE_WORLD,     // 砸瓦鲁多
                 SHIELD_GENERATOR -> true; // 护盾发生器
            default -> false;
        };
    }
    
    /**
     * 检查物品是否自己管理目标检查（在useXxx方法中处理）
     */
    private boolean managesOwnTarget(SpecialItemManager.SpecialItemType type) {
        return switch (type) {
            case BLUE_SCREEN,    // 蓝屏
                 HYPNOSIS_APP,  // 催眠APP
                 THE_WORLD,     // 砸瓦鲁多
                 SHIELD_GENERATOR -> true; // 护盾发生器
            default -> false;
        };
    }
    
    /**
     * 获取物品的冷却时间（秒）
     */
    private Long getCooldownSeconds(SpecialItemManager.SpecialItemType type) {
        Long cooldownMillis = cooldownTimes.get(type);
        return cooldownMillis != null ? cooldownMillis / 1000 : null;
    }
    
    /**
     * 查找最近的玩家目标
     */
    private Player findNearestPlayer(Player player, double radius) {
        Player nearest = null;
        double minDistance = Double.MAX_VALUE;
        
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof Player && !entity.equals(player)) {
                double distance = entity.getLocation().distance(player.getLocation());
                if (distance < minDistance) {
                    minDistance = distance;
                    nearest = (Player) entity;
                }
            }
        }
        
        return nearest;
    }

    /**
     * 查找最近的其他玩家（排除指定玩家）
     * @param excludePlayer 要排除的玩家
     * @param location 中心位置
     * @param radius 搜索半径
     * @return 最近的其他玩家，如果没有则返回null
     */
    private Player findNearestOtherPlayer(Player excludePlayer, Location location, double radius) {
        Player nearest = null;
        double minDistance = Double.MAX_VALUE;
        
        for (Entity entity : location.getWorld().getNearbyEntities(location, radius, radius, radius)) {
            if (entity instanceof Player && !entity.equals(excludePlayer)) {
                double distance = entity.getLocation().distance(location);
                if (distance < minDistance) {
                    minDistance = distance;
                    nearest = (Player) entity;
                }
            }
        }
        
        return nearest;
    }
    
    /**
     * 检查物品是否需要范围内有玩家作为目标
     */
    private boolean requiresTargetPlayer(SpecialItemManager.SpecialItemType type) {
        return switch (type) {
            case HYPNOSIS_APP, // 催眠APP - 需要目标玩家
                 BLUE_SCREEN, // 蓝屏 - 需要目标玩家
                 PIXIE, // 皮鞋 - 需要目标玩家
                 THE_WORLD, // 砸瓦鲁多 - 冻结周围玩家
                 KNOCKBACK_STICK, // 击退棒 - 击退周围玩家
                 CARD -> true; // 牌 - 验牌击退周围玩家
            default -> false;
        };
    }

    public void onPlayerInteract(Player player, ItemStack item) {
        onPlayerInteract(player, item, null);
    }

    public void onPlayerInteract(Player player, ItemStack item, org.bukkit.block.Block clickedBlock) {
        if (item == null) return;

        SpecialItemManager.SpecialItemType type = specialItemManager.getSpecialItemType(item);
        if (type == null) return;

        // 火箭靴和跑鞋是装备类，不需要右键处理，直接返回让它们正常装备
        if (type == SpecialItemManager.SpecialItemType.ROCKET_BOOTS ||
            type == SpecialItemManager.SpecialItemType.RUNNING_SHOES) {
            return;
        }

        // 皮鞋特殊处理：右键时先检查目标和冷却
        if (type == SpecialItemManager.SpecialItemType.PIXIE) {
            // 检查是否有目标和冷却
            boolean success = usePixie(player);
            if (success) {
                grantItemAchievement(player, "pixie");
            }
            // 无论成功与否，都允许穿上（不取消事件）
            return;
        }

        // 检查物品限制（冷却和目标）
        if (!checkItemRestrictions(player, type)) {
            return;
        }

        // 检查是否需要消耗物品（消耗型物品）
        boolean shouldConsume = isConsumable(type);

        switch (type) {
            case KNOCKBACK_STICK -> {
                boolean success = useKnockbackStick(player);
                if (success) {
                    grantItemAchievement(player, "knockback_stick");
                } else {
                    shouldConsume = false;
                }
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
                // 铁剑没有对应的成就
            }
            case CARD -> {
                boolean success = useCard(player);
                if (success) {
                    grantItemAchievement(player, "yanpai");
                } else {
                    shouldConsume = false;
                }
            }
            case PIXIE -> {
                // 已在上面的装备类物品检查中处理，这里不会执行
                // usePixie(player);
                // grantItemAchievement(player, "pixie");
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
                // 蓝屏自己管理冷却和目标检查
                boolean success = useBlueScreen(player);
                if (success) {
                    grantItemAchievement(player, "blue_screen");
                } else {
                    // 使用失败（无目标或冷却中），不消耗物品
                    shouldConsume = false;
                }
            }
            case HONGBAO -> {
                useHongbao(player);
                grantItemAchievement(player, "hongbao");
            }
            case HYPNOSIS_APP -> {
                // 催眠APP自己管理冷却和目标检查
                boolean success = useHypnosisApp(player);
                if (success) {
                    grantItemAchievement(player, "hypnosis_app");
                } else {
                    shouldConsume = false;
                }
            }
            case CLOCK -> {
                useClock(player);
                grantItemAchievement(player, "clock");
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
                // 使用ExcaliburManager的实现
                plugin.getExcaliburManager().useExcalibur(player);
                grantItemAchievement(player, "ex_curry_stick");
            }
            case INVISIBLE_SAND -> grantItemAchievement(player, "invisible_sand");
            case FEATHER -> {
                // 羽毛没有对应的成就
            }
            case GODLY_PICKAXE -> grantItemAchievement(player, "godly_pickaxe");
            case THE_WORLD -> {
                boolean success = useTheWorld(player);
                if (success) {
                    // 砸瓦鲁多没有对应的成就
                } else {
                    shouldConsume = false;
                }
            }
            case SHIELD_GENERATOR -> {
                useShieldGenerator(player);
                grantItemAchievement(player, "shield_generator");
            }
            case RUSSIAN_ROULETTE -> {
                useRussianRoulette(player);
                grantItemAchievement(player, "russian_roulette");
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
                 BRUCE, // 布鲁斯
                 // EX_CURRY_STICK 不是消耗品，可以重复使用
                 THE_WORLD, // 砸瓦鲁多
                 SHIELD_GENERATOR -> true; // 护盾发生器
            default -> false;
        };
    }

    /**
     * 消耗玩家手中的物品
     * 支持主手和副手物品
     */
    private void consumeItem(Player player, ItemStack item) {
        // 减少物品数量
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            // 如果只有一个，检查是主手还是副手，然后从对应位置移除
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            ItemStack offHand = player.getInventory().getItemInOffHand();

            // 使用isSimilar来比较物品，因为传入的item可能是副本
            boolean isMainHand = mainHand != null && mainHand.isSimilar(item);
            boolean isOffHand = offHand != null && offHand.isSimilar(item);

            if (isMainHand) {
                player.getInventory().setItemInMainHand(null);
            } else if (isOffHand) {
                // 是副手物品
                player.getInventory().setItemInOffHand(null);
            } else {
                // 如果都无法匹配（理论上不应该发生），尝试从主手移除
                player.getInventory().setItemInMainHand(null);
            }
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

        // 向前冲刺 - 使用 RegionScheduler 确保线程安全
        Vector direction = player.getLocation().getDirection().normalize();
        Vector velocity = direction.multiply(1.5).setY(0.3);
        Bukkit.getRegionScheduler().execute(plugin, player.getLocation(), () -> {
            player.setVelocity(velocity);
        });
    }

    private boolean useKnockbackStick(Player player) {
        // 击退棒：击退周围玩家
        // 检查是否有目标（已经在checkItemRestrictions中检查，这里只是确认）
        boolean hasTarget = false;
        for (Entity entity : player.getNearbyEntities(3, 3, 3)) {
            if (entity instanceof Player && !entity.equals(player)) {
                hasTarget = true;
                break;
            }
        }
        
        if (!hasTarget) {
            player.sendMessage(ChatColor.RED + "附近没有其他玩家！无法使用击退棒。");
            return false;
        }
        
        player.sendMessage(ChatColor.GOLD + "吃我一棒！");
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.0f);

        // 击退周围玩家2格 - 使用 RegionScheduler 确保线程安全
        for (Entity entity : player.getNearbyEntities(3, 3, 3)) {
            if (entity instanceof Player && !entity.equals(player)) {
                Vector knockback = entity.getLocation().toVector()
                        .subtract(player.getLocation().toVector())
                        .normalize()
                        .multiply(2.0)
                        .setY(0.5);
                Bukkit.getRegionScheduler().execute(plugin, entity.getLocation(), () -> {
                    entity.setVelocity(knockback);
                });
                ((Player) entity).sendMessage(ChatColor.RED + "被" + player.getName() + "击退了！");
            }
        }
        return true;
    }

    private void useBone(Player player) {
        // 有骨无鸡爪：食用效果
        // 恢复3点生命值（1.5颗心）
        player.setHealth(Math.min(player.getHealth() + 3, player.getMaxHealth()));
        // 恢复4点饥饿值
        player.setFoodLevel(Math.min(player.getFoodLevel() + 4, 20));
        // 恢复2点饱和度
        player.setSaturation(Math.min(player.getSaturation() + 2, 20));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, 1.0f, 1.0f);
        player.sendMessage(ChatColor.GOLD + "嘎嘣脆！");
    }

    private boolean useCard(Player player) {
        // 牌：播报"我要验牌！"并击退周围玩家1格
        // 检查是否有目标（已经在checkItemRestrictions中检查，这里只是确认）
        boolean hasTarget = false;
        for (Entity entity : player.getNearbyEntities(5, 5, 5)) {
            if (entity instanceof Player && !entity.equals(player)) {
                hasTarget = true;
                break;
            }
        }
        
        if (!hasTarget) {
            player.sendMessage(ChatColor.RED + "附近没有其他玩家！无法使用牌。");
            return false;
        }
        
        Bukkit.broadcastMessage(ChatColor.GOLD + "【验牌】" + ChatColor.WHITE + player.getName() + ": " + ChatColor.YELLOW + "我要验牌！");
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.0f);

        // 击退周围玩家1格 - 使用 RegionScheduler 确保线程安全
        for (Entity entity : player.getNearbyEntities(5, 5, 5)) {
            if (entity instanceof Player && !entity.equals(player)) {
                Vector knockback = entity.getLocation().toVector()
                        .subtract(player.getLocation().toVector())
                        .normalize()
                        .multiply(1.0)
                        .setY(0.3);
                Bukkit.getRegionScheduler().execute(plugin, entity.getLocation(), () -> {
                    entity.setVelocity(knockback);
                });
                ((Player) entity).sendMessage(ChatColor.RED + "被" + player.getName() + "验牌击退！");
            }
        }
        return true;
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

    private boolean usePixie(Player player) {
        // 检查周围是否有其他玩家
        boolean hasTarget = false;
        for (Entity entity : player.getNearbyEntities(3, 3, 3)) {
            if (entity instanceof Player && !entity.equals(player)) {
                hasTarget = true;
                break;
            }
        }
        
        if (!hasTarget) {
            player.sendMessage(ChatColor.RED + "附近没有其他玩家！无法使用皮鞋。");
            return false;
        }
        
        // 使用新的冷却系统
        String itemId = "pixie";
        String itemName = "皮鞋";
        long cooldownSeconds = 60; // 60秒冷却
        
        // 检查冷却
        if (plugin.getItemCooldownManager().isOnCooldown(player, itemId, cooldownSeconds)) {
            long remaining = plugin.getItemCooldownManager().getRemainingCooldown(player, itemId, cooldownSeconds);
            player.sendMessage(ChatColor.RED + "「" + itemName + "」冷却中，剩余 " + remaining + " 秒");
            return false;
        }
        
        // 设置冷却并显示BossBar
        plugin.getItemCooldownManager().setCooldown(player, itemId);
        plugin.getItemCooldownManager().startCooldownDisplay(
            player, itemId, itemName, cooldownSeconds, net.kyori.adventure.bossbar.BossBar.Color.PINK
        );
        
        // 皮鞋：给我擦皮鞋！
        Bukkit.broadcastMessage(ChatColor.GOLD + "【系统】" + ChatColor.WHITE + player.getName() + ": 给我擦皮鞋！");
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);

        // 击退周围玩家0.5格 - 使用 RegionScheduler 确保线程安全
        for (Entity entity : player.getNearbyEntities(3, 3, 3)) {
            if (entity instanceof Player && !entity.equals(player)) {
                Vector knockback = entity.getLocation().toVector()
                        .subtract(player.getLocation().toVector())
                        .normalize()
                        .multiply(0.5)
                        .setY(0.2);
                Bukkit.getRegionScheduler().execute(plugin, entity.getLocation(), () -> {
                    entity.setVelocity(knockback);
                });
            }
        }
        
        return true;
    }

    // ==================== 特殊类物品 ====================

    private boolean useBlueScreen(Player player) {
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

        if (target == null) {
            // 没有有效目标，拒绝使用
            player.sendMessage(ChatColor.RED + "附近没有其他玩家！无法使用蓝屏。");
            return false;
        }

        // 使用新的冷却系统
        String itemId = "blue_screen";
        String itemName = "蓝屏";
        long cooldownSeconds = 10; // 10秒冷却
        
        // 检查冷却
        if (plugin.getItemCooldownManager().isOnCooldown(player, itemId, cooldownSeconds)) {
            long remaining = plugin.getItemCooldownManager().getRemainingCooldown(player, itemId, cooldownSeconds);
            player.sendMessage(ChatColor.RED + "「蓝屏」冷却中，剩余 " + remaining + " 秒");
            return false;
        }
        
        // 设置冷却并显示BossBar
        plugin.getItemCooldownManager().setCooldown(player, itemId);
        plugin.getItemCooldownManager().startCooldownDisplay(
            player, itemId, itemName, cooldownSeconds, net.kyori.adventure.bossbar.BossBar.Color.BLUE
        );
        
        {
            final Player finalTarget = target;
            player.sendMessage(ChatColor.BLUE + "对 " + target.getName() + " 使用蓝屏！");
            target.sendMessage(ChatColor.BLUE + "" + ChatColor.BOLD + "╔════════════════════════════════╗");
            target.sendMessage(ChatColor.BLUE + "" + ChatColor.BOLD + "║      WINDOWS ERROR             ║");
            target.sendMessage(ChatColor.BLUE + "" + ChatColor.BOLD + "║                                ║");
            target.sendMessage(ChatColor.BLUE + "" + ChatColor.BOLD + "║  :( Your PC ran into a problem ║");
            target.sendMessage(ChatColor.BLUE + "" + ChatColor.BOLD + "║  and needs to restart.         ║");
            target.sendMessage(ChatColor.BLUE + "" + ChatColor.BOLD + "╚════════════════════════════════╝");

            // 蓝屏效果：12秒内通过属性修改移动速度和跳跃力度
            // 添加属性修改器：移动速度设为-1（无法移动，基础值0.1 * (1-1) = 0）
            target.getAttribute(Attribute.MOVEMENT_SPEED).addModifier(
                new AttributeModifier(UUID.randomUUID(), "blue_screen_movement", -0.1,
                    AttributeModifier.Operation.ADD_NUMBER));
            // 添加属性修改器：跳跃力度设为-1（无法跳跃，基础值0.42 * (1-1) = 0）
            target.getAttribute(Attribute.JUMP_STRENGTH).addModifier(
                new AttributeModifier(UUID.randomUUID(), "blue_screen_jump", -0.42,
                    AttributeModifier.Operation.ADD_NUMBER));
            // 挖掘疲劳（无法挖掘）
            target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 240, 4, false, false));
            // 失明
            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 240, 0, false, false));
            // 虚弱（无法攻击）
            target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 240, 4, false, false));

            // 播放蓝屏音效
            target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);

            // 12秒后解除属性修改和效果
            final UUID movementModifierUuid = target.getAttribute(Attribute.MOVEMENT_SPEED).getModifiers().stream()
                .filter(m -> m.getName().equals("blue_screen_movement"))
                .findFirst().map(AttributeModifier::getUniqueId).orElse(null);
            final UUID jumpModifierUuid = target.getAttribute(Attribute.JUMP_STRENGTH).getModifiers().stream()
                .filter(m -> m.getName().equals("blue_screen_jump"))
                .findFirst().map(AttributeModifier::getUniqueId).orElse(null);
            
            final Player finalTarget2 = target;
            Bukkit.getRegionScheduler().runDelayed(plugin, target.getLocation(), new java.util.function.Consumer<io.papermc.paper.threadedregions.scheduler.ScheduledTask>() {
                @Override
                public void accept(io.papermc.paper.threadedregions.scheduler.ScheduledTask task) {
                    if (finalTarget2.isOnline()) {
                        // 移除属性修改器
                        if (movementModifierUuid != null) {
                            finalTarget2.getAttribute(Attribute.MOVEMENT_SPEED).removeModifier(movementModifierUuid);
                        }
                        if (jumpModifierUuid != null) {
                            finalTarget2.getAttribute(Attribute.JUMP_STRENGTH).removeModifier(jumpModifierUuid);
                        }
                        finalTarget2.sendMessage(ChatColor.GREEN + "系统已恢复！");
                        finalTarget2.playSound(finalTarget2.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
                    }
                }
            }, 240L);
        }
        return true;
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

    private boolean useHypnosisApp(Player player) {
        // 催眠APP：随机催眠一名玩家
        List<Player> targets = new ArrayList<>();
        for (Entity entity : player.getNearbyEntities(20, 20, 20)) {
            if (entity instanceof Player && !entity.equals(player)) {
                targets.add((Player) entity);
            }
        }

        if (targets.isEmpty()) {
            player.sendMessage(ChatColor.RED + "附近没有其他玩家！无法使用催眠APP。");
            return false;
        }
        
        // 使用新的冷却系统
        String itemId = "hypnosis_app";
        String itemName = "催眠APP";
        long cooldownSeconds = 30; // 30秒冷却
        
        // 检查冷却
        if (plugin.getItemCooldownManager().isOnCooldown(player, itemId, cooldownSeconds)) {
            long remaining = plugin.getItemCooldownManager().getRemainingCooldown(player, itemId, cooldownSeconds);
            player.sendMessage(ChatColor.RED + "「" + itemName + "」冷却中，剩余 " + remaining + " 秒");
            return false;
        }
        
        // 设置冷却并显示BossBar
        plugin.getItemCooldownManager().setCooldown(player, itemId);
        plugin.getItemCooldownManager().startCooldownDisplay(
            player, itemId, itemName, cooldownSeconds, net.kyori.adventure.bossbar.BossBar.Color.PURPLE
        );

        Player target = targets.get(random.nextInt(targets.size()));
        player.sendMessage(ChatColor.LIGHT_PURPLE + "对 " + target.getName() + " 使用催眠APP！");
        target.sendMessage(ChatColor.LIGHT_PURPLE + "你被 " + player.getName() + " 催眠了！");

        // 催眠效果：缓慢I 10s，挖掘疲劳III 15s，反胃I 30s（加强效果）
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 0)); // 缓慢I 10s
        target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 300, 2)); // 挖掘疲劳III 15s
        target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 600, 0)); // 反胃I 30s（加强）
        
        return true;
    }

    private void useClock(Player player) {
        // 时钟：切换白天/夜晚
        // 注意：在Folia中，修改世界时间需要在全局调度器上执行
        World world = player.getWorld();
        long currentTime = world.getTime();
        if (currentTime > 12000) {
            // 当前是夜晚，切换到白天
            Bukkit.getGlobalRegionScheduler().run(this.plugin, task -> {
                world.setTime(1000);
            });
            Bukkit.broadcastMessage(ChatColor.YELLOW + player.getName() + " 使用时钟将时间切换到白天！");
        } else {
            // 当前是白天，切换到夜晚
            Bukkit.getGlobalRegionScheduler().run(this.plugin, task -> {
                world.setTime(13000);
            });
            Bukkit.broadcastMessage(ChatColor.BLUE + player.getName() + " 使用时钟将时间切换到夜晚！");
        }
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.0f);
    }

    private void useBruce(Player player) {
        // 布鲁斯：召唤一只狼 - 使用 RegionScheduler 确保线程安全
        player.sendMessage(ChatColor.GRAY + "召唤布鲁斯！");
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WOLF_AMBIENT, 1.0f, 1.0f);

        Bukkit.getRegionScheduler().execute(plugin, player.getLocation(), () -> {
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
        });
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

        // 向上发射 (Y轴速度 1.3，与数据包一致) - 使用 RegionScheduler 确保线程安全
        Vector velocity = player.getVelocity();
        velocity.setY(1.3);
        Bukkit.getRegionScheduler().execute(plugin, player.getLocation(), () -> {
            player.setVelocity(velocity);
        });
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
    private boolean useTheWorld(Player player) {
        // 检查是否有目标（已经在checkItemRestrictions中检查，这里只是确认）
        boolean hasTarget = false;
        for (Entity entity : player.getNearbyEntities(10, 10, 10)) {
            if (entity instanceof Player && !entity.equals(player)) {
                hasTarget = true;
                break;
            }
        }
        
        if (!hasTarget) {
            player.sendMessage(ChatColor.RED + "附近没有其他玩家！无法使用砸瓦鲁多。");
            return false;
        }
        
        player.sendMessage(ChatColor.GOLD + "The World!!!");
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BELL_USE, 1.0f, 1.0f);

        // 冻结周围10格内的所有玩家9秒（180 ticks）
        for (Entity entity : player.getNearbyEntities(10, 10, 10)) {
            if (entity instanceof Player target && !target.equals(player)) {
                target.sendMessage(ChatColor.GRAY + "时间停止了...");

                // 记录被冻结时的视角（位置和朝向）
                final Location frozenLoc = target.getLocation().clone();

                // 缓慢255级使玩家无法移动
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 180, 255, false, false));
                // 挖掘疲劳使玩家无法挖掘
                target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 180, 255, false, false));
                // 虚弱使玩家无法攻击
                target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 180, 255, false, false));

                // 使用属性修改器降低跳跃力度（而不是跳跃提升-128）
                UUID jumpModifierUUID = UUID.randomUUID();
                AttributeModifier jumpModifier = new AttributeModifier(
                    jumpModifierUUID,
                    "the_world_jump",
                    -0.42, // 将跳跃力度降为0
                    AttributeModifier.Operation.ADD_NUMBER
                );
                target.getAttribute(Attribute.JUMP_STRENGTH).addModifier(jumpModifier);

                // 锁定视角任务 - 每tick强制玩家回到冻结位置和朝向
                final ScheduledTask[] lockTask = new ScheduledTask[1];
                lockTask[0] = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, new java.util.function.Consumer<io.papermc.paper.threadedregions.scheduler.ScheduledTask>() {
                    @Override
                    public void accept(io.papermc.paper.threadedregions.scheduler.ScheduledTask task) {
                        if (!target.isOnline()) {
                            task.cancel();
                            return;
                        }
                        // 强制玩家位置和朝向
                        Bukkit.getRegionScheduler().run(plugin, target.getLocation(), regionTask -> {
                            if (target.isOnline()) {
                                target.teleport(frozenLoc);
                            }
                        });
                    }
                }, 1L, 1L);

                // 9秒后解除效果
                final UUID finalJumpModifierUUID = jumpModifierUUID;
                Bukkit.getRegionScheduler().runDelayed(plugin, target.getLocation(), new java.util.function.Consumer<io.papermc.paper.threadedregions.scheduler.ScheduledTask>() {
                    @Override
                    public void accept(io.papermc.paper.threadedregions.scheduler.ScheduledTask task) {
                        if (target.isOnline()) {
                            // 移除跳跃力度修改器
                            target.getAttribute(Attribute.JUMP_STRENGTH).removeModifier(finalJumpModifierUUID);
                            target.sendMessage(ChatColor.GREEN + "时间开始流动...");
                        }
                        // 取消视角锁定任务
                        if (lockTask[0] != null) {
                            lockTask[0].cancel();
                        }
                    }
                }, 180L);
            }
        }

        // 9秒后提示使用者
        Bukkit.getRegionScheduler().runDelayed(plugin, player.getLocation(), new java.util.function.Consumer<ScheduledTask>() {
            @Override
            public void accept(ScheduledTask task) {
                player.sendMessage(ChatColor.GREEN + "时间开始流动...");
            }
        }, 180L);
        return true;
    }
    
    /**
     * 护盾发生器 - 生成5秒可吸收8点生命值的护盾，并提供击退抗性
     */
    private void useShieldGenerator(Player player) {
        player.sendMessage(ChatColor.AQUA + "护盾已生成！");
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f);
        
        // 给予玩家吸收8点生命值（4颗心）
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 100, 3, false, false));
        
        // 给予玩家击退抗性（100%免疫击退）
        UUID modifierUUID = UUID.randomUUID();
        AttributeModifier knockbackModifier = new AttributeModifier(
            modifierUUID,
            "shield_generator_knockback",
            1.0, // 100%击退抗性
            AttributeModifier.Operation.ADD_NUMBER
        );
        player.getAttribute(Attribute.KNOCKBACK_RESISTANCE).addModifier(knockbackModifier);
        
        // 5秒后移除护盾效果
        Bukkit.getRegionScheduler().runDelayed(plugin, player.getLocation(), new java.util.function.Consumer<io.papermc.paper.threadedregions.scheduler.ScheduledTask>() {
            @Override
            public void accept(io.papermc.paper.threadedregions.scheduler.ScheduledTask task) {
                if (player.isOnline()) {
                    // 移除击退抗性
                    player.getAttribute(Attribute.KNOCKBACK_RESISTANCE).removeModifier(modifierUUID);
                    
                    player.sendMessage(ChatColor.GRAY + "护盾已消失");
                    player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.0f);
                }
            }
        }, 100L);
    }
    
    /**
     * 俄罗斯轮盘枪 - 打开选择界面
     */
    private void useRussianRoulette(Player player) {
        // 打开俄罗斯轮盘选择界面
        plugin.getRussianRouletteGUI().openRouletteGUI(player);
    }
}
