package com.newpillar.game.events;

import com.newpillar.game.enums.EventType;

import com.newpillar.game.enums.GameStatus;

import com.newpillar.game.GameManager;

import com.newpillar.NewPillar;
import com.newpillar.utils.DebugLogger;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Wither;
import org.bukkit.entity.AbstractArrow.PickupStatus;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public class EventSystem {
   private final NewPillar plugin;
   private final GameManager gameManager;
   private final DebugLogger debugLogger;
   private final Random random = new Random();
   private ScheduledTask eventTask;
   private ScheduledTask durationTask;
   private int eventTimer = 0;
   private EventType currentEvent = null;
   private int eventDuration = 0;
   private EventType forcedNextEvent = null;
   private final List<Entity> eventEntities = new ArrayList<>();
   private final Map<UUID, Boolean> originalAllowFlight = new HashMap<>();
   private UUID lookAtMeTarget = null;
   private int anvilTickCounter = 0;
   private float rotationYawOffset = 0.0F;
   private Player currentKing = null;
   private final Map<UUID, Integer> slimeEatCount = new HashMap<>();
   private boolean kingGameTriggered = false; // 国王游戏事件是否已触发

   // BossBar 用于显示事件倒计时
   private net.kyori.adventure.bossbar.BossBar eventBossBar = null;
   private int eventTotalDuration = 0; // 事件总持续时间（用于计算进度）

   public EventSystem(NewPillar plugin, GameManager gameManager) {
      this.plugin = plugin;
      this.gameManager = gameManager;
      this.debugLogger = plugin.getDebugLogger();
   }

   /**
    * 获取当前国王
    */
   public Player getCurrentKing() {
      return this.currentKing;
   }

   /**
    * 检查国王游戏是否正在进行
    */
   public boolean isKingGameActive() {
      return this.currentEvent == EventType.KING_GAME && this.currentKing != null;
   }

   public void start() {
      this.eventTimer = this.plugin.getConfig().getInt("timers.event_time", 45);
      World world = this.gameManager.getGameWorld();
      if (world != null) {
         this.eventTask = Bukkit.getRegionScheduler().runAtFixedRate(this.plugin, world, 0, 0, scheduledTask -> {
            if (this.gameManager.getGameStatus() != GameStatus.PLAYING) {
               scheduledTask.cancel();
            } else {
               // 只有在没有事件进行时才开始倒计时
               if (this.currentEvent == null) {
                  this.eventTimer--;
                  if (this.eventTimer <= 0) {
                     this.triggerRandomEvent();
                  }
               }
            }
         }, 1L, 20L);
         this.durationTask = Bukkit.getRegionScheduler().runAtFixedRate(this.plugin, world, 0, 0, scheduledTask -> {
            if (this.gameManager.getGameStatus() != GameStatus.PLAYING) {
               scheduledTask.cancel();
            } else {
               if (this.eventDuration > 0) {
                  this.eventDuration--;
                  // 每20 ticks（1秒）更新一次 BossBar
                  if (this.eventDuration % 20 == 0) {
                     this.updateEventBossBar();
                  }
                  if (this.eventDuration <= 0) {
                     this.endCurrentEvent();
                  }
               }

               if (this.currentEvent != null) {
                  this.runEventTick();
               }

               // 始终处理史莱姆的吞噬行为（即使事件已结束，史莱姆仍然存在）
               this.tickSlimeEat();
            }
         }, 1L, 1L);
      }
   }

   public void stop() {
      if (this.eventTask != null) {
         this.eventTask.cancel();
      }

      if (this.durationTask != null) {
         this.durationTask.cancel();
      }

      this.endCurrentEvent();
      
      // 重置国王游戏触发状态
      this.kingGameTriggered = false;
   }

   public void triggerRandomEvent() {
      EventType eventType;
      if (this.forcedNextEvent != null) {
         eventType = this.forcedNextEvent;
         this.plugin.getLogger().info("[事件系统] 触发强制设置的事件: " + eventType.getName());
         this.forcedNextEvent = null;
      } else if (this.gameManager.getRuleSystem().shouldForceInvExchange()) {
         // 背包交换规则：强制触发背包交换事件
         eventType = EventType.INV_EXCHANGE;
         this.plugin.getLogger().info("[事件系统] 背包交换规则激活，强制触发背包交换事件");
      } else {
         // 检查国王游戏事件是否已触发
         if (this.kingGameTriggered) {
            // 已触发过国王游戏，从34个事件中选择（排除国王游戏）
            int eventId;
            do {
               eventId = this.random.nextInt(34) + 1;
            } while (eventId == 17); // 17是国王游戏事件ID，跳过它
            eventType = EventType.getById(eventId);
            this.plugin.getLogger().info("[事件系统] 国王游戏已触发，跳过事件17");
         } else {
            // 还未触发国王游戏，从35个事件中随机选择
            int eventId = this.random.nextInt(35) + 1;
            eventType = EventType.getById(eventId);
            
            // 如果选中国王游戏，标记为已触发
            if (eventType == EventType.KING_GAME) {
               this.kingGameTriggered = true;
               this.plugin.getLogger().info("[事件系统] 国王游戏事件已触发，本局将不再重复触发");
            }
         }
      }

      this.triggerEvent(eventType);
   }

   public void triggerEvent(EventType eventType) {
      this.endCurrentEvent();
      this.currentEvent = eventType;
      this.eventDuration = eventType.getDuration() * 20;
      this.eventTotalDuration = this.eventDuration; // 记录总持续时间用于BossBar进度

      // 创建事件BossBar
      this.createEventBossBar(eventType);

      // 显示标题和副标题（与数据包一致，使用颜色）
      String title = eventType.getColoredTitle();
      String subtitle = eventType.getDescription();
      for (Player player : Bukkit.getOnlinePlayers()) {
         player.sendTitle(title, subtitle, 10, 70, 20);
      }

      // 发送聊天消息（简化格式，与title颜色一致）
      Bukkit.broadcastMessage("§e§l[事件] §r" + title);

      for (Player player : this.getInGamePlayers()) {
         player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0F, 1.0F);
      }

      // 授予事件成就
      this.grantEventAchievements(eventType);

      this.executeEvent(eventType);

      // 对于持续时间为0的事件，立即结束
      if (eventType.getDuration() <= 0) {
         this.endCurrentEvent();
      }
   }

   /**
    * 创建事件倒计时 BossBar
    */
   private void createEventBossBar(EventType eventType) {
      // 移除旧的 BossBar
      this.removeEventBossBar();

      // 创建新的 BossBar
      net.kyori.adventure.text.Component title = net.kyori.adventure.text.Component.text("事件: " + eventType.getName());
      this.eventBossBar = net.kyori.adventure.bossbar.BossBar.bossBar(title, 1.0f, net.kyori.adventure.bossbar.BossBar.Color.YELLOW, net.kyori.adventure.bossbar.BossBar.Overlay.PROGRESS);

      // 为所有在线玩家显示 BossBar
      for (Player player : Bukkit.getOnlinePlayers()) {
         player.showBossBar(this.eventBossBar);
      }
   }

   /**
    * 更新事件 BossBar 进度
    */
   private void updateEventBossBar() {
      if (this.eventBossBar != null && this.eventTotalDuration > 0) {
         float progress = (float) this.eventDuration / this.eventTotalDuration;
         this.eventBossBar.progress(Math.max(0.0f, Math.min(1.0f, progress)));

         // 计算剩余秒数（精确到小数点后一位）
         float remainingSeconds = this.eventDuration / 20.0f;
         String timeText = String.format("%.1f", remainingSeconds);

         // 更新标题显示剩余时间
         net.kyori.adventure.text.Component title = net.kyori.adventure.text.Component.text("事件: " + (this.currentEvent != null ? this.currentEvent.getName() : "") + " - " + timeText + "秒");
         this.eventBossBar.name(title);

         // 根据剩余时间改变颜色
         if (progress > 0.6f) {
            this.eventBossBar.color(net.kyori.adventure.bossbar.BossBar.Color.GREEN);
         } else if (progress > 0.3f) {
            this.eventBossBar.color(net.kyori.adventure.bossbar.BossBar.Color.YELLOW);
         } else {
            this.eventBossBar.color(net.kyori.adventure.bossbar.BossBar.Color.RED);
         }
      }
   }

   /**
    * 移除事件 BossBar
    */
   private void removeEventBossBar() {
      if (this.eventBossBar != null) {
         for (Player player : Bukkit.getOnlinePlayers()) {
            player.hideBossBar(this.eventBossBar);
         }
         this.eventBossBar = null;
      }
   }

   /**
    * 授予事件相关成就
    */
   private void grantEventAchievements(EventType eventType) {
      if (this.plugin.getAchievementSystem() == null) return;

      switch (eventType) {
         case NUCLEAR:
            for (Player player : this.getInGamePlayers()) {
               this.plugin.getAchievementSystem().grantEventAchievement(player, "nuclear");
            }
            break;
         // 注意：KING_GAME(加冕为王)成就只在settleKingGame中授予真正的国王
         // 注意：KINGSLAYER(弑君者)事件不存在于EventType中，如果添加需要同步更新
         default:
            break;
      }
   }

   private void executeEvent(EventType eventType) {
      switch (eventType) {
         case NIGHT_FALL:
            this.eventNightFall();
            break;
         case FALLING_ANVIL:
            this.eventFallingAnvil();
            break;
         case WITHER:
            this.eventWither();
            break;
         case FLY:
            this.eventFly();
            break;
         case RAIN:
            this.eventRain();
            break;
         case SKY_WALKER:
            this.eventSkyWalker();
            break;
         case ROTATION:
            this.eventRotation();
            break;
         case LIGHTNING:
            this.eventLightning();
            break;
         case HELLO_WORLD:
            this.eventHelloWorld();
            break;
         case UNDEAD:
            this.eventUndead();
            break;
         case BROKEN_LEG:
            this.eventBrokenLeg();
            break;
         case PUNCH:
            this.eventPunch();
            break;
         case CREEPER:
            this.eventCreeper();
            break;
         case TOUCH:
            this.eventTouch();
            break;
         case INV_EXCHANGE:
            this.eventInvExchange();
            break;
         case KING_GAME:
            this.eventKingGame();
            break;
         case LUCKY_DOLL:
            this.eventLuckyDoll();
            break;
         case HUNGRY:
            this.eventHungry();
            break;
         case BLACK:
            this.eventBlack();
            break;
         case SPEED:
            this.eventSpeed();
            break;
         case MINI:
            this.eventMini();
            break;
         case HUGE:
            this.eventHuge();
            break;
         case NUCLEAR:
            this.eventNuclear();
            break;
         case GREEDY_SLIME:
            this.eventGreedySlime();
            break;
         case LOCATION_EXCHANGE:
            this.eventLocationExchange();
            break;
         case LAVA_RISE:
            this.eventLavaRise();
            break;
         case LOOK_AT_ME:
            this.eventLookAtMe();
            break;
         case FIRED:
            this.eventFired();
            break;
         case KEY_INVERSION:
            this.eventKeyInversion();
            break;
         case ALWAYS_EXPLODE:
            this.eventAlwaysExplode();
            break;
         case NOTHING_31:
         case NOTHING_32:
         case NOTHING_33:
         case NOTHING_34:
         case NOTHING_35:
            break;
      }
   }

   private void runEventTick() {
      if (this.currentEvent != null) {
         switch (this.currentEvent) {
            case FALLING_ANVIL:
               this.tickFallingAnvil();
            case WITHER:
            case FLY:
            case HELLO_WORLD:
            case UNDEAD:
            case PUNCH:
            case CREEPER:
            case TOUCH:
            case INV_EXCHANGE:
            case KING_GAME:
            case LUCKY_DOLL:
            case HUNGRY:
            case BLACK:
            case SPEED:
            case MINI:
            case HUGE:
            case NUCLEAR:
            case LOCATION_EXCHANGE:
            case FIRED:
            default:
               break;
            case RAIN:
               this.tickRain();
               break;
            case SKY_WALKER:
               this.tickSkyWalker();
               break;
            case ROTATION:
               this.tickRotation();
               break;
            case LIGHTNING:
               this.tickLightning();
               break;
            case BROKEN_LEG:
               this.tickBrokenLeg();
               break;
            case GREEDY_SLIME:
               this.tickGreedySlime();
               break;
            case LAVA_RISE:
               this.tickLavaRise();
               break;
            case LOOK_AT_ME:
               this.tickLookAtMe();
               break;
            case KEY_INVERSION:
               this.tickKeyInversion();
         }
      }
   }

   private void endCurrentEvent() {
      if (this.currentEvent != null) {
         this.plugin.getLogger().info("[事件系统] 开始结束事件: " + this.currentEvent.getName());

         // 移除事件 BossBar
         this.removeEventBossBar();

         // 不再清除召唤的生物，让它们继续存在
         this.eventEntities.clear();

         for (Player player : this.getInGamePlayers()) {
            this.debugLogger.debug("[事件系统] 恢复玩家属性: " + player.getName());
            AttributeInstance scaleAttr = player.getAttribute(Attribute.SCALE);
            if (scaleAttr != null) {
               long scaleModCount = scaleAttr.getModifiers()
                  .stream()
                  .filter(mod -> Math.abs(mod.getAmount() - -0.8) < 0.001 || Math.abs(mod.getAmount() - 4.0) < 0.001)
                  .peek(mod -> {
                     scaleAttr.removeModifier(mod);
                     this.debugLogger.debug("[事件系统] 移除玩家 " + player.getName() + " 的体型修改器: 数值=" + mod.getAmount());
                  })
                  .count();
               this.debugLogger.debug("[事件系统] 玩家 " + player.getName() + " 移除了 " + scaleModCount + " 个体型修改器");
            }

            AttributeInstance speedAttr = player.getAttribute(Attribute.MOVEMENT_SPEED);
            if (speedAttr != null) {
               long speedModCount = speedAttr.getModifiers().stream().filter(mod -> Math.abs(mod.getAmount() - 0.2) < 0.001).peek(mod -> {
                  speedAttr.removeModifier(mod);
                  this.debugLogger.debug("[事件系统] 移除玩家 " + player.getName() + " 的速度修改器: 数值=" + mod.getAmount());
               }).count();
               this.debugLogger.debug("[事件系统] 玩家 " + player.getName() + " 移除了 " + speedModCount + " 个速度修改器");
            }

            AttributeInstance attackDamage = player.getAttribute(Attribute.ATTACK_DAMAGE);
            if (attackDamage != null) {
               this.debugLogger.debug("[事件系统] 玩家 " + player.getName() + " 当前攻击伤害修改器数量: " + attackDamage.getModifiers().size());
               long attackModCount = attackDamage.getModifiers().stream().filter(mod -> Math.abs(mod.getAmount() - 39.0) < 0.001).peek(mod -> {
                  attackDamage.removeModifier(mod);
                  this.debugLogger.debug("[事件系统] 移除玩家 " + player.getName() + " 的攻击伤害修改器: 数值=" + mod.getAmount());
               }).count();
               this.debugLogger.debug("[事件系统] 玩家 " + player.getName() + " 移除了 " + attackModCount + " 个攻击伤害修改器");
               this.debugLogger.debug("[事件系统] 玩家 " + player.getName() + " 剩余攻击伤害修改器数量: " + attackDamage.getModifiers().size());
            }

            AttributeInstance jumpAttr = player.getAttribute(Attribute.JUMP_STRENGTH);
            if (jumpAttr != null) {
               long jumpModCount = jumpAttr.getModifiers().stream().filter(mod -> Math.abs(mod.getAmount() - -1.0) < 0.001).peek(mod -> {
                  jumpAttr.removeModifier(mod);
                  this.debugLogger.debug("[事件系统] 移除玩家 " + player.getName() + " 的跳跃强度修改器: 数值=" + mod.getAmount());
               }).count();
               this.debugLogger.debug("[事件系统] 玩家 " + player.getName() + " 移除了 " + jumpModCount + " 个跳跃强度修改器");
            }

            try {
               Attribute blockRangeAttr = Attribute.valueOf("PLAYER_BLOCK_INTERACTION_RANGE");
               AttributeInstance blockRange = player.getAttribute(blockRangeAttr);
               if (blockRange != null) {
                  // 清除所有事件添加的方块交互距离修改器
                  long blockRangeModCount = blockRange.getModifiers().stream().filter(mod -> 
                     mod.getName().startsWith("event_") ||
                     Math.abs(mod.getAmount() - 4.0) < 0.001 ||
                     Math.abs(mod.getAmount() - 10.0) < 0.001
                  ).peek(mod -> {
                     blockRange.removeModifier(mod);
                     this.debugLogger.debug("[事件系统] 移除玩家 " + player.getName() + " 的方块交互距离修改器: 名称=" + mod.getName() + ", 数值=" + mod.getAmount());
                  }).count();
                  this.debugLogger.debug("[事件系统] 玩家 " + player.getName() + " 移除了 " + blockRangeModCount + " 个方块交互距离修改器");
               }
            } catch (IllegalArgumentException var12) {
            }

            try {
               Attribute entityRangeAttr = Attribute.valueOf("PLAYER_ENTITY_INTERACTION_RANGE");
               AttributeInstance entityRange = player.getAttribute(entityRangeAttr);
               if (entityRange != null) {
                  // 清除所有事件添加的实体交互距离修改器（包括摸摸事件的+10和国王游戏的+4）
                  long entityRangeModCount = entityRange.getModifiers().stream().filter(mod -> 
                     mod.getName().equals("event_touch_entity") || 
                     mod.getName().equals("event_king_entity") ||
                     Math.abs(mod.getAmount() - 4.0) < 0.001 ||
                     Math.abs(mod.getAmount() - 10.0) < 0.001
                  ).peek(mod -> {
                     entityRange.removeModifier(mod);
                     this.debugLogger.debug("[事件系统] 移除玩家 " + player.getName() + " 的实体交互距离修改器: 名称=" + mod.getName() + ", 数值=" + mod.getAmount());
                  }).count();
                  this.debugLogger.debug("[事件系统] 玩家 " + player.getName() + " 移除了 " + entityRangeModCount + " 个实体交互距离修改器");
               }
            } catch (IllegalArgumentException var11) {
            }

            if (this.originalAllowFlight.containsKey(player.getUniqueId())) {
               player.setAllowFlight(this.originalAllowFlight.get(player.getUniqueId()));
               player.setFlying(false);
               this.debugLogger.debug("[事件系统] 恢复玩家 " + player.getName() + " 的飞行状态");
            }

            player.removePotionEffect(PotionEffectType.SLOWNESS);
            player.removePotionEffect(PotionEffectType.SPEED);
            player.removePotionEffect(PotionEffectType.DARKNESS);
            player.removePotionEffect(PotionEffectType.BLINDNESS);
            player.removePotionEffect(PotionEffectType.JUMP_BOOST);
            player.removePotionEffect(PotionEffectType.WITHER);
            player.removePotionEffect(PotionEffectType.GLOWING);
            player.removePotionEffect(PotionEffectType.LEVITATION);
         }

         this.originalAllowFlight.clear();
         World world = this.gameManager.getGameWorld();
         if (world != null) {
            Bukkit.getGlobalRegionScheduler().run(this.plugin, scheduledTask -> {
               world.setStorm(false);
               world.setThundering(false);
            });
         }

         if (this.currentEvent == EventType.KING_GAME) {
            this.settleKingGame();
         }

         if (this.currentEvent == EventType.LAVA_RISE) {
            this.clearLavaRise();
         }

         this.gameManager.setLookAtMeTarget(null);
         this.gameManager.setKeyInversionActive(false);
         this.currentEvent = null;
         this.eventDuration = 0;
         
         // 事件结束后重置事件间隔计时器
         this.eventTimer = this.plugin.getConfig().getInt("timers.event_time", 45);
         this.debugLogger.eventInfo("[事件系统] 事件结束，下一事件计时器已重置为: " + this.eventTimer + "秒");
      }
   }

   private void eventNightFall() {
      this.debugLogger.eventInfo("[事件1-夜晚降临] 开始执行，玩家数: " + this.getInGamePlayers().size());
      World world = this.gameManager.getGameWorld();
      if (world != null) {
         Bukkit.getGlobalRegionScheduler().run(this.plugin, scheduledTask -> {
            world.setTime(18000L);
            this.plugin.getLogger().info("[事件1-夜晚降临] 时间已设置为午夜 (18000)");
         });
         List<Player> players = this.getInGamePlayers();
         if (!players.isEmpty()) {
            Player target = players.get(this.random.nextInt(players.size()));
            Location playerLoc = target.getLocation();
            Location spawnLoc = new Location(world, playerLoc.getX(), playerLoc.getY() + 10.0, playerLoc.getZ());
            this.plugin
               .getLogger()
               .info(
                  "[事件1-夜晚降临] 选中玩家: "
                     + target.getName()
                     + " 玩家位置: "
                     + playerLoc.getBlockX()
                     + ","
                     + playerLoc.getBlockY()
                     + ","
                     + playerLoc.getBlockZ()
                     + " 幻翼生成位置(相对Y+10): "
                     + spawnLoc.getBlockX()
                     + ","
                     + spawnLoc.getBlockY()
                     + ","
                     + spawnLoc.getBlockZ()
               );
            Bukkit.getRegionScheduler().execute(this.plugin, spawnLoc, () -> {
               for (int i = 0; i < 2; i++) {
                  try {
                     Phantom phantom = (Phantom)world.spawnEntity(spawnLoc, EntityType.PHANTOM);
                     if (phantom != null) {
                        phantom.setTicksLived(1);
                        this.eventEntities.add(phantom);
                        this.plugin.getLogger().info("[事件1-夜晚降临] 已生成幻翼 " + (i + 1) + "/2，实体ID: " + phantom.getEntityId());
                     } else {
                        this.plugin.getLogger().warning("[事件1-夜晚降临] 幻翼生成失败，返回null " + (i + 1) + "/2");
                     }
                  } catch (Exception var5x) {
                     this.plugin.getLogger().warning("[事件1-夜晚降临] 幻翼生成异常 " + (i + 1) + "/2: " + var5x.getMessage());
                     var5x.printStackTrace();
                  }
               }
            });
         } else {
            this.plugin.getLogger().warning("[事件1-夜晚降临] 没有玩家，无法生成幻翼");
         }
      } else {
         this.plugin.getLogger().warning("[事件1-夜晚降临] 世界为空，无法执行");
      }
   }

   private void eventFallingAnvil() {
      this.debugLogger.eventInfo("[事件2-铁砧下落] 开始执行，玩家数: " + this.getInGamePlayers().size());
      this.anvilTickCounter = 0;
   }

   private void tickFallingAnvil() {
      this.anvilTickCounter++;
      if (this.anvilTickCounter % 10 == 0) {
         for (Player player : this.getInGamePlayers()) {
            Location playerLoc = player.getLocation();
            int offsetX;
            int offsetZ;
            if (this.random.nextDouble() < 0.25) {
               offsetX = this.random.nextInt(3) - 1;
               offsetZ = this.random.nextInt(3) - 1;
            } else {
               offsetX = this.random.nextInt(11) - 5;
               offsetZ = this.random.nextInt(11) - 5;
            }

            int offsetY = 5 + this.random.nextInt(6);
            Location anvilLoc = playerLoc.clone().add((double)offsetX, (double)offsetY, (double)offsetZ);
            Bukkit.getRegionScheduler().execute(this.plugin, anvilLoc, () -> {
               for (int y = 0; y < offsetY; y++) {
                  Location clearLoc = anvilLoc.clone().subtract(0.0, (double)y, 0.0);
                  clearLoc.getBlock().setType(Material.AIR);
               }

               anvilLoc.getBlock().setType(Material.ANVIL);
            });
         }
      }
   }

   private void eventWither() {
      this.debugLogger.eventInfo("[事件4-凋灵] 开始执行，玩家数: " + this.getInGamePlayers().size());
      World world = this.gameManager.getGameWorld();
      if (world != null) {
         List<Player> players = this.getInGamePlayers();
         if (players.isEmpty()) {
            this.plugin.getLogger().warning("[事件4-凋灵] 没有玩家，无法执行");
            return;
         }

         Player target = players.get(this.random.nextInt(players.size()));
         Location playerLoc = target.getLocation();
         Location loc = new Location(world, playerLoc.getX(), playerLoc.getY() + 10.0, playerLoc.getZ());
         this.plugin
            .getLogger()
            .info(
               "[事件4-凋灵] 选中玩家: "
                  + target.getName()
                  + " 玩家位置: "
                  + playerLoc.getBlockX()
                  + ","
                  + playerLoc.getBlockY()
                  + ","
                  + playerLoc.getBlockZ()
                  + " 凋灵生成位置(相对Y+10): "
                  + loc.getBlockX()
                  + ","
                  + loc.getBlockY()
                  + ","
                  + loc.getBlockZ()
            );
         Bukkit.getRegionScheduler().execute(this.plugin, loc, () -> {
            Wither wither = (Wither)world.spawnEntity(loc, EntityType.WITHER);
            wither.setTarget(target);
            this.eventEntities.add(wither);
            this.plugin.getLogger().info("[事件4-凋灵] 凋灵已生成，目标: " + target.getName());
         });
      }
   }

   private void eventFly() {
      this.debugLogger.eventInfo("[事件5-飞行模式] 开始执行，玩家数: " + this.getInGamePlayers().size());

      for (Player player : this.getInGamePlayers()) {
         Location loc = player.getLocation();
         this.plugin.getLogger().info("[事件5-飞行模式] 玩家: " + player.getName() + " 给予鞘翅和3个烟花");
         Bukkit.getRegionScheduler().execute(this.plugin, loc, () -> {
            ItemStack elytra = new ItemStack(Material.ELYTRA);
            elytra.editMeta(meta -> meta.setDisplayName("§b§l自由的风"));
            player.getInventory().addItem(new ItemStack[]{elytra});
            this.plugin.getLogger().info("[事件5-飞行模式] 玩家: " + player.getName() + " 已获得鞘翅");
            ItemStack fireworks = new ItemStack(Material.FIREWORK_ROCKET, 3);
            player.getInventory().addItem(new ItemStack[]{fireworks});
            player.sendMessage("§b自由的风！获得鞘翅和烟花！");
         });
      }
   }

   private void eventRain() {
   }

   private void tickRain() {
      if (this.eventDuration % 10 == 0) {
         for (Player player : this.getInGamePlayers()) {
            Location playerLoc = player.getLocation();

            for (int i = 0; i < 5; i++) {
               double offsetX;
               double offsetZ;
               if (this.random.nextDouble() < 0.5) {
                  offsetX = (this.random.nextDouble() - 0.5) * 4.0;
                  offsetZ = (this.random.nextDouble() - 0.5) * 4.0;
               } else {
                  offsetX = (this.random.nextDouble() - 0.5) * 20.0;
                  offsetZ = (this.random.nextDouble() - 0.5) * 20.0;
               }

               double offsetY = 15.0 + this.random.nextDouble() * 10.0;
               Location arrowLoc = playerLoc.clone().add(offsetX, offsetY, offsetZ);
               Bukkit.getRegionScheduler().execute(this.plugin, arrowLoc, () -> {
                  Arrow arrow = player.getWorld().spawnArrow(arrowLoc, new Vector(0, -1, 0), 0.8F, 12.0F);
                  arrow.setCritical(true);
                  arrow.setPickupStatus(PickupStatus.DISALLOWED);
                  arrow.setDamage(2.0);
                  this.eventEntities.add(arrow);
               });
            }
         }
      }
   }

   private void eventSkyWalker() {
      this.debugLogger.eventInfo("[事件7-踏空] 开始执行，玩家数: " + this.getInGamePlayers().size());

      for (Player player : this.getInGamePlayers()) {
         Location loc = player.getLocation();
         this.plugin
            .getLogger()
            .info("[事件7-踏空] 玩家: " + player.getName() + " 位置: " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + " 给予漂浮效果10秒");
         player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 200, 0));
         player.sendMessage("§e你可以在空中行走了！");
      }
   }

   private void tickSkyWalker() {
   }

   private void eventRotation() {
      this.debugLogger.eventInfo("[事件8-自转] 开始执行，玩家数: " + this.getInGamePlayers().size());

      for (Player player : this.getInGamePlayers()) {
         Location loc = player.getLocation();
         this.plugin.getLogger().info("[事件8-自转] 玩家: " + player.getName() + " 初始朝向: " + loc.getYaw());
      }
   }

   private void tickRotation() {
      this.rotationYawOffset += 2.0F;
      if (this.rotationYawOffset >= 360.0F) {
         this.rotationYawOffset -= 360.0F;
      }

      for (Player player : this.getInGamePlayers()) {
         Location loc = player.getLocation();
         float newYaw = loc.getYaw() + 2.0F;

         try {
            Method setRotationMethod = player.getClass().getMethod("setRotation", float.class, float.class);
            setRotationMethod.invoke(player, newYaw, loc.getPitch());
         } catch (Exception var7) {
            Location newLoc = loc.clone();
            newLoc.setYaw(newYaw);
            player.teleportAsync(newLoc, TeleportCause.PLUGIN);
         }
      }
   }

   private void eventLightning() {
      this.debugLogger.eventInfo("[事件9-雷击] 开始执行，玩家数: " + this.getInGamePlayers().size());

      for (Player player : this.getInGamePlayers()) {
         Location loc = player.getLocation();
         this.plugin.getLogger().info("[事件9-雷击] 玩家: " + player.getName() + " 位置: " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + " 召唤雷电");
         Bukkit.getRegionScheduler().execute(this.plugin, loc, () -> {
            player.getWorld().strikeLightning(loc);
            this.plugin.getLogger().info("[事件9-雷击] 已在 " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + " 生成雷电");
         });
      }
   }

   private void tickLightning() {
   }

   private void eventHelloWorld() {
      this.debugLogger.eventInfo("[事件10-你好世界] 开始执行，玩家数: " + this.getInGamePlayers().size());
      World world = this.gameManager.getGameWorld();
      if (world != null) {
         Bukkit.getGlobalRegionScheduler().run(this.plugin, scheduledTask -> {
            world.setTime(1000L);
            this.plugin.getLogger().info("[事件10-你好世界] 设置时间为白天 (1000)");
         });
      }

      for (Player player : this.getInGamePlayers()) {
         Location loc = player.getLocation();
         this.plugin.getLogger().info("[事件10-你好世界] 玩家: " + player.getName() + " 给予瞬间治疗效果");
         Bukkit.getRegionScheduler().execute(this.plugin, loc, () -> {
            player.addPotionEffect(new PotionEffect(PotionEffectType.INSTANT_HEALTH, 1, 4));
            player.sendMessage("§a你好世界！");
         });
      }
   }

   private void eventUndead() {
      this.debugLogger.eventInfo("[事件10-不死] 开始执行，玩家数: " + this.getInGamePlayers().size());

      for (Player player : this.getInGamePlayers()) {
         Location loc = player.getLocation();
         this.plugin.getLogger().info("[事件10-不死] 玩家: " + player.getName() + " 给予不死图腾");
         Bukkit.getRegionScheduler().execute(this.plugin, loc, () -> {
            ItemStack totem = new ItemStack(Material.TOTEM_OF_UNDYING);
            player.getInventory().addItem(new ItemStack[]{totem});
            player.sendMessage("§6不死图腾！+1条命！");
         });
      }
   }

   private void eventBrokenLeg() {
      this.debugLogger.eventInfo("[事件12-骨折] 开始执行，玩家数: " + this.getInGamePlayers().size());

      for (Player player : this.getInGamePlayers()) {
         player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 400, 2));
         AttributeInstance jumpAttr = player.getAttribute(Attribute.JUMP_STRENGTH);
         if (jumpAttr != null) {
            jumpAttr.getModifiers().stream().filter(mod -> mod.getName().equals("event_broken_leg")).forEach(jumpAttr::removeModifier);
            jumpAttr.addModifier(new AttributeModifier(UUID.randomUUID(), "event_broken_leg", -0.5, Operation.ADD_NUMBER));
         }

         player.sendMessage("§c你摔断了腿！移动变慢且无法跳跃！");
      }
   }

   private void eventPunch() {
      this.debugLogger.eventInfo("[事件13-一击必杀] 开始执行，玩家数: " + this.getInGamePlayers().size());

      for (Player player : this.getInGamePlayers()) {
         Location loc = player.getLocation();
         this.plugin.getLogger().info("[事件13-一击必杀] 玩家: " + player.getName() + " 设置攻击伤害为40");
         AttributeInstance attackDamage = player.getAttribute(Attribute.ATTACK_DAMAGE);
         if (attackDamage != null) {
            attackDamage.getModifiers().stream().filter(mod -> mod.getName().equals("event_punch")).forEach(attackDamage::removeModifier);
            attackDamage.addModifier(new AttributeModifier(UUID.randomUUID(), "event_punch", 39.0, Operation.ADD_NUMBER));
            this.plugin.getLogger().info("[事件13-一击必杀] 玩家: " + player.getName() + " 攻击伤害已设置为40 (基础1 + 修改器39)");
         }

         player.sendMessage("§c一击必杀！攻击伤害变为40！");
      }
   }

   private void eventCreeper() {
      this.debugLogger.eventInfo("[事件14-苦力怕] 开始执行，玩家数: " + this.getInGamePlayers().size());

      for (Player player : this.getInGamePlayers()) {
         Location loc = player.getLocation().clone().add(0.0, 0.5, 0.0);
         this.plugin
            .getLogger()
            .info("[事件14-苦力怕] 玩家: " + player.getName() + " 位置: " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + " 召唤苦力怕");
         Bukkit.getRegionScheduler().execute(this.plugin, loc, () -> {
            Creeper creeper = (Creeper)player.getWorld().spawnEntity(loc, EntityType.CREEPER);
            boolean powered = this.random.nextDouble() < 0.1;
            if (powered) {
               creeper.setPowered(true);
            }

            this.eventEntities.add(creeper);
            this.plugin.getLogger().info("[事件14-苦力怕] 已在 " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + " 生成苦力怕，带电: " + powered);
         });
      }
   }

   private void eventTouch() {
      this.debugLogger.eventInfo("[事件15-摸摸] 开始执行，玩家数: " + this.getInGamePlayers().size());

      for (Player player : this.getInGamePlayers()) {
         this.plugin.getLogger().info("[事件15-摸摸] 玩家: " + player.getName() + " 增加实体交互距离 +10");

         try {
            Attribute entityRangeAttr = Attribute.valueOf("PLAYER_ENTITY_INTERACTION_RANGE");
            AttributeInstance entityRange = player.getAttribute(entityRangeAttr);
            if (entityRange != null) {
               entityRange.addModifier(new AttributeModifier(UUID.randomUUID(), "event_touch_entity", 10.0, Operation.ADD_NUMBER));
               this.plugin.getLogger().info("[事件15-摸摸] 玩家: " + player.getName() + " 实体交互距离已增加10格");
            }
         } catch (IllegalArgumentException var5) {
            this.plugin.getLogger().warning("[事件15-摸摸] PLAYER_ENTITY_INTERACTION_RANGE 属性不存在");
         }

         player.sendMessage("§e摸摸！交互距离增加了10格！");
      }
   }

   private void eventInvExchange() {
      this.debugLogger.eventInfo("[事件16-背包交换] 开始执行，玩家数: " + this.getInGamePlayers().size());
      List<Player> players = this.getInGamePlayers();
      if (players.size() < 2) {
         this.plugin.getLogger().warning("[事件16-背包交换] 玩家数量不足2人，无法交换");
      } else {
         Player p1 = players.get(this.random.nextInt(players.size()));
         Player p2 = players.get(this.random.nextInt(players.size()));

         while (p1 == p2) {
            p2 = players.get(this.random.nextInt(players.size()));
         }

         this.plugin.getLogger().info("[事件16-背包交换] 交换玩家: " + p1.getName() + " <-> " + p2.getName());
         PlayerInventory inv1 = p1.getInventory();
         PlayerInventory inv2 = p2.getInventory();
         ItemStack[] contents1 = inv1.getContents();
         ItemStack[] contents2 = inv2.getContents();
         Player finalP2 = p2;
         Location loc1 = p1.getLocation();
         Bukkit.getRegionScheduler().execute(this.plugin, loc1, () -> {
            inv1.setContents(contents2);
            p1.sendMessage("§d你和 " + finalP2.getName() + " 交换了背包！");
            this.plugin.getLogger().info("[事件16-背包交换] " + p1.getName() + " 已获得 " + finalP2.getName() + " 的背包");
         });
         Location loc2 = p2.getLocation();
         Bukkit.getRegionScheduler().execute(this.plugin, loc2, () -> {
            inv2.setContents(contents1);
            finalP2.sendMessage("§d你和 " + p1.getName() + " 交换了背包！");
            this.plugin.getLogger().info("[事件16-背包交换] " + finalP2.getName() + " 已获得 " + p1.getName() + " 的背包");
         });
      }
   }

   private void eventKingGame() {
      this.debugLogger.eventInfo("[事件17-国王游戏] 开始执行，玩家数: " + this.getInGamePlayers().size());
      List<Player> players = this.getInGamePlayers();
      if (players.isEmpty()) {
         this.plugin.getLogger().warning("[事件17-国王游戏] 没有玩家，无法执行");
      } else {
         this.currentKing = players.get(this.random.nextInt(players.size()));
         this.plugin.getLogger().info("[事件17-国王游戏] 选中国王: " + this.currentKing.getName());
         Player finalKing = this.currentKing;
         Location kingLoc = this.currentKing.getLocation();
         Bukkit.getRegionScheduler()
            .execute(
               this.plugin,
               kingLoc,
               () -> {
                  finalKing.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0));
                  this.plugin.getLogger().info("[事件17-国王游戏] 国王 " + finalKing.getName() + " 已获得发光效果");
                  ItemStack fakeCrown = new ItemStack(Material.COPPER_HELMET);
                  fakeCrown.editMeta(
                     meta -> {
                        meta.setDisplayName("§7§l伪·王冠");
                        meta.setLore(Arrays.asList("§c『欲戴王冠 必承其重』"));
                        meta.addEnchant(Enchantment.BINDING_CURSE, 1, true);
                        // 清除铜头盔的默认属性，只保留王冠属性
                        meta.setAttributeModifiers(null);
                        meta.addAttributeModifier(
                           Attribute.ARMOR, new AttributeModifier(UUID.randomUUID(), "fake_crown_armor", 2.0, Operation.ADD_NUMBER)
                        );
                        meta.addAttributeModifier(
                           Attribute.ARMOR_TOUGHNESS, new AttributeModifier(UUID.randomUUID(), "fake_crown_toughness", 2.0, Operation.ADD_NUMBER)
                        );
                        meta.addAttributeModifier(
                           Attribute.MAX_HEALTH, new AttributeModifier(UUID.randomUUID(), "fake_crown_health", 5.0, Operation.ADD_NUMBER)
                        );
                     }
                  );
                  EntityEquipment equipment = finalKing.getEquipment();
                  if (equipment != null) {
                     equipment.setHelmet(fakeCrown);
                     this.plugin.getLogger().info("[事件17-国王游戏] 国王 " + finalKing.getName() + " 已装备伪王冠");
                  }
               }
            );
         int rebelCount = 0;

         for (Player player : players) {
            if (player != this.currentKing) {
               rebelCount++;
               Location loc = player.getLocation();
               Bukkit.getRegionScheduler().execute(this.plugin, loc, () -> player.getInventory().addItem(new ItemStack[]{new ItemStack(Material.STONE, 16)}));
            }
         }

         this.plugin.getLogger().info("[事件17-国王游戏] " + rebelCount + " 名叛军获得16个石头");
         Bukkit.broadcastMessage("§6§l" + this.currentKing.getName() + " 成为国王！其他玩家获得石头攻击国王！");
      }
   }

   private void settleKingGame() {
      if (this.currentKing != null) {
         boolean kingAlive = this.getInGamePlayers().contains(this.currentKing);
         if (kingAlive) {
            Player king = this.currentKing;
            Location kingLoc = king.getLocation();
            Bukkit.getRegionScheduler()
               .execute(
                  this.plugin,
                  kingLoc,
                  () -> {
                     if (king.isOnline()) {
                        king.removePotionEffect(PotionEffectType.GLOWING);
                        ItemStack realCrown = new ItemStack(Material.GOLDEN_HELMET);
                        realCrown.editMeta(
                           meta -> {
                              meta.setDisplayName("§6§l王冠");
                              meta.setLore(Arrays.asList("§c『欲戴王冠 必承其重』"));
                              meta.addEnchant(Enchantment.BINDING_CURSE, 1, true);
                              // 清除金头盔的默认属性，只保留王冠属性
                              meta.setAttributeModifiers(null);
                              meta.addAttributeModifier(
                                 Attribute.ARMOR, new AttributeModifier(UUID.randomUUID(), "real_crown_armor", 4.0, Operation.ADD_NUMBER)
                              );
                              meta.addAttributeModifier(
                                 Attribute.ARMOR_TOUGHNESS, new AttributeModifier(UUID.randomUUID(), "real_crown_toughness", 4.0, Operation.ADD_NUMBER)
                              );
                              meta.addAttributeModifier(
                                 Attribute.ATTACK_DAMAGE, new AttributeModifier(UUID.randomUUID(), "real_crown_attack", 3.0, Operation.ADD_NUMBER)
                              );
                              meta.addAttributeModifier(
                                 Attribute.MAX_HEALTH, new AttributeModifier(UUID.randomUUID(), "real_crown_health", 10.0, Operation.ADD_NUMBER)
                              );
                           }
                        );
                        EntityEquipment equipment = king.getEquipment();
                        if (equipment != null) {
                           equipment.setHelmet(realCrown);
                        }

                        Bukkit.broadcastMessage("§6§l[国王游戏] " + king.getName() + " 加冕成功！");
                        
                        // 授予国王成就
                        if (plugin.getAchievementSystem() != null) {
                           plugin.getAchievementSystem().grantEventAchievement(king, "king_game");
                        }
                     }
                  }
               );
         } else {
            Bukkit.broadcastMessage("§c§l[国王游戏] 国王卫冕失败！");

            for (Player player : this.getInGamePlayers()) {
               Location loc = player.getLocation();
               Bukkit.getRegionScheduler().execute(this.plugin, loc, () -> {
                  // 给予叛军奖励：抗性30s, 力量30s, 生命回复10s, 饱和5s
                  player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 600, 0, false, false));
                  player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 600, 0, false, false));
                  player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 0, false, false));
                  player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 100, 0, false, false));
               });
            }
         }

         this.currentKing = null;
      }
   }

   private void eventLuckyDoll() {
      this.debugLogger.eventInfo("[事件18-幸运玩偶] 开始执行，玩家数: " + this.getInGamePlayers().size());
      String[] playerIds = new String[]{
         "fishing886_",
         "YuWan_SAMA",
         "home1247",
         "XuetiaoG",
         "TheXiaoYu0v0_",
         "pingsanyi",
         "Rachel521",
         "BingHuoX",
         "mc_mdba",
         "bulu__boom",
         "TheTaiZiY",
         "CraftSuperkulou",
         "HashBrown0v0",
         "M14_Mod3",
         "Mayro_neko",
         "iamakiller1654",
         "carefree_lonely",
         "efsdw",
         "FTHR_Linncdr",
         "Hart_GS",
         "angeng233",
         "baokaixin",
         "si_feng",
         "tianxiao123",
         "offline_anytime",
         "Tartgralia",
         "Dougllase",
         "田所浩二"
      };
      String[] greetings = new String[]{
         "你好:)",
         "你好:)",
         "你好:)",
         "这里已经满员了",
         "你好:)",
         "你好:)",
         "你好:)",
         "你好:)",
         "你好:)",
         "你好:)",
         "你好:)",
         "祝君好运:）",
         "土豆地雷",
         "你好:)",
         "你好:)",
         "你好:)",
         "你好:)",
         "你好:)",
         "你好:)",
         "你好:)",
         "你好:)",
         "你好:)",
         "你好:)",
         "你好:)",
         "你好:)",
         "你好:)",
         "你好:)",
         "你好:)"
      };

      for (Player player : this.getInGamePlayers()) {
         Location loc = player.getLocation();
         int index = this.random.nextInt(playerIds.length);
         String playerId = playerIds[index];
         String greeting = greetings[index];
         this.plugin.getLogger().info("[事件18-幸运玩偶] 玩家: " + player.getName() + " 获得幸运玩偶 " + playerId);
         Bukkit.getRegionScheduler().execute(this.plugin, loc, () -> {
            ItemStack doll = new ItemStack(Material.TOTEM_OF_UNDYING);
            doll.editMeta(meta -> {
               meta.setDisplayName("§6§l幸运玩偶");
               meta.setLore(Arrays.asList("§a" + greeting, "§b玩家ID：§b" + playerId));
            });
            player.getInventory().addItem(new ItemStack[]{doll});
            this.plugin.getLogger().info("[事件18-幸运玩偶] 玩家: " + player.getName() + " 获得幸运玩偶 " + playerId);
            player.sendMessage("§a幸运降临！你获得了幸运玩偶 §b" + playerId);
         });
      }
   }

   private void eventHungry() {
      this.debugLogger.eventInfo("[事件19-饿啊饿啊] 开始执行，玩家数: " + this.getInGamePlayers().size());

      for (Player player : this.getInGamePlayers()) {
         Location loc = player.getLocation();
         this.plugin.getLogger().info("[事件19-饿啊饿啊] 玩家: " + player.getName() + " 给予饥饿效果30秒等级40");
         Bukkit.getRegionScheduler().execute(this.plugin, loc, () -> {
            player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 600, 40));
            player.sendMessage("§c饿啊饿啊！饥饿效果！");
         });
      }
   }

   private void eventBlack() {
      this.debugLogger.eventInfo("[事件20-黑] 开始执行，玩家数: " + this.getInGamePlayers().size());

      for (Player player : this.getInGamePlayers()) {
         Location loc = player.getLocation();
         this.plugin.getLogger().info("[事件20-黑] 玩家: " + player.getName() + " 黑暗效果");
         Bukkit.getRegionScheduler().execute(this.plugin, loc, () -> {
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 200, 0));
            player.sendMessage("§0真的黑！");
         });
      }
   }

   private void eventSpeed() {
      this.debugLogger.eventInfo("[事件21-速度] 开始执行，玩家数: " + this.getInGamePlayers().size());

      for (Player player : this.getInGamePlayers()) {
         this.plugin.getLogger().info("[事件21-速度] 玩家: " + player.getName() + " 增加移动速度 +0.2");
         AttributeInstance speedAttr = player.getAttribute(Attribute.MOVEMENT_SPEED);
         if (speedAttr != null) {
            speedAttr.addModifier(new AttributeModifier(UUID.randomUUID(), "event_speed", 0.2, Operation.ADD_NUMBER));
            this.plugin.getLogger().info("[事件21-速度] 玩家: " + player.getName() + " 速度修改器已添加");
         }

         player.sendMessage("§b♿冲刺冲刺♿");
      }
   }

   private void eventMini() {
      this.debugLogger.eventInfo("[事件22-迷你化] 开始执行，玩家数: " + this.getInGamePlayers().size());

      for (Player player : this.getInGamePlayers()) {
         this.plugin.getLogger().info("[事件22-迷你化] 玩家: " + player.getName() + " 减少体型 -0.8");
         AttributeInstance scaleAttr = player.getAttribute(Attribute.SCALE);
         if (scaleAttr != null) {
            scaleAttr.addModifier(new AttributeModifier(UUID.randomUUID(), "event_mini", -0.8, Operation.ADD_NUMBER));
            this.plugin.getLogger().info("[事件22-迷你化] 玩家: " + player.getName() + " 体型修改器已添加");
         }

         player.sendMessage("§a> <");
      }
   }

   private void eventHuge() {
      this.debugLogger.eventInfo("[事件23-巨大化] 开始执行，玩家数: " + this.getInGamePlayers().size());

      for (Player player : this.getInGamePlayers()) {
         this.plugin.getLogger().info("[事件23-巨大化] 玩家: " + player.getName() + " 增加体型 +4");
         AttributeInstance scaleAttr = player.getAttribute(Attribute.SCALE);
         if (scaleAttr != null) {
            scaleAttr.addModifier(new AttributeModifier(UUID.randomUUID(), "event_huge", 4.0, Operation.ADD_NUMBER));
            this.plugin.getLogger().info("[事件23-巨大化] 玩家: " + player.getName() + " 体型修改器已添加");
         }

         player.sendMessage("§c= =");
      }
   }

   private void eventNuclear() {
      this.debugLogger.eventInfo("[事件24-核电] 开始执行，玩家数: " + this.getInGamePlayers().size());

      for (Player player : this.getInGamePlayers()) {
         Location loc = player.getLocation();
         this.plugin
            .getLogger()
            .info("[事件24-核电] 玩家: " + player.getName() + " 位置: " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + " 召唤带电苦力怕");
         Bukkit.getRegionScheduler().execute(this.plugin, loc, () -> {
            Creeper creeper = (Creeper)player.getWorld().spawnEntity(loc, EntityType.CREEPER);
            creeper.setPowered(true);
            creeper.setExplosionRadius(10);
            creeper.customName(Component.text("坏了坏了", TextColor.color(255, 0, 0)));
            this.eventEntities.add(creeper);
            this.plugin.getLogger().info("[事件24-核电] 已在 " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + " 生成带电苦力怕");
            player.sendMessage("§c核电，轻而易举！");
         });
      }
   }

   private void eventGreedySlime() {
      this.debugLogger.eventInfo("[事件26-贪吃的史莱姆] 开始执行");
      World world = this.gameManager.getGameWorld();
      if (world == null) {
         this.plugin.getLogger().warning("[事件26-贪吃的史莱姆] 世界为空，无法执行");
      } else {
         List<Player> players = this.getInGamePlayers();
         if (players.isEmpty()) {
            this.plugin.getLogger().warning("[事件26-贪吃的史莱姆] 没有玩家，无法执行");
         } else {
            Player target = players.get(this.random.nextInt(players.size()));
            Location playerLoc = target.getLocation();
            Location spawnLoc = new Location(world, playerLoc.getX(), 66.0, playerLoc.getZ());
            this.plugin
               .getLogger()
               .info(
                  "[事件26-贪吃的史莱姆] 选中玩家: "
                     + target.getName()
                     + " 玩家位置: "
                     + playerLoc.getBlockX()
                     + ","
                     + playerLoc.getBlockY()
                     + ","
                     + playerLoc.getBlockZ()
                     + " 史莱姆生成位置(绝对Y=66): "
                     + spawnLoc.getBlockX()
                     + ","
                     + spawnLoc.getBlockY()
                     + ","
                     + spawnLoc.getBlockZ()
               );
            Bukkit.getRegionScheduler().execute(this.plugin, spawnLoc, () -> {
               try {
                  Slime slime = (Slime)world.spawnEntity(spawnLoc, EntityType.SLIME);
                  if (slime != null) {
                     slime.setSize(1);
                     slime.setCustomName("§c§l大嘴");
                     slime.setCustomNameVisible(true);
                     slime.setGlowing(true);
                     slime.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 600, 0));
                     slime.getAttribute(Attribute.MAX_HEALTH).setBaseValue(10.0);
                     slime.setHealth(10.0);
                     this.eventEntities.add(slime);
                     this.plugin.getLogger().info("[事件26-贪吃的史莱姆] 史莱姆已生成，大小: 1, 生命值: 10");
                  }
               } catch (Exception var4x) {
                  this.plugin.getLogger().warning("[事件26-贪吃的史莱姆] 生成史莱姆时出错: " + var4x.getMessage());
               }
            });
         }
      }
   }

   private void tickGreedySlime() {
      // 调用统一的史莱姆吞噬处理
      this.tickSlimeEat();
   }

   /**
    * 处理史莱姆吞噬物品的行为
    * 这个方法在事件进行中以及事件结束后都会调用，确保史莱姆持续吞噬物品
    */
   private void tickSlimeEat() {
      World world = this.gameManager.getGameWorld();
      if (world == null) {
         return;
      }

      // 从 eventEntities 列表中查找史莱姆（事件进行中）
      for (Entity entity : this.eventEntities) {
         if (entity instanceof Slime slime && !slime.isDead()) {
            this.processSlimeEat(slime, world);
         }
      }

      // 额外：在世界中查找所有名为"大嘴"的史莱姆（事件结束后）
      // 这样可以确保事件结束后史莱姆仍然能吞噬物品
      for (Entity entity : world.getEntitiesByClass(Slime.class)) {
         if (!entity.isDead()) {
            String customName = entity.customName() != null ? entity.customName().toString() : "";
            if (customName.contains("大嘴") && !this.eventEntities.contains(entity)) {
               this.processSlimeEat((Slime) entity, world);
            }
         }
      }
   }

   /**
    * 处理单个史莱姆的吞噬逻辑
    */
   private void processSlimeEat(Slime slime, World world) {
      Location slimeLoc = slime.getLocation();
      UUID slimeId = slime.getUniqueId();

      for (Entity nearby : world.getNearbyEntities(slimeLoc, 3.0, 3.0, 3.0)) {
         if (nearby instanceof Item item) {
            final Location itemLoc = item.getLocation();
            // 使用 RegionScheduler 执行实体操作
            Bukkit.getRegionScheduler().execute(this.plugin, itemLoc, () -> {
               if (!item.isDead()) {
                  item.remove();
               }
            });

            // 增加吃物品计数
            int eatCount = slimeEatCount.getOrDefault(slimeId, 0) + 1;
            slimeEatCount.put(slimeId, eatCount);
            this.plugin.getLogger().info("[大嘴] 史莱姆吃掉物品，当前计数: " + eatCount);

            // 每吃3个物品变大
            if (eatCount >= 3) {
               int currentSize = slime.getSize();
               if (currentSize < 10) {
                  // 使用 RegionScheduler 执行史莱姆操作
                  Bukkit.getRegionScheduler().execute(this.plugin, slimeLoc, () -> {
                     if (!slime.isDead()) {
                        slime.setSize(currentSize + 1);
                     }
                  });
                  this.plugin.getLogger().info("[大嘴] 史莱姆成长，大小: " + currentSize + " -> " + (currentSize + 1));
               }
               // 重置计数
               slimeEatCount.put(slimeId, 0);
            }
            break;
         }
      }
   }

   private void eventLocationExchange() {
      this.debugLogger.eventInfo("[事件27-位置交换] 开始执行，玩家数: " + this.getInGamePlayers().size());
      List<Player> players = this.getInGamePlayers();
      if (players.size() < 2) {
         this.plugin.getLogger().warning("[事件27-位置交换] 玩家数量不足2人，无法交换");
      } else {
         Player p1 = players.get(this.random.nextInt(players.size()));
         Player p2 = players.get(this.random.nextInt(players.size()));

         while (p1 == p2) {
            p2 = players.get(this.random.nextInt(players.size()));
         }

         this.plugin.getLogger().info("[事件27-位置交换] 交换玩家: " + p1.getName() + " <-> " + p2.getName());
         Location loc1 = p1.getLocation();
         Location loc2 = p2.getLocation();
         Player finalP2 = p2;
         this.plugin.getLogger().info("[事件27-位置交换] " + p1.getName() + " 位置: " + loc1.getBlockX() + "," + loc1.getBlockY() + "," + loc1.getBlockZ());
         this.plugin.getLogger().info("[事件27-位置交换] " + p2.getName() + " 位置: " + loc2.getBlockX() + "," + loc2.getBlockY() + "," + loc2.getBlockZ());
         Bukkit.getRegionScheduler().execute(this.plugin, loc1, () -> p1.teleportAsync(loc2).thenAccept(success -> {
               if (success) {
                  p1.sendMessage("§d你和 " + finalP2.getName() + " 交换了位置！");
                  this.plugin.getLogger().info("[事件27-位置交换] " + p1.getName() + " 已传送到 " + loc2.getBlockX() + "," + loc2.getBlockY() + "," + loc2.getBlockZ());
               }
            }));
         Bukkit.getRegionScheduler()
            .execute(
               this.plugin,
               loc2,
               () -> finalP2.teleportAsync(loc1)
                     .thenAccept(
                        success -> {
                           if (success) {
                              finalP2.sendMessage("§d你和 " + p1.getName() + " 交换了位置！");
                              this.plugin
                                 .getLogger()
                                 .info("[事件27-位置交换] " + finalP2.getName() + " 已传送到 " + loc1.getBlockX() + "," + loc1.getBlockY() + "," + loc1.getBlockZ());
                           }
                        }
                     )
            );
      }
   }

   private void eventLavaRise() {
      this.debugLogger.eventInfo("[事件28-岩浆上升] 开始执行");
   }

   private void tickLavaRise() {
      if (this.eventDuration % 40 == 0) {
         World world = this.gameManager.getGameWorld();
         if (world != null) {
            Location center = this.gameManager.getMapRegion().getCenter();
            // 根据玩家数量调整范围：≤8人: 16x16, >8人: 20x20
            int playerCount = this.getInGamePlayers().size();
            int range = playerCount <= 8 ? 16 : 20;
            int totalSeconds = this.currentEvent.getDuration();
            int elapsedSeconds = totalSeconds - this.eventDuration / 20;
            int lavaHeight = elapsedSeconds / 2;

            // 方形区域填充（与数据包一致）
            for (int x = -range; x <= range; x++) {
               for (int z = -range; z <= range; z++) {
                  Location loc = new Location(world, center.getX() + (double)x, (double)lavaHeight, center.getZ() + (double)z);
                  if (loc.getBlock().getType() == Material.AIR) {
                     Bukkit.getRegionScheduler().execute(this.plugin, loc, () -> loc.getBlock().setType(Material.LAVA));
                  }
               }
            }
         }
      }
   }

   private void clearLavaRise() {
      World world = this.gameManager.getGameWorld();
      if (world != null) {
         Location center = this.gameManager.getMapRegion().getCenter();
         int playerCount = this.getInGamePlayers().size();
         // 进一步扩大清理范围，包含向外流动多格的岩浆（在原来基础上再+3格）
         int range = playerCount <= 8 ? 20 : 24;
         int maxHeight = this.currentEvent.getDuration() / 2;

         // 清理所有生成的岩浆，包括流到平台下方和向外流动的岩浆
         // 从最高层向下清理到y=-64（世界底部），确保清理所有流下的岩浆
         for (int y = -64; y <= maxHeight; y++) {
            for (int x = -range; x <= range; x++) {
               for (int z = -range; z <= range; z++) {
                  Location loc = new Location(world, center.getX() + (double)x, (double)y, center.getZ() + (double)z);
                  if (loc.getBlock().getType() == Material.LAVA) {
                     Bukkit.getRegionScheduler().execute(this.plugin, loc, () -> loc.getBlock().setType(Material.AIR));
                  }
               }
            }
         }
         this.plugin.getLogger().info("[事件28-岩浆上升] 岩浆已清理（范围：x/z=±" + range + ", y=-64 到 y=" + maxHeight + "）");
      }
   }

   private void eventLookAtMe() {
      this.debugLogger.eventInfo("[事件29-看着我] 开始执行，玩家数: " + this.getInGamePlayers().size());
      List<Player> players = this.getInGamePlayers();
      if (players.isEmpty()) {
         this.plugin.getLogger().warning("[事件29-看着我] 没有玩家，无法执行");
      } else {
         Player target = players.get(this.random.nextInt(players.size()));
         this.gameManager.setLookAtMeTarget(target);
         this.plugin.getLogger().info("[事件29-看着我] 选中玩家: " + target.getName() + " 给予发光效果");
         Location loc = target.getLocation();
         Bukkit.getRegionScheduler().execute(this.plugin, loc, () -> {
            target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, this.eventDuration, 0));
            this.plugin.getLogger().info("[事件29-看着我] 玩家 " + target.getName() + " 已获得发光效果");
         });
         Bukkit.broadcastMessage("§e§l全体目光向 " + target.getName() + " 看齐！");
      }
   }

   private void tickLookAtMe() {
      Player target = this.gameManager.getLookAtMeTarget();
      if (target == null || !target.isOnline()) {
         this.gameManager.setLookAtMeTarget(null);
      }
   }

   private void tickBrokenLeg() {
      if (this.eventDuration % 20 == 0) {
         for (Player player : this.getInGamePlayers()) {
            if (!player.hasPotionEffect(PotionEffectType.SLOWNESS)) {
               Location loc = player.getLocation();
               Bukkit.getRegionScheduler().execute(this.plugin, loc, () -> {
                  AttributeInstance jumpAttr = player.getAttribute(Attribute.JUMP_STRENGTH);
                  if (jumpAttr != null) {
                     boolean hasModifier = jumpAttr.getModifiers().stream().anyMatch(mod -> mod.getName().equals("event_broken_leg"));
                     if (hasModifier) {
                        jumpAttr.getModifiers().stream().filter(mod -> mod.getName().equals("event_broken_leg")).forEach(jumpAttr::removeModifier);
                        player.sendMessage("§a你的腿伤恢复了，可以跳跃了！");
                     }
                  }
               });
            }
         }
      }
   }

   private void eventFired() {
      this.debugLogger.eventInfo("[事件28-我火了] 开始执行，玩家数: " + this.getInGamePlayers().size());
      World world = this.gameManager.getGameWorld();
      if (world != null) {
         long time = world.getTime();
         boolean isDaytime = time >= 0L && time <= 13800L;
         boolean isStormy = world.hasStorm() || world.isThundering();

         for (Player player : this.getInGamePlayers()) {
            Location loc = player.getLocation();
            boolean canSeeSky = loc.getBlock().getLightFromSky() >= 15;
            if (canSeeSky && isDaytime && !isStormy) {
               Bukkit.getRegionScheduler().execute(this.plugin, loc, () -> {
                  player.setFireTicks(160);
                  player.sendMessage("§c阳光太强烈了！你着火了！");
               });
            }
         }
      }
   }

   private void eventKeyInversion() {
      this.debugLogger.eventInfo("[事件31-键位反转] 开始执行，玩家数: " + this.getInGamePlayers().size());
      this.gameManager.setKeyInversionActive(true);

      for (Player player : this.getInGamePlayers()) {
         player.sendMessage("§d按键反转了！W=S, A=D");
      }
   }

   /**
    * 事件30-不是怎么老被炸呀
    * 在所有玩家身边产生不造成击退的爆炸，造成6点固定伤害，并将装备耐久设为1
    */
   private void eventAlwaysExplode() {
      this.debugLogger.eventInfo("[事件30-不是怎么老被炸呀] 开始执行，玩家数: " + this.getInGamePlayers().size());

      for (Player player : this.getInGamePlayers()) {
         Location loc = player.getLocation();

         Bukkit.getRegionScheduler().execute(this.plugin, loc, () -> {
            // 创建不造成击退的爆炸（使用 createExplosion 的 damageBlocks 和 fire 参数）
            // 通过设置爆炸威力为0来避免击退，然后手动处理伤害
            player.getWorld().createExplosion(loc, 0.0f, false, false);

            // 造成6点固定伤害（3颗心）
            player.damage(6.0);

            // 设置装备耐久为1
            this.damageEquipmentToOne(player);

            player.sendMessage("§c§lBOOM！§7你的装备快坏了！");
         });
      }
   }

   /**
    * 将玩家装备耐久设置为1
    */
   private void damageEquipmentToOne(Player player) {
      EntityEquipment equipment = player.getEquipment();
      if (equipment != null) {
         ItemStack helmet = equipment.getHelmet();
         ItemStack chestplate = equipment.getChestplate();
         ItemStack leggings = equipment.getLeggings();
         ItemStack boots = equipment.getBoots();

         // 设置头盔耐久为1
         if (helmet != null && helmet.getType() != Material.AIR) {
            helmet.setDurability((short) (helmet.getType().getMaxDurability() - 1));
         }
         // 设置胸甲耐久为1
         if (chestplate != null && chestplate.getType() != Material.AIR) {
            chestplate.setDurability((short) (chestplate.getType().getMaxDurability() - 1));
         }
         // 设置护腿耐久为1
         if (leggings != null && leggings.getType() != Material.AIR) {
            leggings.setDurability((short) (leggings.getType().getMaxDurability() - 1));
         }
         // 设置靴子耐久为1
         if (boots != null && boots.getType() != Material.AIR) {
            boots.setDurability((short) (boots.getType().getMaxDurability() - 1));
         }
      }
   }

   private void tickKeyInversion() {
      if (!this.gameManager.isKeyInversionActive()) {
         this.gameManager.setKeyInversionActive(true);
      }
   }

   private List<Player> getInGamePlayers() {
      List<Player> players = new ArrayList<>();

      for (UUID uuid : this.gameManager.getAlivePlayers()) {
         Player player = Bukkit.getPlayer(uuid);
         if (player != null && player.isOnline()) {
            players.add(player);
         }
      }

      return players;
   }

   public EventType getCurrentEvent() {
      return this.currentEvent;
   }

   public int getEventTimer() {
      return this.eventTimer;
   }

   public int getEventDuration() {
      return this.eventDuration;
   }

   public void setForcedNextEvent(EventType eventType) {
      this.forcedNextEvent = eventType;
   }

   public EventType getForcedNextEvent() {
      return this.forcedNextEvent;
   }
}
