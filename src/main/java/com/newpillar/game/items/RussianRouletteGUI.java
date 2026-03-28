package com.newpillar.game.items;

import com.newpillar.NewPillar;
import com.newpillar.game.enums.AchievementType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.*;

/**
 * 俄罗斯轮盘枪系统
 * 
 * 设计规则：
 * - 6发弹仓，选择装1-6颗子弹
 * - 死亡概率 = 子弹数/6 (16.6%, 33.3%, 50%, 66.6%, 83.3%, 100%)
 * - 选中子弹数后修改物品属性为"已装填"状态
 * - 右键开枪，中枪死亡，未中枪发放奖励
 * - 6颗子弹：必定死亡，获得成就【左轮不会卡壳】
 */
public class RussianRouletteGUI implements Listener {
    private final NewPillar plugin;
    private final Random random = new Random();
    
    // 跟踪玩家的俄罗斯轮盘枪状态
    private final Map<UUID, RouletteState> playerStates = new HashMap<>();
    
    // GUI标题
    private static final String GUI_TITLE = "§8§l俄罗斯轮盘 - 选择子弹数量";
    
    // PersistentData key
    private final NamespacedKey bulletsKey;
    private final NamespacedKey loadedKey;
    
    public RussianRouletteGUI(NewPillar plugin) {
        this.plugin = plugin;
        this.bulletsKey = new NamespacedKey(plugin, "roulette_bullets");
        this.loadedKey = new NamespacedKey(plugin, "roulette_loaded");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
    
    /**
     * 打开俄罗斯轮盘选择界面
     */
    public void openRouletteGUI(Player player) {
        UUID uuid = player.getUniqueId();
        
        Inventory gui = Bukkit.createInventory(null, 27, GUI_TITLE);
        
        // 填充背景
        ItemStack background = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            gui.setItem(i, background);
        }
        
        // 设置6个选项（下界合金锭）- 左轮手枪弹仓形状排列
        gui.setItem(10, createBulletOption(1, "【绿色】1颗子弹", "浅尝辄止…", "死亡概率: 16.6%", true));
        gui.setItem(12, createBulletOption(2, "【灰色】2颗子弹", "慢慢有趣…", "死亡概率: 33.3%", true));
        gui.setItem(14, createBulletOption(3, "【黄色】3颗子弹", "有点意思…", "死亡概率: 50%", true));
        gui.setItem(16, createBulletOption(4, "【黄色】4颗子弹", "勇气可嘉…", "死亡概率: 66.6%", true));
        gui.setItem(20, createBulletOption(5, "【红色】5颗子弹", "孤注一掷…", "死亡概率: 83.3%", true));
        gui.setItem(24, createBulletOption(6, "【紫色】6颗子弹", "你认真的…？", "死亡概率: 100%", true));
        
        player.openInventory(gui);
    }
    
