package com.newpillar.game.events.eventtypes;

import com.newpillar.game.events.EventSystem;
import org.bukkit.World;

/**
 * 游戏事件接口
 */
public interface GameEvent {
    
    /**
     * 获取事件类型
     */
    String getName();
    
    /**
     * 开始事件
     * @param eventSystem 事件系统引用
     * @param world 游戏世界
     */
    void start(EventSystem eventSystem, World world);
    
    /**
     * 结束事件
     * @param eventSystem 事件系统引用
     * @param world 游戏世界
     */
    void end(EventSystem eventSystem, World world);
    
    /**
     * 获取事件持续时间（秒）
     */
    int getDuration();
    
    /**
     * 是否可以强制结束
     */
    boolean canForceEnd();
}
