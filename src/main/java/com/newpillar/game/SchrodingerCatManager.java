package com.newpillar.game;

import com.newpillar.NewPillar;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 薛定谔的猫管理器
 * 开启后，玩家获得的24个物品将预先生成
 */
public class SchrodingerCatManager {
    private final NewPillar plugin;
    private final SpecialItemManager specialItemManager;
    private final VanillaItemManager vanillaItemManager;
    private final LootTableSystem lootTableSystem;

    // 是否启用薛定谔的猫
    private boolean enabled;

    // 是否启用观测者效应（列表随获取继续向后随机）
    private boolean observerEffectEnabled;

    // 玩家预生成物品列表 (UUID -> 物品列表)
    private final Map<UUID, List<String>> playerItemLists = new ConcurrentHashMap<>();

    // 玩家当前物品索引 (UUID -> 下一个要获取的物品索引)
    private final Map<UUID, Integer> playerItemIndices = new ConcurrentHashMap<>();

    // 玩家下一个要获取的物品（关闭时使用）
    private final Map<UUID, String> playerNextItems = new ConcurrentHashMap<>();

    // 原版物品列表（用于随机生成）
    private final List<Material> vanillaItems = new ArrayList<>();
    private final Random random = new Random();

    public SchrodingerCatManager(NewPillar plugin) {
        this.plugin = plugin;
        this.specialItemManager = plugin.getSpecialItemManager();
        this.vanillaItemManager = plugin.getVanillaItemManager();
        this.lootTableSystem = plugin.getLootTableSystem();
        this.enabled = plugin.getConfig().getBoolean("schrodinger-cat.enabled", false);
        this.observerEffectEnabled = plugin.getConfig().getBoolean("schrodinger-cat.observer-effect", false);
        initVanillaItems();
    }

    /**
     * 初始化原版物品列表
     */
    private void initVanillaItems() {
        // 添加一些常用的原版物品
        vanillaItems.add(Material.WOODEN_SWORD);
        vanillaItems.add(Material.STONE_SWORD);
        vanillaItems.add(Material.IRON_SWORD);
        vanillaItems.add(Material.GOLDEN_SWORD);
        vanillaItems.add(Material.DIAMOND_SWORD);
        vanillaItems.add(Material.WOODEN_AXE);
        vanillaItems.add(Material.STONE_AXE);
        vanillaItems.add(Material.IRON_AXE);
        vanillaItems.add(Material.GOLDEN_AXE);
        vanillaItems.add(Material.DIAMOND_AXE);
        vanillaItems.add(Material.BOW);
        vanillaItems.add(Material.CROSSBOW);
        vanillaItems.add(Material.FISHING_ROD);
        vanillaItems.add(Material.GOLDEN_APPLE);
        vanillaItems.add(Material.ENCHANTED_GOLDEN_APPLE);
        vanillaItems.add(Material.COOKED_BEEF);
        vanillaItems.add(Material.COOKED_PORKCHOP);
        vanillaItems.add(Material.COOKED_CHICKEN);
        vanillaItems.add(Material.ARROW);
        vanillaItems.add(Material.SHIELD);
        vanillaItems.add(Material.IRON_HELMET);
        vanillaItems.add(Material.IRON_CHESTPLATE);
        vanillaItems.add(Material.IRON_LEGGINGS);
        vanillaItems.add(Material.IRON_BOOTS);
        vanillaItems.add(Material.DIAMOND_HELMET);
        vanillaItems.add(Material.DIAMOND_CHESTPLATE);
        vanillaItems.add(Material.DIAMOND_LEGGINGS);
        vanillaItems.add(Material.DIAMOND_BOOTS);
        vanillaItems.add(Material.TOTEM_OF_UNDYING);
        vanillaItems.add(Material.ENDER_PEARL);
        vanillaItems.add(Material.FIREWORK_ROCKET);
        vanillaItems.add(Material.TNT);
        vanillaItems.add(Material.FLINT_AND_STEEL);
        vanillaItems.add(Material.OAK_PLANKS);
        vanillaItems.add(Material.COBBLESTONE);
        vanillaItems.add(Material.OBSIDIAN);
        vanillaItems.add(Material.LAVA_BUCKET);
        vanillaItems.add(Material.WATER_BUCKET);
        vanillaItems.add(Material.EXPERIENCE_BOTTLE);
        vanillaItems.add(Material.EMERALD);
        vanillaItems.add(Material.DIAMOND);
        vanillaItems.add(Material.IRON_INGOT);
        vanillaItems.add(Material.GOLD_INGOT);
    }

