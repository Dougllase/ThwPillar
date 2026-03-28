package com.newpillar.game.enums;

import org.bukkit.Material;

public enum AchievementType {
    // 击杀成就
    KILLED_1(1, "初露锋芒", "击杀 1 名玩家", Material.WOODEN_SWORD, "task"),
    KILLED_20(2, "崭露头角", "击杀 20 名玩家", Material.STONE_SWORD, "task"),
    KILLED_50(3, "身经百战", "击杀 50 名玩家", Material.IRON_SWORD, "goal"),
    KILLED_100(4, "所向披靡", "击杀 100 名玩家", Material.GOLDEN_SWORD, "goal"),
    KILLED_200(5, "战神降临", "击杀 200 名玩家", Material.DIAMOND_SWORD, "challenge"),

    // 胜利成就
    WIN_1(6, "首战告捷", "赢 1 局游戏", Material.COAL, "task"),
    WIN_20(7, "常胜将军", "赢 20 局游戏", Material.COPPER_INGOT, "task"),
    WIN_50(8, "无敌王者", "赢 50 局游戏", Material.IRON_INGOT, "goal"),
    WIN_100(9, "传奇玩家", "赢 100 局游戏", Material.GOLD_INGOT, "goal"),
    WIN_200(10, "永恒传说", "赢 200 局游戏", Material.DIAMOND, "challenge"),

    // 事件成就
    KING_GAME(11, "加冕为王", "披荆斩棘，血铸王冠", Material.DIAMOND_HELMET, "challenge"),
    NUCLEAR(12, "核电，轻而易举啊...", "坏了坏了", Material.TNT, "task"),
    KINGSLAYER(13, "弑君者", "在国王游戏中击杀国王", Material.IRON_SWORD, "goal"),

    // 武器类物品成就
    MEOW_AXE(14, "喵人斧？！", "颗秒", Material.GOLDEN_AXE, "task"),
    FLY_MACE(15, "让你飞起来", "略有失重感", Material.MACE, "task"),
    KNOCKBACK_STICK(16, "击退棒", "bye~", Material.STICK, "task"),
    GODLY_PICKAXE(17, "我滴神镐", "人形挖土机", Material.NETHERITE_PICKAXE, "task"),
    SPEAR(18, "长♂矛", "这个世界太乱♂", Material.GOLDEN_SPEAR, "task"),
    SPECIAL_BOW(19, "神弓", "这一箭，贯穿星辰", Material.BOW, "task"),
    SPECIAL_CROSSBOW(20, "神弩", "弩，怒也，有执怒也。其柄曰臂，似人臂也。钩弦者曰牙，似齿牙也。牙外曰郭，为牙之规郭也...", Material.CROSSBOW, "task"),
    LIFE_STEAL_SWORD(21, "生命偷取剑", "获得生命偷取剑", Material.GOLDEN_SWORD, "task"),
    POISON_DAGGER(22, "剧毒匕首", "为什么不是绿的？", Material.STONE_SWORD, "task"),

    // 装备类物品成就
    INVISIBLE_SAND(23, "隐身沙粒", "你看不见我", Material.PRISMARINE_CRYSTALS, "task"),
    ROCKET_BOOTS(24, "火箭靴", "是二段跳！", Material.DIAMOND_BOOTS, "task"),
    RUNNING_SHOES(25, "跑鞋", "飞一般的感觉", Material.IRON_BOOTS, "task"),
    GRAVITY_BOOTS(26, "重力靴", "获得重力靴", Material.NETHERITE_BOOTS, "task"),
    SHIELD_GENERATOR(27, "护盾发生器", "固若金汤", Material.SHIELD, "task"),

