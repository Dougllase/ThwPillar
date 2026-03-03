package com.newpillar.gui;

import com.newpillar.NewPillar;
import com.newpillar.game.GameManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class LobbyVoteItem implements Listener {
    private final NewPillar plugin;
    private final GameManager gameManager;
    private final VoteGUI voteGUI;
    private static final String VOTE_ITEM_NAME = "§6§l规则投票";
    private static final int VOTE_ITEM_SLOT = 4; // 物品栏中间位置

    public LobbyVoteItem(NewPillar plugin, GameManager gameManager, VoteGUI voteGUI) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.voteGUI = voteGUI;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void giveVoteItem(Player player) {
        ItemStack voteItem = createVoteItem();
        player.getInventory().setItem(VOTE_ITEM_SLOT, voteItem);
    }

    public void removeVoteItem(Player player) {
        player.getInventory().setItem(VOTE_ITEM_SLOT, null);
    }

    private ItemStack createVoteItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(VOTE_ITEM_NAME)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("§7右键点击打开投票界面"));
        lore.add(Component.text("§7选择本局游戏规则"));
        lore.add(Component.empty());
        lore.add(Component.text("§e点击打开投票菜单"));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || item.getType() != Material.BOOK) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        Component displayName = meta.displayName();
        if (displayName == null) return;

        String name = displayName.toString();
        if (!name.contains("规则投票")) return;

        // 检查玩家是否在大厅
        if (gameManager.getGameStatus() != GameManager.GameStatus.LOBBY) {
            return;
        }

        event.setCancelled(true);

        // 检查是否是右键
        if (event.getAction() == Action.RIGHT_CLICK_AIR || 
            event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            voteGUI.openVoteGUI(player);
        }
    }

    public void giveVoteItemToAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            giveVoteItem(player);
        }
    }

    public void removeVoteItemFromAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeVoteItem(player);
        }
    }
}
