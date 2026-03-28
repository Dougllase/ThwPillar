package com.newpillar.game;

import com.newpillar.game.enums.MapType;
import com.newpillar.game.enums.RuleType;
import com.newpillar.game.enums.PlayerState;
import com.newpillar.game.enums.GameStatus;
import com.newpillar.game.items.ItemSystem;
import com.newpillar.game.events.EventSystem;
import com.newpillar.game.map.MapRegion;
import com.newpillar.game.map.MapTemplateManager;
import com.newpillar.game.data.StatisticsSystem;
import com.newpillar.game.map.MapTemplate;
import com.newpillar.game.map.TemplateMapGenerator;
import com.newpillar.utils.GameConstants;

import com.newpillar.NewPillar;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.FireworkEffect.Type;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import com.newpillar.game.data.StatisticsSystem.PlayerStatistics;
import com.newpillar.utils.SchedulerUtils;

public class GameManager {
   private final NewPillar plugin;
   private final com.newpillar.utils.DebugLogger debugLogger;
   private GameStatus gameStatus = GameStatus.LOBBY;
   private int gameId = 0;
   private final Map<UUID, PlayerData> playerDataMap = new ConcurrentHashMap<>();
   private final Set<UUID> readyPlayers = ConcurrentHashMap.newKeySet();
   private final Set<UUID> alivePlayers = ConcurrentHashMap.newKeySet();
   private final Set<UUID> spectators = ConcurrentHashMap.newKeySet();
   private final Set<UUID> gameParticipants = ConcurrentHashMap.newKeySet(); // 真正参与游戏的玩家（被选中参与对局的）
   private int gameStartPlayerCount = 0; // 游戏开始时的总人数（用于侧边栏显示）
   private int lootTimer = 0;
   private int eventTimer = 0;
   private int borderTimer = 0;
   private int beginTimer = 0;
   private int gameTimeMin = 0;
   private int gameTimeSec = 0;
   private ScheduledTask countdownTask;
   private ScheduledTask gameLoopTask;
   private ScheduledTask autoStartTask;
   private int autoStartCountdown = 0;
   private boolean autoStartActive = false;
   private boolean autoStartEnabled = true; // 自动开始功能开关
   private int autoStartMinPlayers = 2; // 自动开始所需的最少玩家数量
   
   // 地图随机选择
   private MapType selectedMap = MapType.WOOL;
   private boolean mapSelected = false;
   
   private final PillarManager pillarManager;
   private final ItemSystem itemSystem;
   private final EventSystem eventSystem;
   private final SidebarManager sidebarManager;
   private final MapRegion mapRegion;
   private final MapTemplateManager templateManager;
   private final TemplateMapGenerator templateMapGenerator;
   private final RuleSystem ruleSystem;
   private MapType currentMapType = MapType.WOOL;
   private Player lookAtMeTarget = null;
   private final com.newpillar.cache.PlayerCache playerCache = new com.newpillar.cache.PlayerCache();
   
   // Made in Heaven 时间加速相关
   private boolean timeAccelerationActive = false;
   private int timeAccelerationMultiplier = 4; // 4倍速
   private ScheduledTask dayNightCycleTask = null;
   private long originalTime = 0;
   
   // 新增管理器
   private final BorderManager borderManager;
   private final CollapseManager collapseManager;
   private final VoteManager voteManager;

   public GameManager(NewPillar plugin) {
      this.plugin = plugin;
      this.debugLogger = plugin.getDebugLogger();
      this.pillarManager = new PillarManager(plugin, this);
      this.itemSystem = new ItemSystem(plugin, this);
      this.eventSystem = new EventSystem(plugin, this);
      this.sidebarManager = new SidebarManager(plugin, this);
      this.mapRegion = new MapRegion(plugin, this);
      this.templateManager = new MapTemplateManager(plugin);
      this.templateMapGenerator = new TemplateMapGenerator(plugin, this);
      this.ruleSystem = new RuleSystem(plugin, this);
      this.borderManager = new BorderManager(plugin, this);
      this.collapseManager = new CollapseManager(plugin, this);
      this.voteManager = new VoteManager(plugin, this);
      this.loadConfig();
      this.initializeWorldSettings();
   }

   /**
    * 初始化世界设置（游戏规则、世界边界等）- 使用GlobalRegionScheduler
    */
   private void initializeWorldSettings() {
      World world = this.getGameWorld();
      if (world == null) {
         this.plugin.getLogger().warning("世界未加载，无法初始化世界设置");
         return;
      }

      // 使用SchedulerUtils在全局区域线程上执行世界设置
      SchedulerUtils.runGlobal(() -> {
         // 设置游戏规则 - 使用 GameRule API
         world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
         world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
         world.setGameRule(GameRule.PLAYERS_SLEEPING_PERCENTAGE, 100);
         world.setGameRule(GameRule.SEND_COMMAND_FEEDBACK, false);
         world.setGameRule(GameRule.SPAWN_RADIUS, 0);
         world.setGameRule(GameRule.FALL_DAMAGE, true);
         
         // 设置立即重生（1.21+ 使用 DO_IMMEDIATE_RESPAWN）
         try {
            world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
            this.plugin.getLogger().info("  - 立即重生: true");
         } catch (Exception e) {
            this.plugin.getLogger().warning("无法设置立即重生规则，可能是不支持的版本");
         }

         this.plugin.getLogger().info("游戏规则已设置：");
         this.plugin.getLogger().info("  - 禁止天气变化: true");
         this.plugin.getLogger().info("  - 禁止时间流逝: true");
         this.plugin.getLogger().info("  - 玩家睡眠百分比: 100%");
         this.plugin.getLogger().info("  - 出生点半径: 0");
         this.plugin.getLogger().info("  - 立即重生: true");

         // 设置世界边界 - 游戏未进行时边界设为20000x20000（10000格半径）
         org.bukkit.WorldBorder worldBorder = world.getWorldBorder();
         worldBorder.setCenter(GameConstants.BORDER_CENTER_X, GameConstants.BORDER_CENTER_Z);    // 中心设置在出生点附近
         worldBorder.setSize(GameConstants.BORDER_SIZE_INITIAL);       // 游戏未进行时边界为20000格
         worldBorder.setDamageAmount(1.0);   // 伤害量 1
         worldBorder.setDamageBuffer(0);     // 缓冲 0
         worldBorder.setWarningDistance(0);  // 警告距离 0

         this.plugin.getLogger().info("世界边界已设置：");
         this.plugin.getLogger().info("  - 中心: (100, 100)");
         this.plugin.getLogger().info("  - 大小: 20000");
         this.plugin.getLogger().info("  - 伤害: 1.0");
         this.plugin.getLogger().info("  - 缓冲: 0");
         this.plugin.getLogger().info("  - 警告距离: 0");

         // 设置世界出生点
         world.setSpawnLocation(100, 3, 100);

         this.plugin.getLogger().info("世界设置初始化完成！");
      });
   }

   private void loadConfig() {
      this.lootTimer = this.plugin.getConfig().getInt("timers.loot_time", 30);
      this.eventTimer = this.plugin.getConfig().getInt("timers.event_time", 60);
      // borderTimer 现在由 BorderManager 动态计算，这里保留默认值
      this.borderTimer = 60;
      this.beginTimer = this.plugin.getConfig().getInt("timers.begin_time", 10);
      this.autoStartEnabled = this.plugin.getConfig().getBoolean("game.auto-start-enabled", true); // 默认启用自动开始
      this.autoStartMinPlayers = this.plugin.getConfig().getInt("game.auto-start-min-players", 2); // 默认最少2人
   }

   public void startGame() {
      this.startGame(false, false);
   }

   public void startGame(boolean force) {
      this.startGame(force, false);
   }

   // 上局参与游戏的玩家UUID集合
   private final Set<UUID> lastGamePlayers = ConcurrentHashMap.newKeySet();
   // 最大游戏玩家数
   private static final int MAX_GAME_PLAYERS = 12;

   public void startGame(boolean force, boolean skipMapGeneration) {
      this.plugin.getLogger().info("[调试] startGame 被调用 - force=" + force + ", skipMapGeneration=" + skipMapGeneration + ", gameStatus=" + this.gameStatus);
      if (force || this.canStartGame()) {
         if (force) {
            this.forceJoinAllPlayers();
         }

         if (this.readyPlayers.size() < 1) {
            this.broadcastMessage("§c没有足够的玩家开始游戏！");
         } else {
            this.gameStatus = GameStatus.PLAYING;
            this.gameId++;
            this.plugin.getLogger().info("[调试] 游戏开始 - gameId=" + this.gameId + ", readyPlayers=" + this.readyPlayers.size());

            // 重置所有玩家的本局统计数据
            this.plugin.getStatisticsSystem().resetGameStats();
            
            // 通知ThwReward子插件游戏开始
            this.plugin.getThwRewardIntegration().onGameStart(this.gameId);
            
            // 通知奖励管理器游戏开始（发送招人消息等）
            this.plugin.getRewardManager().onGameStart(this.gameId);

            for (PlayerData data : this.playerDataMap.values()) {
               data.setDeathCheck(0);
            }

            // 保存所有准备玩家的副本（用于后续处理未被选中的玩家）
            Set<UUID> allReadyPlayers = new java.util.HashSet<>(this.readyPlayers);
            
            // 选择参与本局游戏的玩家
            Set<UUID> selectedPlayers = this.selectGamePlayers();
            
            // 计算未被选中的玩家
            Set<UUID> notSelectedPlayers = new java.util.HashSet<>(allReadyPlayers);
            notSelectedPlayers.removeAll(selectedPlayers);
            
            // 更新准备玩家集合为选中的玩家
            this.readyPlayers.clear();
            this.readyPlayers.addAll(selectedPlayers);
            
            // 记录真正参与游戏的玩家（用于奖励计算）
            this.gameParticipants.clear();
            this.gameParticipants.addAll(selectedPlayers);
            
            // 记录游戏开始时的总人数（用于侧边栏显示）
            this.gameStartPlayerCount = selectedPlayers.size();
            
            int totalPlayers = this.readyPlayers.size();

            // 先填充alivePlayers，这样旁观玩家可以附身到存活玩家
            for (UUID uuid : this.readyPlayers) {
               Player player = Bukkit.getPlayer(uuid);
               if (player != null && player.isOnline()) {
                  PlayerData data = this.playerDataMap.get(uuid);
                  if (data != null) {
                     data.setGameId(this.gameId);
                     data.setState(PlayerState.INGAME);
                     this.alivePlayers.add(uuid);
                     SchedulerUtils.runOnPlayer(player, () -> player.setGameMode(GameMode.ADVENTURE));
                  }
               }
            }
            
            // 将未被选中的玩家设为观察者（在alivePlayers填充之后，这样他们可以附身到存活玩家）
            this.handleNotSelectedPlayers(notSelectedPlayers);

            // 旁观者已经在handleNotSelectedPlayers中设置好了，不需要重复设置
            // 只需要确保他们保持旁观模式即可

            World world = this.getGameWorld();
            if (world != null) {
               SchedulerUtils.runGlobal(() -> {
                  world.setStorm(false);
                  world.setThundering(false);
               });
            }

            // 游戏开始前，清除所有在线玩家的属性效果（防止上一局的残留效果）
            // 跳过旁观玩家，保持他们的飞行模式
            for (Player player : Bukkit.getOnlinePlayers()) {
               if (this.spectators.contains(player.getUniqueId())) {
                  continue; // 跳过旁观玩家
               }
               SchedulerUtils.runOnPlayer(player, () -> {
                  // 清理所有属性修改
                  this.cleanupPlayerAttributes(player);
               });
            }
            
            for (UUID uuidxx : this.readyPlayers) {
               Player player = Bukkit.getPlayer(uuidxx);
               if (player != null && player.isOnline()) {
                  SchedulerUtils.runOnPlayer(player, () -> {
                     // 清理主物品栏
                     player.getInventory().clear();
                     // 清理光标上的物品
                     player.setItemOnCursor(null);
                     // 清理合成栏（包括2x2合成格）
                     player.getOpenInventory().getTopInventory().clear();
                     // 重置玩家属性（包括移除所有药水效果、重置体型和攻击伤害）
                     this.ruleSystem.resetPlayerAttributes(player);
                     // 恢复满血（在重置属性之后，避免被清除）
                     player.addPotionEffect(new PotionEffect(PotionEffectType.INSTANT_HEALTH, 5, 5, true, false));
                  });
               }
            }

            this.loadConfig();
            this.resetAllPlayers();
            this.assignPlayerNumbers();
            if (!skipMapGeneration) {
               this.generateMapWithTemplate(totalPlayers);
               this.teleportPlayersWithTemplate(totalPlayers);
            }

            this.sidebarManager.showInGameSidebar();
            
            // 广播游戏开始信息（地图、人数等）
            this.broadcastGameStartInfo(totalPlayers);
            
            this.startCountdown();
            if (world != null) {
               SchedulerUtils.runGlobal(() -> world.setTime(1000L));
            }

            if (world != null) {
               SchedulerUtils.runGlobal(() -> world.setGameRule(GameRule.KEEP_INVENTORY, false));
            }
         }
      }
   }

