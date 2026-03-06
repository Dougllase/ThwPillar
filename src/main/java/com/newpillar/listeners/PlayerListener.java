package com.newpillar.listeners;

import com.newpillar.game.items.LootTableSystem;

import com.newpillar.game.advancements.AchievementSystem;

import com.newpillar.game.data.StatisticsSystem;

import com.newpillar.NewPillar;
import com.newpillar.game.GameManager;
import com.newpillar.game.items.ItemEffectManager;
import com.newpillar.game.PlayerData;
import com.newpillar.game.enums.PlayerState;
import com.newpillar.game.items.SpecialItemManager;
import com.newpillar.game.items.VanillaItemManager;
import com.newpillar.game.items.VanillaItemEffectManager;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Fox;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import com.newpillar.game.enums.MapType;
import java.util.Random;

public class PlayerListener implements Listener {
   private final NewPillar plugin;
   private final GameManager gameManager;
   private final SpecialItemManager specialItemManager;
   private final ItemEffectManager itemEffectManager;
   private final VanillaItemManager vanillaItemManager;
   private final VanillaItemEffectManager vanillaItemEffectManager;
   
   // 火箭靴二段跳跟踪
   private final Map<UUID, Boolean> playerOnGround = new HashMap<>();
   private final Map<UUID, Boolean> playerCanDoubleJump = new HashMap<>();

   public PlayerListener(NewPillar plugin, GameManager gameManager,
                         SpecialItemManager specialItemManager,
                         ItemEffectManager itemEffectManager,
                         VanillaItemManager vanillaItemManager,
                         VanillaItemEffectManager vanillaItemEffectManager) {
      this.plugin = plugin;
      this.gameManager = gameManager;
      this.specialItemManager = specialItemManager;
      this.itemEffectManager = itemEffectManager;
      this.vanillaItemManager = vanillaItemManager;
      this.vanillaItemEffectManager = vanillaItemEffectManager;
   }

   @EventHandler
   public void onPlayerDeath(PlayerDeathEvent event) {
      Player player = event.getEntity();
      PlayerData data = this.gameManager.getPlayerData(player.getUniqueId());
      if (data != null) {
         if (data.getState() == PlayerState.INGAME) {
            // 检查是否是国王游戏中国王被杀
            Player killer = player.getKiller();
            if (killer != null && this.gameManager.isKingGameActive() && this.gameManager.isCurrentKing(player)) {
               // 授予弑君者成就
               plugin.getAchievementSystem().grantEventAchievement(killer, "kingslayer");
               Bukkit.broadcastMessage("§6§l[国王游戏] " + killer.getName() + " 杀死了国王 " + player.getName() + "！");
            }

            // 记录击杀和死亡统计（只在局内记录）
            if (killer != null) {
               // 记录击杀者
               plugin.getStatisticsSystem().recordKill(killer, player);
            }
            // 记录死亡
            plugin.getStatisticsSystem().recordDeath(player);

            // 保存死亡位置（处理虚空死亡情况）
            Location deathLocation = player.getLocation();
            // 如果是虚空死亡（Y坐标过低），将位置设置为地图上空
            if (deathLocation.getY() < -64) {
               deathLocation = deathLocation.clone();
               deathLocation.setY(200); // 地图上空
            }
            data.setDeathLocation(deathLocation);
            plugin.getLogger().info("[调试] 玩家 " + player.getName() + " 死亡位置已保存: " + deathLocation);

            this.gameManager.playerOut(player);
            this.gameManager.broadcastMessage("§c" + player.getName() + " §7被淘汰了！剩余玩家: §a" + this.gameManager.getAlivePlayers().size());

            // 触发死亡成就
            plugin.getAchievementSystem().onPlayerDeath(player);

            // 立即设置玩家为旁观模式，防止玩家继续掉落
            player.setGameMode(GameMode.SPECTATOR);
            plugin.getLogger().info("[调试] 玩家 " + player.getName() + " 死亡后立即设置为旁观模式");

            // 发送出局Title提示
            player.sendTitle("§c§l您已出局", "§7已进入观察者模式", 10, 70, 20);

            // 立即传送到死亡位置（或最近的存活玩家）
            Location teleportLocation = deathLocation;
            
            // 如果是虚空死亡（Y坐标过低），传送到最近的存活玩家
            if (player.getLocation().getY() < -64) {
               Player nearestPlayer = this.gameManager.getNearestAlivePlayer(player);
               if (nearestPlayer != null) {
                  teleportLocation = nearestPlayer.getLocation();
                  plugin.getLogger().info("[调试] 玩家 " + player.getName() + " 虚空死亡，传送到最近的玩家: " + nearestPlayer.getName());
               }
            }
            
            // 使用 EntityScheduler 延迟 10-20 tick 后传送（避免重生事件不触发的问题）
            final Location finalTeleportLocation = teleportLocation;
            player.getScheduler().runDelayed(this.plugin, (task) -> {
               if (player.isOnline()) {
                  plugin.getLogger().info("[调试] 延迟10tick: 传送玩家 " + player.getName() + " 到: " + finalTeleportLocation);
                  player.setGameMode(GameMode.SPECTATOR);
                  player.teleportAsync(finalTeleportLocation);
               }
            }, () -> {}, 10);
            
            // 再次延迟传送确保成功
            player.getScheduler().runDelayed(this.plugin, (task) -> {
               if (player.isOnline()) {
                  plugin.getLogger().info("[调试] 延迟20tick: 再次传送玩家 " + player.getName());
                  player.setGameMode(GameMode.SPECTATOR);
                  player.teleportAsync(finalTeleportLocation);
               }
            }, () -> {}, 20);
         }
      }
   }

