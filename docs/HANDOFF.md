# EnhancedLittleMaidAI 交接文档

> 版本: v0.10.0 | 平台: NeoForge 1.21.1 | 更新: 2026-05-01

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
| 联动 (可选) | MLM `libs/mininglittlemaid-1.0.0-*.jar` |
| 联动 (可选) | MSM `libs/1.21.1-maid_storage_manager-1.15.6.jar` |
| UI 依赖 | `libs/cloth-config-neoforge-15.0.140.jar` |

---

## 2. 源码结构

```
src/main/java/com/github/lonelygeo/enhancedlittlemaidai/
├── EnhancedLittleMaidAI.java           # @Mod 入口 → Config注册 + 事件注册 + 总调度
├── EnhancedLittleMaidExtension.java    # @LittleMaidExtension → 上下文注册
├── command/
│   └── MindPalaceCommand.java          # /mindpalace list|clear|stats
├── compat/
│   ├── MiningCompat.java               # MLM 反射兼容层
│   ├── MiningMessageHandler.java       # MLM 采矿事件 → LLM 接管
│   ├── MiningChatCallback.java         # MLM 采矿 LLM 回调
│   ├── StorageCompat.java              # MSM 反射兼容层
│   └── StorageMemoryHandler.java       # MSM 储物事件 → 记忆写入
├── config/
│   ├── EnhancedConfig.java             # NeoForge ModConfigSpec (所有配置项)
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
│   ├── EntityMaidMixin.java            # remove/tick 注入 (死亡记忆+主动聊天+女仆对话)
│   ├── LLMCallbackMixin.java           # onFunctionCall 重定向 + onSuccess 记忆提取
│   ├── LLMMessageMixin.java            # [空占位]
│   ├── LLMOpenAIClientMixin.java       # JSON reasoning_content 注入 + 缓存检查
│   ├── MaidAIChatDataMixin.java        # NBT 持久化 (MindPalace)
│   ├── MaidAIChatManagerMixin.java     # buildMessage 记忆上下文注入
│   └── MessageMixin.java               # [空占位]
└── util/
    ├── LLMUtil.java                    # LLM 可用性统一校验 (enabled+url+apiKey)
    ├── LLMResponseCache.java           # 响应 LRU 缓存 (20条)
    ├── SimpleResponseCache.java        # 通用缓存后端 (synchronized)
    ├── ProactiveChatManager.java       # 主动聊天冷却/频率控制
    ├── ProactiveChatCallback.java      # 主动聊天 LLM 回调 + 角色设定注入
    ├── EnvironmentEventDetector.java   # 环境事件检测 (日出/雨/雷/群系)
    ├── DayPhaseUtil.java               # 游戏时间 → 4 phase 映射
    ├── InterMaidChatManager.java       # 女仆对话管理器 (扫描/提案/群组/冷却)
    ├── InterMaidChatCallback.java      # 女仆间对话链式 LLM 回调
    ├── InterMaidDecisionCallback.java  # B接受提案的 LLM 决策回调
    ├── MaidSpawnHandler.java           # 女仆放置 → 设定生成 + 见面问候
    └── bm25/
        ├── ChineseTokenizer.java       # 字符级 bigram 分词器
        └── Bm25Index.java              # BM25 倒排索引
```

---

## 3. 7 个活跃 Mixin 注入点

| # | Mixin | 目标方法 | 注解 | 功能 |
|---|---|---|---|---|
| 1 | `EntityMaidMixin` | `remove` HEAD | `@Inject` | 死亡记忆写入 MindPalace |
| 2 | `EntityMaidMixin` | `remove` TAIL | `@Inject` | 清理所有管理器状态 |
| 3 | `EntityMaidMixin` | `tick` TAIL | `@Inject` | 环境事件/女仆对话/主动聊天分流调度 |
| 4 | `LLMCallbackMixin` | `onFunctionCall` | `@Redirect` ×2 | reasoning_content 注入 assistant 消息 (require=0) |
| 5 | `LLMCallbackMixin` | `onSuccess` TAIL | `@Inject` | 记忆提取+压缩触发 (跳过 InterMaid/Proactive) |
| 6 | `LLMOpenAIClientMixin` | `chat` HEAD | `@Inject(cancellable)` | 缓存检查 + reasoning 注入 + 回调捕获 |
| 7 | `LLMOpenAIClientMixin` | `Gson.toJson` | `@Redirect` | JSON 构建拦截 → injectReasoningContent() |
| 8 | `LLMOpenAIClientMixin` | `onTextCall` HEAD | `@Inject` | 响应文本提取 reasoning→addAssistantHistory |
| 9 | `LLMOpenAIClientMixin` | `onTextCall` TAIL | `@Inject` | 响应存入缓存 |
| 10 | `MaidAIChatDataMixin` | `readFromTag` TAIL | `@Inject` | 恢复 MindPalace 记忆 |
| 11 | `MaidAIChatDataMixin` | `writeToTag` TAIL | `@Inject` | 持久化 MindPalace 记忆 |
| 12 | `MaidAIChatManagerMixin` | `buildMessage` TAIL | `@Inject` | 注入 `<memory>` XML 上下文 |

