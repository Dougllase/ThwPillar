package com.newpillar.commands;

import com.newpillar.game.map.MapRegion;

import com.newpillar.game.events.EventSystem;

import com.newpillar.game.items.LootTableSystem;

import com.newpillar.game.advancements.AchievementSystem;

import com.newpillar.game.items.SpecialItemManager;

import com.newpillar.game.enums.RuleType;

import com.newpillar.NewPillar;
import com.newpillar.game.enums.EventType;
import com.newpillar.game.GameManager;
import com.newpillar.game.enums.GameStatus;
import com.newpillar.game.enums.MapType;
import com.newpillar.utils.StructureTemplate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class NewPillarCommand implements CommandExecutor, TabCompleter {
   private final NewPillar plugin;

   public NewPillarCommand(NewPillar plugin) {
      this.plugin = plugin;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player player) {
         if (args.length == 0) {
            this.sendHelp(player);
            return true;
         } else {
            GameManager gameManager = this.plugin.getGameManager();
            String var7 = args[0].toLowerCase();
            switch (var7) {
               case "spectator":
                  gameManager.playerSpectate(player);
                  break;
               case "start":
                  if (!player.hasPermission("newpillar.admin")) {
                     player.sendMessage("§c你没有权限！");
                     return true;
                  }

                  gameManager.startGame();
                  break;
               case "forcestart":
                  if (!player.hasPermission("newpillar.admin")) {
                     player.sendMessage("§c你没有权限！");
                     return true;
                  }

                  gameManager.startGame(true);
                  player.sendMessage("§a已强制开始游戏，所有在线玩家已加入！");
                  break;
               case "stop":
                  if (!player.hasPermission("newpillar.admin")) {
                     player.sendMessage("§c你没有权限！");
                     return true;
                  }

                  gameManager.endGame();
                  break;
               case "status":
                  player.sendMessage("§6=== 游戏状态 ===");
                  player.sendMessage("§f状态: " + gameManager.getGameStatus());
                  player.sendMessage("§f游戏ID: " + gameManager.getGameId());
                  player.sendMessage("§f准备玩家: " + gameManager.getReadyPlayers().size());
                  player.sendMessage("§f存活玩家: " + gameManager.getAlivePlayers().size());
                  break;
               case "test":
                  if (!player.hasPermission("newpillar.admin")) {
                     player.sendMessage("§c你没有权限！");
                     return true;
                  }

                  this.handleTestCommand(player, args, gameManager);
                  break;
               case "structure":
                  if (!player.hasPermission("newpillar.admin")) {
                     player.sendMessage("§c你没有权限！");
                     return true;
                  }

                  this.handleStructureCommand(player, args);
                  break;
               case "menu":
                  if (!player.hasPermission("newpillar.admin")) {
                     player.sendMessage("§c你没有权限！");
                     return true;
                  }
                  this.plugin.getDialogManager().openSettingsMenu(player);
                  break;
               case "stats":
                  this.plugin.getDialogManager().openPlayerStatistics(player);
                  break;
               case "rule":
                  this.handleRuleCommand(player, args, gameManager);
                  break;
               case "mapmenu":
                  if (!player.hasPermission("newpillar.admin")) {
                     player.sendMessage("§c你没有权限！");
                     return true;
                  }

                  this.plugin.getDialogManager().openMapSelection(player);
                  break;
               case "event":
                  if (!player.hasPermission("newpillar.admin")) {
                     player.sendMessage("§c你没有权限！");
                     return true;
                  }

                  this.handleEventCommand(player, args, gameManager);
                  break;
               case "item":
                  if (!player.hasPermission("newpillar.admin")) {
                     player.sendMessage("§c你没有权限！");
                     return true;
                  }

                  this.handleItemCommand(player, args);
                  break;
               case "loottest":
                  if (!player.hasPermission("newpillar.admin")) {
                     player.sendMessage("§c你没有权限！");
                     return true;
                  }

                  this.handleLootTestCommand(player, args);
                  break;
               case "autostart":
                  if (!player.hasPermission("newpillar.admin")) {
                     player.sendMessage("§c你没有权限！");
                     return true;
                  }

                  this.handleAutoStartCommand(player, args, gameManager);
                  break;
               case "minplayers":
                  if (!player.hasPermission("newpillar.admin")) {
                     player.sendMessage("§c你没有权限！");
                     return true;
                  }

                  this.handleMinPlayersCommand(player, args, gameManager);
                  break;
               case "allitems":
                  if (!player.hasPermission("newpillar.admin")) {
                     player.sendMessage("§c你没有权限！");
                     return true;
                  }

                  this.handleAllItemsCommand(player, args);
                  break;
               case "collapse":
                  if (!player.hasPermission("newpillar.admin")) {
                     player.sendMessage("§c你没有权限！");
                     return true;
                  }

                  this.handleCollapseCommand(player, gameManager);
                  break;
               default:
                  this.sendHelp(player);
            }

            return true;
         }
      } else {
         sender.sendMessage("§c只有玩家可以执行此命令！");
         return true;
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (args.length == 1) {
         List<String> completions = new ArrayList<>(Arrays.asList("spectator", "status", "stats", "rule"));
         if (sender.hasPermission("newpillar.admin")) {
            completions.addAll(Arrays.asList("start", "forcestart", "stop", "test", "mapmenu", "event", "item", "loottest", "autostart", "menu", "minplayers", "allitems", "collapse"));
         }

         return completions.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
      } else if (args.length == 2 && args[0].equalsIgnoreCase("rule")) {
         return Arrays.asList("vote").stream().filter(s -> s.startsWith(args[1].toLowerCase())).toList();
      } else if (args.length == 3 && args[0].equalsIgnoreCase("rule") && args[1].equalsIgnoreCase("vote")) {
         return Arrays.asList("1", "2", "3").stream().filter(s -> s.startsWith(args[2])).toList();
      } else if (args.length == 2 && args[0].equalsIgnoreCase("autostart")) {
         return Arrays.asList("on", "off", "toggle", "status").stream().filter(s -> s.startsWith(args[1].toLowerCase())).toList();
      } else if (args.length == 2 && args[0].equalsIgnoreCase("minplayers")) {
         return Arrays.asList("set", "get").stream().filter(s -> s.startsWith(args[1].toLowerCase())).toList();
      } else if (args.length == 3 && args[0].equalsIgnoreCase("minplayers") && args[1].equalsIgnoreCase("set")) {
         // 提供几个常用数值的补全
         return Arrays.asList("1", "2", "3", "4", "5").stream().filter(s -> s.startsWith(args[2])).toList();
      } else if (args.length == 2 && args[0].equalsIgnoreCase("event")) {
         return Arrays.asList("trigger", "setnext", "list", "stop").stream().filter(s -> s.startsWith(args[1].toLowerCase())).toList();
      } else if (args.length == 3 && args[0].equalsIgnoreCase("event") && 
                 (args[1].equalsIgnoreCase("trigger") || args[1].equalsIgnoreCase("setnext"))) {
         List<String> completions = new ArrayList<>();
         completions.addAll(
            Arrays.stream(EventType.values()).filter(EventType::isRealEvent).map(Enum::name).filter(s -> s.startsWith(args[2].toUpperCase())).toList()
         );
         if (args[1].equalsIgnoreCase("setnext")) {
            completions.add("clear");
            completions.add("none");
         }
         return completions;
      } else if (args.length == 2 && args[0].equalsIgnoreCase("test")) {
         return Arrays.asList("players", "clear", "clearmap").stream().filter(s -> s.startsWith(args[1].toLowerCase())).toList();
      } else if (args.length == 2 && args[0].equalsIgnoreCase("structure")) {
         return Arrays.asList("place", "info", "list", "clearcache").stream().filter(s -> s.startsWith(args[1].toLowerCase())).toList();
      } else if (args.length == 3 && args[0].equalsIgnoreCase("structure") && args[1].equalsIgnoreCase("place")) {
         return Arrays.asList(
               "cage",
               "pillar/wool/2",
               "pillar/wool/5",
               "pillar/wool/9",
               "pillar/sea/2",
               "pillar/sea/5",
               "pillar/sea/9",
               "pillar/nether/2",
               "pillar/nether/5",
               "pillar/nether/9"
            )
            .stream()
            .filter(s -> s.startsWith(args[2].toLowerCase()))
            .toList();
      } else if (args.length == 2 && args[0].equalsIgnoreCase("item")) {
         // item 指令的自动补全 - 添加 list、simulate 和所有物品类型
         List<String> completions = new ArrayList<>();
         completions.add("list");
         completions.add("simulate");
         completions.addAll(
            Arrays.stream(com.newpillar.game.items.SpecialItemManager.SpecialItemType.values())
               .map(Enum::name)
               .filter(s -> s.startsWith(args[1].toUpperCase()))
               .toList()
         );
         return completions;
      } else if (args.length == 2 && args[0].equalsIgnoreCase("loottest")) {
         // loottest 指令的自动补全 - 列出所有战利品表
         return this.plugin.getLootTableSystem().getAllLootTableNames()
            .stream()
            .filter(s -> s.startsWith(args[1].toLowerCase()))
            .toList();
      } else if (args.length == 3 && args[0].equalsIgnoreCase("loottest")) {
         // loottest 指令的类别筛选补全
         List<String> completions = new ArrayList<>(Arrays.asList("block", "weapon", "food", "tool", "armor", "special", "all", "none"));
         completions.addAll(Arrays.asList("1", "10", "100", "1000"));
         return completions.stream()
            .filter(s -> s.startsWith(args[2].toLowerCase()))
            .toList();
      } else if (args.length == 4 && args[0].equalsIgnoreCase("loottest")) {
         // loottest 指令的次数补全
         return Arrays.asList("1", "10", "100", "1000").stream()
            .filter(s -> s.startsWith(args[3]))
            .toList();
      } else {
         return List.of();
      }
   }

   private void sendHelp(Player player) {
      player.sendMessage("§6=== NewPillar 帮助 ===");
      player.sendMessage("§f/np spectator §7- 切换旁观者");
      player.sendMessage("§f/np status §7- 查看游戏状态");
      player.sendMessage("§f/np stats §7- 查看个人统计信息");
      player.sendMessage("§f/np rule §7- 查看当前规则投票");
      player.sendMessage("§f/np rule vote <编号> §7- 投票选择规则");
      if (player.hasPermission("newpillar.admin")) {
         player.sendMessage("§f/np start §7- 开始游戏 (Admin)");
         player.sendMessage("§f/np forcestart §7- 强制开始游戏，拉入所有在线玩家 (Admin)");
         player.sendMessage("§f/np stop §7- 停止游戏 (Admin)");
         player.sendMessage("§f/np menu §7- 打开设置菜单 (Admin)");
         player.sendMessage("§f/np mapmenu §7- 打开地图选择菜单 (Admin)");
         player.sendMessage("§f/np test players <数量> §7- 生成虚拟玩家测试 (Admin)");
         player.sendMessage("§f/np test clear §7- 清除虚拟玩家 (Admin)");
         player.sendMessage("§f/np test clearmap §7- 清除地图区域方块 (Admin)");
         player.sendMessage("§f/np structure place <结构名> §7- 在当前位置放置NBT结构 (Admin)");
         player.sendMessage("§f/np structure info <结构名> §7- 查看结构信息 (Admin)");
         player.sendMessage("§f/np structure list §7- 列出已缓存的结构 (Admin)");
         player.sendMessage("§f/np structure clearcache §7- 清除结构缓存 (Admin)");
         player.sendMessage("§f/np event trigger <事件名> §7- 强制触发事件 (Admin)");
         player.sendMessage("§f/np event list §7- 列出所有事件 (Admin)");
         player.sendMessage("§f/np event stop §7- 停止当前事件 (Admin)");
         player.sendMessage("§f/np item <物品名> §7- 获取特殊物品 (Admin)");
         player.sendMessage("§f/np item list §7- 列出所有特殊物品 (Admin)");
         player.sendMessage("§f/np allitems [类别] §7- 获取所有特殊物品 (Admin)");
         player.sendMessage("§f/np loottest <战利品表> [类别] [次数] §7- 测试战利品表随机结果 (Admin)");
         player.sendMessage("§7  类别: block, weapon, food, tool, armor, special, all, none");
         player.sendMessage("§f/np autostart <on|off|toggle|status> §7- 控制自动开始功能 (Admin)");
         player.sendMessage("§f/np minplayers set <数量> §7- 设置自动开始所需的最少玩家数 (Admin)");
         player.sendMessage("§f/np minplayers get §7- 查看当前自动开始所需的最少玩家数 (Admin)");
         player.sendMessage("§f/np collapse §7- 强制触发平台崩塌 (Admin)");
      }
   }

   private void handleTestCommand(Player player, String[] args, GameManager gameManager) {
      if (args.length < 2) {
         player.sendMessage("§c用法: /np test <players|clear|clearmap> [参数]");
      } else {
         String var4 = args[1].toLowerCase();
         switch (var4) {
            case "players":
               if (args.length < 3) {
                  player.sendMessage("§c用法: /np test players <数量>");
                  return;
               }

               try {
                  int count = Integer.parseInt(args[2]);
                  if (count >= 1 && count <= 100) {
                     player.sendMessage("§a步骤0: 清除旧地图...");
                     gameManager.getMapRegion().clearRegionBlocks();
                     gameManager.getPillarManager().reset();
                     player.sendMessage("§a步骤1: 拉入所有在线玩家...");
                     gameManager.forceJoinAllPlayers();
                     int realPlayers = gameManager.getReadyPlayers().size();
                     int totalPlayers = realPlayers + count;
                     player.sendMessage("§a真实玩家: " + realPlayers + ", 虚拟玩家: " + count + ", 总计: " + totalPlayers);
                     player.sendMessage("§a步骤2: 生成地图 (总玩家数: " + totalPlayers + ")...");
                     gameManager.generateMapWithTemplate(totalPlayers);
                     player.sendMessage("§a步骤3: 生成 " + count + " 个虚拟玩家...");
                     gameManager.getMapRegion().createVirtualPlayers(count, totalPlayers);
                     player.sendMessage("§a步骤4: 传送玩家到柱子...");
                     gameManager.teleportPlayersWithTemplate(totalPlayers);
                     player.sendMessage("§a步骤5: 开始游戏！");
                     gameManager.startGame(true, true);
                     player.sendMessage("§a测试模式启动完成！");
                     break;
                  }

                  player.sendMessage("§c数量必须在 1-100 之间！");
                  return;
               } catch (NumberFormatException var9) {
                  player.sendMessage("§c请输入有效的数字！");
                  break;
               }
            case "clear":
               int cleared = gameManager.getMapRegion().getVirtualPlayerCount();
               gameManager.getMapRegion().clearVirtualPlayers();
               player.sendMessage("§a已清除 " + cleared + " 个虚拟玩家！");
               break;
            case "clearmap":
               gameManager.getMapRegion().clearRegionBlocks();
               player.sendMessage("§a已开始清除地图区域方块，请查看控制台进度！");
               break;
            default:
               player.sendMessage("§c未知测试命令。用法: /np test <players|clear|clearmap>");
         }
      }
   }

   private void handleStructureCommand(Player player, String[] args) {
      if (args.length < 2) {
         player.sendMessage("§c用法: /np structure <place|info|list|clearcache> [参数]");
      } else {
         StructureTemplate structureTemplate = this.plugin.getStructureTemplate();
         String var4 = args[1].toLowerCase();
         switch (var4) {
            case "place":
               if (args.length < 3) {
                  player.sendMessage("§c用法: /np structure place <结构名>");
                  player.sendMessage("§7示例: /np structure place cage");
                  player.sendMessage("§7示例: /np structure place pillar/wool/2");
                  return;
               }

               String structureNamex = args[2];
               Location loc = player.getLocation();
               player.sendMessage("§6正在放置结构: §f" + structureNamex);
               player.sendMessage("§7位置: §f" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
               boolean success = structureTemplate.placeStructure(structureNamex, loc);
               if (success) {
                  player.sendMessage("§a结构放置成功！");
               } else {
                  player.sendMessage("§c结构放置失败！请检查结构名称是否正确。");
                  player.sendMessage("§7结构文件应位于: D:\\HYKJ\\Desktop\\thw_projects\\Lucky-Pillar\\src\\data\\yw-pillar\\structure\\" + structureNamex + ".nbt");
               }
               break;
            case "info":
               if (args.length < 3) {
                  player.sendMessage("§c用法: /np structure info <结构名>");
                  return;
               }

               String structureName = args[2];
               player.sendMessage("§6正在加载结构: §f" + structureName);
               if (!structureTemplate.loadStructure(structureName)) {
                  player.sendMessage("§c无法加载结构！");
                  return;
               }

               int[] size = structureTemplate.getStructureSize(structureName);
               if (size != null) {
                  player.sendMessage("§a结构信息:");
                  player.sendMessage("§7名称: §f" + structureName);
                  player.sendMessage("§7大小: §f" + size[0] + " x " + size[1] + " x " + size[2]);
               } else {
                  player.sendMessage("§c无法获取结构大小！");
               }
               break;
            case "list":
               List<String> cachedStructures = structureTemplate.getCachedStructures();
               if (cachedStructures.isEmpty()) {
                  player.sendMessage("§7没有已缓存的结构。");
               } else {
                  player.sendMessage("§6已缓存的结构 (" + cachedStructures.size() + " 个):");

                  for (String name : cachedStructures) {
                     player.sendMessage("§7- §f" + name);
                  }
               }
               break;
            case "clearcache":
               structureTemplate.clearCache();
               player.sendMessage("§a结构缓存已清除！");
               break;
            default:
               player.sendMessage("§c未知命令。用法: /np structure <place|info|list|clearcache>");
         }
      }
   }

   private void handleEventCommand(Player player, String[] args, GameManager gameManager) {
      if (args.length < 2) {
         player.sendMessage("§c用法: /np event <trigger|setnext|list|stop> [参数]");
      } else {
         String var4 = args[1].toLowerCase();
         switch (var4) {
            case "trigger":
               if (args.length < 3) {
                  player.sendMessage("§c用法: /np event trigger <事件名>");
                  player.sendMessage("§7使用 §f/np event list §7查看所有可用事件");
                  return;
               }

               String eventName = args[2].toUpperCase();
               EventType eventType = EventType.getByName(eventName);
               if (eventType == null) {
                  player.sendMessage("§c未知的事件: §f" + eventName);
                  player.sendMessage("§7使用 §f/np event list §7查看所有可用事件");
                  return;
               }

               if (!eventType.isRealEvent()) {
                  player.sendMessage("§c不能触发'无事发生'类型的事件！");
                  return;
               }

               if (gameManager.getGameStatus() != GameStatus.PLAYING) {
                  player.sendMessage("§c游戏未在进行中！");
                  return;
               }

               gameManager.getEventSystem().triggerEvent(eventType);
               player.sendMessage("§a已强制触发事件: §f" + eventType.getName());
               break;
            case "setnext":
               if (args.length < 3) {
                  player.sendMessage("§c用法: /np event setnext <事件名>");
                  player.sendMessage("§7设置下一次触发的事件（不是立即触发）");
                  player.sendMessage("§7使用 §f/np event list §7查看所有可用事件");
                  player.sendMessage("§7使用 §f/np event setnext clear §7取消设置");
                  return;
               }

               String eventNamex = args[2].toUpperCase();
               if (eventNamex.equals("CLEAR") || eventNamex.equals("NONE")) {
                  gameManager.getEventSystem().setForcedNextEvent(null);
                  player.sendMessage("§a已清除下一次事件的强制设置");
                  return;
               }

               EventType eventTypex = EventType.getByName(eventNamex);
               if (eventTypex == null) {
                  player.sendMessage("§c未知的事件: §f" + eventNamex);
                  player.sendMessage("§7使用 §f/np event list §7查看所有可用事件");
                  return;
               }

               if (!eventTypex.isRealEvent()) {
                  player.sendMessage("§c不能设置'无事发生'类型的事件！");
                  return;
               }

               gameManager.getEventSystem().setForcedNextEvent(eventTypex);
               EventType current = gameManager.getEventSystem().getForcedNextEvent();
               player.sendMessage("§a已设置下一次事件为: §f" + eventTypex.getName());
               player.sendMessage("§7注意: 事件将在下一次计时器触发时执行");
               break;
            case "list":
               player.sendMessage("§6=== 可用事件列表 ===");
               player.sendMessage(
                  "§7当前事件: §f" + (gameManager.getEventSystem().getCurrentEvent() != null ? gameManager.getEventSystem().getCurrentEvent().getName() : "无")
               );
               player.sendMessage("");
               int count = 0;

               for (EventType type : EventType.values()) {
                  if (type.isRealEvent()) {
                     player.sendMessage("§f" + type.name() + " §7- " + type.getName());
                     player.sendMessage("  §8" + type.getDescription() + " §7(持续" + type.getDuration() + "秒)");
                     if (++count >= 10) {
                        player.sendMessage("§7... 还有 " + (EventType.values().length - count - 4) + " 个事件");
                        break;
                     }
                  }
               }

               player.sendMessage("");
               player.sendMessage("§7共 §f" + (EventType.values().length - 4) + " §7个有效事件");
               break;
            case "stop":
               if (gameManager.getEventSystem().getCurrentEvent() == null) {
                  player.sendMessage("§c当前没有正在运行的事件！");
                  return;
               }

               gameManager.getEventSystem().stop();
               player.sendMessage("§a已停止当前事件！");
               break;
            default:
               player.sendMessage("§c未知命令。用法: /np event <trigger|list|stop>");
         }
      }
   }

   private void handleItemCommand(Player player, String[] args) {
      if (args.length < 2) {
         player.sendMessage("§c用法: /np item <物品名|list|simulate>");
         player.sendMessage("§7使用 §f/np item list §7查看所有可用物品");
         player.sendMessage("§7使用 §f/np item simulate <次数> §7模拟战利品获取");
         return;
      }

      String itemName = args[1].toLowerCase();

      if (itemName.equals("list")) {
         player.sendMessage("§6=== 特殊物品列表 ===");
         for (com.newpillar.game.items.SpecialItemManager.SpecialItemType type : com.newpillar.game.items.SpecialItemManager.SpecialItemType.values()) {
            player.sendMessage("§f" + type.name() + " §7- " + type.getDisplayName() + " §8[" + type.getCategory() + "]");
         }
         player.sendMessage("");
         player.sendMessage("§7使用 §f/np item <物品名> §7获取物品");
         return;
      }
      
      // 模拟战利品获取调试指令
      if (itemName.equals("simulate")) {
         int times = 100; // 默认100次
         if (args.length >= 3) {
            try {
               times = Integer.parseInt(args[2]);
               if (times < 1 || times > 10000) {
                  player.sendMessage("§c模拟次数必须在 1-10000 之间！");
                  return;
               }
            } catch (NumberFormatException e) {
               player.sendMessage("§c请输入有效的数字！");
               return;
            }
         }
         
         final int finalTimes = times;
         player.sendMessage("§6正在模拟 §f" + finalTimes + " §6次战利品获取...");
         
         java.util.Map<String, Integer> results = new java.util.HashMap<>();
         int[] specialItemCount = {0};
         
         for (int i = 0; i < finalTimes; i++) {
            org.bukkit.inventory.ItemStack item = this.plugin.getLootTableSystem().generateLoot();
            if (item != null && item.getType() != org.bukkit.Material.AIR) {
               String itemName2 = item.hasItemMeta() && item.getItemMeta().hasDisplayName() 
                  ? item.getItemMeta().getDisplayName() 
                  : item.getType().name();
               results.merge(itemName2, 1, Integer::sum);
               
               // 检查是否是特殊物品
               if (item.hasItemMeta()) {
                  org.bukkit.persistence.PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
                  org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(this.plugin, "item_id");
                  if (container.has(key, org.bukkit.persistence.PersistentDataType.STRING)) {
                     specialItemCount[0]++;
                  }
               }
            }
         }
         
         player.sendMessage("§a模拟完成！结果统计：");
         player.sendMessage("§7总获取物品数: §f" + results.values().stream().mapToInt(Integer::intValue).sum());
         player.sendMessage("§7特殊物品数: §f" + specialItemCount[0] + " §7(" + String.format("%.2f%%", specialItemCount[0] * 100.0 / finalTimes) + ")");
         player.sendMessage("§6=== 物品获取统计 ===");
         
         // 按数量排序显示
         results.entrySet().stream()
            .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
            .limit(20) // 只显示前20个
            .forEach(entry -> {
               double percentage = entry.getValue() * 100.0 / finalTimes;
               player.sendMessage("§f" + entry.getKey() + " §7x§f" + entry.getValue() + " §8(" + String.format("%.2f%%", percentage) + ")");
            });
         
         if (results.size() > 20) {
            player.sendMessage("§7... 还有 §f" + (results.size() - 20) + " §7种物品");
         }
         return;
      }

      try {
         com.newpillar.game.items.SpecialItemManager.SpecialItemType type = 
            com.newpillar.game.items.SpecialItemManager.SpecialItemType.valueOf(itemName.toUpperCase());
         org.bukkit.inventory.ItemStack item = this.plugin.getSpecialItemManager().createSpecialItem(type);
         player.getInventory().addItem(item);
         player.sendMessage("§a已获得 §f" + type.getDisplayName() + " §a!");
         
         // 触发成就
         String achievementId = getAchievementIdForItem(type);
         if (achievementId != null && this.plugin.getAchievementSystem() != null) {
            this.plugin.getAchievementSystem().grantItemAchievement(player, achievementId);
         }
      } catch (IllegalArgumentException e) {
         player.sendMessage("§c未知的物品: §f" + itemName);
         player.sendMessage("§7使用 §f/np item list §7查看所有可用物品");
      }
   }
   
   /**
    * 获取物品对应的成就ID
    */
   private String getAchievementIdForItem(com.newpillar.game.items.SpecialItemManager.SpecialItemType type) {
      return switch (type) {
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
         case INVISIBLE_SAND -> "invisible_sand";
         case FEATHER -> "feather";
         case GODLY_PICKAXE -> "godly_pickaxe";
         case SPECIAL_BOW -> "special_bow";
         case SPECIAL_CROSSBOW -> "special_crossbow";
         case SPEAR -> "spear";
         case IRON_SWORD -> "iron_sword";
         default -> null;
      };
   }
   
   /**
    * 处理战利品表测试命令
    */
   private void handleLootTestCommand(Player player, String[] args) {
      if (args.length < 2) {
         player.sendMessage("§c用法: /np loottest <战利品表名称> [类别] [测试次数]");
         player.sendMessage("§7示例: /np loottest main");
         player.sendMessage("§7示例: /np loottest main 100");
         player.sendMessage("§7示例: /np loottest main weapon 1000");
         player.sendMessage("§7类别: block, weapon, food, tool, armor, special, all, none");
         player.sendMessage("§7使用 Tab 键查看所有可用的战利品表");
         return;
      }

      String tableName = args[1];
      String categoryFilter = "none"; // 默认不筛选
      int times = 100; // 默认测试100次

      // 解析参数
      if (args.length >= 3) {
         // 检查第三个参数是类别还是次数
         java.util.List<String> categories = Arrays.asList("block", "weapon", "food", "tool", "armor", "special", "all", "none");
         if (categories.contains(args[2].toLowerCase())) {
            categoryFilter = args[2].toLowerCase();
            if (args.length >= 4) {
               try {
                  times = Integer.parseInt(args[3]);
               } catch (NumberFormatException e) {
                  player.sendMessage("§c请输入有效的数字！");
                  return;
               }
            }
         } else {
            try {
               times = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
               player.sendMessage("§c无效的类别或数字！");
               player.sendMessage("§7类别: block, weapon, food, tool, armor, special, all, none");
               return;
            }
         }
      }

      if (times < 1 || times > 10000) {
         player.sendMessage("§c测试次数必须在 1-10000 之间！");
         return;
      }

      // 检查战利品表是否存在
      java.util.List<String> availableTables = this.plugin.getLootTableSystem().getAllLootTableNames();
      if (!availableTables.contains(tableName)) {
         player.sendMessage("§c未知的战利品表: §f" + tableName);
         player.sendMessage("§7可用的战利品表:");
         availableTables.forEach(name -> player.sendMessage("§7  - §f" + name));
         return;
      }

      player.sendMessage("§6=== 战利品表测试 ===");
      player.sendMessage("§7测试次数: §f" + times);
      player.sendMessage("§7正在测试中...");

      // 执行测试
      java.util.Map<String, Integer> results = this.plugin.getLootTableSystem().testLootTable(tableName, times);
      
      // 如果有类别筛选，过滤结果
      if (!categoryFilter.equals("none")) {
         results = filterResultsByCategory(results, categoryFilter);
      }

      // 计算总物品数
      int totalItems = results.values().stream().mapToInt(Integer::intValue).sum();
      int emptyCount = results.getOrDefault("(空)", 0);
      int actualItems = totalItems - emptyCount;

      player.sendMessage("");
      player.sendMessage("§a测试完成！结果统计：");
      player.sendMessage("§7总抽取次数: §f" + times);
      player.sendMessage("§7成功获取物品: §f" + actualItems + " §7(" + String.format("%.2f%%", actualItems * 100.0 / times) + ")");
      if (emptyCount > 0) {
         player.sendMessage("§7空奖次数: §f" + emptyCount + " §7(" + String.format("%.2f%%", emptyCount * 100.0 / times) + ")");
      }

      // 按数量排序显示
      player.sendMessage("");
      player.sendMessage("§6=== 物品掉落统计 ===");
      final int finalTimes = times;
      results.entrySet().stream()
         .filter(e -> !e.getKey().equals("(空)"))
         .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
         .limit(30) // 只显示前30个
         .forEach(entry -> {
            double percentage = entry.getValue() * 100.0 / finalTimes;
            String bar = generatePercentageBar(percentage);
            player.sendMessage("§f" + entry.getKey() + " §7x§f" + entry.getValue() + " §8(" + String.format("%.1f%%", percentage) + ") " + bar);
         });

      if (results.size() > 30) {
         player.sendMessage("§7... 还有 §f" + (results.size() - 30) + " §7种物品");
      }
   }

   /**
    * 生成百分比进度条
    */
   private String generatePercentageBar(double percentage) {
      int filled = (int) (percentage / 2); // 每2%一个方块
      filled = Math.min(filled, 10); // 最多10个方块
      int empty = 10 - filled;
      return "§a" + "█".repeat(filled) + "§7" + "░".repeat(empty);
   }

   /**
    * 按类别测试战利品表
    * @param category 物品类别
    * @param times 测试次数
    * @return 测试结果统计
    */
   private java.util.Map<String, Integer> testLootTableByCategory(String category, int times) {
      java.util.Map<String, Integer> results = new java.util.HashMap<>();
      
      for (int i = 0; i < times; i++) {
         org.bukkit.inventory.ItemStack item = this.plugin.getLootTableSystem().generateLoot();
         if (item != null && item.getType() != org.bukkit.Material.AIR) {
            String itemName = item.hasItemMeta() && item.getItemMeta().hasDisplayName() 
               ? item.getItemMeta().getDisplayName() 
               : item.getType().name();
            
            // 检查物品类别
            boolean matchesCategory = false;
            org.bukkit.Material type = item.getType();
            
            switch (category) {
               case "block":
                  matchesCategory = type.isBlock();
                  break;
               case "weapon":
                  matchesCategory = type.name().endsWith("SWORD") || type.name().endsWith("AXE") 
                     || type.name().endsWith("MACE") || type.name().endsWith("TRIDENT")
                     || type == org.bukkit.Material.BOW || type == org.bukkit.Material.CROSSBOW;
                  break;
               case "food":
                  matchesCategory = type.isEdible();
                  break;
               case "tool":
                  matchesCategory = type.name().endsWith("PICKAXE") || type.name().endsWith("SHOVEL")
                     || type.name().endsWith("HOE") || type.name().endsWith("FLINT_AND_STEEL")
                     || type.name().endsWith("SHEARS") || type.name().endsWith("FISHING_ROD");
                  break;
               case "armor":
                  matchesCategory = type.name().endsWith("HELMET") || type.name().endsWith("CHESTPLATE")
                     || type.name().endsWith("LEGGINGS") || type.name().endsWith("BOOTS")
                     || type.name().endsWith("HORSE_ARMOR");
                  break;
               case "special":
                  // 特殊物品检查
                  if (item.hasItemMeta()) {
                     org.bukkit.persistence.PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
                     org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(this.plugin, "item_id");
                     matchesCategory = container.has(key, org.bukkit.persistence.PersistentDataType.STRING);
                  }
                  break;
               case "all":
                  matchesCategory = true;
                  break;
            }
            
            if (matchesCategory) {
               results.merge(itemName, 1, Integer::sum);
            } else {
               results.merge("(不符合类别)", 1, Integer::sum);
            }
         } else {
            results.merge("(空)", 1, Integer::sum);
         }
      }
      
      return results;
   }

   /**
    * 根据类别筛选战利品表测试结果
    * @param results 原始结果
    * @param category 类别筛选
    * @return 筛选后的结果
    */
   private java.util.Map<String, Integer> filterResultsByCategory(java.util.Map<String, Integer> results, String category) {
      java.util.Map<String, Integer> filtered = new java.util.HashMap<>();
      
      for (java.util.Map.Entry<String, Integer> entry : results.entrySet()) {
         String itemName = entry.getKey();
         int count = entry.getValue();
         
         // 跳过空奖
         if (itemName.equals("(空)")) {
            filtered.put(itemName, count);
            continue;
         }
         
         // 根据类别筛选
         boolean matches = false;
         String upperName = itemName.toUpperCase();
         
         switch (category) {
            case "block":
               // 检查是否是方块（通过Material判断）
               try {
                  org.bukkit.Material mat = org.bukkit.Material.valueOf(upperName);
                  matches = mat.isBlock();
               } catch (IllegalArgumentException e) {
                  // 可能是自定义物品名称，尝试从显示名判断
                  matches = false;
               }
               break;
            case "weapon":
               matches = upperName.contains("SWORD") || upperName.contains("AXE") 
                  || upperName.contains("MACE") || upperName.contains("TRIDENT")
                  || upperName.contains("BOW") || upperName.contains("CROSSBOW");
               break;
            case "food":
               try {
                  org.bukkit.Material mat = org.bukkit.Material.valueOf(upperName);
                  matches = mat.isEdible();
               } catch (IllegalArgumentException e) {
                  matches = false;
               }
               break;
            case "tool":
               matches = upperName.contains("PICKAXE") || upperName.contains("SHOVEL")
                  || upperName.contains("HOE") || upperName.contains("FLINT_AND_STEEL")
                  || upperName.contains("SHEARS") || upperName.contains("FISHING_ROD");
               break;
            case "armor":
               matches = upperName.contains("HELMET") || upperName.contains("CHESTPLATE")
                  || upperName.contains("LEGGINGS") || upperName.contains("BOOTS")
                  || upperName.contains("HORSE_ARMOR");
               break;
            case "special":
               // 特殊物品：通过匹配 SpecialItemType 的显示名称来判断
               matches = isSpecialItemByDisplayName(itemName);
               break;
            case "all":
               matches = true;
               break;
         }
         
         if (matches) {
            filtered.put(itemName, count);
         }
      }
      
      return filtered;
   }

   /**
    * 根据显示名称判断是否是特殊物品
    * @param displayName 物品显示名称
    * @return 是否是特殊物品
    */
   private boolean isSpecialItemByDisplayName(String displayName) {
      // 去除颜色代码后进行匹配
      String cleanName = displayName.replaceAll("§[0-9a-fk-orA-FK-OR]", "").trim();
      
      // 遍历所有 SpecialItemType，检查显示名称是否匹配
      for (com.newpillar.game.items.SpecialItemManager.SpecialItemType type : 
           com.newpillar.game.items.SpecialItemManager.SpecialItemType.values()) {
         if (type.getDisplayName().equals(cleanName)) {
            return true;
         }
      }
      
      return false;
   }

   /**
    * 处理规则投票命令
    */
   private void handleRuleCommand(Player player, String[] args, GameManager gameManager) {
      if (args.length < 2) {
         // 显示当前可投票的规则列表，并直接打开GUI
         java.util.List<com.newpillar.game.enums.RuleType> votingRules = gameManager.getVotingRules();
         if (votingRules == null || votingRules.isEmpty()) {
            player.sendMessage("§c当前没有进行中的规则投票！");
            return;
         }
         
         // 直接打开投票GUI
         plugin.getVoteGUI().openVoteGUI(player);
         return;
      }
      
      String subCommand = args[1].toLowerCase();
      switch (subCommand) {
         case "vote":
            // 直接打开投票GUI，不再处理数字参数
            plugin.getVoteGUI().openVoteGUI(player);
            break;
         default:
            player.sendMessage("§c未知命令。用法: /np rule [vote]");
      }
   }
   
   /**
    * 处理最少玩家数命令
    */
   private void handleMinPlayersCommand(Player player, String[] args, GameManager gameManager) {
      if (args.length < 2) {
         // 显示当前状态
         int minPlayers = gameManager.getAutoStartMinPlayers();
         player.sendMessage("§6=== 自动开始最少玩家数 ===");
         player.sendMessage("§f当前设置: §a" + minPlayers + " §f人");
         player.sendMessage("§7使用 §f/np minplayers set <数量> §7修改设置");
         player.sendMessage("§7使用 §f/np minplayers get §7查看当前设置");
         return;
      }

      String subCommand = args[1].toLowerCase();
      switch (subCommand) {
         case "set":
            if (args.length < 3) {
               player.sendMessage("§c用法: /np minplayers set <数量>");
               player.sendMessage("§7数量范围: 1-100");
               return;
            }

            try {
               int minPlayers = Integer.parseInt(args[2]);
               if (minPlayers < 1 || minPlayers > 100) {
                  player.sendMessage("§c数量必须在 1-100 之间！");
                  return;
               }
               gameManager.setAutoStartMinPlayers(minPlayers);
               player.sendMessage("§a自动开始所需的最少玩家数已设置为: §f" + minPlayers + " §a人");
               player.sendMessage("§7配置已保存到配置文件");
            } catch (NumberFormatException e) {
               player.sendMessage("§c请输入有效的数字！");
            }
            break;
         case "get":
         case "status":
            int currentMinPlayers = gameManager.getAutoStartMinPlayers();
            player.sendMessage("§6=== 自动开始最少玩家数 ===");
            player.sendMessage("§f当前设置: §a" + currentMinPlayers + " §f人");
            player.sendMessage("§7当准备玩家数达到此数值时，游戏将自动开始倒计时");
            break;
         default:
            player.sendMessage("§c未知命令。用法: /np minplayers <set|get> [数量]");
      }
   }

   /**
    * 处理获取所有特殊物品命令
    */
   private void handleAllItemsCommand(Player player, String[] args) {
      String category = args.length >= 2 ? args[1].toLowerCase() : "all";
      
      java.util.List<com.newpillar.game.items.SpecialItemManager.SpecialItemType> itemsToGive = new java.util.ArrayList<>();
      
      for (com.newpillar.game.items.SpecialItemManager.SpecialItemType type : com.newpillar.game.items.SpecialItemManager.SpecialItemType.values()) {
         String itemCategory = type.getCategory();
         boolean shouldAdd = false;
         
         switch (category) {
            case "all":
               shouldAdd = true;
               break;
            case "attack":
            case "weapon":
               shouldAdd = itemCategory.equals("攻击类");
               break;
            case "assist":
            case "armor":
               shouldAdd = itemCategory.equals("辅助类");
               break;
            case "special":
               // special 给所有特殊物品（攻击类+辅助类+特殊类）
               shouldAdd = true;
               break;
            case "special_class":
               // special_class 只给"特殊类"分类的物品
               shouldAdd = itemCategory.equals("特殊类");
               break;
         }
         
         if (shouldAdd) {
            itemsToGive.add(type);
         }
      }
      
      if (itemsToGive.isEmpty()) {
         player.sendMessage("§c没有找到该类别的物品！");
         player.sendMessage("§7可用类别: all, attack/weapon, assist/armor, special, special_class");
         return;
      }
      
      // 给予玩家所有物品
      int count = 0;
      for (com.newpillar.game.items.SpecialItemManager.SpecialItemType type : itemsToGive) {
         org.bukkit.inventory.ItemStack item = this.plugin.getSpecialItemManager().createSpecialItem(type);
         player.getInventory().addItem(item);
         count++;
      }
      
      player.sendMessage("§a已获得 §f" + count + " §a个特殊物品！");
      if (!category.equals("all")) {
         player.sendMessage("§7类别: §f" + category);
      }
   }

   /**
    * 处理自动开始命令
    */
   private void handleAutoStartCommand(Player player, String[] args, GameManager gameManager) {
      if (args.length < 2) {
         // 显示当前状态
         boolean enabled = gameManager.isAutoStartEnabled();
         player.sendMessage("§6=== 自动开始功能 ===");
         player.sendMessage("§f当前状态: " + (enabled ? "§a已启用" : "§c已禁用"));
         player.sendMessage("§7使用 §f/np autostart <on|off|toggle|status> §7控制功能");
         return;
      }
      
      String subCommand = args[1].toLowerCase();
      switch (subCommand) {
         case "on":
         case "enable":
            gameManager.setAutoStartEnabled(true);
            player.sendMessage("§a自动开始功能已 §l启用§a！");
            break;
         case "off":
         case "disable":
            gameManager.setAutoStartEnabled(false);
            player.sendMessage("§c自动开始功能已 §l禁用§c！");
            break;
         case "toggle":
            boolean newState = gameManager.toggleAutoStart();
            player.sendMessage("§a自动开始功能已切换为: " + (newState ? "§a§l启用" : "§c§l禁用"));
            break;
         case "status":
            boolean status = gameManager.isAutoStartEnabled();
            player.sendMessage("§6=== 自动开始功能状态 ===");
            player.sendMessage("§f当前状态: " + (status ? "§a§l已启用" : "§c§l已禁用"));
            break;
         default:
            player.sendMessage("§c未知命令。用法: /np autostart <on|off|toggle|status>");
      }
   }

   /**
    * 处理强制触发平台崩塌命令
    */
   private void handleCollapseCommand(Player player, GameManager gameManager) {
      if (gameManager.getGameStatus() != com.newpillar.game.enums.GameStatus.PLAYING) {
         player.sendMessage("§c游戏未在进行中，无法触发平台崩塌！");
         return;
      }

      org.bukkit.World world = gameManager.getGameWorld();
      if (world == null) {
         player.sendMessage("§c游戏世界未加载！");
         return;
      }

      player.sendMessage("§a正在强制触发平台崩塌...");
      gameManager.getCollapseManager().startPlatformCollapse(world);
   }
}
