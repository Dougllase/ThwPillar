# ThwReward 集成分支说明

## 分支信息

**分支名称**: `thwreward-standalone`

**用途**: 此分支包含主插件（NewPillar）与 ThwReward 子插件的集成代码。

## 主要内容

### 1. 集成文件
- `src/main/java/com/newpillar/integration/ThwRewardIntegration.java`
  - 负责与 ThwReward 子插件的通信
  - 支持可选依赖，ThwReward 缺失时自动降级
  - 提供游戏事件转发（开始、结束、击杀等）

### 2. 主插件完整性
此分支**保留所有主插件内容**，包括：
- 完整的游戏逻辑
- 所有命令和监听器
- 数据库管理
- 成就系统
- 物品系统
- 地图系统
- 等等...

### 3. 与主分支的区别
- 添加了 ThwReward 集成类
- 主插件通过集成类与 ThwReward 通信
- ThwReward 作为可选依赖，不影响主插件独立运行

## 使用方式

### 作为主插件运行
此分支可以直接作为主插件编译运行，不依赖 ThwReward：
```bash
mvn clean package
```

### 与 ThwReward 配合使用
1. 安装 ThwNewPillar（此分支编译的 JAR）
2. 安装 ThwReward 子插件
3. 两个插件会自动协同工作

## 开发说明

### 添加新的集成点
在 `ThwRewardIntegration.java` 中添加新的方法：

```java
public void onSomeEvent(Data data) {
    if (!isEnabled()) return;
    
    try {
        // 调用 ThwReward 的方法
        Class<?> managerClass = gameRewardManager.getClass();
        Method method = managerClass.getMethod("onSomeEvent", Data.class);
        method.invoke(gameRewardManager, data);
    } catch (Exception e) {
        plugin.getLogger().log(Level.FINE, "调用失败: " + e.getMessage());
    }
}
```

### 在主插件中调用
```java
// 在 NewPillar.java 中
thwRewardIntegration.onSomeEvent(data);
```

## 相关仓库

- **ThwReward 子插件**: 独立的奖励系统插件
  - 提供金币奖励、跨服招人等功能
  - 可作为独立插件或配合主插件使用

## 注意事项

1. 此分支的代码与主分支（main）保持同步
2. 集成代码使用反射调用，避免编译时依赖
3. ThwReward 是可选依赖，缺失时主插件功能不受影响
