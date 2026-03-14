package com.newpillar.game.items;

import com.newpillar.NewPillar;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

/**
 * 海洋钓鱼系统 - 使用 LootTableSystem 的 sea.json 战利品表
 * sea.json: 80% main + 钓鱼子表（鱼8%、垃圾12%、宝藏5%）
 * 已统一使用 LootTableSystem 处理战利品获取
 */
public class SeaFishingSystem {
    private final NewPillar plugin;
    private final Random random = new Random();

    public SeaFishingSystem(NewPillar plugin) {
        this.plugin = plugin;
    }

    /**
     * 获取钓鱼战利品
     * 使用配置的 sea_fishing_pool 随机池
     * 默认 sea.json 定义：80% main, 20% 钓鱼子表（fishing/fish, fishing/junk, fishing/treasure）
     * @return 随机的钓鱼战利品
     */
    public ItemStack getFishingLoot() {
        // 使用配置的随机池获取战利品
        String seaPool = plugin.getConfig().getString("loot_pools.sea_fishing_pool", "sea");
        return this.plugin.getLootTableSystem().getRandomLoot(seaPool);
    }

    /**
     * 获取钓鱼战利品（兼容旧接口）
     * @deprecated 使用 {@link #getFishingLoot()} 代替
     */
    @Deprecated
    public ItemStack getFishingLoot(ItemSystem itemSystem, EnchantedBookSystem bookSystem) {
        return getFishingLoot();
    }
}