    /**
     * 检查是否启用薛定谔的猫
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置启用状态
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        plugin.getConfig().set("schrodinger-cat.enabled", enabled);
        plugin.saveConfig();
    }

    /**
     * 切换启用状态
     */
    public boolean toggle() {
        enabled = !enabled;
        plugin.getConfig().set("schrodinger-cat.enabled", enabled);
        plugin.saveConfig();
        return enabled;
    }

    /**
     * 检查是否启用观测者效应
     */
    public boolean isObserverEffectEnabled() {
        return observerEffectEnabled;
    }

    /**
     * 设置观测者效应启用状态
     */
    public void setObserverEffectEnabled(boolean enabled) {
        this.observerEffectEnabled = enabled;
        plugin.getConfig().set("schrodinger-cat.observer-effect", enabled);
        plugin.saveConfig();
    }

    /**
     * 切换观测者效应启用状态
     */
    public boolean toggleObserverEffect() {
        observerEffectEnabled = !observerEffectEnabled;
        plugin.getConfig().set("schrodinger-cat.observer-effect", observerEffectEnabled);
        plugin.saveConfig();
        return observerEffectEnabled;
    }

    /**
     * 为玩家预生成24个物品
     */
    public void generateItemsForPlayer(Player player) {
        List<String> items = new ArrayList<>();

        for (int i = 0; i < 24; i++) {
            String itemId = generateRandomItemId();
            items.add(itemId);
        }

        playerItemLists.put(player.getUniqueId(), items);
        playerItemIndices.put(player.getUniqueId(), 0);

        plugin.getLogger().fine("为玩家 " + player.getName() + " 预生成了24个物品");
    }

    /**
     * 生成随机物品ID
     * 使用main战利品表来获取物品，确保与数据包完全一致
     */
    private String generateRandomItemId() {
        // 使用main战利品表生成物品
        ItemStack item = lootTableSystem.generateLootFromTable("main");
        if (item == null) {
            // 回退到本地列表
            Material material = vanillaItems.get(random.nextInt(vanillaItems.size()));
            return "VANILLA:" + material.name();
        }
        
        // 将物品转换为ID格式
        return convertItemToId(item);
    }
    
    /**
     * 将物品转换为ID格式
     */
    private String convertItemToId(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return "VANILLA:STONE";
        }
        
