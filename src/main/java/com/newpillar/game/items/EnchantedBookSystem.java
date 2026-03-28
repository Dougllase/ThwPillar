package com.newpillar.game.items;

import com.newpillar.NewPillar;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 附魔书系统 - 从数据包同步的附魔书战利品表
 */
public class EnchantedBookSystem {
    private final NewPillar plugin;
    private final Random random = new Random();
    private final List<Enchantment> availableEnchantments = new ArrayList<>();

    public EnchantedBookSystem(NewPillar plugin) {
        this.plugin = plugin;
        this.initEnchantments();
    }

    /**
     * 初始化可用附魔列表
     */
    private void initEnchantments() {
        // 护甲附魔
        availableEnchantments.add(Enchantment.PROTECTION);
        availableEnchantments.add(Enchantment.FIRE_PROTECTION);
        availableEnchantments.add(Enchantment.FEATHER_FALLING);
        availableEnchantments.add(Enchantment.BLAST_PROTECTION);
        availableEnchantments.add(Enchantment.PROJECTILE_PROTECTION);
        availableEnchantments.add(Enchantment.RESPIRATION);
        availableEnchantments.add(Enchantment.AQUA_AFFINITY);
        availableEnchantments.add(Enchantment.THORNS);
        availableEnchantments.add(Enchantment.DEPTH_STRIDER);
        availableEnchantments.add(Enchantment.FROST_WALKER);
        availableEnchantments.add(Enchantment.BINDING_CURSE);
        availableEnchantments.add(Enchantment.SOUL_SPEED);
        availableEnchantments.add(Enchantment.SWIFT_SNEAK);

        // 武器附魔
        availableEnchantments.add(Enchantment.SHARPNESS);
        availableEnchantments.add(Enchantment.SMITE);
        availableEnchantments.add(Enchantment.BANE_OF_ARTHROPODS);
        availableEnchantments.add(Enchantment.KNOCKBACK);
        availableEnchantments.add(Enchantment.FIRE_ASPECT);
        availableEnchantments.add(Enchantment.LOOTING);
        availableEnchantments.add(Enchantment.SWEEPING_EDGE);

        // 工具附魔
        availableEnchantments.add(Enchantment.EFFICIENCY);
        availableEnchantments.add(Enchantment.SILK_TOUCH);
        availableEnchantments.add(Enchantment.UNBREAKING);
        availableEnchantments.add(Enchantment.FORTUNE);

        // 弓附魔
        availableEnchantments.add(Enchantment.POWER);
        availableEnchantments.add(Enchantment.PUNCH);
        availableEnchantments.add(Enchantment.FLAME);
        availableEnchantments.add(Enchantment.INFINITY);

        // 钓鱼竿附魔
        availableEnchantments.add(Enchantment.LUCK_OF_THE_SEA);
        availableEnchantments.add(Enchantment.LURE);

        // 三叉戟附魔
        availableEnchantments.add(Enchantment.CHANNELING);
        availableEnchantments.add(Enchantment.IMPALING);
        availableEnchantments.add(Enchantment.LOYALTY);
        availableEnchantments.add(Enchantment.RIPTIDE);

        // 通用附魔
        availableEnchantments.add(Enchantment.MENDING);
        availableEnchantments.add(Enchantment.VANISHING_CURSE);

        this.plugin.getLogger().info("[EnchantedBookSystem] 附魔书表已初始化，共 " + this.availableEnchantments.size() + " 种附魔");
    }

    /**
     * 获取随机附魔书
     * 与数据包一致：随机附魔 + 自定义说明
     */
    public ItemStack getRandomEnchantedBook() {
        if (this.availableEnchantments.isEmpty()) {
            return null;
        }

        // 随机选择一个附魔
        Enchantment enchantment = this.availableEnchantments.get(this.random.nextInt(this.availableEnchantments.size()));
        
        // 获取该附魔的等级范围
        int maxLevel = enchantment.getMaxLevel();
        int level = this.random.nextInt(maxLevel) + 1;

        // 创建附魔书
        ItemStack book = new ItemStack(Material.BOOK);
        book.editMeta(meta -> {
            // 添加附魔
            meta.addEnchant(enchantment, level, true);
            
            // 添加自定义说明（与数据包一致）
            List<String> lore = new ArrayList<>();
            lore.add("§7附魔书放置在§6主手§7，需要附魔的物品放置在§6副手");
            lore.add("§6右键§7进行附魔");
            meta.setLore(lore);
            
            // 设置自定义数据标记
            meta.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey(this.plugin, "item"),
                org.bukkit.persistence.PersistentDataType.STRING,
                "enchanted_book"
            );
        });

        return book;
    }

    /**
     * 获取所有可用附魔
     */
    public List<Enchantment> getAvailableEnchantments() {
        return new ArrayList<>(this.availableEnchantments);
    }
}
