package com.newpillar.utils;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Consumer;

/**
 * Folia调度器工具类
 * 提供统一的API来处理Folia的多线程调度
 * 简化GlobalRegionScheduler、RegionScheduler和EntityScheduler的使用
 */
public class SchedulerUtils {

    private static JavaPlugin plugin;
    private static boolean initialized = false;

    /**
     * 初始化工具类
     * @param plugin 插件实例
     */
    public static void init(JavaPlugin plugin) {
        SchedulerUtils.plugin = plugin;
        SchedulerUtils.initialized = true;
    }

    /**
     * 检查是否已初始化
     */
    private static void checkInit() {
        if (!initialized) {
            throw new IllegalStateException("SchedulerUtils未初始化！请在插件onEnable中调用SchedulerUtils.init(this)");
        }
    }

    // ==================== 立即执行任务 ====================

    /**
     * 在全局区域执行（世界设置、游戏规则等）
     * @param task 要执行的任务
     */
    public static void runGlobal(Runnable task) {
        checkInit();
        Bukkit.getGlobalRegionScheduler().execute(plugin, task);
    }

    /**
     * 在指定世界执行（在世界的spawn位置执行）
     * @param world 目标世界
     * @param task 要执行的任务
     */
    public static void runOnWorld(World world, Runnable task) {
        checkInit();
        Location spawn = world.getSpawnLocation();
        Bukkit.getRegionScheduler().execute(plugin, spawn, task);
    }

    /**
     * 在指定位置执行
     * @param location 目标位置
     * @param task 要执行的任务
     */
    public static void runOnLocation(Location location, Runnable task) {
        checkInit();
        Bukkit.getRegionScheduler().execute(plugin, location, task);
    }

    /**
     * 在实体所在线程执行
     * @param entity 目标实体
     * @param task 要执行的任务
     */
    public static void runOnEntity(Entity entity, Runnable task) {
        checkInit();
        if (entity.isValid()) {
            entity.getScheduler().execute(plugin, task, null, 1L);
        }
    }

    /**
     * 在玩家所在线程执行（便捷方法）
     * @param player 目标玩家
     * @param task 要执行的任务
     */
    public static void runOnPlayer(Player player, Runnable task) {
        checkInit();
        if (player.isOnline()) {
            player.getScheduler().execute(plugin, task, null, 1L);
        }
    }

    /**
     * 在指定区块执行
     * @param world 目标世界
     * @param chunkX 区块X坐标
     * @param chunkZ 区块Z坐标
     * @param task 要执行的任务
     */
    public static void runOnChunk(World world, int chunkX, int chunkZ, Runnable task) {
        checkInit();
        Location loc = new Location(world, chunkX << 4, 64, chunkZ << 4);
        Bukkit.getRegionScheduler().execute(plugin, loc, task);
    }

    // ==================== 延迟执行任务 ====================

    /**
     * 延迟后在全局区域执行
     * @param delay 延迟tick数
     * @param task 要执行的任务
     * @return ScheduledTask实例，可用于取消任务
     */
    public static ScheduledTask runLaterGlobal(long delay, Runnable task) {
        checkInit();
        return Bukkit.getGlobalRegionScheduler().runDelayed(plugin, 
            scheduledTask -> task.run(), Math.max(1, delay));
    }

