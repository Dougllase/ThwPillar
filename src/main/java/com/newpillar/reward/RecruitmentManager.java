package com.newpillar.reward;

import com.newpillar.NewPillar;
import com.newpillar.utils.GameConstants;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 招人管理器
 * 负责跨服招人消息的发送
 */
public class RecruitmentManager {
    
    private final NewPillar plugin;
    
    // 配置
    private final int minPlayers;
    private final int maxPlayers;
    private final long recruitInterval;
    private final boolean recruitEnabled;
    
    // 首个玩家加入广播配置
    private final boolean firstJoinBroadcastEnabled;
    private final String firstJoinBroadcastFormat;
    
    // 玩家加入广播配置
    private final boolean joinBroadcastEnabled;
    private final int joinBroadcastMinPlayers;
    private final int joinBroadcastMaxPlayers;
    private final String joinBroadcastFormat;
    
    // 定期招人消息配置
    private final String recruitMessageFormat;
    
    // 招人循环控制
    private long lastRecruitTime = 0;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicBoolean isRecruitmentActive = new AtomicBoolean(false);
    private ScheduledTask checkTask;
    
    // 首次加入检测
    private volatile boolean hasAnnouncedFirstPlayer = false;
    private volatile long firstPlayerJoinTime = 0;
    private static final long FIRST_RECRUIT_DELAY_MS = GameConstants.FIRST_RECRUIT_DELAY; // 首次招人延迟2分钟
    
    // 延迟发送机制
    private volatile boolean hasPendingRecruit = false;
    private volatile int pendingNeedPlayers = 0;
    private volatile long pendingTimestamp = 0;
    private static final long PENDING_EXPIRY_MS = GameConstants.PENDING_EXPIRY; // 5分钟过期
    
    public RecruitmentManager(NewPillar plugin) {
        this.plugin = plugin;
        
        // 加载配置
        var config = plugin.getConfig();
        this.recruitEnabled = config.getBoolean("recruitment.enabled", true);
        this.minPlayers = config.getInt("recruitment.min-players", GameConstants.MIN_PLAYERS);
        this.maxPlayers = config.getInt("recruitment.max-players", GameConstants.MAX_PLAYERS);
        this.recruitInterval = config.getLong("recruitment.interval-minutes", GameConstants.RECRUIT_INTERVAL / (60 * 1000)) * 60 * 1000;
        
        // 首个玩家加入广播配置（从配置文件读取）
        var firstJoinConfig = config.getConfigurationSection("recruitment.first-join-broadcast");
        if (firstJoinConfig != null) {
            this.firstJoinBroadcastEnabled = firstJoinConfig.getBoolean("enabled", true);
            this.firstJoinBroadcastFormat = firstJoinConfig.getString("message-format",
                "§6§l[幸运之柱] §e%player% §7已加入幸运之柱小游戏！(%current%/%max%) %click_able(&a&l[点击加入],\"server minigame\")%");
        } else {
            this.firstJoinBroadcastEnabled = true;
            this.firstJoinBroadcastFormat = "§6§l[幸运之柱] §e%player% §7已加入幸运之柱小游戏！(%current%/%max%) %click_able(&a&l[点击加入],\"server minigame\")%";
        }
        
        // 玩家加入广播配置（从配置文件读取）
        var joinBroadcastConfig = config.getConfigurationSection("recruitment.join-broadcast");
        if (joinBroadcastConfig != null) {
            this.joinBroadcastEnabled = joinBroadcastConfig.getBoolean("enabled", true);
            this.joinBroadcastMinPlayers = joinBroadcastConfig.getInt("min-players-to-broadcast", GameConstants.JOIN_BROADCAST_MIN);
            this.joinBroadcastMaxPlayers = joinBroadcastConfig.getInt("max-players-to-broadcast", GameConstants.JOIN_BROADCAST_MAX);
            this.joinBroadcastFormat = joinBroadcastConfig.getString("message-format",
                "§6§l[幸运之柱] §e%player% §7已加入幸运之柱小游戏！(%current%/%max%) %click_able(&a&l[点击加入],\"server minigame\")%");
        } else {
            this.joinBroadcastEnabled = true;
            this.joinBroadcastMinPlayers = 2;
            this.joinBroadcastMaxPlayers = 11;
            this.joinBroadcastFormat = "§6§l[幸运之柱] §e%player% §7已加入幸运之柱小游戏！(%current%/%max%) %click_able(&a&l[点击加入],\"server minigame\")%";
        }
        
        // 定期招人消息格式（从配置文件读取）
        this.recruitMessageFormat = config.getString("recruitment.message",
            "§6§l[幸运之柱] §e幸运之柱 §7即将开始，还缺 §c%need% §7人！快来加入！ %click_able(&a&l[点击加入],\"server minigame\")%");
    }
    
