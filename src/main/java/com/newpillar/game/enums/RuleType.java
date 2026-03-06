package com.newpillar.game.enums;

public enum RuleType {
    // 规则名称和描述同步自 Lucky-Pillar 数据包
    // 颜色代码: 0=黑,1=深蓝,2=深绿,3=青色,4=深红,5=紫,6=金,7=灰,8=深灰,9=蓝,a=绿,b=青,c=红,d=粉,e=黄,f=白
    NONE(0, "无规则", "f", "没有花里胡哨的规则，只有原汁原味的幸运玩法。"),
    SMALL_CUTE(1, "小小的也很可爱❤", "d", "玩家尺寸缩小为原来的§l1/3"),
    BIG(2, "大！大！大！", "6", "玩家尺寸增大为原来的§l3/2"),
    MY_PARTNER(3, "我的伙伴", "9", "获得一只§l狐狸\n狐狸存活时,给玩家提供力量和生命回复"),
    PUNCH(4, "一击必杀！", "c", "玩家攻击伤害变成§l40"),
    INV_EXCHANGE(5, "背包交换", "a", "随机事件固定为§l背包交换"),
    VOID_MERCY(6, "虚空的仁慈", "5", "掉落虚空将被向上传送§r§l60§r格");

    private final int id;
    private final String name;
    private final String color;
    private final String description;

    private RuleType(int id, String name, String color, String description) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.description = description;
    }

    public int getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public String getColor() {
        return this.color;
    }

    public String getDescription() {
        return this.description;
    }

    public static RuleType getById(int id) {
        for (RuleType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        return NONE;
    }

    public static RuleType getRandom() {
        int id = (int) (Math.random() * 7) + 1;
        return getById(id);
    }
}
