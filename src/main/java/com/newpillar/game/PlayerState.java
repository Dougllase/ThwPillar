package com.newpillar.game;

/**
 * 玩家状态枚举
 */
public enum PlayerState {
    LOBBY,      // 在大厅
    READY,      // 已准备
    INGAME,     // 游戏中
    SPECTATOR,  // 观察者
    OUT         // 已出局
}
