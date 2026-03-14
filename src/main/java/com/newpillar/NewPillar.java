package com.newpillar;

import com.newpillar.game.events.LuckyBlockSystem;

import com.newpillar.game.items.LootTableSystem;
import com.newpillar.game.items.RussianRouletteGUI;

import com.newpillar.game.advancements.AdvancementManager;

import com.newpillar.game.advancements.AchievementSystem;

import com.newpillar.game.items.SpecialItemManager;

import com.newpillar.game.advancements.AdvancementGenerator;

import com.newpillar.game.items.ExcaliburManager;
import com.newpillar.game.items.ItemCooldownManager;
import com.newpillar.game.items.ItemEffectManager;
import com.newpillar.game.items.VanillaItemManager;
import com.newpillar.game.items.VanillaItemEffectManager;

import com.newpillar.game.data.StatisticsSystem;

import com.newpillar.commands.NewPillarCommand;
import com.newpillar.database.DatabaseManager;
import com.newpillar.dialog.DialogManager;
import com.newpillar.game.*;
import com.newpillar.integration.ThwRewardIntegration;
import com.newpillar.listeners.PlayerListener;
import com.newpillar.utils.DebugLogger;
import com.newpillar.utils.StructureTemplate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.plugin.java.JavaPlugin;

public class NewPillar extends JavaPlugin {
   private static NewPillar instance;
   private GameManager gameManager;
   private StructureTemplate structureTemplate;
   private DialogManager dialogManager;
   private SpecialItemManager specialItemManager;
   private ItemEffectManager itemEffectManager;
   private VanillaItemManager vanillaItemManager;
   private VanillaItemEffectManager vanillaItemEffectManager;
   private AchievementSystem achievementSystem;
   private StatisticsSystem statisticsSystem;
   private LuckyBlockSystem luckyBlockSystem;
   private LootTableSystem lootTableSystem;
   private DebugLogger debugLogger;
   private DatabaseManager databaseManager;
   private AdvancementManager advancementManager;
   private AdvancementGenerator advancementGenerator;
   private com.newpillar.game.gui.VoteGUI voteGUI;
   private ExcaliburManager excaliburManager;
   private RussianRouletteGUI russianRouletteGUI;
   private ItemCooldownManager itemCooldownManager;
   private SchrodingerCatManager schrodingerCatManager;
   private Set<UUID> pendingFoxSpawns = new HashSet<>();
   private ThwRewardIntegration thwRewardIntegration;

   public void onEnable() {
      instance = this;
      this.saveDefaultConfig();
      this.debugLogger = new DebugLogger(this);
      
      // 初始化数据库
      this.databaseManager = new DatabaseManager(this);
      
      // 初始化原生进度管理器
      this.advancementManager = new AdvancementManager(this);
      this.advancementManager.initialize();
      
      // 初始化动态进度生成器（生成数据包）
      this.advancementGenerator = new AdvancementGenerator(this);
      this.advancementGenerator.generateAdvancements();
      
      this.structureTemplate = new StructureTemplate(this);
      this.gameManager = new GameManager(this);
      this.dialogManager = new DialogManager(this);
      
      // 初始化特殊物品系统
      this.specialItemManager = new SpecialItemManager(this);
      this.itemEffectManager = new ItemEffectManager(this, specialItemManager);
      
      // 初始化原版物品系统
      this.vanillaItemManager = new VanillaItemManager(this);
      this.vanillaItemEffectManager = new VanillaItemEffectManager(this, vanillaItemManager);
      
      // 初始化成就系统
      this.achievementSystem = new AchievementSystem(this);
      
      // 初始化统计系统
      this.statisticsSystem = new StatisticsSystem(this);
      
      // 初始化幸运方块系统
      this.luckyBlockSystem = new LuckyBlockSystem(this);
      
      // 初始化战利品表系统
      this.lootTableSystem = new LootTableSystem(this);

      // 初始化EX咖喱棒管理器
      this.excaliburManager = new ExcaliburManager(this);
      
      // 初始化俄罗斯轮盘枪GUI
      this.russianRouletteGUI = new RussianRouletteGUI(this);
      
      // 初始化物品冷却管理器
      this.itemCooldownManager = new ItemCooldownManager(this);
      
      // 初始化薛定谔的猫管理器
      this.schrodingerCatManager = new SchrodingerCatManager(this);
      
      // 初始化ThwReward集成（子插件，可选）
      this.thwRewardIntegration = new ThwRewardIntegration(this);
      this.thwRewardIntegration.initialize();

      this.getCommand("thwp").setExecutor(new NewPillarCommand(this));
      
      // 注册薛定谔的猫指令
      com.newpillar.commands.SchrodingerCatCommand schrodingerCommand = new com.newpillar.commands.SchrodingerCatCommand(this);
      this.getCommand("schrodinger").setExecutor(schrodingerCommand);
      this.getCommand("schrodinger").setTabCompleter(schrodingerCommand);
      com.newpillar.commands.VoteCommand voteCommand = new com.newpillar.commands.VoteCommand(this);
      this.getCommand("vote").setExecutor(voteCommand);
      this.getCommand("vote").setTabCompleter(voteCommand);
      
      // 初始化投票GUI
      this.voteGUI = new com.newpillar.game.gui.VoteGUI(this, this.gameManager, this.gameManager.getVoteManager());
      this.getServer().getPluginManager().registerEvents(
         new PlayerListener(this, this.gameManager, this.specialItemManager, this.itemEffectManager, this.vanillaItemManager, this.vanillaItemEffectManager),
         this
      );
      
      this.getLogger().info("NewPillar 已启用！");
      this.getLogger().info("数据库系统已加载！");
      this.getLogger().info("原生进度系统已加载！");
      this.getLogger().info("特殊物品系统已加载！");
      this.getLogger().info("成就系统已加载！");
      this.getLogger().info("统计系统已加载！");
      this.getLogger().info("幸运方块系统已加载！");
      this.getLogger().info("战利品表系统已加载！");
      this.getLogger().info("薛定谔的猫系统已加载！");
      this.getLogger().info("ThwReward奖励系统集成: " + (this.thwRewardIntegration.isEnabled() ? "已启用" : "未启用（子插件缺失）"));
   }