   @EventHandler
   public void onPlayerRespawn(PlayerRespawnEvent event) {
      Player player = event.getPlayer();
      PlayerData data = this.gameManager.getPlayerData(player.getUniqueId());
      plugin.getLogger().info("[调试] onPlayerRespawn 被调用: " + player.getName() + ", data=" + (data != null));
      if (data != null) {
         // 获取死亡位置
         final Location deathLocation = data.getDeathLocation();
         PlayerState state = data.getState();
         plugin.getLogger().info("[调试] 玩家 " + player.getName() + " 状态: " + state + ", 死亡位置: " + deathLocation);
         
         // 如果玩家是观察者状态（出局后转为观察者）且有死亡位置记录
         if (state == PlayerState.SPECTATOR && deathLocation != null) {
            plugin.getLogger().info("[调试] 设置重生位置为死亡位置: " + deathLocation);
            // 设置重生位置为死亡位置
            event.setRespawnLocation(deathLocation);
            
            // 使用玩家的 EntityScheduler 确保在正确的线程执行
            player.getScheduler().execute(this.plugin, () -> {
               if (player.isOnline()) {
                  plugin.getLogger().info("[调试] 立即执行: 设置 " + player.getName() + " 为旁观模式并传送");
                  // 设置为旁观模式
                  player.setGameMode(GameMode.SPECTATOR);
                  // 传送到死亡位置（使用 teleportAsync）
                  player.teleportAsync(deathLocation);
               }
            }, () -> {}, 1L);
            
            // 延迟再次传送确保成功
            player.getScheduler().runDelayed(this.plugin, (task) -> {
               if (player.isOnline()) {
                  plugin.getLogger().info("[调试] 延迟5tick: 再次传送 " + player.getName());
                  player.setGameMode(GameMode.SPECTATOR);
                  player.teleportAsync(deathLocation);
               }
            }, () -> {}, 5);
            
            player.getScheduler().runDelayed(this.plugin, (task) -> {
               if (player.isOnline()) {
                  plugin.getLogger().info("[调试] 延迟10tick: 最终传送 " + player.getName());
                  player.setGameMode(GameMode.SPECTATOR);
                  player.teleportAsync(deathLocation);
               }
            }, () -> {}, 10);
         } else {
            plugin.getLogger().info("[调试] 玩家 " + player.getName() + " 不是观察者或没有死亡位置，正常重生");
         }
      }
   }

   @EventHandler
   public void onPlayerQuit(PlayerQuitEvent event) {
      Player player = event.getPlayer();
      PlayerData data = this.gameManager.getPlayerData(player.getUniqueId());
      if (data != null && data.getState() == PlayerState.INGAME) {
         this.gameManager.playerOut(player);
         this.gameManager.broadcastMessage("§c" + player.getName() + " §7离开了游戏！剩余玩家: §a" + this.gameManager.getAlivePlayers().size());
      }

      this.gameManager.playerLeave(player);
   }

