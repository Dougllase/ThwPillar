package com.newpillar.game.items;

import com.newpillar.NewPillar;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
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

        Bukkit.getRegionScheduler().execute(plugin, spawnLoc, () -> {
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

        Bukkit.getRegionScheduler().execute(plugin, spawnLoc, () -> {
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

        Bukkit.getRegionScheduler().execute(plugin, spawnLoc, () -> {
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
        Bukkit.getRegionScheduler().execute(plugin, finalPlaceLoc, () -> {
            // 检查位置是否适合放置（只需上方是空气即可，下方可以是任意方块）
            Block block = finalPlaceLoc.getBlock();
            Block below = block.getRelative(BlockFace.DOWN);

            if (block.getType() == Material.AIR && below.getType() != Material.AIR) {
                // 使用实体方式生成末地水晶，这样可以在任意方块上放置
                EnderCrystal crystal = (EnderCrystal) player.getWorld().spawnEntity(finalPlaceLoc, EntityType.END_CRYSTAL);
                if (crystal != null) {
                    player.getWorld().playSound(finalPlaceLoc, Sound.BLOCK_GLASS_PLACE, 1.0f, 1.0f);

                    // 消耗物品
                    Bukkit.getRegionScheduler().run(plugin, player.getLocation(), task -> consumeItem(player));
                }
            } else {
                Bukkit.getRegionScheduler().run(plugin, player.getLocation(), task ->
                        player.sendMessage(ChatColor.RED + "无法在此处放置末地水晶！"));
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
        // 清理资源（如果需要）
    }
}
