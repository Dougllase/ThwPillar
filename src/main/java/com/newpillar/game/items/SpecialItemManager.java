package com.newpillar.game.items;

import com.newpillar.NewPillar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SpecialItemManager {
    private final NewPillar plugin;
    private final NamespacedKey itemKey;

    public SpecialItemManager(NewPillar plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "special_item");
    }

    public enum SpecialItemType {
        // 攻击类
        KNOCKBACK_STICK("knockback_stick", "击退棒", Material.STICK, "攻击类"),
        SPEAR("spear", "长♂矛", Material.GOLDEN_SPEAR, "攻击类"),
        BONES_WITHOUT_CHICKEN_FEET("bones_without_chicken_feet", "有骨无鸡爪", Material.BONE, "攻击类"),
        IRON_SWORD("iron_sword", "铁剑", Material.IRON_SWORD, "攻击类"),
        CARD("yanpai", "牌", Material.PAPER, "攻击类"),
        MEOW_AXE("meow_axe", "喵人斧", Material.GOLDEN_AXE, "攻击类"),
        BIG_FLAME_ROD("big_flame_rod", "大火杆", Material.BLAZE_ROD, "攻击类"),
        FLY_MACE("fly_mace", "让你飞起来", Material.MACE, "攻击类"),
        SPECIAL_BOW("special_bow", "神弓", Material.BOW, "攻击类"),
        SPECIAL_CROSSBOW("special_crossbow", "神弩", Material.CROSSBOW, "攻击类"),
        LIFE_STEAL_SWORD("life_steal_sword", "生命偷取剑", Material.GOLDEN_SWORD, "攻击类"),
        POISON_DAGGER("poison_dagger", "剧毒匕首", Material.IRON_SWORD, "攻击类"),

        // 辅助类
        FEATHER("feather", "羽毛", Material.FEATHER, "辅助类"),
        INVISIBLE_SAND("invisible_sand", "隐身沙粒", Material.PRISMARINE_CRYSTALS, "辅助类"),
        PIXIE("pixie", "皮鞋", Material.LEATHER_BOOTS, "辅助类"),
        ROCKET_BOOTS("rocket_boots", "火箭靴", Material.DIAMOND_BOOTS, "辅助类"),
        RUNNING_SHOES("running_shoes", "跑鞋", Material.IRON_BOOTS, "辅助类"),
        GRAVITY_BOOTS("gravity_boots", "重力靴", Material.NETHERITE_BOOTS, "辅助类"),
        SHIELD_GENERATOR("shield_generator", "护盾发生器", Material.SHIELD, "辅助类"),

        // 特殊类
        WITCH_APPLE("witch_apple", "女巫的红苹果", Material.APPLE, "特殊类"),
        BRUCE("bruce", "布鲁斯", Material.WOLF_SPAWN_EGG, "特殊类"),
        GODLY_PICKAXE("godly_pickaxe", "我滴神镐", Material.NETHERITE_PICKAXE, "特殊类"),
        CLOCK("clock", "时间", Material.CLOCK, "特殊类"),
        BLUE_SCREEN("blue_screen", "蓝屏", Material.BLUE_DYE, "特殊类"),
        HONGBAO("hongbao", "红包", Material.NETHER_BRICK, "特殊类"),
        HYPNOSIS_APP("hypnosis_app", "催眠 app", Material.IRON_INGOT, "特殊类"),
        EX_CURRY_STICK("ex_curry_stick", "『EX咖喱棒』", Material.DIAMOND_SWORD, "特殊类"),
        SPAWNER("spawner", "刷怪笼", Material.SPAWNER, "特殊类"),
        THE_WORLD("the_world", "砸瓦鲁多", Material.BELL, "特殊类");

        private final String id;
        private final String displayName;
        private final Material material;
        private final String category;

        SpecialItemType(String id, String displayName, Material material, String category) {
            this.id = id;
            this.displayName = displayName;
            this.material = material;
            this.category = category;
        }

        public String getId() { return id; }
        public String getDisplayName() { return displayName; }
        public Material getMaterial() { return material; }
        public String getCategory() { return category; }
    }

    public ItemStack createSpecialItem(SpecialItemType type) {
        ItemStack item = new ItemStack(type.getMaterial());
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // 设置显示名称 - 金色加粗
            meta.displayName(Component.text(type.getDisplayName())
                    .color(TextColor.color(0xFFD700))
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));

            // 设置Lore
            List<Component> lore = new ArrayList<>();

            // 根据物品类型添加特殊说明
            switch (type) {
                case KNOCKBACK_STICK -> {
                    // 击退棒 - 只有附魔，没有Lore
                    meta.addEnchant(Enchantment.KNOCKBACK, 4, true);
                }
                case SPEAR -> {
                    // 长♂矛 - 锋利5，主手+3实体交互距离，显示附魔和属性
                    // 添加锋利5附魔
                    meta.addEnchant(Enchantment.SHARPNESS, 5, true);
                    // 添加原版突进附魔 (LUNGE)
                    meta.addEnchant(Enchantment.LUNGE, 3, true);
                    // 主手+3实体交互距离
                    meta.addAttributeModifier(Attribute.ENTITY_INTERACTION_RANGE,
                            new AttributeModifier(UUID.randomUUID(), "spear_range", 3.0,
                                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
                    // 不添加HIDE_ENCHANTS和HIDE_ATTRIBUTES，让附魔和属性显示出来
                }
                case BONES_WITHOUT_CHICKEN_FEET -> {
                    // 有骨无鸡爪
                    lore.add(Component.text("食用回复").color(TextColor.color(0xAAAAAA))
                            .append(Component.text("❤x1").color(TextColor.color(0xFF5555))));
                }
                case IRON_SWORD -> {
                    // 铁剑 - 1.7.10风格
                    lore.add(Component.text("那是一个黄金时代——").color(TextColor.color(0xAAAAAA))
                            .append(Component.text("1.7.10").color(TextColor.color(0xFFD700)).decorate(TextDecoration.BOLD)));
                    // 攻击伤害 6
                    meta.addAttributeModifier(Attribute.ATTACK_DAMAGE,
                            new AttributeModifier(UUID.randomUUID(), "iron_sword_damage", 6.0,
                                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
                    // 攻击速度 114514
                    meta.addAttributeModifier(Attribute.ATTACK_SPEED,
                            new AttributeModifier(UUID.randomUUID(), "iron_sword_speed", 114514.0,
                                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
                }
                case CARD -> {
                    // 牌 - "我要验牌"
                    lore.add(Component.text("我要验牌！").color(TextColor.color(0xFFD700)).decorate(TextDecoration.BOLD));
                    lore.add(Component.empty());
                    lore.add(Component.text("右键使用，击退周围玩家").color(TextColor.color(0xAAAAAA)));
                }
                case MEOW_AXE -> {
                    // 喵人斧 - 耐久只有2
                    lore.add(Component.text("哈~").color(TextColor.color(0xAAAAAA)));
                    lore.add(Component.empty());
                    lore.add(Component.text("极易损坏").color(TextColor.color(0xFF5555)));
                    // 攻击伤害 99
                    meta.addAttributeModifier(Attribute.ATTACK_DAMAGE,
                            new AttributeModifier(UUID.randomUUID(), "meow_axe_damage", 99.0,
                                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
                }
                case BIG_FLAME_ROD -> {
                    // 大火杆 - 玩梗：早期MC中文翻译错误
                    lore.add(Component.text("大火杆").color(TextColor.color(0xFF5555)));
                    lore.add(Component.empty());
                    // 实体交互距离 +4
                    meta.addAttributeModifier(Attribute.ENTITY_INTERACTION_RANGE,
                            new AttributeModifier(UUID.randomUUID(), "big_fire_stick_entity", 4.0,
                                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
                    // 方块交互距离 +4
                    meta.addAttributeModifier(Attribute.BLOCK_INTERACTION_RANGE,
                            new AttributeModifier(UUID.randomUUID(), "big_fire_stick_block", 4.0,
                                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
                }
                case FLY_MACE -> {
                    // 让你飞起来
                    lore.add(Component.text("右键起飞").color(TextColor.color(0xAAAAAA)));
                    lore.add(Component.empty());
                    lore.add(Component.text("冷却时间：").color(TextColor.color(0x55FFFF))
                            .append(Component.text("5s").color(TextColor.color(0xFFD700))));
                    // 安全掉落距离 +2
                    meta.addAttributeModifier(Attribute.SAFE_FALL_DISTANCE,
                            new AttributeModifier(UUID.randomUUID(), "fly_mace_fall", 2.0,
                                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
                }
                case FEATHER -> {
                    // 羽毛
                    lore.add(Component.text("羽毛").color(TextColor.color(0xFF5555)));
                    lore.add(Component.empty());
                    lore.add(Component.text("主手持有获得缓降效果").color(TextColor.color(0xAAAAAA)));
                }
                case INVISIBLE_SAND -> {
                    // 隐身沙粒 - 原隐身围巾，材质海晶沙粒
                    lore.add(Component.text("获得").color(TextColor.color(0xAAAAAA))
                            .append(Component.text("隐身").color(TextColor.color(0x55FFFF)))
                            .append(Component.text("效果").color(TextColor.color(0xAAAAAA))));
                    lore.add(Component.text("放在").color(TextColor.color(0xAAAAAA))
                            .append(Component.text("快捷栏").color(TextColor.color(0xFFD700)))
                            .append(Component.text("生效").color(TextColor.color(0xAAAAAA))));
                }
                case PIXIE -> {
                    // 皮鞋
                    lore.add(Component.text("横扫发租界，驰名中外，号称从未输过的发国赌神，皮炎特松！").color(TextColor.color(0xFFD700)));
                }
                case ROCKET_BOOTS -> {
                    // 火箭靴
                    lore.add(Component.text("空中按shift能够触发").color(TextColor.color(0xAAAAAA))
                            .append(Component.text("立体弹射模块").color(TextColor.color(0xFFD700))));
                    // 安全掉落距离 +3
                    meta.addAttributeModifier(Attribute.SAFE_FALL_DISTANCE,
                            new AttributeModifier(UUID.randomUUID(), "rocket_boots_fall", 3.0,
                                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.FEET));
                }
                case RUNNING_SHOES -> {
                    // 跑鞋
                    lore.add(Component.text("移动速度 +5%").color(TextColor.color(0x55FF55)));
                    lore.add(Component.empty());
                    lore.add(Component.text("被动效果：穿戴时自动生效").color(TextColor.color(0xAAAAAA)));
                    meta.addAttributeModifier(Attribute.MOVEMENT_SPEED,
                            new AttributeModifier(UUID.randomUUID(), "running_shoes_speed", 0.05,
                                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.FEET));
                }
                case WITCH_APPLE -> {
                    // 女巫的红苹果 - 同步数据包属性
                    meta.setEnchantmentGlintOverride(true);
                    // 注意：食物属性需要通过其他方式设置，ItemMeta 不直接支持
                }
                case BRUCE -> {
                    // 布鲁斯
                    lore.add(Component.text("布鲁斯").color(TextColor.color(0xFF5555)));
                    lore.add(Component.empty());
                    lore.add(Component.text("右键召唤一只狼").color(TextColor.color(0xFFFF55)));
                }
                case GODLY_PICKAXE -> {
                    // 我滴神镐
                    meta.addEnchant(Enchantment.UNBREAKING, 3, true);
                    meta.addEnchant(Enchantment.EFFICIENCY, 5, true);
                    meta.addEnchant(Enchantment.FORTUNE, 3, true);
                    meta.addEnchant(Enchantment.MENDING, 1, true);
                }
                case CLOCK -> {
                    // 时间
                    lore.add(Component.text("").color(TextColor.color(0xAAAAAA)));
                    lore.add(Component.empty());
                    lore.add(Component.text("右键切换白天/夜晚").color(TextColor.color(0xFFFF55)));
                }
                case BLUE_SCREEN -> {
                    // 蓝屏 - 材质蓝色染料
                    lore.add(Component.text("蓝屏！").color(TextColor.color(0x5555FF)).decorate(TextDecoration.BOLD));
                    lore.add(Component.empty());
                    lore.add(Component.text("右键能够让某个你身边的人的电脑蓝屏哦～（并非真的）").color(TextColor.color(0xAAAAAA)));
                }
                case HONGBAO -> {
                    // 红包 - 材质地狱砖
                    lore.add(Component.text("新年好~").color(TextColor.color(0xFF5555)));
                }
                case HYPNOSIS_APP -> {
                    // 催眠app - 材质铁锭
                    lore.add(Component.text("右键催眠一个玩家，让ta获得：").color(TextColor.color(0xAAAAAA)));
                    lore.add(Component.empty());
                    lore.add(Component.text("缓慢I 10s, 挖掘疲劳III 15s").color(TextColor.color(0x55FFFF)));
                }
                case EX_CURRY_STICK -> {
                    // 『EX咖喱棒』 - 材质钻石剑
                    lore.add(Component.text("右键召唤大光柱").color(TextColor.color(0xAAAAAA)));
                    lore.add(Component.text("光柱倒下造成伤害并击退").color(TextColor.color(0x55FFFF)));
                    lore.add(Component.empty());
                    lore.add(Component.text("冷却时间：").color(TextColor.color(0xFFD700))
                            .append(Component.text("90s").color(TextColor.color(0x55FFFF))));
                    lore.add(Component.text("伤害：6-13").color(TextColor.color(0xFF5555)));
                    lore.add(Component.text("范围：直线路径").color(TextColor.color(0x55FFFF)));
                }
                case SPAWNER -> {
                    // 刷怪笼
                }
                case SPECIAL_BOW -> {
                    // 神弓 - 发射爆炸箭，显示真实附魔
                    meta.addEnchant(Enchantment.POWER, 5, true);
                    meta.addEnchant(Enchantment.UNBREAKING, 3, true);
                    // 不隐藏附魔，让真实附魔显示
                }
                case SPECIAL_CROSSBOW -> {
                    // 神弩 - 多重射击，显示真实附魔
                    meta.addEnchant(Enchantment.MULTISHOT, 1, true);
                    meta.addEnchant(Enchantment.QUICK_CHARGE, 3, true);
                    meta.addEnchant(Enchantment.UNBREAKING, 3, true);
                    // 不隐藏附魔，让真实附魔显示
                }
                case THE_WORLD -> {
                    // 砸瓦鲁多 - 钟，冷却1分30秒，右键冻结周围玩家9秒
                    lore.add(Component.text("The World!!!").color(TextColor.color(0xFFD700)).decorate(TextDecoration.BOLD));
                    lore.add(Component.empty());
                    lore.add(Component.text("冷却时间：1分30秒").color(TextColor.color(0xAAAAAA)));
                    lore.add(Component.text("右键冻结周围玩家9秒").color(TextColor.color(0x55FFFF)));
                }
                case GRAVITY_BOOTS -> {
                    // 重力靴 - 下界合金靴，免疫击退，增加下落速度
                    lore.add(Component.text("风雨不动安如山").color(TextColor.color(0x55FFFF)));
                    // 击退抗性
                    meta.addAttributeModifier(Attribute.KNOCKBACK_RESISTANCE,
                            new AttributeModifier(UUID.randomUUID(), "gravity_boots_knockback", 1.0,
                                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.FEET));
                    // 增加重力（通过移动速度减少来模拟）
                    meta.addAttributeModifier(Attribute.MOVEMENT_SPEED,
                            new AttributeModifier(UUID.randomUUID(), "gravity_boots_speed", -0.1,
                                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.FEET));
                }
                case LIFE_STEAL_SWORD -> {
                    // 生命偷取剑 - 金剑，造成伤害的50%转化为生命值
                    lore.add(Component.text("大血条转移术").color(TextColor.color(0xFF5555)));
                    lore.add(Component.empty());
                    lore.add(Component.text("造成伤害的50%转化为生命值").color(TextColor.color(0xAAAAAA)));
                }
                case SHIELD_GENERATOR -> {
                    // 护盾发生器 - 盾牌，冷却1分钟，生成5秒可吸收8点生命值的护盾
                    lore.add(Component.text("安如磐石").color(TextColor.color(0x55FFFF)));
                    lore.add(Component.empty());
                    lore.add(Component.text("冷却时间：1分钟").color(TextColor.color(0xAAAAAA)));
                    lore.add(Component.text("右键生成5秒可吸收8点生命值的护盾").color(TextColor.color(0x55FFFF)));
                    lore.add(Component.text("并小幅提升抗击退能力").color(TextColor.color(0xAAAAAA)));
                }
                case POISON_DAGGER -> {
                    // 剧毒匕首 - 铁剑，攻击附加3秒中毒效果
                    lore.add(Component.text("是的，这剑有毒").color(TextColor.color(0x55FF55)));
                    lore.add(Component.empty());
                    lore.add(Component.text("攻击附加3秒中毒效果").color(TextColor.color(0xAAAAAA)));
                }
            }

            meta.lore(lore);

            // 隐藏附魔显示，但显示属性（击退棒、长矛、神弓、神弩除外）
            if (type != SpecialItemType.KNOCKBACK_STICK && type != SpecialItemType.SPEAR 
                && type != SpecialItemType.SPECIAL_BOW && type != SpecialItemType.SPECIAL_CROSSBOW) {
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            
            // 长矛显示属性
            if (type == SpecialItemType.SPEAR) {
                // 不隐藏属性，让属性显示出来
            } else {
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            }

            // 设置PersistentData
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(itemKey, PersistentDataType.STRING, type.getId());

            item.setItemMeta(meta);

            // 喵人斧特殊处理：设置耐久为1（剩余1点使用次数）
            if (type == SpecialItemType.MEOW_AXE) {
                if (item.getItemMeta() instanceof Damageable damageable) {
                    int maxDurability = item.getType().getMaxDurability();
                    // 设置损坏值为最大耐久 - 1，即剩余1点耐久
                    damageable.setDamage(maxDurability - 1);
                    item.setItemMeta(damageable);
                }
            }
        }

        return item;
    }

    public boolean isSpecialItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.has(itemKey, PersistentDataType.STRING);
    }

    public SpecialItemType getSpecialItemType(ItemStack item) {
        if (!isSpecialItem(item)) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer container = meta.getPersistentDataContainer();
        String id = container.get(itemKey, PersistentDataType.STRING);
        if (id == null) return null;

        for (SpecialItemType type : SpecialItemType.values()) {
            if (type.getId().equals(id)) {
                return type;
            }
        }
        return null;
    }

    public NamespacedKey getItemKey() {
        return itemKey;
    }
}
