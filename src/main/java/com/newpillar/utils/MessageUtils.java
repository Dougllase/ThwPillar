package com.newpillar.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Duration;

/**
 * 消息工具类
 * 提供线程安全的消息发送方法，确保在正确的线程执行玩家操作
 */
public class MessageUtils {
    
    private static final MiniMessage miniMessage = MiniMessage.miniMessage();
    
    // ==================== 基础消息发送 ====================
    
    /**
     * 向玩家发送消息（线程安全）
     * @param player 目标玩家
     * @param message 消息内容（支持MiniMessage格式）
     */
    public static void sendMessage(Player player, String message) {
        if (player == null || !player.isOnline()) return;
        SchedulerUtils.runOnPlayer(player, () -> {
            player.sendMessage(miniMessage.deserialize(message));
        });
    }
    
    /**
     * 向玩家发送普通文本消息（线程安全）
     * @param player 目标玩家
     * @param message 消息内容（普通文本）
     */
    public static void sendPlainMessage(Player player, String message) {
        if (player == null || !player.isOnline()) return;
        SchedulerUtils.runOnPlayer(player, () -> player.sendMessage(message));
    }
    
    /**
     * 向玩家发送消息（在指定位置线程执行）
     * @param player 目标玩家
     * @param location 执行位置
     * @param message 消息内容
     */
    public static void sendMessageAtLocation(Player player, Location location, String message) {
        if (player == null || !player.isOnline()) return;
        SchedulerUtils.runOnLocation(location, () -> player.sendMessage(message));
    }
    
    // ==================== 广播消息 ====================
    
    /**
     * 广播消息到所有玩家（线程安全）
     * @param message 消息内容
     */
    public static void broadcast(String message) {
        SchedulerUtils.runGlobal(() -> Bukkit.broadcastMessage(message));
    }
    
    /**
     * 广播消息到所有玩家（支持MiniMessage）
     * @param message 消息内容（支持MiniMessage格式）
     */
    public static void broadcastMiniMessage(String message) {
        Component component = miniMessage.deserialize(message);
        SchedulerUtils.runGlobal(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(component);
            }
        });
    }
    
    /**
     * 广播消息到指定世界的玩家
     * @param worldName 目标世界名称
     * @param message 消息内容
     */
    public static void broadcastToWorld(String worldName, String message) {
        SchedulerUtils.runGlobal(() -> {
            Bukkit.getWorlds().stream()
                .filter(world -> world.getName().equals(worldName))
                .findFirst()
                .ifPresent(world -> world.getPlayers().forEach(player -> player.sendMessage(message)));
        });
    }
    
    // ==================== Title 和 ActionBar ====================
    
    /**
     * 向玩家发送Title（线程安全）
     * @param player 目标玩家
     * @param title 主标题
     * @param subtitle 副标题
     * @param fadeIn 淡入时间（ticks）
     * @param stay 停留时间（ticks）
     * @param fadeOut 淡出时间（ticks）
     */
    public static void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        if (player == null || !player.isOnline()) return;
        SchedulerUtils.runOnPlayer(player, () -> player.sendTitle(title, subtitle, fadeIn, stay, fadeOut));
    }
    
    /**
     * 向玩家发送Title（默认时间）
     * @param player 目标玩家
     * @param title 主标题
     * @param subtitle 副标题
     */
    public static void sendTitle(Player player, String title, String subtitle) {
        sendTitle(player, title, subtitle, 10, 70, 20);
    }
    
    /**
     * 向玩家发送ActionBar（线程安全）
     * @param player 目标玩家
     * @param message 消息内容
     */
    public static void sendActionBar(Player player, String message) {
        if (player == null || !player.isOnline()) return;
        Component component = miniMessage.deserialize(message);
        SchedulerUtils.runOnPlayer(player, () -> player.sendActionBar(component));
    }
    
    // ==================== 音效 ====================
    
    /**
     * 播放音效给玩家（线程安全）
     * @param player 目标玩家
     * @param sound 音效类型
     * @param volume 音量
     * @param pitch 音调
     */
    public static void playSound(Player player, Sound sound, float volume, float pitch) {
        if (player == null || !player.isOnline()) return;
        SchedulerUtils.runOnPlayer(player, () -> player.playSound(player.getLocation(), sound, volume, pitch));
    }
    
    /**
     * 在指定位置播放音效（线程安全）
     * @param location 音效位置
     * @param sound 音效类型
     * @param volume 音量
     * @param pitch 音调
     */
    public static void playSoundAtLocation(Location location, Sound sound, float volume, float pitch) {
        if (location == null || location.getWorld() == null) return;
        SchedulerUtils.runOnLocation(location, () -> location.getWorld().playSound(location, sound, volume, pitch));
    }
    
    // ==================== 组合消息 ====================
    
    /**
     * 发送消息并播放音效
     * @param player 目标玩家
     * @param message 消息内容
     * @param sound 音效类型
     */
    public static void sendMessageWithSound(Player player, String message, Sound sound) {
        sendPlainMessage(player, message);
        playSound(player, sound, 1.0f, 1.0f);
    }
    
    /**
     * 发送消息、Title并播放音效
     * @param player 目标玩家
     * @param title 主标题
     * @param subtitle 副标题
     * @param message 聊天消息
     * @param sound 音效类型
     */
    public static void sendFullMessage(Player player, String title, String subtitle, String message, Sound sound) {
        sendTitle(player, title, subtitle);
        sendPlainMessage(player, message);
        playSound(player, sound, 1.0f, 1.0f);
    }
    
    // ==================== 特殊消息 ====================
    
    /**
     * 发送成功消息（绿色）
     * @param player 目标玩家
     * @param message 消息内容
     */
    public static void sendSuccess(Player player, String message) {
        sendPlainMessage(player, "§a✓ " + message);
    }
    
    /**
     * 发送错误消息（红色）
     * @param player 目标玩家
     * @param message 消息内容
     */
    public static void sendError(Player player, String message) {
        sendPlainMessage(player, "§c✗ " + message);
    }
    
    /**
     * 发送警告消息（黄色）
     * @param player 目标玩家
     * @param message 消息内容
     */
    public static void sendWarning(Player player, String message) {
        sendPlainMessage(player, "§e⚠ " + message);
    }
    
    /**
     * 发送信息消息（蓝色）
     * @param player 目标玩家
     * @param message 消息内容
     */
    public static void sendInfo(Player player, String message) {
        sendPlainMessage(player, "§bℹ " + message);
    }
    
    private MessageUtils() {
        // 防止实例化
        throw new UnsupportedOperationException("工具类不能被实例化");
    }
}
