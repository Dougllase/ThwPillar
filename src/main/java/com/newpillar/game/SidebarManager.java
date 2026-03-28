package com.newpillar.game;

import com.newpillar.game.events.EventSystem;

import com.newpillar.game.enums.MapType;

import com.newpillar.game.enums.RuleType;

import com.newpillar.game.enums.EventType;

import com.newpillar.game.enums.GameStatus;

import com.newpillar.NewPillar;
import fr.mrmicky.fastboard.FastBoard;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * 侧边栏管理器 - 设计同步自 Lucky-Pillar 数据包
 * 大厅和游戏中的侧边栏显示
 */
public class SidebarManager {
   private final NewPillar plugin;
   private final GameManager gameManager;
   private final Map<UUID, FastBoard> playerBoards;
   
   // 版本号和发布日期 (同步自数据包)
   private static final String VERSION = "v1.1.4";
   private static final String RELEASE_DATE = "2026.02.27";
   
   // 颜色代码 (同步自数据包 #1bf169 绿色主题)
   private static final String COLOR_PRIMARY = "§a";      // 绿色 (标题/边框)
   private static final String COLOR_SECONDARY = "§f";    // 白色 (标签)
   private static final String COLOR_ACCENT = "§5";       // 深紫色 加粗 (游戏模式)
   private static final String COLOR_VALUE = "§a";        // 绿色 加粗 (数值)
   private static final String COLOR_VALUE_GOLD = "§6";   // 金色 加粗 (地图名)
   private static final String COLOR_VALUE_AQUA = "§b";   // 青色 加粗 (时间)
   private static final String COLOR_VALUE_PURPLE = "§d"; // 粉色 加粗 (规则)
   private static final String BOLD = "§l";               // 加粗

   /**
    * 获取配置中的服务器名称显示（居中）
    * 支持十六进制颜色代码 (&#RRGGBB)
    */
   private String getServerNameLine() {
      String serverName = this.plugin.getConfig().getString("server.name", "&#e991ff桃&#f08cec花&#f787d8坞");
      String serverColor = this.plugin.getConfig().getString("server.color", "§d");
      boolean serverBold = this.plugin.getConfig().getBoolean("server.bold", true);

      // 去除可能的引号
      serverName = serverName.replace("\"", "");

      // 解析颜色代码
      String parsedName;
      if (serverName.contains("&") || serverName.contains("§")) {
         parsedName = parseColorCodes(serverName);
      } else {
         parsedName = serverColor + (serverBold ? BOLD : "") + serverName;
      }

      // 计算文本长度（去除颜色代码）
      String plainText = parsedName.replaceAll("§[0-9a-fA-Fk-oK-OrRxX]", "");
      int textLength = plainText.length();

      // 侧边栏宽度约为 20-22 个字符，计算居中需要的空格
      int totalWidth = 20;
      int spacesNeeded = (totalWidth - textLength) / 2 + 4; // 额外添加4个空格向右偏移2个中文字符
      if (spacesNeeded < 0) spacesNeeded = 0;

      // 构建居中的文本
      StringBuilder centered = new StringBuilder();
      for (int i = 0; i < spacesNeeded; i++) {
         centered.append(" ");
      }
      centered.append(parsedName);

      return centered.toString();
   }

   /**
    * 解析颜色代码（包括十六进制颜色）
    * 支持 &#RRGGBB 格式转换为 Minecraft 1.16+ 的 §x§R§R§G§G§B§B 格式
    */
   private String parseColorCodes(String text) {
      if (text == null || text.isEmpty()) {
         return text;
      }

      // 转换 & 为 §
      text = text.replace('&', '§');

      // 解析十六进制颜色代码 (§#RRGGBB -> §x§R§R§G§G§B§B)
      java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("§#([0-9a-fA-F]{6})");
      java.util.regex.Matcher matcher = pattern.matcher(text);
      StringBuffer sb = new StringBuffer();

      while (matcher.find()) {
         String hex = matcher.group(1);
         StringBuilder replacement = new StringBuilder("§x");
         for (char c : hex.toCharArray()) {
            replacement.append("§").append(Character.toLowerCase(c));
         }
         matcher.appendReplacement(sb, replacement.toString());
      }
      matcher.appendTail(sb);

      return sb.toString();
   }

   public SidebarManager(NewPillar plugin, GameManager gameManager) {
      this.plugin = plugin;
      this.gameManager = gameManager;
      this.playerBoards = new ConcurrentHashMap<>();
   }

   private void createBoard(Player player) {
      FastBoard board = new FastBoard(player);
      // 标题使用绿色主题
      board.updateTitle(COLOR_PRIMARY + "幸运之柱");
      this.playerBoards.put(player.getUniqueId(), board);
   }

