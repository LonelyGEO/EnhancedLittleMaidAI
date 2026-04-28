# MiningLittleMaid Config 系统技术文档

## 适用模组

基于 NeoForge 1.21.1 的 TouhouLittleMaid 附属模组

---

## 1. 整体架构

```
Config.java (ModConfigSpec 定义)
    ↓
@Mod 构造函数 → modContainer.registerConfig(Type.COMMON, SPEC)
    ↓
运行时生成: config/mininglittlemaid-common.toml
    ↓
MiningClothConfig.java 监听 AddClothConfigEvent
    ↓
游戏内通过 "Mods → Touhou Little Maid → 配置 → 采矿" 修改
```

**本模组不独立注册 `IConfigScreenFactory`**，而是通过 TLM 的 `AddClothConfigEvent` 将配置项注入到 TouhouLittleMaid 的设置菜单中。

---

## 2. Config.java 完整模板

```java
package com.example.modid.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.logging.log4j.Logger;

public class Config {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue MAX_VEIN_SIZE;
    public static final ModConfigSpec.IntValue MIN_LIGHT_LEVEL;
    public static final ModConfigSpec.IntValue TORCH_COOLDOWN_TICKS;
    public static final ModConfigSpec.IntValue COMBAT_RETURN_DELAY_TICKS;
    public static final ModConfigSpec.BooleanValue DEBUG_LOGGING;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Mining Little Maid 配置");
        builder.push("mining")
                .translation("config.modid.section.mining");

        MAX_VEIN_SIZE = builder
                .comment("矿脉连锁最大块数（仅好感度等级 3 生效）")
                .defineInRange("maxVeinSize", 8, 2, 64);

        MIN_LIGHT_LEVEL = builder
                .comment("亮度低于此值时女仆放置火把")
                .defineInRange("minLightLevel", 7, 0, 15);

        TORCH_COOLDOWN_TICKS = builder
                .comment("火把放置冷却时间（tick，20 tick = 1 秒）")
                .defineInRange("torchCooldownTicks", 120, 20, 600);

        COMBAT_RETURN_DELAY_TICKS = builder
                .comment("战斗结束后等多久切回采矿（tick，20 tick = 1 秒）")
                .defineInRange("combatReturnDelayTicks", 100, 20, 600);

        DEBUG_LOGGING = builder
                .comment("启用 Debug 日志输出（需重启生效）")
                .define("enableDebugLog", false);

        builder.pop();
        SPEC = builder.build();
    }

    public static void debugLog(Logger logger, String message, Object... params) {
        if (DEBUG_LOGGING.get()) {
            logger.debug(message, params);
        }
    }
}
```

生成的 TOML 文件：

```toml
# config/mininglittlemaid-common.toml
[mining]
    # 矿脉连锁最大块数（仅好感度等级 3 生效）
    # 范围: 2 ~ 64
    maxVeinSize = 8
    # 亮度低于此值时女仆放置火把
    # 范围: 0 ~ 15
    minLightLevel = 7
    # 火把放置冷却时间（tick，20 tick = 1 秒）
    # 范围: 20 ~ 600
    torchCooldownTicks = 120
    # 战斗结束后等多久切回采矿（tick，20 tick = 1 秒）
    # 范围: 20 ~ 600
    combatReturnDelayTicks = 100
    # 启用 Debug 日志输出（需重启生效）
    enableDebugLog = false
```

---

## 3. 配置值类型

| 方法 | 返回类型 | 用法 |
|------|---------|------|
| `.defineInRange(key, default, min, max)` | `IntValue` | 整数，自动约束范围 |
| `.define(key, default)` | `BooleanValue` | 布尔值 |
| `.defineInRange(key, default, min, max)` | `DoubleValue` | 浮点数（用 `Double` 参数） |
| `.define(key, default)` | `ConfigValue<String>` | 字符串 |
| `.defineList(key, default, validator)` | `ConfigValue<List<T>>` | 列表 |

---

## 4. 读取配置值

```java
// 在运行时读取（线程安全）
int veinSize = Config.MAX_VEIN_SIZE.get();
boolean enabled = Config.DEBUG_LOGGING.get();
```

**注意**：不要在 static 初始化块中调用 `.get()`，只能在 `static {}` 完成后读取。

---

## 5. 注册到 NeoForge

在 `@Mod` 主类的构造函数中：

```java
@Mod(MyMod.MOD_ID)
public class MyMod {
    public static final String MOD_ID = "my_mod";

    public MyMod(IEventBus modEventBus, ModContainer modContainer) {
        // 注册 COMMON 配置（客户端+服务端共用）
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }
}
```

