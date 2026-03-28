package com.newpillar.integration;

import com.newpillar.NewPillar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MessageService 跨服消息集成
 * 用于跨服招人广播和奖励消息发送
 * 直接集成在主插件中，不依赖 ThwReward
 */
public class MessageServiceIntegration {
    
    private final NewPillar plugin;
    private Plugin messageServicePlugin;
    private Object apiInstance;
    private boolean enabled = false;
    
    // 服务器名称配置
    private String currentServerName;
    private List<String> targetServers;
    
    public MessageServiceIntegration(NewPillar plugin) {
        this.plugin = plugin;
        loadConfig();
    }
    
    /**
     * 加载配置
     */
    private void loadConfig() {
        this.currentServerName = plugin.getConfig().getString("cross-server.server-name", "minigame");
        this.targetServers = plugin.getConfig().getStringList("cross-server.target-servers");
        
        // 默认目标服务器（生存服）
        if (targetServers.isEmpty()) {
            targetServers = Arrays.asList("survival", "lobby");
        }
    }
    
    /**
     * 初始化 MessageService 集成
     */
    public void initialize() {
        try {
            messageServicePlugin = Bukkit.getPluginManager().getPlugin("MessageService");
            
            if (messageServicePlugin == null) {
                plugin.getLogger().info("未检测到 MessageService 插件，跨服消息功能已禁用");
                return;
            }
            
            if (!messageServicePlugin.isEnabled()) {
                plugin.getLogger().warning("MessageService 插件未启用");
                return;
            }
            
            // 获取API实例
            Class<?> apiClass = Class.forName("com.example.messageservice.api.MessageServiceApi");
            Method getInstance = apiClass.getMethod("getInstance");
            apiInstance = getInstance.invoke(null);
            
            if (apiInstance == null) {
                plugin.getLogger().warning("无法获取 MessageService API 实例");
                return;
            }
            
            enabled = true;
            plugin.getLogger().info("MessageService 集成已启用，跨服消息功能可用");
            plugin.getLogger().info("  - 当前服务器: " + currentServerName);
            plugin.getLogger().info("  - 目标服务器: " + targetServers);
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "初始化 MessageService 集成失败: " + e.getMessage(), e);
            enabled = false;
        }
    }
    
    /**
     * 是否已启用
     */
    public boolean isEnabled() {
        if (!enabled || apiInstance == null) {
            tryReinitialize();
        }
        return enabled && apiInstance != null;
    }
    
    /**
     * 尝试重新初始化
     */
    private void tryReinitialize() {
        try {
            // 如果插件未加载或未启用，尝试重新初始化
            if (messageServicePlugin == null) {
                plugin.getLogger().info("[MessageService] 插件未加载，尝试重新初始化...");
                initialize();
            } else if (!messageServicePlugin.isEnabled()) {
                plugin.getLogger().info("[MessageService] 插件未启用，等待下次检查...");
            } else {
                // 插件已启用但 apiInstance 为 null，重新初始化
                plugin.getLogger().info("[MessageService] 插件已启用但API未初始化，尝试重新初始化...");
                initialize();
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[MessageService] 重新初始化失败: " + e.getMessage());
        }
    }
    
    /**
     * 发送跨服临时公告
     * 
     * @param content 内容
     * @param displayType 显示类型 (CHAT, TITLE, ACTION_BAR)
     */
    public void broadcast(String content, String displayType) {
        if (!isEnabled()) {
            // 本地广播
            Bukkit.broadcastMessage(content);
            return;
        }
        
        try {
            Class<?> apiClass = apiInstance.getClass();
            Class<?> displayTypeClass = Class.forName("com.example.messageservice.models.Announcement$DisplayType");
            
            // 获取显示类型枚举
            Object displayTypeEnum = displayTypeClass.getMethod("valueOf", String.class).invoke(null, displayType);
            
            // 处理消息中的关键词（如可点击按钮）
            String processedContent = processKeywords(content);
            
            // 调用 sendTemporary 方法
            Method sendTemporary = apiClass.getMethod("sendTemporary", String.class, displayTypeClass, String.class, List.class);
            sendTemporary.invoke(apiInstance, processedContent, displayTypeEnum, null, null);
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "发送跨服消息失败: " + e.getMessage());
            // 降级为本地广播
            Bukkit.broadcastMessage(content);
        }
    }
    
    /**
     * 发送跨服聊天消息
     * 
     * @param content 内容
     */
    public void broadcastChat(String content) {
        broadcast(content, "CHAT");
    }
    
    /**
     * 发送跨服Title
     * 
     * @param title 标题
     * @param subtitle 副标题
     */
    public void broadcastTitle(String title, String subtitle) {
        if (!isEnabled()) {
            // 本地发送Title
            for (var player : Bukkit.getOnlinePlayers()) {
                player.sendTitle(title, subtitle, 10, 70, 20);
            }
            return;
        }
        
        try {
            String content = title + "\n" + subtitle;
            broadcast(content, "TITLE");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "发送跨服Title失败: " + e.getMessage());
            // 降级为本地发送
            for (var player : Bukkit.getOnlinePlayers()) {
                player.sendTitle(title, subtitle, 10, 70, 20);
            }
        }
    }
    
    /**
     * 发送跨服ActionBar
     * 
     * @param content 内容
     */
    public void broadcastActionBar(String content) {
        broadcast(content, "ACTION_BAR");
    }
    
    /**
     * 发送招人消息到指定服务器
     * 
     * @param needPlayers 需要的人数
     */
    public void sendRecruitMessage(int needPlayers) {
        String message = plugin.getConfig().getString("recruitment.message", 
            "§6§l[幸运之柱] §e幸运之柱 §7即将开始，还缺 §c%need% §7人！快来加入！ %click_able(&a&l[点击加入],\"server minigame\")%")
            .replace("%need%", String.valueOf(needPlayers))
            // 将旧格式的 /server minigame 替换为可点击按钮格式
            .replace("§6/server minigame", "%click_able(&a&l[点击加入],\"server minigame\")%");
        
        // 检查是否有在线玩家（MessageService需要玩家来发送跨服消息）
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            plugin.getLogger().warning("没有在线玩家，无法发送跨服招人消息");
            return;
        }
        
        if (!isEnabled()) {
            plugin.getLogger().warning("MessageService未启用，只在本地广播招人消息");
            // 只在本地广播
            Component component = parseClickableMessage(message);
            for (var player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(component);
            }
            return;
        }
        
        try {
            Class<?> apiClass = apiInstance.getClass();
            Class<?> displayTypeClass = Class.forName("com.example.messageservice.models.Announcement$DisplayType");
            
            // 获取CHAT显示类型
            Object chatType = displayTypeClass.getMethod("valueOf", String.class).invoke(null, "CHAT");
            
            // 处理消息中的关键词
            String processedContent = processKeywords(message);
            
            // 调用 sendTemporary 方法发送到目标服务器
            Method sendTemporary = apiClass.getMethod("sendTemporary", String.class, displayTypeClass, String.class, List.class);
            sendTemporary.invoke(apiInstance, processedContent, chatType, null, targetServers);
            
            plugin.getLogger().info("已发送跨服招人消息到服务器: " + targetServers);
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "发送跨服招人消息失败: " + e.getMessage(), e);
            // 降级为本地广播
            Component component = parseClickableMessage(message);
            for (var player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(component);
            }
        }
    }
    
    /**
     * 发送消息到所有服务器
     * 
     * @param message 消息内容
     */
    public void sendToAllServers(String message) {
        // 检查是否有在线玩家（MessageService需要玩家来发送跨服消息）
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            plugin.getLogger().warning("没有在线玩家，无法发送跨服消息");
            return;
        }
        
        if (!isEnabled()) {
            plugin.getLogger().warning("MessageService未启用，只在本地广播");
            Bukkit.broadcastMessage(message);
            return;
        }
        
        try {
            Class<?> apiClass = apiInstance.getClass();
            Class<?> displayTypeClass = Class.forName("com.example.messageservice.models.Announcement$DisplayType");
            
            // 获取CHAT显示类型
            Object chatType = displayTypeClass.getMethod("valueOf", String.class).invoke(null, "CHAT");
            
            // 处理消息中的关键词
            String processedContent = processKeywords(message);
            
            // 调用 sendTemporary 方法发送到所有服务器（targetServers为null表示所有服务器）
            Method sendTemporary = apiClass.getMethod("sendTemporary", String.class, displayTypeClass, String.class, List.class);
            Object result = sendTemporary.invoke(apiInstance, processedContent, chatType, null, null);
            
            plugin.getLogger().info("已发送消息到所有服务器: " + message);
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "发送跨服消息失败: " + e.getMessage(), e);
            // 降级为本地广播
            Bukkit.broadcastMessage(message);
        }
    }
    
    /**
     * 发送消息到其他服务器（排除当前服务器），并在本地发送不带点击按钮的版本
     * 
     * @param messageWithClick 带点击按钮的完整消息（发送到其他服务器）
     * @param messageWithoutClick 不带点击按钮的消息（发送到本地服务器），为null表示不发送本地消息
     */
    public void sendToOtherServers(String messageWithClick, String messageWithoutClick) {
        sendToOtherServers(messageWithClick, messageWithoutClick, null);
    }
    
    /**
     * 发送消息到其他服务器（排除当前服务器），并在本地发送不带点击按钮的版本
     * 可以指定只发送给特定玩家（通过玩家名列表）
     * 
     * @param messageWithClick 带点击按钮的完整消息（发送到其他服务器）
     * @param messageWithoutClick 不带点击按钮的消息（发送到本地服务器），为null表示不发送本地消息
     * @param targetPlayers 目标玩家名列表（null表示发送给所有玩家）
     */
    public void sendToOtherServers(String messageWithClick, String messageWithoutClick, List<String> targetPlayers) {
        // 检查是否有在线玩家（MessageService需要玩家来发送跨服消息）
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            plugin.getLogger().warning("没有在线玩家，无法发送跨服消息");
            return;
        }
        
        // 如果提供了本地消息，先在本地发送不带点击按钮的消息
        if (messageWithoutClick != null && !messageWithoutClick.isEmpty()) {
            Bukkit.broadcastMessage(messageWithoutClick);
            plugin.getLogger().info("已向本地服务器发送消息（无点击按钮）: " + messageWithoutClick);
        }
        
        if (!isEnabled()) {
            plugin.getLogger().warning("MessageService未启用，只发送了本地消息");
            return;
        }
        
        try {
            Class<?> apiClass = apiInstance.getClass();
            Class<?> displayTypeClass = Class.forName("com.example.messageservice.models.Announcement$DisplayType");
            
            // 获取CHAT显示类型
            Object chatType = displayTypeClass.getMethod("valueOf", String.class).invoke(null, "CHAT");
            
            // 处理消息中的关键词
            String processedContent = processKeywords(messageWithClick);
            
            // 获取其他服务器列表（排除当前服务器）
            List<String> otherServers = getOtherServers();
            
            if (otherServers.isEmpty()) {
                plugin.getLogger().info("没有其他目标服务器需要发送消息");
                return;
            }
            
            // 调用 sendTemporary 方法发送到其他服务器
            // 如果指定了目标玩家，使用第一个玩家名作为targetPlayer参数（MessageService格式）
            String targetPlayerParam = null;
            if (targetPlayers != null && !targetPlayers.isEmpty()) {
                // 将玩家列表序列化为逗号分隔的字符串
                targetPlayerParam = String.join(",", targetPlayers);
            }
            
            Method sendTemporary = apiClass.getMethod("sendTemporary", String.class, displayTypeClass, String.class, List.class);
            Object result = sendTemporary.invoke(apiInstance, processedContent, chatType, targetPlayerParam, otherServers);
            
            plugin.getLogger().info("已发送消息到其他服务器 " + otherServers + "，目标玩家: " + targetPlayers + "，结果: " + result);
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "发送跨服消息失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取其他服务器列表（排除当前服务器）
     */
    private List<String> getOtherServers() {
        List<String> otherServers = new ArrayList<>();
        for (String server : targetServers) {
            if (!server.equalsIgnoreCase(currentServerName)) {
                otherServers.add(server);
            }
        }
        return otherServers;
    }
    
    /**
     * 处理消息中的关键词
     * 支持：%click_able(显示文本,"命令")% - 可点击按钮
     * 
     * 注意：MessageService 期望的格式就是 %click_able(...)%，
     * 所以这里直接返回原始内容，不做转换
     * 
     * 同时将 § 颜色代码转换为 & 颜色代码，以便 MessageService 正确解析
     */
    private String processKeywords(String content) {
        // MessageService 会自己处理 %click_able(...)% 格式
        // 将 § 颜色代码转换为 & 颜色代码，以便 MessageService 正确解析
        return content
            .replace("§0", "&0").replace("§1", "&1").replace("§2", "&2").replace("§3", "&3")
            .replace("§4", "&4").replace("§5", "&5").replace("§6", "&6").replace("§7", "&7")
            .replace("§8", "&8").replace("§9", "&9").replace("§a", "&a").replace("§b", "&b")
            .replace("§c", "&c").replace("§d", "&d").replace("§e", "&e").replace("§f", "&f")
            .replace("§k", "&k").replace("§l", "&l").replace("§m", "&m").replace("§n", "&n")
            .replace("§o", "&o").replace("§r", "&r");
    }
    
    /**
     * 解析带点击按钮的消息（本地显示用）
     */
    private Component parseClickableMessage(String message) {
        // 处理 click_able 关键词
        Pattern pattern = Pattern.compile("%click_able\\(([^,]+),\\\"([^\\\"]+)\\\"\\)%");
        Matcher matcher = pattern.matcher(message);
        
        Component result = Component.empty();
        int lastEnd = 0;
        
        while (matcher.find()) {
            // 添加匹配前的文本
            if (matcher.start() > lastEnd) {
                String beforeText = message.substring(lastEnd, matcher.start());
                result = result.append(parseLegacyText(beforeText));
            }
            
            // 提取显示文本和命令
            String displayText = matcher.group(1);
            String command = matcher.group(2);
            
            // 解析 MiniMessage 格式的显示文本
            Component clickComponent = MiniMessage.miniMessage().deserialize(displayText)
                .clickEvent(ClickEvent.runCommand("/" + command));
            
            result = result.append(clickComponent);
            lastEnd = matcher.end();
        }
        
        // 添加剩余文本
        if (lastEnd < message.length()) {
            result = result.append(parseLegacyText(message.substring(lastEnd)));
        }
        
        return result;
    }
    
    /**
     * 解析传统颜色代码文本
     */
    private Component parseLegacyText(String text) {
        return LegacyComponentSerializer.legacySection().deserialize(text);
    }
    
    /**
     * 获取当前服务器名称
     */
    public String getCurrentServerName() {
        return currentServerName;
    }
    
    /**
     * 获取目标服务器列表
     */
    public List<String> getTargetServers() {
        return targetServers;
    }
}
