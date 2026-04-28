# Enhanced Little Maid AI

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-blue.svg)](https://www.minecraft.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1-orange.svg)](https://neoforged.net/)
![Version](https://img.shields.io/badge/Version-0.6.0--neoforge%2Bmc1.21.1-brightgreen)

**Enhanced Little Maid AI** 是 [Touhou Little Maid](https://github.com/TartaricAcid/TouhouLittleMaid) 的 NeoForge 附属模组，通过 Mixin 注入为女仆 AI 提供增强功能。

> 💡 必须安装父模组 [Touhou Little Maid (NeoForge 1.21.1)](https://github.com/TartaricAcid/TouhouLittleMaid) 才能使用。

## 功能

### 🧠 推理内容支持 (Reasoning Content)

- 支持 LLM 返回的 `reasoning_content`（思考链）字段
- 在对话历史中持久化女仆的思考过程
- 下次对话时自动注入推理上下文

### 🏰 思维宫殿 (MindPalace) — 长期记忆系统

- **记忆分类**：地点、人物、事件、偏好、知识五大类别
- **BM25 语义检索**：基于倒排索引的语义匹配，支持中英文混合记忆检索
- **空间召回**：根据女仆当前位置召回附近相关记忆
- **自动去重**：使用 BM25 相似度评分避免重复记忆
- **智能淘汰**：结合重要性、新鲜度、访问频率的评分机制
- **LLM 记忆压缩**：记忆数量达到阈值后自动触发异步压缩，将旧记忆合并为摘要
- **关键词触发**：对话中提及"记住""别忘了"等关键词时立即创建记忆
- **死亡记忆**：女仆死亡时自动记录事件记忆
- **持久化**：记忆数据通过 NBT 存入存档

### 💬 主动聊天 (Proactive Chat)

- 女仆在空闲时有可能主动发起对话
- 多层限制：好感度等级、冷却时间、每会话次数上限、距离检查
- 聊天内容结合当前环境和记忆上下文
- 不影响正常对话历史和记忆系统

### 🌍 世界上下文感知

- **附近方块感知（BFS 采样）**：对女仆周围方块进行广度优先采样，识别突出特征
- **环境详情**：光照水平、室内/室外判定、红石信号
- **实体感知**：附近实体的类型、血量、职业等详细信息

### ⛏️ 采矿联动 (MiningLittleMaid)

- 与 [MiningLittleMaid](https://github.com/LonelyGEO/MiningLittleMaid) 可选联动
- 提供附近矿石分布、采矿状态等智能上下文
- 通过反射兼容层实现可选依赖

### ⚙️ 配置系统

- 17 项可配置参数，分 4 个类别
- 支持 Cloth Config 设置界面（通过 TLM 配置菜单集成）
- 热加载支持（无需重启游戏）

## 安装

1. 确保已安装 **NeoForge 1.21.1**
2. 安装父模组 **[Touhou Little Maid](https://github.com/TartaricAcid/TouhouLittleMaid)** 1.5.2+
3. 将 `enhancedlittlemaidai-<version>.jar` 放入 `mods/` 文件夹
4. （可选）安装 [MiningLittleMaid](https://github.com/LonelyGEO/MiningLittleMaid) 以启用采矿联动
5. （可选）安装 Cloth Config 以获得游戏内配置界面

## 命令

| 命令 | 权限 | 说明 |
|------|------|------|
| `/mindpalace list <女仆>` | 2 (管理员) | 列出女仆的所有记忆 |
| `/mindpalace clear <女仆>` | 2 (管理员) | 清除女仆的所有记忆 |
| `/mindpalace stats <女仆>` | 2 (管理员) | 查看记忆统计信息 |

## 配置项

配置文件位于 `config/enhancedlittlemaidai.toml`

### [memory] 记忆系统

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `maxMemories` | 100 | 每女仆最大记忆数 |
| `compressTrigger` | 80 | 触发 LLM 压缩的阈值 |
| `dedupScoreThreshold` | 0.85 | 去重相似度阈值 |
| `semanticRetrieveTopK` | 3 | BM25 检索返回数量 |

### [proactive_chat] 主动聊天

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `cooldownTicks` | 12000 | 冷却时间 (~10分钟) |
| `triggerChancePerTick` | 0.002 | 每 tick 触发概率 |
| `maxChatsPerSession` | 8 | 每会话最大主动聊天次数 |

### [context] 上下文感知

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `bfsMaxDepth` | 5 | BFS 方块采样深度 |
| `entityRadius` | 16 | 实体扫描半径 |
| `maxEntities` | 30 | 实体最大返回数 |

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
- 父模组 JAR 需放入 `libs/` 目录
  - `touhoulittlemaid-1.5.2-neoforge+mc1.21.1.jar`（必需）
  - `mininglittlemaid-0.4.2-neoforge+mc1.21.1.jar`（可选，compileOnly）
  - `cloth-config-neoforge-15.0.140.jar`（可选，compileOnly）

## 开发文档

- [AGENTS.md](AGENTS.md) — AI 编码助手规范与项目约定
- [planToAgent.md](planToAgent.md) — 各版本已完成功能记录

## 许可证

MIT License — 详见 [LICENSE](LICENSE)
