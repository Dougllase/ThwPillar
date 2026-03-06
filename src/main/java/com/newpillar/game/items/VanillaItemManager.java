package com.newpillar.game.items;

import com.newpillar.NewPillar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * 原版物品管理器 - 管理移出特殊物品的原版物品
 * 这些物品在原版基础上添加了新的机制
 */
public class VanillaItemManager {
    private final NewPillar plugin;
    private final NamespacedKey vanillaItemKey;
    private final Random random = new Random();

    public VanillaItemManager(NewPillar plugin) {
        this.plugin = plugin;
        this.vanillaItemKey = new NamespacedKey(plugin, "vanilla_item_type");
    }

    public enum VanillaItemType {
        // 攻击类
        ECHO_SHARD("echo_shard", "末影碎片", Material.ECHO_SHARD),
        DRAGON_BREATH("dragon_breath", "龙息", Material.DRAGON_BREATH),
        TNT("tnt", "TNT", Material.TNT),
        FIRE_CHARGE("fire_charge", "火球", Material.FIRE_CHARGE),
        END_CRYSTAL("end_crystal", "末地水晶", Material.END_CRYSTAL),
        ENCHANTED_BOOK("enchanted_book", "附魔书", Material.ENCHANTED_BOOK);

        private final String id;
        private final String displayName;
        private final Material material;

        VanillaItemType(String id, String displayName, Material material) {
            this.id = id;
            this.displayName = displayName;
            this.material = material;
        }

        public String getId() { return id; }
        public String getDisplayName() { return displayName; }
        public Material getMaterial() { return material; }
    }

    /**
     * 创建带有特殊机制的原版物品
     */
    public ItemStack createVanillaItem(VanillaItemType type) {
        ItemStack item = new ItemStack(type.getMaterial());
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // 设置显示名称
            meta.displayName(Component.text(type.getDisplayName())
                    .color(TextColor.color(0x55FFFF))
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));

            // 设置Lore
            List<Component> lore = new ArrayList<>();

            switch (type) {
                case ECHO_SHARD -> {
                    lore.add(Component.text("末影碎片").color(TextColor.color(0x55FFFF)));
                    lore.add(Component.empty());
                    lore.add(Component.text("右键随机传送").color(TextColor.color(0xAAAAAA)));
                }
                case DRAGON_BREATH -> {
                    lore.add(Component.text("龙息").color(TextColor.color(0xAA00AA)));
                    lore.add(Component.empty());
                    lore.add(Component.text("右键发射龙息弹").color(TextColor.color(0xAAAAAA)));
                }
                case TNT -> {
                    lore.add(Component.text("TNT").color(TextColor.color(0xFF5555)));
                    lore.add(Component.empty());
                    lore.add(Component.text("右键放置并点燃").color(TextColor.color(0xAAAAAA)));
                }
                case FIRE_CHARGE -> {
                    lore.add(Component.text("火球").color(TextColor.color(0xFF5555)));
                    lore.add(Component.empty());
                    lore.add(Component.text("右键发射火球").color(TextColor.color(0xAAAAAA)));
                }
                case END_CRYSTAL -> {
                    lore.add(Component.text("末地水晶").color(TextColor.color(0xFF55FF)));
                    lore.add(Component.empty());
                    lore.add(Component.text("右键放置").color(TextColor.color(0xAAAAAA)));
                    lore.add(Component.text("对空放置会在最远交互距离处生成").color(TextColor.color(0x55FFFF)));
                }
                case ENCHANTED_BOOK -> {
                    lore.add(Component.text("附魔书").color(TextColor.color(0xFF55FF)));
                    lore.add(Component.empty());
                    lore.add(Component.text("右键将书中附魔给予副手物品").color(TextColor.color(0xAAAAAA)));
                }
            }

            meta.lore(lore);

            // 设置PersistentData用于识别
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(vanillaItemKey, PersistentDataType.STRING, type.getId());

            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * 检查物品是否是原版特殊物品
     */
    public boolean isVanillaItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.has(vanillaItemKey, PersistentDataType.STRING);
    }

    /**
     * 获取原版物品类型
     */
    public VanillaItemType getVanillaItemType(ItemStack item) {
        if (!isVanillaItem(item)) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer container = meta.getPersistentDataContainer();
        String id = container.get(vanillaItemKey, PersistentDataType.STRING);
        if (id == null) return null;

        for (VanillaItemType type : VanillaItemType.values()) {
            if (type.getId().equals(id)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 获取物品key
     */
    public NamespacedKey getVanillaItemKey() {
        return vanillaItemKey;
    }
}
