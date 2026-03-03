package com.newpillar.game;

import com.newpillar.NewPillar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 死亡播报管理器
 * 接管所有死亡播报，显示详细的死亡原因，支持队伍颜色和中文死亡信息
 */
public class DeathMessageManager {
    private final NewPillar plugin;
    
    // 连杀追踪: 玩家UUID -> 连杀数
    private final Map<UUID, Integer> killStreaks = new ConcurrentHashMap<>();
    // 连杀时间戳: 玩家UUID -> 上次击杀时间
    private final Map<UUID, Long> lastKillTime = new ConcurrentHashMap<>();
    // 连杀阈值（秒）
    private static final long KILL_STREAK_WINDOW_MS = 45000; // 45秒内
    // 多连杀播报阈值
    private static final int MULTI_KILL_THRESHOLD = 3;
    
    // 英文死亡原因到中文的映射
    private final Map<String, String> deathMessageTranslations = new HashMap<>();
    
    public DeathMessageManager(NewPillar plugin) {
        this.plugin = plugin;
        initializeTranslations();
    }
    
    /**
     * 初始化死亡消息中文翻译
     */
    private void initializeTranslations() {
        // 玩家击杀
        deathMessageTranslations.put("was slain by", "被");
        deathMessageTranslations.put("was shot by", "被");
        deathMessageTranslations.put("was killed by", "被");
        deathMessageTranslations.put("was blown up by", "被");
        deathMessageTranslations.put("was fireballed by", "被");
        deathMessageTranslations.put("was pummeled by", "被");
        deathMessageTranslations.put("was killed by magic", "被魔法杀死了");
        
        // 环境伤害
        deathMessageTranslations.put("drowned", "淹死了");
        deathMessageTranslations.put("experienced kinetic energy", "感受到了动能");
        deathMessageTranslations.put("blew up", "爆炸了");
        deathMessageTranslations.put("hit the ground too hard", "落地过猛");
        deathMessageTranslations.put("fell from a high place", "从高处摔了下来");
        deathMessageTranslations.put("was doomed to fall", "注定要摔死");
        deathMessageTranslations.put("fell off a ladder", "从梯子上摔了下来");
        deathMessageTranslations.put("fell off some vines", "从藤蔓上摔了下来");
        deathMessageTranslations.put("fell out of the water", "从水中摔了出来");
        deathMessageTranslations.put("was impaled on a stalagmite", "被石笋刺穿了");
        deathMessageTranslations.put("was squashed by a falling anvil", "被坠落的铁砧压扁了");
        deathMessageTranslations.put("was squashed by a falling block", "被坠落的方块压扁了");
        deathMessageTranslations.put("went up in flames", "浴火焚身");
        deathMessageTranslations.put("burned to death", "被烧死了");
        deathMessageTranslations.put("tried to swim in lava", "试图在熔岩里游泳");
        deathMessageTranslations.put("was struck by lightning", "被闪电击中了");
        deathMessageTranslations.put("discovered the floor was lava", "发现地板是熔岩");
        deathMessageTranslations.put("walked into danger zone due to", "走进了危险区域，因为");
        deathMessageTranslations.put("was killed by", "被");
        deathMessageTranslations.put("was fireballed by", "被");
        deathMessageTranslations.put("was pummeled by", "被");
        deathMessageTranslations.put("was blown up by", "被");
        deathMessageTranslations.put("was shot by", "被");
        deathMessageTranslations.put("was slain by", "被");
        
        // 生物击杀
        deathMessageTranslations.put("was stung to death by", "被");
        deathMessageTranslations.put("was killed by", "被");
        deathMessageTranslations.put("was impaled by", "被");
        deathMessageTranslations.put("was poked to death by", "被");
        deathMessageTranslations.put("died", "死了");
        deathMessageTranslations.put("was roasted in dragon breath", "被龙息烤熟了");
        deathMessageTranslations.put("suffocated in a wall", "在墙里窒息了");
        deathMessageTranslations.put("was squished too much", "被挤扁了");
        deathMessageTranslations.put("was killed while trying to hurt", "在试图伤害");
        deathMessageTranslations.put("died because of", "因为");
        deathMessageTranslations.put("was pricked to death", "被刺死了");
        deathMessageTranslations.put("drowned whilst trying to escape", "在试图逃离");
        deathMessageTranslations.put("starved to death", "饿死了");
        deathMessageTranslations.put("withered away", "凋零了");
        deathMessageTranslations.put("was pummeled by", "被");
        
        // 其他
        deathMessageTranslations.put("fell out of the world", "掉出了这个世界");
        deathMessageTranslations.put("was knocked into the void by", "被");
        deathMessageTranslations.put("was killed by even more magic", "被更多的魔法杀死了");
        deathMessageTranslations.put("was obliterated by a sonically-charged shriek", "被声波尖啸 obliterated");
        deathMessageTranslations.put("was skewered by a falling stalactite", "被坠落的钟乳石刺穿了");
    }
    
