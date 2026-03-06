package com.newpillar.game.events;

import com.newpillar.NewPillar;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class LuckyBlockSystem implements Listener {
    private final NewPillar plugin;
    private final NamespacedKey luckyBlockKey;
    private final NamespacedKey lootChestKey;
    private final Random random = new Random();
    
    // 原版战利品表列表（对应数据包的56种）
    private final List<String> vanillaLootTables = Arrays.asList(
        "minecraft:chests/abandoned_mineshaft",
        "minecraft:chests/ancient_city",
        "minecraft:chests/ancient_city_ice_box",
        "minecraft:chests/bastion_bridge",
        "minecraft:chests/bastion_hoglin_stable",
        "minecraft:chests/bastion_other",
        "minecraft:chests/bastion_treasure",
        "minecraft:chests/buried_treasure",
        "minecraft:chests/desert_pyramid",
        "minecraft:chests/end_city_treasure",
        "minecraft:chests/igloo_chest",
        "minecraft:chests/jungle_temple",
        "minecraft:chests/jungle_temple_dispenser",
        "minecraft:chests/nether_bridge",
        "minecraft:chests/pillager_outpost",
        "minecraft:chests/ruined_portal",
        "minecraft:chests/shipwreck_map",
        "minecraft:chests/shipwreck_supply",
        "minecraft:chests/shipwreck_treasure",
        "minecraft:chests/simple_dungeon",
        "minecraft:chests/spawn_bonus_chest",
        "minecraft:chests/stronghold_corridor",
        "minecraft:chests/stronghold_crossing",
        "minecraft:chests/stronghold_library",
        "minecraft:chests/trial_chambers/corridor",
        "minecraft:chests/trial_chambers/entrance",
        "minecraft:chests/trial_chambers/intersection",
        "minecraft:chests/trial_chambers/intersection_barrel",
        "minecraft:chests/trial_chambers/reward",
        "minecraft:chests/trial_chambers/reward_common",
        "minecraft:chests/trial_chambers/reward_ominous",
        "minecraft:chests/trial_chambers/reward_ominous_common",
        "minecraft:chests/trial_chambers/reward_ominous_rare",
        "minecraft:chests/trial_chambers/reward_ominous_unique",
        "minecraft:chests/trial_chambers/reward_rare",
        "minecraft:chests/trial_chambers/reward_unique",
        "minecraft:chests/trial_chambers/supply",
        "minecraft:chests/underwater_ruin_big",
        "minecraft:chests/underwater_ruin_small",
        "minecraft:chests/village/village_armorer",
        "minecraft:chests/village/village_butcher",
        "minecraft:chests/village/village_cartographer",
        "minecraft:chests/village/village_desert_house",
        "minecraft:chests/village/village_fisher",
        "minecraft:chests/village/village_fletcher",
        "minecraft:chests/village/village_mason",
        "minecraft:chests/village/village_plains_house",
        "minecraft:chests/village/village_savanna_house",
        "minecraft:chests/village/village_shepherd",
        "minecraft:chests/village/village_snowy_house",
        "minecraft:chests/village/village_taiga_house",
        "minecraft:chests/village/village_tannery",
        "minecraft:chests/village/village_temple",
        "minecraft:chests/village/village_toolsmith",
        "minecraft:chests/village/village_weaponsmith",
        "minecraft:chests/woodland_mansion"
    );
    
    public LuckyBlockSystem(NewPillar plugin) {
        this.plugin = plugin;
        this.luckyBlockKey = new NamespacedKey(plugin, "lucky_block");
        this.lootChestKey = new NamespacedKey(plugin, "loot_chest");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
    
    /**
     * 创建幸运方块物品
     */
    public ItemStack createLuckyBlock() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§l幸运方块");
            meta.setLore(Arrays.asList(
                "§7右键使用",
                "§7随机获得各种奖励！"
            ));
            
            // 设置自定义数据标记
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(luckyBlockKey, PersistentDataType.BYTE, (byte) 1);
            
            item.setItemMeta(meta);
        }
        return item;
    }
    
    /**
     * 创建战利品箱标记物品
     */
    public ItemStack createLootChestMarker() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e§l战利品箱");
            meta.setLore(Arrays.asList(
                "§7放置后生成战利品箱"
            ));
            
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(lootChestKey, PersistentDataType.BYTE, (byte) 1);
            
            item.setItemMeta(meta);
        }
        return item;
    }
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        if (item == null || item.getType() != Material.PAPER) {
            return;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        
        PersistentDataContainer container = meta.getPersistentDataContainer();
        
        // 检查是否是幸运方块
        if (container.has(luckyBlockKey, PersistentDataType.BYTE)) {
            event.setCancelled(true);
            
            if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                openLuckyBlock(player, item);
            }
        }
        // 检查是否是战利品箱标记
        else if (container.has(lootChestKey, PersistentDataType.BYTE)) {
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
                event.setCancelled(true);
                placeLootChest(player, event.getClickedBlock(), item);
            }
        }
    }
    
    /**
     * 打开幸运方块
     */
    private void openLuckyBlock(Player player, ItemStack item) {
        // 消耗物品
        item.setAmount(item.getAmount() - 1);
        
        // 播放音效
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
        
        // 随机决定奖励类型
        int roll = random.nextInt(100);
        
        if (roll < 60) {
            // 60% 概率：普通物品奖励
            giveRandomLoot(player);
        } else if (roll < 90) {
            // 30% 概率：战利品箱
            giveLootChest(player);
        } else {
            // 10% 概率：特殊效果
            triggerSpecialEffect(player);
        }
    }
    
    /**
     * 给予随机物品奖励
     */
    private void giveRandomLoot(Player player) {
        // 使用LootTableSystem获取随机战利品
        ItemStack loot = plugin.getLootTableSystem().getRandomLoot("main");
        
        if (loot != null) {
            player.getInventory().addItem(loot);
            player.sendMessage("§a§l幸运奖励！§f 你获得了 " + loot.getAmount() + "x " + 
                loot.getType().name().toLowerCase().replace("_", " "));
            
            // 触发特殊物品获取成就
            checkAndGrantSpecialItemAchievement(player, loot);
        }
    }
    
    /**
     * 检查并授予特殊物品成就
     */
    private void checkAndGrantSpecialItemAchievement(Player player, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        
        // 获取物品的自定义ID
        org.bukkit.persistence.PersistentDataContainer container = meta.getPersistentDataContainer();
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "item_id");
        
        if (container.has(key, org.bukkit.persistence.PersistentDataType.STRING)) {
            String itemId = container.get(key, org.bukkit.persistence.PersistentDataType.STRING);
            if (itemId != null && plugin.getAchievementSystem() != null) {
                plugin.getAchievementSystem().grantItemAchievement(player, itemId);
            }
        }
    }
    
    /**
     * 给予战利品箱标记
     */
    private void giveLootChest(Player player) {
        ItemStack lootChest = createLootChestMarker();
        player.getInventory().addItem(lootChest);
        player.sendMessage("§6§l幸运奖励！§f 你获得了一个战利品箱！");
        player.sendMessage("§7放置在地面上来生成战利品箱");
    }
    
    /**
     * 放置战利品箱
     */
    private void placeLootChest(Player player, Block clickedBlock, ItemStack item) {
        // 在点击方块上方放置箱子
        Block chestBlock = clickedBlock.getRelative(0, 1, 0);
        
        if (chestBlock.getType() != Material.AIR) {
            player.sendMessage("§c这里无法放置箱子！");
            return;
        }
        
        // 消耗物品
        item.setAmount(item.getAmount() - 1);
        
        // 设置方块为箱子
        chestBlock.setType(Material.CHEST);
        
        // 获取箱子状态
        if (chestBlock.getState() instanceof Chest chest) {
            Inventory inventory = chest.getInventory();
            
            // 随机选择一个原版战利品表
            String lootTable = vanillaLootTables.get(random.nextInt(vanillaLootTables.size()));
            
            // 填充战利品
            fillLootChest(inventory, lootTable);
            
            player.sendMessage("§6§l战利品箱已生成！");
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
        }
    }
    
    /**
     * 填充战利品箱
     */
    private void fillLootChest(Inventory inventory, String lootTable) {
        // 清空箱子
        inventory.clear();
        
        // 根据战利品表类型生成不同数量和质量的物品
        int itemCount = 3 + random.nextInt(5); // 3-7个物品
        
        for (int i = 0; i < itemCount; i++) {
            ItemStack item = generateLootItem(lootTable);
            if (item != null) {
                int slot = random.nextInt(inventory.getSize());
                inventory.setItem(slot, item);
            }
        }
    }
    
    /**
     * 根据战利品表生成物品
     */
    private ItemStack generateLootItem(String lootTable) {
        // 根据战利品表类型决定物品质量
        Material[] commonItems = {
            Material.STONE, Material.COBBLESTONE, Material.DIRT, Material.OAK_PLANKS,
            Material.COAL, Material.TORCH, Material.BREAD, Material.APPLE
        };
        
        Material[] uncommonItems = {
            Material.IRON_INGOT, Material.GOLD_INGOT, Material.REDSTONE,
            Material.LAPIS_LAZULI, Material.BOW, Material.ARROW, Material.LEATHER_HELMET
        };
        
        Material[] rareItems = {
            Material.DIAMOND, Material.EMERALD, Material.GOLDEN_APPLE,
            Material.IRON_SWORD, Material.SHIELD, Material.EXPERIENCE_BOTTLE
        };
        
        Material[] epicItems = {
            Material.NETHERITE_SCRAP, Material.ENCHANTED_GOLDEN_APPLE,
            Material.DIAMOND_SWORD, Material.TOTEM_OF_UNDYING, Material.ENDER_PEARL
        };
        
        Material[] legendaryItems = {
            Material.NETHERITE_INGOT, Material.NETHER_STAR, Material.DRAGON_EGG,
            Material.BEACON, Material.END_CRYSTAL
        };
        
        Material material;
        int roll = random.nextInt(100);
        
        // 根据战利品表稀有度调整概率
        if (lootTable.contains("treasure") || lootTable.contains("end_city")) {
            // 高价值战利品表
            if (roll < 10) material = legendaryItems[random.nextInt(legendaryItems.length)];
            else if (roll < 30) material = epicItems[random.nextInt(epicItems.length)];
            else if (roll < 60) material = rareItems[random.nextInt(rareItems.length)];
            else material = uncommonItems[random.nextInt(uncommonItems.length)];
        } else if (lootTable.contains("ancient_city") || lootTable.contains("bastion_treasure")) {
            // 中高价值
            if (roll < 5) material = legendaryItems[random.nextInt(legendaryItems.length)];
            else if (roll < 20) material = epicItems[random.nextInt(epicItems.length)];
            else if (roll < 50) material = rareItems[random.nextInt(rareItems.length)];
            else material = uncommonItems[random.nextInt(uncommonItems.length)];
        } else {
            // 普通战利品表
            if (roll < 40) material = commonItems[random.nextInt(commonItems.length)];
            else if (roll < 70) material = uncommonItems[random.nextInt(uncommonItems.length)];
            else if (roll < 90) material = rareItems[random.nextInt(rareItems.length)];
            else material = epicItems[random.nextInt(epicItems.length)];
        }
        
        int amount = 1 + random.nextInt(Math.min(16, material.getMaxStackSize()));
        return new ItemStack(material, amount);
    }
    
    /**
     * 触发特殊效果
     */
    private void triggerSpecialEffect(Player player) {
        int effect = random.nextInt(10);
        
        switch (effect) {
            case 0 -> {
                // 治疗
                player.setHealth(Math.min(player.getHealth() + 10, player.getMaxHealth()));
                player.sendMessage("§a§l幸运治疗！§f 你恢复了10点生命值！");
            }
            case 1 -> {
                // 食物
                player.setFoodLevel(20);
                player.setSaturation(20);
                player.sendMessage("§a§l饱食满满！§f 你的饥饿值已恢复！");
            }
            case 2 -> {
                // 经验
                player.giveExp(100 + random.nextInt(400));
                player.sendMessage("§a§l经验奖励！§f 你获得了经验值！");
            }
            case 3 -> {
                // 随机传送（短距离）
                Location loc = player.getLocation();
                loc.add(random.nextInt(20) - 10, 0, random.nextInt(20) - 10);
                loc.setY(player.getWorld().getHighestBlockYAt(loc) + 1);
                player.teleport(loc);
                player.sendMessage("§e§l随机传送！§f 你被传送到了附近！");
            }
            case 4 -> {
                // 给予钻石
                player.getInventory().addItem(new ItemStack(Material.DIAMOND, 3 + random.nextInt(5)));
                player.sendMessage("§b§l钻石奖励！§f 你获得了钻石！");
            }
            case 5 -> {
                // 给予金苹果
                player.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE, 2 + random.nextInt(3)));
                player.sendMessage("§6§l金苹果奖励！§f 你获得了金苹果！");
            }
            case 6 -> {
                // 烟花效果
                Location loc = player.getLocation();
                player.getWorld().spawnParticle(Particle.FIREWORK, loc, 100, 1, 1, 1, 0.5);
                player.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.0f);
                player.sendMessage("§d§l烟花庆祝！§f 恭喜！");
            }
            case 7 -> {
                // 给予附魔书
                ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
                player.getInventory().addItem(book);
                player.sendMessage("§9§l附魔书奖励！§f 你获得了附魔书！");
            }
            case 8 -> {
                // 给予TNT
                player.getInventory().addItem(new ItemStack(Material.TNT, 5 + random.nextInt(10)));
                player.sendMessage("§c§lTNT奖励！§f 小心使用！");
            }
            case 9 -> {
                // 给予末影珍珠
                player.getInventory().addItem(new ItemStack(Material.ENDER_PEARL, 8 + random.nextInt(8)));
                player.sendMessage("§5§l末影珍珠奖励！§f 你获得了末影珍珠！");
            }
        }
    }
    
    /**
     * 给予玩家幸运方块
     */
    public void giveLuckyBlock(Player player, int amount) {
        ItemStack luckyBlock = createLuckyBlock();
        luckyBlock.setAmount(amount);
        player.getInventory().addItem(luckyBlock);
        player.sendMessage("§6§l幸运方块！§f 你获得了 " + amount + " 个幸运方块！");
    }
}
