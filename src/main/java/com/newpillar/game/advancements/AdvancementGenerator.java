package com.newpillar.game.advancements;

import com.newpillar.game.enums.AchievementType;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.newpillar.NewPillar;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * 动态生成 Minecraft 进度数据包
 * 无需手动准备数据包文件
 */
public class AdvancementGenerator {
    private final NewPillar plugin;
    private final Gson gson;
    private final Map<String, AdvancementDefinition> definitions = new HashMap<>();
    private boolean generated = false;
    
    public AdvancementGenerator(NewPillar plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }
    
    /**
     * 生成所有进度定义并保存到数据包
     */
    public void generateAdvancements() {
        if (generated) return;
        
        try {
            // 定义所有成就进度
            defineAdvancements();
            
            // 生成数据包文件
            generateDatapack();
            
            generated = true;
            plugin.getLogger().info("成就进度数据包生成完成！");
            plugin.getLogger().info("请使用 /reload 命令或重启服务器以加载进度");
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "生成进度数据包失败: " + e.getMessage(), e);
        }
    }
    
    private void defineAdvancements() {
        // 根进度 - 添加樱花树叶背景
        addAdvancement("root", "幸运之柱", "开始你的幸运之旅", 
            Material.NETHER_STAR, "task", null,
            createImpossibleTrigger(), "block/cherry_leaves");
        
        // 击杀分支根节点
        addAdvancement("kills_root", "杀戮之路", "在战斗中成长", 
            Material.IRON_SWORD, "task", "root",
            createImpossibleTrigger(), null);
        
        // 击杀成就
        addAdvancement("killed_1", "初露锋芒", "击杀 1 名玩家", 
            Material.WOODEN_SWORD, "task", "kills_root",
            createImpossibleTrigger(), null);
        addAdvancement("killed_20", "崭露头角", "击杀 20 名玩家", 
            Material.STONE_SWORD, "task", "killed_1",
            createImpossibleTrigger(), null);
        addAdvancement("killed_50", "身经百战", "击杀 50 名玩家", 
            Material.IRON_SWORD, "goal", "killed_20",
            createImpossibleTrigger(), null);
        addAdvancement("killed_100", "所向披靡", "击杀 100 名玩家", 
            Material.GOLDEN_SWORD, "goal", "killed_50",
            createImpossibleTrigger(), null);
        addAdvancement("killed_200", "战神降临", "击杀 200 名玩家", 
            Material.DIAMOND_SWORD, "challenge", "killed_100",
            createImpossibleTrigger(), null);
        
        // 胜利分支根节点
        addAdvancement("wins_root", "荣耀之路", "追求胜利", 
            Material.GOLDEN_APPLE, "task", "root",
            createImpossibleTrigger(), null);
        
        // 胜利成就
        addAdvancement("win_1", "首战告捷", "赢 1 局游戏", 
            Material.COAL, "task", "wins_root",
            createImpossibleTrigger(), null);
        addAdvancement("win_20", "常胜将军", "赢 20 局游戏", 
            Material.COPPER_INGOT, "task", "win_1",
            createImpossibleTrigger(), null);
        addAdvancement("win_50", "无敌王者", "赢 50 局游戏", 
            Material.IRON_INGOT, "goal", "win_20",
            createImpossibleTrigger(), null);
        addAdvancement("win_100", "传奇玩家", "赢 100 局游戏", 
            Material.GOLD_INGOT, "goal", "win_50",
            createImpossibleTrigger(), null);
        addAdvancement("win_200", "永恒传说", "赢 200 局游戏", 
            Material.DIAMOND, "challenge", "win_100",
            createImpossibleTrigger(), null);
        
        // 事件分支根节点
        addAdvancement("events_root", "事件挑战", "参与特殊事件", 
            Material.TNT, "task", "root",
            createImpossibleTrigger(), null);
        
        // 事件成就
        addAdvancement("king_game", "加冕为王", "披荆斩棘，血铸王冠",
            Material.DIAMOND_HELMET, "challenge", "events_root",
            createImpossibleTrigger(), null);
        addAdvancement("nuclear", "核电，轻而易举啊...", "坏了坏了",
            Material.TNT, "task", "events_root",
            createImpossibleTrigger(), null);
        addAdvancement("kingslayer", "弑君者", "在国王游戏中击杀国王",
            Material.IRON_SWORD, "goal", "king_game",
            createImpossibleTrigger(), null);
        
        // 物品分支根节点
        addAdvancement("items_root", "收集者", "收集特殊物品", 
            Material.CHEST, "task", "root",
            createImpossibleTrigger(), null);
        
        // 武器类
        addAdvancement("weapons_root", "神兵利器", "强大的武器",
            Material.DIAMOND_SWORD, "task", "items_root",
            createImpossibleTrigger(), null);
        addAdvancement("meow_axe", "喵人斧？！", "颗秒",
            Material.GOLDEN_AXE, "task", "weapons_root",
            createImpossibleTrigger(), null);
        addAdvancement("fly_mace", "让你飞起来", "略有失重感",
            Material.MACE, "task", "weapons_root",
            createImpossibleTrigger(), null);
        addAdvancement("knockback_stick", "击退棒", "bye~",
            Material.STICK, "task", "weapons_root",
            createImpossibleTrigger(), null);
        addAdvancement("godly_pickaxe", "我滴神镐", "人形挖土机",
            Material.NETHERITE_PICKAXE, "task", "weapons_root",
            createImpossibleTrigger(), null);
        addAdvancement("spear", "长♂矛", "这个世界太乱♂",
            Material.GOLDEN_SPEAR, "task", "weapons_root",
            createImpossibleTrigger(), null);
        addAdvancement("special_bow", "神弓", "这一箭，贯穿星辰",
            Material.BOW, "task", "weapons_root",
            createImpossibleTrigger(), null);
        addAdvancement("special_crossbow", "神弩", "弩，怒也，有执怒也。其柄曰臂，似人臂也。钩弦者曰牙，似齿牙也。牙外曰郭，为牙之规郭也...",
            Material.CROSSBOW, "task", "weapons_root",
            createImpossibleTrigger(), null);
        addAdvancement("life_steal_sword", "生命偷取剑", "获得生命偷取剑",
            Material.GOLDEN_SWORD, "task", "weapons_root",
            createImpossibleTrigger(), null);
        addAdvancement("poison_dagger", "剧毒匕首", "为什么不是绿的？",
            Material.STONE_SWORD, "task", "weapons_root",
            createImpossibleTrigger(), null);
        
        // 装备类
        addAdvancement("armor_root", "神奇装备", "特殊装备",
            Material.DIAMOND_CHESTPLATE, "task", "items_root",
            createImpossibleTrigger(), null);
        addAdvancement("invisible_sand", "隐身沙粒", "你看不见我",
            Material.PRISMARINE_CRYSTALS, "task", "armor_root",
            createImpossibleTrigger(), null);
        addAdvancement("rocket_boots", "火箭靴", "是二段跳！",
            Material.DIAMOND_BOOTS, "task", "armor_root",
            createImpossibleTrigger(), null);
        addAdvancement("running_shoes", "跑鞋", "飞一般的感觉",
            Material.IRON_BOOTS, "task", "armor_root",
            createImpossibleTrigger(), null);
        addAdvancement("gravity_boots", "重力靴", "获得重力靴",
            Material.NETHERITE_BOOTS, "task", "armor_root",
            createImpossibleTrigger(), null);
        addAdvancement("shield_generator", "护盾发生器", "获得护盾发生器",
            Material.SHIELD, "task", "armor_root",
            createImpossibleTrigger(), null);
        
        // 道具类
        addAdvancement("tools_root", "奇妙道具", "实用道具",
            Material.COMPASS, "task", "items_root",
            createImpossibleTrigger(), null);
        addAdvancement("bruce", "布鲁斯", "好样的,布鲁斯！",
            Material.WOLF_SPAWN_EGG, "task", "tools_root",
            createImpossibleTrigger(), null);
        addAdvancement("blue_screen", "蓝屏", "xxx~",
            Material.BLUE_DYE, "task", "tools_root",
            createImpossibleTrigger(), null);
        addAdvancement("big_flame_rod", "大火杆", "锟斤拷烫烫烫！！！",
            Material.BLAZE_ROD, "task", "tools_root",
            createImpossibleTrigger(), null);
        addAdvancement("pixie", "皮鞋", "给我檫皮鞋",
            Material.NETHERITE_BOOTS, "task", "tools_root",
            createImpossibleTrigger(), null);
        addAdvancement("clock", "时间", "我掌握了逆转时间的公式",
            Material.CLOCK, "task", "tools_root",
            createImpossibleTrigger(), null);
        addAdvancement("hongbao", "红包", "恭喜发财！",
            Material.NETHER_BRICK, "task", "tools_root",
            createImpossibleTrigger(), null);
        addAdvancement("hypnosis_app", "催眠 app", "你是谁？",
            Material.IRON_INGOT, "task", "tools_root",
            createImpossibleTrigger(), null);
        addAdvancement("bones_without_chicken_feet", "有骨无鸡爪", "好吃，嚼嚼嚼~",
            Material.BONE, "task", "tools_root",
            createImpossibleTrigger(), null);
        
        // 消耗品类
        addAdvancement("consumables_root", "消耗品", "一次性物品",
            Material.POTION, "task", "items_root",
            createImpossibleTrigger(), null);
        addAdvancement("witch_apple", "女巫的红苹果", "Good Or Bad?",
            Material.APPLE, "task", "consumables_root",
            createImpossibleTrigger(), null);
        addAdvancement("yanpai", "牌", "我要验牌",
            Material.PAPER, "task", "consumables_root",
            createImpossibleTrigger(), null);
        addAdvancement("spawner", "刷怪笼", "召唤(怪物)师",
            Material.SPAWNER, "goal", "consumables_root",
            createImpossibleTrigger(), null);
        
        // 特殊武器
        addAdvancement("excalibur", "EX咖喱棒", "常胜之王高声的念出手上奇迹的真名，那正是——誓约胜利之剑",
            Material.DIAMOND_SWORD, "challenge", "weapons_root",
            createImpossibleTrigger(), null);

        // 死亡成就分支
        addAdvancement("deaths_root", "死亡之路", "在失败中成长",
            Material.BONE, "task", "root",
            createImpossibleTrigger(), null);
        addAdvancement("death_1", "初次阵亡", "出师未捷身先死",
            Material.BONE, "task", "deaths_root",
            createImpossibleTrigger(), null);
        addAdvancement("death_fall", "摔落", "这地怎么这么滑",
            Material.LEATHER_BOOTS, "task", "deaths_root",
            createImpossibleTrigger(), null);

        // 肘击王成就
        addAdvancement("elbow_king", "肘击王", "以man!之力肘击50次孩子们",
            Material.ORANGE_WOOL, "challenge", "kills_root",
            createImpossibleTrigger(), null);
        
        // 俄罗斯轮盘枪成就
        addAdvancement("russian_roulette", "俄罗斯轮盘", "亡命之人",
            Material.IRON_HORSE_ARMOR, "task", "events_root",
            createImpossibleTrigger(), null);
        addAdvancement("russian_roulette_6", "左轮不会卡壳", "在俄罗斯轮盘游戏中选择6颗子弹并不出意外的被一枪崩死",
            Material.NETHERITE_INGOT, "challenge", "russian_roulette",
            createImpossibleTrigger(), null);
    }
    
    private void addAdvancement(String key, String title, String description, 
                                 Material icon, String frame, String parent,
                                 JsonObject trigger, String background) {
        definitions.put(key, new AdvancementDefinition(key, title, description, 
            icon, frame, parent, trigger, background));
    }
    
    private JsonObject createImpossibleTrigger() {
        JsonObject trigger = new JsonObject();
        trigger.addProperty("trigger", "minecraft:impossible");
        trigger.add("conditions", new JsonObject());
        return trigger;
    }
    
    private void generateDatapack() throws IOException {
        // 获取世界文件夹
        File worldFolder = Bukkit.getWorlds().get(0).getWorldFolder();
        File datapacksFolder = new File(worldFolder, "datapacks");
        
        // 创建数据包文件夹
        File packFolder = new File(datapacksFolder, "newpillar_advancements");
        File advancementsFolder = new File(packFolder, "data/newpillar/advancement");
        
        // 如果数据包已存在，先删除旧的文件
        if (packFolder.exists()) {
            plugin.getLogger().info("发现旧的数据包，正在清理...");
            deleteDirectory(packFolder);
        }
        
        // 重新创建文件夹
        advancementsFolder.mkdirs();
        
        // 创建 pack.mcmeta
        createPackMeta(packFolder);
        
        // 生成所有进度文件
        for (AdvancementDefinition def : definitions.values()) {
            generateAdvancementFile(advancementsFolder, def);
        }
        
        plugin.getLogger().info("数据包位置: " + packFolder.getAbsolutePath());
    }
    
    /**
     * 递归删除目录及其内容
     */
    private void deleteDirectory(File directory) {
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        directory.delete();
    }
    
    private void createPackMeta(File packFolder) throws IOException {
        JsonObject meta = new JsonObject();
        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", 61); // 1.21.4
        pack.addProperty("description", "NewPillar 成就进度数据包");
        meta.add("pack", pack);
        
        FileWriter writer = new FileWriter(new File(packFolder, "pack.mcmeta"));
        gson.toJson(meta, writer);
        writer.close();
    }
    
    private void generateAdvancementFile(File folder, AdvancementDefinition def) throws IOException {
        JsonObject advancement = new JsonObject();
        
        // 添加父进度
        if (def.parent != null) {
            advancement.addProperty("parent", "newpillar:" + def.parent);
        }
        
        // 添加显示信息
        JsonObject display = new JsonObject();
        
        JsonObject icon = new JsonObject();
        icon.addProperty("id", def.icon.getKey().toString());
        display.add("icon", icon);
        
        JsonObject title = new JsonObject();
        title.addProperty("text", def.title);
        display.add("title", title);
        
        JsonObject description = new JsonObject();
        description.addProperty("text", def.description);
        display.add("description", description);
        
        display.addProperty("frame", def.frame);
        display.addProperty("show_toast", true);
        display.addProperty("announce_to_chat", true);
        display.addProperty("hidden", false);
        
        // 添加背景材质（仅根进度）
        if (def.background != null) {
            display.addProperty("background", def.background);
        }
        
        advancement.add("display", display);
        
        // 添加触发器
        JsonObject criteria = new JsonObject();
        criteria.add("impossible", def.trigger);
        advancement.add("criteria", criteria);
        
        // 添加要求 - requirements 必须是 JSON 数组
        JsonArray requirements = new JsonArray();
        JsonArray innerArray = new JsonArray();
        innerArray.add("impossible");
        requirements.add(innerArray);
        advancement.add("requirements", requirements);
        
        // 保存文件
        FileWriter writer = new FileWriter(new File(folder, def.key + ".json"));
        gson.toJson(advancement, writer);
        writer.close();
    }
    
    /**
     * 授予玩家进度（通过命令）
     * 使用 Folia 调度器在主线程执行
     */
    public void grantAdvancement(Player player, String key) {
        if (!generated) {
            generateAdvancements();
        }
        
        // 使用 Folia 的 GlobalRegionScheduler 在主线程执行命令
        Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
            String command = String.format("advancement grant %s only newpillar:%s", 
                player.getName(), key);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        });
    }
    
    /**
     * 检查玩家是否拥有进度
     */
    public boolean hasAdvancement(Player player, String key) {
        org.bukkit.advancement.Advancement advancement = Bukkit.getAdvancement(
            new NamespacedKey(plugin, key));
        if (advancement == null) return false;
        
        return player.getAdvancementProgress(advancement).isDone();
    }
    
    /**
     * 将 AchievementType 转换为 key
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
            // 武器类
            case MEOW_AXE -> "meow_axe";
            case FLY_MACE -> "fly_mace";
            case KNOCKBACK_STICK -> "knockback_stick";
            case GODLY_PICKAXE -> "godly_pickaxe";
            case SPEAR -> "spear";
            case SPECIAL_BOW -> "special_bow";
            case SPECIAL_CROSSBOW -> "special_crossbow";
            case LIFE_STEAL_SWORD -> "life_steal_sword";
            case POISON_DAGGER -> "poison_dagger";
            // 装备类
            case INVISIBLE_SAND -> "invisible_sand";
            case ROCKET_BOOTS -> "rocket_boots";
            case RUNNING_SHOES -> "running_shoes";
            case GRAVITY_BOOTS -> "gravity_boots";
            case SHIELD_GENERATOR -> "shield_generator";
            // 道具类
            case BRUCE -> "bruce";
            case BLUE_SCREEN -> "blue_screen";
            case BIG_FLAME_ROD -> "big_flame_rod";
            case PIXIE -> "pixie";
            case CLOCK -> "clock";
            case HONGBAO -> "hongbao";
            case HYPNOSIS_APP -> "hypnosis_app";
            case BONES_WITHOUT_CHICKEN_FEET -> "bones_without_chicken_feet";
            // 消耗品类
            case WITCH_APPLE -> "witch_apple";
            case YANPAI -> "yanpai";
            case SPAWNER -> "spawner";
            // 特殊武器
            case EXCALIBUR -> "excalibur";
            // 死亡成就
            case DEATH_1 -> "death_1";
            case DEATH_FALL -> "death_fall";
            // 肘击王
            case ELBOW_KING -> "elbow_king";
            // 俄罗斯轮盘
            case RUSSIAN_ROULETTE -> "russian_roulette";
            case RUSSIAN_ROULETTE_6 -> "russian_roulette_6";
            // 缺失的成就类型
            case INVISIBLE_SCARF -> "invisible_scarf";
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
        };
    }
    
    public boolean isGenerated() {
        return generated;
    }
    
    private record AdvancementDefinition(
        String key,
        String title,
        String description,
        Material icon,
        String frame,
        String parent,
        JsonObject trigger,
        String background
    ) {}
}
