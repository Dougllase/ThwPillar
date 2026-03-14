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

import java.util.*;

/**
 * 俄罗斯轮盘枪系统
 * 
 * 设计规则：
 * - 6发弹仓，选择装1-6颗子弹
 * - 死亡概率 = 子弹数/6 (16.6%, 33.3%, 50%, 66.6%, 83.3%, 100%)
 * - 存活后可继续开枪，直到6颗子弹打完
 * - 6颗子弹：必定死亡，获得成就【左轮不会卡壳】
 * - 子弹打完后物品消失
 */
public class RussianRouletteGUI implements Listener {
    private final NewPillar plugin;
    private final Random random = new Random();
    
    // 跟踪玩家的俄罗斯轮盘枪状态
    private final Map<UUID, RouletteState> playerStates = new HashMap<>();
    
    // GUI标题
    private static final String GUI_TITLE = "§8§l俄罗斯轮盘 - 选择子弹数量";
    
    public RussianRouletteGUI(NewPillar plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
    
    /**
     * 打开俄罗斯轮盘选择界面
     */
    public void openRouletteGUI(Player player) {
        UUID uuid = player.getUniqueId();
        RouletteState state = playerStates.getOrDefault(uuid, new RouletteState());
        
        // 如果已经打完了6颗子弹，移除物品并返回
        if (state.getShotsFired() >= 6) {
            player.sendMessage(ChatColor.RED + "【俄罗斯轮盘】子弹已打完，左轮枪已消失！");
            playerStates.remove(uuid);
            return;
        }
        
        Inventory gui = Bukkit.createInventory(null, 27, GUI_TITLE);
        
        // 填充背景
        ItemStack background = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            gui.setItem(i, background);
        }
        
        // 设置6个选项（下界合金锭）- 左轮手枪弹仓形状排列
        // 位置：10, 12, 14, 16（上排）和 20, 24（下排）形成圆形
        int remainingShots = 6 - state.getShotsFired();
        
        gui.setItem(10, createBulletOption(1, "§a【绿色】1颗子弹", "§7浅尝辄止…", "§f死亡概率: 16.6%", remainingShots >= 1));
        gui.setItem(12, createBulletOption(2, "§f【灰色】2颗子弹", "§f慢慢有趣…", "§f死亡概率: 33.3%", remainingShots >= 2));
        gui.setItem(14, createBulletOption(3, "§e【黄色】3颗子弹", "§b有点意思…", "§f死亡概率: 50%", remainingShots >= 3));
        gui.setItem(16, createBulletOption(4, "§e【黄色】4颗子弹", "§9勇气可嘉…", "§f死亡概率: 66.6%", remainingShots >= 4));
        gui.setItem(20, createBulletOption(5, "§c【红色】5颗子弹", "§4孤注一掷…", "§f死亡概率: 83.3%", remainingShots >= 5));
        gui.setItem(24, createBulletOption(6, "§5【紫色】6颗子弹", "§4你认真的…？", "§f§l死亡概率: 100%", remainingShots >= 6));
        
        // 显示当前状态
        if (state.getShotsFired() > 0) {
            player.sendMessage(ChatColor.GRAY + "【俄罗斯轮盘】已开枪 " + state.getShotsFired() + "/6 次，剩余 " + remainingShots + " 发");
        }
        
        playerStates.put(uuid, state);
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
                meta.displayName(Component.text(name.replace("§", ""))
                        .color(getBulletColor(bullets))
                        .decoration(TextDecoration.BOLD, true));
                meta.lore(Arrays.asList(
                    Component.text(""),
                    Component.text(desc1.replace("§", "")).color(TextColor.color(0xAAAAAA)),
                    Component.text(desc2.replace("§", "")).color(TextColor.color(0xFFFFFF)),
                    Component.text(""),
                    Component.text("点击选择").color(TextColor.color(0x55FF55))
                ));
            } else {
                meta.displayName(Component.text("§7【已禁用】" + bullets + "颗子弹")
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
            UUID uuid = player.getUniqueId();
            RouletteState state = playerStates.getOrDefault(uuid, new RouletteState());
            
            // 检查是否有足够的剩余子弹
            int remainingShots = 6 - state.getShotsFired();
            if (bullets > remainingShots) {
                player.sendMessage(ChatColor.RED + "【俄罗斯轮盘】剩余子弹不足！");
                return;
            }
            
            player.closeInventory();
            
            // 播放选择音效
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
            
            // 执行开枪
            Bukkit.getScheduler().runTaskLater(plugin, () -> fireRoulette(player, bullets, state), 5L);
        }
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;
        
        UUID uuid = player.getUniqueId();
        RouletteState state = playerStates.get(uuid);
        
        // 如果没有状态（已经开过枪）或者已经打完了，不返还物品
        if (state == null || state.getShotsFired() >= 6) {
            return;
        }
        
        // 如果还没开过枪，返还物品
        if (state.getShotsFired() == 0) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                ItemStack roulette = plugin.getSpecialItemManager().createSpecialItem(SpecialItemManager.SpecialItemType.RUSSIAN_ROULETTE);
                player.getInventory().addItem(roulette);
                player.sendMessage(ChatColor.GRAY + "未作选择，俄罗斯轮盘枪已返还");
                playerStates.remove(uuid);
            }, 1L);
        }
        // 如果已经开过枪但没打完，保留状态，物品已经在玩家手中
    }
    
    /**
     * 开枪执行俄罗斯轮盘
     * 
     * @param player 玩家
     * @param bullets 选择的子弹数
     * @param state 当前状态
     */
    private void fireRoulette(Player player, int bullets, RouletteState state) {
        UUID uuid = player.getUniqueId();
        
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
            
            // 清除状态
            playerStates.remove(uuid);
            
            // 杀死玩家
            player.setHealth(0);
        } else {
            // 存活
            player.sendMessage(ChatColor.GREEN + "【俄罗斯轮盘】咔... 你活下来了！");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 2.0f);
            
            // 增加已开枪次数
            state.incrementShotsFired();
            
            // 发放奖励
            giveReward(player, bullets);
            
            // 检查是否还有剩余子弹
            if (state.getShotsFired() >= 6) {
                player.sendMessage(ChatColor.RED + "【俄罗斯轮盘】子弹已打完，左轮枪已消失！");
                playerStates.remove(uuid);
            } else {
                // 还有子弹，延迟后重新打开选择界面
                int remainingShots = 6 - state.getShotsFired();
                player.sendMessage(ChatColor.YELLOW + "【俄罗斯轮盘】剩余 " + remainingShots + " 发子弹，3秒后再次选择...");
                
                playerStates.put(uuid, state);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline() && player.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                        openRouletteGUI(player);
                    }
                }, 60L); // 3秒后重新打开
            }
        }
    }
    
    /**
     * 根据子弹数量发放奖励
     * 使用对应的战利品表：
     * - common: 普通物品（系统定时给予的物品）
     * - rare: 高级装备（钻石、下界合金装备、弓弩）
     * - epic: 特殊物品（有特殊技能或属性的物品）
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
     * 俄罗斯轮盘状态类
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
