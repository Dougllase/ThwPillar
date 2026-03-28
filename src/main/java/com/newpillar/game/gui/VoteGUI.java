package com.newpillar.game.gui;

import com.newpillar.NewPillar;
import com.newpillar.game.GameManager;
import com.newpillar.game.PlayerData;
import com.newpillar.game.VoteManager;
import com.newpillar.game.enums.PlayerState;
import com.newpillar.game.enums.RuleType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * 投票GUI
 * 提供图形化界面进行规则投票
 */
public class VoteGUI implements Listener {
    private final NewPillar plugin;
    private final GameManager gameManager;
    private final VoteManager voteManager;
    
    private static final String GUI_TITLE = "§6§l规则投票";
    private static final int GUI_SIZE = 27;
    
    public VoteGUI(NewPillar plugin, GameManager gameManager, VoteManager voteManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.voteManager = voteManager;
        
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
    
    /**
     * 打开投票GUI
     */
    public void openVoteGUI(Player player) {
        // 检查玩家是否在游戏中
        PlayerData data = gameManager.getPlayerData(player.getUniqueId());
        if (data == null || (data.getState() != PlayerState.READY && data.getState() != PlayerState.LOBBY)) {
            player.sendMessage("§c只有已加入游戏的玩家才能投票！");
            return;
        }
        
        Inventory gui = Bukkit.createInventory(null, GUI_SIZE, Component.text(GUI_TITLE));
        
        // 获取投票选项
        List<RuleType> votingRules = voteManager.getVotingRules();
        
        // 在GUI中放置规则选项
        int[] slots = {11, 13, 15}; // 中间三个槽位
        
        for (int i = 0; i < votingRules.size() && i < slots.length; i++) {
            RuleType rule = votingRules.get(i);
            ItemStack ruleItem = createRuleItem(rule, i + 1);
            gui.setItem(slots[i], ruleItem);
        }
        
        // 添加装饰边框
        fillBorder(gui);
        
        player.openInventory(gui);
    }
    
    /**
     * 创建规则选项物品
     */
    private ItemStack createRuleItem(RuleType rule, int number) {
        Material material = getMaterialForRule(rule);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            // 设置名称
            String colorCode = "§" + rule.getColor();
            meta.displayName(Component.text(colorCode + "§l[" + number + "] " + rule.getName())
                    .decoration(TextDecoration.ITALIC, false));
            
            // 设置Lore
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Component.text("§7" + rule.getDescription()).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("§e点击投票").color(TextColor.color(0xFFD700))
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));
            
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    /**
     * 根据规则类型获取对应材质
     */
    private Material getMaterialForRule(RuleType rule) {
        return switch (rule) {
            case NONE -> Material.BARRIER;
            case SMALL_CUTE -> Material.RED_MUSHROOM;  // 小小的也很可爱
            case BIG -> Material.GOLDEN_APPLE;  // 大！大！大！
            case MY_PARTNER -> Material.FOX_SPAWN_EGG;  // 我的伙伴
            case PUNCH -> Material.NETHERITE_SWORD;  // 一击必杀
            case INV_EXCHANGE -> Material.ENDER_CHEST;  // 背包交换
            case VOID_MERCY -> Material.ENDER_PEARL;  // 虚空的仁慈
            default -> Material.PAPER;
        };
    }
    
    /**
     * 填充边框
     */
    private void fillBorder(Inventory gui) {
        ItemStack borderItem = createBorderItem();
        
        // 顶部和底部边框
        for (int i = 0; i < 9; i++) {
            gui.setItem(i, borderItem);
            gui.setItem(i + 18, borderItem);
        }
        
        // 左右边框
        for (int i = 0; i < 3; i++) {
            gui.setItem(i * 9, borderItem);
            gui.setItem(i * 9 + 8, borderItem);
        }
    }
    
    /**
     * 创建边框物品
     */
    private ItemStack createBorderItem() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            item.setItemMeta(meta);
        }
        return item;
    }
    
    /**
     * 处理GUI点击事件
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        // 检查是否是投票GUI
        if (event.getView().title().equals(Component.text(GUI_TITLE))) {
            event.setCancelled(true);
            
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;
            if (clickedItem.getType() == Material.GRAY_STAINED_GLASS_PANE) return;
            
            // 获取点击的规则
            int slot = event.getSlot();
            int[] slots = {11, 13, 15};
            int voteIndex = -1;
            
            for (int i = 0; i < slots.length; i++) {
                if (slots[i] == slot) {
                    voteIndex = i + 1;
                    break;
                }
            }
            
            if (voteIndex > 0) {
                // 执行投票
                voteManager.playerVoteRule(player, voteIndex);
                player.closeInventory();
            }
        }
    }
    
    /**
     * 处理GUI关闭事件
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // 可以在这里添加关闭时的逻辑
    }
}
