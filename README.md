# 幸运之柱 (Lucky Pillar) - Minecraft 插件

[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.11-blue.svg)](https://papermc.io/)
[![Server Software](https://img.shields.io/badge/Server-Folia-orange.svg)](https://papermc.io/software/folia)
[![Java Version](https://img.shields.io/badge/Java-21+-green.svg)](https://adoptium.net/)

> **🌿 当前分支**: `thwreward-standalone` - 包含 ThwReward 子插件集成
> 
> 此分支包含主插件与 ThwReward 奖励子插件的集成代码。ThwReward 是可选依赖，不影响主插件独立运行。
> 
> 查看 [THWREWARD_BRANCH.md](THWREWARD_BRANCH.md) 了解分支详情。

一个基于Folia的高性能Minecraft小游戏插件，支持多人在线对战。

## 游戏简介

幸运之柱是一款快节奏的PVP小游戏，玩家出生在随机分配的柱子上，通过收集物品、击败对手成为最后的幸存者。

### 游戏特色

- 🎮 支持2-12人同时游戏
- 🗺️ 多种地图类型（羊毛、玻璃、地狱、海洋、月球、虚空等）
- 🎲 随机规则投票系统
- 📦 丰富的战利品系统
- 🎭 特殊物品和技能
- 📊 统计系统和成就系统
- 🏆 排行榜功能

## 服务器要求

### 必需环境

| 项目 | 要求 |
|------|------|
| **Minecraft版本** | 1.21.11 |
| **服务器软件** | Folia |
| **Java版本** | Java 21 或更高 |
| **内存** | 至少 2GB RAM（推荐4GB） |
| **数据库** | MySQL 5.7+ 或 MariaDB 10.3+ |

### 推荐配置

```bash
# 启动参数示例
java -Xms2G -Xmx4G -jar folia-1.21.11.jar --nogui
```

### 下载Folia

从 [PaperMC官网](https://papermc.io/software/folia) 下载最新版本的Folia 1.21.11

## 安装步骤

### 1. 安装插件

1. 下载插件JAR文件：`ThwNewPillar-1.1.4-SNAPSHOT.jar`
2. 将JAR文件放入服务器的 `plugins/` 文件夹
3. 启动服务器生成配置文件

### 2. 配置数据库

编辑 `plugins/ThwNewPillar/config.yml`：

```yaml
# 数据库配置
database:
  # 数据库类型: MYSQL 或 SQLITE
  type: MYSQL
  
  # MySQL配置
  host: localhost
  port: 3306
  name: lucky_pillar
  username: root
  password: your_password
  
  # 连接池设置
  pool:
    min: 5
    max: 20
    timeout: 30000
```

#### 数据库初始化

插件会自动创建所需的数据表，无需手动执行SQL脚本。

### 3. 配置文件说明

#### 基础配置

```yaml
# 游戏设置
game:
  # 是否启用自动开始
  auto-start-enabled: true
  # 自动开始所需的最少玩家数
  auto-start-min-players: 2

# 倒计时设置（单位：秒）
timers:
  # 战利品刷新间隔
  loot_time: 10
  # 事件触发间隔
  event_time: 60
  # 边界收缩间隔
  border_time: 51
  # 游戏开始倒计时
  begin_time: 10

# 边界收缩设置
border:
  # 初始边界大小
  initial_size: 80
  # 每次收缩的格数
  shrink_amount: 5
  # 收缩动画持续时间
  shrink_duration: 30
  # 收缩间隔
  shrink_interval: 21
  # 停止收缩的边界大小
  min_size: 10
```

#### 地图设置

```yaml
# 地图区域设置
map_region:
  # 中心点坐标
  center_x: 0
  center_y: 0
  center_z: 0
  # 区域半径
  radius: 50
  # Y坐标范围
  min_y: -64
  max_y: 320

# 大厅设置
lobby:
  x: 100
  y: 4
  z: 100
```

## 游戏指令

### 玩家指令

| 指令 | 说明 |
|------|------|
| `/np spectator` | 切换到观察者模式 |
| `/np status` | 查看游戏状态 |
| `/np stats` | 查看个人统计 |
| `/np rule vote <1-3>` | 规则投票 |
| `/vote <1-3>` | 快捷投票 |

### 管理员指令

| 指令 | 说明 |
|------|------|
| `/np start` | 开始游戏 |
| `/np forcestart` | 强制开始游戏 |
| `/np stop` | 结束游戏 |
| `/np menu` | 打开设置菜单 |
| `/np mapmenu` | 选择地图 |
| `/np event trigger <事件名>` | 触发事件 |
| `/np item <物品名>` | 获取特殊物品 |
| `/np test players <数量>` | 生成虚拟玩家测试 |

## 游戏机制

### 游戏规则

1. **准备阶段**：玩家加入后自动准备，达到最低人数后倒计时开始
2. **投票阶段**：玩家可以投票选择本局游戏规则
3. **游戏开始**：玩家被传送到随机柱子，笼子封闭
4. **笼子开启**：倒计时结束后笼子破坏，战斗开始
5. **边界收缩**：游戏进行12分钟后边界开始收缩
6. **柱子崩塌**：边界收缩到最小时，柱子开始逐层崩塌
7. **胜利条件**：最后存活的玩家获胜

### 特殊物品

- **击退棒**：强力的击退效果
- **火箭靴**：飞行能力
- **龙息弹**：发射龙息火球
- **TNT**：发射点燃的TNT
- **火焰弹**：发射大火球（可被打回）
- **末影碎片**：随机传送
- ... 更多物品等待探索

### 随机事件

游戏中会随机触发各种事件，增加游戏趣味性：
- 背包交换
- 玩家变大
- 获得伙伴（狐狸）
- 等等...

## 权限节点

```yaml
newpillar.player:      # 玩家基础权限
  - newpillar.spectator
  - newpillar.status
  - newpillar.stats
  - newpillar.rule.vote

newpillar.admin:       # 管理员权限（包含所有玩家权限）
  - newpillar.start
  - newpillar.forcestart
  - newpillar.stop
  - newpillar.menu
  - newpillar.mapmenu
  - newpillar.event
  - newpillar.item
  - newpillar.test
```

## 常见问题

### Q: 插件支持哪些数据库？
A: 目前支持MySQL和SQLite，推荐使用MySQL以获得更好的性能。

### Q: 最多支持多少玩家？
A: 游戏机制支持最多12人同时游戏，超过12人会随机选择12人参与。

### Q: 如何自定义地图？
A: 可以通过修改 `config.yml` 中的地图设置，或使用 `/np mapmenu` 命令选择不同地图类型。

### Q: 插件是否支持跨版本？
A: 插件专为Minecraft 1.21.11和Folia设计，不支持其他版本。

## 技术支持

如有问题或建议，欢迎提交Issue或Pull Request。

## 许可证

本项目采用 MIT 许可证开源。

---

**注意**：请确保使用兼容的服务器版本（Folia 1.21.11），否则插件可能无法正常工作。