**本模组不注册 `IConfigScreenFactory`**——这是刻意为之。本模组通过 TLM 的 `AddClothConfigEvent` 将配置项注入到 TouhouLittleMaid 的设置菜单中（见第 8 节）。

---

## 6. 配置文件类型对比

| 类型 | 文件 | 同步方式 | 适用场景 |
|------|------|---------|---------|
| `COMMON` | `modid-common.toml` | 客户端从服务端同步 | **推荐** 服务端管理，所有玩家统一 |
| `SERVER` | `modid-server.toml` | 仅服务端 | 服务端专属设置（备份间隔等） |
| `CLIENT` | `modid-client.toml` | 仅客户端 | 客户端显示设置（渲染等） |

> **本模组原则**：服务端管理的配置（`maxVeinSize` 等）放 `COMMON`；每只女仆的个性化设置（`显示挖矿提示` 等）放 NBT + 任务配置 Tab。

---

## 7. 分类 section（push/pop）

```java
builder.push("mining")                               // TOML 生成 [mining] 头
        .translation("config.modid.section.mining");  // 分类标题翻译键（Cloth Config 2 用）
// ... 配置项 ...
builder.pop();                                       // 必须与 push 配对
```

**`push` 生成效果**：

```toml
[mining]       ← builder.push("mining") 产生
maxVeinSize = 8
```

**`.translation()` 的作用**：在 Cloth Config 2 的 GUI 中显示为分类标题。本模组通过 `AddClothConfigEvent` 注入配置项时，相同的翻译键会自动带入 TLM 的配置菜单。

翻译键注册（语言文件）：

```json
{
    "config.modid.section.mining": "采矿"
}
```

---

## 8. 整合入 TLM 配置菜单（AddClothConfigEvent）

### 核心原理

TLM 在构建配置 GUI 后，通过 **`NeoForge.EVENT_BUS`**（GAME bus）发送 `AddClothConfigEvent`。附属模组只需监听该事件，即可向 TLM 的配置菜单中添加自定义分类和条目。

**不需要** Mixin、反射或 `IConfigScreenFactory`。

### MiningClothConfig.java 完整实现

```java
package com.example.modid.compat;

import com.example.modid.config.Config;
import com.github.tartaricacid.touhoulittlemaid.api.event.client.AddClothConfigEvent;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(bus = EventBusSubscriber.Bus.GAME)
public class MiningClothConfig {

    @SubscribeEvent
    public static void onAddClothConfig(AddClothConfigEvent event) {
        ConfigEntryBuilder entryBuilder = event.getEntryBuilder();
        ConfigCategory mining = event.getRoot().getOrCreateCategory(
                Component.translatable("config.modid.section.mining"));

        mining.addEntry(entryBuilder
                .startIntSlider(
                        Component.translatable("config.modid.maxVeinSize"),
                        Config.MAX_VEIN_SIZE.get(), 2, 64)
                .setDefaultValue(8)
                .setSaveConsumer(val -> Config.MAX_VEIN_SIZE.set(val))
                .setTooltip(Component.literal("仅好感度等级 3 生效"))
                .build());

        mining.addEntry(entryBuilder
                .startIntSlider(
                        Component.translatable("config.modid.minLightLevel"),
                        Config.MIN_LIGHT_LEVEL.get(), 0, 15)
                .setDefaultValue(7)
                .setSaveConsumer(val -> Config.MIN_LIGHT_LEVEL.set(val))
                .setTooltip(Component.literal("亮度低于此值时女仆放置火把"))
                .build());

        mining.addEntry(entryBuilder
                .startBooleanToggle(
                        Component.translatable("config.modid.enableDebugLog"),
                        Config.DEBUG_LOGGING.get())
                .setDefaultValue(false)
                .setSaveConsumer(val -> Config.DEBUG_LOGGING.set(val))
                .setTooltip(Component.literal("需重启生效"))
                .build());
    }
}
```

### 关键 API

| API | 来源 | 用途 |
|-----|------|------|
| `AddClothConfigEvent` | TLM (`api.event.client`) | 扩展点事件，在 TLM 构建配置菜单后发送 |
| `event.getRoot()` | → `me.shedaniel.clothconfig2.api.ConfigBuilder` | Cloth Config 根构建器 |
| `event.getEntryBuilder()` | → `ConfigEntryBuilder` | 创建滑块、开关等条目 |
| `getOrCreateCategory(Component)` | → `ConfigCategory` | 获取或创建一个分类 |
| `.setSaveConsumer(val -> Config.XXX.set(val))` | Cloth Config | 用户修改后写回 `ModConfigSpec` |

### 注册流程