    /**
     * 游戏开始
     */
    public void onGameStart(int gameId) {
        // 重置招人系统
        reset();
        plugin.getLogger().info("[招人系统] 游戏 #" + gameId + " 开始，招人系统已重置");
    }
    
    /**
     * 游戏结束
     */
    public void onGameEnd(int gameId) {
        // 停止招人循环
        if (isRunning.compareAndSet(true, false)) {
            if (checkTask != null) {
                checkTask.cancel();
            }
            isRecruitmentActive.set(false);
        }
        reset();
        plugin.getLogger().info("[招人系统] 游戏 #" + gameId + " 结束，招人系统已停止");
    }
    
    /**
     * 玩家加入
     */
    public void onPlayerJoin(Player player) {
        String playerName = player.getName();
        int currentPlayers = Bukkit.getOnlinePlayers().size();
        
        // 如果是首个玩家，发送首个玩家加入广播并启动招人循环
        if (!hasAnnouncedFirstPlayer && currentPlayers == 1) {
            hasAnnouncedFirstPlayer = true;
            firstPlayerJoinTime = System.currentTimeMillis();
            
            // 发送首个玩家加入广播
            broadcastFirstPlayerJoin(playerName);
            
            // 启动招人循环（2分钟后首次发送）
            scheduleFirstRecruit();
            
            plugin.getLogger().info("[招人系统] 首个玩家 " + playerName + " 加入，已发送通知，2分钟后开始招人");
        } else {
            // 非首个玩家，广播玩家加入消息
            broadcastPlayerJoin(playerName, currentPlayers);
        }
        
        // 尝试发送缓存的招人消息
        trySendPendingRecruit();
    }
    
    /**
     * 玩家退出
     */
    public void onPlayerQuit(Player player) {
        String playerName = player.getName();
        int currentPlayers = Bukkit.getOnlinePlayers().size() - 1; // 退出后的人数
        
        // 广播玩家退出消息
        broadcastPlayerQuit(playerName, currentPlayers);
    }
    
