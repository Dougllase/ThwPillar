package com.newpillar.game;

import com.newpillar.NewPillar;
import com.newpillar.game.enums.EventType;
import com.newpillar.game.items.LootTableSystem;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * 薛定谔的猫管理器
 * - 物品预知：预生成24个物品，获取时顺延并补全
 * - 事件预知：预生成8个事件，触发时顺延并补全
 * - 历史记录：物品历史32个，事件历史8个
 */
public class SchrodingerCatManager {
    private final NewPillar plugin;
    private final LootTableSystem lootTableSystem;
    
    // 物品预知队列（24个）
    private final LinkedList<ItemStack> itemPredictionQueue = new LinkedList<>();
    private static final int ITEM_PREDICTION_SIZE = 24;
    
    // 事件预知队列（8个）
    private final LinkedList<EventType> eventPredictionQueue = new LinkedList<>();
    private static final int EVENT_PREDICTION_SIZE = 8;
    
    // 历史记录
    private final LinkedList<ItemStack> itemHistory = new LinkedList<>();
    private final LinkedList<EventType> eventHistory = new LinkedList<>();
    private static final int MAX_ITEM_HISTORY = 32;
    private static final int MAX_EVENT_HISTORY = 8;
    
    // 洞察模式状态
    private boolean insightModeEnabled = false;
    
    // 薛定谔的猫机制是否启用
    private boolean enabled = false;
    
    public SchrodingerCatManager(NewPillar plugin) {
        this.plugin = plugin;
        this.lootTableSystem = plugin.getLootTableSystem();
    }
    
    /**
     * 启用/禁用薛定谔的猫机制
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            // 启用时预生成物品和事件
            refillItemPredictions();
            refillEventPredictions();
            plugin.getLogger().info("[薛定谔的猫] 机制已启用，预生成 " + ITEM_PREDICTION_SIZE + " 个物品和 " + EVENT_PREDICTION_SIZE + " 个事件");
        } else {
            // 禁用时清空队列
            itemPredictionQueue.clear();
            eventPredictionQueue.clear();
            plugin.getLogger().info("[薛定谔的猫] 机制已禁用");
        }
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * 设置洞察模式
     */
    public void setInsightMode(boolean enabled) {
        this.insightModeEnabled = enabled;
        plugin.getLogger().info("[薛定谔的猫] 洞察模式 " + (enabled ? "已启用" : "已禁用"));
    }
    
    public boolean isInsightModeEnabled() {
        return insightModeEnabled;
    }
    
    // ==================== 物品预知机制 ====================
    
    /**
     * 预生成物品队列
     */
    private void refillItemPredictions() {
        while (itemPredictionQueue.size() < ITEM_PREDICTION_SIZE) {
            ItemStack item = lootTableSystem.generateLoot();
            if (item != null) {
                itemPredictionQueue.addLast(item.clone());
            }
        }
    }
    
    /**
     * 获取下一个物品（从预生成队列中取出并补全）
     */
    public ItemStack getNextItem() {
        if (!enabled || itemPredictionQueue.isEmpty()) {
            return lootTableSystem.generateLoot();
        }
        
        // 取出队列第一个物品
        ItemStack item = itemPredictionQueue.pollFirst();
        
        // 添加到历史记录
        addToItemHistory(item.clone());
        
        // 补全队列
        refillItemPredictions();
        
        return item;
    }
    
    /**
     * 查看物品预知列表（不改变队列）
     */
    public List<ItemStack> getItemPredictions() {
        List<ItemStack> predictions = new ArrayList<>();
        for (ItemStack item : itemPredictionQueue) {
            predictions.add(item.clone());
        }
        return predictions;
    }
    
    /**
     * 设置指定位置的物品
     */
    public boolean setItemAt(int index, ItemStack item) {
        if (index < 0 || index >= itemPredictionQueue.size()) {
            return false;
        }
        
        List<ItemStack> tempList = new ArrayList<>(itemPredictionQueue);
        tempList.set(index, item.clone());
        itemPredictionQueue.clear();
        itemPredictionQueue.addAll(tempList);
        return true;
    }
    
