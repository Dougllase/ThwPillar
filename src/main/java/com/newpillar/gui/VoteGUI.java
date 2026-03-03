package com.newpillar.gui;

import com.newpillar.NewPillar;
import com.newpillar.game.GameManager;
import com.newpillar.game.RuleType;
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

public class VoteGUI implements Listener {
    private final NewPillar plugin;
    private final GameManager gameManager;
    private static final String GUI_TITLE = "§6§l规则投票";
    private static final int GUI_SIZE = 27;

    public VoteGUI(NewPillar plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openVoteGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, GUI_SIZE, GUI_TITLE);

        // 检查投票是否已锁定
        if (gameManager.isVotingLocked()) {
            player.sendMessage("§c§l[幸运之柱] §c投票已锁定，无法更改投票！");
            return;
        }

        // 获取投票选项
        List<RuleType> votingRules = gameManager.getVotingRules();
        if (votingRules == null || votingRules.isEmpty()) {
            player.sendMessage("§c§l[幸运之柱] §c当前没有正在进行的投票！");
            return;
        }

        // 在GUI中放置投票选项
        int[] slots = {11, 13, 15}; // 三个选项的位置
        for (int i = 0; i < votingRules.size() && i < slots.length; i++) {
            RuleType rule = votingRules.get(i);
            ItemStack voteItem = createVoteItem(rule, i + 1);
            gui.setItem(slots[i], voteItem);
        }

        // 添加装饰性玻璃板
        ItemStack glass = createGlassPane();
        for (int i = 0; i < GUI_SIZE; i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, glass);
            }
        }

        player.openInventory(gui);
    }

    private ItemStack createVoteItem(RuleType rule, int optionNumber) {
        Material material = getMaterialForRule(rule);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        // 设置显示名称
        String ruleName = getRuleDisplayName(rule);
        meta.displayName(Component.text("§e§l选项 " + optionNumber + ": " + ruleName)
                .decoration(TextDecoration.ITALIC, false));

        // 设置Lore
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("§7" + getRuleDescription(rule)));
        lore.add(Component.empty());

        // 显示当前票数
        int votes = gameManager.getRuleVotes(rule);
        lore.add(Component.text("§e当前票数: §f" + votes));

        lore.add(Component.empty());
        lore.add(Component.text("§e点击进行投票").color(TextColor.color(0xFFFF55)));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createGlassPane() {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        meta.displayName(Component.empty());
        glass.setItemMeta(meta);
        return glass;
    }

    private Material getMaterialForRule(RuleType rule) {
        return switch (rule) {
            case NONE -> Material.BARRIER;
            case SMALL_CUTE -> Material.RED_MUSHROOM;
            case BIG -> Material.OAK_LOG;
            case MY_PARTNER -> Material.FOX_SPAWN_EGG;
            case PUNCH -> Material.DIAMOND_SWORD;
            case INV_EXCHANGE -> Material.ENDER_CHEST;
            case VOID_MERCY -> Material.ENDER_PEARL;
            default -> Material.PAPER;
        };
    }

    private String getRuleDisplayName(RuleType rule) {
        return switch (rule) {
            case NONE -> "§f无规则";
            case SMALL_CUTE -> "§d小小的也很可爱❤";
            case BIG -> "§6大！大！大！";
            case MY_PARTNER -> "§9我的伙伴";
            case PUNCH -> "§c一击必杀！";
            case INV_EXCHANGE -> "§a背包交换";
            case VOID_MERCY -> "§5虚空的仁慈";
            default -> "§7未知规则";
        };
    }

    private String getRuleDescription(RuleType rule) {
        return switch (rule) {
            case NONE -> "没有花里胡哨的规则，只有原汁原味的幸运玩法。";
            case SMALL_CUTE -> "玩家尺寸缩小为原来的1/3";
            case BIG -> "玩家尺寸增大为原来的3/2";
            case MY_PARTNER -> "获得一只狐狸，狐狸存活时给玩家提供力量和生命回复";
            case PUNCH -> "玩家攻击伤害变成40";
            case INV_EXCHANGE -> "随机事件固定为背包交换";
            case VOID_MERCY -> "掉落虚空将被向上传送60格";
            default -> "未知效果";
        };
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;
        if (clickedItem.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        // 获取点击的选项
        int slot = event.getRawSlot();
        int optionIndex = -1;
        if (slot == 11) optionIndex = 0;
        else if (slot == 13) optionIndex = 1;
        else if (slot == 15) optionIndex = 2;

        if (optionIndex == -1) return;

        List<RuleType> votingRules = gameManager.getVotingRules();
        if (optionIndex >= votingRules.size()) return;

        RuleType selectedRule = votingRules.get(optionIndex);

        // 执行投票
        gameManager.voteForRule(player, selectedRule);

        // 关闭GUI
        player.closeInventory();
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // 可以在这里添加关闭时的逻辑
    }
}
