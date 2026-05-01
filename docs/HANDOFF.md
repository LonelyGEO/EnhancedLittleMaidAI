# EnhancedLittleMaidAI 交接文档

> 版本: v0.12.0 | 平台: NeoForge 1.21.1 | 更新: 2026-05-01

---

## 1. 项目概览

**EnhancedLittleMaidAI** 是 [Touhou Little Maid](https://github.com/TartaricAcid/TouhouLittleMaid) (TLM) 的 Mixin 附属模组。

| 信息 | 值 |
|---|---|
| 仓库 | `https://github.com/LonelyGEO/EnhancedLittleMaidAI.git` |
| 分支 | `1.21` |
| Gradle | `./gradlew.bat build` (Windows) / `./gradlew build` |
| JDK | 21 |
| Mod 类型 | NeoForge Mixin Addon |
| 父模组 JAR | `libs/touhoulittlemaid-1.5.2-neoforge+mc1.21.1.jar` |
| 联动 (可选) | MLM `libs/mininglittlemaid-0.9.0-neoforge+mc1.21.1.jar` |
| 联动 (可选) | MSM `libs/maid_storage_manager-1.15.6-neoforge+mc1.21.1.jar` |
| UI 依赖 | `libs/cloth-config-neoforge-15.0.140.jar` |

---

## 2. 源码结构

```
src/main/java/com/github/lonelygeo/enhancedlittlemaidai/
├── EnhancedLittleMaidAI.java           # @Mod 入口 → Config注册 + 事件/命令注册
├── EnhancedLittleMaidExtension.java    # @LittleMaidExtension → 上下文/工具注册
├── command/
│   ├── MindPalaceCommand.java          # /elmai mindpalace list|clear|stats|search
│   ├── ElmaStatusCommand.java          # /elmai status <maid> (new)
│   └── ElmaConfigCommand.java          # /elmai config (new)
├── compat/
│   ├── MiningCompat.java               # MLM 反射兼容层
│   ├── MiningMessageHandler.java       # MLM 采矿事件 → LLM 接管
│   ├── MiningChatCallback.java         # MLM 采矿 LLM 回调
│   ├── StorageCompat.java              # MSM 反射兼容层
│   └── StorageMemoryHandler.java       # MSM 储物事件 → 记忆写入
├── config/
│   ├── EnhancedConfig.java             # NeoForge ModConfigSpec (~55 项)
│   ├── ClothConfigHandler.java         # Cloth Config GUI 构建器
│   └── ClothConfigIntegration.java     # Cloth Config 反射注册入口
├── context/
│   ├── BlockAwareContexts.java         # BFS方块+环境+实体上下文
│   ├── ContextProviders.java           # 饥饿值+工具耐久+附近玩家上下文
│   ├── MiningContextProvider.java       # 矿石扫描+采矿状态上下文
│   └── StorageContextProvider.java      # 仓储物品清单上下文
├── memory/
│   ├── MemoryCategory.java             # 记忆分类枚举
│   ├── MemoryItem.java                 # 记忆数据模型 (record)
│   ├── MemoryStore.java                # BM25 去重+检索+淘汰
│   ├── MindPalace.java                 # 全局管理器 (store + socialStore + NBT)
│   ├── MemoryExtractionCallback.java   # 记忆提取 LLM 回调
│   ├── MemoryResponseParser.java       # 提取结果解析
│   └── MemoryCompressor.java           # LLM 记忆压缩
├── mixin/                              # Mixin 注入点 (全部 remap=false)
│   ├── ChatMessageMixin.java           # [空占位]
│   ├── EntityMaidMixin.java            # tick: 环境事件+女仆社交+主动聊天+MaidsSpawn重试
│   │                                    # remove: 死亡记忆+清理
│   ├── LLMCallbackMixin.java           # onFunctionCall @Redirect ×2 (reasoningContent)
│   │                                    # onSuccess TAIL: 记忆提取+压缩
│   ├── LLMMessageMixin.java            # [空占位]
│   ├── LLMOpenAIClientMixin.java       # chat HEAD: 缓存检查+capture
│   │                                    # Gson.toJson @Redirect: reasoningContent JSON注入+ELMAI dump
│   │                                    # chat/onTextCall: 父模组INFO dump @Redirect抑制
│   │                                    # onTextCall TAIL: 响应缓存
│   ├── MaidAIChatDataMixin.java        # writeToTag: Phase 1 reasoningContent剥离 (NBT缩减)
│   │                                    # Shadow: getMaid() + getHistory()
│   ├── MaidAIChatManagerMixin.java     # normalChat @Redirect: context重排+天气修正
│   │                                    # tts HEAD: 全局TTS禁用检查
│   └── MessageMixin.java               # [空占位]
└── util/
    ├── LLMUtil.java                    # LLM 可用性统一校验
    ├── LLMResponseCache.java           # 响应 LRU 缓存 (20条)
    ├── SimpleResponseCache.java        # 通用缓存后端 (synchronized)
    ├── ProactiveChatManager.java       # 主动聊天冷却/频率控制
    ├── ProactiveChatCallback.java      # 主动聊天 LLM 回调 + Prompt构建
    ├── EnvironmentEventDetector.java   # 环境事件检测 (生物群系降水过滤+范围冷却+启动抖动)
    ├── DayPhaseUtil.java               # 游戏时间 → 4 phase 映射
    ├── InterMaidChatManager.java       # 女仆对话管理器 (密度感知+DECIDING+claimProposal)
    ├── InterMaidChatCallback.java      # 女仆间对话链式 LLM 回调 (随机说话者+历史传递)
    ├── InterMaidDecisionCallback.java  # B接受提案的LLM决策 (指令前置+解析容错)
    ├── ChatMaidCommandHandler.java     # /maid 命令 (new)
    ├── MaidSpawnHandler.java           # 女仆放置 → 设定生成+见面问候 (延迟重试)
    ├── PromptConstants.java            # 共享提示常量 (new)
    ├── ReasoningContentStore.java       # 跨Mixin共享存储 (可能孤立,待审计)
    └── bm25/
        ├── ChineseTokenizer.java       # 字符级 bigram 分词器
        └── Bm25Index.java              # BM25 倒排索引
```

### Skills (数据文件)

```
src/main/resources/data/touhou_little_maid/skills/
├── elmai/          # ELMAI 自身能力指南 (knowledge)
├── enhanced_storage/  # 仓储管理指南 (knowledge)
└── elma_quick/     # 命令速查卡 (非knowledge, body直接返回)
```

---

## 3. 全部命令

| 命令 | 权限 | 说明 |
|------|------|------|
| `/elmai mindpalace list <maid>` | 2 | 编号列表记忆（含坐标+查阅次数） |
| `/elmai mindpalace search <maid> <keyword>` | 2 | BM25 搜索记忆 |
| `/elmai mindpalace clear <maid>` | 2 | 清除记忆（显示条数） |
| `/elmai mindpalace stats <maid>` | 2 | 五分类统计 |
| `/elmai status <maid>` | 0 | 女仆运行状态（cooling用s显示） |
| `/elmai config` | 0 | Config 速查（tick→秒/小时换算） |
| `/maid <name> <message>` | 0 | 打字与女仆LLM对话 |

---

## 4. Mixin 注入点速查（15 个活跃）

| # | Mixin | 目标方法 | 注解 | 功能 |
|---|---|---|---|---|
| 1 | `EntityMaidMixin` | `remove` HEAD | `@Inject` | 死亡记忆写入 MindPalace |
| 2 | `EntityMaidMixin` | `remove` TAIL | `@Inject` | 清理所有管理器 + GREETED 集合 |
| 3 | `EntityMaidMixin` | `tick` TAIL | `@Inject` | 环境事件/女仆社交/主动聊天/MaidSpawn重试分流 |
| 4 | `LLMCallbackMixin` | `onFunctionCall` | `@Redirect` ×2 | reasoningContent 注入 history (require=0) |
| 5 | `LLMCallbackMixin` | `onSuccess` TAIL | `@Inject` | 记忆提取+压缩 (跳过 InterMaid/Proactive) |
| 6 | `LLMOpenAIClientMixin` | `chat` HEAD | `@Inject(cancellable)` | 缓存检查 + callback捕获 |
| 7 | `LLMOpenAIClientMixin` | `Gson.toJson` in `chat` | `@Redirect` | JSON 拦截 → injectReasoningContent + ELMAI dump |
| 8 | `LLMOpenAIClientMixin` | `Logger.info(String)` in `chat` | `@Redirect(require=0)` | 抑制父模组请求 JSON dump (可配置) |
| 9 | `LLMOpenAIClientMixin` | `Logger.info(String)` in `lambda$handle$1` | `@Redirect(require=0)` | 抑制父模组响应 JSON dump (可配置) |
| 10 | `LLMOpenAIClientMixin` | `onTextCall` TAIL | `@Inject` | 响应存入缓存 |
| 11 | `MaidAIChatDataMixin` | `writeToTag` | `HEAD+TAIL @Inject` + `getDeque() @Redirect` | Phase 1: 写NBT前剥 reasoningContent |
| 12 | `MaidAIChatManagerMixin` | `normalChat` → `UserPromptContexts.addContext` | `@Redirect(require=0)` | 上下文重排 + 天气修正 |
| 13 | `MaidAIChatManagerMixin` | `buildMessage` TAIL | `@Inject` | 记忆上下文注入 (PUSH模式) |
| 14 | `MaidAIChatManagerMixin` | `tts` HEAD | `@Inject(cancellable, require=0)` | 全局TTS禁用检查 |
| 15 | `MaidAIChatDataMixin` | (占位) | `@Shadow` | getMaid() + getHistory() |

---

## 5. 配置系统 (~55 项)

TOML 文件: `config/enhancedlittlemaidai-common.toml`
GUI 入口: `Mods → Touhou Little Maid → 配置 → 强化AI`

### GUI 结构

```
强化AI
├── LLM 采矿对话 [开关]      (仅 MLM 加载时)
├── ▸ 上下文感知    (3项)
├── ▸ 记忆系统      (8项) — 含新鲜窗口/空间召回/语义检索
├── ▸ 主动聊天      (9项) — 含启用开关/提示词模式/事件范围冷却
├── ▸ 女仆社交      (19项) — 含跨主人/群聊人数/轮间延迟/扫描间隔
├── ▸ 仓储感知      (4项, 仅 MSM 加载)
└── ▸ 调试          (5项) — 含抑制父模组JSON dump开关
```

### 关键配置速查

| 段 | 键 | 默认 | 说明 |
|---|---|---|---|
| proactive_chat | `enabled` | true | 主动聊天总开关 |
| proactive_chat | `cooldownTicks` | 12000 | 冷却 (600s) |
| proactive_chat | `eventRangeBlocks` | 32 | 环境事件范围冷却 (格) |
| inter_maid | `enabled` | true | 女仆对话总开关 |
| inter_maid | `maxRounds` | 2 | 对话轮数 (2-4) |
| inter_maid | `scanInterval` | 120 | 扫描间隔 tick (6s) |
| inter_maid | `roundDelayMin/Max` | 3/5 | 轮间随机延迟 (秒) |
| inter_maid | `decisionMode` | LLM | B接受决策方式 |
| memory | `maxFreshAgeHours` | 10 | 记忆新鲜窗口 (小时) |
| storage_context | `enableStorageMemory` | true | 储物记忆写入 |
| debug | `debugLog` | false | 全局调试日志 |
| debug | `suppressParentJsonDump` | true | 抑制父模组 JSON dump |
| debug | `overrideTTSDisabled` | false | 全局关闭TTS |

---

## 6. 关键设计模式

### 6.1 女仆间对话完整流程 (v0.12 当前状态)

```
📡 A侧扫描 (每6秒, 密度感知)
  EntityMaid.tick() → scanInterval % == 0 → canScan → findPartners
  ↓ densityScale = 2.0/(partners+1), Math.random() <= scale
  ↓ proposeGroup → PENDING_PROPOSALS[B] = Proposal(A)

🤔 B侧接收 (每tick)
  getProposer(B) → handleProposal(B, A)
  ├── canAccept(B, A) → LLM可用? 不忙? 每日上限? 同对冷却? 距离?
  │   ├── false + LLM可用 → markRejected (写 pair cooldown)
  │   └── false + LLM不可用 → rejectFromGroup (软拒绝, 不写cooldown)
  ├── isDeciding(B) || isDeciding(A) → skip
  ├── tryAcquireDecisionSlot() → 全局上限2
  └── LLM决策: InterMaidDecisionCallback
      ├── Prompt: "[系统指令] 只输出 ACCEPT 或 REJECT。禁止任何中文/描写/括号/星号"
      │            + 角色设定后置
      └── onSuccess: 解析时去除括号内容后搜索 ACCEPT/REJECT

🎫 ACCEPT → 启动对话
  claimProposal(B) → PENDING_PROPOSALS 原子抢出 (防止A侧扫描覆盖)
  server.submit → finalizeAcceptance(B, A_UUID)
    → GROUP_PROPOSALS[A].accepted.add(B)
    → tryStartConversation → Level.getEntitiesOfClass (server thread)
    → markBusy(participants) → startConversation

💬 对话 (2-4轮, 随机说话者)
  Round 1: A发言 → "**少女们商量中...**" → LLM响应 → 替换气泡
  [3-5秒延迟] Round 2: B发言 → 包含完整历史 → LLM → 替换气泡
  roundCount >= totalRounds → finishConversation

📝 结束
  → 写入双方 MindPalace socialStore
  → releaseBusy → markTriggered (pair cooldown 5分钟)
```

### 6.2 反射兼容层 (MiningCompat / StorageCompat)

```
Compat.isLoaded() → ModList.get().isLoaded("modid")
  ↓
invokeStatic() / 独立反射方法 → Class.forName + Method.invoke
  ↓
失败 → 返回默认值 + EnhancedLittleMaidAI.LOGGER.warn (统一Logger)
```

### 6.3 LLM 可用性校验 (LLMUtil.isAvailable())

```
LLMUtil.isAvailable(maid)
  ├── site == null → false
  ├── !site.enabled() → false
  ├── site.url() 为空 → false
  └── secretKey() 为空 → false (仅 LLMOpenAISite)
```

debugLog=true 时每 tick 打印 `api key blank` 极嘈杂 → 考虑按 UUID 去重。

### 6.4 reasoningContent 处理链

```
写入 (仅 tool call): LLMCallbackMixin @Redirect → addAssistantHistory(msg, tools, rc)
写入 (NBT持久化): MaidAIChatDataMixin Phase 1 → writeToTag 前剥空 reasoningContent
读取 (JSON回传): LLMOpenAIClientMixin → injectReasoningContent() 读 msg.reasoningContent()
```

### 6.5 配置新增流程

1. `EnhancedConfig.java`: 声明字段 + `builder.define/defineInRange`
2. `ClothConfigHandler.java`: `entryBuilder.startXxx(...)` 构建 GUI 条目
3. `zh_cn.json` + `en_us.json`: 添加翻译键
4. `gradlew build` 验证

**注意**：每次 Config spec 变化后，NeoForge 会重建 toml 文件并将 `debugLog` 重置为 `false`。需手动改回。

---

## 7. 已发现的问题与陷阱

### 7.1 已知 Bug/待验证

| # | 问题 | 状态 |
|---|---|---|
| 1 | **acceptIntoGroup 竞态** — `server.submit` 异步延迟期 proposal 被覆盖 | ✅ 已修复 (claimProposal) 未测试 |
| 2 | **父模组日志洗屏** — `TouhouLittleMaid.LOGGER.info(JSON)` 全量 dump | ✅ @Redirect 抑制, config 可开关 |
| 3 | **EntityJoinLevelEvent vs AvailableSites.init() 竞态** — LLM site 未就绪时 genSetting 失败 | ✅ MaidSpawnHandler 延迟重试 |
| 4 | **NeoForge config 回退** — Config spec 变化后 debugLog 被重置 | ⚠️ 手动恢复 toml |
| 5 | **LLMUtil.isAvailable() 日志洪泛** — debug=true 时每 tick 重复 `api key blank` | ⚠️ 可加 UUID 去重 |
| 6 | **新女仆默认 LLM site=PLAYER2** — 父模组行为, 需手动改为 deepseek | ⚠️ 父模组限制, 可在 ELMAI 加默认值 |
| 7 | **MindPalace PUSH vs PULL** — 记忆每次对话全量注入 SYSTEM[2] | 📋 已设计工具化方案, 未实现 |
| 8 | **InterMaidDecisionCallback LLM 决策仍不稳定** — LLM 有时返回角色扮演文本被当 REJECT | ⚠️ 解析容错已加固 |
| 9 | **debugLog 开关不方便** — 开发环境每次被 NeoForge 重置, 需手动改 toml | 📋 可加 `-D` JVM 属性方案 |
| 10 | **ReasoningContentStore 可能孤立** — HEAD inject 删除后写入路径消失 | 📋 待审计 |

### 7.2 通用陷阱

| # | 陷阱 | 注意 |
|---|---|---|
| 1 | **Mixin 方法可见性** | 所有非注入方法必须 `private` 或 `@Unique` |
| 2 | **LLMMessage 是 record** | 不可变, 需用 `new LLMMessage(...)` 创建不带 reasoningContent 副本 |
| 3 | **`.gitignore` 漏洞** | `/com/` `/net/` `/data/` `temp_extract/` `.temp_decompile/` `temp_extracted_class/` 均已覆盖 |
| 4 | **`addThinkingText` vs `addTextChatBubble`** | 前者 90s 到期需 LLM 回调替换, 后者 15s 自动消失 |
| 5 | **`server.submit()` 异步性** | forkJoin→server thread 有延迟窗口, 需在提交前抢出共享状态 |
| 6 | **`require=0` 惯用法** | 所有 Redirect 在可能被父模组变更破坏的地方用 `require=0` 容错 |

### 7.3 思考气泡表

| 场景 | 气泡文字 | 类型 | 存活 |
|------|---------|------|------|
| 概率触发 | `**少女思考中...**` | `addThinkingText` → `addLLMChatText` | 90s→15s |
| 环境事件 | `**少女感知中...**` | 同上 | 同上 |
| 采矿对话 | `**少女分析中...**` | 同上 | 同上 |
| 女仆社交 | `**少女们商量中...**` | 同上 | 同上 |
| 设定生成 | `**少女苏醒中...**` | 同上 | 同上 |
| 寻友扫描 | `**少女寻友中...**` | `addTextChatBubble` | 5s (仅debugLog) |

---

## 8. 构建与测试

```bash
# 编译+测试
./gradlew.bat build

# 启动客户端 (需手动关闭游戏窗口)
./gradlew.bat runClient      # 超时 15 分钟

# 推送
git push origin 1.21

# Release (需 gh CLI + 确认)
gh release create v0.x.x build/libs/enhancedlittlemaidai-0.x.x-*.jar \
  --title "v0.x.x-beta" --prerelease --notes "..."
```

**运行前确保**:
- 父模组 LLM 站点已配置 (Mods → Touhou Little Maid → AI 聊天设置 → LLM 站点)
- 新女仆 LLM site 默认是 player2, 需手动改为深寻或其他
- `debugLog` 在 `run/config/enhancedlittlemaidai-common.toml` 中手动设 `true`
- `suppressParentJsonDump` 默认 `true` (抑制), 设为 `false` 可恢复父模组原始日志

---

## 9. 版本历史

| 版本 | 主要变更 |
|---|---|
| v0.10.x | 女仆放置问候 + 多女仆群聊 + 跨主人对话 + LLMUtil 统一校验 |
| v0.11.0 | MSM联动 (StorageCompat + 上下文) + Skills (elmai/enhanced_storage) + 思考气泡差异化 + 女仆社交修复 (密度感知+随机说话者) |
| v0.11.1 | Skill 重写高密度百科 + elma_quick 快查 + token 优化 (XML→纯文本+PromptConstants) + Config GUI 补全 |
| v0.12.0 | /elmai 命令树 + reasoningContent NBT 剥离 + 日志系统优化 (抑制父模组dump+自主截断) + 天气修复 + 环境事件范围冷却 + 女仆社交全链修复 (决策Prompt重构+线程安全+请求洪泛+claimProposal竞态) + 魂符2MB崩溃修复 + acceptIntoGroup竞态修复 |
