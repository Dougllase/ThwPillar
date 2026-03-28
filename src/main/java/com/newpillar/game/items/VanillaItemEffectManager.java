package com.newpillar.game.items;

import com.newpillar.NewPillar;
import com.newpillar.utils.GameConstants;
import com.newpillar.utils.SchedulerUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * 原版物品效果管理器 - 处理移出特殊物品的原版物品效果
 */
public class VanillaItemEffectManager {
    private final NewPillar plugin;
    private final VanillaItemManager vanillaItemManager;
    private final Random random = new Random();
    private final Map<UUID, BossBar> dragonBossBars = new HashMap<>();

    public VanillaItemEffectManager(NewPillar plugin, VanillaItemManager vanillaItemManager) {
        this.plugin = plugin;
        this.vanillaItemManager = vanillaItemManager;
    }

    /**
     * 处理玩家右键使用原版物品
     */
    public void onPlayerInteract(Player player, ItemStack item) {
        if (item == null) return;

        VanillaItemManager.VanillaItemType type = vanillaItemManager.getVanillaItemType(item);
        if (type != null) {
            // 特殊物品按类型处理
            switch (type) {
                case ECHO_SHARD -> useEchoShard(player);
                case DRAGON_BREATH -> useDragonBreath(player);
                case TNT -> useTNT(player);
                case FIRE_CHARGE -> useFireCharge(player);
                case END_CRYSTAL -> useEndCrystal(player);
                case ENCHANTED_BOOK -> useEnchantedBook(player);
                case DRAGON_EGG -> useDragonEgg(player);
            }
        } else {
            // 普通物品也支持右键使用
            Material material = item.getType();
            switch (material) {
                case FIRE_CHARGE -> useFireCharge(player);
                case TNT -> useTNT(player);
                case DRAGON_BREATH -> useDragonBreath(player);
                default -> {} // 其他普通物品不处理
            }
        }
    }

    // ==================== 原版物品效果 ====================

