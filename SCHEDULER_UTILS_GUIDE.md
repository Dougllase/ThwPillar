# SchedulerUtils 使用指南

## 1. 初始化

在插件主类的 `onEnable()` 方法中初始化：

```java
package com.newpillar;

import com.newpillar.utils.SchedulerUtils;

public class NewPillar extends JavaPlugin {
    @Override
    public void onEnable() {
        // 初始化调度器工具类
        SchedulerUtils.init(this);
        
        // 其他初始化代码...
    }
}
```

## 2. 重构对比示例

### 示例 1：立即执行任务

**重构前：**
```java
// GameManager.java 第260行
Bukkit.getRegionScheduler().execute(this.plugin, player.getLocation(), () -> {
    player.setGameMode(GameMode.ADVENTURE);
});
```

**重构后：**
```java
// 简洁明了
SchedulerUtils.runOnLocation(player.getLocation(), () -> {
    player.setGameMode(GameMode.ADVENTURE);
});

// 或者使用玩家专用方法
SchedulerUtils.runOnPlayer(player, () -> {
    player.setGameMode(GameMode.ADVENTURE);
});
```

---

### 示例 2：传送玩家

**重构前：**
```java
// GameManager.java 第655行
player.teleportAsync(targetLoc).thenRun(() -> {
    Bukkit.getRegionScheduler().execute(this.plugin, targetLoc, () -> {
        player.setGameMode(GameMode.SURVIVAL);
        player.setHealth(20.0);
    });
});
```

**重构后：**
```java
// 方式1：传送后在目标位置执行
SchedulerUtils.teleport(player, targetLoc, () -> {
    player.setGameMode(GameMode.SURVIVAL);
    player.setHealth(20.0);
});

// 方式2：传送后在玩家线程执行
SchedulerUtils.teleportAndRunOnPlayer(player, targetLoc, () -> {
    player.setGameMode(GameMode.SURVIVAL);
    player.setHealth(20.0);
});
```

---

### 示例 3：定时重复任务

**重构前：**
```java
// GameManager.java 第356行
this.countdownTask = Bukkit.getRegionScheduler().runAtFixedRate(
    this.plugin, 
    world, 
    0, 
    0, 
    scheduledTask -> {
        // 倒计时逻辑
        if (countdown <= 0) {
            scheduledTask.cancel();
            return;
        }
        countdown--;
    }
);
```

**重构后：**
```java
this.countdownTask = SchedulerUtils.runTimerOnWorld(world, 0, 0, scheduledTask -> {
    // 倒计时逻辑
    if (countdown <= 0) {
        SchedulerUtils.cancel(scheduledTask);
        return;
    }
    countdown--;
});
```

---

### 示例 4：全局区域操作

**重构前：**
```java
// GameManager.java 第132行
Bukkit.getGlobalRegionScheduler().execute(this.plugin, () -> {
    world.setGameRule(GameRule.KEEP_INVENTORY, false);
    world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
    world.setGameRule(GameRule.SHOW_DEATH_MESSAGES, false);
});
```

**重构后：**
```java
SchedulerUtils.runGlobal(() -> {
    world.setGameRule(GameRule.KEEP_INVENTORY, false);
    world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
    world.setGameRule(GameRule.SHOW_DEATH_MESSAGES, false);
});
```

---

### 示例 5：延迟执行

**重构前：**
```java
// 延迟3秒后执行
Bukkit.getRegionScheduler().runDelayed(
    this.plugin, 
    location, 
    scheduledTask -> {
        // 延迟执行的代码
        spawnFirework(location);
    }, 
    60L  // 3秒 = 60 tick
);
```

**重构后：**
```java
SchedulerUtils.runLaterOnLocation(location, 60L, () -> {
    spawnFirework(location);
});
```

---

### 示例 6：取消任务

**重构前：**
```java
if (this.countdownTask != null) {
    this.countdownTask.cancel();
    this.countdownTask = null;
}
```

**重构后：**
```java
SchedulerUtils.cancelSafely(this.countdownTask);
this.countdownTask = null;

// 或者直接（如果确定不为null）
SchedulerUtils.cancel(this.countdownTask);
```

---

## 3. 方法速查表