    /**
     * 处理玩家死亡事件
     * @param event 死亡事件
     * @param victim 死亡玩家
     * @param killer 击杀者（可能为null）
     */
    public void handlePlayerDeath(PlayerDeathEvent event, Player victim, Player killer) {
        // 获取原版死亡消息
        Component originalMessage = event.deathMessage();
        
        // 构建带颜色和中文的死亡消息
        Component coloredMessage = buildColoredDeathMessage(originalMessage, victim, killer);
        
        // 将 Component 转换为 Legacy 字符串并设置
        if (coloredMessage != null) {
            String legacyMessage = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(coloredMessage);
            event.setDeathMessage(legacyMessage);
        }
        
        // 处理击杀者逻辑
        if (killer != null && !killer.equals(victim)) {
            // 发送ActionBar提示
            sendKillActionBar(killer);
            
            // 更新连杀数
            updateKillStreak(killer);
        }
    }
    
    /**
     * 构建带颜色的死亡播报消息
     * 保留原版风格，只将玩家名字替换为带颜色的版本
     */
    private Component buildColoredDeathMessage(Component originalMessage, Player victim, Player killer) {
        if (originalMessage == null) {
            return null;
        }
        
        // 将原版消息转换为字符串
        String messageStr = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(originalMessage);
        
        // 翻译死亡消息为中文
        String translatedMessage = translateDeathMessage(messageStr, victim.getName(), killer != null ? killer.getName() : null);
        
        TextColor victimColor = getPlayerColor(victim);
        Component victimName = Component.text(victim.getName()).color(victimColor);
        
        if (killer != null && !killer.equals(victim)) {
            // 被其他玩家击杀，替换击杀者名字
            TextColor killerColor = getPlayerColor(killer);
            Component killerName = Component.text(killer.getName()).color(killerColor);
            
            // 构建新消息：将消息中的玩家名字替换为带颜色的组件
            return buildFinalMessage(translatedMessage, victim.getName(), victimName, killer.getName(), killerName);
        } else {
            // 非玩家击杀，只替换受害者名字
            return buildFinalMessage(translatedMessage, victim.getName(), victimName, null, null);
        }
    }
    
    /**
     * 翻译死亡消息为中文
     */
    private String translateDeathMessage(String originalMessage, String victimName, String killerName) {
        String result = originalMessage;
        
        // 尝试匹配并替换死亡原因
        for (Map.Entry<String, String> entry : deathMessageTranslations.entrySet()) {
            String englishPattern = entry.getKey();
            String chineseReplacement = entry.getValue();
            
            // 替换英文死亡原因为中文
            if (result.contains(englishPattern)) {
                result = result.replace(englishPattern, chineseReplacement);
                
                // 处理 "using" 和武器名
                result = result.replace("using", "使用");
                
                // 添加 "杀死了" 结尾（如果是被击杀）
                if (killerName != null && (englishPattern.contains("by") || englishPattern.contains("while"))) {
                    if (!result.endsWith("杀死了") && !result.endsWith("杀死了")) {
                        result += " 杀死了";
                    }
                }
                break; // 只替换第一个匹配的
            }
        }
        
        return result;
    }
    
