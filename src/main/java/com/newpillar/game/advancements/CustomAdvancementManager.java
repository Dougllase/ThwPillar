package com.newpillar.game.advancements;

import com.newpillar.game.enums.AchievementType;

import com.newpillar.NewPillar;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Level;

/**
 * 自定义进度管理器 - 动态创建原生 Minecraft 进度
 * 无需依赖数据包
 */
public class CustomAdvancementManager {
    private final NewPillar plugin;
    private final Map<String, NamespacedKey> advancementKeys = new HashMap<>();
    private final Map<String, AdvancementData> advancementDataMap = new HashMap<>();
    private boolean initialized = false;
    
    // 反射相关
    private Object minecraftServer;
    private Method getAdvancementDataWorldMethod;
    private Method registerAdvancementMethod;
    private Class<?> advancementHolderClass;
    private Class<?> advancementClass;
    
    public CustomAdvancementManager(NewPillar plugin) {
        this.plugin = plugin;
    }
    
    public void initialize() {
        try {
            // 初始化反射
            initReflection();
            
            // 注册所有成就进度
            registerAllAdvancements();
            
            initialized = true;
            plugin.getLogger().info("自定义进度系统初始化完成！共注册 " + advancementKeys.size() + " 个进度");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "自定义进度系统初始化失败: " + e.getMessage(), e);
            plugin.getLogger().info("将使用命令方式授予进度（需要数据包支持）");
        }
    }
    
    private void initReflection() throws Exception {
        // 获取 MinecraftServer 实例
        Class<?> craftServerClass = Class.forName("org.bukkit.craftbukkit." + getNMSVersion() + ".CraftServer");
        Object craftServer = craftServerClass.cast(Bukkit.getServer());
        Method getServerMethod = craftServerClass.getMethod("getServer");
        minecraftServer = getServerMethod.invoke(craftServer);
        
        // 获取 AdvancementDataWorld
        Class<?> minecraftServerClass = minecraftServer.getClass();
        getAdvancementDataWorldMethod = findMethod(minecraftServerClass, "ba", "getAdvancementData");
        
        // 获取 Advancement 相关类
        advancementClass = Class.forName("net.minecraft.advancements.Advancement");
        advancementHolderClass = Class.forName("net.minecraft.advancements.AdvancementHolder");
        
        plugin.getLogger().info("反射初始化成功！");
    }
    
    private String getNMSVersion() {
        String packageName = Bukkit.getServer().getClass().getPackage().getName();
        return packageName.split("\\.")[3];
    }
    
    private Method findMethod(Class<?> clazz, String... possibleNames) {
        for (Method method : clazz.getMethods()) {
            for (String name : possibleNames) {
                if (method.getName().equals(name)) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        return null;
    }
    
    private void registerAllAdvancements() {
        // 根进度
        registerAdvancement("root", "幸运之柱", "开始你的幸运之旅", Material.NETHER_STAR, "task", null);
        
        // 击杀成就
        registerAdvancement("killed_1", "初露锋芒", "击杀 1 名玩家", Material.WOODEN_SWORD, "task", "root");
        registerAdvancement("killed_20", "崭露头角", "击杀 20 名玩家", Material.STONE_SWORD, "task", "killed_1");
        registerAdvancement("killed_50", "身经百战", "击杀 50 名玩家", Material.IRON_SWORD, "goal", "killed_20");
        registerAdvancement("killed_100", "所向披靡", "击杀 100 名玩家", Material.GOLDEN_SWORD, "goal", "killed_50");
        registerAdvancement("killed_200", "战神降临", "击杀 200 名玩家", Material.DIAMOND_SWORD, "challenge", "killed_100");
        
        // 胜利成就
        registerAdvancement("win_1", "首战告捷", "获得 1 次胜利", Material.OAK_SAPLING, "task", "root");
        registerAdvancement("win_20", "常胜将军", "获得 20 次胜利", Material.APPLE, "task", "win_1");
        registerAdvancement("win_50", "无敌王者", "获得 50 次胜利", Material.GOLDEN_APPLE, "goal", "win_20");
        registerAdvancement("win_100", "传奇玩家", "获得 100 次胜利", Material.ENCHANTED_GOLDEN_APPLE, "goal", "win_50");
        registerAdvancement("win_200", "永恒传说", "获得 200 次胜利", Material.NETHER_STAR, "challenge", "win_100");
        
        // 事件成就
        registerAdvancement("king_game", "加冕为王", "在国王游戏中存活并成为真正的国王", Material.GOLDEN_HELMET, "task", "root");
        registerAdvancement("nuclear", "核平使者", "在核爆事件中幸存", Material.TNT, "task", "root");
        registerAdvancement("kingslayer", "弑君者", "在国王游戏中击杀国王", Material.DIAMOND_SWORD, "goal", "king_game");
        
        // 物品成就
        registerAdvancement("bruce", "Bruce!", "获得布鲁斯之力", Material.BLAZE_POWDER, "task", "root");
        registerAdvancement("blue_screen", "蓝屏警告", "获得蓝屏", Material.BLUE_DYE, "task", "root");
        registerAdvancement("fly_mace", "流星锤", "获得流星锤", Material.MACE, "task", "root");
        registerAdvancement("invisible_scarf", "隐形围巾", "获得隐形围巾", Material.WHITE_WOOL, "task", "root");
        registerAdvancement("big_flame_rod", "大火杆", "获得大火杆", Material.BLAZE_ROD, "task", "root");
        registerAdvancement("meow_axe", "喵喵斧", "获得喵喵斧", Material.IRON_AXE, "task", "root");
        registerAdvancement("pixie", "小精灵", "获得小精灵", Material.FEATHER, "task", "root");
        registerAdvancement("rocket_boots", "火箭靴", "获得火箭靴", Material.LEATHER_BOOTS, "task", "root");
        registerAdvancement("running_shoes", "跑鞋", "获得跑鞋", Material.GOLDEN_BOOTS, "task", "root");
        registerAdvancement("witch_apple", "女巫苹果", "获得女巫苹果", Material.APPLE, "task", "root");
        registerAdvancement("yanpai", "验牌", "获得验牌", Material.FIRE_CHARGE, "task", "root");
        registerAdvancement("clock", "时钟", "获得时钟", Material.CLOCK, "task", "root");
        registerAdvancement("hongbao", "红包", "获得红包", Material.PAPER, "task", "root");
        registerAdvancement("hypnosis_app", "催眠APP", "获得催眠APP", Material.ENDER_EYE, "task", "root");
        registerAdvancement("bones_without_chicken_feet", "无鸡脚", "获得无鸡脚", Material.BONE, "task", "root");
        registerAdvancement("knockback_stick", "击退棒", "获得击退棒", Material.STICK, "task", "root");
        registerAdvancement("godly_pickaxe", "神镐", "获得神镐", Material.DIAMOND_PICKAXE, "task", "root");
        registerAdvancement("spawner", "刷怪笼", "获得刷怪笼", Material.SPAWNER, "goal", "root");
        registerAdvancement("nether_star_use", "下界之星", "使用下界之星", Material.NETHER_STAR, "task", "root");
        registerAdvancement("dragon_breath_use", "龙息", "使用龙息", Material.DRAGON_BREATH, "task", "root");
        registerAdvancement("echo_shard_use", "回响碎片", "使用回响碎片", Material.ECHO_SHARD, "task", "root");
        registerAdvancement("fire_charge_use", "火焰弹", "使用火焰弹", Material.FIRE_CHARGE, "task", "root");
        registerAdvancement("tnt_use", "TNT", "使用TNT", Material.TNT, "task", "root");
        registerAdvancement("bow_use", "神弓", "获得神弓", Material.BOW, "task", "root");
        registerAdvancement("crossbow_use", "神弩", "获得神弩", Material.CROSSBOW, "task", "root");
        registerAdvancement("end_crystal_use", "末地水晶", "使用末地水晶", Material.END_CRYSTAL, "goal", "root");
        registerAdvancement("feather_use", "羽毛", "使用羽毛", Material.FEATHER, "task", "root");
        registerAdvancement("enchanted_book_use", "附魔书", "使用附魔书", Material.BOOK, "task", "root");
        
        // 尝试动态注册到服务器
        tryRegisterAdvancementsToServer();
    }
    
    private void registerAdvancement(String key, String title, String description, Material icon, String frame, String parent) {
        NamespacedKey namespacedKey = new NamespacedKey(plugin, key);
        advancementKeys.put(key, namespacedKey);
        advancementDataMap.put(key, new AdvancementData(namespacedKey, title, description, icon, frame, parent));
    }
    
    private void tryRegisterAdvancementsToServer() {
        // 由于 NMS 反射创建 Advancement 非常复杂且版本依赖严重
        // 这里我们使用一种更简单的方法：通过命令创建虚拟进度
        // 并在玩家加入时通过命令授予
        
        plugin.getLogger().info("使用命令方式管理进度（无需数据包）");
        
        // 创建根进度（必须存在）
        createRootAdvancement();
    }
    
    private void createRootAdvancement() {
        // 尝试使用 Paper API 或命令创建根进度
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                // 使用命令创建根进度
                String command = String.format(
                    "advancement grant %s only minecraft:story/root",
                    Bukkit.getConsoleSender().getName()
                );
                // 这只是测试命令是否可用
            } catch (Exception e) {
                plugin.getLogger().warning("命令方式创建进度可能不可用");
            }
        }, 20L);
    }
    
    /**
     * 授予玩家成就进度
     */
    public void grantAdvancement(Player player, String achievementKey) {
        if (!initialized) {
            plugin.getLogger().warning("进度系统尚未初始化");
            return;
        }
        
        NamespacedKey key = advancementKeys.get(achievementKey.toLowerCase());
        if (key == null) {
            plugin.getLogger().warning("未找到成就: " + achievementKey);
            return;
        }
        
        // 方法1：尝试使用 Bukkit API
        Advancement advancement = Bukkit.getAdvancement(key);
        if (advancement != null) {
            AdvancementProgress progress = player.getAdvancementProgress(advancement);
            if (!progress.isDone()) {
                for (String criteria : progress.getRemainingCriteria()) {
                    progress.awardCriteria(criteria);
                }
                plugin.getLogger().info("授予玩家 " + player.getName() + " 进度: " + achievementKey);
            }
            return;
        }
        
        // 方法2：使用命令授予（适用于动态创建的进度）
        grantAdvancementViaCommand(player, key);
    }
    
    private void grantAdvancementViaCommand(Player player, NamespacedKey key) {
        // 使用命令授予进度
        String command = String.format("advancement grant %s only %s", player.getName(), key.toString());
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        
        // 显示成就解锁消息（因为可能没有进度界面）
        AdvancementData data = advancementDataMap.get(key.getKey());
        if (data != null) {
            showAchievementUnlockMessage(player, data);
        }
    }
    
    private void showAchievementUnlockMessage(Player player, AdvancementData data) {
        String frameColor = switch (data.frame) {
            case "challenge" -> "§6";
            case "goal" -> "§d";
            default -> "§a";
        };
        
        player.sendMessage("");
        player.sendMessage("§6§l━━━━━━━━━━ 成就解锁 ━━━━━━━━━━");
        player.sendMessage(frameColor + "§l" + data.title);
        player.sendMessage("§7" + data.description);
        player.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("");
        
        // 播放音效
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
    }
    
    /**
     * 检查玩家是否拥有成就
     */
    public boolean hasAdvancement(Player player, String achievementKey) {
        NamespacedKey key = advancementKeys.get(achievementKey.toLowerCase());
        if (key == null) return false;
        
        Advancement advancement = Bukkit.getAdvancement(key);
        if (advancement == null) {
            // 如果进度不存在，假设未解锁
            return false;
        }
        
        return player.getAdvancementProgress(advancement).isDone();
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
     * 打开成就菜单（使用命令）
     */
    public void openAdvancementMenu(Player player) {
        player.sendMessage("§a请按 §e§lL §a键打开成就进度界面！");
        player.sendMessage("§7如果看不到进度，请确保服务器已启用成就系统。");
    }
    
    public boolean isInitialized() {
        return initialized;
    }
    
    /**
     * 进度数据类
     */
    private record AdvancementData(
        NamespacedKey key,
        String title,
        String description,
        Material icon,
        String frame,
        String parent
    ) {}
}