    /**
     * 设置指定位置为特殊物品
     */
    public boolean setSpecialItemAt(int index, String specialItemId) {
        if (index < 0 || index >= itemPredictionQueue.size()) {
            return false;
        }
        
        ItemStack specialItem = lootTableSystem.createSpecialItemById(specialItemId.toLowerCase());
        if (specialItem == null) {
            return false;
        }
        
        return setItemAt(index, specialItem);
    }
    
    // ==================== 事件预知机制 ====================
    
    /**
     * 预生成事件队列
     */
    private void refillEventPredictions() {
        while (eventPredictionQueue.size() < EVENT_PREDICTION_SIZE) {
            // 随机生成事件ID (1-36，排除31-35的NOTHING事件)
            int eventId;
            do {
                eventId = (int) (Math.random() * 36) + 1;
            } while (eventId >= 31 && eventId <= 35); // 跳过NOTHING事件
            
            EventType event = EventType.getById(eventId);
            if (event != null && event.isRealEvent()) {
                eventPredictionQueue.addLast(event);
            }
        }
    }
    
    /**
     * 获取下一个事件（从预生成队列中取出并补全）
     */
    public EventType getNextEvent() {
        if (!enabled || eventPredictionQueue.isEmpty()) {
            // 随机生成事件ID (1-36，排除31-35的NOTHING事件)
            int eventId;
            do {
                eventId = (int) (Math.random() * 36) + 1;
            } while (eventId >= 31 && eventId <= 35); // 跳过NOTHING事件
            return EventType.getById(eventId);
        }
        
        // 取出队列第一个事件
        EventType event = eventPredictionQueue.pollFirst();
        
        // 添加到历史记录
        addToEventHistory(event);
        
        // 补全队列
        refillEventPredictions();
        
        return event;
    }
    
    /**
     * 查看事件预知列表（不改变队列）
     */
    public List<EventType> getEventPredictions() {
        return new ArrayList<>(eventPredictionQueue);
    }
    
    /**
     * 设置指定位置的事件
     */
    public boolean setEventAt(int index, EventType event) {
        if (index < 0 || index >= eventPredictionQueue.size()) {
            return false;
        }
        
        List<EventType> tempList = new ArrayList<>(eventPredictionQueue);
        tempList.set(index, event);
        eventPredictionQueue.clear();
        eventPredictionQueue.addAll(tempList);
        return true;
    }
    
    // ==================== 历史记录 ====================
    
    /**
     * 添加到物品历史
     */
    private void addToItemHistory(ItemStack item) {
        itemHistory.addFirst(item);
        while (itemHistory.size() > MAX_ITEM_HISTORY) {
            itemHistory.removeLast();
        }
    }
    
    /**
     * 添加到事件历史
     */
    private void addToEventHistory(EventType event) {
        eventHistory.addFirst(event);
        while (eventHistory.size() > MAX_EVENT_HISTORY) {
            eventHistory.removeLast();
        }
    }
    
    /**
     * 获取物品历史
     */
    public List<ItemStack> getItemHistory() {
        List<ItemStack> history = new ArrayList<>();
        for (ItemStack item : itemHistory) {
            history.add(item.clone());
        }
        return history;
    }
    
    /**
     * 获取事件历史
     */
    public List<EventType> getEventHistory() {
        return new ArrayList<>(eventHistory);
    }
    
    /**
     * 清空历史记录
     */
    public void clearHistory() {
        itemHistory.clear();
        eventHistory.clear();
    }
    
    // ==================== 洞察模式 ====================
    
    /**
     * 获取洞察模式下的物品预览（前8个）
     */
    public List<ItemStack> getInsightItems() {
        List<ItemStack> insight = new ArrayList<>();
        int count = 0;
        for (ItemStack item : itemPredictionQueue) {
            if (count >= 8) break;
            insight.add(item.clone());
            count++;
        }
        return insight;
    }
    
    /**
     * 获取洞察模式下的事件预览（前8个）
     */
    public List<EventType> getInsightEvents() {
        return new ArrayList<>(eventPredictionQueue);
    }
}