    /**
     * 创建子弹选项物品
     */
    private ItemStack createBulletOption(int bullets, String name, String desc1, String desc2, boolean enabled) {
        Material material = enabled ? Material.NETHERITE_INGOT : Material.GRAY_STAINED_GLASS_PANE;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (enabled) {
                meta.displayName(Component.text(name)
                        .color(getBulletColor(bullets))
                        .decoration(TextDecoration.BOLD, true));
                meta.lore(Arrays.asList(
                    Component.text(""),
                    Component.text(desc1).color(TextColor.color(0xAAAAAA)),
                    Component.text(desc2).color(TextColor.color(0xFFFFFF)),
                    Component.text(""),
                    Component.text("点击选择").color(TextColor.color(0x55FF55))
                ));
            } else {
                meta.displayName(Component.text("【已禁用】" + bullets + "颗子弹")
                        .color(TextColor.color(0x777777)));
                meta.lore(Arrays.asList(
                    Component.text(""),
                    Component.text("剩余子弹不足").color(TextColor.color(0xFF5555))
                ));
            }
            item.setItemMeta(meta);
        }
        return item;
    }
    
    /**
     * 获取子弹数量对应的颜色
     */
    private TextColor getBulletColor(int bullets) {
        return switch (bullets) {
            case 1 -> TextColor.color(0x55FF55); // 绿色
            case 2 -> TextColor.color(0xAAAAAA); // 灰色
            case 3, 4 -> TextColor.color(0xFFFF55); // 黄色
            case 5 -> TextColor.color(0xFF5555); // 红色
            case 6 -> TextColor.color(0xAA00AA); // 紫色
            default -> TextColor.color(0xFFFFFF);
        };
    }
    
    /**
     * 创建简单物品
     */
    private ItemStack createItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name));
            item.setItemMeta(meta);
        }
        return item;
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;
        
        event.setCancelled(true);
        
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null) return;
        
        // 检查是否点击了有效的选项
        if (clicked.getType() != Material.NETHERITE_INGOT) return;
        
        int slot = event.getRawSlot();
        int bullets = switch (slot) {
            case 10 -> 1;
            case 12 -> 2;
            case 14 -> 3;
            case 16 -> 4;
            case 20 -> 5;
            case 24 -> 6;
            default -> 0;
        };
        
        if (bullets > 0) {
            player.closeInventory();
            
            // 播放选择音效
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
            
            // 修改玩家手中的轮盘枪属性
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            if (isRussianRouletteItem(mainHand)) {
                setBulletsLoaded(mainHand, bullets);
                player.sendMessage(ChatColor.GREEN + "【俄罗斯轮盘】已装填 " + bullets + " 颗子弹，右键开枪！");
            }
        }
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;
        
        // 关闭界面后无后续操作，物品不返还（因为物品从未被消耗）
    }
    
    /**
     * 检查物品是否是俄罗斯轮盘枪
     */
    public boolean isRussianRouletteItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.has(bulletsKey, PersistentDataType.INTEGER) ||
               SpecialItemManager.SpecialItemType.RUSSIAN_ROULETTE.getId().equals(
                   container.get(new NamespacedKey(plugin, "special_item"), PersistentDataType.STRING));
    }
    
    /**
     * 设置子弹数量并标记为已装填
     */
    public void setBulletsLoaded(ItemStack item, int bullets) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(bulletsKey, PersistentDataType.INTEGER, bullets);
        container.set(loadedKey, PersistentDataType.BYTE, (byte) 1);
        
        // 更新Lore显示已装填状态
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("试试你的运气").color(TextColor.color(0xAA0000)).decorate(TextDecoration.BOLD));
        lore.add(Component.empty());
        lore.add(Component.text("已装填: ").color(TextColor.color(0xAAAAAA))
                .append(Component.text(bullets + " 颗子弹").color(TextColor.color(0xFF5555))));
        lore.add(Component.text("右键开枪").color(TextColor.color(0x55FF55)));
        lore.add(Component.empty());
        lore.add(Component.text("1-6颗子弹，奖励递增").color(TextColor.color(0x55FF55)));
        lore.add(Component.text("6颗子弹可获得特殊成就").color(TextColor.color(0xAA00AA)));
        meta.lore(lore);
        
        item.setItemMeta(meta);
    }
    
    /**
     * 检查物品是否已装填子弹
     */
    public boolean isLoaded(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer container = meta.getPersistentDataContainer();
        Byte loaded = container.get(loadedKey, PersistentDataType.BYTE);
        return loaded != null && loaded == 1;
    }
    
    /**
     * 获取装填的子弹数量
     */
    public int getBullets(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        PersistentDataContainer container = meta.getPersistentDataContainer();
        Integer bullets = container.get(bulletsKey, PersistentDataType.INTEGER);
        return bullets != null ? bullets : 0;
    }
    
    /**
     * 执行开枪
     */
    public void fire(Player player, ItemStack rouletteItem) {
        int bullets = getBullets(rouletteItem);
        if (bullets <= 0 || bullets > 6) {
            player.sendMessage(ChatColor.RED + "【俄罗斯轮盘】请先右键选择子弹数量！");
            return;
        }
        
        // 计算死亡概率: bullets/6
        int chamber = random.nextInt(6) + 1; // 1-6
        boolean died = chamber <= bullets;
        
        // 播放开枪音效
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
        
        if (died) {
            // 死亡
            player.sendMessage(ChatColor.RED + "【俄罗斯轮盘】砰！你死了！");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_DEATH, 1.0f, 1.0f);
            
            // 6颗子弹死亡时获得成就
            if (bullets == 6) {
                player.sendMessage(ChatColor.DARK_PURPLE + "§l【左轮不会卡壳】人类迷惑行为大赏");
                if (plugin.getAchievementSystem() != null) {
                    plugin.getAchievementSystem().grantAchievement(player, AchievementType.RUSSIAN_ROULETTE_6);
                }
            }
            
            // 消耗物品
            rouletteItem.setAmount(rouletteItem.getAmount() - 1);
            
            // 杀死玩家
            player.setHealth(0);
        } else {
            // 存活
            player.sendMessage(ChatColor.GREEN + "【俄罗斯轮盘】咔... 你活下来了！");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 2.0f);
            
            // 发放奖励
            giveReward(player, bullets);
            
            // 消耗物品
            rouletteItem.setAmount(rouletteItem.getAmount() - 1);
        }
    }
    
    /**
     * 根据子弹数量发放奖励
     */
    private void giveReward(Player player, int bullets) {
        LootTableSystem lootTable = plugin.getLootTableSystem();
        
        // 从配置读取随机池名称
        String commonPool = plugin.getConfig().getString("loot_pools.common_pool", "common");
        String rarePool = plugin.getConfig().getString("loot_pools.rare_pool", "rare");
        String epicPool = plugin.getConfig().getString("loot_pools.epic_pool", "epic");
        String legendaryPool = plugin.getConfig().getString("loot_pools.legendary_pool", "legendary");

        switch (bullets) {
            case 1 -> {
                player.sendMessage(ChatColor.GREEN + "奖励：一个普通物品");
                ItemStack reward = lootTable.getRandomLoot(commonPool);
                if (reward != null) {
                    player.getInventory().addItem(reward);
                }
            }
            case 2 -> {
                player.sendMessage(ChatColor.WHITE + "奖励：两个普通物品");
                for (int i = 0; i < 2; i++) {
                    ItemStack reward = lootTable.getRandomLoot(commonPool);
                    if (reward != null) {
                        player.getInventory().addItem(reward);
                    }
                }
            }
            case 3 -> {
                player.sendMessage(ChatColor.YELLOW + "奖励：一个高级装备");
                ItemStack reward = lootTable.getRandomLoot(rarePool);
                if (reward != null) {
                    player.getInventory().addItem(reward);
                }
            }
            case 4 -> {
                player.sendMessage(ChatColor.BLUE + "奖励：一个特殊物品");
                ItemStack reward = lootTable.getRandomLoot(epicPool);
                if (reward != null) {
                    player.getInventory().addItem(reward);
                }
            }
            case 5 -> {
                player.sendMessage(ChatColor.RED + "奖励：两个特殊物品");
                for (int i = 0; i < 2; i++) {
                    ItemStack reward = lootTable.getRandomLoot(epicPool);
                    if (reward != null) {
                        player.getInventory().addItem(reward);
                    }
                }
            }
            case 6 -> {
                // 6颗子弹不会走到这里，因为必定死亡
                player.sendMessage(ChatColor.DARK_PURPLE + "奖励：传说物品");
                ItemStack reward = lootTable.getRandomLoot(legendaryPool);
                if (reward != null) {
                    player.getInventory().addItem(reward);
                }
            }
        }
    }
    
    /**
     * 俄罗斯轮盘状态类（保留用于兼容性）
     */
    private static class RouletteState {
        private int shotsFired = 0;
        
        public int getShotsFired() {
            return shotsFired;
        }
        
        public void incrementShotsFired() {
            this.shotsFired++;
        }
    }
}
