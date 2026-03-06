package com.newpillar.game.enums;

public enum EventType {
   // 事件名称、描述和颜色同步自 Lucky-Pillar 数据包
   // 注意：只包含插件已实现的事件
   // 颜色代码对应数据包中的 color1 (标题颜色) 和 color2 (副标题颜色)
   NIGHT_FALL(1, "夜晚降临", "§0黑暗里有点东西", 30, "§0", "§f"),
   FALLING_ANVIL(2, "铁砧下落", "§8注意头顶！", 20, "§8", "§f"),
   WITHER(3, "凋灵", "§k☠☠☠", 30, "§c", "§f"),
   FLY(4, "FLY", "§b自由的风", 20, "§9", "§b"),
   RAIN(5, "箭雨", "§5弓箭手！放箭！", 10, "§5", "§f"),
   SKY_WALKER(6, "踏空", "§4恐怖如斯", 20, "§b", "§4"),
   ROTATION(7, "自转", "§3oiiaioooooiai", 10, "§3", "§f"),
   LIGHTNING(8, "雷击", "§6我的避雷针呢？", 20, "§6", "§f"),
   HELLO_WORLD(9, "你好,世界", "§aHello World!", 10, "§a", "§f"),
   UNDEAD(10, "「不死」", "§6+1 life", 15, "§6", "§f"),
   BROKEN_LEG(11, "断腿", "§f", 20, "§c", "§f"),
   PUNCH(12, "一击必杀", "§eONE PUNCH!!!", 15, "§e", "§f"),
   CREEPER(13, "CREEPER", "§fCreeper?", 20, "§f", "§f"),
   TOUCH(14, "摸摸", "§d摸得更远", 15, "§d", "§f"),
   INV_EXCHANGE(15, "背包交换", "§a这何尝不是一种NTR", 15, "§a", "§f"),
   KING_GAME(16, "国王游戏", "§6成王败寇", 60, "§6", "§f"),
   LUCKY_DOLL(17, "幸运玩偶", "§6qvq", 15, "§6", "§f"),
   HUNGRY(18, "饿啊饿啊", "§2eaea", 30, "§2", "§f"),
   BLACK(19, "黑", "§f真的黑", 9, "§f", "§f"),
   SPEED(20, "Speed", "§b♿冲刺冲刺♿", 20, "§b", "§b"),
   MINI(21, "迷你化", "§c> <", 20, "§c", "§f"),
   HUGE(22, "巨大化", "§9= =", 20, "§9", "§f"),
   NUCLEAR(23, "核电", "§2核电，轻而易举", 25, "§2", "§f"),
   GREEDY_SLIME(24, "贪吃的史莱姆", "§a嚼嚼嚼~", 20, "§a", "§f"),
   LOCATION_EXCHANGE(25, "位置交换", "§e给我干哪来了", 15, "§e", "§f"),
   LAVA_RISE(26, "岩浆上升", "§c往上走！", 30, "§c", "§f"),
   LOOK_AT_ME(27, "看我看我", "§e全体目光向我看齐！", 15, "§e", "§f"),
   FIRED(28, "我火了", "§c物理上的", 15, "§c", "§f"),
   KEY_INVERSION(29, "键位反转", "§5WASD反转了", 20, "§5", "§f"),
   ALWAYS_EXPLODE(30, "不是怎么老被炸呀", "§c我的假牙！", 0, "§c", "§f"),
   NOTHING_31(31, "无事发生", "§a无事发生...", 0, "§a", "§a"),
   NOTHING_32(32, "无事发生", "§a无事发生...", 0, "§a", "§f"),
   NOTHING_33(33, "无事发生", "§a无事发生...", 0, "§a", "§f"),
   NOTHING_34(34, "无事发生", "§a无事发生...", 0, "§a", "§f"),
   NOTHING_35(35, "无事发生", "§a无事发生...", 0, "§a", "§f");

   private final int id;
   private final String name;
   private final String description;
   private final int duration;
   private final String titleColor;
   private final String descColor;

   private EventType(int id, String name, String description, int duration, String titleColor, String descColor) {
      this.id = id;
      this.name = name;
      this.description = description;
      this.duration = duration;
      this.titleColor = titleColor;
      this.descColor = descColor;
   }

   public int getId() {
      return this.id;
   }

   public String getName() {
      return this.name;
   }

   public String getDescription() {
      return this.description;
   }

   public int getDuration() {
      return this.duration;
   }

   public String getTitleColor() {
      return this.titleColor;
   }

   public String getDescColor() {
      return this.descColor;
   }

   /**
    * 获取带颜色的标题文本
    */
   public String getColoredTitle() {
      return this.titleColor + this.name;
   }

   /**
    * 获取带颜色的描述文本
    */
   public String getColoredDescription() {
      return this.descColor + this.name;
   }

   public static EventType getById(int id) {
      for (EventType type : values()) {
         if (type.id == id) {
            return type;
         }
      }

      return NOTHING_32;
   }

   public static EventType getByName(String name) {
      for (EventType type : values()) {
         if (type.name().equalsIgnoreCase(name)) {
            return type;
         }
      }

      return null;
   }

   public boolean isRealEvent() {
      return this.id < 32;
   }
}