---

## 4. 配置系统 (34 项)

TOM 文件: `config/enhancedlittlemaidai-common.toml`  
GUI 入口: `Mods → Touhou Little Maid → 配置 → 强化AI`

### 结构

```
强化AI
├── LLM 采矿对话 [开关]      (仅 MLM 加载时)
├── ▸ 上下文感知    (3项)
├── ▸ 记忆系统      (5项)
├── ▸ 主动聊天      (7项)
├── ▸ 女仆社交      (17项)
├── ▸ 仓储感知      (4项, 仅 MSM 加载)
└── ▸ 调试          (3项)
```

### 关键配置速查

| 段 | 键 | 默认 | 说明 |
|---|---|---|---|
| proactive_chat | `proactivePromptMode` | FULL | 主动聊天角色设定长度 (FULL/SUMMARY/MINIMAL) |
| proactive_chat | `cooldownTicks` | 12000 | 冷却 (600s 显示) |
| proactive_chat | `eventCooldownTicks` | 6000 | 环境事件冷却 (300s) |
| proactive_chat | `eventMaxPerDay` | 5 | 环境事件每日上限 |
| inter_maid | `enabled` | true | 女仆对话总开关 |
| inter_maid | `promptMode` | FULL | 女仆对话角色设定长度 |
| inter_maid | `maxRounds` | 2 | 对话轮数 |
| inter_maid | `maxGroupSize` | 3 | 最大群聊人数 |
| inter_maid | `crossOwner` | true | 跨主人对话 |
| inter_maid | `scanInterval` | 40 | 扫描间隔 tick |
| inter_maid | `decisionMode` | LLM | B接受决策 (LLM/WEIGHT) |
| storage_context | `enableStorageMemory` | true | 储物记忆写入 (需 MSM) |
| debug | `debugLog` | false | 调试日志全局开关 |
| debug | `enableMiningChat` | true | 采矿 LLM 接管开关 |
| debug | `enableMaidGreeting` | true | 女仆放置问候开关 |

---

## 5. 关键设计模式

### 5.1 反射兼容层 (MiningCompat / StorageCompat)

```
Compat.isLoaded() → ModList.get().isLoaded("modid")
  ↓
invokeStatic() / 独立反射方法 → Class.forName + Method.invoke
  ↓
失败 → 返回默认值 + LOGGER.warn
```

**模式**: 参照 `MiningCompat.java`。所有外部 API 调用通过反射，避免 `NoClassDefFoundError`。

### 5.2 反射注册 (ClothConfigIntegration / MiningMessageHandler / StorageMemoryHandler / MaidSpawnHandler)

```
@Mod 构造函数 → if (Compat.isLoaded()) {
    Class.forName("...Handler").getMethod("register").invoke(null)
}
  ↓
Handler.register() → NeoForge.EVENT_BUS.register(Handler.class)
  ↓
@SubscribeEvent 接收事件
```

**注意**: `StorageMemoryHandler` 用 `Object` 作为事件参数类型 (运行时匹配)，需 `debugLog` 确认路由正常。

### 5.3 LLM 可用性校验 (LLMUtil.isAvailable())

```
LLMUtil.isAvailable(maid)
  ├── site == null → false
  ├── !site.enabled() → false
  ├── site.url() 为空 → false
  └── secretKey() 为空 → false (仅 LLMOpenAISite)
```

**所有 LLM 调用入口必须先调用此方法**。5 个调用点已在 `ProactiveChatManager`、`EntityMaidMixin`、`InterMaidChatManager`、`MiningMessageHandler`、`MaidSpawnHandler` 中。

### 5.4 reasoning_content 处理

```
写入: Message.getReasoningContent() → addAssistantHistory(msg, toolCalls, rc)
  ↓ LLMMessage record 字段自动持久化 (TLM 内置)
读取: injectReasoningContent() → llmMsg.reasoningContent()
  ↓ JSON 层面注入 reasoning_content 字段
```

**已删除 `ReasoningContentStore`** (IdentityHashMap)。TLM 1.5.2+ 内置 reasoning 支持。