   public void onDisable() {
      if (this.gameManager != null) {
         this.gameManager.shutdown();
      }
      
      if (this.itemEffectManager != null) {
         this.itemEffectManager.cleanup();
      }
      
      if (this.vanillaItemEffectManager != null) {
         this.vanillaItemEffectManager.cleanup();
      }
      
      // 关闭数据库连接
      if (this.databaseManager != null) {
         this.databaseManager.close();
      }

      // 关闭物品冷却管理器
      if (this.itemCooldownManager != null) {
         this.itemCooldownManager.shutdown();
      }

      this.getLogger().info("NewPillar 已禁用！");
   }

   public static NewPillar getInstance() {
      return instance;
   }

   public GameManager getGameManager() {
      return this.gameManager;
   }

   public StructureTemplate getStructureTemplate() {
      return this.structureTemplate;
   }

   public DialogManager getDialogManager() {
      return this.dialogManager;
   }
   
   public SpecialItemManager getSpecialItemManager() {
      return this.specialItemManager;
   }
   
   public ItemEffectManager getItemEffectManager() {
      return this.itemEffectManager;
   }
   
   public AchievementSystem getAchievementSystem() {
      return this.achievementSystem;
   }
   
   public StatisticsSystem getStatisticsSystem() {
      return this.statisticsSystem;
   }
   
   public LuckyBlockSystem getLuckyBlockSystem() {
      return this.luckyBlockSystem;
   }
   
   public LootTableSystem getLootTableSystem() {
      return this.lootTableSystem;
   }
   
   public VanillaItemManager getVanillaItemManager() {
      return this.vanillaItemManager;
   }
   
   public VanillaItemEffectManager getVanillaItemEffectManager() {
      return this.vanillaItemEffectManager;
   }

   public DebugLogger getDebugLogger() {
      return this.debugLogger;
   }
   
   public com.newpillar.game.gui.VoteGUI getVoteGUI() {
      return this.voteGUI;
   }

   public DatabaseManager getDatabaseManager() {
      return this.databaseManager;
   }

   public AdvancementManager getAdvancementManager() {
      return this.advancementManager;
   }

   public ExcaliburManager getExcaliburManager() {
      return this.excaliburManager;
   }

   public RussianRouletteGUI getRussianRouletteGUI() {
      return this.russianRouletteGUI;
   }
   
   public ItemCooldownManager getItemCooldownManager() {
      return this.itemCooldownManager;
   }

   public AdvancementGenerator getAdvancementGenerator() {
      return this.advancementGenerator;
   }
   
   public SchrodingerCatManager getSchrodingerCatManager() {
      return this.schrodingerCatManager;
   }

   public ThwRewardIntegration getThwRewardIntegration() {
      return this.thwRewardIntegration;
   }

   // 规则3狐狸生成标记方法
   public void setPendingFoxSpawn(UUID playerUuid) {
      pendingFoxSpawns.add(playerUuid);
   }

   public boolean isPendingFoxSpawn(UUID playerUuid) {
      return pendingFoxSpawns.contains(playerUuid);
   }

   public void clearPendingFoxSpawn(UUID playerUuid) {
      pendingFoxSpawns.remove(playerUuid);
   }
}