### 立即执行
| 方法 | 用途 | 适用场景 |
|------|------|---------|
| `runGlobal(Runnable)` | 全局区域 | 世界设置、游戏规则 |
| `runOnWorld(World, Runnable)` | 指定世界 | 世界内操作 |
| `runOnLocation(Location, Runnable)` | 指定位置 | 位置相关操作 |
| `runOnEntity(Entity, Runnable)` | 实体线程 | 实体属性修改 |
| `runOnPlayer(Player, Runnable)` | 玩家线程 | 玩家属性修改 |

### 延迟执行
| 方法 | 用途 |
|------|------|
| `runLaterGlobal(long, Runnable)` | 延迟后全局执行 |
| `runLaterOnWorld(World, long, Runnable)` | 延迟后在世界执行 |
| `runLaterOnLocation(Location, long, Runnable)` | 延迟后在位置执行 |
| `runLaterOnEntity(Entity, long, Runnable)` | 延迟后在实体线程执行 |

### 定时重复
| 方法 | 用途 |
|------|------|
| `runTimerGlobal(long, long, Consumer<ScheduledTask>)` | 全局定时任务 |
| `runTimerOnWorld(World, long, long, Consumer<ScheduledTask>)` | 世界定时任务 |
| `runTimerOnLocation(Location, long, long, Consumer<ScheduledTask>)` | 位置定时任务 |
| `runTimerOnEntity(Entity, long, long, Consumer<ScheduledTask>)` | 实体定时任务 |

### 传送
| 方法 | 用途 |
|------|------|
| `teleport(Player, Location)` | 传送玩家 |
| `teleport(Player, Location, Runnable)` | 传送+回调 |
| `teleportAndRunOnPlayer(Player, Location, Runnable)` | 传送+玩家线程回调 |

### 取消任务
| 方法 | 用途 |
|------|------|
| `cancel(ScheduledTask)` | 取消任务 |
| `cancelSafely(ScheduledTask)` | 安全取消（处理null） |

---

## 4. 逐步重构计划

### 第一阶段：核心管理器（优先级：高）
1. **GameManager.java** - 使用最频繁，约48处
2. **PillarManager.java** - 游戏核心逻辑
3. **BorderManager.java** - 边界管理
4. **CollapseManager.java** - 坍塌管理

### 第二阶段：物品系统（优先级：中）
1. **ItemSystem.java**
2. **ItemEffectManager.java**
3. **VanillaItemEffectManager.java**
4. **ExcaliburManager.java**

### 第三阶段：事件和地图（优先级：中）
1. **EventSystem.java**
2. **LuckyBlockSystem.java**
3. **TemplateMapGenerator.java**
4. **MapRegion.java**

### 第四阶段：其他（优先级：低）
1. **PlayerListener.java**
2. **RecruitmentManager.java**
3. **RuleSystem.java**
4. **DialogManager.java**
5. **AbstractDialog.java**

---

## 5. 重构检查清单

- [ ] 在NewPillar主类中添加初始化代码
- [ ] 重构GameManager.java
- [ ] 重构PillarManager.java
- [ ] 重构BorderManager.java
- [ ] 重构CollapseManager.java
- [ ] 重构ItemSystem.java
- [ ] 重构ItemEffectManager.java
- [ ] 重构VanillaItemEffectManager.java
- [ ] 重构ExcaliburManager.java
- [ ] 重构EventSystem.java
- [ ] 重构LuckyBlockSystem.java
- [ ] 重构TemplateMapGenerator.java
- [ ] 重构MapRegion.java
- [ ] 重构PlayerListener.java
- [ ] 重构RecruitmentManager.java
- [ ] 重构RuleSystem.java
- [ ] 重构DialogManager.java
- [ ] 重构AbstractDialog.java
- [ ] 测试所有功能正常

---

## 6. 注意事项

1. **初始化检查** - 如果忘记调用`SchedulerUtils.init(this)`，会抛出异常
2. **线程安全** - 工具类只是简化API，不解决业务逻辑的线程安全问题
3. **性能** - 工具类本身开销极小，不会成为性能瓶颈
4. **兼容性** - 仅适用于Folia服务器，不要在Paper/Spigot上使用

---

## 7. 预期效果

重构完成后：
- ✅ 代码量减少约40-50%
- ✅ 可读性大幅提升
- ✅ 维护成本降低
- ✅ 统一错误处理
- ✅ 便于后续功能扩展
