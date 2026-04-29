# Enhanced Little Maid AI

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-blue.svg)](https://www.minecraft.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1-orange.svg)](https://neoforged.net/)
![Version](https://img.shields.io/badge/Version-0.8.0--neoforge%2Bmc1.21.1-brightgreen)

**Enhanced Little Maid AI** 是 [Touhou Little Maid](https://github.com/TartaricAcid/TouhouLittleMaid) 的 NeoForge 附属模组，通过 Mixin 注入为女仆 AI 提供增强功能。

> 必须安装父模组 [Touhou Little Maid (NeoForge 1.21.1)](https://github.com/TartaricAcid/TouhouLittleMaid) 才能使用。

---

## 功能

### 思维宫殿 (MindPalace) — 长期记忆 + 社交记忆

- **玩家记忆**：地点、人物、事件、偏好、知识五大类别
- **社交记忆**：自动记录女仆间对话，在主动聊天中概率引用
- **BM25 语义检索**：零外部依赖的中英文混合语义匹配
- **空间召回**：根据女仆当前位置召回附近相关记忆
- **自动去重 + 智能淘汰**：BM25 相似度去重，多维评分淘汰
- **LLM 压缩**：记忆达到阈值后异步 LLM 合并旧记忆
- **关键词触发**：对话中提及"记住""别忘了"时立即记录
- **NBT 持久化**：跨重启不丢失

### 多女仆协调对话 (Inter-Maid Chat)

- 同主人、附近的两位女仆自动发起 2-4 轮 LLM 对话
- 两阶段匹配：A 扫描 B → B 基于社交记忆决定接受或拒绝
- 双模式决策：LLM 判断（AI 基于记忆）或 纯概率权重
- 聊天气泡 + 聊天栏推送附近玩家
- 独立冷却、每日上限、可配置触发距离和概率

### 主动聊天 (Proactive Chat)

- 女仆空闲时概率发起对话，基于环境 + 记忆上下文
- 环境事件触发：日出/日落/雨/雷暴/进入新群系时主动评论
- 每日上限（日出清零），冷却时间、触发概率完全可配置

### 采矿 LLM 对话 (Mining Chat)

- 与 [MiningLittleMaid](https://github.com/LonelyGEO/MiningLittleMaid) 联动
- MLM 采矿事件（发现矿石/背包满/缺火把）由 LLM 接管，基于女仆角色设定生成个性化对话
- 替代原模组硬编码文本

### 仓储感知 (Storage Awareness)

- 与 [MaidStorageManager](https://github.com/LonelyGEO/MaidStorageManager) 联动
- **库存上下文**：让 LLM 感知附近容器内容物（哪个箱子有什么、各有多少），作为 `inventory` 上下文按需注入
- **储物记忆**：取物/存物操作完成后自动写入 MindPalace 记忆，关联空间位置，后续可召回
- **自然语言取物**：LLM 可通过多步调用（`switch_work_task` → `get_storage` → `storage_fetch`）实现「帮我把钻石拿出来」的完整流程
- 可选模组，未安装 MSM 时自动跳过

### 推理内容支持 (Reasoning Content)

- 支持 DeepSeek 等模型的 `reasoning_content`（思考链）
- 自动注入 JSON 请求、回传对话历史、NBT 持久化

### LLM 响应缓存

- LRU 缓存 20 条，SHA-256 键，主动聊天 60s TTL / 常规 30s TTL
- 自动排除工具调用、记忆提取、采矿对话

### 世界上下文感知

- **方块采样**：BFS 扫描周围方块，Top-15 统计
- **环境详情**：光照、室内/室外、红石信号
- **实体感知**：附近生物详情（血量、职业、敌对状态）
- **扩展上下文**：饥饿值、主手工具耐久、附近玩家列表

---

## 命令

| 命令 | 权限 | 说明 |
|---|---|---|
| `/mindpalace list <女仆>` | 2 (管理员) | 列出女仆的所有记忆 |
| `/mindpalace clear <女仆>` | 2 (管理员) | 清除女仆的所有记忆 |
| `/mindpalace stats <女仆>` | 2 (管理员) | 查看记忆统计 |

---

## 配置

所有配置通过 **Cloth Config** 集成到 TLM 设置菜单中（`Mods → Touhou Little Maid → 配置 → 强化AI`）。无需 Cloth Config 时可通过 `config/enhancedlittlemaidai-common.toml` 手动编辑。

### GUI 结构

```
强化AI
├── LLM 采矿对话           [开关]     ← 仅 MLM 加载时显示
├── ▸ 上下文感知           (3 项)
├── ▸ 记忆系统             (5 项)
├── ▸ 主动聊天             (6 项)
├── ▸ 女仆社交             (17 项)
├── ▸ 仓储感知             (4 项)     ← 仅 MSM 加载时显示
└── ▸ 调试                 (1 项)
```

### 配置项一览（共 38 项）

#### 上下文感知

| 参数 | 默认值 | 说明 |
|---|---|---|
| `bfsMaxDepth` | 5 | BFS 方块采样深度 (1-10) |
| `entityRadius` | 16 | 实体扫描半径 (4-64) |
| `maxEntities` | 30 | 实体最大返回数 (5-100) |

#### 记忆系统

| 参数 | 默认值 | 说明 |
|---|---|---|
| `maxMemories` | 100 | 每女仆最大记忆数 (20-500) |
| `compressTrigger` | 80 | 触发 LLM 压缩阈值 (20-500) |
| `dedupScoreThreshold` | 0.85 | 去重相似度阈值 (0.5-1.0) |
| `compressBatchSize` | 20 | 压缩批次大小 (5-100) |
| `targetSummaries` | 5 | 压缩摘要上限 (1-30) |

#### 主动聊天

| 参数 | 默认值 | 说明 |
|---|---|---|
| `cooldownTicks` | 12000 | 冷却时间 (~600s) |
| `triggerChancePerTick` | 0.002 | 每 tick 触发概率 |
| `maxChatsPerDay` | 8 | 每日主动聊天上限 |
| `minPlayerDistance` | 10.0 | 主人最小距离 (格) |
| `eventCooldownTicks` | 6000 | 环境事件冷却 (~300s) |
| `eventMaxPerDay` | 5 | 每日环境事件上限 |

#### 女仆社交

| 参数 | 默认值 | 说明 |
|---|---|---|
| `enabled` | true | 启用多女仆对话 |
| `promptMode` | FULL | 角色设定长度 (FULL/SUMMARY/MINIMAL) |
| `maxRounds` | 2 | 最大对话轮数 (2-4) |
| `workingDistance` | 5.0 | 工作中触发距离 (格) |
| `idleDistance` | 16.0 | 空闲触发距离 (格) |
| `workingChance` | 0.0002 | 工作中触发概率/tick |
| `idleChance` | 0.0005 | 空闲触发概率/tick |
| `scanInterval` | 40 | 扫描间隔 (tick, 40=2s) |
| `playerDistance` | 18.0 | 玩家感知范围 (格) |
| `maxPerDay` | 3 | 每女仆每日上限 |
| `maxGlobalPerDay` | 10 | 全局每日上限 |
| `cooldownTicks` | 6000 | 同对冷却 (~300s) |
| `decisionMode` | LLM | 接受决策方式 (LLM/WEIGHT) |
| `socialMemoryInjectChance` | 0.3 | 社交记忆注入概率 |
| `socialMemoryTopK` | 2 | 每次注入条数 (1-5) |
| `socialStoreMaxSize` | 50 | 社交记忆存储上限 |
| `socialMemoryCompressTrigger` | 40 | 社交记忆压缩阈值 |

#### 仓储感知

| 参数 | 默认值 | 说明 |
|---|---|---|
| `enableStorageMemory` | true | 储物操作自动写入 MindPalace |
| `maxStoragePositions` | 10 | 附近存储最多显示位置数 (3-30) |
| `maxItemsPerStorage` | 8 | 每个位置最多显示物品数 (3-20) |
| `maxItemsSummary` | 15 | 库存摘要最多显示物品数 (5-50) |

#### 调试

| 参数 | 默认值 | 说明 |
|---|---|---|
| `debugLog` | false | 启用调试日志 |
| `enableMiningChat` | true | LLM 采矿对话开关 |

---

## 安装

1. 确保已安装 **NeoForge 1.21.1**
2. 安装父模组 **[Touhou Little Maid](https://github.com/TartaricAcid/TouhouLittleMaid)** 1.5.2+
3. 将 `enhancedlittlemaidai-<version>.jar` 放入 `mods/` 文件夹
4. （可选）安装 [MiningLittleMaid](https://github.com/LonelyGEO/MiningLittleMaid) 以启用采矿联动和采矿对话
5. （可选）安装 [MaidStorageManager](https://github.com/LonelyGEO/MaidStorageManager) 以启用仓储感知、储物记忆和自然语言取物
6. （可选）安装 Cloth Config 以获得游戏内配置界面

---

## 构建

```bash
# 克隆仓库
git clone https://github.com/LonelyGEO/EnhancedLittleMaidAI.git
cd EnhancedLittleMaidAI

# 构建
./gradlew.bat build    # Windows
./gradlew build        # macOS / Linux

# 运行测试
./gradlew.bat test

# 启动开发客户端
./gradlew.bat runClient
```

### 开发环境要求

- **JDK 21**
- 父模组 JAR 需放入 `libs/` 目录：
  - `touhoulittlemaid-1.5.2-neoforge+mc1.21.1.jar`（必需）
  - `mininglittlemaid-0.9.0-neoforge+mc1.21.1.jar`（可选，compileOnly）
  - `maid_storage_manager-1.15.6-neoforge+mc1.21.1.jar`（可选，compileOnly）
  - `cloth-config-neoforge-15.0.140.jar`（可选，compileOnly）

---

## 许可证

MIT License — 详见 [LICENSE](LICENSE)
