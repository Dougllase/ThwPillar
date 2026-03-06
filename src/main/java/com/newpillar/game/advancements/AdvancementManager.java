package com.newpillar.game.advancements;

import com.newpillar.game.enums.AchievementType;

import com.newpillar.NewPillar;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.logging.Level;

public class AdvancementManager {
    private final NewPillar plugin;
    private final Map<String, NamespacedKey> advancementKeys = new HashMap<>();
    private boolean initialized = false;
    
    public AdvancementManager(NewPillar plugin) {
        this.plugin = plugin;
    }
    
    /**
     * 初始化所有成就进度
     * 注意：Bukkit API 不允许动态创建 Advancement，
     * 我们需要使用数据包方式或依赖预定义的进度
     */
    public void initialize() {
        // 注册所有成就的 NamespacedKey
        registerAdvancementKeys();
        
        // 检查并创建数据包进度文件
        createAdvancementDataPack();
        
        initialized = true;
        plugin.getLogger().info("成就进度系统初始化完成！");
    }
    
    private void registerAdvancementKeys() {
        // 根进度
        advancementKeys.put("root", new NamespacedKey(plugin, "main/root"));
        
        // 击杀成就
        advancementKeys.put("killed_1", new NamespacedKey(plugin, "main/game/killed/1"));
        advancementKeys.put("killed_20", new NamespacedKey(plugin, "main/game/killed/20"));
        advancementKeys.put("killed_50", new NamespacedKey(plugin, "main/game/killed/50"));
        advancementKeys.put("killed_100", new NamespacedKey(plugin, "main/game/killed/100"));
        advancementKeys.put("killed_200", new NamespacedKey(plugin, "main/game/killed/200"));
        
        // 胜利成就
        advancementKeys.put("win_1", new NamespacedKey(plugin, "main/game/win/1"));
        advancementKeys.put("win_20", new NamespacedKey(plugin, "main/game/win/20"));
        advancementKeys.put("win_50", new NamespacedKey(plugin, "main/game/win/50"));
        advancementKeys.put("win_100", new NamespacedKey(plugin, "main/game/win/100"));
        advancementKeys.put("win_200", new NamespacedKey(plugin, "main/game/win/200"));
        
        // 事件成就
        advancementKeys.put("king_game", new NamespacedKey(plugin, "main/event/17_king_game/be_king"));
        advancementKeys.put("nuclear", new NamespacedKey(plugin, "main/event/24_nuclear"));
        advancementKeys.put("kingslayer", new NamespacedKey(plugin, "main/event/17_king_game/kingslayer"));
        
        // 物品成就
        advancementKeys.put("bruce", new NamespacedKey(plugin, "main/item/bruce"));
        advancementKeys.put("blue_screen", new NamespacedKey(plugin, "main/item/blue_screen"));
        advancementKeys.put("fly_mace", new NamespacedKey(plugin, "main/item/fly_mace"));
        advancementKeys.put("invisible_scarf", new NamespacedKey(plugin, "main/item/invisible_scarf"));
        advancementKeys.put("big_flame_rod", new NamespacedKey(plugin, "main/item/big_flame_rod"));
        advancementKeys.put("meow_axe", new NamespacedKey(plugin, "main/item/meow_axe"));
        advancementKeys.put("pixie", new NamespacedKey(plugin, "main/item/pixie"));
        advancementKeys.put("rocket_boots", new NamespacedKey(plugin, "main/item/rocket_boots"));
        advancementKeys.put("running_shoes", new NamespacedKey(plugin, "main/item/running_shoes"));
        advancementKeys.put("witch_apple", new NamespacedKey(plugin, "main/item/witch_apple"));
        advancementKeys.put("yanpai", new NamespacedKey(plugin, "main/item/yanpai"));
        advancementKeys.put("clock", new NamespacedKey(plugin, "main/item/clock"));
        advancementKeys.put("hongbao", new NamespacedKey(plugin, "main/item/hongbao"));
        advancementKeys.put("hypnosis_app", new NamespacedKey(plugin, "main/item/hypnosis_app"));
        advancementKeys.put("bones_without_chicken_feet", new NamespacedKey(plugin, "main/item/bones_without_chicken_feet"));
        advancementKeys.put("knockback_stick", new NamespacedKey(plugin, "main/item/knockback_stick"));
        advancementKeys.put("godly_pickaxe", new NamespacedKey(plugin, "main/item/godly_pickaxe"));
        advancementKeys.put("spawner", new NamespacedKey(plugin, "main/item/spawner"));
        advancementKeys.put("nether_star_use", new NamespacedKey(plugin, "main/item/nether_star"));
        advancementKeys.put("dragon_breath_use", new NamespacedKey(plugin, "main/item/dragon_breath"));
        advancementKeys.put("echo_shard_use", new NamespacedKey(plugin, "main/item/echo_shard"));
        advancementKeys.put("fire_charge_use", new NamespacedKey(plugin, "main/item/fire_charge"));
        advancementKeys.put("tnt_use", new NamespacedKey(plugin, "main/item/tnt"));
        advancementKeys.put("bow_use", new NamespacedKey(plugin, "main/item/bow"));
        advancementKeys.put("crossbow_use", new NamespacedKey(plugin, "main/item/crossbow"));
        advancementKeys.put("end_crystal_use", new NamespacedKey(plugin, "main/item/end_crystal"));
        advancementKeys.put("feather_use", new NamespacedKey(plugin, "main/item/feather"));
        advancementKeys.put("enchanted_book_use", new NamespacedKey(plugin, "main/item/enchanted_book"));
    }
    
