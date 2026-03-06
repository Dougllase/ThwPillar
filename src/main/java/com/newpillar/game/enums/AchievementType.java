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
    WIN_1(6, "首战告捷", "获得 1 次胜利", Material.OAK_SAPLING, "task"),
    WIN_20(7, "常胜将军", "获得 20 次胜利", Material.APPLE, "task"),
    WIN_50(8, "无敌王者", "获得 50 次胜利", Material.GOLDEN_APPLE, "goal"),
    WIN_100(9, "传奇玩家", "获得 100 次胜利", Material.ENCHANTED_GOLDEN_APPLE, "goal"),
    WIN_200(10, "永恒传说", "获得 200 次胜利", Material.NETHER_STAR, "challenge"),
    
    // 事件成就
    KING_GAME(11, "加冕为王", "在国王游戏中存活并成为真正的国王", Material.GOLDEN_HELMET, "task"),
    NUCLEAR(12, "核平使者", "在核爆事件中幸存", Material.TNT, "task"),
    KINGSLAYER(13, "弑君者", "在国王游戏中击杀国王", Material.DIAMOND_SWORD, "goal"),
    
    // 物品成就
    BRUCE(14, "Bruce!", "获得布鲁斯之力", Material.BLAZE_POWDER, "task"),
    BLUE_SCREEN(15, "蓝屏警告", "获得蓝屏", Material.BLUE_DYE, "task"),
    FLY_MACE(16, "流星锤", "获得流星锤", Material.MACE, "task"),
    INVISIBLE_SCARF(18, "隐形围巾", "获得隐形围巾", Material.WHITE_WOOL, "task"),
    BIG_FLAME_ROD(19, "大火杆", "获得大火杆", Material.BLAZE_ROD, "task"),
    MEOW_AXE(20, "喵喵斧", "获得喵喵斧", Material.IRON_AXE, "task"),
    PIXIE(21, "皮鞋", "获得皮鞋", Material.FEATHER, "task"),
    ROCKET_BOOTS(22, "火箭靴", "获得火箭靴", Material.LEATHER_BOOTS, "task"),
    RUNNING_SHOES(23, "跑鞋", "获得跑鞋", Material.GOLDEN_BOOTS, "task"),
    WITCH_APPLE(24, "女巫苹果", "获得女巫苹果", Material.APPLE, "task"),
    YANPAI(25, "验牌", "获得验牌", Material.FIRE_CHARGE, "task"),
    CLOCK(26, "时钟", "获得时钟", Material.CLOCK, "task"),
    HONGBAO(27, "红包", "获得红包", Material.PAPER, "task"),
    HYPNOSIS_APP(28, "催眠APP", "获得催眠APP", Material.ENDER_EYE, "task"),
    BONES_WITHOUT_CHICKEN_FEET(29, "无鸡脚", "获得无鸡脚", Material.BONE, "task"),
    KNOCKBACK_STICK(30, "击退棒", "获得击退棒", Material.STICK, "task"),
    GODLY_PICKAXE(31, "神镐", "获得神镐", Material.DIAMOND_PICKAXE, "task"),
    SPANWER(32, "刷怪笼", "获得刷怪笼", Material.SPAWNER, "goal"),
    NETHER_STAR_USE(33, "下界之星", "使用下界之星", Material.NETHER_STAR, "task"),
    DRAGON_BREATH_USE(34, "龙息", "使用龙息", Material.DRAGON_BREATH, "task"),
    ECHO_SHARD_USE(35, "回响碎片", "使用回响碎片", Material.ECHO_SHARD, "task"),
    FIRE_CHARGE_USE(36, "火焰弹", "使用火焰弹", Material.FIRE_CHARGE, "task"),
    TNT_USE(37, "TNT", "使用TNT", Material.TNT, "task"),
    BOW_USE(38, "神弓", "获得神弓", Material.BOW, "task"),
    CROSSBOW_USE(39, "神弩", "获得神弩", Material.CROSSBOW, "task"),
    END_CRYSTAL_USE(40, "末地水晶", "使用末地水晶", Material.END_CRYSTAL, "goal"),
    FEATHER_USE(41, "羽毛", "使用羽毛", Material.FEATHER, "task"),
    ENCHANTED_BOOK_USE(42, "附魔书", "使用附魔书", Material.BOOK, "task"),

    // 死亡成就
    DEATH_1(43, "初次阵亡", "死亡 1 次", Material.BONE, "task"),
    DEATH_FALL(44, "摔落", "摔死", Material.LEATHER_BOOTS, "task"),

    // 肘击王成就
    ELBOW_KING(45, "肘击王", "以man!之力肘击50次孩子们", Material.ORANGE_WOOL, "challenge");

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