   private void removeBoard(Player player) {
      FastBoard board = this.playerBoards.remove(player.getUniqueId());
      if (board != null) {
         board.delete();
      }
   }

   /**
    * 显示大厅侧边栏 (同步自数据包 lobby.mcfunction)
    */
   public void showLobbySidebar() {
      for (Player player : Bukkit.getOnlinePlayers()) {
         FastBoard board = this.playerBoards.get(player.getUniqueId());
         if (board == null) {
            this.createBoard(player);
            board = this.playerBoards.get(player.getUniqueId());
         }

         if (board != null) {
            this.updateLobbySidebar(board);
         }
      }
   }

   /**
    * 更新大厅侧边栏内容
    * 格式同步自 Lucky-Pillar 数据包
    */
   private void updateLobbySidebar(FastBoard board) {
      String gameMode = this.getGameModeName();
      int readyCount = this.gameManager.getReadyPlayerCount();
      String mapName = this.gameManager.getCurrentMapType().getDisplayName();
      String ruleName = this.gameManager.getRuleSystem().getCurrentRule().getName();
      int lootTime = this.plugin.getConfig().getInt("timers.loot_time", 30);
      int eventTime = this.plugin.getConfig().getInt("timers.event_time", 45);
      int borderTime = this.plugin.getConfig().getInt("timers.border_time", 120);

      // 获取自动开始倒计时（如果正在进行）
      int autoStartCountdown = this.gameManager.getAutoStartCountdown();
      boolean autoStartActive = this.gameManager.isAutoStartActive();

      // 如果规则是NONE，不显示规则行
      RuleType currentRule = this.gameManager.getRuleSystem().getCurrentRule();

      // 判断是否为海洋地图
      boolean isSeaMap = this.gameManager.getCurrentMapType() == MapType.SEA;

      // 构建倒计时显示行
      String countdownLine;
      if (autoStartActive && autoStartCountdown > 0) {
         countdownLine = "  " + COLOR_SECONDARY + "开始倒计时：" + COLOR_VALUE + BOLD + autoStartCountdown + "s";
      } else {
         countdownLine = "  " + COLOR_SECONDARY + "边界收缩：" + COLOR_VALUE_AQUA + BOLD + borderTime + "s";
      }

      // 构建物品行（海洋地图显示特殊提示）
      String lootLine = isSeaMap
         ? "  " + COLOR_SECONDARY + "获取物品：" + COLOR_VALUE_AQUA + BOLD + "请钓鱼获取物品！"
         : "  " + COLOR_SECONDARY + "物品间隔：" + COLOR_VALUE_AQUA + BOLD + lootTime + "s";

      // 获取服务器名称行
      String serverLine = this.getServerNameLine();

      if (currentRule == RuleType.NONE) {
         board.updateLines(
            COLOR_PRIMARY + "------- " + VERSION + " -------",
            "  " + COLOR_SECONDARY + "游戏模式：" + COLOR_ACCENT + BOLD + gameMode,
            "  " + COLOR_SECONDARY + "准备人数：" + COLOR_VALUE + BOLD + readyCount,
            "  " + COLOR_SECONDARY + "当前地图：" + COLOR_VALUE_GOLD + BOLD + mapName,
            lootLine,
            "  " + COLOR_SECONDARY + "事件间隔：" + COLOR_VALUE_AQUA + BOLD + eventTime + "s",
            countdownLine,
            serverLine
         );
      } else {
         board.updateLines(
            COLOR_PRIMARY + "------- " + VERSION + " -------",
            "  " + COLOR_SECONDARY + "游戏模式：" + COLOR_ACCENT + BOLD + gameMode,
            "  " + COLOR_SECONDARY + "准备人数：" + COLOR_VALUE + BOLD + readyCount,
            "  " + COLOR_SECONDARY + "当前地图：" + COLOR_VALUE_GOLD + BOLD + mapName,
            "  " + COLOR_SECONDARY + "当前规则：" + COLOR_VALUE_PURPLE + BOLD + ruleName,
            lootLine,
            "  " + COLOR_SECONDARY + "事件间隔：" + COLOR_VALUE_AQUA + BOLD + eventTime + "s",
            countdownLine,
            serverLine
         );
      }
   }

   /**
    * 显示游戏中侧边栏 (同步自数据包 ingame.mcfunction)
    */
   public void showInGameSidebar() {
      for (Player player : Bukkit.getOnlinePlayers()) {
         FastBoard board = this.playerBoards.get(player.getUniqueId());
         if (board == null) {
            this.createBoard(player);
            board = this.playerBoards.get(player.getUniqueId());
         }

         if (board != null) {
            this.updateInGameSidebar(board);
         }
      }
   }

