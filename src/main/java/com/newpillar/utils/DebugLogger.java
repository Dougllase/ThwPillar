package com.newpillar.utils;

import com.newpillar.NewPillar;
import java.util.logging.Level;

/**
 * 调试日志工具类
 * 提供带开关的日志输出功能
 */
public class DebugLogger {
    private final NewPillar plugin;
    private static final String PREFIX = "[调试] ";

    public DebugLogger(NewPillar plugin) {
        this.plugin = plugin;
    }

    /**
     * 检查是否启用了调试模式
     */
    public boolean isDebugEnabled() {
        return plugin.getConfig().getBoolean("debug.enabled", false);
    }

    /**
     * 检查是否启用了事件日志
     */
    public boolean isEventLogsEnabled() {
        return plugin.getConfig().getBoolean("debug.event_logs", true);
    }

    /**
     * 输出信息级别日志（仅在调试模式开启时）
     */
    public void info(String message) {
        if (isDebugEnabled()) {
            plugin.getLogger().info(PREFIX + message);
        }
    }

    /**
     * 输出警告级别日志（仅在调试模式开启时）
     */
    public void warning(String message) {
        if (isDebugEnabled()) {
            plugin.getLogger().warning(PREFIX + message);
        }
    }

    /**
     * 输出严重级别日志（仅在调试模式开启时）
     */
    public void severe(String message) {
        if (isDebugEnabled()) {
            plugin.getLogger().severe(PREFIX + message);
        }
    }

    /**
     * 输出调试级别日志（仅在调试模式开启时）
     */
    public void debug(String message) {
        if (isDebugEnabled()) {
            plugin.getLogger().info(PREFIX + message);
        }
    }

    /**
     * 输出事件系统信息日志（需要同时开启调试模式和事件日志）
     */
    public void eventInfo(String message) {
        if (isDebugEnabled() && isEventLogsEnabled()) {
            plugin.getLogger().info(PREFIX + "[事件] " + message);
        }
    }

    /**
     * 输出事件系统警告日志（需要同时开启调试模式和事件日志）
     */
    public void eventWarning(String message) {
        if (isDebugEnabled() && isEventLogsEnabled()) {
            plugin.getLogger().warning(PREFIX + "[事件] " + message);
        }
    }

    /**
     * 输出普通信息日志（不受调试模式影响）
     */
    public void alwaysInfo(String message) {
        plugin.getLogger().info(message);
    }

    /**
     * 输出普通警告日志（不受调试模式影响）
     */
    public void alwaysWarning(String message) {
        plugin.getLogger().warning(message);
    }

    /**
     * 格式化并输出事件日志
     */
    public void logEvent(String eventName, String action, Object... params) {
        if (!isDebugEnabled() || !isEventLogsEnabled()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(eventName).append("] ").append(action);
        if (params.length > 0) {
            sb.append(" | 参数: ");
            for (int i = 0; i < params.length; i += 2) {
                if (i > 0) sb.append(", ");
                sb.append(params[i]).append("=").append(params[i + 1]);
            }
        }
        plugin.getLogger().info(PREFIX + sb.toString());
    }
}
