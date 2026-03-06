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

public class GameManager {
   private final NewPillar plugin;
   private final com.newpillar.utils.DebugLogger debugLogger;
   private GameStatus gameStatus = GameStatus.LOBBY;
   private int gameId = 0;
   private final Map<UUID, PlayerData> playerDataMap = new ConcurrentHashMap<>();
   private final Set<UUID> readyPlayers = ConcurrentHashMap.newKeySet();
   private final Set<UUID> alivePlayers = ConcurrentHashMap.newKeySet();
   private final Set<UUID> spectators = ConcurrentHashMap.newKeySet();
   private int lootTimer = 0;
   private int eventTimer = 0;
   private int borderTimer = 0;
   private int beginTimer = 0;
   private int gameTimeMin = 0;
   private int gameTimeSec = 0;
   private ScheduledTask countdownTask;
   private ScheduledTask gameLoopTask;
   private ScheduledTask borderShrinkTask;
   private ScheduledTask collapseTask;
   private ScheduledTask autoStartTask;
   private int collapseTimes = 0;
   private int autoStartCountdown = 0;
   private boolean autoStartActive = false;
   private boolean autoStartEnabled = true; // 自动开始功能开关
   private int autoStartMinPlayers = 2; // 自动开始所需的最少玩家数量
   
   // 边界收缩倒计时
   private int borderShrinkCountdown = 0;
   private ScheduledTask borderCountdownTask = null;
   
   // 规则投票系统
   private List<RuleType> votingRules = new ArrayList<>();
   private Map<UUID, RuleType> playerVotes = new HashMap<>();
   private boolean votingLocked = false;
   private RuleType selectedRule = RuleType.NONE;
   
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
   private boolean keyInversionActive = false;
   private final com.newpillar.cache.PlayerCache playerCache = new com.newpillar.cache.PlayerCache();
   
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