        // 检查是否是特殊物品（通过PersistentDataContainer）
        if (item.hasItemMeta()) {
            org.bukkit.persistence.PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
            org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "item_id");
            if (container.has(key, org.bukkit.persistence.PersistentDataType.STRING)) {
                String itemId = container.get(key, org.bukkit.persistence.PersistentDataType.STRING);
                // 将特殊物品ID转换为大写以匹配SpecialItemType
                return itemId.toUpperCase();
            }
        }
        
        // 检查是否是附魔书
        if (item.getType() == Material.ENCHANTED_BOOK) {
            return "ENCHANTED_BOOK";
        }
        
        // 检查是否是药水
        if (item.getType() == Material.POTION || item.getType() == Material.SPLASH_POTION) {
            // 药水使用原版物品格式，实际效果在创建时通过PotionMeta恢复
            return "VANILLA:" + item.getType().name();
        }
        
        // 原版物品
        return "VANILLA:" + item.getType().name();
    }

    /**
     * 获取玩家的下一个物品（薛定谔的猫开启时使用）
     */
    public ItemStack getNextItem(Player player) {
        UUID uuid = player.getUniqueId();

        // 如果没有预生成列表，先生成
        if (!playerItemLists.containsKey(uuid)) {
            generateItemsForPlayer(player);
        }

        List<String> items = playerItemLists.get(uuid);
        Integer index = playerItemIndices.getOrDefault(uuid, 0);

        // 观测者效应：如果启用且列表快用完了，动态添加新物品
        if (observerEffectEnabled && index >= items.size() - 5) {
            // 列表快用完了，添加新物品而不是重新生成
            for (int i = 0; i < 12; i++) {
                String newItemId = generateRandomItemId();
                items.add(newItemId);
            }
            // 保持列表长度不超过48个
            while (items.size() > 48) {
                items.remove(0);
                index--; // 调整索引
            }
            plugin.getLogger().info("观测者效应：为玩家 " + player.getName() + " 动态添加了12个新物品到列表");
        } else if (index >= items.size()) {
            // 观测者效应关闭时，列表已用完，重新生成
            generateItemsForPlayer(player);
            items = playerItemLists.get(uuid);
            index = 0;
        }

        String itemId = items.get(index);
        playerItemIndices.put(uuid, index + 1);

        return createItemFromId(itemId);
    }

    /**
     * 获取玩家的下一个物品（薛定谔的猫关闭时使用）
     */
    public ItemStack getNextItemDisabled(Player player) {
        UUID uuid = player.getUniqueId();
        String itemId = playerNextItems.get(uuid);

        if (itemId != null) {
            // 使用预设的下一个物品
            playerNextItems.remove(uuid);
            return createItemFromId(itemId);
        } else {
            // 随机生成
            return generateRandomItem();
        }
    }

    /**
     * 设置玩家的下一个物品（关闭时使用）
     */
    public void setNextItem(Player player, String itemId) {
        playerNextItems.put(player.getUniqueId(), itemId);
    }

    /**
     * 从ID创建物品
     */
    private ItemStack createItemFromId(String itemId) {
        if (itemId.startsWith("VANILLA:")) {
            String materialName = itemId.substring(8);
            try {
                Material material = Material.valueOf(materialName);
                // 对于附魔书，使用战利品表系统生成一个带随机附魔的
                if (material == Material.ENCHANTED_BOOK) {
                    return lootTableSystem.generateLootFromTable("enchanted_book");
                }
                return new ItemStack(material);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("未知的原版物品: " + materialName);
                return generateRandomItem();
            }
        } else if (itemId.equals("ENCHANTED_BOOK")) {
            // 附魔书ID
            return lootTableSystem.generateLootFromTable("enchanted_book");
        } else {
            try {
                SpecialItemManager.SpecialItemType type = SpecialItemManager.SpecialItemType.valueOf(itemId);
                return specialItemManager.createSpecialItem(type);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("未知的特殊物品: " + itemId);
                return generateRandomItem();
            }
        }
    }

    /**
     * 生成随机物品 - 使用ItemSystem的战利品表
     */
    private ItemStack generateRandomItem() {
        if (random.nextDouble() < 0.5) {
            SpecialItemManager.SpecialItemType[] specialItems = SpecialItemManager.SpecialItemType.values();
            return specialItemManager.createSpecialItem(specialItems[random.nextInt(specialItems.length)]);
        } else {
            // 使用ItemSystem的完整战利品表
            ItemSystem itemSystem = plugin.getGameManager().getItemSystem();
            ItemStack loot = itemSystem.getRandomLoot();
            if (loot != null) {
                return loot;
            }
            // 如果战利品表为空，回退到简单列表
            Material material = vanillaItems.get(random.nextInt(vanillaItems.size()));
            return new ItemStack(material);
        }
    }

    /**
     * 获取玩家的预生成物品列表
     */
    public List<String> getPlayerItemList(Player player) {
        return playerItemLists.getOrDefault(player.getUniqueId(), new ArrayList<>());
    }

    /**
     * 获取玩家的预生成物品列表（通过UUID）
     */
    public List<String> getPlayerItemList(UUID uuid) {
        return playerItemLists.getOrDefault(uuid, new ArrayList<>());
    }

    /**
     * 修改玩家预生成列表中的物品
     */
    public boolean modifyPlayerItem(Player player, int index, String itemId) {
        return modifyPlayerItem(player.getUniqueId(), index, itemId);
    }

    /**
     * 修改玩家预生成列表中的物品（通过UUID）
     */
    public boolean modifyPlayerItem(UUID uuid, int index, String itemId) {
        List<String> items = playerItemLists.get(uuid);
        if (items == null || index < 0 || index >= items.size()) {
            return false;
        }

        // 验证物品ID是否有效
        if (!isValidItemId(itemId)) {
            return false;
        }

        items.set(index, itemId);
        return true;
    }

    /**
     * 验证物品ID是否有效
     */
    public boolean isValidItemId(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return false;
        }

        if (itemId.startsWith("VANILLA:")) {
            String materialName = itemId.substring(8);
            try {
                Material.valueOf(materialName);
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        } else {
            try {
                SpecialItemManager.SpecialItemType.valueOf(itemId);
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
    }

    /**
     * 获取玩家当前物品索引
     */
    public int getPlayerItemIndex(Player player) {
        return playerItemIndices.getOrDefault(player.getUniqueId(), 0);
    }

    /**
     * 获取玩家的下一个预设物品（关闭时使用）
     */
    public String getPlayerNextItem(Player player) {
        return playerNextItems.get(player.getUniqueId());
    }

    /**
     * 清除玩家的数据
     */
    public void clearPlayerData(Player player) {
        UUID uuid = player.getUniqueId();
        playerItemLists.remove(uuid);
        playerItemIndices.remove(uuid);
        playerNextItems.remove(uuid);
    }

    /**
     * 清除所有数据
     */
    public void clearAllData() {
        playerItemLists.clear();
        playerItemIndices.clear();
        playerNextItems.clear();
    }

    /**
     * 获取所有支持的特殊物品ID列表
     */
    public List<String> getAllSpecialItemIds() {
        List<String> ids = new ArrayList<>();
        for (SpecialItemManager.SpecialItemType type : SpecialItemManager.SpecialItemType.values()) {
            ids.add(type.name());
        }
        return ids;
    }
}
