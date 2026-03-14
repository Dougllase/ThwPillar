package com.newpillar.commands;

import com.newpillar.NewPillar;
import com.newpillar.game.SchrodingerCatManager;
import com.newpillar.game.enums.EventType;
import com.newpillar.game.items.SpecialItemManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 薛定谔的猫指令
 * /schrodinger - 主指令
 *   toggle - 启用/禁用机制
 *   insight - 启用/禁用洞察模式
 *   item - 物品相关
 *     list - 查看24个物品预知列表
 *     set <index> <itemType> [specialItemId] - 设置指定位置物品
 *     history - 查看物品历史（32个）
 *   event - 事件相关
 *     list - 查看8个事件预知列表
 *     set <index> <eventType> - 设置指定位置事件
 *     history - 查看事件历史（8个）
 */
public class SchrodingerCatCommand implements TabExecutor {
    private final NewPillar plugin;
    private final SchrodingerCatManager catManager;
    
    public SchrodingerCatCommand(NewPillar plugin) {
        this.plugin = plugin;
        this.catManager = plugin.getSchrodingerCatManager();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "toggle" -> handleToggle(sender);
            case "insight" -> handleInsight(sender);
            case "item" -> handleItem(sender, args);
            case "event" -> handleEvent(sender, args);
            case "status" -> handleStatus(sender);
            case "clearhistory" -> handleClearHistory(sender);
            default -> sendHelp(sender);
        }
        
