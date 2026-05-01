# Enhanced Little Maid AI

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-blue.svg)](https://www.minecraft.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1-orange.svg)](https://neoforged.net/)
![Version](https://img.shields.io/badge/Version-1.0.0--neoforge%2Bmc1.21.1-brightgreen)

**Enhanced Little Maid AI** 是 [Touhou Little Maid](https://github.com/TartaricAcid/TouhouLittleMaid) 的 NeoForge 附属模组，通过 Mixin 注入为女仆 AI 提供增强功能。

> 必须安装父模组 [Touhou Little Maid (NeoForge 1.21.1)](https://github.com/TartaricAcid/TouhouLittleMaid) 才能使用。

---

## 功能

### 多女仆协同对话 (Inter-Maid Chat)

- 附近的女仆自动发起 2-4 轮 LLM 对话，角色设定驱动的自然闲聊
- **WEIGHT 默认决策**：基于社交记忆的纯概率接受，零 LLM API 消耗
- **群聊支持**：2-8 人参与，随机说话顺序，轮间随机延迟
- **随机轮数**：minRounds ~ maxRounds 随机取值，参与女仆每多 1 人最低轮数 +1
- 聊天气泡 + 聊天栏全玩家可见（TLM 原生机制）
- 独立冷却、每日上限、可配置触发距离和概率
- 密度感知：附近女仆越多，单个触发概率越低

### 思维宫殿 (MindPalace) — 长期记忆 + 社交记忆

- **玩家记忆**：地点、人物、事件、偏好、知识五大类别
- **社交记忆**：自动记录女仆间对话，在主动聊天中概率引用
- **BM25 语义检索**：零外部依赖的中英文混合语义匹配
- **空间召回**：根据女仆当前位置召回附近相关记忆
- **自动去重 + 智能淘汰**：BM25 相似度去重，多维评分淘汰
- **LLM 压缩**：记忆达到阈值后异步 LLM 合并旧记忆
- **关键词触发**：对话中提及"记住""别忘了"时立即记录
- **NBT 持久化**：磁盘独立存储，不参与网络同步

### 主动聊天 (Proactive Chat)

- 女仆空闲时概率发起对话，基于环境 + 记忆上下文
- 环境事件触发：日出/日落/雨/雷暴/进入新群系时主动评论
- 每日上限（日出清零），冷却时间、触发概率完全可配置
- LLM 返回空内容时自动回退到 reasoning_content

### 采矿 LLM 对话 (Mining Chat)

- 与 [MiningLittleMaid](https://github.com/LonelyGEO/MiningLittleMaid) 联动
- MLM 采矿事件由 LLM 接管，基于女仆角色设定生成个性化对话

### 仓储感知 (Storage Awareness)

- 与 [MaidStorageManager](https://github.com/LonelyGEO/MaidStorageManager) 联动
- 库存上下文：让 LLM 感知附近容器内容物
- 储物记忆：取物/存物操作自动写入 MindPalace 记忆
- 自然语言取物：LLM 多步调用实现完整取物流程

### 推理内容支持 (Reasoning Content)

- DeepSeek 等推理模型的思考链自动注入和回传
- 空 content 自动回退到 reasoning_content

### LLM 响应缓存

- LRU 缓存 20 条，SHA-256 键，主动聊天 60s TTL / 常规 30s TTL

### 世界上下文感知

- 方块采样 (BFS)、环境详情 (光照/红石)、实体感知 (血量/职业)
- 扩展上下文：饥饿值、工具耐久、附近玩家列表

---

## 命令

| 命令 | 权限 | 说明 |
|---|---|---|
| `/elmai mindpalace list <女仆>` | 2 (管理员) | 列出女仆的记忆 |
| `/elmai mindpalace search <女仆> <关键词>` | 2 (管理员) | BM25 搜索记忆 |
| `/elmai mindpalace clear <女仆>` | 2 (管理员) | 清除女仆的记忆 |
| `/elmai mindpalace stats <女仆>` | 2 (管理员) | 查看记忆统计 |
| `/elmai status <女仆>` | 任意 | 女仆运行状态速查 |
| `/elmai config` | 任意 | Config 当前值速查 |
| `/maid <女仆名> <消息>` | 任意 | 打字与女仆 LLM 对话 |

---

## 配置

所有配置通过 **Cloth Config** 集成到 TLM 设置菜单中（`Mods → Touhou Little Maid → 配置 → 强化AI`）。

配置文件：`config/enhancedlittlemaidai-common.toml`  
覆盖文件：`config/elmai-overrides.toml`（开发环境，不受 NeoForge 重置）

### GUI 结构

```
强化AI
├── 女仆问候              [开关]  default:true
├── 调试日志              [开关]  default:false
├── 抑制父模组日志        [开关]  default:true
├── ▸ 上下文感知           (3 项)
├── ▸ 记忆系统             (8 项)
├── ▸ 主动聊天             (10 项，含采矿对话)
├── ▸ 女仆社交             (20 项)
└── ▸ 仓储感知             (4 项，仅 MSM 加载)
```

### 配置项一览

#### 上下文感知

| 参数 | 默认值 | 说明 |
|---|---|---|
| `bfsMaxDepth` | 5 | BFS 方块采样深度 |
| `entityRadius` | 16 | 实体扫描半径（格） |
| `maxEntities` | 30 | 实体最大返回数 |

#### 记忆系统

| 参数 | 默认值 | 说明 |
|---|---|---|
| `maxMemories` | 100 | 每女仆最大记忆数 |
| `compressTrigger` | 80 | LLM 压缩触发阈值 |
| `compressBatchSize` | 20 | 压缩批次大小 |
| `targetSummaries` | 5 | 压缩摘要上限 |
| `spatialRecallRadius` | 16 | 空间召回半径（格） |
| `semanticRetrieveTopK` | 3 | 语义检索 Top-K |
| `dedupScoreThreshold` | 0.85 | 去重相似度阈值 |
| `maxFreshAgeHours` | 10 | 记忆新鲜度（小时） |

#### 主动聊天

| 参数 | 默认值 | 说明 |
|---|---|---|
| `enableMiningChat` | true | LLM 采矿对话（仅 MLM 加载） |
| `enabled` | true | 主动聊天总开关 |
| `cooldownTicks` | 12000 | 冷却时间 (10 分钟) |
| `triggerChance` | 0.002 | 每 tick 触发概率 |
| `maxChatsPerDay` | 8 | 每日主动聊天上限 |
| `minPlayerDistance` | 10 | 主人最小距离（格） |
| `eventCooldownTicks` | 6000 | 环境事件冷却 (5 分钟) |
| `eventMaxPerDay` | 5 | 每日环境事件上限 |
| `eventRangeBlocks` | 32 | 事件感知范围（格） |
| `promptMode` | FULL | 提示词模式 (FULL/SUMMARY/MINIMAL) |

#### 女仆社交

| 参数 | 默认值 | 说明 |
|---|---|---|
| `enabled` | true | 女仆社交总开关 |
| `minRounds` | 2 | 最少对话轮数 (多人 +1/人) |
| `maxRounds` | 4 | 最大对话轮数 (随机取值) |
| `workingDistance` | 5.0 | 工作中触发距离（格） |
| `idleDistance` | 16.0 | 空闲触发距离（格） |
| `workingChance` | 0.10 | 工作中扫描触发概率 |
| `idleChance` | 0.20 | 空闲中扫描触发概率 |
| `scanInterval` | 120 | 扫描间隔 (tick, 6秒) |
| `playerDistance` | 18.0 | 感知玩家范围（格） |
| `maxPerDay` | 3 | 每女仆每日上限 |
| `maxGlobalPerDay` | 15 | 全局每日上限 |
| `cooldownTicks` | 1200 | 同对冷却 (1 分钟) |
| `decisionMode` | WEIGHT | 接受决策 (LLM/WEIGHT) |
| `crossOwner` | true | 跨主人对话 |
| `maxGroupSize` | 3 | 最大群聊人数 (2-8) |
| `roundDelayMin` | 3 | 轮间延迟下限 (秒) |
| `roundDelayMax` | 5 | 轮间延迟上限 (秒) |
| `socialInjectChance` | 0.3 | 社交记忆注入概率 |
| `socialTopK` | 2 | 社交记忆注入条数 |
| `socialMaxSize` | 50 | 社交记忆存储上限 |
| `socialCompressTrigger` | 40 | 社交记忆压缩阈值 |

#### 仓储感知

| 参数 | 默认值 | 说明 |
|---|---|---|
| `enableStorageMemory` | true | 储物记忆自动写入 |
| `maxStoragePositions` | 10 | 最多显示存储位置数 |
| `maxItemsPerStorage` | 8 | 每位置最多显示物品数 |
| `maxItemsSummary` | 15 | 摘要最多显示物品数 |

---

## 安装

1. 确保已安装 **NeoForge 1.21.1**
2. 安装父模组 **[Touhou Little Maid](https://github.com/TartaricAcid/TouhouLittleMaid)** 1.5.2+
3. 将 `enhancedlittlemaidai-<version>.jar` 放入 `mods/` 文件夹
4. （可选）[MiningLittleMaid](https://github.com/LonelyGEO/MiningLittleMaid) — 采矿联动
5. （可选）[MaidStorageManager](https://github.com/LonelyGEO/MaidStorageManager) — 仓储感知
6. （可选）Cloth Config — 游戏内配置界面

---

## 构建

```bash
git clone https://github.com/LonelyGEO/EnhancedLittleMaidAI.git
cd EnhancedLittleMaidAI
./gradlew.bat build
./gradlew.bat runClient
```

**要求**：JDK 21  
**依赖**：`libs/` 目录需放入父模组及联动模组 JAR

---

## 许可证

MIT License — 详见 [LICENSE](LICENSE)