    /**
     * 延迟后在全局区域执行（带任务参数）
     * @param delay 延迟tick数
     * @param task 要执行的任务
     * @return ScheduledTask实例，可用于取消任务
     */
    public static ScheduledTask runLaterGlobal(long delay, Consumer<ScheduledTask> task) {
        checkInit();
        return Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task, Math.max(1, delay));
    }

    /**
     * 延迟后在指定世界执行（在世界的spawn位置执行）
     * @param world 目标世界
     * @param delay 延迟tick数
     * @param task 要执行的任务
     * @return ScheduledTask实例，可用于取消任务
     */
    public static ScheduledTask runLaterOnWorld(World world, long delay, Runnable task) {
        checkInit();
        Location spawn = world.getSpawnLocation();
        return Bukkit.getRegionScheduler().runDelayed(plugin, spawn,
            scheduledTask -> task.run(), Math.max(1, delay));
    }

    /**
     * 延迟后在指定位置执行
     * @param location 目标位置
     * @param delay 延迟tick数
     * @param task 要执行的任务
     * @return ScheduledTask实例，可用于取消任务
     */
    public static ScheduledTask runLaterOnLocation(Location location, long delay, Runnable task) {
        checkInit();
        return Bukkit.getRegionScheduler().runDelayed(plugin, location,
            scheduledTask -> task.run(), Math.max(1, delay));
    }

    /**
     * 延迟后在实体所在线程执行
     * @param entity 目标实体
     * @param delay 延迟tick数
     * @param task 要执行的任务
     * @return ScheduledTask实例，可用于取消任务
     */
    public static ScheduledTask runLaterOnEntity(Entity entity, long delay, Runnable task) {
        checkInit();
        if (!entity.isValid()) {
            return null;
        }
        return entity.getScheduler().runDelayed(plugin,
            scheduledTask -> task.run(), null, Math.max(1, delay));
    }

    /**
     * 延迟后在玩家所在线程执行（便捷方法）
     * @param player 目标玩家
     * @param delay 延迟tick数
     * @param task 要执行的任务
     * @return ScheduledTask实例，可用于取消任务
     */
    public static ScheduledTask runLaterOnPlayer(Player player, long delay, Runnable task) {
        return runLaterOnEntity(player, delay, task);
    }

    // ==================== 定时重复任务 ====================

    /**
     * 在全局区域定时重复执行
     * @param delay 首次执行延迟tick数
     * @param period 执行间隔tick数
     * @param task 要执行的任务
     * @return ScheduledTask实例，可用于取消任务
     */
    public static ScheduledTask runTimerGlobal(long delay, long period, Consumer<ScheduledTask> task) {
        checkInit();
        return Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task, Math.max(1, delay), Math.max(1, period));
    }

    /**
     * 在指定世界定时重复执行（在世界的spawn位置执行）
     * @param world 目标世界
     * @param delay 首次执行延迟tick数
     * @param period 执行间隔tick数
     * @param task 要执行的任务
     * @return ScheduledTask实例，可用于取消任务
     */
    public static ScheduledTask runTimerOnWorld(World world, long delay, long period, Consumer<ScheduledTask> task) {
        checkInit();
        Location spawn = world.getSpawnLocation();
        return Bukkit.getRegionScheduler().runAtFixedRate(plugin, spawn, task, Math.max(1, delay), Math.max(1, period));
    }

    /**
     * 在指定位置定时重复执行
     * @param location 目标位置
     * @param delay 首次执行延迟tick数
     * @param period 执行间隔tick数
     * @param task 要执行的任务
     * @return ScheduledTask实例，可用于取消任务
     */
    public static ScheduledTask runTimerOnLocation(Location location, long delay, long period, Consumer<ScheduledTask> task) {
        checkInit();
        return Bukkit.getRegionScheduler().runAtFixedRate(plugin, location, task, Math.max(1, delay), Math.max(1, period));
    }

    /**
     * 在实体所在线程定时重复执行
     * @param entity 目标实体
     * @param delay 首次执行延迟tick数
     * @param period 执行间隔tick数
     * @param task 要执行的任务
     * @return ScheduledTask实例，可用于取消任务
     */
    public static ScheduledTask runTimerOnEntity(Entity entity, long delay, long period, Consumer<ScheduledTask> task) {
        checkInit();
        if (!entity.isValid()) {
            return null;
        }
        return entity.getScheduler().runAtFixedRate(plugin, task, null, Math.max(1, delay), Math.max(1, period));
    }

    // ==================== 传送工具 ====================

    /**
     * 异步传送玩家
     * @param player 要传送的玩家
     * @param location 目标位置
     * @param onComplete 传送完成后的回调（可为null）
     */
    public static void teleport(Player player, Location location, Runnable onComplete) {
        checkInit();
        player.teleportAsync(location).thenRun(() -> {
            if (onComplete != null) {
                // 在目标位置所在线程执行回调
                runOnLocation(location, onComplete);
            }
        });
    }

    /**
     * 异步传送玩家（无回调）
     * @param player 要传送的玩家
     * @param location 目标位置
     */
    public static void teleport(Player player, Location location) {
        teleport(player, location, null);
    }

    /**
     * 异步传送玩家并在传送后执行玩家相关操作
     * @param player 要传送的玩家
     * @param location 目标位置
     * @param onComplete 传送完成后在玩家线程执行的回调
     */
    public static void teleportAndRunOnPlayer(Player player, Location location, Runnable onComplete) {
        checkInit();
        player.teleportAsync(location).thenRun(() -> {
            if (onComplete != null) {
                runOnPlayer(player, onComplete);
            }
        });
    }

    // ==================== 异步任务 ====================

    /**
     * 在异步线程立即执行任务（用于数据库操作等）
     * @param task 要执行的任务
     */
    public static void runAsync(Runnable task) {
        checkInit();
        Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
    }

    /**
     * 在异步线程定时重复执行任务
     * @param delay 首次执行延迟毫秒数
     * @param period 执行间隔毫秒数
     * @param task 要执行的任务
     * @return ScheduledTask实例，可用于取消任务
     */
    public static ScheduledTask runTimerAsync(long delay, long period, Consumer<ScheduledTask> task) {
        checkInit();
        return Bukkit.getAsyncScheduler().runAtFixedRate(plugin, task, delay, period, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * 延迟后在异步线程执行任务
     * @param delay 延迟毫秒数
     * @param task 要执行的任务
     * @return ScheduledTask实例，可用于取消任务
     */
    public static ScheduledTask runLaterAsync(long delay, Consumer<ScheduledTask> task) {
        checkInit();
        return Bukkit.getAsyncScheduler().runDelayed(plugin, task, delay, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    // ==================== 任务取消 ====================

    /**
     * 取消任务
     * @param task 要取消的ScheduledTask
     */
    public static void cancel(ScheduledTask task) {
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * 安全取消任务（处理null）
     * @param task 要取消的ScheduledTask（可为null）
     */
    public static void cancelSafely(ScheduledTask task) {
        cancel(task);
    }
}