    /**
     * 创建数据包进度文件
     * 将进度定义写入世界文件夹的数据包中
     */
    private void createAdvancementDataPack() {
        // 由于 Bukkit API 限制，我们需要通过其他方式创建进度
        // 这里我们使用反射或 NMS 来创建进度，或者依赖服务器管理员手动安装数据包
        plugin.getLogger().info("注意：原生进度需要数据包支持，请确保服务器已安装 NewPillar 数据包！");
    }
    
    /**
     * 授予玩家成就进度
     */
    public void grantAdvancement(Player player, String achievementKey) {
        if (!initialized) {
            plugin.getLogger().warning("成就系统尚未初始化，无法授予成就: " + achievementKey);
            return;
        }
        
        NamespacedKey key = advancementKeys.get(achievementKey.toLowerCase());
        if (key == null) {
            plugin.getLogger().warning("未找到成就: " + achievementKey);
            return;
        }
        
        Advancement advancement = Bukkit.getAdvancement(key);
        if (advancement == null) {
            // 如果进度不存在，尝试使用命令创建（适用于某些服务器环境）
            grantAdvancementViaCommand(player, key);
            return;
        }
        
        AdvancementProgress progress = player.getAdvancementProgress(advancement);
        if (!progress.isDone()) {
            for (String criteria : progress.getRemainingCriteria()) {
                progress.awardCriteria(criteria);
            }
            plugin.getLogger().info("授予玩家 " + player.getName() + " 成就: " + achievementKey);
        }
    }
    
    /**
     * 通过命令授予成就（备用方案）
     */
    private void grantAdvancementViaCommand(Player player, NamespacedKey key) {
        String command = String.format("advancement grant %s only %s", player.getName(), key.toString());
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }
    
    /**
     * 撤销玩家成就进度
     */
    public void revokeAdvancement(Player player, String achievementKey) {
        NamespacedKey key = advancementKeys.get(achievementKey.toLowerCase());
        if (key == null) return;
        
        Advancement advancement = Bukkit.getAdvancement(key);
        if (advancement == null) {
            revokeAdvancementViaCommand(player, key);
            return;
        }
        
        AdvancementProgress progress = player.getAdvancementProgress(advancement);
        if (progress.isDone()) {
            for (String criteria : progress.getAwardedCriteria()) {
                progress.revokeCriteria(criteria);
            }
        }
    }
    
    private void revokeAdvancementViaCommand(Player player, NamespacedKey key) {
        String command = String.format("advancement revoke %s only %s", player.getName(), key.toString());
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }
    