### 5.5 女仆间对话完整流程

```
EntityMaid.tick() → 概率扫描 (15%/2s)
  ↓ findPartners → 扫描附近已驯服女仆
  ↓ proposeGroup → 群组提案
  ↓ 各B独立 tick → canAccept → LLM决策/概率 → handleAcceptance
  ↓ tryStartConversation → 排序参与者 → startConversation
  ↓ InterMaidChatCallback → chain: round0 → 2s延迟 → round1 → ...
  ↓ finishConversation → stripDescription → 写入社交记忆 (socialStore)
```

**防刷屏**: B(概率扫描)+C(启动抖动0-600tick)

### 5.6 每日上限重置

```
EnvironmentEventDetector.detect() → SUNRISE
  ↓ EntityMaidMixin.tick
  ├── ProactiveChatManager.resetDayCounts(uuid)
  ├── EnvironmentEventDetector.resetDayCounts(uuid)
  ├── InterMaidChatManager.resetDayCounts(uuid)
  └── InterMaidChatManager.resetGlobalDayCount()
```

---

## 6. 已发现的陷阱 (Gotchas)

| # | 陷阱 | 解决方案 |
|---|---|---|
| 1 | **Mixin 非 private 方法** | 所有非注入方法必须 `private` 或 `@Unique`。`acceptAndTryStart` 曾因 `static` 且被跨包调用而崩溃 → 已移至 `InterMaidChatManager.handleAcceptance` |
| 2 | **`@SubscribeEvent` 用 `Object` 参数** | `StorageMemoryHandler` 的事件方法参数类型是 `Object`，运行时依赖 NeoForge 反射路由。无编译期检查，需 `debugLog` 确认 |
| 3 | **`.gitignore` `com/` 误伤** | 改为 `/com/` 仅匹配根目录。新文件放 `com/` 下可能被忽略 → 检查 `git check-ignore` |
| 4 | **LLMMessage 是 record** | `llmMsg.reasoningContent()` 直接读取。reasoning_content 在 NBT 反序列化后对象身份变化，之前 `IdentityHashMap` 已废弃 |
| 5 | **`MaidAIChatDataMixin` @Unique 被 Discarding** | 正常现象——TLM 已内置这些方法。`require=0` 保证兼容 |
| 6 | **`addLLMChatText` vs `addTextChatBubble`** | 前者替换思考气泡(需 bubbleId)，后者创建新文本气泡。交错使用时注意 bubbleId 生命周期 |
| 7 | **新女仆 `isTame()=false`** | `MaidSpawnHandler` 已删除此检查，女仆放置即可生成设定 |
| 8 | **MLM v1.0.0 依赖 `cloth_config`** | 需将 `cloth-config-neoforge-15.0.140.jar` 放在 `run/client/mods/` |

---

## 7. Cloth Config GUI 代码生成模式

添加新配置项的步骤:

1. `EnhancedConfig.java`: 声明字段 + `builder.define/defineInRange`
2. `ClothConfigHandler.java`: `entryBuilder.startXxx(...)` 构建 GUI 条目
3. `zh_cn.json` + `en_us.json`: 添加翻译键
4. `gradlew build` 验证

**子分类模式**:
```java
SubCategoryBuilder sub = entryBuilder.startSubCategory(Component.translatable("key.sub"));
sub.setExpanded(true);
sub.add(entryBuilder.startXxx(...).build());
category.addEntry(sub.build());
```

---

## 8. 构建与测试

```bash
# 编译+测试
./gradlew.bat build

# 仅测试
./gradlew.bat test

# 启动客户端 (需手动关闭游戏窗口)
./gradlew.bat runClient

# 推送
git push origin 1.21

# Release
gh release create v0.x.x build/libs/enhancedlittlemaidai-0.x.x-*.jar \
  --title "v0.x.x-beta" --prerelease --notes "..."
```

**运行前确保**:
- `run/client/mods/` 中有 `cloth-config-neoforge-15.0.140.jar` (MLM 需要)
- `run/client/mods/` 中有 MLM jar (如启用采矿联动)
- 已配置 LLM 站点 (URL + API Key)

---

## 9. 版本历史

| 版本 | 主要变更 |
|---|---|
| v0.6.x | reasoning_content + MindPalace 记忆系统 |
| v0.7.x | MiningMessageEvent LLM 接管 + Cloth Config |
| v0.8.x | 环境事件/上下文注入/LLM缓存/多女仆对话 |
| v0.9.x | 多女仆群聊 + 角色设定注入 |
| v0.10.x | 女仆放置问候 + 颜文字+翻译提示 + LLMUtil统一校验 + 刷屏修复 |