      // 使用GlobalRegionScheduler在全局区域线程上执行世界设置
      Bukkit.getGlobalRegionScheduler().execute(this.plugin, () -> {
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
         worldBorder.setCenter(100, 100);    // 中心设置在出生点附近
         worldBorder.setSize(20000.0);       // 游戏未进行时边界为20000格
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
      this.borderTimer = this.plugin.getConfig().getInt("timers.border_time", 51); // 首次收缩前等待51秒
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
      if (force || this.canStartGame()) {
         if (force) {
            this.forceJoinAllPlayers();
         }

         if (this.readyPlayers.size() < 1) {
            this.broadcastMessage("§c没有足够的玩家开始游戏！");
         } else {
            this.gameStatus = GameStatus.PLAYING;
            this.gameId++;

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
            
            // 将未被选中的玩家设为观察者
            this.handleNotSelectedPlayers(notSelectedPlayers);
            
            int totalPlayers = this.readyPlayers.size();

            for (UUID uuid : this.readyPlayers) {
               Player player = Bukkit.getPlayer(uuid);
               if (player != null && player.isOnline()) {
                  PlayerData data = this.playerDataMap.get(uuid);
                  if (data != null) {
                     data.setGameId(this.gameId);
                     data.setState(PlayerState.INGAME);
                     this.alivePlayers.add(uuid);
                     Bukkit.getRegionScheduler().execute(this.plugin, player.getLocation(), () -> player.setGameMode(GameMode.ADVENTURE));
                  }
               }
            }

            for (UUID uuidx : this.spectators) {
               Player player = Bukkit.getPlayer(uuidx);
               if (player != null && player.isOnline()) {
                  Bukkit.getRegionScheduler().execute(this.plugin, player.getLocation(), () -> player.setGameMode(GameMode.SPECTATOR));
               }
            }

            World world = this.getGameWorld();
            if (world != null) {
               Bukkit.getGlobalRegionScheduler().execute(this.plugin, () -> {
                  world.setStorm(false);
                  world.setThundering(false);
               });
            }

            for (UUID uuidxx : this.readyPlayers) {
               Player player = Bukkit.getPlayer(uuidxx);
               if (player != null && player.isOnline()) {
                  Bukkit.getRegionScheduler().execute(this.plugin, player.getLocation(), () -> {
                     player.getInventory().clear();
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
            this.startCountdown();
            if (world != null) {
               Bukkit.getGlobalRegionScheduler().execute(this.plugin, () -> world.setTime(1000L));
            }

            if (world != null) {
               Bukkit.getGlobalRegionScheduler().execute(this.plugin, () -> world.setGameRule(GameRule.KEEP_INVENTORY, false));
            }

            this.broadcastMessage("§a游戏开始！准备倒计时...");
         }
      }
   }

   public boolean canStartGame() {
      return this.gameStatus != GameStatus.LOBBY ? false : this.readyPlayers.size() >= 2;
   }

   private void startCountdown() {
      World world = this.getGameWorld();
      if (world != null) {
         int[] remaining = new int[]{this.beginTimer};
         this.countdownTask = Bukkit.getRegionScheduler().runAtFixedRate(this.plugin, world, 0, 0, scheduledTask -> {
            if (this.gameStatus != GameStatus.PLAYING) {
               scheduledTask.cancel();
            } else {
               if (remaining[0] <= 5 && remaining[0] > 0) {
                  String color = this.getCountdownColor(remaining[0]);
                  String title = color + remaining[0];

                  for (Player player : Bukkit.getOnlinePlayers()) {
                     player.sendTitle(title, "", 5, 20, 5);
                     player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.0F);
                  }
               }

               if (remaining[0] <= 0) {
                  scheduledTask.cancel();
                  this.onGameActuallyStart();
               } else {
                  remaining[0]--;
               }
            }
         }, 1L, 20L);
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
            Bukkit.getRegionScheduler().execute(this.plugin, player.getLocation(), () -> {
               player.setGameMode(GameMode.SURVIVAL);
               // 应用规则到每个玩家
               this.ruleSystem.applyRuleToPlayer(player);
            });
         }
      }

      // 启动规则系统
      this.ruleSystem.start();
      
      // 设置世界边界中心和初始大小
      World world = this.getGameWorld();
      if (world != null) {
         Location center = this.mapRegion.getCenter();
         org.bukkit.WorldBorder worldBorder = world.getWorldBorder();
         worldBorder.setCenter(center);
         worldBorder.setSize(100.0); // 初始边界大小 100x100（从100开始收缩）
         this.plugin.getLogger().info("世界边界已设置：中心(" + center.getBlockX() + ", " + center.getBlockZ() + "), 大小: 100");
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
      this.broadcastMessage("§e本局游戏共有 " + allReadyPlayers.size() + " 人准备，随机选中 " + selected.size() + " 人参与！");
      
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
            // 添加到观察者集合
            this.spectators.add(uuid);
            
            // 获取玩家数据并更新状态
            PlayerData data = this.playerDataMap.get(uuid);
            if (data != null) {
               data.setState(PlayerState.SPECTATOR);
            }
            
            // 设置为观察者模式
            Bukkit.getRegionScheduler().execute(this.plugin, player.getLocation(), () -> {
               player.setGameMode(GameMode.SPECTATOR);
               player.sendMessage("§7你当前是观察者，可以飞行观看游戏。");
            });
            
            this.plugin.getLogger().info("[玩家选择] 玩家 " + player.getName() + " 未被选中，设为观察者");
         }
      }
   }

   private void startGameLoop() {
      World world = this.getGameWorld();
      if (world != null) {
         // 启动边界收缩（延迟borderTimer秒后）
         this.startBorderShrink(world);

         this.gameLoopTask = Bukkit.getRegionScheduler().runAtFixedRate(this.plugin, world, 0, 0, scheduledTask -> {
            if (this.gameStatus != GameStatus.PLAYING) {
               scheduledTask.cancel();
            } else {
               this.gameTimeSec++;
               if (this.gameTimeSec >= 60) {
                  this.gameTimeSec = 0;
                  this.gameTimeMin++;
               }

               this.sidebarManager.update();
               if (this.alivePlayers.isEmpty()) {
                  this.endGame();
               }
            }
         }, 1L, 20L);
      }
   }

   /**
    * 启动边界收缩 - 与数据包一致：分阶段收缩
    */
   private void startBorderShrink(World world) {
      // 初始化倒计时
      this.borderShrinkCountdown = this.borderTimer;
      
      // 启动倒计时任务
      Location centerLoc = world.getWorldBorder().getCenter();
      this.borderCountdownTask = Bukkit.getRegionScheduler().runAtFixedRate(this.plugin, centerLoc, new java.util.function.Consumer<ScheduledTask>() {
         @Override
         public void accept(ScheduledTask scheduledTask) {
            if (GameManager.this.gameStatus != GameStatus.PLAYING) {
               scheduledTask.cancel();
               return;
            }
            
            GameManager.this.borderShrinkCountdown--;
            
            if (GameManager.this.borderShrinkCountdown <= 0) {
               scheduledTask.cancel();
               // 开始分阶段收缩循环
               GameManager.this.runBorderShrinkCycle(world);
            }
         }
      }, 20L, 20L); // 每秒更新一次
   }

   /**
    * 边界收缩周期 - 优化为更快节奏
    * 初始大小100，每次减少10，最终到30，然后平台崩溃
    * 从100->90->80->...->30，共7次收缩，每次间隔20秒
    */
   private void runBorderShrinkCycle(World world) {
      org.bukkit.WorldBorder worldBorder = world.getWorldBorder();
      double currentSize = worldBorder.getSize();
      Location centerLoc = worldBorder.getCenter();

      // 如果边界大于等于最小尺寸(30)，继续收缩
      if (currentSize >= 30.0) {
         // 减少10格，用时15秒（动画时间）
         worldBorder.setSize(currentSize - 10, 15L);
         this.broadcastMessage("§6§l[幸运之柱] §c边界开始收缩！当前大小: " + (int)currentSize + " -> " + (int)(currentSize - 10));
         this.debugLogger.debug("[Border] Shrink: " + currentSize + " -> " + (currentSize - 10));

         // 设置倒计时为20秒（15秒动画 + 5秒间隔）
         this.borderShrinkCountdown = 20;
         
         // 启动倒计时任务，20秒后再次收缩
         this.borderCountdownTask = Bukkit.getRegionScheduler().runAtFixedRate(this.plugin, centerLoc, new java.util.function.Consumer<ScheduledTask>() {
            @Override
            public void accept(ScheduledTask scheduledTask) {
               if (GameManager.this.gameStatus != GameStatus.PLAYING) {
                  scheduledTask.cancel();
                  return;
               }
               
               GameManager.this.borderShrinkCountdown--;
               
               if (GameManager.this.borderShrinkCountdown <= 0) {
                  scheduledTask.cancel();
                  if (GameManager.this.gameStatus == GameStatus.PLAYING) {
                     GameManager.this.runBorderShrinkCycle(world);
                  }
               }
            }
         }, 20L, 20L);
      } else {
         // 边界小于30格，触发平台崩溃（半径缩小模式）
         this.startPlatformCollapseRadiusMode(world);
      }
   }

   /**
    * 平台崩溃机制 - 半径缩小模式
    * 以地图中心为圆心，按半径从大到小依次破坏方块
    * 第1轮：91格外 → 第2轮：61格外 → 第3轮：31格外 → 第4轮：逐格缩小直至0
    */
   private void startPlatformCollapseRadiusMode(World world) {
      if (this.collapseTimes >= 4) {
         return; // 最多4轮
      }

      this.collapseTimes++;
      this.broadcastMessage("§6§l[幸运之柱] §c平台开始崩溃！第 " + this.collapseTimes + "/4 轮");
      this.debugLogger.debug("平台崩溃（半径模式）第 " + this.collapseTimes + " 次");

      // 定义每轮的半径范围
      int outerRadius, innerRadius;
      boolean isFinalRound = false;
      switch (this.collapseTimes) {
         case 1 -> { 
            outerRadius = 100; 
            innerRadius = 91; 
            this.broadcastMessage("§c§l91格半径外的方块开始消失！");
         }
         case 2 -> { 
            outerRadius = 91; 
            innerRadius = 61; 
            this.broadcastMessage("§c§l61-91格半径的方块开始消失！");
         }
         case 3 -> { 
            outerRadius = 61; 
            innerRadius = 31; 
            this.broadcastMessage("§c§l31-61格半径的方块开始消失！");
         }
         case 4 -> { 
            outerRadius = 31; 
            innerRadius = 0; 
            isFinalRound = true;
            this.broadcastMessage("§c§l最后阶段！平台将从外向内逐格崩溃！");
         }
         default -> { return; }
      }

      Location centerLoc = new Location(world, 0, 0, 0);
      
      if (!isFinalRound) {
         // 前3轮：直接破坏指定半径范围内的所有方块
         int blocksPerTick = 500; // 每tick破坏500个方块
         int[] currentRadius = {outerRadius};
         
         this.collapseTask = Bukkit.getRegionScheduler().runAtFixedRate(this.plugin, centerLoc, scheduledTask -> {
            int blocksBroken = 0;
            int radius = currentRadius[0];
            
            // 在当前半径层破坏方块（整层Y轴）
            for (int x = -radius; x <= radius && blocksBroken < blocksPerTick; x++) {
               for (int z = -radius; z <= radius && blocksBroken < blocksPerTick; z++) {
                  // 检查是否在圆环范围内
                  double distance = Math.sqrt(x * x + z * z);
                  if (distance >= innerRadius && distance < outerRadius) {
                     // 破坏该位置的所有Y层方块
                     for (int y = -64; y <= 320; y++) {
                        world.getBlockAt(x, y, z).setType(Material.AIR);
                     }
                     blocksBroken++;
                  }
               }
            }
            
            currentRadius[0]--;
            
            // 检查是否完成本轮
            if (currentRadius[0] < innerRadius || this.gameStatus != GameStatus.PLAYING) {
               scheduledTask.cancel();
               this.debugLogger.debug("平台崩溃第 " + this.collapseTimes + " 轮完成");
               
               // 延迟后开始下一轮
               if (this.collapseTimes < 4 && this.gameStatus == GameStatus.PLAYING) {
                  Bukkit.getRegionScheduler().runDelayed(this.plugin, centerLoc, task -> {
                     if (GameManager.this.gameStatus == GameStatus.PLAYING) {
                        GameManager.this.startPlatformCollapseRadiusMode(world);
                     }
                  }, 100L); // 5秒间隔
               }
            }
         }, 0L, 1L); // 每tick执行
         
      } else {
         // 第4轮：逐格缩小，一格一格地破坏
         int[] currentRadius = {outerRadius}; // 从31格开始
         
         this.collapseTask = Bukkit.getRegionScheduler().runAtFixedRate(this.plugin, centerLoc, scheduledTask -> {
            int radius = currentRadius[0];
            
            // 破坏当前半径层的所有方块（整圈）
            for (int x = -radius; x <= radius; x++) {
               for (int z = -radius; z <= radius; z++) {
                  double distance = Math.sqrt(x * x + z * z);
                  // 在当前半径±0.5范围内
                  if (distance >= radius - 0.5 && distance <= radius + 0.5) {
                     for (int y = -64; y <= 320; y++) {
                        world.getBlockAt(x, y, z).setType(Material.AIR);
                     }
                  }
               }
            }
            
            this.broadcastMessage("§c§l平台崩溃中... 剩余半径: " + radius + " 格");
            currentRadius[0]--;
            
            // 检查是否完全崩溃
            if (currentRadius[0] < 0 || this.gameStatus != GameStatus.PLAYING) {
               scheduledTask.cancel();
               this.debugLogger.debug("平台完全崩溃！");
               this.broadcastMessage("§c§l平台已完全崩溃！");
            }
         }, 0L, 10L); // 每0.5秒缩小一格
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
      }

      if (this.gameLoopTask != null) {
         this.gameLoopTask.cancel();
      }

      if (this.borderShrinkTask != null) {
         this.borderShrinkTask.cancel();
      }

      if (this.collapseTask != null) {
         this.collapseTask.cancel();
      }

      // 重置崩溃次数
      this.collapseTimes = 0;

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
         
         Bukkit.getRegionScheduler().execute(this.plugin, finalWinnerLocation, () -> {
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
         String[] messages = {
            "",
            "§6§l═══════════════════════════",
            "§e§l        游戏结束！",
            "",
            "§6§l冠军: §f§l" + finalWinnerName,
            "§c§l淘汰王: §f§l" + finalTopKillerName + " §7(§f" + finalTopKills + "§7击杀)",
            "§7游戏时间: §f" + timeString,
            "§6§l═══════════════════════════",
            ""
         };
         
         // 使用遍历所有在线玩家发送消息，确保观察者也能收到
         int msgCount = 0;
         for (Player player : Bukkit.getOnlinePlayers()) {
            for (String msg : messages) {
               player.sendMessage(msg);
            }
            msgCount++;
         }
         this.plugin.getLogger().info("[调试] 广播消息已发送给 " + msgCount + " 名玩家");
         
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
      }

      // 延迟15秒后清理和传送（庆祝时间）
      if (!isShutdown) {
         World world = this.getGameWorld();
         Location centerLoc = world != null ? world.getWorldBorder().getCenter() : new Location(world, 0, 100, 0);
         
         Bukkit.getRegionScheduler().runDelayed(this.plugin, centerLoc, new java.util.function.Consumer<ScheduledTask>() {
            @Override
            public void accept(ScheduledTask task) {
               // 重置世界边界为大厅状态（20000格）
               if (world != null) {
                  org.bukkit.WorldBorder worldBorder = world.getWorldBorder();
                  worldBorder.setCenter(100, 100);
                  worldBorder.setSize(20000.0);
                  GameManager.this.plugin.getLogger().info("世界边界已重置为大厅状态：中心(100, 100)，大小20000");
                  
                  Bukkit.getGlobalRegionScheduler().execute(GameManager.this.plugin, () -> {
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
               
               // 传送所有玩家回大厅（先传送，后设置游戏模式）
               for (Player player : Bukkit.getOnlinePlayers()) {
                  UUID uuid = player.getUniqueId();
                  PlayerData data = GameManager.this.playerDataMap.get(uuid);
                  
                  if (data != null) {
                     // 先传送到大厅
                     player.teleportAsync(lobby);
                  }
               }
               
               // 延迟后重置所有玩家属性和游戏模式（使用 Folia 的 AsyncScheduler）
               Bukkit.getAsyncScheduler().runDelayed(GameManager.this.plugin, (scheduledTask) -> {
                  Bukkit.getGlobalRegionScheduler().execute(GameManager.this.plugin, () -> {
                     for (Player player : Bukkit.getOnlinePlayers()) {
                        Bukkit.getRegionScheduler().execute(GameManager.this.plugin, player.getLocation(), () -> {
                           // 清空背包
                           player.getInventory().clear();
                           // 重置玩家属性（包括体型、攻击伤害、移动速度、跳跃力度、重力等）
                           GameManager.this.ruleSystem.resetPlayerAttributes(player);
                           // 设置为冒险模式（大厅模式）
                           player.setGameMode(GameMode.ADVENTURE);
                        });
                     }
                  });
               }, 5L * 50L, java.util.concurrent.TimeUnit.MILLISECONDS);

               GameManager.this.alivePlayers.clear();
               GameManager.this.readyPlayers.clear();
               GameManager.this.autoStartActive = false;
               GameManager.this.pillarManager.reset();
               GameManager.this.resetMap();
               GameManager.this.sidebarManager.showLobbySidebar();
               
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
            }
         }, 15 * 20L);
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
         Bukkit.getRegionScheduler().runDelayed(this.plugin, winnerLoc, new java.util.function.Consumer<ScheduledTask>() {
            @Override
            public void accept(ScheduledTask task) {
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
            }
         }, delay);
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
         this.playerDataMap.put(uuid, new PlayerData(uuid));
         PlayerData data = this.playerDataMap.get(uuid);
         data.setPlayerName(player.getName());
         data.setState(PlayerState.SPECTATOR);
         data.setGameId(this.gameId);
         this.spectators.add(uuid);

         // 传送到随机存活玩家位置
         Player target = getRandomAlivePlayer();
         if (target != null) {
            player.teleportAsync(target.getLocation());
         } else {
            player.teleportAsync(this.getLobbyLocation());
         }

         player.setGameMode(GameMode.SPECTATOR);
         this.sidebarManager.playerJoin(player);
         this.sidebarManager.showInGameSidebar();
         player.sendMessage("§7你以§f§l观察者§7身份加入游戏！");
         player.sendMessage("§7你可以自由飞行观看比赛。");

         this.broadcastMessage("§7[+] §f" + player.getName() + " §7以观察者身份加入");
      } else {
         this.playerDataMap.put(uuid, new PlayerData(uuid));
         PlayerData lobbyData = this.playerDataMap.get(uuid);
         lobbyData.setPlayerName(player.getName());
         player.teleportAsync(this.getLobbyLocation());
         player.setGameMode(GameMode.ADVENTURE);
         this.sidebarManager.playerJoin(player);
         this.sidebarManager.showLobbySidebar();
         
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
                  player.sendMessage("");
                  player.sendMessage("§6§l═══════════════════════════");
                  player.sendMessage("§e§l        游戏即将开始");
                  player.sendMessage("§e倒计时: §c" + this.autoStartCountdown + " §e秒");
                  player.sendMessage("§6§l═══════════════════════════");
                  
                  // 如果正在进行规则投票，向新玩家显示投票信息
                  if (this.isVotingActive() && !this.votingRules.isEmpty()) {
                     player.sendMessage("");
                     player.sendMessage("§6§l═══════════════════════════");
                     player.sendMessage("§e§l        规则投票进行中");
                     player.sendMessage("");
                     for (int i = 0; i < this.votingRules.size(); i++) {
                        RuleType rule = this.votingRules.get(i);
                        player.sendMessage("§" + rule.getColor() + "§l[" + (i + 1) + "] " + rule.getName());
                        player.sendMessage("§7" + rule.getDescription());
                        player.sendMessage("");
                     }
                     player.sendMessage("§e使用 §f/vote <编号> §e进行规则投票");
                     player.sendMessage("§6§l═══════════════════════════");
                  }
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
         if (readyCount == 2) {
            this.autoStartCountdown = 60;
         } else if (readyCount >= 3 && readyCount <= 5) {
            this.autoStartCountdown = 45;
         } else if (readyCount >= 6 && readyCount <= 8) {
            this.autoStartCountdown = 30;
         } else {
            this.autoStartCountdown = 15;
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
         this.initRuleVoting();
         
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
      
      Location centerLoc = world.getWorldBorder().getCenter();
      
      this.autoStartTask = Bukkit.getRegionScheduler().runAtFixedRate(this.plugin, centerLoc, new java.util.function.Consumer<ScheduledTask>() {
         @Override
         public void accept(ScheduledTask task) {
            if (GameManager.this.gameStatus != GameStatus.LOBBY) {
               task.cancel();
               GameManager.this.autoStartActive = false;
               return;
            }
            
            // 检查准备人数是否足够
            if (GameManager.this.readyPlayers.size() < 2) {
               GameManager.this.broadcastMessage("§c§l[幸运之柱] §c准备人数不足，自动开始已取消！");
               task.cancel();
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
               GameManager.this.lockVotingAndSelectRule();
               GameManager.this.selectAndAnnounceMap();
            }
            
            // 倒计时结束，开始游戏
            if (GameManager.this.autoStartCountdown <= 0) {
               task.cancel();
               GameManager.this.autoStartActive = false;
               GameManager.this.startGame();
            }
         }
      }, 20L, 20L); // 每秒执行一次
   }
   
   /**
    * 初始化规则投票
    */
   public void initRuleVoting() {
      this.votingRules.clear();
      this.playerVotes.clear();
      this.votingLocked = false;
      this.selectedRule = RuleType.NONE;
      
      // 获取所有可用规则（除了NONE）
      List<RuleType> allRules = new ArrayList<>();
      for (RuleType rule : RuleType.values()) {
         if (rule != RuleType.NONE) {
            allRules.add(rule);
         }
      }
      
      // 40%概率将"无规则"作为第一个选项
      if (Math.random() < 0.4) {
         this.votingRules.add(RuleType.NONE);
         // 再从其他规则中随机选择2个
         Collections.shuffle(allRules);
         int count = Math.min(2, allRules.size());
         for (int i = 0; i < count; i++) {
            this.votingRules.add(allRules.get(i));
         }
      } else {
         // 随机选择3个规则（不包含NONE）
         Collections.shuffle(allRules);
         int count = Math.min(3, allRules.size());
         for (int i = 0; i < count; i++) {
            this.votingRules.add(allRules.get(i));
         }
      }
      
      // 广播投票信息
      this.broadcastMessage("");
      this.broadcastMessage("§6§l═══════════════════════════");
      this.broadcastMessage("§e§l        规则投票");
      this.broadcastMessage("");
      for (int i = 0; i < this.votingRules.size(); i++) {
         RuleType rule = this.votingRules.get(i);
         this.broadcastMessage("§" + rule.getColor() + "§l[" + (i + 1) + "] " + rule.getName());
         this.broadcastMessage("§7" + rule.getDescription());
         this.broadcastMessage("");
      }
      this.broadcastMessage("§e使用 §f/vote <编号> §e进行规则投票");
      this.broadcastMessage("§6§l═══════════════════════════");
   }
   
   /**
    * 玩家投票
    */
   public void playerVoteRule(Player player, int voteIndex) {
      // 检查玩家是否在游戏中
      PlayerData data = this.playerDataMap.get(player.getUniqueId());
      if (data == null || (data.getState() != PlayerState.READY && data.getState() != PlayerState.LOBBY)) {
         player.sendMessage("§c只有已加入游戏的玩家才能投票！");
         return;
      }

      if (this.votingLocked) {
         player.sendMessage("§c投票已锁定，无法投票！");
         return;
      }

      if (voteIndex < 1 || voteIndex > this.votingRules.size()) {
         player.sendMessage("§c无效的投票编号！");
         return;
      }

      RuleType votedRule = this.votingRules.get(voteIndex - 1);
      this.playerVotes.put(player.getUniqueId(), votedRule);
      player.sendMessage("§a你已投票给 §r§l" + votedRule.getName());
   }

   /**
    * 检查是否正在进行投票
    */
   public boolean isVotingActive() {
      return !this.votingRules.isEmpty() && !this.votingLocked;
   }

   /**
    * 获取投票选项中的规则
    */
   public RuleType getVotingRule(int index) {
      if (index < 0 || index >= this.votingRules.size()) {
         return null;
      }
      return this.votingRules.get(index);
   }

   /**
    * 执行投票（供 VoteCommand 使用）
    */
   public boolean castVote(Player player, RuleType rule) {
      // 检查玩家是否在游戏中
      PlayerData data = this.playerDataMap.get(player.getUniqueId());
      if (data == null || (data.getState() != PlayerState.READY && data.getState() != PlayerState.LOBBY)) {
         return false;
      }

      if (this.votingLocked) {
         return false;
      }

      // 检查该规则是否在投票选项中
      if (!this.votingRules.contains(rule)) {
         return false;
      }

      // 检查玩家是否已经投过票
      if (this.playerVotes.containsKey(player.getUniqueId())) {
         return false;
      }

      this.playerVotes.put(player.getUniqueId(), rule);
      return true;
   }
   
   /**
    * 锁定投票并选择规则
    */
   private void lockVotingAndSelectRule() {
      this.votingLocked = true;
      
      // 统计票数
      Map<RuleType, Integer> voteCount = new HashMap<>();
      for (RuleType rule : this.votingRules) {
         voteCount.put(rule, 0);
      }
      
      for (RuleType vote : this.playerVotes.values()) {
         voteCount.put(vote, voteCount.getOrDefault(vote, 0) + 1);
      }
      
      // 找出票数最多的规则
      int maxVotes = -1;
      List<RuleType> topRules = new ArrayList<>();
      
      for (Map.Entry<RuleType, Integer> entry : voteCount.entrySet()) {
         if (entry.getValue() > maxVotes) {
            maxVotes = entry.getValue();
            topRules.clear();
            topRules.add(entry.getKey());
         } else if (entry.getValue() == maxVotes) {
            topRules.add(entry.getKey());
         }
      }
      
      // 如果有多个最高票，随机选择一个
      if (topRules.isEmpty() || maxVotes == 0) {
         // 无人投票，随机选择
         this.selectedRule = this.votingRules.get((int) (Math.random() * this.votingRules.size()));
         this.broadcastMessage("§6§l[幸运之柱] §e无人投票，随机选择规则: §r§l" + this.selectedRule.getName());
      } else {
         this.selectedRule = topRules.get((int) (Math.random() * topRules.size()));
         this.broadcastMessage("§6§l[幸运之柱] §e投票结束！本局规则: §r§l" + this.selectedRule.getName());
      }
      
      // 设置规则
      this.ruleSystem.setRule(this.selectedRule);
   }
   
   /**
     * 获取当前投票规则列表
     */
    public List<RuleType> getVotingRules() {
       return new ArrayList<>(this.votingRules);
    }
    
    /**
     * 随机选择并公告地图
     */
    private void selectAndAnnounceMap() {
       // 随机选择地图
       MapType[] allMaps = MapType.values();
       this.selectedMap = allMaps[(int) (Math.random() * allMaps.length)];
       this.mapSelected = true;
       
       // 设置当前地图类型
       this.currentMapType = this.selectedMap;
       
       // 公告地图信息
       this.broadcastMessage("");
       this.broadcastMessage("§6§l═══════════════════════════");
       this.broadcastMessage("§e§l        本局地图");
       this.broadcastMessage("");
       this.broadcastMessage("§6§l" + this.selectedMap.getDisplayName());
       this.broadcastMessage("§7" + this.selectedMap.getDescription());
       this.broadcastMessage("§6§l═══════════════════════════");
    }
    
    /**
     * 获取选中的地图
     */
    public MapType getSelectedMap() {
       return this.selectedMap;
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
      if (this.gameStatus != GameStatus.LOBBY) {
         player.sendMessage("§c游戏进行中，无法切换 spectator！");
      } else {
         UUID uuid = player.getUniqueId();
         PlayerData data = this.playerDataMap.get(uuid);
         if (data != null) {
            // 切换模式
            if (data.getState() == PlayerState.SPECTATOR) {
               // 从旁观切换到准备状态
               data.setState(PlayerState.LOBBY);
               this.spectators.remove(uuid);
               player.sendMessage("§a你已退出旁观模式！");
            } else {
               // 从其他状态切换到旁观
               data.setState(PlayerState.SPECTATOR);
               this.readyPlayers.remove(uuid);
               this.spectators.add(uuid);
               player.sendMessage("§7你现在是旁观者！");
            }
         }
      }
   }

   public void playerOut(Player player) {
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

      // 同步玩家统计信息到数据库
      this.plugin.getStatisticsSystem().syncPlayerToDatabase(uuid);
      this.plugin.getLogger().info("玩家 " + player.getName() + " 出局，转为观察者，统计信息已同步到数据库");

      // 注意：游戏模式设置和传送在PlayerListener.onPlayerRespawn中处理
      // 检查是否只剩一个存活的玩家
      if (this.alivePlayers.size() <= 1) {
         this.endGame();
      }
   }

   private void resetAllPlayers() {
      for (PlayerData data : this.playerDataMap.values()) {
         data.reset();
      }
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

         Bukkit.getGlobalRegionScheduler().execute(this.plugin, () -> {
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
      return new Location(this.getGameWorld(), 100.0, 4.0, 100.0);
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

   public Set<UUID> getAlivePlayers() {
      return this.alivePlayers;
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
            Bukkit.getRegionScheduler().execute(this.plugin, playerLoc, () -> {
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
               Bukkit.getRegionScheduler().execute(this.plugin, playerLoc, () -> {
                  if (!player.isOnline()) return;
                  AttributeInstance gravityAttr = player.getAttribute(Attribute.GRAVITY);
                  if (gravityAttr != null) {
                     gravityAttr.setBaseValue(0.08); // 恢复默认值
                  }
                  AttributeInstance safeFallAttr = player.getAttribute(Attribute.SAFE_FALL_DISTANCE);
                  if (safeFallAttr != null) {
                     safeFallAttr.setBaseValue(3.0); // 恢复默认值
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
            Bukkit.getRegionScheduler().execute(this.plugin, playerLoc, () -> {
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
      int supportedCount = this.templateManager.getClosestSupportedCount(playerCount);
      MapTemplate template = this.templateManager.getTemplate(this.currentMapType, supportedCount);
      int index = 0;

      for (UUID uuid : this.readyPlayers) {
         Player player = Bukkit.getPlayer(uuid);
         if (player != null && player.isOnline() && index < template.getPillars().size()) {
            MapTemplate.PillarConfig pillar = template.getPillars().get(index);
            Location teleportLoc = pillar.getTeleportLocation(this.mapRegion.getCenter());
            player.teleportAsync(teleportLoc);
            index++;
         }
      }
   }

   public Player getLookAtMeTarget() {
      return this.lookAtMeTarget;
   }

   public void setLookAtMeTarget(Player lookAtMeTarget) {
      this.lookAtMeTarget = lookAtMeTarget;
   }

   public boolean isKeyInversionActive() {
      return this.keyInversionActive;
   }

   public void setKeyInversionActive(boolean keyInversionActive) {
      this.keyInversionActive = keyInversionActive;
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
      return this.borderShrinkCountdown;
   }
   
   public com.newpillar.cache.PlayerCache getPlayerCache() {
      return this.playerCache;
   }
}