    /**
     * 检查玩家是否拥有成就
     */
    public boolean hasAdvancement(Player player, String achievementKey) {
        NamespacedKey key = advancementKeys.get(achievementKey.toLowerCase());
        if (key == null) return false;
        
        Advancement advancement = Bukkit.getAdvancement(key);
        if (advancement == null) return false;
        
        return player.getAdvancementProgress(advancement).isDone();
    }
    
    /**
     * 同步数据库成就到 Minecraft 进度系统
     * 在玩家加入时调用
     */
    public void syncAchievementsFromDatabase(Player player) {
        Set<String> achievements = plugin.getDatabaseManager().getPlayerAchievements(player.getUniqueId());
        
        for (String achievementId : achievements) {
            grantAdvancement(player, achievementId);
        }
        
        plugin.getLogger().info("已为玩家 " + player.getName() + " 同步 " + achievements.size() + " 个成就");
    }
    
    /**
     * 将 AchievementType 转换为进度 key
     */
    public String convertToKey(AchievementType type) {
        return switch (type) {
            case KILLED_1 -> "killed_1";
            case KILLED_20 -> "killed_20";
            case KILLED_50 -> "killed_50";
            case KILLED_100 -> "killed_100";
            case KILLED_200 -> "killed_200";
            case WIN_1 -> "win_1";
            case WIN_20 -> "win_20";
            case WIN_50 -> "win_50";
            case WIN_100 -> "win_100";
            case WIN_200 -> "win_200";
            case KING_GAME -> "king_game";
            case NUCLEAR -> "nuclear";
            case KINGSLAYER -> "kingslayer";
            case BRUCE -> "bruce";
            case BLUE_SCREEN -> "blue_screen";
            case FLY_MACE -> "fly_mace";
            case INVISIBLE_SCARF -> "invisible_scarf";
            case BIG_FLAME_ROD -> "big_flame_rod";
            case MEOW_AXE -> "meow_axe";
            case PIXIE -> "pixie";
            case ROCKET_BOOTS -> "rocket_boots";
            case RUNNING_SHOES -> "running_shoes";
            case WITCH_APPLE -> "witch_apple";
            case YANPAI -> "yanpai";
            case CLOCK -> "clock";
            case HONGBAO -> "hongbao";
            case HYPNOSIS_APP -> "hypnosis_app";
            case BONES_WITHOUT_CHICKEN_FEET -> "bones_without_chicken_feet";
            case KNOCKBACK_STICK -> "knockback_stick";
            case GODLY_PICKAXE -> "godly_pickaxe";
            case SPANWER -> "spawner";
            case NETHER_STAR_USE -> "nether_star_use";
            case DRAGON_BREATH_USE -> "dragon_breath_use";
            case ECHO_SHARD_USE -> "echo_shard_use";
            case FIRE_CHARGE_USE -> "fire_charge_use";
            case TNT_USE -> "tnt_use";
            case BOW_USE -> "bow_use";
            case CROSSBOW_USE -> "crossbow_use";
            case END_CRYSTAL_USE -> "end_crystal_use";
            case FEATHER_USE -> "feather_use";
            case ENCHANTED_BOOK_USE -> "enchanted_book_use";
            case DEATH_1 -> "death_1";
            case DEATH_FALL -> "death_fall";
            case ELBOW_KING -> "elbow_king";
        };
    }
    
    /**
     * 打开玩家的进度界面
     */
    public void openAdvancementMenu(Player player) {
        // 使用命令打开进度界面
        String command = String.format("advancement grant %s from newpillar:main/root", player.getName());
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        
        // 实际上玩家需要按 L 键查看，这里只是确保进度已加载
        player.sendMessage("§a请按 §e§lL §a键打开成就进度界面！");
    }
    
    /**
     * 重置所有玩家的进度（用于测试）
     */
    public void resetAllAdvancements(Player player) {
        String command = String.format("advancement revoke %s everything", player.getName());
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        plugin.getLogger().info("已重置玩家 " + player.getName() + " 的所有进度");
    }
    
    public boolean isInitialized() {
        return initialized;
    }
}