        return true;
    }
    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6§l===== 薛定谔的猫指令 =====");
        sender.sendMessage("§e/schrodinger toggle §7- 启用/禁用机制");
        sender.sendMessage("§e/schrodinger insight §7- 启用/禁用洞察模式");
        sender.sendMessage("§e/schrodinger status §7- 查看当前状态");
        sender.sendMessage("§e/schrodinger item list §7- 查看24个物品预知");
        sender.sendMessage("§e/schrodinger item set <序号> <类型> [ID] §7- 设置物品");
        sender.sendMessage("§e/schrodinger item history §7- 查看物品历史(32个)");
        sender.sendMessage("§e/schrodinger event list §7- 查看8个事件预知");
        sender.sendMessage("§e/schrodinger event set <序号> <事件> §7- 设置事件");
        sender.sendMessage("§e/schrodinger event history §7- 查看事件历史(8个)");
        sender.sendMessage("§e/schrodinger clearhistory §7- 清空历史记录");
        sender.sendMessage("§7物品类型: §fnormal, special");
        sender.sendMessage("§7事件类型: §fnone, small_cute, big, my_partner, punch, inv_exchange, void_mercy");
    }
    
    private void handleToggle(CommandSender sender) {
        boolean newState = !catManager.isEnabled();
        catManager.setEnabled(newState);
        sender.sendMessage(newState ? "§a[薛定谔的猫] 机制已启用" : "§c[薛定谔的猫] 机制已禁用");
    }
    
    private void handleInsight(CommandSender sender) {
        boolean newState = !catManager.isInsightModeEnabled();
        catManager.setInsightMode(newState);
        sender.sendMessage(newState ? "§a[薛定谔的猫] 洞察模式已启用" : "§c[薛定谔的猫] 洞察模式已禁用");
    }
    
    private void handleStatus(CommandSender sender) {
        sender.sendMessage("§6§l===== 薛定谔的猫状态 =====");
        sender.sendMessage("§e机制状态: " + (catManager.isEnabled() ? "§a已启用" : "§c已禁用"));
        sender.sendMessage("§e洞察模式: " + (catManager.isInsightModeEnabled() ? "§a已启用" : "§c已禁用"));
        if (catManager.isEnabled()) {
            sender.sendMessage("§e物品预知: §f24个");
            sender.sendMessage("§e事件预知: §f8个");
        }
    }
    
    private void handleClearHistory(CommandSender sender) {
        catManager.clearHistory();
        sender.sendMessage("§a[薛定谔的猫] 历史记录已清空");
    }
    
    private void handleItem(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c用法: /schrodinger item <list|set|history>");
            return;
        }
        
        String itemSubCommand = args[1].toLowerCase();
        
        switch (itemSubCommand) {
            case "list" -> {
                List<ItemStack> predictions = catManager.getItemPredictions();
                sender.sendMessage("§6§l===== 物品预知列表 (24个) =====");
                for (int i = 0; i < predictions.size(); i++) {
                    ItemStack item = predictions.get(i);
                    String itemName = item.hasItemMeta() && item.getItemMeta().hasDisplayName() 
                        ? item.getItemMeta().getDisplayName() 
                        : item.getType().name();
                    sender.sendMessage("§e[" + i + "] §f" + itemName + " §7x" + item.getAmount());
                }
            }
            case "set" -> {
                if (args.length < 4) {
                    sender.sendMessage("§c用法: /schrodinger item set <序号(0-23)> <类型> [特殊物品ID]");
                    sender.sendMessage("§c类型: normal, special");
                    return;
                }
                
                int index;
                try {
                    index = Integer.parseInt(args[2]);
                    if (index < 0 || index >= 24) {
                        sender.sendMessage("§c序号必须在 0-23 之间");
                        return;
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage("§c序号必须是数字");
                    return;
                }
                
                String itemType = args[3].toLowerCase();
                
                switch (itemType) {
                    case "normal" -> {
                        ItemStack item = plugin.getLootTableSystem().generateLoot();
                        if (catManager.setItemAt(index, item)) {
                            sender.sendMessage("§a已设置位置 " + index + " 为普通物品: " + item.getType().name());
                        } else {
                            sender.sendMessage("§c设置失败");
                        }
                    }
                    case "special" -> {
                        if (args.length < 5) {
                            sender.sendMessage("§c使用 special 类型时需要指定特殊物品ID");
                            sender.sendMessage("§c可用ID: KNOCKBACK_STICK, SPEAR, WITCH_APPLE, BRUCE, GODLY_PICKAXE, ...");
                            return;
                        }
                        String specialId = args[4].toUpperCase();
                        if (catManager.setSpecialItemAt(index, specialId)) {
                            sender.sendMessage("§a已设置位置 " + index + " 为特殊物品: " + specialId);
                        } else {
                            sender.sendMessage("§c设置失败，无效的特殊物品ID: " + specialId);
                        }
                    }
                    default -> sender.sendMessage("§c无效的类型，可用: normal, special");
                }
            }
            case "history" -> {
                List<ItemStack> history = catManager.getItemHistory();
                if (history.isEmpty()) {
                    sender.sendMessage("§7暂无物品历史记录");
                    return;
                }
                sender.sendMessage("§6§l===== 物品历史记录 (最多32个) =====");
                for (int i = 0; i < history.size(); i++) {
                    ItemStack item = history.get(i);
                    String itemName = item.hasItemMeta() && item.getItemMeta().hasDisplayName() 
                        ? item.getItemMeta().getDisplayName() 
                        : item.getType().name();
                    sender.sendMessage("§e[" + i + "] §f" + itemName + " §7x" + item.getAmount());
                }
            }
            default -> sender.sendMessage("§c未知子指令，可用: list, set, history");
        }
    }
    
    private void handleEvent(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c用法: /schrodinger event <list|set|history>");
            return;
        }
        
        String eventSubCommand = args[1].toLowerCase();
        
        switch (eventSubCommand) {
            case "list" -> {
                List<EventType> predictions = catManager.getEventPredictions();
                sender.sendMessage("§6§l===== 事件预知列表 (8个) =====");
                for (int i = 0; i < predictions.size(); i++) {
                    EventType event = predictions.get(i);
                    sender.sendMessage("§e[" + i + "] §f" + event.getName() + " §7- " + event.getDescription());
                }
            }
            case "set" -> {
                if (args.length < 4) {
                    sender.sendMessage("§c用法: /schrodinger event set <序号(0-7)> <事件类型>");
                    sender.sendMessage("§c事件类型: none, small_cute, big, my_partner, punch, inv_exchange, void_mercy");
                    return;
                }
                
                int index;
                try {
                    index = Integer.parseInt(args[2]);
                    if (index < 0 || index >= 8) {
                        sender.sendMessage("§c序号必须在 0-7 之间");
                        return;
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage("§c序号必须是数字");
                    return;
                }
                
                String eventName = args[3].toUpperCase();
                EventType eventType;
                try {
                    eventType = EventType.valueOf(eventName);
                } catch (IllegalArgumentException e) {
                    sender.sendMessage("§c无效的事件类型: " + eventName);
                    return;
                }
                
                if (catManager.setEventAt(index, eventType)) {
                    sender.sendMessage("§a已设置位置 " + index + " 为事件: " + eventType.getName());
                } else {
                    sender.sendMessage("§c设置失败");
                }
            }
            case "history" -> {
                List<EventType> history = catManager.getEventHistory();
                if (history.isEmpty()) {
                    sender.sendMessage("§7暂无事件历史记录");
                    return;
                }
                sender.sendMessage("§6§l===== 事件历史记录 (最多8个) =====");
                for (int i = 0; i < history.size(); i++) {
                    EventType event = history.get(i);
                    sender.sendMessage("§e[" + i + "] §f" + event.getName() + " §7- " + event.getDescription());
                }
            }
            default -> sender.sendMessage("§c未知子指令，可用: list, set, history");
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.addAll(Arrays.asList("toggle", "insight", "status", "item", "event", "clearhistory"));
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "item" -> completions.addAll(Arrays.asList("list", "set", "history"));
                case "event" -> completions.addAll(Arrays.asList("list", "set", "history"));
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("item") && args[1].equalsIgnoreCase("set")) {
                // 序号 0-23
                for (int i = 0; i < 24; i++) {
                    completions.add(String.valueOf(i));
                }
            } else if (args[0].equalsIgnoreCase("event") && args[1].equalsIgnoreCase("set")) {
                // 序号 0-7
                for (int i = 0; i < 8; i++) {
                    completions.add(String.valueOf(i));
                }
            }
        } else if (args.length == 4) {
            if (args[0].equalsIgnoreCase("item") && args[1].equalsIgnoreCase("set")) {
                completions.addAll(Arrays.asList("normal", "special"));
            } else if (args[0].equalsIgnoreCase("event") && args[1].equalsIgnoreCase("set")) {
                // 事件类型
                for (EventType type : EventType.values()) {
                    completions.add(type.name().toLowerCase());
                }
            }
        } else if (args.length == 5) {
            if (args[0].equalsIgnoreCase("item") && args[1].equalsIgnoreCase("set") 
                && args[3].equalsIgnoreCase("special")) {
                // 特殊物品ID（大写）
                for (SpecialItemManager.SpecialItemType type : SpecialItemManager.SpecialItemType.values()) {
                    completions.add(type.name().toUpperCase());
                }
            }
        }
        
        return completions.stream()
            .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
            .collect(Collectors.toList());
    }
}
