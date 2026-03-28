package com.newpillar.game.items;

import com.newpillar.NewPillar;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 药水系统 - 从数据包同步的药水战利品表
 */
public class PotionSystem {
    private final NewPillar plugin;
    private final Random random = new Random();
    private final List<PotionEntry> potionTable = new ArrayList<>();

    public PotionSystem(NewPillar plugin) {
        this.plugin = plugin;
        this.initPotionTable();
    }

    /**
     * 初始化药水表（与数据包 potion.json 同步）
     */
    private void initPotionTable() {
        // 普通药水（权重相同）
        addPotion(Material.POTION, PotionType.NIGHT_VISION, 10);
        addPotion(Material.POTION, PotionType.INVISIBILITY, 10);
        addPotion(Material.POTION, PotionType.LEAPING, 10);  // JUMP -> LEAPING
        addPotion(Material.POTION, PotionType.FIRE_RESISTANCE, 10);
        addPotion(Material.POTION, PotionType.SWIFTNESS, 10);  // SPEED -> SWIFTNESS
        addPotion(Material.POTION, PotionType.WATER, 10);
        addPotion(Material.POTION, PotionType.TURTLE_MASTER, 10);
        addPotion(Material.POTION, PotionType.SLOW_FALLING, 10);

        // 治疗药水（权重分组）
        addPotion(Material.POTION, PotionType.HEALING, 6);  // INSTANT_HEAL -> HEALING
        addPotion(Material.POTION, PotionType.STRONG_HEALING, 4);

        // 再生药水（权重分组）
        addPotion(Material.POTION, PotionType.REGENERATION, 6);
        addPotion(Material.POTION, PotionType.LONG_REGENERATION, 4);

        // 力量药水（权重分组）
        addPotion(Material.POTION, PotionType.STRENGTH, 8);
        addPotion(Material.POTION, PotionType.LONG_STRENGTH, 2);

        // 喷溅药水
        addPotion(Material.SPLASH_POTION, PotionType.SLOWNESS, 10);
        addPotion(Material.SPLASH_POTION, PotionType.HARMING, 8);  // INSTANT_DAMAGE -> HARMING
        addPotion(Material.SPLASH_POTION, PotionType.STRONG_HARMING, 2);

        this.plugin.getLogger().info("[PotionSystem] 药水表已初始化，共 " + this.potionTable.size() + " 种药水");
    }

    private void addPotion(Material material, PotionType type, int weight) {
        this.potionTable.add(new PotionEntry(material, type, weight));
    }

    /**
     * 获取随机药水
     */
    public ItemStack getRandomPotion() {
        if (this.potionTable.isEmpty()) {
            return null;
        }

        int totalWeight = 0;
        for (PotionEntry entry : this.potionTable) {
            totalWeight += entry.weight;
        }

        int randomValue = this.random.nextInt(totalWeight);
        int currentWeight = 0;

        for (PotionEntry entry : this.potionTable) {
            currentWeight += entry.weight;
            if (randomValue < currentWeight) {
                ItemStack potion = new ItemStack(entry.material);
                potion.editMeta(meta -> {
                    if (meta instanceof PotionMeta potionMeta) {
                        potionMeta.setBasePotionType(entry.type);
                    }
                });
                return potion;
            }
        }

        // 默认返回最后一个
        PotionEntry lastEntry = this.potionTable.get(this.potionTable.size() - 1);
        ItemStack potion = new ItemStack(lastEntry.material);
        potion.editMeta(meta -> {
            if (meta instanceof PotionMeta potionMeta) {
                potionMeta.setBasePotionType(lastEntry.type);
            }
        });
        return potion;
    }

    private record PotionEntry(Material material, PotionType type, int weight) {}
}