    /**
     * 构建最终的消息组件
     */
    private Component buildFinalMessage(String message, String victimName, Component victimNameColored, 
                                         String killerName, Component killerNameColored) {
        // 分割消息，替换玩家名字
        Component result = Component.empty();
        
        if (killerName != null && killerNameColored != null) {
            // 有击杀者的情况
            // 将消息分成三部分：受害者名字前、受害者名字、受害者名字后到击杀者名字前、击杀者名字、击杀者名字后
            int victimIndex = message.indexOf(victimName);
            int killerIndex = message.indexOf(killerName);
            
            if (victimIndex != -1 && killerIndex != -1) {
                // 确保顺序正确
                if (victimIndex < killerIndex) {
                    // 受害者在前
                    String beforeVictim = message.substring(0, victimIndex);
                    String between = message.substring(victimIndex + victimName.length(), killerIndex);
                    String afterKiller = message.substring(killerIndex + killerName.length());
                    
                    result = result.append(Component.text(beforeVictim));
                    result = result.append(victimNameColored);
                    result = result.append(Component.text(between));
                    result = result.append(killerNameColored);
                    result = result.append(Component.text(afterKiller));
                } else {
                    // 击杀者在前
                    String beforeKiller = message.substring(0, killerIndex);
                    String between = message.substring(killerIndex + killerName.length(), victimIndex);
                    String afterVictim = message.substring(victimIndex + victimName.length());
                    
                    result = result.append(Component.text(beforeKiller));
                    result = result.append(killerNameColored);
                    result = result.append(Component.text(between));
                    result = result.append(victimNameColored);
                    result = result.append(Component.text(afterVictim));
                }
            } else {
                // 找不到名字，直接返回文本消息，但替换名字
                result = Component.text(message.replace(victimName, "").replace(killerName, ""));
                // 在适当位置插入带颜色的名字
                // 简化处理：直接返回文本消息
                result = Component.text(message);
            }
        } else {
            // 无击杀者的情况
            int victimIndex = message.indexOf(victimName);
            if (victimIndex != -1) {
                String before = message.substring(0, victimIndex);
                String after = message.substring(victimIndex + victimName.length());
                
                result = result.append(Component.text(before));
                result = result.append(victimNameColored);
                result = result.append(Component.text(after));
            } else {
                result = Component.text(message);
            }
        }
        
        return result;
    }
    
    /**
     * 获取玩家颜色（默认金色）
     */
    private TextColor getPlayerColor(Player player) {
        // 队伍功能已禁用，所有玩家使用默认金色
        return TextColor.color(255, 215, 0); // 金色
    }
    
    /**
     * 发送击杀ActionBar提示
     */
    private void sendKillActionBar(Player killer) {
        int currentKills = plugin.getStatisticsSystem().getPlayerStats(killer.getUniqueId()).kills;
        
        Component message = Component.text("击杀 +1 (本局: ")
            .color(TextColor.color(255, 255, 255))
            .append(Component.text(currentKills).color(TextColor.color(255, 215, 0)))
            .append(Component.text(")").color(TextColor.color(255, 255, 255)));
        
        killer.sendActionBar(message);
    }
    
    /**
     * 更新连杀数并检查是否需要播报多连杀
     */
    private void updateKillStreak(Player killer) {
        UUID killerId = killer.getUniqueId();
        long currentTime = System.currentTimeMillis();
        
        // 检查是否在连杀时间窗口内
        Long lastKill = lastKillTime.get(killerId);
        if (lastKill != null && (currentTime - lastKill) > KILL_STREAK_WINDOW_MS) {
            // 超过时间窗口，重置连杀
            killStreaks.put(killerId, 1);
        } else {
            // 在连杀窗口内，增加连杀数
            killStreaks.merge(killerId, 1, Integer::sum);
        }
        
        lastKillTime.put(killerId, currentTime);
        
        // 检查是否达到多连杀阈值
        int streak = killStreaks.getOrDefault(killerId, 1);
        if (streak >= MULTI_KILL_THRESHOLD) {
            broadcastMultiKill(killer, streak);
        }
    }
    
    /**
     * 播报多连杀
     */
    private void broadcastMultiKill(Player killer, int streak) {
        TextColor killerColor = getPlayerColor(killer);
        Component killerName = Component.text(killer.getName()).color(killerColor);
        
        String streakText = switch (streak) {
            case 3 -> "§6§l三连杀！";
            case 4 -> "§c§l四连杀！";
            case 5 -> "§4§l五连杀！";
            case 6 -> "§5§l六连杀！";
            case 7 -> "§d§l七连杀！";
            case 8 -> "§b§l八连杀！";
            case 9 -> "§3§l九连杀！";
            default -> streak >= 10 ? "§e§l§o" + streak + " 连杀！超神！" : "§6§l" + streak + " 连杀！";
        };
        
        Component message = Component.text("\n§6§l═══════════════════════════\n")
            .append(Component.text("  "))
            .append(killerName)
            .append(Component.text(" " + streakText + "\n"))
            .append(Component.text("§6§l═══════════════════════════\n"));
        
        Bukkit.broadcast(message);
        
        // 播放音效
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }
    }
    
    /**
     * 重置连杀数据（游戏结束时调用）
     */
    public void reset() {
        killStreaks.clear();
        lastKillTime.clear();
    }
}
