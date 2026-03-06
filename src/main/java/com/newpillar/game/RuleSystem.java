package com.newpillar.game;

import com.newpillar.NewPillar;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fox;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Wolf;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class RuleSystem implements Listener {
    private final NewPillar plugin;
    private final GameManager gameManager;
    private RuleType currentRule = RuleType.NONE;
    private final Map<UUID, UUID> playerFoxes = new HashMap<>(); // 玩家UUID -> 狐狸UUID
    private ScheduledTask partnerTask;
    private final NamespacedKey partnerTagKey;
    
    // 虚空的仁慈冷却机制
    private final Map<UUID, Long> voidMercyCooldowns = new HashMap<>();
    private static final long VOID_MERCY_COOLDOWN_MS = 60 * 1000; // 1分钟冷却

    public RuleSystem(NewPillar plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.partnerTagKey = new NamespacedKey(plugin, "rule_partner");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void setRule(RuleType ruleType) {
        this.currentRule = ruleType;
    }

    public RuleType getCurrentRule() {
        return this.currentRule;
    }

    public void applyRuleToPlayer(Player player) {
        if (currentRule == RuleType.NONE) return;

        switch (currentRule) {
            case SMALL_CUTE:
                applySmallCute(player);
                break;
            case BIG:
                applyBig(player);
                break;
            case MY_PARTNER:
                giveFoxPartner(player);
                break;
            case PUNCH:
                applyPunch(player);
                break;
            case VOID_MERCY:
                // 虚空仁慈通过事件监听处理
                break;
            default:
                break;
        }
    }

    public void start() {
        if (currentRule == RuleType.MY_PARTNER) {
            startPartnerTask();
        }
    }

    public void stop() {
        if (partnerTask != null) {
            partnerTask.cancel();
            partnerTask = null;
        }

        // 不再清理狐狸，让它们继续存在
        playerFoxes.clear();
        
        // 清理虚空的仁慈冷却记录
        voidMercyCooldowns.clear();
    }

    // 规则1: 小小的也很可爱 - 体型 0.33
    private void applySmallCute(Player player) {
        AttributeInstance scaleAttr = player.getAttribute(Attribute.SCALE);
        if (scaleAttr != null) {
            scaleAttr.setBaseValue(0.33);
        }
        player.sendMessage("§d§l规则: §r§d小小的也很可爱❤ - 你的体型缩小为原来的1/3");
    }

    // 规则2: 大！大！大！ - 体型 1.5
    private void applyBig(Player player) {
        AttributeInstance scaleAttr = player.getAttribute(Attribute.SCALE);
        if (scaleAttr != null) {
            scaleAttr.setBaseValue(1.5);
        }
        player.sendMessage("§6§l规则: §r§6大！大！大！ - 你的体型增大为原来的3/2");
    }

    // 规则3: 我的伙伴 - 获得狐狸刷怪蛋
    private void giveFoxPartner(Player player) {
        // 给玩家一个狐狸刷怪蛋
        ItemStack spawnEgg = new ItemStack(Material.FOX_SPAWN_EGG);
        ItemMeta meta = spawnEgg.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§d我的伙伴");
            meta.setLore(Arrays.asList("§7右键召唤狐狐"));
            // 使用PersistentDataContainer标记这是规则3的刷怪蛋
            meta.getPersistentDataContainer().set(partnerTagKey, PersistentDataType.STRING, player.getUniqueId().toString());
            spawnEgg.setItemMeta(meta);
        }
        player.getInventory().addItem(spawnEgg);
        player.sendMessage("§9§l规则: §r§9我的伙伴 - 你获得了一个狐狸刷怪蛋，右键召唤你的狐狐伙伴！");
    }
    
    // 处理玩家使用狐狸刷怪蛋
    public void onPlayerUseFoxSpawnEgg(Player player, Fox fox) {
        UUID playerUuid = player.getUniqueId();
        
        // 设置狐狸属性
        fox.setCustomName("§b" + player.getName() + "的狐狐");
        fox.setCustomNameVisible(true);
        fox.setSitting(false);
        
        // 存储狐狸UUID
        playerFoxes.put(playerUuid, fox.getUniqueId());
        player.sendMessage("§a你的狐狐伙伴已召唤！它会保护你并给予你力量加持！");
    }

    private void startPartnerTask() {
        World world = gameManager.getGameWorld();
        if (world == null) return;
        
        partnerTask = Bukkit.getRegionScheduler().runAtFixedRate(plugin, world, 0, 0, task -> {
            if (gameManager.getGameStatus() != GameStatus.PLAYING) {
                task.cancel();
                return;
            }

            for (Map.Entry<UUID, UUID> entry : playerFoxes.entrySet()) {
                Player player = Bukkit.getPlayer(entry.getKey());
                org.bukkit.entity.Entity entity = Bukkit.getEntity(entry.getValue());
                
                if (player != null && player.isOnline() && entity instanceof Fox && !entity.isDead()) {
                    // 检查狐狸是否在玩家附近（类似数据包的检测逻辑）
                    if (player.getWorld().equals(entity.getWorld()) && 
                        player.getLocation().distance(entity.getLocation()) <= 50) {
                        // 给予玩家力量和生命回复效果（与数据包一致：10秒）
                        Bukkit.getRegionScheduler().execute(plugin, player.getLocation(), () -> {
                            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, 0, false, false));
                            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 0, false, false));
                        });
                    }
                }
            }
        }, 20L, 20L);
    }

    @EventHandler
    public void onFoxDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Fox) {
            Fox deadFox = (Fox) event.getEntity();
            for (Map.Entry<UUID, UUID> entry : playerFoxes.entrySet()) {
                if (entry.getValue().equals(deadFox.getUniqueId())) {
                    Player player = Bukkit.getPlayer(entry.getKey());
                    if (player != null && player.isOnline()) {
                        player.sendMessage("§c你的狐狐伙伴已死亡，你失去了力量加持！");
                    }
                    playerFoxes.remove(entry.getKey());
                    break;
                }
            }
        }
    }

    // 规则4: 一击必杀 - 攻击伤害 40
    private void applyPunch(Player player) {
        AttributeInstance attackDamage = player.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attackDamage != null) {
            attackDamage.setBaseValue(40);
        }
        player.sendMessage("§c§l规则: §r§c一击必杀！ - 你的攻击伤害变为40");
    }

    // 规则6: 虚空的仁慈 - 掉落虚空传送+60格（带1分钟冷却）
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (currentRule != RuleType.VOID_MERCY) return;
        if (gameManager.getGameStatus() != GameStatus.PLAYING) return;

        Player player = event.getPlayer();
        if (!isInGame(player)) return;

        if (player.getLocation().getY() < -10) {
            UUID playerUuid = player.getUniqueId();
            long currentTime = System.currentTimeMillis();
            
            // 检查冷却时间
            Long lastTriggerTime = voidMercyCooldowns.get(playerUuid);
            if (lastTriggerTime != null && (currentTime - lastTriggerTime) < VOID_MERCY_COOLDOWN_MS) {
                // 冷却中，静默处理（不发送提示避免刷屏）
                return;
            }
            
            // 记录触发时间
            voidMercyCooldowns.put(playerUuid, currentTime);
            
            Location loc = player.getLocation();
            loc.setY(loc.getY() + 60);
            // Folia需要使用teleportAsync
            player.teleportAsync(loc).thenAccept(success -> {
                if (success) {
                    Bukkit.getRegionScheduler().execute(plugin, loc, () -> {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 200, 1));
                        player.sendMessage("§5§l规则: §r§5虚空的仁慈 §a- 你被向上传送了60格！ §7(冷却: 1分钟)");
                    });
                }
            });
        }
    }

    private boolean isInGame(Player player) {
        return gameManager.getAlivePlayers().contains(player.getUniqueId());
    }

    public boolean shouldForceInvExchange() {
        return currentRule == RuleType.INV_EXCHANGE;
    }

    public boolean shouldSkipPunchEvent() {
        return currentRule == RuleType.MY_PARTNER;
    }

    public void resetPlayerAttributes(Player player) {
        // 重置体型
        AttributeInstance scaleAttr = player.getAttribute(Attribute.SCALE);
        if (scaleAttr != null) {
            scaleAttr.setBaseValue(1.0);
        }

        // 重置攻击伤害（清除所有修改器后设置基础值）
        AttributeInstance attackDamage = player.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attackDamage != null) {
            attackDamage.getModifiers().forEach(attackDamage::removeModifier);
            attackDamage.setBaseValue(1.0);
        }

        // 重置最大生命值（防止其他插件修改）
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(20.0);
        }

        // 重置移动速度（清除所有修改器后设置基础值）
        AttributeInstance movementSpeed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (movementSpeed != null) {
            movementSpeed.getModifiers().forEach(movementSpeed::removeModifier);
            movementSpeed.setBaseValue(0.1);
        }

        // 重置跳跃力度（清除所有修改器后设置基础值）
        AttributeInstance jumpStrength = player.getAttribute(Attribute.JUMP_STRENGTH);
        if (jumpStrength != null) {
            jumpStrength.getModifiers().forEach(jumpStrength::removeModifier);
            jumpStrength.setBaseValue(0.42);
        }

        // 重置重力（月球地图低重力效果）
        AttributeInstance gravityAttr = player.getAttribute(Attribute.GRAVITY);
        if (gravityAttr != null) {
            gravityAttr.setBaseValue(0.08); // 默认重力值
        }

        // 重置安全摔落距离（月球地图修改过）
        AttributeInstance safeFallAttr = player.getAttribute(Attribute.SAFE_FALL_DISTANCE);
        if (safeFallAttr != null) {
            safeFallAttr.setBaseValue(3.0); // 默认安全摔落距离
        }

        // 移除所有药水效果（包括规则3的力量/生命回复、规则6的缓降、隐身、发光等）
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));

        // 重置生命值和饥饿值
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20);

        // 重置经验值
        player.setExp(0);
        player.setLevel(0);

        // 重置火焰和冻结时间
        player.setFireTicks(0);
        player.setFreezeTicks(0);

        // 重置空气值
        player.setRemainingAir(player.getMaximumAir());
    }
}