    // 道具类物品成就
    BRUCE(28, "布鲁斯", "好样的,布鲁斯！", Material.WOLF_SPAWN_EGG, "task"),
    BLUE_SCREEN(29, "蓝屏", "xxx~", Material.BLUE_DYE, "task"),
    BIG_FLAME_ROD(30, "大火杆", "锟斤拷烫烫烫！！！", Material.BLAZE_ROD, "task"),
    PIXIE(31, "皮鞋", "给我檫皮鞋", Material.NETHERITE_BOOTS, "task"),
    CLOCK(32, "时间", "我掌握了逆转时间的公式", Material.CLOCK, "task"),
    HONGBAO(33, "红包", "恭喜发财！", Material.NETHER_BRICK, "task"),
    HYPNOSIS_APP(34, "催眠 app", "你是谁？", Material.IRON_INGOT, "task"),
    BONES_WITHOUT_CHICKEN_FEET(35, "有骨无鸡爪", "好吃，嚼嚼嚼~", Material.BONE, "task"),

    // 消耗品类物品成就
    WITCH_APPLE(36, "女巫的红苹果", "Good Or Bad?", Material.APPLE, "task"),
    YANPAI(37, "牌", "我要验牌", Material.PAPER, "task"),
    SPAWNER(38, "刷怪笼", "召唤(怪物)师", Material.SPAWNER, "goal"),

    // 特殊武器成就
    EXCALIBUR(39, "EX咖喱棒", "常胜之王高声的念出手上奇迹的真名，那正是——誓约胜利之剑", Material.DIAMOND_SWORD, "challenge"),

    // 死亡成就
    DEATH_1(40, "初次阵亡", "出师未捷身先死", Material.BONE, "task"),
    DEATH_FALL(41, "摔落", "这地怎么这么滑", Material.LEATHER_BOOTS, "task"),

    // 肘击王成就
    ELBOW_KING(42, "肘击王", "以man!之力肘击50次孩子们", Material.ORANGE_WOOL, "challenge"),

    // 俄罗斯轮盘枪成就
    RUSSIAN_ROULETTE(43, "俄罗斯轮盘", "亡命之人", Material.IRON_HORSE_ARMOR, "task"),
    RUSSIAN_ROULETTE_6(44, "左轮不会卡壳", "在俄罗斯轮盘游戏中选择6颗子弹并不出意外的被一枪崩死", Material.NETHERITE_INGOT, "challenge"),

    // 缺失的成就类型（用于代码编译）
    INVISIBLE_SCARF(45, "隐身围巾", "获得隐身围巾", Material.WHITE_WOOL, "task"),
    NETHER_STAR_USE(46, "下界之星", "使用下界之星", Material.NETHER_STAR, "task"),
    DRAGON_BREATH_USE(47, "龙息", "使用龙息", Material.DRAGON_BREATH, "task"),
    ECHO_SHARD_USE(48, "回响碎片", "使用回响碎片", Material.ECHO_SHARD, "task"),
    FIRE_CHARGE_USE(49, "火焰弹", "使用火焰弹", Material.FIRE_CHARGE, "task"),
    TNT_USE(50, "TNT", "使用TNT", Material.TNT, "task"),
    BOW_USE(51, "弓", "使用弓", Material.BOW, "task"),
    CROSSBOW_USE(52, "弩", "使用弩", Material.CROSSBOW, "task"),
    END_CRYSTAL_USE(53, "末地水晶", "使用末地水晶", Material.END_CRYSTAL, "task"),
    FEATHER_USE(54, "羽毛", "使用羽毛", Material.FEATHER, "task"),
    ENCHANTED_BOOK_USE(55, "附魔书", "使用附魔书", Material.ENCHANTED_BOOK, "task");

    private final int id;
    private final String title;
    private final String description;
    private final Material icon;
    private final String frame;

    AchievementType(int id, String title, String description, Material icon, String frame) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.icon = icon;
        this.frame = frame;
    }

    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Material getIcon() { return icon; }
    public String getFrame() { return frame; }

    public static AchievementType getById(int id) {
        for (AchievementType type : values()) {
            if (type.id == id) return type;
        }
        return null;
    }
}