| 步骤 | 文件 | 说明 |
|------|------|------|
| 1 | `Config.java` | 定义 `ModConfigSpec`（`push("mining")` + 5 项配置） |
| 2 | `@Mod` 构造函数 | `modContainer.registerConfig(COMMON, Config.SPEC)` |
| 3 | `MiningClothConfig.java` | `@SubscribeEvent` 监听 `AddClothConfigEvent` → 加条目 |
| 4 | 语言文件 | `config.modid.section.mining` + 各条目翻译键 |

### 入口路径

```
主菜单 → Mods → Touhou Little Maid → 配置 → 采矿（新增分类）
                                               ├── 矿脉连锁最大块数（滑块 2-64）
                                               ├── 火把放置亮度阈值（滑块 0-15）
                                               ├── 火把放置冷却时间（滑块 20-600）
                                               ├── 战斗切回延迟（滑块 20-600）
                                               └── 启用 Debug 日志（开关）
```

### 与 `IConfigScreenFactory` 的区别

| | IConfigScreenFactory | AddClothConfigEvent |
|---|---|---|
| 入口位置 | 独立的 Mod 配置页 | **TLM 配置菜单内部** |
| 依赖 | 需要 Cloth Config API 编译依赖 | 无需额外依赖（TLM 已提供） |
| 用户体验 | 用户需在 Mods 列表中找我们的 Mod | 用户只需打开 TLM 的配置页面 |
| 本模组使用 | ❌ 已废弃 | ✅ 当前方案 |

---

## 9. 多 section 扩展示例

大型模组可按功能拆分多个 section：

```java
static {
    ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

    builder.push("mining").translation("config.modid.section.mining");
    MAX_VEIN_SIZE = builder.defineInRange("maxVeinSize", 8, 2, 64);
    builder.pop();

    builder.push("combat").translation("config.modid.section.combat");
    COMBAT_DELAY = builder.defineInRange("combatDelay", 100, 20, 600);
    builder.pop();

    SPEC = builder.build();
}
```

生成 TOML：

```toml
[mining]
maxVeinSize = 8

[combat]
combatDelay = 100
```

在 Cloth Config 侧，每个 section 对应一个 `getOrCreateCategory()` 调用，需要为每个分类单独添加条目。

---

## 10. TouhouLittleMaid 父模组配置参考

TouhouLittleMaid 的 `GeneralConfig` 分为 6 个 section（使用 `builder.push`）：

| section | 翻译键 | 内容 |
|---------|--------|------|
| `[maid]` | `config.touhou_little_maid.maid` | 女仆基本行为 |
| `[chair]` | `config.touhou_little_maid.chair` | 椅子相关 |
| `[misc]` | `config.touhou_little_maid.misc` | 杂项 |
| `[vanilla]` | `config.touhou_little_maid.vanilla` | 原版交互 |
| `[render]` | `config.touhou_little_maid.render` | 渲染 |
| `[ai]` | *(无翻译)* | AI 相关 |

父模组在 `MenuIntegration.getConfigBuilder()` 末尾发送 `AddClothConfigEvent`，本模组即通过此钩子注入。

---

## 11. Debug/条件日志模式

```java
// Config.java 中定义
public static void debugLog(Logger logger, String message, Object... params) {
    if (DEBUG_LOGGING.get()) {
        logger.debug(message, params);
    }
}

// 业务代码中调用（用 { } 占位符避免字符串拼接）
Config.debugLog(LOGGER, "BFS search started, radius={}", radius);
```

---

## 12. 每只女仆的个性化设置（非 Config.java）

如需每只女仆独立配置，使用 **NBT 持久化** + **任务配置 Tab** 模式：

```java
// 写入（服务端）
maid.getPersistentData().putBoolean("my_key", true);

// 读取
boolean val = maid.getPersistentData().getBoolean("my_key");
// 注意：key 不存在时返回 false，用 contains 先判断默认值
boolean enabled = !maid.getPersistentData().contains("my_key")
        || maid.getPersistentData().getBoolean("my_key");
```

---

## 13. 完整文件清单

| 文件 | 职责 |
|------|------|
| `config/Config.java` | 定义 `ModConfigSpec`（`push("mining")` + 5 项配置 + `debugLog` 方法） |
| `compat/MiningClothConfig.java` | 监听 `AddClothConfigEvent`，向 TLM 配置菜单注入条目 |
| `lang/zh_cn.json` | 包含所有 `config.modid.*` 翻译键 |
| `lang/en_us.json` | 同上，英文版 |
| `YourMod.java` (@Mod) | `modContainer.registerConfig(COMMON, Config.SPEC)`（不要注册 `IConfigScreenFactory`） |
| `libs/cloth-config-neoforge-15.0.140.jar` | Cloth Config API（flatDir 本地依赖） |