   /**
    * 更新游戏中侧边栏内容
    * 格式同步自 Lucky-Pillar 数据包
    */
   private void updateInGameSidebar(FastBoard board) {
      String gameMode = this.getGameModeName();
      int aliveCount = this.gameManager.getAlivePlayers().size();
      int totalCount = this.gameManager.getGameStartPlayerCount();
      int gameTimeMin = this.gameManager.getGameTimeMin();
      int gameTimeSec = this.gameManager.getGameTimeSec();
      String ruleName = this.gameManager.getRuleSystem().getCurrentRule().getName();
      
      EventSystem eventSystem = this.gameManager.getEventSystem();
      EventType currentEvent = eventSystem.getCurrentEvent();
      int nextEventTime = eventSystem.getEventTimer();
      int borderTime = this.gameManager.getBorderTimer();
      
      // 构建事件显示行
      String eventLine;
      if (currentEvent != null) {
         eventLine = "  " + COLOR_SECONDARY + "当前事件：" + COLOR_VALUE + BOLD + currentEvent.getName();
      } else {
         eventLine = "  " + COLOR_SECONDARY + "下一事件：" + COLOR_VALUE + BOLD + nextEventTime + "s";
      }
      
      // 检查是否处于平台崩溃阶段
      CollapseManager collapseManager = this.gameManager.getCollapseManager();
      String borderLine;
      if (collapseManager != null && collapseManager.isCollapseActive()) {
         // 平台崩溃阶段显示 - 显示剩余格数和时间
         int remainingRadius = collapseManager.getCurrentRadius();
         int remainingSecs = collapseManager.getRemainingSeconds();
         borderLine = "  " + COLOR_SECONDARY + "平台崩溃：" + COLOR_VALUE + BOLD + remainingRadius + "格 " + remainingSecs + "s";
      } else {
         // 边界收缩阶段显示
         borderLine = "  " + COLOR_SECONDARY + "边界收缩：" + COLOR_VALUE + BOLD + borderTime + "s";
      }
      
      // 如果规则是NONE，不显示规则行
      RuleType currentRule = this.gameManager.getRuleSystem().getCurrentRule();

      // 获取服务器名称行
      String serverLine = this.getServerNameLine();

      if (currentRule == RuleType.NONE) {
         board.updateLines(
            COLOR_PRIMARY + "------- " + VERSION + " -------",
            "  " + COLOR_SECONDARY + "游戏模式：" + COLOR_ACCENT + BOLD + gameMode,
            "  " + COLOR_SECONDARY + "游戏人数：" + COLOR_VALUE + BOLD + aliveCount + "/" + COLOR_VALUE_GOLD + BOLD + totalCount,
            "  " + COLOR_SECONDARY + "游戏时长：" + COLOR_VALUE + BOLD + gameTimeMin + "m " + gameTimeSec + "s",
            eventLine,
            borderLine,
            serverLine
         );
      } else {
         board.updateLines(
            COLOR_PRIMARY + "------- " + VERSION + " -------",
            "  " + COLOR_SECONDARY + "游戏模式：" + COLOR_ACCENT + BOLD + gameMode,
            "  " + COLOR_SECONDARY + "游戏人数：" + COLOR_VALUE + BOLD + aliveCount + "/" + COLOR_VALUE_GOLD + BOLD + totalCount,
            "  " + COLOR_SECONDARY + "游戏时长：" + COLOR_VALUE + BOLD + gameTimeMin + "m " + gameTimeSec + "s",
            "  " + COLOR_SECONDARY + "当前规则：" + COLOR_VALUE_PURPLE + BOLD + ruleName,
            eventLine,
            borderLine,
            serverLine
         );
      }
   }

   /**
    * 更新所有玩家的侧边栏
    */
   public void update() {
      for (Player player : Bukkit.getOnlinePlayers()) {
         FastBoard board = this.playerBoards.get(player.getUniqueId());
         if (board == null) {
            this.createBoard(player);
            board = this.playerBoards.get(player.getUniqueId());
         }

         if (board != null) {
            if (this.gameManager.getGameStatus() == GameStatus.PLAYING) {
               this.updateInGameSidebar(board);
            } else {
               this.updateLobbySidebar(board);
            }
         }
      }
   }

   /**
    * 获取游戏模式名称
    */
   private String getGameModeName() {
      return this.gameManager.getGameMode().getDisplayName();
   }

   public void playerJoin(Player player) {
      this.createBoard(player);
      // 玩家加入时显示大厅侧边栏
      FastBoard board = this.playerBoards.get(player.getUniqueId());
      if (board != null) {
         this.updateLobbySidebar(board);
      }
   }

   public void playerLeave(Player player) {
      this.removeBoard(player);
   }

   public void shutdown() {
      for (FastBoard board : this.playerBoards.values()) {
         if (board != null) {
            board.delete();
         }
      }
      this.playerBoards.clear();
   }
}
