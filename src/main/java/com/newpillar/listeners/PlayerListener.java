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
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
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
      
      // 大厅中玩家死亡不掉落物品
      if (data != null && data.getState() == PlayerState.LOBBY) {
         event.setKeepInventory(true);
         event.getDrops().clear();
         event.setKeepLevel(true);
         event.setDroppedExp(0);
         return;
      }
      
      if (data != null) {
         if (data.getState() == PlayerState.INGAME) {
            // 获取击杀者
            Player killer = player.getKiller();
            
            // 如果是虚空死亡（Y坐标过低）且没有直接击杀者，检查最近20秒内的伤害记录
            if (killer == null && player.getLocation().getY() < -64) {
               UUID recentAttackerUuid = data.getRecentAttacker();
               if (recentAttackerUuid != null) {
                  killer = org.bukkit.Bukkit.getPlayer(recentAttackerUuid);
                  if (killer != null) {
                     plugin.getLogger().info("[虚空击杀] 玩家 " + player.getName() + " 被 " + killer.getName() + " 在20秒内击杀（虚空死亡）");
                  }
               }
            }
            
            // 检查是否是国王游戏中国王被杀
            if (killer != null && this.gameManager.isKingGameActive() && this.gameManager.isCurrentKing(player)) {
               // 授予弑君者成就
               plugin.getAchievementSystem().grantEventAchievement(killer, "kingslayer");
               final String kingMessage = "§6§l[国王游戏] " + killer.getName() + " 杀死了国王 " + player.getName() + "！";
               Bukkit.getGlobalRegionScheduler().execute(this.plugin, () -> Bukkit.broadcastMessage(kingMessage));
            }

            // 记录击杀和死亡统计（只在局内记录）
            if (killer != null) {
               // 记录击杀者
               plugin.getStatisticsSystem().recordKill(killer, player);
               // 通知ThwReward子插件处理击杀奖励（在playerOut之前，避免游戏结束导致奖励无法记录）
               plugin.getThwRewardIntegration().onPlayerKill(killer, player);
               // 通知奖励管理器发送击杀消息
               plugin.getRewardManager().onPlayerKill(killer, player);
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

            // 获取死亡原因
            EntityDamageEvent lastDamage = player.getLastDamageCause();
            String deathCause = lastDamage != null ? lastDamage.getCause().name() : null;
            plugin.getLogger().info("[调试] 玩家 " + player.getName() + " 死亡原因: " + deathCause);

            // 重要：在playerOut之前通知ThwReward子插件玩家出局
            // 因为playerOut可能会触发endGame()，导致gameRunning变为false
            // 从而使onPlayerEliminated直接返回，玩家无法获得失败奖励
            plugin.getThwRewardIntegration().onPlayerEliminated(player, deathCause);
            // 通知奖励管理器发送出局消息
            plugin.getRewardManager().onPlayerEliminated(player, deathCause);

            // 触发死亡成就
            plugin.getAchievementSystem().onPlayerDeath(player);

            this.gameManager.playerOut(player);
            final String eliminationMessage = "§c" + player.getName() + " §7被淘汰了！剩余玩家: §a" + this.gameManager.getAlivePlayers().size();
            Bukkit.getGlobalRegionScheduler().execute(this.plugin, () -> this.gameManager.broadcastMessage(eliminationMessage));

            // 清理玩家所有属性修改（包括实体交互范围等）
            this.cleanupPlayerAttributes(player);

            // 立即设置玩家为旁观模式，防止玩家继续掉落
            player.setGameMode(GameMode.SPECTATOR);
            plugin.getLogger().info("[调试] 玩家 " + player.getName() + " 死亡后立即设置为旁观模式");

            // 发送出局Title提示 - 使用EntityScheduler在玩家线程执行
            player.getScheduler().execute(this.plugin, () -> {
               if (player.isOnline()) {
                  player.sendTitle("§c§l您已出局", "§7已进入观察者模式", 10, 70, 20);
               }
            }, () -> {}, 1L);

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
         
         // 如果玩家是观察者状态（出局后转为观察者）
         if (state == PlayerState.SPECTATOR) {
            // 使用统一的旁观者处理方法（会附身到随机存活玩家）
            plugin.getLogger().info("[调试] 玩家 " + player.getName() + " 使用统一方法成为旁观者");
            
            // 先设置重生位置为死亡位置（如果存在）
            if (deathLocation != null) {
               event.setRespawnLocation(deathLocation);
            }
            
            // 立即设置为旁观模式（在重生前）
            player.setGameMode(GameMode.SPECTATOR);
            
            // 延迟一tick执行，确保重生完成
            player.getScheduler().execute(this.plugin, () -> {
               if (player.isOnline()) {
                  // 确保是旁观模式
                  player.setGameMode(GameMode.SPECTATOR);
                  player.setAllowFlight(true);
                  player.setFlying(true);
                  // 使用统一方法处理旁观者（会附身到随机存活玩家）
                  this.gameManager.handlePlayerBecomeSpectator(player, null, 
                     "§7你已被淘汰，现在可以飞行观看游戏。");
               }
            }, () -> {}, 1L);
            
            // 再次延迟确保模式正确
            player.getScheduler().execute(this.plugin, () -> {
               if (player.isOnline() && player.getGameMode() != GameMode.SPECTATOR) {
                  plugin.getLogger().info("[调试] 玩家 " + player.getName() + " 模式不正确，重新设置为旁观模式");
                  player.setGameMode(GameMode.SPECTATOR);
                  player.setAllowFlight(true);
                  player.setFlying(true);
               }
            }, () -> {}, 5L);
         } else {
            plugin.getLogger().info("[调试] 玩家 " + player.getName() + " 不是观察者，正常重生");
         }
      }
   }

   @EventHandler
   public void onPlayerQuit(PlayerQuitEvent event) {
      Player player = event.getPlayer();
      UUID uuid = player.getUniqueId();
      PlayerData data = this.gameManager.getPlayerData(uuid);
      
      if (data != null) {
         PlayerState state = data.getState();
         
         if (state == PlayerState.INGAME) {
            // 未出局玩家主动退出 - 扣除所有本局收益
            plugin.getLogger().info("[调试] 玩家 " + player.getName() + " 未出局就退出，扣除本局收益");
            
            // 通知ThwReward扣除收益
            plugin.getThwRewardIntegration().onPlayerQuitEarly(player);
            
            // 通知奖励管理器扣除收益
            plugin.getRewardManager().onPlayerQuitEarly(player);
            
            // 调用playerOut处理玩家出局逻辑（不触发endGame，由playerLeave统一处理）
            this.gameManager.playerOut(player, true);
            
            // 获取剩余的存活玩家数量（playerOut已经移除了该玩家）
            final int remainingPlayers = this.gameManager.getAlivePlayers().size();
            final String quitMessage = "§c" + player.getName() + " §7离开了游戏！剩余玩家: §a" + remainingPlayers;
            Bukkit.getGlobalRegionScheduler().execute(this.plugin, () -> this.gameManager.broadcastMessage(quitMessage));
         } else if (state == PlayerState.SPECTATOR) {
            // 已出局玩家（旁观者）退出 - 已经结算过奖励，不再处理
            plugin.getLogger().info("[调试] 玩家 " + player.getName() + " 已出局后退出，不扣除收益");
            
            // 只需要调用playerOut和playerLeave清理数据
            this.gameManager.playerOut(player, true);
            
            // 获取剩余的存活玩家数量
            final int remainingPlayers = this.gameManager.getAlivePlayers().size();
            final String quitMessage = "§c" + player.getName() + " §7离开了游戏！剩余玩家: §a" + remainingPlayers;
            Bukkit.getGlobalRegionScheduler().execute(this.plugin, () -> this.gameManager.broadcastMessage(quitMessage));
         }
      }

      this.gameManager.playerLeave(player);
      
      // 通知奖励管理器玩家退出（招人系统用）
      plugin.getRewardManager().onPlayerQuit(player);
   }

   @EventHandler
   public void onPlayerJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      // 玩家登录时自动加入游戏
      this.gameManager.playerJoin(player);
      
      // 通知ThwReward玩家加入（用于招人系统广播）
      notifyThwRewardPlayerJoin(player);
      
      // 通知奖励管理器玩家加入（发送招人消息等）
      plugin.getRewardManager().onPlayerJoin(player);
      
      // 检查玩家当前手持物品，应用对应效果（如羽毛的缓降效果）
      ItemStack mainHandItem = player.getInventory().getItemInMainHand();
      if (mainHandItem != null && !mainHandItem.getType().isAir()) {
         itemEffectManager.onItemHeld(player, mainHandItem);
      }
   }
   
   /**
    * 通知ThwReward插件玩家加入
    */
   private void notifyThwRewardPlayerJoin(Player player) {
      try {
         org.bukkit.plugin.Plugin thwRewardPlugin = Bukkit.getPluginManager().getPlugin("ThwNewPillarRewards");
         if (thwRewardPlugin == null || !thwRewardPlugin.isEnabled()) {
            return;
         }

         // 获取RecruitmentSystem
         java.lang.reflect.Method getRecruitmentSystem = thwRewardPlugin.getClass().getMethod("getRecruitmentSystem");
         Object recruitmentSystem = getRecruitmentSystem.invoke(thwRewardPlugin);

         if (recruitmentSystem == null) {
            return;
         }

         // 获取当前玩家数量（使用准备玩家数量）
         int currentPlayers = this.gameManager.getReadyPlayers().size();

         // 调用onPlayerJoin方法
         java.lang.reflect.Method onPlayerJoin = recruitmentSystem.getClass().getMethod("onPlayerJoin", String.class, int.class);
         onPlayerJoin.invoke(recruitmentSystem, player.getName(), currentPlayers);

      } catch (Exception e) {
         // 静默处理，不影响主插件功能
         this.plugin.getDebugLogger().debug("通知ThwReward玩家加入失败: " + e.getMessage());
      }
   }

   @EventHandler
   public void onPlayerMove(PlayerMoveEvent event) {
      Player player = event.getPlayer();
      
      // 蓝屏效果：取消移动
      if (this.itemEffectManager.isBlueScreenPlayer(player)) {
         event.setCancelled(true);
         return;
      }
      
      // 断腿效果：取消跳跃（通过检测Y轴速度）
      if (this.gameManager.getEventSystem() != null && this.gameManager.getEventSystem().isBrokenLegPlayer(player)) {
         // 检测玩家是否正在跳跃（Y轴速度向上且大于0.1）
         Vector velocity = player.getVelocity();
         if (velocity.getY() > 0.1 && !player.isOnGround()) {
            // 取消跳跃：将Y轴速度设为0
            velocity.setY(0);
            player.setVelocity(velocity);
         }
      }
      
      // 火箭靴二段跳检测
      handleRocketBootsDoubleJump(player);
      
      PlayerData data = this.gameManager.getPlayerData(player.getUniqueId());
      
      // 大厅状态掉出世界检测
      if (data != null && data.getState() == PlayerState.LOBBY) {
         if (player.getLocation().getY() < -64) {
            // 传送到大厅
            Location lobby = this.gameManager.getLobbyLocation();
            player.teleportAsync(lobby);
            player.sendMessage("§c你掉出了世界，已传送回大厅！");
         }
      }
      
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

      // 优先检查主手物品，如果主手有特殊物品则使用主手，否则检查副手
      ItemStack mainHand = player.getInventory().getItemInMainHand();
      ItemStack offHand = player.getInventory().getItemInOffHand();

      ItemStack item = null;
      boolean isMainHand = false;

      // 优先主手：如果主手有特殊物品或原版特殊物品，使用主手
      if (mainHand != null && !mainHand.getType().isAir()) {
         if (specialItemManager.isSpecialItem(mainHand) || vanillaItemManager.isVanillaItem(mainHand) ||
             mainHand.getType() == Material.TNT || mainHand.getType() == Material.DRAGON_BREATH ||
             mainHand.getType() == Material.FIRE_CHARGE ||
             (mainHand.getType() == Material.BOOK && mainHand.hasItemMeta() &&
              "§6§l投票".equals(mainHand.getItemMeta().getDisplayName()))) {
            item = mainHand;
            isMainHand = true;
         }
      }

      // 如果主手没有特殊物品，检查副手
      if (item == null && offHand != null && !offHand.getType().isAir()) {
         if (specialItemManager.isSpecialItem(offHand) || vanillaItemManager.isVanillaItem(offHand) ||
             offHand.getType() == Material.TNT || offHand.getType() == Material.DRAGON_BREATH ||
             offHand.getType() == Material.FIRE_CHARGE ||
             (offHand.getType() == Material.BOOK && offHand.hasItemMeta() &&
              "§6§l投票".equals(offHand.getItemMeta().getDisplayName()))) {
            item = offHand;
            isMainHand = false;
         }
      }

      // 如果都没有特殊物品，使用event.getItem()（通常是主手）
      if (item == null) {
         item = event.getItem();
         isMainHand = true;
      }

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

         // 俄罗斯轮盘枪 - 右键打开选择界面
         if (type == SpecialItemManager.SpecialItemType.RUSSIAN_ROULETTE) {
            if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
               event.setCancelled(true);
               // 消耗一个物品
               item.setAmount(item.getAmount() - 1);
               // 打开俄罗斯轮盘GUI
               plugin.getRussianRouletteGUI().openRouletteGUI(player);
            }
            return;
         }

         // 以下物品不取消事件，让它们正常工作：
         // - 弓弩类：正常拉弓/装填，特殊效果在 EntityShootBowEvent 中处理
         // - 工具类（神镐）：正常挖掘方块
         // - 长矛：使用原版突进功能
         if (type == SpecialItemManager.SpecialItemType.SPECIAL_BOW ||
             type == SpecialItemManager.SpecialItemType.SPECIAL_CROSSBOW ||
             type == SpecialItemManager.SpecialItemType.GODLY_PICKAXE ||
             type == SpecialItemManager.SpecialItemType.SPEAR) {
            return;
         }

         // "让你飞起来"（重锤）任意右键触发技能
         // 允许正常的左键攻击（空中和地面）
         if (type == SpecialItemManager.SpecialItemType.FLY_MACE) {
            // 只有右键才触发技能，任意位置（空中或地面）都可以
            if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
               // 右键触发技能
               event.setCancelled(true);
               itemEffectManager.onPlayerInteract(player, item, event.getClickedBlock());
            }
            // 左键不处理，让重锤正常攻击
            return;
         }

         // 鞋类物品（火箭靴、跑鞋）没有右键技能，不取消事件让它们正常装备
         if (type == SpecialItemManager.SpecialItemType.ROCKET_BOOTS ||
             type == SpecialItemManager.SpecialItemType.RUNNING_SHOES) {
            return;
         }
         
         // 被动效果物品（羽毛、隐身沙粒）：右键触发效果，但不取消事件
         if (type == SpecialItemManager.SpecialItemType.FEATHER ||
             type == SpecialItemManager.SpecialItemType.INVISIBLE_SAND) {
            // 触发被动效果
            itemEffectManager.onItemHeld(player, item);
            // 不取消事件，让玩家可以正常放置/使用
            return;
         }

         event.setCancelled(true);
         itemEffectManager.onPlayerInteract(player, item, event.getClickedBlock());
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
         return;
      }
      
      // 检查是否是投票物品（书本）
      if (material == Material.BOOK && item.hasItemMeta()) {
         String displayName = item.getItemMeta().getDisplayName();
         if ("§6§l投票".equals(displayName)) {
            event.setCancelled(true);
            // 打开投票GUI
            if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
               plugin.getVoteGUI().openVoteGUI(player);
            }
         }
      }

      // 检查是否是附魔书（主手持有，给副手武器附魔）
      if (material == Material.ENCHANTED_BOOK && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
         handleEnchantedBook(player, item, event);
      }
   }

   /**
    * 处理附魔书右键给副手物品附魔
    * 不限制副手物品类型，只要存在物品即可附魔
    */
   private void handleEnchantedBook(Player player, ItemStack book, PlayerInteractEvent event) {
      // 获取副手物品
      ItemStack offHandItem = player.getInventory().getItemInOffHand();
      
      // 检查副手是否有物品
      if (offHandItem == null || offHandItem.getType() == Material.AIR) {
         player.sendMessage("§c副手必须持有物品才能附魔！");
         return;
      }
      
      // 获取附魔书的附魔
      org.bukkit.inventory.meta.EnchantmentStorageMeta bookMeta = (org.bukkit.inventory.meta.EnchantmentStorageMeta) book.getItemMeta();
      if (bookMeta == null || !bookMeta.hasStoredEnchants()) {
         player.sendMessage("§c这本附魔书没有附魔！");
         return;
      }
      
      // 给副手物品添加附魔
      Material offHandType = offHandItem.getType();
      org.bukkit.inventory.meta.ItemMeta itemMeta = offHandItem.getItemMeta();
      if (itemMeta == null) {
         itemMeta = org.bukkit.Bukkit.getItemFactory().getItemMeta(offHandType);
      }
      
      int enchantCount = 0;
      for (java.util.Map.Entry<org.bukkit.enchantments.Enchantment, Integer> entry : bookMeta.getStoredEnchants().entrySet()) {
         org.bukkit.enchantments.Enchantment enchant = entry.getKey();
         int level = entry.getValue();
         
         // 无视限制直接添加附魔
         itemMeta.addEnchant(enchant, level, true);
         enchantCount++;
      }
      
      if (enchantCount > 0) {
         offHandItem.setItemMeta(itemMeta);
         
         // 消耗附魔书
         book.setAmount(book.getAmount() - 1);
         
         // 播放音效
         player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
         
         // 显示粒子效果
         player.getWorld().spawnParticle(org.bukkit.Particle.ENCHANT, player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.5);
         
         player.sendMessage("§a成功给副手" + offHandType.name().toLowerCase().replace("_", " ") + "附魔！");
         event.setCancelled(true);
      } else {
         player.sendMessage("§c这本附魔书没有附魔！");
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
      if (event.getEntity() instanceof Player player) {
         // 检查玩家是否在大厅
         PlayerData data = this.gameManager.getPlayerData(player.getUniqueId());
         if (data != null && data.getState() == PlayerState.LOBBY) {
            event.setCancelled(true);
            return;
         }
         
         // 检查游戏状态是否为LOBBY
         if (this.gameManager.getGameStatus() == com.newpillar.game.enums.GameStatus.LOBBY) {
            event.setCancelled(true);
            return;
         }

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

   // ==================== 大厅PVP保护 ====================

   @EventHandler
   public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
      // 记录玩家造成的伤害和受到的伤害（用于统计和虚空击杀判断）
      if (event.getEntity() instanceof Player target && event.getDamager() instanceof Player attacker) {
         PlayerData targetData = this.gameManager.getPlayerData(target.getUniqueId());
         PlayerData attackerData = this.gameManager.getPlayerData(attacker.getUniqueId());
         
         if (targetData != null && targetData.getState() == PlayerState.INGAME &&
             attackerData != null && attackerData.getState() == PlayerState.INGAME) {
            // 记录伤害用于虚空击杀判断
            targetData.recordDamage(attacker.getUniqueId());
            
            // 记录造成伤害统计
            double damage = event.getFinalDamage();
            plugin.getStatisticsSystem().recordDamageDealt(attacker, damage);
            plugin.getStatisticsSystem().recordDamageTaken(target, damage);
         }
      }
      
      // 阻止对玩家的伤害（大厅保护）
      if (event.getEntity() instanceof Player target) {
         PlayerData targetData = this.gameManager.getPlayerData(target.getUniqueId());
         if (targetData != null && targetData.getState() == PlayerState.LOBBY) {
            event.setCancelled(true);
            return;
         }

         if (this.gameManager.getGameStatus() == com.newpillar.game.enums.GameStatus.LOBBY) {
            event.setCancelled(true);
            return;
         }
      }

      // 生命偷取剑 - 造成伤害的50%转化为生命值
      if (event.getDamager() instanceof Player attacker) {
         ItemStack weapon = attacker.getInventory().getItemInMainHand();
         if (weapon != null && weapon.hasItemMeta()) {
            NamespacedKey itemKey = new NamespacedKey(plugin, "special_item");
            String itemId = weapon.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);

            if ("life_steal_sword".equals(itemId)) {
               double damage = event.getDamage();
               double healAmount = damage * 0.5;
               double newHealth = Math.min(attacker.getHealth() + healAmount, attacker.getMaxHealth());
               attacker.setHealth(newHealth);
               // 播放生命恢复音效
               attacker.playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
            }
         }
      }

      // 剧毒匕首 - 攻击附加3秒中毒效果
      if (event.getDamager() instanceof Player attacker) {
         ItemStack weapon = attacker.getInventory().getItemInMainHand();
         if (weapon != null && weapon.hasItemMeta()) {
            NamespacedKey itemKey = new NamespacedKey(plugin, "special_item");
            String itemId = weapon.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);

            if ("poison_dagger".equals(itemId) && event.getEntity() instanceof org.bukkit.entity.LivingEntity target) {
               target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0, false, false));
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
            // 造成1点伤害（半颗心）
            target.damage(1.0);
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
            // 造成1点伤害（半颗心）
            target.damage(1.0);
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

   // ==================== 实体死亡处理（玩家击杀 + 生物击杀统计）====================

   @EventHandler
   public void onEntityDeath(EntityDeathEvent event) {
      // 获取击杀者
      Player killer = event.getEntity().getKiller();
      if (killer == null) return;

      // 检查击杀者是否在游戏中
      PlayerData killerData = this.gameManager.getPlayerData(killer.getUniqueId());
      if (killerData == null || killerData.getState() != PlayerState.INGAME) {
         return;
      }

      // 处理玩家被击杀（肘击王成就检测）
      if (event.getEntity() instanceof Player victim) {
         // 检查是否是空手击杀
         ItemStack mainHand = killer.getInventory().getItemInMainHand();
         if (mainHand.getType() == Material.AIR) {
            // 空手击杀，触发肘击王成就
            plugin.getAchievementSystem().addElbowKill(killer);
         }
      } else {
         // 处理生物被击杀（不包括玩家）
         // 记录生物击杀统计
         plugin.getStatisticsSystem().recordMobKill(killer);
      }
   }

   // ==================== 物品获取成就检测 ====================

   @EventHandler
   public void onEntityPickupItem(EntityPickupItemEvent event) {
      if (!(event.getEntity() instanceof Player)) return;

      Player player = (Player) event.getEntity();
      ItemStack item = event.getItem().getItemStack();
      
      // 记录获得物品统计（只在游戏中记录）
      PlayerData data = this.gameManager.getPlayerData(player.getUniqueId());
      if (data != null && data.getState() == PlayerState.INGAME) {
         plugin.getStatisticsSystem().recordItemLooted(player);
      }

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
      // 给予向上的冲量 (Y轴速度 0.8，减小数值) - 使用 RegionScheduler 确保线程安全
      Vector velocity = player.getVelocity();
      velocity.setY(0.8);
      Bukkit.getRegionScheduler().execute(plugin, player.getLocation(), () -> {
         player.setVelocity(velocity);
      });

      // 播放音效 (与数据包一致: wind_burst)
      player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.0f, 1.0f);

      // 播放粒子效果 (与数据包一致: gust_emitter_small)
      player.getWorld().spawnParticle(Particle.GUST, player.getLocation(), 10, 0.5, 0.5, 0.5, 0.1);
   }

   // ==================== 投票书保护逻辑 ====================
   
   /**
    * 检查物品是否是投票书
    */
   private boolean isVoteBook(ItemStack item) {
      if (item == null || item.getType() != Material.BOOK) return false;
      if (!item.hasItemMeta()) return false;
      String displayName = item.getItemMeta().getDisplayName();
      return "§6§l投票".equals(displayName);
   }
   
   /**
    * 阻止投票书被丢弃，丢弃时打开GUI
    */
   @EventHandler
   public void onPlayerDropItem(PlayerDropItemEvent event) {
      Player player = event.getPlayer();
      ItemStack item = event.getItemDrop().getItemStack();
      
      if (isVoteBook(item)) {
         event.setCancelled(true);
         // 打开投票GUI
         plugin.getVoteGUI().openVoteGUI(player);
         player.sendMessage("§e点击投票书即可打开投票界面！");
      }
   }
   
   /**
    * 阻止投票书在背包中被移动，尝试移动时打开GUI
    */
   @EventHandler
   public void onInventoryClick(InventoryClickEvent event) {
      if (!(event.getWhoClicked() instanceof Player player)) return;
      
      ItemStack clickedItem = event.getCurrentItem();
      ItemStack cursorItem = event.getCursor();
      
      // 检查是否涉及投票书
      if (isVoteBook(clickedItem) || isVoteBook(cursorItem)) {
         event.setCancelled(true);
         // 打开投票GUI
         plugin.getVoteGUI().openVoteGUI(player);
         player.sendMessage("§e投票书不能被移动，点击即可投票！");
      }
   }
   
   /**
    * 阻止投票书被拖拽
    */
   @EventHandler
   public void onInventoryDrag(InventoryDragEvent event) {
      if (!(event.getWhoClicked() instanceof Player player)) return;
      
      ItemStack item = event.getOldCursor();
      if (isVoteBook(item)) {
         event.setCancelled(true);
         // 打开投票GUI
         plugin.getVoteGUI().openVoteGUI(player);
         player.sendMessage("§e投票书不能被移动，点击即可投票！");
      }
   }
   
   // ==================== 大厅保护逻辑 ====================
   
   // ==================== 方块放置/破坏统计 ====================
   
   @EventHandler
   public void onBlockPlace(BlockPlaceEvent event) {
      Player player = event.getPlayer();
      PlayerData data = this.gameManager.getPlayerData(player.getUniqueId());
      
      // 只在游戏中记录
      if (data != null && data.getState() == PlayerState.INGAME) {
         plugin.getStatisticsSystem().recordBlockPlaced(player);
      }
   }
   
   @EventHandler
   public void onBlockBreak(BlockBreakEvent event) {
      Player player = event.getPlayer();
      PlayerData data = this.gameManager.getPlayerData(player.getUniqueId());
      
      // 只在游戏中记录
      if (data != null && data.getState() == PlayerState.INGAME) {
         plugin.getStatisticsSystem().recordBlockBroken(player);
      }
   }
   
   // ==================== 海洋地图钓鱼系统 ====================

   private final Random fishingRandom = new Random();

   /**
    * 处理钓鱼事件 - 海洋地图特殊战利品
    * 检测鱼上钩后，清除鱼钩并给予战利品表物品（严格1个）
    * 同时控制钓鱼竿拉力：减小普通钓鱼竿拉力，海洋钓竿无拉力
    */
   @EventHandler
   public void onPlayerFish(PlayerFishEvent event) {
      Player player = event.getPlayer();
      PlayerFishEvent.State state = event.getState();
      
      // 处理钓到实体（玩家）的情况 - 控制拉力
      if (state == PlayerFishEvent.State.CAUGHT_ENTITY) {
         handleFishingRodPull(event);
         return;
      }
      
      // 只处理钓到物品的情况
      if (state != PlayerFishEvent.State.CAUGHT_FISH) {
         return;
      }

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

      // 使用 ItemSystem 的战利品表（与其他地图定时获取的物品池一致）
      ItemStack reward = this.plugin.getGameManager().getItemSystem().getRandomLoot();

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
   
   /**
    * 处理钓鱼竿拉力
    * - 海洋钓竿：无拉力（完全取消）
    * - 普通钓鱼竿：减小拉力到原来的30%
    */
   private void handleFishingRodPull(PlayerFishEvent event) {
      Player fisher = event.getPlayer();
      ItemStack rod = fisher.getInventory().getItemInMainHand();
      
      // 检查是否是海洋钓竿（通过显示名称判断）
      boolean isSeaFishingRod = false;
      if (rod.getType() == Material.FISHING_ROD && rod.hasItemMeta()) {
         var meta = rod.getItemMeta();
         if (meta.hasDisplayName()) {
            String displayName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(meta.displayName());
            isSeaFishingRod = displayName.contains("海洋钓竿");
         }
      }
      
      // 获取被钩中的实体
      org.bukkit.entity.Entity caughtEntity = event.getCaught();
      if (!(caughtEntity instanceof Player target)) {
         return;
      }
      
      // 检查目标玩家是否在游戏中
      PlayerData targetData = this.gameManager.getPlayerData(target.getUniqueId());
      if (targetData == null || targetData.getState() != PlayerState.INGAME) {
         return;
      }
      
      // 海洋钓竿：完全取消拉力
      if (isSeaFishingRod) {
         event.setCancelled(true);
         // 移除鱼钩，但不产生拉力
         event.getHook().remove();
         fisher.sendMessage("§7海洋钓竿无法拉动玩家");
         return;
      }
      
      // 普通钓鱼竿：减小拉力到30%
      // 取消原事件，手动应用减小的拉力
      event.setCancelled(true);
      
      // 计算减小的拉力方向
      Vector pullDirection = fisher.getLocation().toVector()
            .subtract(target.getLocation().toVector())
            .normalize();
      
      // 应用30%的拉力（原版约0.3的拉力，我们减小到0.09）
      Vector reducedPull = pullDirection.multiply(0.09).setY(0.1);
      
      // 给被钩中的玩家应用减小的速度
      target.setVelocity(target.getVelocity().add(reducedPull));
      
      // 移除鱼钩
      event.getHook().remove();
   }
   
   /**
    * 清理玩家所有属性修改
    * 在玩家出局时调用，确保所有事件添加的属性都被清除
    */
   private void cleanupPlayerAttributes(Player player) {
      // 清理体型修改
      org.bukkit.attribute.AttributeInstance scaleAttr = player.getAttribute(org.bukkit.attribute.Attribute.SCALE);
      if (scaleAttr != null) {
         scaleAttr.getModifiers().forEach(scaleAttr::removeModifier);
      }
      
      // 清理速度修改
      org.bukkit.attribute.AttributeInstance speedAttr = player.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED);
      if (speedAttr != null) {
         speedAttr.getModifiers().forEach(speedAttr::removeModifier);
      }
      
      // 清理攻击伤害修改
      org.bukkit.attribute.AttributeInstance attackDamage = player.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE);
      if (attackDamage != null) {
         attackDamage.getModifiers().forEach(attackDamage::removeModifier);
      }
      
      // 清理跳跃强度修改
      org.bukkit.attribute.AttributeInstance jumpAttr = player.getAttribute(org.bukkit.attribute.Attribute.JUMP_STRENGTH);
      if (jumpAttr != null) {
         jumpAttr.getModifiers().forEach(jumpAttr::removeModifier);
      }
      
      // 清理方块交互距离修改
      try {
         org.bukkit.attribute.Attribute blockRangeAttr = org.bukkit.attribute.Attribute.valueOf("PLAYER_BLOCK_INTERACTION_RANGE");
         org.bukkit.attribute.AttributeInstance blockRange = player.getAttribute(blockRangeAttr);
         if (blockRange != null) {
            blockRange.getModifiers().forEach(blockRange::removeModifier);
         }
      } catch (IllegalArgumentException e) {
         // 属性不存在，忽略
      }
      
      // 清理实体交互距离修改
      try {
         org.bukkit.attribute.Attribute entityRangeAttr = org.bukkit.attribute.Attribute.valueOf("PLAYER_ENTITY_INTERACTION_RANGE");
         org.bukkit.attribute.AttributeInstance entityRange = player.getAttribute(entityRangeAttr);
         if (entityRange != null) {
            entityRange.getModifiers().forEach(entityRange::removeModifier);
         }
      } catch (IllegalArgumentException e) {
         // 属性不存在，忽略
      }
      
      // 移除所有药水效果
      player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
      
      // 恢复飞行状态
      player.setAllowFlight(false);
      player.setFlying(false);
      
      plugin.getLogger().info("[属性清理] 玩家 " + player.getName() + " 出局时所有属性已清理");
   }
}