    private void useEchoShard(Player player) {
        // 末影碎片：随机传送
        Location loc = player.getLocation();
        World world = player.getWorld();

        // 随机传送范围：-50 到 50
        int x = loc.getBlockX() + random.nextInt(100) - 50;
        int z = loc.getBlockZ() + random.nextInt(100) - 50;
        int y = world.getHighestBlockYAt(x, z);

        Location targetLoc = new Location(world, x + 0.5, y + 1, z + 0.5);

        player.teleportAsync(targetLoc).thenAccept(success -> {
            if (success) {
                player.sendMessage(ChatColor.DARK_PURPLE + "末影碎片将你传送到了新的位置！");
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation(), 50, 1, 1, 1, 0.5);

                // 消耗物品
                consumeItem(player);
            }
        });
    }

    private void useDragonBreath(Player player) {
        // 龙息：发射龙息弹
        Location eyeLoc = player.getEyeLocation();
        Vector direction = eyeLoc.getDirection();

        Location spawnLoc = eyeLoc.clone().add(direction.clone().multiply(2));

        SchedulerUtils.runOnLocation(spawnLoc, () -> {
            DragonFireball fireball = (DragonFireball) player.getWorld().spawnEntity(spawnLoc, EntityType.DRAGON_FIREBALL);
            if (fireball != null) {
                fireball.setVelocity(direction.multiply(1.5));
                fireball.setShooter(player);
            }
        });

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_SHOOT, 1.0f, 1.0f);

        // 消耗物品
        consumeItem(player);
    }

    private void useTNT(Player player) {
        // TNT：发射点燃的TNT（类似火焰弹）
        Location eyeLoc = player.getEyeLocation();
        Vector direction = eyeLoc.getDirection();

        Location spawnLoc = eyeLoc.clone().add(direction.clone().multiply(2));

        SchedulerUtils.runOnLocation(spawnLoc, () -> {
            TNTPrimed tnt = (TNTPrimed) player.getWorld().spawnEntity(spawnLoc, EntityType.TNT);
            if (tnt != null) {
                tnt.setVelocity(direction.multiply(1.2)); // 适中的速度
                tnt.setFuseTicks(80); // 4秒引线
                tnt.setSource(player); // 设置来源，用于计分板击杀统计
            }
        });

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_TNT_PRIMED, 1.0f, 1.0f);

        // 消耗物品
        consumeItem(player);
    }

    private void useFireCharge(Player player) {
        // 火球：发射火球（使用Fireball大球，可被打回）
        Location eyeLoc = player.getEyeLocation();
        Vector direction = eyeLoc.getDirection();

        Location spawnLoc = eyeLoc.clone().add(direction.clone().multiply(2));

        SchedulerUtils.runOnLocation(spawnLoc, () -> {
            // 使用Fireball（大火球）而不是SmallFireball，可以被玩家打回
            Fireball fireball = (Fireball) player.getWorld().spawnEntity(spawnLoc, EntityType.FIREBALL);
            if (fireball != null) {
                fireball.setVelocity(direction.multiply(0.8)); // 降低速度，更容易被打回
                fireball.setShooter(player);
                fireball.setYield(1.0f); // 爆炸威力
                fireball.setIsIncendiary(true); // 可以引燃方块
            }
        });

        player.getWorld().playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1.0f, 1.0f);

        // 消耗物品
        consumeItem(player);
    }

    private void useEndCrystal(Player player) {
        // 末地水晶：放置（可在任意方块上放置）
        // 获取玩家看向的方块
        Block targetBlock = player.getTargetBlockExact(100);

        Location placeLoc;
        if (targetBlock == null || targetBlock.getType() == Material.AIR) {
            // 对空放置 - 放置在距离等于玩家最远实体交互范围的位置
            double interactionRange = player.getAttribute(org.bukkit.attribute.Attribute.ENTITY_INTERACTION_RANGE) != null
                    ? player.getAttribute(org.bukkit.attribute.Attribute.ENTITY_INTERACTION_RANGE).getValue()
                    : 3.0;

            Location eyeLoc = player.getEyeLocation();
            Vector direction = eyeLoc.getDirection();
            placeLoc = eyeLoc.clone().add(direction.multiply(interactionRange));
        } else {
            // 放置在目标方块上方
            placeLoc = targetBlock.getLocation().add(0.5, 1, 0.5);
        }

        Location finalPlaceLoc = placeLoc;
        SchedulerUtils.runOnLocation(finalPlaceLoc, () -> {
            // 检查位置是否适合放置（只需上方是空气即可，下方可以是任意方块）
            Block block = finalPlaceLoc.getBlock();
            Block below = block.getRelative(BlockFace.DOWN);

            if (block.getType() == Material.AIR && below.getType() != Material.AIR) {
                // 使用实体方式生成末地水晶，这样可以在任意方块上放置
                EnderCrystal crystal = (EnderCrystal) player.getWorld().spawnEntity(finalPlaceLoc, EntityType.END_CRYSTAL);
                if (crystal != null) {
                    player.getWorld().playSound(finalPlaceLoc, Sound.BLOCK_GLASS_PLACE, 1.0f, 1.0f);

                    // 消耗物品
                    SchedulerUtils.runOnPlayer(player, () -> consumeItem(player));
                }
            } else {
                SchedulerUtils.runOnPlayer(player, () -> player.sendMessage(ChatColor.RED + "无法在此处放置末地水晶！"));
            }
        });
    }

    private void useEnchantedBook(Player player) {
        // 附魔书：将书中附魔给予副手物品
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        if (mainHand.getType() != Material.ENCHANTED_BOOK) {
            player.sendMessage(ChatColor.RED + "主手必须持有附魔书！");
            return;
        }

        if (offHand.getType() == Material.AIR) {
            player.sendMessage(ChatColor.RED + "副手必须持有要附魔的物品！");
            return;
        }

        ItemMeta meta = mainHand.getItemMeta();
        if (!(meta instanceof EnchantmentStorageMeta)) {
            player.sendMessage(ChatColor.RED + "这不是有效的附魔书！");
            return;
        }

        EnchantmentStorageMeta enchantMeta = (EnchantmentStorageMeta) meta;
        Map<Enchantment, Integer> storedEnchants = enchantMeta.getStoredEnchants();

        if (storedEnchants.isEmpty()) {
            player.sendMessage(ChatColor.RED + "这本附魔书没有附魔！");
            return;
        }

        // 将附魔应用到副手物品
        ItemStack offHandItem = offHand.clone();
        boolean applied = false;

        for (Map.Entry<Enchantment, Integer> entry : storedEnchants.entrySet()) {
            Enchantment enchant = entry.getKey();
            int level = entry.getValue();

            try {
                offHandItem.addEnchantment(enchant, level);
                applied = true;
            } catch (IllegalArgumentException e) {
                // 该附魔不能应用到此物品
                player.sendMessage(ChatColor.YELLOW + "无法应用附魔: " + enchant.getKey().getKey());
            }
        }

        if (applied) {
            player.getInventory().setItemInOffHand(offHandItem);
            player.sendMessage(ChatColor.GREEN + "附魔成功！");
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);

            // 消耗附魔书
            consumeItem(player);
        } else {
            player.sendMessage(ChatColor.RED + "无法将任何附魔应用到副手物品！");
        }
    }

    // ==================== 龙蛋效果 ====================

    private void useDragonEgg(Player player) {
        // 龙蛋：召唤末影龙攻击其他玩家
        // 在玩家当前位置生成（风险和机遇并存）
        Location spawnLoc = player.getLocation().clone();

        SchedulerUtils.runOnLocation(spawnLoc, () -> {
            EnderDragon dragon = (EnderDragon) player.getWorld().spawnEntity(spawnLoc, EntityType.ENDER_DRAGON);
            if (dragon != null) {
                // 设置末影龙属性
                dragon.setPhase(org.bukkit.entity.EnderDragon.Phase.CIRCLING); // 设置为盘旋阶段，立即开始行动

                // 存储召唤者UUID，用于识别不攻击的玩家
                dragon.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "dragon_summoner"),
                    PersistentDataType.STRING,
                    player.getUniqueId().toString()
                );

                // 创建模拟BossBar
                BossBar bossBar = Bukkit.createBossBar(
                    ChatColor.DARK_PURPLE + "末影龙 (召唤者: " + player.getName() + ")",
                    BarColor.PURPLE,
                    BarStyle.SEGMENTED_10
                );
                bossBar.setProgress(1.0);

                // 为所有在线玩家显示BossBar
                for (Player p : Bukkit.getOnlinePlayers()) {
                    bossBar.addPlayer(p);
                }

                // 存储BossBar引用
                dragonBossBars.put(dragon.getUniqueId(), bossBar);

                // 播放音效和粒子效果
                player.getWorld().playSound(spawnLoc, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 1.0f);
                player.getWorld().spawnParticle(Particle.DRAGON_BREATH, spawnLoc, 100, 2, 2, 2, 0.1);

                player.sendMessage(ChatColor.DARK_PURPLE + "末影龙已被召唤！它将攻击你的敌人！");

                // 启动末影龙AI任务，使其主动攻击其他玩家
                startDragonAttackTask(dragon, player);

                // 消耗龙蛋
                SchedulerUtils.runOnPlayer(player, () -> consumeItem(player));
            }
        });
    }

    /**
     * 启动末影龙攻击任务
     * 使末影龙主动寻找并攻击除召唤者外的其他玩家
     */
    private void startDragonAttackTask(EnderDragon dragon, Player summoner) {
        // 使用全局调度器定期检查并更新末影龙目标
        final int[] ticks = {0};
        SchedulerUtils.runTimerGlobal(1L, 1L, task -> {
            if (!dragon.isValid() || dragon.isDead()) {
                // 清理BossBar
                removeDragonBossBar(dragon.getUniqueId());
                SchedulerUtils.cancel(task);
                return;
            }

            ticks[0]++;

            // 每5tick更新一次BossBar血量
            if (ticks[0] % 5 == 0) {
                updateDragonBossBar(dragon);
            }

            // 每20tick（1秒）更新一次目标
            if (ticks[0] % 20 == 0) {
                // 寻找最近的非召唤者玩家作为目标
                Player target = findNearestPlayerForDragon(dragon, summoner);
                if (target != null) {
                    // 设置末影龙攻击目标
                    dragon.setTarget(target);

                    // 使用RegionScheduler在目标位置执行攻击逻辑
                    SchedulerUtils.runOnEntity(target, () -> {
                        if (dragon.isValid() && !dragon.isDead()) {
                            // 向目标发射龙息弹
                            Location dragonLoc = dragon.getLocation();
                            Location targetLoc = target.getLocation();
                            Vector direction = targetLoc.toVector().subtract(dragonLoc.toVector()).normalize();

                            Location fireballLoc = dragonLoc.clone().add(direction.multiply(3));
                            DragonFireball fireball = (DragonFireball) dragon.getWorld().spawnEntity(fireballLoc, EntityType.DRAGON_FIREBALL);
                            if (fireball != null) {
                                fireball.setVelocity(direction.multiply(1.5));
                                fireball.setShooter(dragon);
                            }
                        }
                    });
                } else {
                    // 没有目标时清除目标
                    dragon.setTarget(null);
                }
            }

            // 60秒后移除末影龙（1200 ticks）
            if (ticks[0] >= GameConstants.TICKS_PER_SECOND * GameConstants.SECONDS_PER_MINUTE) {
                if (dragon.isValid() && !dragon.isDead()) {
                    dragon.getWorld().spawnParticle(Particle.DRAGON_BREATH, dragon.getLocation(), 50, 2, 2, 2, 0.1);
                    dragon.remove();
                }
                // 清理BossBar
                removeDragonBossBar(dragon.getUniqueId());
                SchedulerUtils.cancel(task);
            }
        });
    }

    /**
     * 更新末影龙BossBar血量
     */
    private void updateDragonBossBar(EnderDragon dragon) {
        BossBar bossBar = dragonBossBars.get(dragon.getUniqueId());
        if (bossBar != null && dragon.isValid() && !dragon.isDead()) {
            double health = dragon.getHealth();
            double maxHealth = dragon.getMaxHealth();
            double progress = Math.max(0.0, Math.min(1.0, health / maxHealth));
            bossBar.setProgress(progress);

            // 根据血量改变颜色
            if (progress > 0.6) {
                bossBar.setColor(BarColor.PURPLE);
            } else if (progress > 0.3) {
                bossBar.setColor(BarColor.YELLOW);
            } else {
                bossBar.setColor(BarColor.RED);
            }
        }
    }

    /**
     * 移除末影龙BossBar
     */
    private void removeDragonBossBar(UUID dragonUUID) {
        BossBar bossBar = dragonBossBars.remove(dragonUUID);
        if (bossBar != null) {
            bossBar.removeAll();
        }
    }

    /**
     * 为末影龙寻找最近的非召唤者玩家
     */
    private Player findNearestPlayerForDragon(EnderDragon dragon, Player summoner) {
        Player nearest = null;
        double minDistance = Double.MAX_VALUE;

        for (Player p : dragon.getWorld().getPlayers()) {
            if (p.equals(summoner) || !p.isOnline() || p.isDead()) {
                continue;
            }

            // 检查玩家是否在游戏中（不是观察者）
            if (p.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }

            double distance = p.getLocation().distanceSquared(dragon.getLocation());
            if (distance < minDistance) {
                minDistance = distance;
                nearest = p;
            }
        }

        return nearest;
    }

    // ==================== 工具方法 ====================

    private void consumeItem(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
    }

    public void cleanup() {
        // 清理所有BossBar
        for (BossBar bossBar : dragonBossBars.values()) {
            bossBar.removeAll();
        }
        dragonBossBars.clear();
    }
}