   public boolean canStartGame() {
      return this.gameStatus != GameStatus.LOBBY ? false : this.readyPlayers.size() >= 2;
   }

   private void startCountdown() {
      World world = this.getGameWorld();
      this.plugin.getLogger().info("[调试] startCountdown 被调用 - countdownTask=" + (this.countdownTask != null ? "非空" : "空"));
      if (world != null) {
         // 检查是否已有计时器在运行，防止重复启动
         if (this.countdownTask != null) {
            this.plugin.getLogger().info("[调试] 倒计时已经在运行中，跳过重复启动");
            return;
         }
         int[] remaining = new int[]{this.beginTimer};
         
         // 发送初始倒计时消息 - 直接向每个玩家发送
         for (Player player : Bukkit.getOnlinePlayers()) {
            if (player != null && player.isOnline()) {
               player.sendMessage("§e笼子将在 §c" + this.beginTimer + " §e秒后破坏！");
            }
         }
         
         this.countdownTask = SchedulerUtils.runTimerOnWorld(world, GameConstants.DELAY_IMMEDIATE, GameConstants.TICKS_PER_SECOND, scheduledTask -> {
            if (this.gameStatus != GameStatus.PLAYING) {
               SchedulerUtils.cancel(scheduledTask);
            } else {
               // 在关键时间点发送消息提示（10秒、5秒、最后5秒每秒）
               if (remaining[0] == 10 || remaining[0] == 5 || (remaining[0] <= 3 && remaining[0] > 0)) {
                  final int currentRemaining = remaining[0];
                  for (Player player : Bukkit.getOnlinePlayers()) {
                     if (player != null && player.isOnline()) {
                        player.sendMessage("§e笼子破坏倒计时: §c" + currentRemaining + " §e秒");
                     }
                  }
               }
               
               if (remaining[0] <= 5 && remaining[0] > 0) {
                  String color = this.getCountdownColor(remaining[0]);
                  String title = color + remaining[0];

                  for (Player player : Bukkit.getOnlinePlayers()) {
                     player.sendTitle(title, "", 5, 20, 5);
                     player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.0F);
                  }
               }

               if (remaining[0] <= 0) {
                  SchedulerUtils.cancel(scheduledTask);
                  this.onGameActuallyStart();
               } else {
                  remaining[0]--;
               }
            }
         });
      }
   }

   private String getCountdownColor(int number) {
      return switch (number) {
         case 1 -> "§2";
         case 2 -> "§a";
         case 3 -> "§6";
         case 4 -> "§c";
         case 5 -> "§4";
         default -> "§f";
      };
   }

   private void onGameActuallyStart() {
      for (Player player : Bukkit.getOnlinePlayers()) {
         player.sendTitle("§a游戏开始", "", 10, 40, 10);
      }

      for (UUID uuid : this.alivePlayers) {
         Player player = Bukkit.getPlayer(uuid);
         if (player != null && player.isOnline()) {
            SchedulerUtils.runOnPlayer(player, () -> {
               player.setGameMode(GameMode.SURVIVAL);
               // 取消无敌状态（游戏正式开始）
               player.setInvulnerable(false);
               this.plugin.getLogger().info("[游戏保护] 玩家 " + player.getName() + " 已取消无敌状态（游戏开始）");
               // 应用规则到每个玩家
               this.ruleSystem.applyRuleToPlayer(player);
            });
         }
      }
      
      // 处理旁观者 - 确保他们可以飞行观看（使用统一方法，不改变游戏模式）
      for (UUID uuid : this.spectators) {
         Player player = Bukkit.getPlayer(uuid);
         if (player != null && player.isOnline()) {
            // 旁观者已经是旁观模式，只需要确保飞行能力和取消无敌状态
            SchedulerUtils.runLaterOnPlayer(player, 1L, () -> {
               player.setAllowFlight(true);
               player.setFlying(true);
               // 取消无敌状态（旁观者不需要无敌）
               player.setInvulnerable(false);
               player.sendMessage("§7笼子已破坏！你可以飞行观看游戏。");
            });
         }
      }

      // 启动规则系统
      this.ruleSystem.start();
      
      // 设置世界边界中心和初始大小
      World world = this.getGameWorld();
      if (world != null) {
         Location center = this.mapRegion.getCenter();
         // 使用SchedulerUtils执行世界边界操作
         SchedulerUtils.runGlobal(() -> {
            org.bukkit.WorldBorder worldBorder = world.getWorldBorder();
            worldBorder.setCenter(center);
            worldBorder.setSize(100.0); // 初始边界大小 100x100（从100开始收缩）
            this.plugin.getLogger().info("世界边界已设置：中心(" + center.getBlockX() + ", " + center.getBlockZ() + "), 大小: 100");
         });
      }

      int supportedCount = this.templateManager.getClosestSupportedCount(this.alivePlayers.size());
      MapTemplate template = this.templateManager.getTemplate(this.currentMapType, supportedCount);
      Location center = this.mapRegion.getCenter();
      this.templateMapGenerator.openCages(template, center);

      // 月球地图：设置低重力效果
      if (this.currentMapType == MapType.MOON) {
         this.applyMoonGravity();
      }

      // 海洋地图：给予玩家饵钓V钓鱼竿
      if (this.currentMapType == MapType.SEA) {
         this.giveSeaFishingRods();
      }

      for (Player player : Bukkit.getOnlinePlayers()) {
         player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
      }

      this.startGameLoop();
      this.itemSystem.start();
      this.plugin.getItemEffectManager().startCooldownUpdateTask();
      this.eventSystem.start();
      this.broadcastMessage("§a笼子已破坏！战斗开始！");
   }

   /**
    * 选择参与本局游戏的玩家
    * 如果玩家数超过12人，则随机选择12人，优先选择上局未游玩的玩家
    * @return 选中的玩家UUID集合
    */
   private Set<UUID> selectGamePlayers() {
      Set<UUID> allReadyPlayers = new java.util.HashSet<>(this.readyPlayers);
      
      if (allReadyPlayers.size() <= MAX_GAME_PLAYERS) {
         // 玩家数不超过12人，所有人都可以参与
         // 记录本局玩家用于下局优先选择
         this.lastGamePlayers.clear();
         this.lastGamePlayers.addAll(allReadyPlayers);
         return allReadyPlayers;
      }
      
      // 玩家数超过12人，需要选择
      this.plugin.getLogger().info("[玩家选择] 准备玩家数 " + allReadyPlayers.size() + " 超过 " + MAX_GAME_PLAYERS + " 人，开始随机选择...");
      
      List<UUID> allPlayers = new ArrayList<>(allReadyPlayers);
      Collections.shuffle(allPlayers); // 随机打乱顺序
      
      Set<UUID> selected = new HashSet<>();
      Set<UUID> notSelected = new HashSet<>();
      
      // 第一步：优先选择上局未游玩的玩家
      for (UUID uuid : allPlayers) {
         if (selected.size() >= MAX_GAME_PLAYERS) break;
         if (!this.lastGamePlayers.contains(uuid)) {
            selected.add(uuid);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
               player.sendMessage("§a你被选为本局游戏玩家！（优先选择：上局未游玩）");
            }
         }
      }
      
      // 第二步：如果还有空位，从剩余玩家中随机选择
      for (UUID uuid : allPlayers) {
         if (selected.size() >= MAX_GAME_PLAYERS) break;
         if (!selected.contains(uuid)) {
            selected.add(uuid);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
               player.sendMessage("§a你被选为本局游戏玩家！");
            }
         }
      }
      
      // 通知未被选中的玩家
      for (UUID uuid : allPlayers) {
         if (!selected.contains(uuid)) {
            notSelected.add(uuid);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
               player.sendMessage("§c本局游戏玩家已满，你将在下局优先被选中！");
               player.sendMessage("§7当前游戏玩家数：" + selected.size() + "，你将在大厅等待...");
            }
         }
      }
      
      // 记录本局玩家用于下局优先选择
      this.lastGamePlayers.clear();
      this.lastGamePlayers.addAll(selected);
      
      this.plugin.getLogger().info("[玩家选择] 已选择 " + selected.size() + " 名玩家参与游戏，" + notSelected.size() + " 名玩家等待下局");
      if (allReadyPlayers.size() > MAX_GAME_PLAYERS) {
         this.broadcastMessage("§e人数超出限制！共有 " + allReadyPlayers.size() + " 人准备，随机选中 " + selected.size() + " 人参与游戏。(" + allReadyPlayers.size() + "/" + MAX_GAME_PLAYERS + ")");
      } else {
         this.broadcastMessage("§e本局游戏共有 " + allReadyPlayers.size() + " 人准备，选中 " + selected.size() + " 人参与！");
      }
      
      return selected;
   }

   /**
    * 处理未被选中的玩家 - 设为观察者模式
    * @param notSelectedPlayers 未被选中的玩家UUID集合
    */
   private void handleNotSelectedPlayers(Set<UUID> notSelectedPlayers) {
      for (UUID uuid : notSelectedPlayers) {
         Player player = Bukkit.getPlayer(uuid);
         if (player != null && player.isOnline()) {
            // 立即设置为旁观模式，防止在大厅被虚空杀死
            player.setGameMode(GameMode.SPECTATOR);
            
            // 使用统一的旁观者处理方法
            // 延迟1tick执行，确保地图生成完成
            SchedulerUtils.runOnPlayer(player, () -> {
               // 确保是旁观模式
               player.setGameMode(GameMode.SPECTATOR);
               player.setAllowFlight(true);
               player.setFlying(true);
               this.handlePlayerBecomeSpectator(player, null, "§7你当前是观察者，可以飞行观看游戏。");
               this.plugin.getLogger().info("[玩家选择] 玩家 " + player.getName() + " 未被选中，设为观察者");
            });
         }
      }
   }
   
   /**
    * 获取旁观者安全传送位置
    * 返回地图中心上方50格的位置，确保旁观者不会坠入虚空
    */
   private Location getSpectatorSafeLocation() {
      World world = this.getGameWorld();
      if (world == null) return null;

      Location center = this.mapRegion.getCenter();
      // 在地图中心上方50格的位置，确保旁观者可以安全飞行观看整个地图
      return new Location(world, center.getX(), center.getY() + 50, center.getZ());
   }

   /**
    * 统一设置玩家为旁观模式
    * 与玩家被淘汰后的旁观模式逻辑保持一致
    * @param player 要设置为旁观模式的玩家
    * @param sendMessage 是否发送消息提示
    */
   public void setPlayerSpectatorMode(Player player, boolean sendMessage) {
      if (player == null || !player.isOnline()) return;

      // 使用EntityScheduler在玩家线程执行
      player.getScheduler().execute(this.plugin, () -> {
         player.setGameMode(GameMode.SPECTATOR);
         player.setAllowFlight(true);
         player.setFlying(true);
         if (sendMessage) {
            player.sendMessage("§7你当前是观察者，可以飞行观看游戏。");
         }
      }, () -> {}, 1L);
   }

   /**
    * 统一处理玩家成为旁观者
    * 适用于所有情况：大厅选择旁观、游戏开始未被选中、游戏进行中加入、被淘汰
    * @param player 玩家
    * @param teleportLocation 传送位置（null则使用默认安全位置）
    * @param message 发送给玩家的消息
    */
   public void handlePlayerBecomeSpectator(Player player, Location teleportLocation, String message) {
      if (player == null || !player.isOnline()) return;

      UUID uuid = player.getUniqueId();

      // 确保玩家数据存在
      PlayerData data = this.playerDataMap.get(uuid);
      if (data == null) {
         data = new PlayerData(uuid);
         data.setPlayerName(player.getName());
         this.playerDataMap.put(uuid, data);
      }

      // 更新状态
      data.setState(PlayerState.SPECTATOR);
      data.setGameId(this.gameId);

      // 添加到旁观者集合
      this.spectators.add(uuid);

      // 从其他集合中移除
      this.readyPlayers.remove(uuid);
      this.alivePlayers.remove(uuid);

      // 确定传送位置
      Location targetLoc = teleportLocation;
      
      // 如果未指定传送位置，使用安全位置
      if (targetLoc == null) {
         targetLoc = this.getSpectatorSafeLocation();
      }
      if (targetLoc == null) {
         targetLoc = this.getLobbyLocation();
      }

      // 传送到目标位置并设置为旁观模式
      final Location finalTargetLoc = targetLoc;
      player.teleportAsync(targetLoc).thenRun(() -> {
         // 传送完成后设置为旁观模式
         player.getScheduler().execute(this.plugin, () -> {
            player.setGameMode(GameMode.SPECTATOR);
            player.setAllowFlight(true);
            player.setFlying(true);
            player.setInvulnerable(false);
            if (message != null && !message.isEmpty()) {
               player.sendMessage(message);
            }
         }, () -> {}, 1L);
      });

      this.plugin.getLogger().info("[旁观者] 玩家 " + player.getName() + " 成为旁观者，传送到: " + finalTargetLoc);
   }

   private void startGameLoop() {
      World world = this.getGameWorld();
      if (world != null) {
         // 重置崩溃管理器，确保每局游戏都是全新状态
         this.collapseManager.reset();
         
         // 初始化边界并启动边界收缩（由BorderManager管理）
         this.borderManager.initBorder(world, new Location(world, 0, 0, 0));
         this.borderManager.startBorderShrink(world);

         this.gameLoopTask = SchedulerUtils.runTimerOnWorld(world, GameConstants.DELAY_IMMEDIATE, GameConstants.TICKS_PER_SECOND, scheduledTask -> {
            if (this.gameStatus != GameStatus.PLAYING) {
               SchedulerUtils.cancel(scheduledTask);
            } else {
               this.gameTimeSec++;
               if (this.gameTimeSec >= 60) {
                  this.gameTimeSec = 0;
                  this.gameTimeMin++;
               }

               // 定期维护旁观者状态（每5秒检查一次）
               if (this.gameTimeSec % 5 == 0) {
                  for (UUID uuid : this.spectators) {
                     Player player = Bukkit.getPlayer(uuid);
                     if (player != null && player.isOnline()) {
                        // 确保旁观者保持旁观模式和飞行能力（使用统一方法）
                        if (player.getGameMode() != GameMode.SPECTATOR) {
                           this.setPlayerSpectatorMode(player, false);
                        }
                     }
                  }
               }

               this.sidebarManager.update();
               if (this.alivePlayers.isEmpty()) {
                  this.endGame();
               }
            }
         });
      }
   }



   public void endGame() {
      this.endGame(false);
   }
   
   public void endGame(boolean isShutdown) {
      // 获取胜利者和淘汰王信息
      Player winner = null;
      String winnerName = "无";
      Location winnerLocation = null;
      
      if (!this.alivePlayers.isEmpty()) {
         UUID winnerUuid = this.alivePlayers.iterator().next();
         winner = Bukkit.getPlayer(winnerUuid);
         this.plugin.getLogger().info("[调试] endGame - 胜利者UUID: " + winnerUuid + ", 在线: " + (winner != null));
         if (winner != null) {
            winnerName = winner.getName();
            winnerLocation = winner.getLocation();
            this.plugin.getLogger().info("[调试] endGame - 胜利者: " + winnerName + ", 位置: " + winnerLocation);
         } else {
            // 如果玩家不在线，尝试从 playerDataMap 获取名字
            PlayerData data = this.playerDataMap.get(winnerUuid);
            if (data != null && data.getPlayerName() != null) {
               winnerName = data.getPlayerName();
               this.plugin.getLogger().info("[调试] endGame - 胜利者离线，从PlayerData获取名字: " + winnerName);
            }
         }
      } else {
         this.plugin.getLogger().info("[调试] endGame - alivePlayers为空，无胜利者");
      }
      
      // 获取淘汰王（本局击杀最多）
      String topKillerName = "无";
      int topKills = 0;
      StatisticsSystem statsSystem = this.plugin.getStatisticsSystem();
      for (UUID uuid : this.playerDataMap.keySet()) {
         PlayerStatistics stats = statsSystem.getPlayerStats(uuid);
         if (stats.kills > topKills) {
            topKills = stats.kills;
            // 优先尝试获取在线玩家的名字
            Player killer = Bukkit.getPlayer(uuid);
            if (killer != null) {
               topKillerName = killer.getName();
            } else {
               // 如果玩家不在线，尝试从 playerDataMap 获取名字
               PlayerData data = this.playerDataMap.get(uuid);
               if (data != null && data.getPlayerName() != null) {
                  topKillerName = data.getPlayerName();
               } else {
                  // 最后尝试从离线玩家获取名字
                  OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
                  if (offlinePlayer.getName() != null) {
                     topKillerName = offlinePlayer.getName();
                  }
               }
            }
         }
      }
      
      // 记录游戏时间
      final int finalGameTimeMin = this.gameTimeMin;
      final int finalGameTimeSec = this.gameTimeSec;
      final String finalWinnerName = winnerName;
      final String finalTopKillerName = topKillerName;
      final int finalTopKills = topKills;
      final Player finalWinner = winner;
      final Location finalWinnerLocation = winnerLocation;
      
      // 停止游戏系统
      this.gameStatus = GameStatus.LOBBY;
      this.gameTimeMin = 0;
      this.gameTimeSec = 0;
      if (this.countdownTask != null) {
         this.countdownTask.cancel();
         this.countdownTask = null;
         this.plugin.getLogger().info("[调试] endGame - 倒计时任务已取消并置空");
      }

      if (this.gameLoopTask != null) {
         this.gameLoopTask.cancel();
         this.gameLoopTask = null;
         this.plugin.getLogger().info("[调试] endGame - 游戏循环任务已取消并置空");
      }

      // 停止边界收缩和平台崩溃
      this.borderManager.stop();
      this.collapseManager.stop();
      this.collapseManager.reset();

      this.itemSystem.stop();
      this.plugin.getItemEffectManager().stopCooldownUpdateTask();
      this.eventSystem.stop();
      this.ruleSystem.stop();

      // 重置月球地图重力效果
      this.resetMoonGravity();

      // 给胜利者添加无敌效果并播放庆祝
      if (finalWinner != null && !isShutdown) {
         // 将边界扩大到10000格，并将中心设置到胜利者位置以避免红色遮罩
         World gameWorld = this.getGameWorld();
         if (gameWorld != null) {
            org.bukkit.WorldBorder worldBorder = gameWorld.getWorldBorder();
            // 先将中心设置到胜利者位置
            worldBorder.setCenter(finalWinnerLocation);
            // 立即扩大边界（无过渡时间），避免红色遮罩出现
            worldBorder.setSize(10000);
         }
         
         SchedulerUtils.runOnLocation(finalWinnerLocation, () -> {
            // 添加抗性提升效果（持续30秒）
            finalWinner.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 30 * 20, 4, true, false));
            
            // 播放胜利音效
            finalWinner.playSound(finalWinnerLocation, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
         });
         
         // 在胜利者位置召唤烟花庆祝（传入胜利者对象以实时跟踪位置）
         this.startFireworkCelebration(finalWinner);
      }
      
      // 清除游戏世界中的敌对生物（游戏胜利时立即清除）
      this.clearHostileMobs();
      
      // 广播游戏结束信息
      if (!isShutdown) {
         String timeString = String.format("%02d:%02d", finalGameTimeMin, finalGameTimeSec);
         
         // 播报胜利者和淘汰王
         this.plugin.getLogger().info("[调试] 广播游戏结束消息 - 在线玩家数: " + Bukkit.getOnlinePlayers().size());
         
         // 构建多行消息（使用换行符合并）
         StringBuilder message = new StringBuilder();
         message.append("\n");
         message.append("§6§l═══════════════════════════\n");
         message.append("§e§l        游戏结束！\n");
         message.append("\n");
         message.append("§6§l冠军: §f§l").append(finalWinnerName).append("\n");
         message.append("§c§l淘汰王: §f§l").append(finalTopKillerName).append(" §7(§f").append(finalTopKills).append("§7击杀)\n");
         message.append("§7游戏时间: §f").append(timeString).append("\n");
         message.append("§6§l═══════════════════════════\n");
         
         String finalMessage = message.toString();
         
         // 向本地玩家发送消息（一次调用）
         int msgCount = 0;
         for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(finalMessage);
            msgCount++;
         }
         this.plugin.getLogger().info("[调试] 广播消息已发送给 " + msgCount + " 名玩家");
         
         // 向其他服务器广播游戏结束消息（生存服）
         // 构建单行居中消息格式，用于跨服发送
         String crossServerMsg = "§6§l[幸运之柱] §e§l" + finalWinnerName + " §6获得胜利！ §7淘汰王: §c" + finalTopKillerName + " §7(§f" + finalTopKills + "§7击杀) §7时间: §f" + timeString;
         
         // 获取本局参与者玩家名列表（用于只发送给这些玩家）
         // 使用gameParticipants（真正参与对局的玩家），而不是lastGamePlayers或playerDataMap.keySet()
         List<String> participantNames = new ArrayList<>();
         for (UUID uuid : GameManager.this.gameParticipants) {
            PlayerData data = GameManager.this.playerDataMap.get(uuid);
            if (data != null && data.getPlayerName() != null) {
               participantNames.add(data.getPlayerName());
            } else {
               // 如果playerDataMap中没有，尝试从在线玩家获取
               Player p = Bukkit.getPlayer(uuid);
               if (p != null) {
                  participantNames.add(p.getName());
               }
            }
         }
         
         this.plugin.getLogger().info("[调试] 游戏结束跨服消息目标玩家: " + participantNames);
         this.plugin.getThwRewardIntegration().broadcastGameEndToOtherServers(crossServerMsg, participantNames);
         
         // 向所有玩家发送Title
         String title = "§6§l玩家 " + finalWinnerName + " 胜利！";
         String subtitle = "§7游戏时间：" + timeString;
         
         int titleCount = 0;
         for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(title, subtitle, 10, 100, 20);
            titleCount++;
            this.plugin.getLogger().info("[调试] Title已发送给: " + player.getName());
         }
         this.plugin.getLogger().info("[调试] Title发送完成，共发送给 " + titleCount + " 名玩家");
         
         // 记录胜利
         if (finalWinner != null) {
            this.plugin.getStatisticsSystem().recordWin(finalWinner);
         }
         
         // 记录所有参与者的游戏场次和胜负
         for (UUID participantUuid : GameManager.this.playerDataMap.keySet()) {
            Player participant = Bukkit.getPlayer(participantUuid);
            if (participant != null && participant.isOnline()) {
               this.plugin.getStatisticsSystem().recordGamePlayed(participant);
               // 如果不是胜利者，记录失败
               if (finalWinner == null || !participantUuid.equals(finalWinner.getUniqueId())) {
                  this.plugin.getStatisticsSystem().recordLoss(participant);
               }
            }
         }
         
         // 保存本局参与者到lastGamePlayers（用于下局优先选择）
         // 使用gameParticipants（真正参与对局的玩家），而不是playerDataMap.keySet()（包含旁观者）
         GameManager.this.lastGamePlayers.clear();
         GameManager.this.lastGamePlayers.addAll(GameManager.this.gameParticipants);
         
         // 通知ThwReward子插件游戏结束
         // 使用gameParticipants（真正参与游戏的玩家）而不是playerDataMap.keySet()（包含旁观者）
         List<UUID> winners = finalWinner != null ? java.util.Collections.singletonList(finalWinner.getUniqueId()) : java.util.Collections.emptyList();
         List<UUID> participants = new java.util.ArrayList<>(GameManager.this.gameParticipants);
         this.plugin.getThwRewardIntegration().onGameEnd(this.gameId, !winners.isEmpty(), winners, participants);
         
         // 通知奖励管理器游戏结束（发送奖励消息等）
         this.plugin.getRewardManager().onGameEnd(this.gameId, !winners.isEmpty(), winners, participants);
      }

      // 延迟15秒后清理和传送（庆祝时间）
      if (!isShutdown) {
         World world = this.getGameWorld();
         Location centerLoc = world != null ? world.getWorldBorder().getCenter() : new Location(world, 0, 100, 0);
         
         // 延迟15秒（300 ticks）让玩家欣赏庆祝烟花
         SchedulerUtils.runLaterOnLocation(centerLoc, GameConstants.DELAY_CELEBRATION, () -> {
            // 重置世界边界为大厅状态（20000格）
            if (world != null) {
               org.bukkit.WorldBorder worldBorder = world.getWorldBorder();
               worldBorder.setCenter(GameConstants.BORDER_CENTER_X, GameConstants.BORDER_CENTER_Z);
               worldBorder.setSize(GameConstants.BORDER_SIZE_INITIAL);
               GameManager.this.plugin.getLogger().info("世界边界已重置为大厅状态：中心(100, 100)，大小20000");
               
               SchedulerUtils.runGlobal(() -> {
                  world.setStorm(false);
                  world.setThundering(false);
               });
            }

            // 庆祝结束，现在才将玩家状态改为LOBBY
            for (PlayerData data : GameManager.this.playerDataMap.values()) {
               data.setState(PlayerState.LOBBY);
               // 清除死亡位置记录
               data.setDeathLocation(null);
            }

            Location lobby = GameManager.this.getLobbyLocation();
            
            // 传送所有玩家回大厅（先设置冒险模式，再传送）
            for (Player player : Bukkit.getOnlinePlayers()) {
               UUID uuid = player.getUniqueId();
               PlayerData data = GameManager.this.playerDataMap.get(uuid);
               
               if (data != null) {
                  // 使用 SchedulerUtils 确保线程安全
                  SchedulerUtils.runOnPlayer(player, () -> {
                     // 清除玩家Motion，防止飞出预定区域
                     player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                     // 先设置为冒险模式
                     player.setGameMode(GameMode.ADVENTURE);
                     // 取消无敌状态（防止状态残留）
                     player.setInvulnerable(false);
                     // 再传送到大厅
                     player.teleportAsync(lobby).thenRun(() -> {
                        // 传送完成后再次清除Motion
                        SchedulerUtils.runOnPlayer(player, () -> {
                           player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                        });
                     });
                  });
               }
            }
            
            // 延迟后重置所有玩家属性（使用 SchedulerUtils）
            SchedulerUtils.runLaterGlobal(5L, () -> {
               for (Player player : Bukkit.getOnlinePlayers()) {
                  SchedulerUtils.runOnPlayer(player, () -> {
                     // 清空背包（主物品栏）
                     player.getInventory().clear();
                     // 清理光标上的物品
                     player.setItemOnCursor(null);
                     // 清理合成栏（包括2x2合成格）
                     player.getOpenInventory().getTopInventory().clear();
                     // 重置玩家属性（包括体型、攻击伤害、移动速度、跳跃力度、重力等）
                     GameManager.this.ruleSystem.resetPlayerAttributes(player);
                     // 给予投票物品
                     giveVoteItem(player);
                  });
               }

               GameManager.this.alivePlayers.clear();
               GameManager.this.readyPlayers.clear();
               GameManager.this.autoStartActive = false;
               GameManager.this.pillarManager.reset();
               GameManager.this.resetMap();
               GameManager.this.sidebarManager.showLobbySidebar();
               
               // 通知上一局参与过的玩家游戏已结束
               for (UUID lastPlayerUuid : GameManager.this.lastGamePlayers) {
                  Player lastPlayer = Bukkit.getPlayer(lastPlayerUuid);
                  if (lastPlayer != null && lastPlayer.isOnline()) {
                     lastPlayer.sendMessage("§e上一局游戏已经结束！§a可以准备新的战斗了！");
                  }
               }
               
               // 游戏结束后，如果自动开始启用且在线玩家>=2，自动重新开始准备流程
               if (GameManager.this.autoStartEnabled && Bukkit.getOnlinePlayers().size() >= 2) {
                  GameManager.this.plugin.getLogger().info("[调试] 游戏结束，自动开始下一轮准备流程");
                  // 清除观察者集合，让中途加入的观察者也能参与下一局
                  for (UUID spectatorUuid : GameManager.this.spectators) {
                     Player spectator = Bukkit.getPlayer(spectatorUuid);
                     if (spectator != null && spectator.isOnline()) {
                        spectator.sendMessage("§a游戏结束！你已退出观察者模式并加入下一局！");
                     }
                  }
                  GameManager.this.spectators.clear();
                  for (Player player : Bukkit.getOnlinePlayers()) {
                     UUID uuid = player.getUniqueId();
                     PlayerData data = GameManager.this.playerDataMap.get(uuid);
                     if (data != null) {
                        data.setState(PlayerState.READY);
                        GameManager.this.readyPlayers.add(uuid);
                        // 通知玩家已自动准备
                        player.sendMessage("§a游戏结束！你已自动准备下一局！");
                     }
                  }
                  GameManager.this.checkAutoStart();
               } else {
                  // 即使不自动开始，也清除观察者集合并通知他们
                  for (UUID spectatorUuid : GameManager.this.spectators) {
                     Player spectator = Bukkit.getPlayer(spectatorUuid);
                     if (spectator != null && spectator.isOnline()) {
                        spectator.sendMessage("§a游戏结束！你已退出观察者模式！");
                     }
                  }
                  GameManager.this.spectators.clear();
               }
            });
         });
      } else {
         this.alivePlayers.clear();
      }
   }
   
   /**
    * 开始烟花庆祝
    */
   private void startFireworkCelebration(Player winner) {
      if (winner == null || !winner.isOnline()) return;
      
      World world = winner.getWorld();
      if (world == null) return;
      
      // 定义各种颜色
      Color[] colors = {
         Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW, 
         Color.PURPLE, Color.ORANGE, Color.AQUA, Color.WHITE,
         Color.FUCHSIA, Color.LIME, Color.MAROON, Color.NAVY,
         Color.OLIVE, Color.SILVER, Color.TEAL
      };
      
      FireworkEffect.Type[] types = {
         FireworkEffect.Type.BALL, FireworkEffect.Type.BALL_LARGE, 
         FireworkEffect.Type.BURST, FireworkEffect.Type.CREEPER,
         FireworkEffect.Type.STAR
      };
      
      // 在胜利者周围召唤烟花，持续15秒，每30tick（1.5秒）一次
      // 使用 RegionScheduler 在胜利者所在区域执行，避免 Folia 并发问题
      for (int i = 0; i < 10; i++) { // 10次烟花
         final int delay = i * 30 + 1; // 每30tick（1.5秒），从1开始避免0
         final int index = i;
         
         // 使用胜利者当前位置来调度区域任务
         Location winnerLoc = winner.getLocation();
         SchedulerUtils.runLaterOnLocation(winnerLoc, delay, () -> {
            // 检查胜利者是否仍然在线
            if (winner == null || !winner.isOnline()) return;
               
               // 获取胜利者当前位置
               Location currentLoc = winner.getLocation();
               World currentWorld = currentLoc.getWorld();
               if (currentWorld == null) return;
               
               // 在胜利者周围随机位置生成烟花
               double offsetX = (Math.random() - 0.5) * 20; // ±10格
               double offsetZ = (Math.random() - 0.5) * 20;
               double offsetY = Math.random() * 10 + 5; // 5-15格高
               
               Location fireworkLoc = currentLoc.clone().add(offsetX, offsetY, offsetZ);
               
               try {
                  Firework firework = (Firework) currentWorld.spawnEntity(fireworkLoc, EntityType.FIREWORK_ROCKET);
                  FireworkMeta meta = firework.getFireworkMeta();
                  
                  // 随机选择颜色和类型
                  Color color1 = colors[(int) (Math.random() * colors.length)];
                  Color color2 = colors[(int) (Math.random() * colors.length)];
                  FireworkEffect.Type type = types[(int) (Math.random() * types.length)];
                  
                  FireworkEffect effect = FireworkEffect.builder()
                     .withColor(color1)
                     .withFade(color2)
                     .with(type)
                     .trail(true)
                     .flicker(Math.random() > 0.5)
                     .build();
                  
                  meta.addEffect(effect);
                  meta.setPower((int) (Math.random() * 2) + 1); // 飞行时间1-2秒
                  firework.setFireworkMeta(meta);
                  
                  // 同时在胜利者正上方也生成一个烟花
                  if (index % 3 == 0) { // 每3次在头顶生成一个
                     Location topLoc = currentLoc.clone().add(0, 15, 0);
                     Firework topFirework = (Firework) currentWorld.spawnEntity(topLoc, EntityType.FIREWORK_ROCKET);
                     FireworkMeta topMeta = topFirework.getFireworkMeta();
                     
                     FireworkEffect topEffect = FireworkEffect.builder()
                        .withColor(Color.YELLOW)
                        .withFade(Color.ORANGE)
                        .with(FireworkEffect.Type.STAR)
                        .trail(true)
                        .flicker(true)
                        .build();
                     
                     topMeta.addEffect(topEffect);
                     topMeta.setPower(2);
                     topFirework.setFireworkMeta(topMeta);
                  }
               } catch (Exception e) {
                  GameManager.this.plugin.getLogger().warning("生成烟花时出错: " + e.getMessage());
               }
         });
      }
   }

   public void playerJoin(Player player) {
      UUID uuid = player.getUniqueId();
      this.plugin.getLogger().info("[调试] playerJoin 被调用: " + player.getName() + ", playerDataMap.containsKey=" + this.playerDataMap.containsKey(uuid) + ", gameStatus=" + this.gameStatus);
      if (this.playerDataMap.containsKey(uuid)) {
         this.plugin.getLogger().info("[调试] 玩家已在游戏中，拒绝加入");
         player.sendMessage("§c你已经在游戏中了！");
      } else if (this.gameStatus == GameStatus.PLAYING) {
         this.plugin.getLogger().info("[调试] 游戏进行中，以观察者身份加入");
         // 游戏进行中，以观察者身份加入
         // 传送到随机存活玩家位置
         Player target = getRandomAlivePlayer();
         Location teleportLoc = target != null ? target.getLocation() : this.getLobbyLocation();

         // 使用统一的旁观者处理方法
         this.handlePlayerBecomeSpectator(player, teleportLoc, "§7你以§f§l观察者§7身份加入游戏！\n§7你可以自由飞行观看比赛。");

         this.sidebarManager.playerJoin(player);
         this.sidebarManager.showInGameSidebar();

         this.broadcastMessage("§7[+] §f" + player.getName() + " §7以观察者身份加入");
      } else {
         this.playerDataMap.put(uuid, new PlayerData(uuid));
         PlayerData lobbyData = this.playerDataMap.get(uuid);
         lobbyData.setPlayerName(player.getName());
         player.teleportAsync(this.getLobbyLocation());
         player.setGameMode(GameMode.ADVENTURE);
         this.sidebarManager.playerJoin(player);
         this.sidebarManager.showLobbySidebar();
         
         // 给予投票物品
         giveVoteItem(player);
         
         // 如果自动开始功能启用，自动将玩家标记为准备
         this.plugin.getLogger().info("[调试] 玩家 " + player.getName() + " 加入，autoStartEnabled=" + this.autoStartEnabled + ", gameStatus=" + this.gameStatus);
         if (this.autoStartEnabled) {
            PlayerData data = this.playerDataMap.get(uuid);
            this.plugin.getLogger().info("[调试] 自动开始启用，PlayerData=" + (data != null ? "不为null" : "为null"));
            if (data != null) {
               data.setState(PlayerState.READY);
               this.readyPlayers.add(uuid);
               this.plugin.getLogger().info("[调试] 玩家 " + player.getName() + " 已自动准备，当前准备人数: " + this.readyPlayers.size());
               player.sendMessage("§a欢迎加入 NewPillar！你已自动准备！");
               player.sendMessage("§e当前准备人数: " + this.readyPlayers.size());
               
               // 如果游戏即将开始（倒计时进行中），向新玩家发送相关信息
               if (this.autoStartActive && this.gameStatus == GameStatus.LOBBY) {
                  StringBuilder gameStartMsg = new StringBuilder();
                  gameStartMsg.append("\n");
                  gameStartMsg.append("§6§l═══════════════════════════\n");
                  gameStartMsg.append("§e§l        游戏即将开始\n");
                  gameStartMsg.append("§e倒计时: §c").append(this.autoStartCountdown).append(" §e秒\n");
                  gameStartMsg.append("§6§l═══════════════════════════");
                  
                  // 如果正在进行规则投票，向新玩家显示投票信息
                  if (this.voteManager.isVotingActive()) {
                     List<RuleType> votingRules = this.voteManager.getVotingRules();
                     gameStartMsg.append("\n\n");
                     gameStartMsg.append("§6§l═══════════════════════════\n");
                     gameStartMsg.append("§e§l        规则投票进行中\n");
                     gameStartMsg.append("\n");
                     for (int i = 0; i < votingRules.size(); i++) {
                        RuleType rule = votingRules.get(i);
                        gameStartMsg.append("§").append(rule.getColor()).append("§l[").append(i + 1).append("] ").append(rule.getName()).append("\n");
                        gameStartMsg.append("§7").append(rule.getDescription()).append("\n");
                        gameStartMsg.append("\n");
                     }
                     gameStartMsg.append("§e点击投票书或使用 §f/vote §e打开投票界面\n");
                     gameStartMsg.append("§6§l═══════════════════════════");
                  }
                  
                  player.sendMessage(gameStartMsg.toString());
               }
               
               // 检查是否触发自动开始
               this.plugin.getLogger().info("[调试] 调用 checkAutoStart()");
               this.checkAutoStart();
            }
         } else {
            this.plugin.getLogger().info("[调试] 自动开始未启用，显示准备提示");
            player.sendMessage("§a欢迎加入 NewPillar！使用 /np ready 准备游戏。");
         }
      }
   }

   /**
    * 获取随机存活玩家
    */
   private Player getRandomAlivePlayer() {
      if (this.alivePlayers.isEmpty()) return null;
      UUID[] uuids = this.alivePlayers.toArray(new UUID[0]);
      UUID randomUuid = uuids[(int)(Math.random() * uuids.length)];
      return Bukkit.getPlayer(randomUuid);
   }

   public void playerLeave(Player player) {
      UUID uuid = player.getUniqueId();
      this.sidebarManager.playerLeave(player);
      this.readyPlayers.remove(uuid);
      this.alivePlayers.remove(uuid);
      this.spectators.remove(uuid);
      this.playerDataMap.remove(uuid);
      if (this.gameStatus == GameStatus.PLAYING && this.alivePlayers.size() <= 1) {
         this.endGame();
      }
   }

   public void playerReady(Player player) {
      if (this.gameStatus != GameStatus.LOBBY) {
         player.sendMessage("§c游戏进行中，无法准备！");
      } else {
         UUID uuid = player.getUniqueId();
         PlayerData data = this.playerDataMap.get(uuid);
         if (data != null) {
            data.setState(PlayerState.READY);
            this.readyPlayers.add(uuid);
            this.spectators.remove(uuid);
            player.sendMessage("§a你已准备！");
            
            // 检查是否触发自动开始
            this.checkAutoStart();
         }
      }
   }
   
   /**
    * 检查并触发自动开始/倒计时
    * 无论自动开始是否启用，只要准备人数>=2就触发倒计时
    */
   private void checkAutoStart() {
      this.plugin.getLogger().info("[调试] checkAutoStart() 被调用");
      
      int readyCount = this.readyPlayers.size();
      this.plugin.getLogger().info("[调试] 准备人数: " + readyCount + ", autoStartActive=" + this.autoStartActive + ", autoStartEnabled=" + this.autoStartEnabled);
      
      // 使用配置的最少玩家数量
      if (readyCount >= this.autoStartMinPlayers && !this.autoStartActive) {
         this.plugin.getLogger().info("[调试] 条件满足，开始倒计时流程");
         this.autoStartActive = true;
         
         // 根据人数设置倒计时时间
         // 2-6人: 60秒, 6-8人: 30秒, 8-10人: 25秒, 10人: 20秒
         if (readyCount >= 2 && readyCount <= 6) {
            this.autoStartCountdown = 60;
         } else if (readyCount > 6 && readyCount <= 8) {
            this.autoStartCountdown = 30;
         } else if (readyCount > 8 && readyCount < 10) {
            this.autoStartCountdown = 25;
         } else if (readyCount >= 10) {
            this.autoStartCountdown = 20;
         } else {
            this.autoStartCountdown = 60;
         }
         
         this.plugin.getLogger().info("[调试] 倒计时设置为: " + this.autoStartCountdown + " 秒");
         
         // 根据模式显示不同的消息
         if (this.autoStartEnabled) {
            this.broadcastMessage("§6§l[幸运之柱] §e已有 " + readyCount + " 名玩家准备，游戏将在 " + this.autoStartCountdown + " 秒后自动开始！");
         } else {
            this.broadcastMessage("§6§l[幸运之柱] §e已有 " + readyCount + " 名玩家准备，游戏将在 " + this.autoStartCountdown + " 秒后开始！");
         }
         
         // 初始化规则投票
         this.plugin.getLogger().info("[调试] 初始化规则投票");
         this.voteManager.initRuleVoting();
         
         // 启动倒计时
         this.plugin.getLogger().info("[调试] 启动倒计时");
         this.startAutoStartCountdown();
      } else {
         this.plugin.getLogger().info("[调试] 条件不满足: readyCount=" + readyCount + " (需要>=2), autoStartActive=" + this.autoStartActive);
      }
   }
   
   /**
    * 启动自动开始倒计时
    */
   private void startAutoStartCountdown() {
      World world = this.getGameWorld();
      if (world == null) return;

      // 如果已经有正在运行的倒计时任务，先取消它
      if (this.autoStartTask != null) {
         this.autoStartTask.cancel();
         this.autoStartTask = null;
      }

      Location centerLoc = world.getWorldBorder().getCenter();

      this.autoStartTask = SchedulerUtils.runTimerOnLocation(centerLoc, 0L, GameConstants.TICKS_PER_SECOND, task -> {
         if (GameManager.this.gameStatus != GameStatus.LOBBY) {
            SchedulerUtils.cancel(task);
            GameManager.this.autoStartActive = false;
            return;
         }
         
         // 检查准备人数是否足够
         if (GameManager.this.readyPlayers.size() < 2) {
            GameManager.this.broadcastMessage("§c§l[幸运之柱] §c准备人数不足，自动开始已取消！");
            SchedulerUtils.cancel(task);
            GameManager.this.autoStartActive = false;
            return;
         }

         GameManager.this.autoStartCountdown--;

         // 更新侧边栏显示
         GameManager.this.sidebarManager.update();

         // 最后10秒倒计时广播
         if (GameManager.this.autoStartCountdown <= 10 && GameManager.this.autoStartCountdown > 0) {
            GameManager.this.broadcastMessage("§6§l[幸运之柱] §e游戏将在 §c" + GameManager.this.autoStartCountdown + " §e秒后开始！");
         }
         
         // 倒计时剩余10秒时锁定投票、选择规则和展示地图
         if (GameManager.this.autoStartCountdown == 10) {
            GameManager.this.voteManager.lockVotingAndSelectRule(GameManager.this.ruleSystem);
            GameManager.this.selectedMap = GameManager.this.voteManager.selectAndAnnounceMap();
            GameManager.this.currentMapType = GameManager.this.selectedMap;
            GameManager.this.mapSelected = true;
         }
         
         // 倒计时结束，开始游戏
         if (GameManager.this.autoStartCountdown <= 0) {
            SchedulerUtils.cancel(task);
            GameManager.this.autoStartActive = false;
            GameManager.this.startGame();
         }
      }); // 每秒执行一次
   }
   
   /**
    * 获取选中的地图
    */
    public MapType getSelectedMap() {
       return this.selectedMap;
    }
    
    /**
    * 检查是否正在进行投票
    */
   public boolean isVotingActive() {
      return this.voteManager.isVotingActive();
   }

   /**
    * 获取当前投票规则列表
    */
   public List<RuleType> getVotingRules() {
      return this.voteManager.getVotingRules();
   }

   /**
    * 获取自动开始倒计时
    */
   public int getAutoStartCountdown() {
      return this.autoStartCountdown;
   }

    /**
     * 检查自动开始是否正在进行
     */
    public boolean isAutoStartActive() {
       return this.autoStartActive;
    }

    /**
     * 获取自动开始功能是否启用
     */
    public boolean isAutoStartEnabled() {
       return this.autoStartEnabled;
    }
    
    /**
     * 设置自动开始功能开关
     */
    public void setAutoStartEnabled(boolean enabled) {
       this.autoStartEnabled = enabled;
       // 保存到配置文件
       this.plugin.getConfig().set("game.auto-start-enabled", enabled);
       this.plugin.saveConfig();
    }
    
    /**
     * 切换自动开始功能开关
     * @return 切换后的状态
     */
    public boolean toggleAutoStart() {
       this.autoStartEnabled = !this.autoStartEnabled;
       // 保存到配置文件
       this.plugin.getConfig().set("game.auto-start-enabled", this.autoStartEnabled);
       this.plugin.saveConfig();
       return this.autoStartEnabled;
    }

    /**
     * 获取自动开始所需的最少玩家数量
     */
    public int getAutoStartMinPlayers() {
       return this.autoStartMinPlayers;
    }

    /**
     * 设置自动开始所需的最少玩家数量
     */
    public void setAutoStartMinPlayers(int minPlayers) {
       if (minPlayers < 1) {
          minPlayers = 1;
       }
       if (minPlayers > 100) {
          minPlayers = 100;
       }
       this.autoStartMinPlayers = minPlayers;
       // 保存到配置文件
       this.plugin.getConfig().set("game.auto-start-min-players", minPlayers);
       this.plugin.saveConfig();
    }

   public void playerSpectate(Player player) {
      UUID uuid = player.getUniqueId();

      if (this.gameStatus == GameStatus.PLAYING) {
         // 游戏进行中，使用统一的旁观者处理方法
         this.handlePlayerBecomeSpectator(player, null, "§7你现在是旁观者，可以飞行观看游戏。");
      } else if (this.gameStatus == GameStatus.LOBBY) {
         // 大厅状态，切换旁观/准备状态
         PlayerData data = this.playerDataMap.get(uuid);
         if (data != null) {
            // 切换模式
            if (data.getState() == PlayerState.SPECTATOR) {
               // 从旁观切换到准备状态
               data.setState(PlayerState.LOBBY);
               this.spectators.remove(uuid);
               this.readyPlayers.add(uuid); // 添加到准备玩家集合
               player.setGameMode(GameMode.ADVENTURE);
               player.teleportAsync(this.getLobbyLocation());
               player.sendMessage("§a你已退出旁观模式并准备就绪！");
               player.sendMessage("§e当前准备人数: " + this.readyPlayers.size());
               this.plugin.getLogger().info("[调试] 玩家 " + player.getName() + " 从旁观者切换回准备状态，当前准备人数: " + this.readyPlayers.size());
               // 检查是否触发自动开始
               this.checkAutoStart();
            } else {
               // 从其他状态切换到旁观
               data.setState(PlayerState.SPECTATOR);
               this.readyPlayers.remove(uuid);
               this.spectators.add(uuid);
               // 设置为旁观模式并传送到安全位置
               this.setPlayerSpectatorMode(player, false);
               Location safeLoc = this.getSpectatorSafeLocation();
               if (safeLoc != null) {
                  player.teleportAsync(safeLoc);
               }
               player.sendMessage("§7你现在成为了旁观者！请等待游戏开始。");
            }
         }
      } else {
         player.sendMessage("§c当前状态无法切换 spectator！");
      }
   }

   public void playerOut(Player player) {
      this.playerOut(player, false);
   }
   
   public void playerOut(Player player, boolean skipEndGameCheck) {
      UUID uuid = player.getUniqueId();
      PlayerData data = this.playerDataMap.get(uuid);
      if (data != null) {
         // 将出局玩家状态设为 SPECTATOR（观察者），而不是 OUT
         data.setState(PlayerState.SPECTATOR);
         // 添加到观察者集合
         this.spectators.add(uuid);
         // 从存活玩家集合中移除
         this.alivePlayers.remove(uuid);
      }

      // 异步同步玩家统计信息到数据库，避免阻塞主线程
      final UUID playerUuid = uuid;
      final String playerName = player.getName();
      SchedulerUtils.runAsync(() -> {
         this.plugin.getStatisticsSystem().syncPlayerToDatabase(playerUuid);
         this.plugin.getLogger().info("玩家 " + playerName + " 出局，统计信息已异步同步到数据库");
      });

      // 注意：游戏模式设置和传送在PlayerListener.onPlayerRespawn中处理
      // 检查是否只剩一个存活的玩家（如果skipEndGameCheck为true则跳过，由调用方统一处理）
      if (!skipEndGameCheck && this.alivePlayers.size() <= 1) {
         this.endGame();
      }
   }

   private void resetAllPlayers() {
      for (PlayerData data : this.playerDataMap.values()) {
         data.reset();
      }
   }
   
   /**
    * 清理玩家所有属性修改
    * 用于游戏开始时清除所有玩家的残留属性效果
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
      
      this.plugin.getLogger().info("[游戏开始] 玩家 " + player.getName() + " 的属性已清理");
   }

   private void assignPlayerNumbers() {
      int number = 1;

      for (UUID uuid : this.readyPlayers) {
         PlayerData data = this.playerDataMap.get(uuid);
         if (data != null) {
            data.setPlayerNumber(number++);
         }
      }
   }

   public void broadcastMessage(String message) {
      Bukkit.broadcastMessage(message);
   }
   
   /**
    * 广播游戏开始信息
    * 向所有参与游戏的玩家发送地图名称、人数等信息
    * 使用换行符合并，只一次调用发送
    */
   private void broadcastGameStartInfo(int totalPlayers) {
      String mapName = this.currentMapType != null ? this.currentMapType.getDisplayName() : "未知地图";
      String mapDescription = this.currentMapType != null ? this.currentMapType.getDescription() : "";
      
      // 构建多行消息（使用换行符合并）
      StringBuilder message = new StringBuilder();
      message.append("\n");
      message.append("§6§l═══════════════════════════\n");
      message.append("§e§l        游戏开始！\n");
      message.append("\n");
      message.append("§6本局地图: §f").append(mapName).append("\n");
      message.append("§7").append(mapDescription).append("\n");
      message.append("§e参与人数: §f").append(totalPlayers).append(" §e人\n");
      message.append("§7准备倒计时: §c").append(this.beginTimer).append(" §7秒\n");
      message.append("\n");
      message.append("§6§l═══════════════════════════\n");
      message.append("");
      
      String finalMessage = message.toString();
      
      // 直接向每个在线玩家发送（一次调用）
      for (Player player : Bukkit.getOnlinePlayers()) {
         if (player != null && player.isOnline()) {
            player.sendMessage(finalMessage);
         }
      }
      
      this.plugin.getLogger().info("[调试] 已广播游戏开始信息 - 地图: " + mapName + ", 人数: " + totalPlayers);
   }

   public void forceJoinAllPlayers() {
      this.readyPlayers.clear();
      this.spectators.clear();

      for (Player player : Bukkit.getOnlinePlayers()) {
         UUID uuid = player.getUniqueId();
         if (!this.playerDataMap.containsKey(uuid)) {
            this.playerDataMap.put(uuid, new PlayerData(uuid));
         }

         PlayerData data = this.playerDataMap.get(uuid);
         data.setPlayerName(player.getName());
         data.setState(PlayerState.READY);
         this.readyPlayers.add(uuid);
         player.sendMessage("§a你已被管理员强制拉入游戏！");
      }

      this.broadcastMessage("§6管理员强制开始游戏，已拉入 " + this.readyPlayers.size() + " 名玩家！");
   }

   private void resetMap() {
      World world = this.getGameWorld();
      if (world != null) {
         this.plugin.getLogger().info("开始重置地图...");
         this.mapRegion.clearRegionBlocks();

         // 清除所有非玩家实体（防止上局生物残留）
         this.clearAllEntities();

         SchedulerUtils.runGlobal(() -> {
            world.getWorldBorder().reset();
            this.pillarManager.reset();
            this.plugin.getLogger().info("地图重置完成");
         });
      }
   }

   /**
    * 清除游戏世界中的所有非玩家实体
    */
   private void clearAllEntities() {
      World world = this.getGameWorld();
      if (world == null) return;

      this.plugin.getLogger().info("开始清除所有非玩家实体...");
      int count = 0;

      for (org.bukkit.entity.Entity entity : world.getEntities()) {
         // 只清除非玩家实体，保留玩家
         if (!(entity instanceof Player) && !entity.hasMetadata("NPC")) {
            entity.remove();
            count++;
         }
      }

      this.plugin.getLogger().info("已清除 " + count + " 个非玩家实体");
   }

   /**
    * 清除游戏世界中的敌对生物
    */
   private void clearHostileMobs() {
      World world = this.getGameWorld();
      if (world == null) return;
      
      this.plugin.getLogger().info("开始清除敌对生物...");
      int count = 0;
      
      for (org.bukkit.entity.Entity entity : world.getEntities()) {
         // 检查是否是敌对生物
         if (entity instanceof org.bukkit.entity.Monster ||
             entity instanceof org.bukkit.entity.Slime ||
             entity instanceof org.bukkit.entity.Ghast ||
             entity instanceof org.bukkit.entity.Phantom ||
             entity instanceof org.bukkit.entity.EnderDragon ||
             entity instanceof org.bukkit.entity.Wither) {
            
            // 不要清除玩家和NPC
            if (!(entity instanceof Player) && !entity.hasMetadata("NPC")) {
               entity.remove();
               count++;
            }
         }
      }
      
      this.plugin.getLogger().info("已清除 " + count + " 个敌对生物");
   }

   public World getGameWorld() {
      return (World)Bukkit.getWorlds().get(0);
   }

   public Location getLobbyLocation() {
      double x = this.plugin.getConfig().getDouble("lobby.x", 100.0);
      double y = this.plugin.getConfig().getDouble("lobby.y", 4.0);
      double z = this.plugin.getConfig().getDouble("lobby.z", 100.0);
      return new Location(this.getGameWorld(), x, y, z);
   }

   public void shutdown() {
      if (this.gameStatus == GameStatus.PLAYING) {
         this.endGame(true);
      }
   }

   public GameStatus getGameStatus() {
      return this.gameStatus;
   }

   public int getGameId() {
      return this.gameId;
   }

   public Set<UUID> getReadyPlayers() {
      return this.readyPlayers;
   }

   /**
    * 获取当前处于READY状态的玩家数量
    * 用于侧边栏显示准备中玩家数
    */
   public int getReadyPlayerCount() {
      int count = 0;
      for (PlayerData data : this.playerDataMap.values()) {
         if (data.getState() == PlayerState.READY) {
            count++;
         }
      }
      return count;
   }

   public Set<UUID> getAlivePlayers() {
      return this.alivePlayers;
   }

   /**
    * 获取游戏开始时的总人数
    * 用于侧边栏显示游戏人数上限
    * @return 游戏开始时的总人数
    */
   public int getGameStartPlayerCount() {
      return this.gameStartPlayerCount;
   }

   /**
    * 获取距离指定玩家最近的存活玩家
    * @param player 参考玩家
    * @return 最近的存活玩家，如果没有则返回null
    */
   public Player getNearestAlivePlayer(Player player) {
      Location playerLoc = player.getLocation();
      Player nearest = null;
      double minDistance = Double.MAX_VALUE;
      
      for (UUID uuid : this.alivePlayers) {
         Player alivePlayer = Bukkit.getPlayer(uuid);
         if (alivePlayer != null && alivePlayer.isOnline() && !alivePlayer.equals(player)) {
            double distance = playerLoc.distanceSquared(alivePlayer.getLocation());
            if (distance < minDistance) {
               minDistance = distance;
               nearest = alivePlayer;
            }
         }
      }
      return nearest;
   }

   public PlayerData getPlayerData(UUID uuid) {
      return this.playerDataMap.get(uuid);
   }

   public int getGameTimeMin() {
      return this.gameTimeMin;
   }

   public int getGameTimeSec() {
      return this.gameTimeSec;
   }

   public PillarManager getPillarManager() {
      return this.pillarManager;
   }

   public EventSystem getEventSystem() {
      return this.eventSystem;
   }

   public ItemSystem getItemSystem() {
      return this.itemSystem;
   }

   /**
    * 检查国王游戏是否正在进行
    */
   public boolean isKingGameActive() {
      return this.eventSystem.isKingGameActive();
   }

   /**
    * 检查指定玩家是否是当前国王
    */
   public boolean isCurrentKing(Player player) {
      Player king = this.eventSystem.getCurrentKing();
      return king != null && king.equals(player);
   }

   public SidebarManager getSidebarManager() {
      return this.sidebarManager;
   }

   public MapRegion getMapRegion() {
      return this.mapRegion;
   }

   public MapTemplateManager getTemplateManager() {
      return this.templateManager;
   }

   public TemplateMapGenerator getTemplateMapGenerator() {
      return this.templateMapGenerator;
   }

   public RuleSystem getRuleSystem() {
      return this.ruleSystem;
   }

   public MapType getCurrentMapType() {
      return this.currentMapType;
   }

   public void setCurrentMapType(MapType mapType) {
      this.currentMapType = mapType;
      this.pillarManager.setMapType(mapType);
      this.plugin.getLogger().info("当前地图类型已设置为: " + mapType.getDisplayName());
   }

   /**
    * 应用月球地图的低重力效果
    * 与数据包一致：gravity=0.03, safe_fall_distance=16
    */
   private void applyMoonGravity() {
      this.plugin.getLogger().info("[月球地图] 正在应用低重力效果...");
      for (UUID uuid : this.alivePlayers) {
         Player player = Bukkit.getPlayer(uuid);
         if (player != null && player.isOnline()) {
            final Location playerLoc = player.getLocation();
            // 使用 RegionScheduler 执行玩家属性操作
            SchedulerUtils.runOnLocation(playerLoc, () -> {
               if (!player.isOnline()) return;
               // 设置重力为 0.03（默认 0.08）
               AttributeInstance gravityAttr = player.getAttribute(Attribute.GRAVITY);
               if (gravityAttr != null) {
                  gravityAttr.setBaseValue(0.03);
               }
               // 设置安全摔落距离为 16（默认 3）
               AttributeInstance safeFallAttr = player.getAttribute(Attribute.SAFE_FALL_DISTANCE);
               if (safeFallAttr != null) {
                  safeFallAttr.setBaseValue(16.0);
               }
               player.sendMessage("§b§l低重力环境已启用！§7你可以跳得更高，摔落伤害减少。");
            });
         }
      }
      this.plugin.getLogger().info("[月球地图] 低重力效果已应用到所有玩家");
   }

   /**
    * 重置玩家的重力属性（游戏结束时调用）
    */
   private void resetMoonGravity() {
      if (this.currentMapType == MapType.MOON) {
         for (UUID uuid : this.alivePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
               final Location playerLoc = player.getLocation();
               // 使用 RegionScheduler 执行玩家属性操作
               SchedulerUtils.runOnLocation(playerLoc, () -> {
                  if (!player.isOnline()) return;
                  AttributeInstance gravityAttr = player.getAttribute(Attribute.GRAVITY);
                  if (gravityAttr != null) {
                     gravityAttr.setBaseValue(GameConstants.BASE_GRAVITY); // 恢复默认值
                  }
                  AttributeInstance safeFallAttr = player.getAttribute(Attribute.SAFE_FALL_DISTANCE);
                  if (safeFallAttr != null) {
                     safeFallAttr.setBaseValue(GameConstants.BASE_SAFE_FALL_DISTANCE); // 恢复默认值
                  }
               });
            }
         }
         this.plugin.getLogger().info("[月球地图] 重力效果已重置");
      }
   }

   /**
    * 给予海洋地图玩家饵钓V钓鱼竿
    */
   private void giveSeaFishingRods() {
      this.plugin.getLogger().info("[海洋地图] 正在给予玩家饵钓V钓鱼竿...");
      for (UUID uuid : this.alivePlayers) {
         Player player = Bukkit.getPlayer(uuid);
         if (player != null && player.isOnline()) {
            final Location playerLoc = player.getLocation();
            SchedulerUtils.runOnLocation(playerLoc, () -> {
               if (!player.isOnline()) return;

               // 创建带有饵钓V的不可破坏钓鱼竿
               ItemStack fishingRod = new ItemStack(Material.FISHING_ROD);
               fishingRod.editMeta(meta -> {
                  // 设置不可破坏
                  meta.setUnbreakable(true);
                  // 添加饵钓V附魔
                  meta.addEnchant(org.bukkit.enchantments.Enchantment.LURE, 5, true);
                  // 设置显示名称
                  meta.displayName(net.kyori.adventure.text.Component.text("§b§l海洋钓竿"));
                  // 添加 lore（仅保留功能说明）
                  java.util.List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();
                  lore.add(net.kyori.adventure.text.Component.text("§e在海洋中钓鱼获取物品！"));
                  meta.lore(lore);
               });

               // 给予玩家钓鱼竿
               player.getInventory().addItem(fishingRod);
               player.sendMessage("§b§l你获得了一根海洋钓竿！§7使用它在水中钓鱼获取物品。");
            });
         }
      }
      this.plugin.getLogger().info("[海洋地图] 饵钓V钓鱼竿已给予所有玩家");
   }

   public void generateMapWithTemplate(int playerCount) {
      int supportedCount = this.templateManager.getClosestSupportedCount(playerCount);
      this.plugin.getLogger().info("使用模板系统生成地图: " + this.currentMapType.getDisplayName() + " (实际玩家: " + playerCount + ", 使用配置: " + supportedCount + ")");
      MapTemplate template = this.templateManager.getTemplate(this.currentMapType, supportedCount);
      Location center = this.mapRegion.getCenter();
      this.templateMapGenerator.generateMap(template, center);
   }

   public void teleportPlayersWithTemplate(int playerCount) {
      // 先传送旁观者到战场上方安全位置（确保他们在其他玩家之前被传送）
      this.teleportSpectatorsToBattlefield();
      
      int supportedCount = this.templateManager.getClosestSupportedCount(playerCount);
      MapTemplate template = this.templateManager.getTemplate(this.currentMapType, supportedCount);
      
      // 获取所有柱子配置并随机打乱
      List<MapTemplate.PillarConfig> pillars = new ArrayList<>(template.getPillars());
      Collections.shuffle(pillars); // 随机打乱柱子顺序
      
      int index = 0;
      Location center = this.mapRegion.getCenter();

      for (UUID uuid : this.readyPlayers) {
         Player player = Bukkit.getPlayer(uuid);
         if (player != null && player.isOnline() && index < pillars.size()) {
            MapTemplate.PillarConfig pillar = pillars.get(index);
            Location teleportLoc = pillar.getTeleportLocation(center, template.getPillarHeight());
            
            // 安全检查：确保传送位置下方有方块支撑，防止生成在虚空
            Location safeLoc = ensureSafeTeleportLocation(teleportLoc, template.getPillarHeight());
            if (safeLoc == null) {
               this.plugin.getLogger().warning("[传送安全] 玩家 " + player.getName() + " 的传送位置不安全，尝试使用备用位置");
               // 使用中心位置作为备用
               safeLoc = center.clone().add(0, template.getPillarHeight() + 2, 0);
            }
            final Location finalTeleportLoc = safeLoc;
            
            // 使用 RegionScheduler 执行玩家操作
            SchedulerUtils.runOnPlayer(player, () -> {
               // 清除玩家Motion，防止飞出预定区域
               player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
               
               player.teleportAsync(finalTeleportLoc).thenRun(() -> {
                  // 传送到笼子后设置无敌状态（游戏开始前保护）
                  SchedulerUtils.runOnLocation(finalTeleportLoc, () -> {
                     // 再次清除Motion确保玩家静止
                     player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                     player.setInvulnerable(true);
                     this.plugin.getLogger().info("[游戏保护] 玩家 " + player.getName() + " 已传送到 " + finalTeleportLoc.getBlockX() + "," + finalTeleportLoc.getBlockY() + "," + finalTeleportLoc.getBlockZ() + " 并设置无敌状态");
                  });
               });
            });
            index++;
         }
      }
   }
   
   /**
    * 确保传送位置安全（下方有方块支撑）
    * @param loc 原始传送位置
    * @param pillarHeight 柱子高度
    * @return 安全的传送位置，如果无法找到则返回null
    */
   private Location ensureSafeTeleportLocation(Location loc, int pillarHeight) {
      if (loc == null || loc.getWorld() == null) {
         return null;
      }
      
      World world = loc.getWorld();
      int x = loc.getBlockX();
      int y = loc.getBlockY();
      int z = loc.getBlockZ();
      
      // 检查原始位置下方是否有支撑方块（笼子顶部）
      for (int checkY = y - 1; checkY >= y - 5 && checkY > world.getMinHeight(); checkY--) {
         Block block = world.getBlockAt(x, checkY, z);
         if (block.getType() != Material.AIR && block.getType() != Material.VOID_AIR) {
            // 找到支撑方块，原始位置安全
            return loc;
         }
      }
      
      // 原始位置不安全，尝试在附近寻找安全位置
      this.plugin.getLogger().warning("[传送安全] 位置 " + x + "," + y + "," + z + " 下方无支撑，尝试寻找安全位置");
      
      // 在当前位置向下搜索，找到最近的支撑方块
      for (int offset = 1; offset <= 10; offset++) {
         Block block = world.getBlockAt(x, y - offset, z);
         if (block.getType() != Material.AIR && block.getType() != Material.VOID_AIR) {
            // 找到支撑方块，返回其上方位置
            Location safeLoc = loc.clone();
            safeLoc.setY(y - offset + 1);
            this.plugin.getLogger().info("[传送安全] 找到安全位置: " + safeLoc.getBlockX() + "," + safeLoc.getBlockY() + "," + safeLoc.getBlockZ());
            return safeLoc;
         }
      }
      
      // 无法找到安全位置
      return null;
   }
   
   /**
    * 传送所有旁观者到战场上方安全位置
    * 在游戏开始时与其他玩家一起传送
    */
   private void teleportSpectatorsToBattlefield() {
      Location safeLoc = this.getSpectatorSafeLocation();
      if (safeLoc == null) {
         this.plugin.getLogger().warning("[旁观者传送] 无法获取安全位置，旁观者将不会被传送");
         return;
      }
      
      for (UUID uuid : this.spectators) {
         Player player = Bukkit.getPlayer(uuid);
         if (player != null && player.isOnline()) {
            // 使用 RegionScheduler 执行传送
            SchedulerUtils.runOnPlayer(player, () -> {
               // 确保是旁观模式
               player.setGameMode(GameMode.SPECTATOR);
               player.setAllowFlight(true);
               player.setFlying(true);
               
               // 传送到战场上方
               player.teleportAsync(safeLoc).thenRun(() -> {
                  this.plugin.getLogger().info("[旁观者传送] 玩家 " + player.getName() + " 已传送到战场上方: " + safeLoc);
               });
            });
         }
      }
   }

   public Player getLookAtMeTarget() {
      return this.lookAtMeTarget;
   }

   public void setLookAtMeTarget(Player lookAtMeTarget) {
      this.lookAtMeTarget = lookAtMeTarget;
   }

   // ==================== Made in Heaven 时间加速方法 ====================

   public boolean isTimeAccelerationActive() {
      return this.timeAccelerationActive;
   }

   public int getTimeAccelerationMultiplier() {
      return this.timeAccelerationActive ? this.timeAccelerationMultiplier : 1;
   }

   /**
    * 启动时间加速（Made in Heaven）
    */
   public void startTimeAcceleration() {
      if (this.timeAccelerationActive) return;

      this.timeAccelerationActive = true;
      World world = this.getGameWorld();
      if (world == null) return;

      this.debugLogger.debug("[Made in Heaven] 时间加速启动！");

      // 启动快速昼夜交替（6秒一个周期 = 3秒白天 + 3秒黑夜）
      // 注意：在Folia中，修改世界时间需要在全局调度器上执行
      this.dayNightCycleTask = SchedulerUtils.runTimerGlobal(1L, 1L,
         (task) -> {
            if (!this.timeAccelerationActive || this.gameStatus != GameStatus.PLAYING) {
               SchedulerUtils.cancel(task);
               return;
            }
            // 快速推进时间 - 在全局调度器上执行
            World gameWorld = this.getGameWorld();
            if (gameWorld != null) {
               long currentTime = gameWorld.getTime();
               gameWorld.setTime((currentTime + 200) % 24000); // 快速推进时间
            }
         });
   }

   /**
    * 停止时间加速
    */
   public void stopTimeAcceleration() {
      if (!this.timeAccelerationActive) return;
      
      this.timeAccelerationActive = false;
      
      if (this.dayNightCycleTask != null) {
         this.dayNightCycleTask.cancel();
         this.dayNightCycleTask = null;
      }
      
      this.debugLogger.debug("[Made in Heaven] 时间加速停止！");
   }

   // ==================== 新增管理器 Getter 方法 ====================

   public BorderManager getBorderManager() {
      return this.borderManager;
   }

   public CollapseManager getCollapseManager() {
      return this.collapseManager;
   }

   public VoteManager getVoteManager() {
      return this.voteManager;
   }

   /**
    * 触发平台崩溃（由BorderManager调用）
    */
   public void triggerPlatformCollapse() {
      World world = this.getGameWorld();
      if (world != null) {
         this.collapseManager.startPlatformCollapse(world);
      }
   }

   /**
    * 开始平台崩溃（公开方法供外部调用）
    */
   public void startPlatformCollapse() {
      World world = this.getGameWorld();
      if (world != null) {
         this.collapseManager.startPlatformCollapse(world);
      }
   }

   // 游戏模式枚举 (同步自数据包设计)
   public static enum GameModeType {
      SOLO("个人竞技"),
      TEAM("团队模式"),
      RED_VS_BLUE("红蓝对抗");

      private final String displayName;

      GameModeType(String displayName) {
         this.displayName = displayName;
      }

      public String getDisplayName() {
         return this.displayName;
      }
   }

   private GameModeType currentGameMode = GameModeType.SOLO;

   public GameModeType getGameMode() {
      return this.currentGameMode;
   }

   public void setGameMode(GameModeType gameMode) {
      this.currentGameMode = gameMode;
   }

   public int getBorderTimer() {
      // 如果游戏正在进行，从BorderManager获取动态倒计时
      if (this.gameStatus == GameStatus.PLAYING && this.borderManager != null) {
         return this.borderManager.getBorderShrinkCountdown();
      }
      // 否则返回配置中的静态值
      return this.borderTimer;
   }
   
   public com.newpillar.cache.PlayerCache getPlayerCache() {
      return this.playerCache;
   }
   
   /**
    * 给予玩家投票物品
    */
   private void giveVoteItem(Player player) {
      // 创建投票物品（书本）
      ItemStack voteItem = new ItemStack(Material.BOOK);
      org.bukkit.inventory.meta.ItemMeta meta = voteItem.getItemMeta();
      if (meta != null) {
         meta.setDisplayName("§6§l投票");
         java.util.List<String> lore = new java.util.ArrayList<>();
         lore.add("§7右键打开投票界面");
         lore.add("§7选择你想要的规则！");
         meta.setLore(lore);
         voteItem.setItemMeta(meta);
      }
      
      // 将物品放入玩家背包的第一个空位
      player.getInventory().setItem(0, voteItem);
   }
}