    /**
     * 广播首个玩家加入消息
     */
    private void broadcastFirstPlayerJoin(String playerName) {
        if (!firstJoinBroadcastEnabled) {
            return;
        }
        
        // 构建完整消息（用于跨服发送，包含点击按钮）
        String messageWithClick = firstJoinBroadcastFormat
            .replace("%player%", playerName)
            .replace("%current%", "1")
            .replace("%max%", String.valueOf(maxPlayers))
            .replace("%min%", String.valueOf(minPlayers))
            .replace("%need%", String.valueOf(minPlayers - 1));
        
        // 构建本地消息（去掉点击按钮）
        String localMessage = messageWithClick.replaceAll("%click_able\\([^)]*\\)%", "").trim();
        
        // 本地广播（不带点击按钮）
        Bukkit.broadcastMessage(localMessage);
        
        plugin.getLogger().info("[招人系统] 已向本地服务器发送首个玩家加入消息: " + playerName);
        
        // 延时3秒后发送跨服消息，确保 MessageService 已准备好
        plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin, task -> {
            sendToOtherServers(messageWithClick);
            plugin.getLogger().info("[招人系统] 已发送首个玩家加入消息到其他服务器: " + playerName);
        }, GameConstants.DELAY_MEDIUM); // 延时3秒
    }
    
    /**
     * 广播玩家加入消息
     */
    private void broadcastPlayerJoin(String playerName, int currentPlayers) {
        if (!joinBroadcastEnabled) {
            return;
        }
        
        // 检查人数是否在广播范围内（2-11人）
        if (currentPlayers < joinBroadcastMinPlayers || currentPlayers > joinBroadcastMaxPlayers) {
            return;
        }
        
        // 检查是否仍然缺人（少于4人）
        if (currentPlayers >= minPlayers) {
            return;
        }
        
        // 计算还缺多少人
        int needPlayers = minPlayers - currentPlayers;
        
        // 构建完整消息（用于跨服发送，包含点击按钮）
        String messageWithClick = joinBroadcastFormat
            .replace("%player%", playerName)
            .replace("%current%", String.valueOf(currentPlayers))
            .replace("%max%", String.valueOf(maxPlayers))
            .replace("%min%", String.valueOf(minPlayers))
            .replace("%need%", String.valueOf(needPlayers));
        
        // 构建本地消息（去掉点击按钮）
        String localMessage = messageWithClick.replaceAll("%click_able\\([^)]*\\)%", "").trim();
        
        // 本地广播（不带点击按钮）
        Bukkit.broadcastMessage(localMessage);
        
        plugin.getLogger().info("[招人系统] 已向本地服务器发送玩家加入消息: " + playerName + " (" + currentPlayers + "/" + maxPlayers + ", 还缺" + needPlayers + "人)");
        
        // 延时3秒后发送跨服消息，确保 MessageService 已准备好
        plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin, task -> {
            sendToOtherServers(messageWithClick);
            plugin.getLogger().info("[招人系统] 已发送玩家加入消息到其他服务器: " + playerName);
        }, GameConstants.DELAY_MEDIUM); // 延时3秒
    }

    /**
     * 广播玩家退出消息
     */
    private void broadcastPlayerQuit(String playerName, int currentPlayers) {
        String quitMessage = "§c[-]§e" + playerName + " §7退出了游戏房间";
        Bukkit.broadcastMessage(quitMessage);
        plugin.getLogger().info("[招人系统] 已广播玩家退出消息: " + playerName + "，剩余玩家: " + currentPlayers);
    }

    /**
     * 安排首次招人（2分钟后）
     */
    private void scheduleFirstRecruit() {
        plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin, task -> {
            // 激活招人循环
            isRecruitmentActive.set(true);
            startRecruitCheckTask();
            plugin.getLogger().info("[招人系统] 招人循环已激活，开始检测人数...");

            // 立即检查一次
            checkAndRecruit();
        }, GameConstants.TICKS_PER_SECOND * GameConstants.SECONDS_PER_MINUTE * 2); // 2分钟后
    }

    /**
     * 启动招人检查任务
     */
    private void startRecruitCheckTask() {
        if (isRunning.compareAndSet(false, true)) {
            checkTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin,
                task -> checkAndRecruit(),
                GameConstants.TICKS_PER_SECOND,  // 1秒后开始
                GameConstants.TICKS_PER_SECOND   // 每秒检查一次
            );
        }
    }
    
    /**
     * 检查是否需要招人
     */
    private void checkAndRecruit() {
        // 如果招人循环未激活，不检查
        if (!isRecruitmentActive.get()) {
            return;
        }
        
        // 检查是否在冷却期
        long now = System.currentTimeMillis();
        if (now - lastRecruitTime < recruitInterval) {
            return;
        }
        
        // 获取游戏状态
        var gameManager = plugin.getGameManager();
        if (gameManager == null) return;
        
        var gameStatus = gameManager.getGameStatus();
        if (gameStatus == null) return;
        
        // 只在游戏未开始（大厅状态）时触发招人
        if (gameStatus != com.newpillar.game.enums.GameStatus.LOBBY) {
            return;
        }
        
        // 检查人数是否不足
        int currentPlayers = Bukkit.getOnlinePlayers().size();
        if (currentPlayers >= minPlayers) {
            // 人数已满，暂停招人循环
            pauseRecruitment("人数已达最低要求 " + currentPlayers + "/" + minPlayers);
            return;
        }
        
        // 发送招人消息
        int needPlayers = minPlayers - currentPlayers;
        sendRecruitMessage(needPlayers);
        
        lastRecruitTime = now;
        plugin.getLogger().info("[招人系统] 已发送招人消息，当前人数: " + currentPlayers + "/" + minPlayers);
    }
    
    /**
     * 发送招人消息
     */
    private void sendRecruitMessage(int needPlayers) {
        Player sender = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (sender == null) {
            // 没有玩家，缓存消息等待玩家加入
            cachePendingRecruit(needPlayers);
            plugin.getLogger().info("[招人系统] 暂无在线玩家，招人消息已缓存");
            return;
        }
        
        // 构建完整消息（用于跨服发送，包含点击按钮）
        String messageWithClick = recruitMessageFormat.replace("%need%", String.valueOf(needPlayers));
        
        // 构建本地消息（去掉点击按钮，不显示任何加入提示，因为玩家已经在小游戏服）
        String localMessage = messageWithClick.replaceAll("%click_able\\([^)]*\\)%", "").trim();
        
        // 本地广播（不带点击按钮）
        Bukkit.broadcastMessage(localMessage);
        
        // 发送到其他服务器（带点击按钮）
        sendToOtherServers(messageWithClick);
        
        plugin.getLogger().info("[招人系统] 已发送招人消息，还缺 " + needPlayers + " 人");
    }
    
    /**
     * 缓存待发送的招人消息
     */
    private void cachePendingRecruit(int needPlayers) {
        hasPendingRecruit = true;
        pendingNeedPlayers = needPlayers;
        pendingTimestamp = System.currentTimeMillis();
    }
    
    /**
     * 尝试发送缓存的招人消息
     */
    private void trySendPendingRecruit() {
        if (!hasPendingRecruit) {
            return;
        }
        
        // 检查消息是否过期
        long now = System.currentTimeMillis();
        if (now - pendingTimestamp > PENDING_EXPIRY_MS) {
            hasPendingRecruit = false;
            pendingNeedPlayers = 0;
            plugin.getLogger().info("[招人系统] 缓存的招人消息已过期");
            return;
        }
        
        // 检查是否有在线玩家
        Player sender = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (sender == null) {
            return;
        }
        
        // 发送缓存的消息
        sendRecruitMessage(pendingNeedPlayers);
        
        // 清除缓存
        hasPendingRecruit = false;
        pendingNeedPlayers = 0;
        lastRecruitTime = now;
        
        plugin.getLogger().info("[招人系统] 玩家加入后，已发送缓存的招人消息");
    }
    
    /**
     * 暂停招人循环
     */
    private void pauseRecruitment(String reason) {
        if (isRecruitmentActive.compareAndSet(true, false)) {
            plugin.getLogger().info("[招人系统] 招人循环已暂停: " + reason);
        }
    }
    
    /**
     * 重置招人系统
     */
    public void reset() {
        hasAnnouncedFirstPlayer = false;
        firstPlayerJoinTime = 0;
        isRecruitmentActive.set(false);
        lastRecruitTime = 0;
        hasPendingRecruit = false;
        pendingNeedPlayers = 0;
    }
    
    /**
     * 发送消息到其他服务器（通过 MessageService）
     * 使用异步调度器避免阻塞主线程
     */
    private void sendToOtherServers(String message) {
        // 使用异步调度器发送跨服消息
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            var messageServiceIntegration = plugin.getMessageServiceIntegration();
            if (messageServiceIntegration == null) {
                plugin.getLogger().warning("MessageService集成未初始化，无法发送跨服消息");
                return;
            }
            
            // 检查是否启用
            if (!messageServiceIntegration.isEnabled()) {
                plugin.getLogger().info("[招人系统] MessageService未启用，尝试重新初始化...");
                // 在异步线程中短暂等待，让初始化有机会完成
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            
            // 再次检查是否启用
            if (messageServiceIntegration.isEnabled()) {
                // 切换回主线程发送消息（MessageService 可能需要主线程）
                plugin.getServer().getGlobalRegionScheduler().run(plugin, mainTask -> {
                    messageServiceIntegration.sendToOtherServers(message, null);
                });
                plugin.getLogger().info("[招人系统] 已发送跨服消息: " + message.substring(0, Math.min(50, message.length())) + "...");
            } else {
                plugin.getLogger().warning("[招人系统] MessageService仍然未启用，无法发送跨服消息");
            }
        });
    }
}