   @EventHandler
   public void onPlayerJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      // 玩家登录时自动加入游戏
      this.gameManager.playerJoin(player);
   }

   @EventHandler
   public void onPlayerMove(PlayerMoveEvent event) {
      Player player = event.getPlayer();
      
      // 火箭靴二段跳检测
      handleRocketBootsDoubleJump(player);
      
      PlayerData data = this.gameManager.getPlayerData(player.getUniqueId());
      if (data != null && data.getState() == PlayerState.INGAME) {
         Player lookAtMeTarget = this.gameManager.getLookAtMeTarget();
         if (lookAtMeTarget != null && !lookAtMeTarget.equals(player)) {
            Location playerLoc = player.getLocation();
            Location targetLoc = lookAtMeTarget.getLocation();
            Vector direction = targetLoc.toVector().subtract(playerLoc.toVector());
            float yaw = (float)Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
            float pitch = (float)Math.toDegrees(
               -Math.atan2(direction.getY(), Math.sqrt(direction.getX() * direction.getX() + direction.getZ() * direction.getZ()))
            );
            Location newLoc = playerLoc.clone();
            newLoc.setYaw(yaw);
            newLoc.setPitch(pitch);

            try {
               Method setRotationMethod = player.getClass().getMethod("setRotation", float.class, float.class);
               setRotationMethod.invoke(player, yaw, pitch);
            } catch (Exception var20) {
               event.setTo(newLoc);
            }
         }

         if (this.gameManager.isKeyInversionActive()) {
            // 键位反转效果：反转玩家的移动输入
            Location from = event.getFrom();
            Location to = event.getTo();
            if (to == null) {
               return;
            }

            // 计算玩家移动的位移
            double deltaX = to.getX() - from.getX();
            double deltaZ = to.getZ() - from.getZ();
            
            // 检测是否有水平移动（使用更小的阈值）
            if (Math.abs(deltaX) > 0.0001 || Math.abs(deltaZ) > 0.0001) {
               // 计算反方向的位置（反转水平移动）
               // 乘以1.2让玩家感觉移动被"放大"了反向效果
               double newX = from.getX() - deltaX * 1.2;
               double newZ = from.getZ() - deltaZ * 1.2;
               // Y轴保持原样，让重力正常工作
               double newY = to.getY();
               
               Location newLoc = new Location(from.getWorld(), newX, newY, newZ);
               newLoc.setYaw(to.getYaw());
               newLoc.setPitch(to.getPitch());
               event.setTo(newLoc);
            }
         }

         // 海洋地图：水有毒效果
         if (this.gameManager.getCurrentMapType() == MapType.SEA) {
            // 检查玩家是否在水中（每20 ticks检查一次，避免过于频繁）
            if (player.getTicksLived() % 20 == 0) {
               if (player.isInWater()) {
                  // 给予中毒效果 5秒（100 ticks），等级0（中毒I）
                  Bukkit.getRegionScheduler().execute(this.plugin, player.getLocation(), () -> {
                     if (player.isOnline()) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 0, false, false));
                     }
                  });
               }
            }
         }
      }
   }

   // ==================== 特殊物品交互处理 ====================

   @EventHandler
   public void onPlayerInteract(PlayerInteractEvent event) {
      Player player = event.getPlayer();
      ItemStack item = event.getItem();

      if (item == null) return;

      // 检查是否是规则3的狐狸刷怪蛋
      if (item.getType() == Material.FOX_SPAWN_EGG) {
         if (item.hasItemMeta()) {
            NamespacedKey key = new NamespacedKey(plugin, "rule_partner");
            String playerUuid = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
            if (playerUuid != null && playerUuid.equals(player.getUniqueId().toString())) {
               // 这是规则3的刷怪蛋，标记为即将生成狐狸
               plugin.setPendingFoxSpawn(player.getUniqueId());
            }
         }
      }

      // 检查是否是特殊物品
      if (specialItemManager.isSpecialItem(item)) {
         SpecialItemManager.SpecialItemType type = specialItemManager.getSpecialItemType(item);

         // 以下物品不取消事件，让它们正常工作：
         // - 弓弩类：正常拉弓/装填，特殊效果在 EntityShootBowEvent 中处理
         // - 工具类（神镐）：正常挖掘方块
         if (type == SpecialItemManager.SpecialItemType.SPECIAL_BOW ||
             type == SpecialItemManager.SpecialItemType.SPECIAL_CROSSBOW ||
             type == SpecialItemManager.SpecialItemType.GODLY_PICKAXE) {
            return;
         }

         // "让你飞起来"（重锤）只在右键且在地面上时触发技能
         // 允许正常的左键攻击（空中和地面）
         if (type == SpecialItemManager.SpecialItemType.FLY_MACE) {
            // 只有右键才触发技能
            if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
               return; // 左键不处理，让重锤正常攻击
            }
            // 右键时检查是否在地面上
            if (!player.isOnGround()) {
               return; // 空中右键不触发技能
            }
         }

         // 鞋类物品（火箭靴、跑鞋）没有右键技能，不取消事件让它们正常装备
         if (type == SpecialItemManager.SpecialItemType.ROCKET_BOOTS ||
             type == SpecialItemManager.SpecialItemType.RUNNING_SHOES) {
            return;
         }

         event.setCancelled(true);
         itemEffectManager.onPlayerInteract(player, item);
         return;
      }

      // 检查是否是原版特殊物品
      if (vanillaItemManager.isVanillaItem(item)) {
         event.setCancelled(true);
         vanillaItemEffectManager.onPlayerInteract(player, item);
         return;
      }
      
      // 检查是否是普通原版物品（通过give指令获取的TNT、龙息、火焰弹）
      Material material = item.getType();
      if (material == Material.TNT || material == Material.DRAGON_BREATH || material == Material.FIRE_CHARGE) {
         // 只有在右键空气或方块时才触发
         if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            vanillaItemEffectManager.onPlayerInteract(player, item);
         }
      }
   }

   @EventHandler
   public void onCreatureSpawn(CreatureSpawnEvent event) {
      if (event.getEntity() instanceof Fox) {
         Fox fox = (Fox) event.getEntity();
         // 检查是否有玩家等待生成狐狸
         for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (plugin.isPendingFoxSpawn(player.getUniqueId())) {
               // 检查狐狸是否在玩家附近
               if (player.getWorld().equals(fox.getWorld()) && 
                   player.getLocation().distance(fox.getLocation()) <= 5) {
                  plugin.clearPendingFoxSpawn(player.getUniqueId());
                  plugin.getGameManager().getRuleSystem().onPlayerUseFoxSpawnEgg(player, fox);
                  break;
               }
            }
         }
      }
   }

   @EventHandler
   public void onItemHeld(PlayerItemHeldEvent event) {
      Player player = event.getPlayer();
      ItemStack newItem = player.getInventory().getItem(event.getNewSlot());

      // 处理手持物品效果
      itemEffectManager.onItemHeld(player, newItem);
   }

   @EventHandler
   public void onEntityDamage(EntityDamageEvent event) {
      if (event.getEntity() instanceof Player) {
         Player player = (Player) event.getEntity();

         // 检查是否在使用铁剑格挡
         ItemStack mainHand = player.getInventory().getItemInMainHand();
         if (specialItemManager.isSpecialItem(mainHand)) {
            SpecialItemManager.SpecialItemType type = specialItemManager.getSpecialItemType(mainHand);
            if (type == SpecialItemManager.SpecialItemType.IRON_SWORD) {
               // 这里可以添加格挡逻辑
               // 注意：实际格挡效果需要在ItemEffectManager中实现
            }
         }
      }
   }

   // ==================== 特殊弓弩射击处理 ====================

   @EventHandler
   public void onEntityShootBow(EntityShootBowEvent event) {
      if (!(event.getEntity() instanceof Player)) return;

      Player player = (Player) event.getEntity();
      ItemStack bow = event.getBow();

      if (bow == null) return;

      // 检查是否是特殊物品弓弩
      if (!specialItemManager.isSpecialItem(bow)) return;

      SpecialItemManager.SpecialItemType type = specialItemManager.getSpecialItemType(bow);
      if (type == null) return;

      // 检查玩家是否在游戏中
      PlayerData data = this.gameManager.getPlayerData(player.getUniqueId());
      if (data == null || data.getState() != PlayerState.INGAME) return;

      // 处理神弓的爆炸箭效果
      if (type == SpecialItemManager.SpecialItemType.SPECIAL_BOW) {
         if (event.getProjectile() instanceof Arrow arrow) {
            arrow.setMetadata("explosive", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
            player.sendMessage("§c§l爆炸箭！");
         }
      }
      // 神弩使用原版多重射击附魔，不需要额外处理
   }

   /**
    * 监听箭矢落地/命中事件，处理爆炸效果
    */
   @EventHandler
   public void onProjectileHit(org.bukkit.event.entity.ProjectileHitEvent event) {
      // 处理雪球和鸡蛋的击退效果
      if (event.getEntity() instanceof org.bukkit.entity.Snowball snowball) {
         if (event.getHitEntity() instanceof Player target) {
            // 击退被命中的玩家
            Vector knockback = target.getLocation().toVector()
                  .subtract(snowball.getLocation().toVector())
                  .normalize()
                  .multiply(0.8)
                  .setY(0.3);
            target.setVelocity(knockback);
         }
         return;
      }
      
      if (event.getEntity() instanceof org.bukkit.entity.Egg egg) {
         if (event.getHitEntity() instanceof Player target) {
            // 击退被命中的玩家
            Vector knockback = target.getLocation().toVector()
                  .subtract(egg.getLocation().toVector())
                  .normalize()
                  .multiply(0.8)
                  .setY(0.3);
            target.setVelocity(knockback);
         }
         return;
      }
      
      if (!(event.getEntity() instanceof Arrow arrow)) return;

      // 处理爆炸箭 - 神弓射出的箭
      if (arrow.hasMetadata("explosive")) {
         Location loc = arrow.getLocation();
         Player shooter = null;
         if (arrow.getShooter() instanceof Player p) {
            shooter = p;
         }
         // 产生爆炸，不破坏方块，不造成火焰
         arrow.getWorld().createExplosion(loc, 3.0f, false, false, shooter);
         arrow.remove();
      }
   }

   // ==================== 肘击王成就检测 ====================

   @EventHandler
   public void onEntityDeath(EntityDeathEvent event) {
      if (!(event.getEntity() instanceof Player)) return;

      Player victim = (Player) event.getEntity();
      Player killer = victim.getKiller();

      if (killer == null) return;

      // 检查是否是空手击杀
      ItemStack mainHand = killer.getInventory().getItemInMainHand();
      if (mainHand.getType() == Material.AIR) {
         // 空手击杀，触发肘击王成就
         plugin.getAchievementSystem().addElbowKill(killer);
      }
   }

   // ==================== 物品获取成就检测 ====================

   @EventHandler
   public void onEntityPickupItem(EntityPickupItemEvent event) {
      if (!(event.getEntity() instanceof Player)) return;

      Player player = (Player) event.getEntity();
      ItemStack item = event.getItem().getItemStack();

      // 检查是否是特殊物品
      if (!specialItemManager.isSpecialItem(item)) {
         return;
      }

      SpecialItemManager.SpecialItemType type = specialItemManager.getSpecialItemType(item);
      if (type == null) {
         return;
      }

      // 根据物品类型触发对应成就
      String achievementId = switch (type) {
         case KNOCKBACK_STICK -> "knockback_stick";
         case BONES_WITHOUT_CHICKEN_FEET -> "bones_without_chicken_feet";
         case CARD -> "yanpai";
         case PIXIE -> "pixie";
         case ROCKET_BOOTS -> "rocket_boots";
         case RUNNING_SHOES -> "running_shoes";
         case BLUE_SCREEN -> "blue_screen";
         case HONGBAO -> "hongbao";
         case HYPNOSIS_APP -> "hypnosis_app";
         case CLOCK -> "clock";
         case SPAWNER -> "spawner";
         case BRUCE -> "bruce";
         case WITCH_APPLE -> "witch_apple";
         case BIG_FLAME_ROD -> "big_flame_rod";
         case MEOW_AXE -> "meow_axe";
         case FLY_MACE -> "fly_mace";
         case INVISIBLE_SAND -> "invisible_scarf";
         case FEATHER -> "feather";
         case GODLY_PICKAXE -> "godly_pickaxe";
         case SPECIAL_BOW -> "special_bow";
         case SPECIAL_CROSSBOW -> "special_crossbow";
         case SPEAR -> "spear";
         case IRON_SWORD -> "iron_sword";
         default -> null;
      };

      if (achievementId != null && plugin.getAchievementSystem() != null) {
         plugin.getAchievementSystem().grantItemAchievement(player, achievementId);
      }
   }
   
   // ==================== 火箭靴二段跳 ====================
   
   /**
    * 处理火箭靴二段跳逻辑
    * 在玩家移动时检测是否满足二段跳条件
    */
   private void handleRocketBootsDoubleJump(Player player) {
      UUID playerId = player.getUniqueId();
      boolean isOnGround = player.isOnGround();
      boolean wasOnGround = playerOnGround.getOrDefault(playerId, true);
      
      // 检查玩家是否穿着火箭靴
      ItemStack boots = player.getInventory().getBoots();
      boolean hasRocketBoots = boots != null && 
            specialItemManager.isSpecialItem(boots) &&
            specialItemManager.getSpecialItemType(boots) == SpecialItemManager.SpecialItemType.ROCKET_BOOTS;
      
      if (!hasRocketBoots) {
         // 如果没有穿火箭靴，重置状态
         playerCanDoubleJump.remove(playerId);
         playerOnGround.put(playerId, isOnGround);
         return;
      }
      
      // 玩家着地时重置二段跳能力
      if (isOnGround && !wasOnGround) {
         playerCanDoubleJump.put(playerId, true);
      }
      
      // 玩家从地面起跳时，标记可以使用二段跳
      if (!isOnGround && wasOnGround) {
         // 玩家刚刚起跳，准备二段跳
         playerCanDoubleJump.put(playerId, true);
      }
      
      playerOnGround.put(playerId, isOnGround);
   }
   
   /**
    * 玩家蹲下时触发二段跳
    */
   @EventHandler
   public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
      Player player = event.getPlayer();
      
      // 只在玩家蹲下时处理（不是站起来）
      if (!event.isSneaking()) return;
      
      // 检查玩家是否穿着火箭靴
      ItemStack boots = player.getInventory().getBoots();
      boolean hasRocketBoots = boots != null && 
            specialItemManager.isSpecialItem(boots) &&
            specialItemManager.getSpecialItemType(boots) == SpecialItemManager.SpecialItemType.ROCKET_BOOTS;
      
      if (!hasRocketBoots) return;
      
      // 检查玩家是否在空中且可以使用二段跳
      UUID playerId = player.getUniqueId();
      boolean canDoubleJump = playerCanDoubleJump.getOrDefault(playerId, false);
      boolean isOnGround = player.isOnGround();
      
      if (!isOnGround && canDoubleJump) {
         // 执行二段跳
         performDoubleJump(player);
         // 标记已使用二段跳
         playerCanDoubleJump.put(playerId, false);
      }
   }
   
   /**
    * 执行二段跳
    */
   private void performDoubleJump(Player player) {
      // 给予向上的冲量 (Y轴速度 0.8，减小数值)
      Vector velocity = player.getVelocity();
      velocity.setY(0.8);
      player.setVelocity(velocity);

      // 播放音效 (与数据包一致: wind_burst)
      player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.0f, 1.0f);

      // 播放粒子效果 (与数据包一致: gust_emitter_small)
      player.getWorld().spawnParticle(Particle.GUST, player.getLocation(), 10, 0.5, 0.5, 0.5, 0.1);
   }

   // ==================== 海洋地图钓鱼系统 ====================

   private final Random fishingRandom = new Random();

   /**
    * 处理钓鱼事件 - 海洋地图特殊战利品
    * 检测鱼上钩后，清除鱼钩并给予战利品表物品（严格1个）
    */
   @EventHandler
   public void onPlayerFish(PlayerFishEvent event) {
      // 只处理钓到物品的情况
      if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
         return;
      }

      Player player = event.getPlayer();

      // 检查是否是海洋地图且玩家在游戏中
      if (this.gameManager.getCurrentMapType() != MapType.SEA) {
         return;
      }

      PlayerData data = this.gameManager.getPlayerData(player.getUniqueId());
      if (data == null || data.getState() != PlayerState.INGAME) {
         return;
      }

      // 取消默认的钓鱼战利品（必须在最前面取消）
      event.setCancelled(true);

      // 获取并移除鱼钩
      final org.bukkit.entity.FishHook hook = event.getHook();
      final Location hookLoc = hook.getLocation();
      
      // 清除鱼钩
      hook.remove();

      // 使用海洋地图专用的 sea 战利品表
      ItemStack reward = this.plugin.getLootTableSystem().getRandomLoot("sea");

      if (reward != null) {
         // 严格设置数量为1（钓鱼只给1个）
         reward.setAmount(1);
         
         // 将物品直接给予玩家
         player.getInventory().addItem(reward);
         
         // 播放音效和粒子效果
         player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
         if (hookLoc != null) {
            player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, hookLoc, 10, 0.5, 0.5, 0.5, 0);
         }
      }
   }

}
