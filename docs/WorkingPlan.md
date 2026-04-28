# Enhanced Little Maid AI —— 详细开发计划 V2

> **使用说明**：本文档包含精确的方法签名、Mixin 注解参数、注入点位置和完整的数据流。执行 agent 应按每个子任务的编号顺序逐一实现并验证。

---

## 项目技术约定

### 关键路径

| 项目 | 路径 |
|---|---|
| 附属模组根目录 | `G:\MCProject\EnhancedLittleMaidAI` |
| 基础模组根目录 | `G:\MCProject\Touhou Little Maid` |
| 附属模组源码 | `src/main/java/com/github/lonelygeo/enhancedlittlemaidai/` |
| Mixin 配置 | `src/main/resources/enhancedlittlemaidai.mixins.json` |

### 代码规范

- 4 空格缩进，K&R 大括号
- Mixin 命名：`<TargetClass>Mixin.java`，注解一律 `remap = false`
- 新增方法命名：`enhanced$` 前缀（避免与目标类方法冲突）
- 日志：`public static final Logger LOGGER = LogManager.getLogger(EnhancedLittleMaidAI.MOD_ID);`
- 零外部依赖：所有新代码使用 Java 标准库 + Minecraft/NeoForge API
- 包路径：`com.github.lonelygeo.enhancedlittlemaidai`

### 已有 Mixin 清单（可修改）

| Mixin 文件 | 目标类 | 已用注入点 |
|---|---|---|
| `MaidAIChatDataMixin.java` | `MaidAIChatData` | `readFromTag` @TAIL, `writeToTag` @TAIL; `@Shadow getMaid()`, `@Shadow getHistory()`, `@Shadow onHistoryUpdated()` |
| `LLMCallbackMixin.java` | `LLMCallback` | `onFunctionCall` @Redirect ×2; `onSuccess` @TAIL（记忆提取+压缩+关键词触发） |
| `LLMOpenAIClientMixin.java` | `LLMOpenAIClient` | `chat` @HEAD, `Gson.toJson()` @Redirect, `onTextCall` @HEAD |
| `EntityMaidMixin.java` | `EntityMaid` | `remove` @HEAD（死亡记忆）; `remove` @TAIL（清理）; `tick` @TAIL（主动聊天） |
| `MaidAIChatManagerMixin.java` | `MaidAIChatManager` | `buildMessage` @TAIL（记忆注入） |
| `ChatMessageMixin.java` | `ChatMessage` | 空占位 |
| `LLMMessageMixin.java` | `LLMMessage` | 空占位 |
| `MessageMixin.java` | `Message` | 空占位 |

### 基础模组关键 API 速查

```java
// === 扩展注册 ===
@LittleMaidExtension                    // 必须加此注解
public class Xxx implements ILittleMaid {
    public Xxx() {}                     // 必须有无参构造器
    // 重写 default 方法注册能力
    default void registerAIMaidContext(GameContextRegister register) {}
    default void registerAITool(ToolRegister register) {}
}

// === 上下文注册 (GameContextRegister) ===
public void registerCategory(String categoryId, String categorySummary, boolean promptContext)
// promptContext=true  → 直接注入每条用户消息（UserPromptContexts.addContext）
// promptContext=false → 通过 query_game_context 工具按需查询

public void registerContext(String categoryId, IMaidContext context)

// === IMaidContext 接口 ===
public interface IMaidContext {
    String key();           // 全局唯一键
    String label();         // 显示标签
    String getValue(EntityMaid maid);   // 计算当前值
}
// 格式化规则：getContext() 内部拼接为 "- {label}: {value}"

// === 工具注册 (ToolRegister) ===
public void register(ITool<?> skill)

// === LLM 消息 (LLMMessage, Java record) ===
public record LLMMessage(Role role, String message, long gameTime,
                         @Nullable List<ToolCall> toolCalls, @Nullable String toolCallId,
                         @Nullable String reasoningContent)
public static LLMMessage systemChat(EntityMaid maid, String message)  // 新建 SYSTEM 消息
public static LLMMessage userChat(EntityMaid maid, String message)
public static LLMMessage assistantChat(EntityMaid maid, String message)
public static LLMMessage assistantChat(EntityMaid maid, String message, @Nullable String reasoningContent)

// === Role 枚举 ===
SYSTEM, USER, ASSISTANT, TOOL, DEVELOPER

// === LLM 请求构建 (LLMOpenAIClient.chat) ===
// 遍历 callback.getMessages() 构建 ChatCompletion
// system role → chatCompletion.developerChat() (推理模型) 或 systemChat()
// user role → chatCompletion.userChat()
// assistant role → chatCompletion.assistantChat()
// 最后 GSON.toJson(chatCompletion) → HTTP POST

// === 消息列表构建 (MaidAIChatManager.buildMessage) ===
private List<LLMMessage> buildMessage(String setting, EntityMaid maid, CappedQueue<LLMMessage> history)
// 返回：[0] SYSTEM(角色设定), [1] SYSTEM(压缩摘要,可选), [2+] 历史消息(old→new)

// === normalChat 流程 ===
private void normalChat(String message, List<LLMMessage> messages, LLMClient chatClient)
// 1. String messageWithContext = UserPromptContexts.addContext(maid, message);
// 2. messages.add(LLMMessage.userChat(maid, messageWithContext));
// 3. callback = new LLMCallback(this, messages);
// 4. chatClient.chat(callback);
```

---

## 阶段一：世界上下文增强

### 目标
让 LLM 能感知女仆周围方块、光照、实体详情。新增上下文通过 `query_game_context` 工具按需获取。

---

### 任务 1.1：创建 @LittleMaidExtension 入口类

**文件**：`src/main/java/com/github/lonelygeo/enhancedlittlemaidai/EnhancedLittleMaidExtension.java`

```java
package com.github.lonelygeo.enhancedlittlemaidai;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.ai.agent.context.GameContextRegister;
import com.github.lonelygeo.enhancedlittlemaidai.context.BlockAwareContexts;

/**
 * 附属模组扩展入口，由本体通过 AnnotatedInstanceUtil.getModExtensions() 自动发现。
 * 必须有无参构造器 + @LittleMaidExtension 注解。
 */
@LittleMaidExtension
public class EnhancedLittleMaidExtension implements ILittleMaid {

    public EnhancedLittleMaidExtension() {
    }

    @Override
    public void registerAIMaidContext(GameContextRegister register) {
        // promptContext=false → 通过 query_game_context 工具按需获取
        register.registerCategory("nearby_blocks",
                "Block types around the maid (BFS depth 5 sampling)",
                false);
        register.registerCategory("entity_details",
                "Detailed info of nearby entities (HP, profession, hostility, etc.)",
                false);
        register.registerCategory("environment_detail",
                "Light level, indoor/outdoor status, and redstone power",
                false);

        BlockAwareContexts.registerAll(register);
    }
}
```

---

### 任务 1.2：创建上下文提供者

**文件**：`src/main/java/com/github/lonelygeo/enhancedlittlemaidai/context/BlockAwareContexts.java`

```java
package com.github.lonelygeo.enhancedlittlemaidai.context;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.context.GameContextRegister;
import com.github.tartaricacid.touhoulittlemaid.ai.agent.context.IMaidContext;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.*;

public final class BlockAwareContexts {
    private static final int BFS_MAX_DEPTH = 5;
    private static final int ENTITY_RADIUS = 16;
    private static final int MAX_ENTITIES = 30;

    public static void registerAll(GameContextRegister register) {
        register.registerContext("nearby_blocks", new NearbyBlocksContext());
        register.registerContext("environment_detail", new EnvironmentDetailContext());
        register.registerContext("entity_details", new EntityDetailContext());
    }

    // ==================== 1. 附近方块上下文 ====================

    private static class NearbyBlocksContext implements IMaidContext {
        @Override public String key() { return "nearby_blocks"; }
        @Override public String label() { return "Nearby Blocks"; }

        @Override
        public String getValue(EntityMaid maid) {
            Level level = maid.level();
            BlockPos center = maid.blockPosition();
            Map<Block, Integer> counts = new LinkedHashMap<>();

            // BFS 采样：深度限制为 BFS_MAX_DEPTH
            Set<BlockPos> visited = new HashSet<>();
            Deque<BlockPos> queue = new ArrayDeque<>();
            queue.add(center);
            visited.add(center);

            int depth = 0;
            while (!queue.isEmpty() && depth <= BFS_MAX_DEPTH) {
                int layerSize = queue.size();
                for (int i = 0; i < layerSize; i++) {
                    BlockPos pos = queue.poll();
                    Block block = level.getBlockState(pos).getBlock();
                    counts.merge(block, 1, Integer::sum);
                    // 每层仅扩展 4 个水平方向 + 上下各 1
                    for (BlockPos next : sampleDirections(pos, depth)) {
                        if (visited.add(next)) {
                            queue.add(next);
                        }
                    }
                }
                depth++;
            }

            // top-15 最常出现方块
            StringBuilder sb = new StringBuilder();
            counts.entrySet().stream()
                    .sorted(Map.Entry.<Block, Integer>comparingByValue().reversed())
                    .limit(15)
                    .forEach(e -> sb.append(e.getKey().getDescriptionId())
                            .append(" x").append(e.getValue()).append(", "));
            return sb.isEmpty() ? "none" : sb.toString();
        }

        private static BlockPos[] sampleDirections(BlockPos center, int depth) {
            // 深度越大，步长越大（粗略采样）
            return new BlockPos[]{
                    center.north(), center.south(), center.east(), center.west(),
                    center.above(), center.below()  // 上下各 1
            };
        }
    }

    // ==================== 2. 环境详情上下文 ====================

    private static class EnvironmentDetailContext implements IMaidContext {
        @Override public String key() { return "environment_detail"; }
        @Override public String label() { return "Environment Detail"; }

        @Override
        public String getValue(EntityMaid maid) {
            BlockPos pos = maid.blockPosition();
            Level level = maid.level();
            int light = level.getMaxLocalRawBrightness(pos);
            boolean skyVisible = level.canSeeSky(pos);
            boolean indoors = !skyVisible && light < 8;
            int redstone = level.getBestNeighborSignal(pos);
            // 简单室内判断：头顶有实心方块
            boolean hasCeiling = level.getBlockState(pos.above(3)).isSolid();

            return String.format("light=%d, indoors=%b, sky_visible=%b, redstone_power=%d",
                    light, indoors || hasCeiling, skyVisible, redstone);
        }
    }

    // ==================== 3. 实体详情上下文 ====================

    private static class EntityDetailContext implements IMaidContext {
        @Override public String key() { return "entity_details"; }
        @Override public String label() { return "Nearby Entity Details"; }

        @Override
        public String getValue(EntityMaid maid) {
            Level level = maid.level();
            AABB range = maid.getBoundingBox().inflate(ENTITY_RADIUS);
            List<LivingEntity> entities = level.getEntitiesOfClass(
                    LivingEntity.class, range,
                    e -> e != maid && e.isAlive()
            );
            entities.sort(Comparator.comparingDouble(e -> e.distanceToSqr(maid)));
            if (entities.size() > MAX_ENTITIES) {
                entities = entities.subList(0, MAX_ENTITIES);
            }

            StringBuilder sb = new StringBuilder();
            for (LivingEntity e : entities) {
                sb.append(e.getDisplayName().getString());
                sb.append("(hp=").append((int) e.getHealth()).append("/").append((int) e.getMaxHealth()).append(")");
                sb.append(", dist=").append((int) e.distanceTo(maid));
                if (e instanceof Villager v) {
                    sb.append(", profession=").append(v.getVariant().toString());
                }
                if (e instanceof TamableAnimal t) {
                    sb.append(t.isTame() ? ", tamed" : "");
                    sb.append(t.isAggressive() ? ", aggressive" : "");
                }
                if (e instanceof Mob m && m.getTarget() != null) {
                    sb.append(", targeting=").append(m.getTarget().getDisplayName().getString());
                }
                sb.append("; ");
            }
            return sb.isEmpty() ? "none" : sb.toString();
        }
    }
}
```

**性能说明**：
- BFS 采样深度 5，最多约 62 个采样点（每层 6 方向 × 5 层 + 1 中心 ≈ 31 实际去重后），纯方块读取无写操作
- 实体查询限制 16 格半径 + 30 数量上限
- 上下文生成在 LLM API 调用过程中，不阻塞 tick loop
- **不添加缓存**（缓存逻辑由 GameContextRegister 内部或本体自行管理；如性能有问题再补充）

---

### 任务 1.3：编译验证

执行 `./gradlew.bat check`，确认新文件编译通过，无方法签名错误。

---

### 任务 1.4：功能验证

1. 运行 `./gradlew.bat runClient`
2. 打开女仆 AI 聊天
3. 向女仆发送"查看附近有哪些方块"之类的消息
4. 确认 LLM 调用了 `query_game_context` 工具且返回了 `nearby_blocks` / `entity_details` / `environment_detail` 类别内容
5. 确认上下文格式正确（`- label: value` 每行一条）

---

## 阶段二：长期记忆（思维宫殿）

### 目标
女仆能记住去过的地方、玩家偏好、重要事件。使用 BM25 算法检索，NBT 持久化跨重启不丢失。

---

### 任务 2.1：BM25 检索引擎（纯 Java，零外部依赖）

#### 2.1.1 ChineseTokenizer

**文件**：`src/main/java/com/github/lonelygeo/enhancedlittlemaidai/util/bm25/ChineseTokenizer.java`

```java
package com.github.lonelygeo.enhancedlittlemaidai.util.bm25;

import java.util.ArrayList;
import java.util.List;

/**
 * 字符级 bigram 分词器：
 * - CJK 字符（U+4E00–U+9FFF, U+3400–U+4DBF, U+F900–U+FAFF）用 bigram
 * - 英文/数字按空白分割
 * - 示例："铁矿石在洞穴" → 6 token（重叠 bigram：铁矿 矿石 石在 在洞 洞穴 穴）
 * - 示例："x=100 y=64" → ["x=100", "y=64"]
 */
public final class ChineseTokenizer {
    private ChineseTokenizer() {}

    public static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null || text.isEmpty()) return tokens;

        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isCJK(c)) {
                if (!buffer.isEmpty()) {
                    tokens.add(buffer.toString().toLowerCase());
                    buffer.setLength(0);
                }
                if (i + 1 < text.length() && isCJK(text.charAt(i + 1))) {
                    tokens.add(String.valueOf(c) + text.charAt(i + 1));
                } else {
                    tokens.add(String.valueOf(c));
                }
            } else if (Character.isWhitespace(c)) {
                if (!buffer.isEmpty()) {
                    tokens.add(buffer.toString().toLowerCase());
                    buffer.setLength(0);
                }
            } else {
                buffer.append(c);
            }
        }
        if (!buffer.isEmpty()) {
            tokens.add(buffer.toString().toLowerCase());
        }
        return tokens;
    }

    private static boolean isCJK(char c) {
        return Character.isIdeographic(c);
    }
}
```

#### 2.1.2 Bm25Index

**文件**：`src/main/java/com/github/lonelygeo/enhancedlittlemaidai/util/bm25/Bm25Index.java`

```java
package com.github.lonelygeo.enhancedlittlemaidai.util.bm25;

import java.util.*;

/**
 * BM25 倒排索引。
 * 
 * BM25 参数：
 *   k1 = 1.2  （词频饱和度系数）
 *   b  = 0.75 （文档长度归一化系数）
 * 
 * 公式：score(q, d) = Σ IDF(t) × (tf(t,d) × (k1+1)) / (tf(t,d) + k1 × (1-b + b × |d|/avgdl))
 * 其中 IDF(t) = ln((N - n(t) + 0.5) / (n(t) + 0.5) + 1)
 */
public class Bm25Index {
    private static final double K1 = 1.2;
    private static final double B = 0.75;

    private final Map<String, Document> documents = new HashMap<>();
    private final Map<String, Map<String, Integer>> invertedIndex = new HashMap<>();
    private long totalLength = 0;

    public void add(String docId, String content) {
        remove(docId);
        List<String> tokens = ChineseTokenizer.tokenize(content);
        if (tokens.isEmpty()) return;

        documents.put(docId, new Document(docId, tokens));
        totalLength += tokens.size();

        for (String token : tokens) {
            invertedIndex.computeIfAbsent(token, k -> new HashMap<>())
                    .merge(docId, 1, Integer::sum);
        }
    }

    public void remove(String docId) {
        Document old = documents.remove(docId);
        if (old == null) return;
        totalLength -= old.tokens.size();

        for (String token : old.tokens) {
            Map<String, Integer> postings = invertedIndex.get(token);
            if (postings != null) {
                int count = postings.remove(docId) - 1;
                if (count <= 0) {
                    postings.remove(docId);
                } else {
                    postings.put(docId, count);
                }
                if (postings.isEmpty()) {
                    invertedIndex.remove(token);
                }
            }
        }
    }

    public List<ScoredDoc> search(String query, int topK) {
        List<String> queryTokens = ChineseTokenizer.tokenize(query);
        if (queryTokens.isEmpty()) return List.of();

        int N = documents.size();
        double avgdl = N > 0 ? (double) totalLength / N : 1.0;

        Map<String, Double> scores = new HashMap<>();
        for (String term : queryTokens) {
            Map<String, Integer> postings = invertedIndex.get(term);
            if (postings == null) continue;
            int df = postings.size();

            // IDF
            double idf = Math.log((N - df + 0.5) / (df + 0.5) + 1.0);

            for (Map.Entry<String, Integer> entry : postings.entrySet()) {
                String docId = entry.getKey();
                int tf = entry.getValue();
                Document doc = documents.get(docId);
                if (doc == null) continue;

                double tfNorm = (tf * (K1 + 1.0))
                        / (tf + K1 * (1.0 - B + B * doc.tokens.size() / avgdl));
                scores.merge(docId, idf * tfNorm, Double::sum);
            }
        }

        // 归一化：除以查询词数
        double normFactor = Math.max(1, queryTokens.size());
        return scores.entrySet().stream()
                .map(e -> new ScoredDoc(e.getKey(), e.getValue() / normFactor))
                .sorted(Comparator.comparingDouble(ScoredDoc::score).reversed())
                .limit(topK)
                .toList();
    }

    public int size() {
        return documents.size();
    }

    // ================== 内部类型 ==================

    public record ScoredDoc(String docId, double score) {}

    private record Document(String id, List<String> tokens) {}
}
```

**性能**：
- 添加/删除文档：O(平均词数)，<1ms（记忆 100 条、每条 80 字 ≈ 40 token）
- 检索 Top-K：O(查询词数 × 平均倒排列表长度)，<1ms

---

### 任务 2.2：记忆数据模型

#### 2.2.1 MemoryCategory

**文件**：`src/main/java/com/github/lonelygeo/enhancedlittlemaidai/memory/MemoryCategory.java`

```java
package com.github.lonelygeo.enhancedlittlemaidai.memory;

import com.mojang.serialization.Codec;

public enum MemoryCategory {
    PLACE("地点"),
    PERSON("人物"),
    EVENT("事件"),
    PREFERENCE("偏好"),
    KNOWLEDGE("知识");

    private final String displayName;

    MemoryCategory(String displayName) { this.displayName = displayName; }

    public String displayName() { return displayName; }

    public static final Codec<MemoryCategory> CODEC =
            Codec.STRING.xmap(MemoryCategory::valueOf, MemoryCategory::name);
}
```

#### 2.2.2 MemoryItem

**文件**：`src/main/java/com/github/lonelygeo/enhancedlittlemaidai/memory/MemoryItem.java`

```java
package com.github.lonelygeo.enhancedlittlemaidai.memory;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.Optional;
import java.util.UUID;

/**
 * 思维宫殿中的一条记忆。
 * 使用 Java record => equals/hashCode 自动生成，便于 Set 去重。
 */
public record MemoryItem(
        UUID id,
        MemoryCategory category,
        String content,                 // 压缩后的简短文本（<=80 字）
        Optional<BlockPos> location,    // 空间关联
        Optional<String> dimension,     // 维度
        long gameTime,                  // 游戏时间戳
        int accessCount,                // 被检索召回次数
        int importance                  // 1-5
) {
    public static final Codec<MemoryItem> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(MemoryItem::id),
            MemoryCategory.CODEC.fieldOf("category").forGetter(MemoryItem::category),
            Codec.STRING.fieldOf("content").forGetter(MemoryItem::content),
            BlockPos.CODEC.optionalFieldOf("location").forGetter(MemoryItem::location),
            Codec.STRING.optionalFieldOf("dimension").forGetter(MemoryItem::dimension),
            Codec.LONG.fieldOf("game_time").forGetter(MemoryItem::gameTime),
            Codec.INT.fieldOf("access_count").forGetter(MemoryItem::accessCount),
            Codec.INT.fieldOf("importance").forGetter(MemoryItem::importance)
    ).apply(instance, MemoryItem::new));

    // ==================== NBT 序列化（不用 Codec，直接用 Tag 读写保证效率） ====================

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("category", category.name());
        tag.putString("content", content);
        location.ifPresent(pos -> tag.putLong("location", pos.asLong()));
        dimension.ifPresent(dim -> tag.putString("dimension", dim));
        tag.putLong("gameTime", gameTime);
        tag.putInt("accessCount", accessCount);
        tag.putInt("importance", importance);
        return tag;
    }

    public static MemoryItem fromTag(CompoundTag tag) {
        return new MemoryItem(
                tag.getUUID("id"),
                MemoryCategory.valueOf(tag.getString("category")),
                tag.getString("content"),
                tag.contains("location") ? Optional.of(BlockPos.of(tag.getLong("location"))) : Optional.empty(),
                tag.contains("dimension") ? Optional.of(tag.getString("dimension")) : Optional.empty(),
                tag.getLong("gameTime"),
                tag.getInt("accessCount"),
                tag.getInt("importance")
        );
    }

    // ==================== 辅助方法 ====================

    /** 召回时返回一个新实例（accessCount 递增），因为 record 不可变 */
    public MemoryItem incrementAccess() {
        return new MemoryItem(id, category, content, location, dimension,
                gameTime, accessCount + 1, importance);
    }

    /**
     * 淘汰评分：重要性 × 0.4 + 新鲜度 × 0.3 + 频率 × 0.3
     * score 越高越应**保留**；分数最低的优先淘汰。
     */
    public double evictionScore(long currentTime, long maxAgeInTicks) {
        double recency = 1.0 - Math.min(1.0, (double) (currentTime - gameTime) / maxAgeInTicks);
        double frequency = Math.min(1.0, accessCount / 10.0);
        return importance * 0.4 + recency * 0.3 + frequency * 0.3;
    }
}
```

---

### 任务 2.3：MemoryStore（记忆存储 + 检索）

**文件**：`src/main/java/com/github/lonelygeo/enhancedlittlemaidai/memory/MemoryStore.java`

```java
package com.github.lonelygeo.enhancedlittlemaidai.memory;

import com.github.lonelygeo.enhancedlittlemaidai.util.bm25.Bm25Index;
import net.minecraft.core.BlockPos;

import java.util.*;

/**
 * 单个女仆的记忆存储。
 * 管理记忆列表 + BM25 索引，提供增删检 + 去重 + 淘汰。
 */
public class MemoryStore {
    private static final int MAX_MEMORIES = 100;
    private static final int COMPRESS_TRIGGER = 80;
    private static final double DEDUP_SCORE_THRESHOLD = 0.85;
    private static final long MAX_AGE_TICKS = 20L * 60 * 20; // 20 分钟 * 20tick/s = 24000 tick ≈ 1 Minecraft 天? 实际=30天

    private final List<MemoryItem> memories = new ArrayList<>();
    private final Bm25Index index = new Bm25Index();

    // ==================== CRUD ====================

    /**
     * 添加记忆。返回 true 表示成功添加，false 表示重复被跳过。
     */
    public boolean add(MemoryItem memory, long gameTime) {
        // 去重检测：BM25 检索相似记忆
        List<Bm25Index.ScoredDoc> similar = index.search(memory.content(), 1);
        if (!similar.isEmpty() && similar.getFirst().score() > DEDUP_SCORE_THRESHOLD) {
            return false;
        }

        memories.add(memory);
        index.add(memory.id().toString(), memory.content());

        // 容量管理
        if (memories.size() > MAX_MEMORIES) {
            evict();
        }

        return true;
    }

    /** 从备份恢复记忆（跳过去重，因为来自 NBT） */
    public void restore(MemoryItem memory) {
        memories.add(memory);
        index.add(memory.id().toString(), memory.content());
    }

    // ==================== 检索 ====================

    /** BM25 语义检索 Top-K */
    public List<MemoryItem> retrieve(String query, int topK) {
        List<Bm25Index.ScoredDoc> results = index.search(query, topK);
        List<MemoryItem> result = new ArrayList<>();
        for (Bm25Index.ScoredDoc doc : results) {
            for (int i = 0; i < memories.size(); i++) {
                MemoryItem m = memories.get(i);
                if (m.id().toString().equals(doc.docId())) {
                    memories.set(i, m.incrementAccess());
                    result.add(m);
                    break;
                }
            }
        }
        return result;
    }

    /** 空间召回：在指定位置半径范围内的记忆 */
    public List<MemoryItem> retrieveByLocation(BlockPos pos, int radius) {
        int radiusSq = radius * radius;
        return memories.stream()
                .filter(m -> m.location().isPresent())
                .filter(m -> m.location().get().distSqr(pos) <= radiusSq)
                .sorted(Comparator.comparingInt(MemoryItem::importance).reversed())
                .limit(5)
                .toList();
    }

    // ==================== 容量管理 ====================

    /** 淘汰 lowest-score 记忆 */
    private void evict() {
        long now = 0; // 实际调用时传入当前游戏时间
        List<MemoryItem> sorted = new ArrayList<>(memories);
        sorted.sort(Comparator.comparingDouble(m -> -m.evictionScore(now, MAX_AGE_TICKS)));

        memories.clear();
        for (int i = 0; i < Math.min(sorted.size(), MAX_MEMORIES); i++) {
            memories.add(sorted.get(i));
        }

        // 重建索引
        rebuildIndex();
    }

    public void evict(long currentGameTime) {
        long now = currentGameTime;
        if (memories.size() <= MAX_MEMORIES) return;

        memories.sort(Comparator.comparingDouble(m -> -m.evictionScore(now, MAX_AGE_TICKS)));
        while (memories.size() > MAX_MEMORIES) {
            MemoryItem removed = memories.removeLast();
            index.remove(removed.id().toString());
        }
    }

    public boolean needsCompression() {
        return memories.size() >= COMPRESS_TRIGGER;
    }

    // ==================== 序列化 ====================

    public List<MemoryItem> toList() {
        return Collections.unmodifiableList(memories);
    }

    public static MemoryStore fromList(List<MemoryItem> items) {
        MemoryStore store = new MemoryStore();
        for (MemoryItem item : items) {
            store.restore(item);
        }
        return store;
    }

    public int size() {
        return memories.size();
    }

    // ==================== 内部 ====================

    private void rebuildIndex() {
        for (MemoryItem m : memories) {
            index.add(m.id().toString(), m.content());
        }
    }
}
```

---

### 任务 2.4：MindPalace 全局管理器

**文件**：`src/main/java/com/github/lonelygeo/enhancedlittlemaidai/memory/MindPalace.java`

```java
package com.github.lonelygeo.enhancedlittlemaidai.memory;

import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 思维宫殿 —— 每个女仆实例对应一个 MindPalace。
 * 存储于全局 Map<MaidUUID, MindPalace>，模式与 ReasoningContentStore 一致。
 */
public class MindPalace {
    private static final Map<UUID, MindPalace> PALACES =
            Collections.synchronizedMap(new LinkedHashMap<>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<UUID, MindPalace> eldest) {
                    return size() > 1000; // 安全上限，防止泄漏
                }
            });

    private static final int SPATIAL_RECALL_RADIUS = 16;
    private static final int SEMANTIC_RETRIEVE_TOP_K = 3;

    private final UUID maidUuid;
    private final MemoryStore store = new MemoryStore();
    private int chatRoundCounter = 0;
    private long lastExtractionGameTime = 0;

    private MindPalace(UUID maidUuid) {
        this.maidUuid = maidUuid;
    }

    // ==================== 全局访问 ====================

    public static MindPalace getOrCreate(UUID maidUuid) {
        return PALACES.computeIfAbsent(maidUuid, MindPalace::new);
    }

    public static MindPalace get(UUID maidUuid) {
        return PALACES.get(maidUuid);
    }

    public static void remove(UUID maidUuid) {
        PALACES.remove(maidUuid);
    }

    // ==================== 记忆上下文构建 ====================

    /**
     * 构建 `<memory>` XML 片段，用于注入到 LLM 消息列表。
     * 包含：语义检索 Top-K + 空间召回（如果女仆在记忆位置附近）。
     */
    public String buildMemoryContext(String userMessage, BlockPos maidPos) {
        // 1. BM25 语义检索
        List<MemoryItem> semanticResults = store.retrieve(userMessage, SEMANTIC_RETRIEVE_TOP_K);

        // 2. 空间召回
        List<MemoryItem> spatialResults = store.retrieveByLocation(maidPos, SPATIAL_RECALL_RADIUS);

        // 3. 合并去重
        Set<MemoryItem> merged = new LinkedHashSet<>(semanticResults);
        merged.addAll(spatialResults);

        if (merged.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("<memory>\n");
        for (MemoryItem m : merged) {
            sb.append("  <item");
            if (m.location().isPresent()) {
                BlockPos loc = m.location().get();
                sb.append(" location=\"").append(loc.getX())
                        .append(",").append(loc.getY())
                        .append(",").append(loc.getZ()).append("\"");
            }
            sb.append(" category=\"").append(m.category().name().toLowerCase()).append("\"");
            sb.append(">").append(m.content()).append("</item>\n");
        }
        sb.append("</memory>");
        return sb.toString();
    }

    // ==================== 记忆提取计数器（由 Mixin 调用） ====================

    public void incrementRoundCounter() {
        chatRoundCounter++;
    }

    public boolean shouldExtractMemories(long currentGameTime) {
        return chatRoundCounter % 5 == 0 && chatRoundCounter > 0
                && (currentGameTime - lastExtractionGameTime) > 100; // 避免同一 tick 重复提取
    }

    public void markExtractionDone(long currentGameTime) {
        lastExtractionGameTime = currentGameTime;
    }

    // ==================== 记忆写入（来自异步 LLM 提取结果） ====================

    public void addMemories(List<MemoryItem> items, long currentGameTime) {
        for (MemoryItem item : items) {
            store.add(item, currentGameTime);
        }
        store.evict(currentGameTime);
    }

    // ==================== NBT 持久化 ====================

    /** 从 NBT 恢复记忆列表 */
    public void readFromTag(CompoundTag tag) {
        if (!tag.contains("MindPalaceMemories", Tag.TAG_LIST)) return;
        ListTag list = tag.getList("MindPalaceMemories", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            MemoryItem item = MemoryItem.fromTag(list.getCompound(i));
            store.restore(item);
        }
    }

    /** 将记忆列表写入 NBT */
    public void writeToTag(CompoundTag tag) {
        ListTag list = new ListTag();
        for (MemoryItem m : store.toList()) {
            list.add(m.toTag());
        }
        tag.put("MindPalaceMemories", list);
    }

    // ==================== getter ====================

    public MemoryStore getStore() {
        return store;
    }

    public int size() {
        return store.size();
    }

    public boolean needsCompression() {
        return store.needsCompression();
    }
}
```

---

### 任务 2.5：记忆注入 Mixin

**文件**：`src/main/java/com/github/lonelygeo/enhancedlittlemaidai/mixin/MaidAIChatManagerMixin.java`

**目的**：在 `MaidAIChatManager.buildMessage()` 的返回值中插入记忆上下文 SYSTEM 消息。

```java
package com.github.lonelygeo.enhancedlittlemaidai.mixin;

import com.github.lonelygeo.enhancedlittlemaidai.memory.MindPalace;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatManager;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.util.CappedQueue;
import net.minecraft.core.BlockPos;
import org.apache.commons.lang3.StringUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 在 LLM 请求构建阶段，将思维宫殿记忆注入为 SYSTEM 消息。
 * 
 * 注入位置：buildMessage() 返回的消息列表 index=2（紧跟角色设定 + 压缩摘要之后）
 */
@Mixin(value = MaidAIChatManager.class, remap = false)
public abstract class MaidAIChatManagerMixin {

    /** Shadow: MaidAIChatData.getMaid() */
    @Shadow
    public abstract EntityMaid getMaid();

    /**
     * 在 buildMessage() 返回前，插入记忆上下文。
     * 
     * buildMessage 签名：
     *   private List<LLMMessage> buildMessage(String setting, EntityMaid maid,
     *                                         CappedQueue<LLMMessage> history)
     */
    @Inject(
            method = "buildMessage",
            at = @At("TAIL"),
            remap = false
    )
    private void enhanced$addMemoryContext(
            String setting,
            EntityMaid maid,
            CappedQueue<LLMMessage> history,
            CallbackInfoReturnable<List<LLMMessage>> cir
    ) {
        try {
            MindPalace palace = MindPalace.get(maid.getUUID());
            if (palace == null || palace.size() == 0) return;

            // 提取当前用户消息关键词：无用户消息时用 setting 内容
            // buildMessage 阶段还没有用户消息，因此用角色设定中提取关键词
            String query = StringUtils.substring(setting, 0, Math.min(200, setting.length()));
            String memoryXml = palace.buildMemoryContext(query, maid.blockPosition());

            if (!memoryXml.isEmpty()) {
                LLMMessage memoryMessage = LLMMessage.systemChat(maid, memoryXml);
                List<LLMMessage> messages = cir.getReturnValue();
                // 插入位置：角色设定[0] + 历史摘要(可选)[1] 之后
                int insertPos = Math.min(2, messages.size());
                messages.add(insertPos, memoryMessage);
            }
        } catch (Exception e) {
            // 记忆注入失败不应中断正常聊天流程
        }
    }
}
```

**注意**：`buildMessage` 是 `private` 方法。Mixin 可以注入 `private` 方法（通过方法名匹配）。

---

### 任务 2.6：记忆提取 Mixin

#### 2.6.1 MemoryExtractionCallback

**文件**：`src/main/java/com/github/lonelygeo/enhancedlittlemaidai/memory/MemoryExtractionCallback.java`

```java
package com.github.lonelygeo.enhancedlittlemaidai.memory;

import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatManager;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.response.ResponseChat;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 子代理 Callback：专门用于向 LLM 请求记忆提取。
 * 继承 LLMCallback，onSuccess 时解析 LLM 返回结果为 MemoryItem 列表。
 */
public class MemoryExtractionCallback extends LLMCallback implements MemoryCallback<MemoryExtractionCallback> {

    private final CompletableFuture<List<MemoryItem>> future;

    public MemoryExtractionCallback(
            MaidAIChatManager chatManager,
            List<LLMMessage> messages,
            CompletableFuture<List<MemoryItem>> future
    ) {
        super(chatManager, messages, true); // 标记为 subagents=true
        this.needAddTools = false;           // 记忆提取不需要工具
        this.future = future;
    }

    @Override
    public void onSuccess(ResponseChat responseChat) {
        String text = responseChat.chatText();
        List<MemoryItem> items = MemoryResponseParser.parse(text);
        future.complete(items);
    }

    @Override
    public void onFailure(com.github.tartaricacid.touhoulittlemaid.ai.service.llm.openai.request.HttpRequest request,
                          Throwable throwable, int errorCode) {
        future.completeExceptionally(
                throwable != null ? throwable : new RuntimeException("Memory extraction failed: " + errorCode));
    }

    @Override
    public MemoryExtractionCallback self() { return this; }

    /** 构建记忆提取 prompt */
    public static String buildPrompt(List<String> recentMessages) {
        return """
                You are a memory extraction system. From the conversation below, extract information 
                worth permanently remembering. Output one memory per line in this format:
                [CATEGORY] content | location:x,y,z,dimension (optional)
                
                Rules:
                - CATEGORY must be one of: PLACE, PERSON, EVENT, PREFERENCE, KNOWLEDGE
                - content must be under 80 characters
                - Only extract genuinely important long-term information
                - Skip casual greetings and trivial chitchat
                - Rate each memory importance from 1 (low) to 5 (high)
                - Output ONLY the memory lines, nothing else
                
                Example output:
                [PLACE] 钻石在地下室第二个箱子里 | location:200,64,-100,minecraft:overworld | importance:4
                [PREFERENCE] 主人喜欢用铁镐挖矿 | importance:3
                [EVENT] 矿洞里遭遇了爬行者破坏通道 | location:150,12,50,minecraft:overworld | importance:5
                
                Conversation:
                %s
                """.formatted(String.join("\n---\n", recentMessages));
    }

    /** 记忆回调接口 */
    public interface MemoryCallback<T> {
        T self();
    }
}
```

#### 2.6.2 MemoryResponseParser

**文件**：`src/main/java/com/github/lonelygeo/enhancedlittlemaidai/memory/MemoryResponseParser.java`

```java
package com.github.lonelygeo.enhancedlittlemaidai.memory;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

/**
 * 解析 LLM 记忆提取返回文本。
 * 格式：[CATEGORY] content | location:x,y,z,dimension | importance:N
 */
public final class MemoryResponseParser {
    private MemoryResponseParser() {}

    public static List<MemoryItem> parse(String llmOutput) {
        List<MemoryItem> items = new ArrayList<>();
        if (StringUtils.isBlank(llmOutput)) return items;

        for (String line : llmOutput.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;

            MemoryCategory category = extractCategory(line);
            if (category == null) continue;

            String content = extractContent(line);
            if (content.isEmpty() || content.length() > 80) continue;

            Optional<BlockPos> location = extractLocation(line);
            Optional<String> dimension = extractDimension(line);
            int importance = extractImportance(line);

            items.add(new MemoryItem(
                    UUID.randomUUID(),
                    category,
                    content,
                    location,
                    dimension,
                    System.currentTimeMillis() / 50L, // 近似游戏时间
                    0,
                    importance
            ));
        }
        return items;
    }

    private static MemoryCategory extractCategory(String line) {
        for (MemoryCategory cat : MemoryCategory.values()) {
            if (line.contains("[" + cat.name() + "]")) return cat;
        }
        return null;
    }

    private static String extractContent(String line) {
        // 内容在 ] 和 | 之间
        int start = line.indexOf(']');
        if (start < 0) return "";
        int end = line.indexOf('|', start);
        if (end < 0) end = line.length();
        return line.substring(start + 1, end).trim();
    }

    private static Optional<BlockPos> extractLocation(String line) {
        // location:x,y,z
        int start = line.indexOf("location:");
        if (start < 0) return Optional.empty();
        String locPart = line.substring(start + 9).split("[\\s|]+")[0];
        String[] parts = locPart.split(",");
        if (parts.length < 3) return Optional.empty();
        try {
            return Optional.of(new BlockPos(
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim())
            ));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static Optional<String> extractDimension(String line) {
        int start = line.indexOf("location:");
        if (start < 0) return Optional.empty();
        String locPart = line.substring(start + 9).split("[\\s|]+")[0];
        String[] parts = locPart.split(",");
        if (parts.length >= 4) return Optional.of(parts[3].trim());
        return Optional.empty();
    }

    private static int extractImportance(String line) {
        int start = line.indexOf("importance:");
        if (start < 0) return 3; // 默认中值
        try {
            return Integer.parseInt(line.substring(start + 11).trim().split("[\\s|]+")[0]);
        } catch (NumberFormatException e) {
            return 3;
        }
    }
}
```

#### 2.6.3 修改 LLMCallbackMixin（新增记忆提取注入点）

**文件**：修改现有 `src/main/java/com/github/lonelygeo/enhancedlittlemaidai/mixin/LLMCallbackMixin.java`

在已有内容之后，新增以下方法：

```java
// ==================== 新增：记忆提取  ====================

@Inject(
        method = "onSuccess",
        at = @At("TAIL"),
        remap = false
)
private void enhanced$extractMemoriesOnSuccess(
        com.github.tartaricacid.touhoulittlemaid.ai.manager.response.ResponseChat responseChat,
        CallbackInfo ci
) {
    try {
        EntityMaid maid = getMaid();
        if (maid == null || maid.isRemoved()) return;

        MindPalace palace = MindPalace.getOrCreate(maid.getUUID());
        palace.incrementRoundCounter();

        long gameTime = maid.level().getGameTime();
        if (!palace.shouldExtractMemories(gameTime)) return;
        palace.markExtractionDone(gameTime);

        // 异步提取记忆
        triggerAsyncMemoryExtraction(maid, palace);
    } catch (Exception e) {
        // 静默失败，不影响正常对话
    }
}

// @Unique 辅助方法（需要加 @Unique 注解）
@Unique
private void enhanced$triggerAsyncMemoryExtraction(EntityMaid maid, MindPalace palace) {
    // FIXME: 需要从 maid 获取 LLMSite。如果 LLM 未配置则跳过。
    // 实现要点：
    // 1. 获取 maid.getAiChatManager().getLLMSite()
    // 2. 获取最近 5 轮对话的文本
    // 3. 构建 MemoryExtractionCallback
    // 4. 调用 LLM 异步提取
    // 5. 在 CompletableFuture thenAccept 中将结果写入 palace
}
```

> **待执行 agent 处理**：`enhanced$triggerAsyncMemoryExtraction` 的实现需要：
> 1. 通过 `maid.getAiChatManager()` 获取 `LLMSite`
> 2. 提取最近对话文本
> 3. 创建 `MemoryExtractionCallback` 并发送 LLM 请求
> 4. 异步回调中调用 `palace.addMemories(items, gameTime)`
> 
> 具体 LLM 调用方式参考 `LLMOpenAIClient.chat()` — 构建 `ChatCompletion` → `GSON.toJson()` → `httpClient.sendAsync()`。

同时需要新增 Shadow 方法和 import：
```java
// 在文件顶部新增 import
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.lonelygeo.enhancedlittlemaidai.memory.MindPalace;

// 新增 @Shadow 声明
@Shadow public abstract EntityMaid getMaid();
// （如果已存在则不需要重复声明）
```

---

### 任务 2.7：NBT 持久化 Mixin

**文件**：修改现有 `src/main/java/com/github/lonelygeo/enhancedlittlemaidai/mixin/MaidAIChatDataMixin.java`

在已有的 `enhanced$readReasoningContent` 和 `enhanced$writeReasoningContent` 方法中追加 MindPalace 逻辑。

#### 修改 readFromTag 注入点

找到已有的 `enhanced$readReasoningContent` 方法，在其**末尾**追加：

```java
// 在 enhanced$readReasoningContent 方法体内追加（推理内容恢复之后）
// ---- 追加：恢复 MindPalace ----
EntityMaid maid = getMaid();
if (maid != null) {
    MindPalace palace = MindPalace.getOrCreate(maid.getUUID());
    palace.readFromTag(cir.getReturnValue());
}
```

#### 修改 writeToTag 注入点

找到已有的 `enhanced$writeReasoningContent` 方法，在其**末尾**追加：

```java
// 在 enhanced$writeReasoningContent 方法体内追加（推理内容保存之后）
// ---- 追加：保存 MindPalace ----
EntityMaid maid = getMaid();
if (maid != null) {
    MindPalace palace = MindPalace.get(maid.getUUID());
    if (palace != null && palace.size() > 0) {
        palace.writeToTag(cir.getReturnValue());
    }
}
```

> **注意**：`readFromTag` 和 `writeToTag` 的注入方法签名均为
> `(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir)`

需要在 Mixin 文件顶部新增 import：
```java
import com.github.lonelygeo.enhancedlittlemaidai.memory.MindPalace;
```

---

### 任务 2.8：记忆压缩器（基础实现）

**文件**：`src/main/java/com/github/lonelygeo/enhancedlittlemaidai/memory/MemoryCompressor.java`

```java
package com.github.lonelygeo.enhancedlittlemaidai.memory;

import com.github.lonelygeo.enhancedlittlemaidai.util.bm25.Bm25Index;

import java.util.*;

/**
 * 当记忆数超过 80 条时，合并相似旧记忆。
 * 
 * 策略：
 * 1. 筛选游戏时间 > 5 天前的记忆
 * 2. 对相似度 > 0.7 的记忆对合并为一条总结
 * 3. 简单实现：直接丢弃最旧的 N 条低分记忆
 */
public final class MemoryCompressor {
    private MemoryCompressor() {}

    /**
     * 精简记忆至目标数量。
     * 返回被移除的记忆列表（供日志记录）。
     */
    public static List<MemoryItem> compress(MemoryStore store, int targetSize, long currentGameTime) {
        if (store.size() <= targetSize) return List.of();

        List<MemoryItem> all = new ArrayList<>(store.toList());
        // 按淘汰分数排序（高分在前）
        all.sort(Comparator.comparingDouble(m -> -m.evictionScore(currentGameTime, 20L * 60 * 20 * 30)));

        List<MemoryItem> removed = new ArrayList<>(all.subList(targetSize, all.size()));
        List<MemoryItem> kept = new ArrayList<>(all.subList(0, targetSize));

        // 重建：清空后重新加入
        MemoryStore newStore = new MemoryStore();
        for (MemoryItem item : kept) {
            newStore.restore(item);
        }

        // 注意：MemoryStore 不支持直接替换内部数据。
        // 这里需要 MemoryStore 提供一个 replace 方法，或者直接操作内部字段。
        // 暂时采用 MemoryStore.add() 逐个添加。
        return removed;
    }
}
```

> **实现说明**：`MemoryCompressor.compress()` 当前是简化版（直接去尾）。
> 如果需要 LLM 合并，则参考 `MemoryExtractionCallback` 模式异步调用 LLM 合并。

---

### 任务 2.9：更新 Mixin 配置

**文件**：`src/main/resources/enhanced_little_maid_ai.mixins.json`

在 `"mixins"` 数组中新增 `"MaidAIChatManagerMixin"`：

```json
{
    "required": true,
    "minVersion": "0.8",
    "package": "com.github.lonelygeo.enhancedlittlemaidai.mixin",
    "compatibilityLevel": "JAVA_17",
    "refmap": "enhancedlittlemaidai.refmap.json",
    "mixins": [
        "ChatMessageMixin",
        "LLMMessageMixin",
        "MessageMixin",
        "LLMOpenAIClientMixin",
        "LLMCallbackMixin",
        "MaidAIChatDataMixin",
        "MaidAIChatManagerMixin"
    ],
    "injectors": {
        "defaultRequire": 1
    }
}
```

---

### 任务 2.10：编译 + 验证

1. 执行 `./gradlew.bat check`，确认全部代码编译通过
2. 运行 `./gradlew.bat runClient`
3. 功能验证步骤：
   - 和女仆对话 5 轮以上
   - 在对话中明确告知一些"值得记住"的信息（如"我喜欢铁镐"，"地下室的箱子里有钻石"）
   - 检查日志确认记忆提取触发
   - 退出游戏重进，对话中询问有关记忆，确认女仆引用了记忆内容
   - 使用 `/data get entity` 命令检查女仆 NBT 中是否有 `MindPalaceMemories` 标签
4. 性能验证：
   - 观察 F3 调试界面 tick 时间
   - BM25 索引操作应 <1ms
   - 记忆提取 LLM 调用应异步，不阻塞 tick

---

## 阶段三：Mining Little Maid 联动

### 目标
让 LLM 感知女仆采矿环境（附近有哪些矿石、当前采矿状态），在对话中自然回应采矿相关话题。

**联动模式**：单向读取（Enhanced → Mining），互不依赖。
- Enhanced 在运行时检测 `mining_little_maid` 是否加载
- 加载时读取 Mining 侧公开 API（`MiningFavorGate`、`MaidMineBreakTask`）
- Mining 完全不知道 Enhanced 的存在，独立发布

### 前置依赖
- MiningLittleMaid 已编译的 jar 文件：`libs/mininglittlemaid-0.4.2-neoforge+mc1.21.1.jar`
- 需要先从 `G:\CursorProject\Mining-Little-Maid` 执行 `gradlew build`，将 `build/libs/` 下的 jar 复制到本项目 `libs/` 目录

---

### 任务 3.1：添加可选依赖声明

#### 3.1.1 build.gradle

**修改**：在 `dependencies` 块中添加 `compileOnly` 依赖：

```groovy
dependencies {
    // 依赖原版 TouhouLittleMaid 模组（编译 + 运行时）
    implementation files("libs/touhoulittlemaid-1.5.2-neoforge+mc1.21.1.jar")

    // MiningLittleMaid 联动（编译时引用 API，运行时 optional）
    compileOnly files("libs/mininglittlemaid-0.4.2-neoforge+mc1.21.1.jar")

    // 单元测试
    testImplementation 'junit:junit:4.13.2'
}
```

#### 3.1.2 neoforge.mods.toml

**修改**：在 `[[dependencies."${mod_id}"]]` 块末尾新增 optional 依赖声明：

```toml
[[dependencies."${mod_id}"]]
    modId = "mining_little_maid"
    type = "optional"
    versionRange = "[1.0.0,)"
    ordering = "AFTER"
    side = "BOTH"
```

> **说明**：`type = "optional"` 表示该模组不存在时 Enhanced 仍可正常加载，仅跳过 mining 相关功能。

---

### 任务 3.2：创建 MiningCompat 运行时检测封装

**文件**：`src/main/java/com/github/lonelygeo/enhancedlittlemaidai/compat/MiningCompat.java`

**设计要点**：
- 使用 `ModList.get().isLoaded("mining_little_maid")` 做运行时检测
- Mining 侧的类引用通过反射调用，避免 `NoClassDefFoundError`
- 所有方法都是 static，调用方先检查 `isLoaded()` 再调用

```java
package com.github.lonelygeo.enhancedlittlemaidai.compat;

import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MiningLittleMaid 运行时兼容层。
 * 所有 Mining 侧 API 调用通过反射实现，确保 Mining 未安装时不会触发 NoClassDefFoundError。
 * <p>
 * 使用方式：
 * <pre>{@code
 * if (MiningCompat.isLoaded()) {
 *     boolean isOre = MiningCompat.isMineableOre(blockState);
 *     int radius = MiningCompat.getSniffRadius(favorLevel);
 * }
 * }</pre>
 */
public final class MiningCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger("EnhancedLittleMaidAI:MiningCompat");
    private static final boolean LOADED = ModList.get().isLoaded("mining_little_maid");

    private MiningCompat() {}

    // ==================== 检测 ====================

    /** 运行时是否已加载 MiningLittleMaid */
    public static boolean isLoaded() {
        return LOADED;
    }

    // ==================== MiningFavorGate API ====================

    /**
     * 检查方块是否为可挖掘的矿石。
     * 对应 {@code MiningFavorGate.isMineableOre(BlockState)}
     */
    public static boolean isMineableOre(BlockState state) {
        return invokeStatic("com.github.tartaricacid.mining_little_maid.task.MiningFavorGate",
                "isMineableOre", false, state);
    }

    /**
     * 根据好感度等级获取嗅探半径。
     * 对应 {@code MiningFavorGate.getSniffRadius(int)}
     * @return 嗅探半径，失败时返回 1
     */
    public static int getSniffRadius(int favorLevel) {
        return invokeStatic("com.github.tartaricacid.mining_little_maid.task.MiningFavorGate",
                "getSniffRadius", 1, favorLevel);
    }

    /**
     * 检查在给定好感度等级下是否可以挖掘该方块。
     * 对应 {@code MiningFavorGate.canMineAtLevel(BlockState, int)}
     */
    public static boolean canMineAtLevel(BlockState state, int favorLevel) {
        return invokeStatic("com.github.tartaricacid.mining_little_maid.task.MiningFavorGate",
                "canMineAtLevel", false, state, favorLevel);
    }

    // ==================== 反射工具 ====================

    @SuppressWarnings("unchecked")
    private static <T> T invokeStatic(String className, String methodName, T defaultValue, Object... args) {
        try {
            Class<?> clazz = Class.forName(className);
            Class<?>[] paramTypes = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) {
                paramTypes[i] = args[i].getClass();
                // 处理基本类型的自动装箱
                if (paramTypes[i] == Integer.class) paramTypes[i] = int.class;
                if (paramTypes[i] == Boolean.class) paramTypes[i] = boolean.class;
            }
            return (T) clazz.getMethod(methodName, paramTypes).invoke(null, args);
        } catch (Exception e) {
            LOGGER.warn("Failed to invoke {}.{}: {}", className, methodName, e.getMessage());
            return defaultValue;
        }
    }
}
```

---

### 任务 3.3：创建 MiningContextProvider

**文件**：`src/main/java/com/github/lonelygeo/enhancedlittlemaidai/context/MiningContextProvider.java`

**功能**：实现 `IMaidContext`，在女仆周围扫描矿石并提供摘要信息给 LLM。

```java
package com.github.lonelygeo.enhancedlittlemaidai.context;

import com.github.lonelygeo.enhancedlittlemaidai.compat.MiningCompat;
import com.github.tartaricacid.touhoulittlemaid.ai.agent.context.IMaidContext;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * 采矿环境上下文 —— 扫描女仆周围矿石并格式化为 LLM 可读文本。
 * 扫描半径由好感度等级决定（通过 MiningCompat.getSniffRadius 获取）。
 */
public final class MiningContextProvider {

    private MiningContextProvider() {}

    // ==================== 附近矿石 ====================

    public static IMaidContext createNearbyOresContext() {
        return new IMaidContext() {
            @Override
            public String key() {
                return "mining_nearby_ores";
            }

            @Override
            public String label() {
                return "Nearby Ores";
            }

            @Override
            public String getValue(EntityMaid maid) {
                if (!MiningCompat.isLoaded()) return "unavailable";

                Level level = maid.level();
                BlockPos center = maid.blockPosition();
                int favorLevel = maid.getFavorabilityManager().getLevel();
                int sniffRadius = MiningCompat.getSniffRadius(favorLevel);

                // 统计各矿石种类及数量
                Map<Block, Integer> oreCounts = new LinkedHashMap<>();

                for (int x = -sniffRadius; x <= sniffRadius; x++) {
                    for (int y = -sniffRadius; y <= sniffRadius; y++) {
                        for (int z = -sniffRadius; z <= sniffRadius; z++) {
                            BlockPos pos = center.offset(x, y, z);
                            BlockState state = level.getBlockState(pos);
                            if (MiningCompat.isMineableOre(state)) {
                                oreCounts.merge(state.getBlock(), 1, Integer::sum);
                            }
                        }
                    }
                }

                if (oreCounts.isEmpty()) {
                    return "No mineable ores detected within sniff range (radius=" + sniffRadius + ")";
                }

                // 按数量降序排列
                StringBuilder sb = new StringBuilder();
                oreCounts.entrySet().stream()
                        .sorted(Map.Entry.<Block, Integer>comparingByValue().reversed())
                        .forEach(e -> sb.append(e.getKey().getDescriptionId())
                                .append(" x").append(e.getValue()).append(", "));
                sb.append("| sniff_radius=").append(sniffRadius);
                return sb.toString();
            }
        };
    }

    // ==================== 采矿状态 ====================

    public static IMaidContext createMiningStatusContext() {
        return new IMaidContext() {
            @Override
            public String key() {
                return "mining_status";
            }

            @Override
            public String label() {
                return "Mining Status";
            }

            @Override
            public String getValue(EntityMaid maid) {
                if (!MiningCompat.isLoaded()) return "unavailable";

                // 检查女仆当前任务是否为采矿任务
                // TaskManager 中的任务通过 UID 查找
                Object taskManager = maid.getTaskManager();
                if (taskManager == null) return "idle";

                try {
                    Object currentTask = taskManager.getClass()
                            .getMethod("getCurrentTask")
                            .invoke(taskManager);
                    if (currentTask == null) return "idle";

                    Object uid = currentTask.getClass()
                            .getMethod("getUid")
                            .invoke(currentTask);
                    boolean isMining = "mining_little_maid:mining".equals(
                            uid.getClass().getMethod("toString").invoke(uid));

                    if (!isMining) return "idle";

                    // 获取好感度门控信息
                    int favorLevel = maid.getFavorabilityManager().getLevel();
                    int sniffRadius = MiningCompat.getSniffRadius(favorLevel);

                    // 获取当前工具耐久（镐子）
                    String toolInfo = "no_pickaxe";
                    ItemStack mainHand = maid.getMainHandItem();
                    if (mainHand.getItem() instanceof PickaxeItem pickaxe) {
                        int durability = mainHand.getMaxDamage() - mainHand.getDamageValue();
                        int maxDurability = mainHand.getMaxDamage();
                        toolInfo = String.format("%s, durability=%d/%d",
                                pickaxe.getDescriptionId(), durability, maxDurability);
                    }

                    return String.format("active, favor_level=%d, sniff_radius=%d, tool=%s",
                            favorLevel, sniffRadius, toolInfo);

                } catch (Exception e) {
                    return "unknown";
                }
            }
        };
    }
}
```

> **注意事项**：
> - `maid.getTaskManager()` / `TaskManager.getCurrentTask()` / `getUid()` 是 TouhouLittleMaid 本体的 API，需要确认实际可用
> - 如果本体 API 路径不同，执行 agent 需根据实际 jar 中的方法签名调整
> - 当前已知 `TaskMining.getUid()` 返回 `ResourceLocation` 对应 UID `mining_little_maid:mining`
> - `mining_status` 输出示例：`active, favor_level=2, sniff_radius=2, tool=item.minecraft.iron_pickaxe, durability=43/250`；非采矿任务返回 `idle`；未持镐时 tool 为 `no_pickaxe`

---

### 任务 3.4：在 EnhancedLittleMaidExtension 中注册 mining 上下文

**修改文件**：`src/main/java/com/github/lonelygeo/enhancedlittlemaidai/EnhancedLittleMaidExtension.java`

在原 `registerAIMaidContext` 方法末尾新增 mining 上下文注册：

```java
@Override
public void registerAIMaidContext(GameContextRegister register) {
    // promptContext=false → 通过 query_game_context 工具按需获取
    register.registerCategory("nearby_blocks",
            "Block types around the maid (BFS depth 5 sampling)",
            false);
    register.registerCategory("entity_details",
            "Detailed info of nearby entities (HP, profession, hostility, etc.)",
            false);
    register.registerCategory("environment_detail",
            "Light level, indoor/outdoor status, and redstone power",
            false);

    BlockAwareContexts.registerAll(register);

    // ---- 新增：MiningLittleMaid 联动上下文 ----
    if (com.github.lonelygeo.enhancedlittlemaidai.compat.MiningCompat.isLoaded()) {
        register.registerCategory("mining_info",
                "Mining-specific info: nearby ores and maid mining status",
                false);
        register.registerContext("mining_info",
                com.github.lonelygeo.enhancedlittlemaidai.context.MiningContextProvider.createNearbyOresContext());
        register.registerContext("mining_info",
                com.github.lonelygeo.enhancedlittlemaidai.context.MiningContextProvider.createMiningStatusContext());
    }
}
```

---

### 任务 3.5：编译 + 验证

1. 确保 `libs/mining_little_maid-1.0.0-neoforge+mc1.21.1.jar` 已放入
2. 执行 `./gradlew.bat check`，确认编译通过
3. 运行 `./gradlew.bat runClient`
4. **不带 Mining** 验证：移除 Mining jar，确认 Enhanced 正常启动不崩溃
5. **带 Mining** 验证：放入 Mining jar，启动游戏，让女仆执行采矿任务，通过 AI 对话询问"附近有什么矿石"，确认 LLM 返回了 `query_game_context` 且包含 `mining_info` 内容
6. 检查日志无异常

---

## 完整文件清单

| # | 文件 | 操作 |
|---|---|---|
| 1 | `EnhancedLittleMaidExtension.java` | **新建** |
| 2 | `context/BlockAwareContexts.java` | **新建** |
| 3 | `util/bm25/ChineseTokenizer.java` | **新建** |
| 4 | `util/bm25/Bm25Index.java` | **新建** |
| 5 | `memory/MemoryCategory.java` | **新建** |
| 6 | `memory/MemoryItem.java` | **新建** |
| 7 | `memory/MemoryStore.java` | **新建** |
| 8 | `memory/MindPalace.java` | **新建** |
| 9 | `mixin/MaidAIChatManagerMixin.java` | **新建** |
| 10 | `memory/MemoryExtractionCallback.java` | **新建** |
| 11 | `memory/MemoryResponseParser.java` | **新建** |
| 12 | `memory/MemoryCompressor.java` | **新建** |
| 13 | `mixin/LLMCallbackMixin.java` | **修改**（新增记忆提取注入） |
| 14 | `mixin/MaidAIChatDataMixin.java` | **修改**（新增 MindPalace NBT 持久化） |
| 15 | `resources/enhanced_little_maid_ai.mixins.json` | **修改**（新增 Mixin 声明） |
| 16 | `compat/MiningCompat.java` | **新建**（阶段三） |
| 17 | `context/MiningContextProvider.java` | **新建**（阶段三） |
| 18 | `EnhancedLittleMaidExtension.java` | **修改**（阶段三：新增 mining 上下文注册） |
| 19 | `build.gradle` | **修改**（阶段三：新增 compileOnly 依赖） |
| 20 | `resources/META-INF/neoforge.mods.toml` | **修改**（阶段三：新增 optional 依赖） |

---

## ✅ 阶段五：女仆主动聊天（已完成）

### 目标
女仆不定时主动发起 LLM 对话与玩家聊天，通过多层限制避免大量消耗 token。

---

### 任务 5.1：ProactiveChatManager（冷却/限速管理器）

**文件**：`util/ProactiveChatManager.java`（新建）

```java
package com.github.lonelygeo.enhancedlittlemaidai.util;

import com.github.lonelygeo.enhancedlittlemaidai.EnhancedLittleMaidAI;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatManager;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMClient;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMSite;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 女仆主动聊天管理器。
 * 负责冷却计时、频率控制、触发条件检查，防止大量消耗 LLM token。
 *
 * 限制规则：
 * - 冷却时间：两次主动聊天之间最少间隔 10 分钟 (12000 ticks)
 * - 触发概率：冷却结束后每次 tick 有 0.2% 概率触发（预期 25 秒）
 * - 会话上限：每次游戏会话最多 8 次主动聊天
 * - 距离限制：主人玩家须在 10 格以内
 * - 前置条件：LLM 站点已启用、主人是玩家且在线
 */
public final class ProactiveChatManager {
    private static final long COOLDOWN_TICKS = 12000;           // 10 分钟
    private static final double TRIGGER_CHANCE_PER_TICK = 0.002; // 0.2%/tick
    private static final int MAX_CHATS_PER_SESSION = 8;
    private static final double MIN_PLAYER_DISTANCE_SQ = 100.0;  // 10 格

    private static final Map<UUID, Long> lastChatTime =
            Collections.synchronizedMap(new HashMap<>());
    private static final Map<UUID, Integer> chatCount =
            Collections.synchronizedMap(new HashMap<>());

    private ProactiveChatManager() {}

    /**
     * 检查是否可以触发主动聊天。
     * 每 tick 调用，性能开销 O(1)。
     */
    public static boolean canTrigger(EntityMaid maid) {
        // 仅服务端
        if (maid.level().isClientSide()) return false;
        if (maid.isRemoved()) return false;

        // 检查主人是否在线且在附近
        LivingEntity owner = maid.getOwner();
        if (!(owner instanceof ServerPlayer)) return false;
        if (owner.distanceToSqr(maid) > MIN_PLAYER_DISTANCE_SQ) return false;

        UUID uuid = maid.getUUID();
        long currentTime = maid.level().getGameTime();

        // 冷却检查
        Long lastTime = lastChatTime.get(uuid);
        if (lastTime != null && (currentTime - lastTime) < COOLDOWN_TICKS) {
            return false;
        }

        // 会话上限
        int count = chatCount.getOrDefault(uuid, 0);
        if (count >= MAX_CHATS_PER_SESSION) {
            return false;
        }

        // 随机触发
        if (Math.random() >= TRIGGER_CHANCE_PER_TICK) {
            return false;
        }

        // LLM 站点可用性检查
        MaidAIChatManager chatManager = maid.getAiChatManager();
        if (chatManager == null) return false;
        LLMSite site = chatManager.getLLMSite();
        if (site == null || !site.enabled()) return false;
        LLMClient client = site.client();
        if (client == null) return false;

        return true;
    }

    public static void markTriggered(UUID uuid, long gameTime) {
        lastChatTime.put(uuid, gameTime);
        chatCount.merge(uuid, 1, Integer::sum);
    }

    public static void reset(UUID uuid) {
        lastChatTime.remove(uuid);
        chatCount.remove(uuid);
    }
}
```

**关键设计**：
- `canTrigger()` 中先做廉价检查（client side、removed、owner），再做 LLM 站点检查
- 最后才调用 `Math.random()`，减少无效随机数开销
- 所有 Map 使用 `Collections.synchronizedMap` 保证线程安全（LLM 回调在 HTTP 线程）

---

### 任务 5.2：ProactiveChatCallback（主动聊天 LLM 回调）

**文件**：`util/ProactiveChatCallback.java`（新建）

```java
package com.github.lonelygeo.enhancedlittlemaidai.util;

import com.github.lonelygeo.enhancedlittlemaidai.EnhancedLittleMaidAI;
import com.github.lonelygeo.enhancedlittlemaidai.memory.MindPalace;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatManager;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.response.ResponseChat;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.LivingEntity;
import org.apache.commons.lang3.StringUtils;

import java.net.http.HttpRequest;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 主动聊天 LLM 回调。
 * 继承 LLMCallback，覆写 onSuccess() 以：
 * 1. 将 LLM 回复显示为女仆聊天气泡
 * 2. 同时向主人玩家发送系统消息
 * 3. 不调用 super.onSuccess()，避免触发 LLMCallbackMixin 的记忆提取链
 */
public class ProactiveChatCallback extends LLMCallback {
    private final CompletableFuture<String> future;
    private long waitingBubbleId = -1;

    public ProactiveChatCallback(
            MaidAIChatManager chatManager,
            List<LLMMessage> messages,
            CompletableFuture<String> future
    ) {
        super(chatManager, messages, true);
        this.needAddTools = false;
        this.future = future;
    }

    /** 设置"思考中"气泡 ID，由 caller 在 chat() 前调用。 */
    public void setWaitingBubbleId(long id) {
        this.waitingBubbleId = id;
    }

    @Override
    public void onSuccess(ResponseChat responseChat) {
        String chatText = responseChat.getChatText();
        EntityMaid maid = getMaid();

        if (maid != null && StringUtils.isNotBlank(chatText)) {
            try {
                if (waitingBubbleId >= 0) {
                    maid.getChatBubbleManager().addLLMChatText(chatText, waitingBubbleId);
                } else {
                    // 兜底：无等待气泡时直接创建新气泡
                    long fallbackId = maid.getChatBubbleManager().addThinkingText("...");
                    maid.getChatBubbleManager().addLLMChatText(chatText, fallbackId);
                }
            } catch (Exception e) {
                EnhancedLittleMaidAI.LOGGER.warn(
                        "EnhancedLittleMaidAI: Failed to display proactive chat bubble", e);
            }
        }

        if (EnhancedLittleMaidAI.DEBUG_LOG) {
            EnhancedLittleMaidAI.LOGGER.info(
                    "EnhancedLittleMaidAI: Proactive chat delivered for maid {}: {}",
                    maid != null ? maid.getUUID() : "null",
                    chatText != null ? chatText.substring(0, Math.min(60, chatText.length())) : "");
        }

        future.complete(chatText);
    }

    @Override
    public void onFailure(HttpRequest request, Throwable throwable, int errorCode) {
        EnhancedLittleMaidAI.LOGGER.warn(
                "EnhancedLittleMaidAI: Proactive chat failed for maid {}: {}",
                getMaid() != null ? getMaid().getUUID() : "null", errorCode);
        future.completeExceptionally(
                throwable != null ? throwable : new RuntimeException("Proactive chat failed: " + errorCode));
    }

    /**
     * 构建主动聊天的系统 prompt。
     * 包含环境信息 + MindPalace 记忆上下文。
     */
    public static String buildProactivePrompt(EntityMaid maid) {
        // 环境信息
        String biome = "未知";
        try {
            var biomeKey = maid.level().getBiome(maid.blockPosition()).unwrapKey();
            if (biomeKey.isPresent()) {
                String path = biomeKey.get().location().getPath();
                biome = path.replace('_', ' ');
            }
        } catch (Exception ignored) {}

        String timeOfDay = "";
        try {
            long time = maid.level().getDayTime() % 24000;
            if (time < 6000) timeOfDay = "清晨";
            else if (time < 12000) timeOfDay = "白天";
            else if (time < 13000) timeOfDay = "日落";
            else timeOfDay = "夜晚";
        } catch (Exception ignored) {}

        String weather = "未知";
        try {
            if (maid.level().isRaining()) {
                weather = maid.level().isThundering() ? "雷雨" : "下雨";
            } else {
                weather = "晴朗";
            }
        } catch (Exception ignored) {}

        String ownerName = "主人";
        try {
            LivingEntity owner = maid.getOwner();
            if (owner != null) ownerName = owner.getDisplayName().getString();
        } catch (Exception ignored) {}

        String maidName = "";
        try {
            maidName = maid.getDisplayName().getString();
        } catch (Exception ignored) {}

        // MindPalace 记忆上下文
        String memoryContext = "";
        try {
            MindPalace palace = MindPalace.get(maid.getUUID());
            if (palace != null && palace.size() > 0) {
                String mem = palace.buildMemoryContext("当前环境", maid.blockPosition());
                if (!mem.isEmpty()) {
                    memoryContext = "\n你记得以下关于当前环境的信息：\n" + mem;
                }
            }
        } catch (Exception ignored) {}

        return String.format("""
                [系统指令] 你现在要主动发起对话（不需要等待主人说话）。

                你是%s，%s的忠诚女仆。你目前在%s，%s天气，%s。
                %s
                请用1-2句简短自然的话主动和主人聊天。直接说话即可，不要加动作描写、括号注释或任何格式标记。

                可选话题：关心主人状态、评论环境或天气、分享你注意到的事情、询问是否需要帮助。
                注意：你是在主动发起对话，不要回应任何人的话。""",
                maidName, ownerName, biome, weather, timeOfDay, memoryContext);
    }
}
```

**关键设计**：
- `needAddTools = false`：主动聊天不需要工具调用
- `onSuccess()` 不调用 `super.onSuccess()`：避免父模组的完整聊天流程（TTL、历史存储等），仅显示气泡
- `addLLMChatText()` 由父模组 `ChatBubbleManager` 提供，自动显示气泡 + 发送系统消息给主人
- 覆写 `onFailure()`：错误仅记录日志，不抛异常

---

### 任务 5.3：修改 EntityMaidMixin（新增 tick 注入 + 清理逻辑）

**修改文件**：`mixin/EntityMaidMixin.java`

**增加**：
1. tick() TAIL 注入 —— 每 tick 检查主动聊天触发条件
2. remove() TAIL 注入 —— 清理 ProactiveChatManager 状态

```java
// 在 enhanced$cleanupMindPalace 之后新增：

@Inject(method = "tick", at = @At("TAIL"), remap = false)
private void enhanced$proactiveChatTick(CallbackInfo ci) {
    try {
        EntityMaid maid = (EntityMaid) (Object) this;
        if (!ProactiveChatManager.canTrigger(maid)) return;

        MaidAIChatManager chatManager = maid.getAiChatManager();
        if (chatManager == null) return;
        LLMSite site = chatManager.getLLMSite();
        if (site == null || !site.enabled()) return;
        LLMClient client = site.client();
        if (client == null) return;

        String systemPrompt = ProactiveChatCallback.buildProactivePrompt(maid);
        LLMMessage sysMsg = LLMMessage.systemChat(maid, systemPrompt);
        LLMMessage userMsg = LLMMessage.userChat(maid, "（主动发起对话）");
        List<LLMMessage> messages = List.of(sysMsg, userMsg);

        long waitingBubbleId = maid.getChatBubbleManager().addThinkingText("...");

        CompletableFuture<String> future = new CompletableFuture<>();
        ProactiveChatCallback callback = new ProactiveChatCallback(chatManager, messages, future);
        callback.setWaitingBubbleId(waitingBubbleId);

        client.chat(callback);
        ProactiveChatManager.markTriggered(maid.getUUID(), maid.level().getGameTime());

        if (EnhancedLittleMaidAI.DEBUG_LOG) {
            EnhancedLittleMaidAI.LOGGER.info(
                    "EnhancedLittleMaidAI: Proactive chat triggered for maid {} (#{})",
                    maid.getUUID(), ProactiveChatManager.getCount(maid.getUUID()));
        }
    } catch (Exception e) {
        EnhancedLittleMaidAI.LOGGER.warn(
                "EnhancedLittleMaidAI: Proactive chat trigger failed for maid {}",
                ((EntityMaid) (Object) this).getUUID(), e);
    }
}
```

**cleanup 修改**：在 `enhanced$cleanupMindPalace` 方法中追加
```java
ProactiveChatManager.reset(((EntityMaid) (Object) this).getUUID());
```

**新增 import**：
```java
import ProactiveChatManager, ProactiveChatCallback, LLMSite, LLMClient, LLMMessage, MaidAIChatManager
import CompletableFuture
```

---

### 任务 5.4：修改 LLMCallbackMixin（跳过 ProactiveChatCallback 记忆提取）

**修改文件**：`mixin/LLMCallbackMixin.java`

修改第 118 行附近的 instanceof 检查：

```java
// 原代码：
if ((Object) this instanceof MemoryExtractionCallback) return;

// 改为：
if ((Object) this instanceof MemoryExtractionCallback) return;
if ((Object) this instanceof ProactiveChatCallback) return;
```

**新增 import**：
```java
import com.github.lonelygeo.enhancedlittlemaidai.util.ProactiveChatCallback;
```

---

### 任务 5.5：编译验证

`./gradlew.bat build`

---

### 限制参数速查

| 参数 | 值 | 说明 |
|------|-----|------|
| COOLDOWN_TICKS | 12000 | 两次主动聊天最少间隔 10 分钟 |
| TRIGGER_CHANCE | 0.002/tick | 冷却后每 tick 0.2% 概率（预期 ~25s 触发） |
| MAX_CHATS | 8/会话 | 每次游戏会话最多 8 次 |
| MIN_DISTANCE | 10 格 | 主人须在 10 格内 |
| needAddTools | false | 主动聊天不调用工具（节省 token） |
| 记忆提取 | 禁用 | ProactiveChatCallback 不触发 LLMCallbackMixin |
| 历史存储 | 禁用 | 不写入聊天历史（不污染对话上下文） |

---

### 数据流总览

```
EntityMaid.tick() @TAIL
  └→ ProactiveChatManager.canTrigger(maid)
       ├─ 冷却检查 (12000 ticks)
       ├─ 会话上限 (8 次)
       ├─ 距离检查 (10 格)
       ├─ 随机概率 (0.2%/tick)
       └─ LLM 站点可用性
            │ (全部通过)
            ├→ buildProactivePrompt() → 构建 system prompt
            ├→ addThinkingText("...") → 显示思考气泡
            └→ LLMClient.chat(callback)
                 │
                 └→ ProactiveChatCallback.onSuccess()
                      └→ addLLMChatText(text, waitingId)
                           ├→ 显示聊天气泡
                           └→ 向主人发送系统消息 "<女仆名> 内容"
```

---


## ✅ 阶段四：记忆深度增强 + 工具链完善（已完成）

### 目标
提升记忆系统的实用价值：LLM 智能压缩、独立 debug、丰富死亡上下文、管理员命令、玩家主动记录。

---

### 任务 4.1：LLM 记忆压缩（MemoryCompressor LLM 版）

**现状**：`MemoryCompressor.compress()` 只是按评分排序后丢弃低分记忆。100 条满后新记忆进不来。

**目标**：调用 LLM 将 20 条旧记忆合并为 5 条摘要。释放 15 个槽位，所有原始事实保留。

#### 4.1.1 重写 MemoryCompressor

**修改文件**：`memory/MemoryCompressor.java`

```java
package com.github.lonelygeo.enhancedlittlemaidai.memory;

import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMClient;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

import java.util.*;

/**
 * LLM 版记忆压缩器。
 * 触发条件：store.size() >= COMPRESS_TRIGGER (80)
 * 策略：取最旧的 20 条记忆发给 LLM，合并为 5 条摘要，替换原记忆。
 */
public final class MemoryCompressor {
    private static final int COMPRESS_TRIGGER = 80;
    private static final int COMPRESS_BATCH_SIZE = 20;
    private static final int TARGET_SUMMARIES = 5;

    private MemoryCompressor() {}

    /**
     * 构建压缩 prompt。
     */
    public static String buildCompressPrompt(List<String> memoryTexts) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                You are a memory summarizer. Merge similar old memories into concise summaries.
                Output one line per summary in this exact format:
                [CATEGORY] content | importance:N

                Rules:
                - Combine memories of the same CATEGORY that share a topic
                - Keep ALL entity names, coordinates, and specific facts
                - Shorten descriptions, never lose facts
                - Output ONLY the summary lines, nothing else
                - Output at most %d summaries

                Memories to compress:
                """.formatted(TARGET_SUMMARIES));

        for (String text : memoryTexts) {
            sb.append(text).append("\n");
        }
        return sb.toString();
    }

    /**
     * 检查是否需要压缩，若需要则触发异步 LLM 压缩。
     */
    public static boolean needsCompression(MemoryStore store) {
        return store.size() >= COMPRESS_TRIGGER;
    }

    /**
     * 获取最旧的 N 条记忆的文本。
     */
    public static List<String> getOldestMemoryTexts(MemoryStore store) {
        List<MemoryItem> items = new ArrayList<>(store.toList());
        // 按 time 升序（最旧的在前）
        items.sort(Comparator.comparingLong(MemoryItem::gameTime));
        int count = Math.min(COMPRESS_BATCH_SIZE, items.size());
        List<String> texts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            MemoryItem m = items.get(i);
            texts.add(String.format("[%s] %s | importance:%d",
                    m.category().name(), m.content(), m.importance()));
        }
        return texts;
    }
}
```

#### 4.1.2 MindPalace 新增压缩触发

**修改文件**：`memory/MindPalace.java` — 新增方法：

```java
/** 异步触发 LLM 记忆压缩。由 LLMCallbackMixin 调用。 */
public void triggerCompression(EntityMaid maid, LLMClient llmClient) {
    List<String> oldTexts = MemoryCompressor.getOldestMemoryTexts(store);
    if (oldTexts.isEmpty()) return;

    String prompt = MemoryCompressor.buildCompressPrompt(oldTexts);
    LLMMessage sysMsg = LLMMessage.systemChat(maid, prompt);
    List<LLMMessage> msgs = List.of(sysMsg);

    CompletableFuture<List<MemoryItem>> future = new CompletableFuture<>();
    MemoryExtractionCallback cb = new MemoryExtractionCallback(
            null /* chatManager 在压缩场景不需要 */, msgs, future);
    llmClient.chat(cb);

    future.whenComplete((summaries, ex) -> {
        if (ex == null && summaries != null && !summaries.isEmpty()) {
            // 替换旧记忆：删除最旧的 COMPRESS_BATCH_SIZE 条，加入摘要
            applyCompressionResult(summaries);
        }
    });
}

private void applyCompressionResult(List<MemoryItem> summaries) {
    // MemoryStore 需要新增 removeOldest(int) 方法
    store.removeOldest(COMPRESS_BATCH_SIZE);
    for (MemoryItem item : summaries) {
        store.restore(item);
    }
}
```

#### 4.1.3 MemoryStore 新增 removeOldest

**修改文件**：`memory/MemoryStore.java` — 新增方法：

```java
/** 删除最旧的 N 条记忆（按 gameTime 排序）。 */
public void removeOldest(int count) {
    if (memories.size() <= count) {
        memories.clear();
        // 重建索引
        for (MemoryItem m : List.copyOf(memories)) {
            index.remove(m.id().toString());
        }
        memories.clear();
        return;
    }
    List<MemoryItem> sorted = new ArrayList<>(memories);
    sorted.sort(Comparator.comparingLong(MemoryItem::gameTime));
    for (int i = 0; i < count; i++) {
        MemoryItem removed = sorted.get(i);
        memories.remove(removed);
        index.remove(removed.id().toString());
    }
}
```

#### 4.1.4 LLMCallbackMixin 添加压缩触发

**修改文件**：`mixin/LLMCallbackMixin.java` — 在 `enhanced$extractMemoriesOnSuccess` 末尾新增：

```java
// 在 enhanced$extractMemoriesOnSuccess 方法末尾，enhanced$triggerAsyncMemoryExtraction 调用之后：

// 记忆压缩检测
if (palace.getStore().needsCompression()) {
    LLMSite site = chatManager.getLLMSite();
    if (site != null && site.enabled()) {
        LLMClient client = site.client();
        if (client != null) {
            palace.triggerCompression(maid, client);
            if (TouhouLittleMaid.DEBUG) {
                TouhouLittleMaid.LOGGER.info(
                        "EnhancedLittleMaidAI: Triggering memory compression for maid {}",
                        maid.getUUID());
            }
        }
    }
}
```

---

### 任务 4.2：独立 Debug 开关

**目标**：新增 `EnhancedLittleMaidAI.DEBUG_LOG` 字段，替换当前所有 `TouhouLittleMaid.DEBUG` 门控。

**改动文件**：

| 文件 | 改动 |
|---|---|
| `EnhancedLittleMaidAI.java` | 新增 `public static boolean DEBUG_LOG = false;` |
| 含 `TouhouLittleMaid.DEBUG` 的 8 个文件 | 替换 `TouhouLittleMaid.DEBUG` → `EnhancedLittleMaidAI.DEBUG_LOG`，同时移除 `import TouhouLittleMaid` |

**受影响文件清单**（全部仅需替换门控变量名，无需改动方法体）：

| # | 文件 | 替换数 |
|---|---|---|
| 1 | `mixin/LLMOpenAIClientMixin.java` | 3 处 |
| 2 | `mixin/LLMCallbackMixin.java` | 5 处 |
| 3 | `mixin/MaidAIChatDataMixin.java` | 2 处 |
| 4 | `mixin/MaidAIChatManagerMixin.java` | 1 处 |
| 5 | `mixin/EntityMaidMixin.java` | 0（不含） |
| 6 | `memory/MemoryExtractionCallback.java` | 2 处 |
| 7 | `memory/MemoryStore.java` | 1 处 |
| 8 | `context/BlockAwareContexts.java` | 3 处 |
| 9 | `context/MiningContextProvider.java` | 2 处 |
| 10 | `EnhancedLittleMaidExtension.java` | 1 处（`TouhouLittleMaid.LOGGER.info` → `EnhancedLittleMaidAI.LOGGER.info`，无需 DEBUG 门控） |

同时需要替换各文件中 `TouhouLittleMaid.LOGGER` → `EnhancedLittleMaidAI.LOGGER`（`MemoryExtractionCallback`、`MemoryStore`、`BlockAwareContexts`、`MiningContextProvider`、`EnhancedLittleMaidExtension` 共 5 个非 Mixin 类使用了自己的 LOGGER 或 TouhouLittleMaid.LOGGER）。

**设计说明**：不需要完整的 ModConfigSpec，一个 `public static boolean` 即可。在运行时通过 IDE 调试、JVM 参数、或反射修改。后续如有需求再加配置文件。

---

### 任务 4.3：死亡记忆增强

**目标**：死亡记忆包含更多上下文信息：手持物品、当前任务、杀死者名称、坐标。

**修改文件**：`mixin/EntityMaidMixin.java` — 扩展 `enhanced$onRemove` 中的 MemoryItem 构建：

**当前**：
```java
palace.addMemory(new MemoryItem(
    UUID.randomUUID(), MemoryCategory.EVENT,
    "被 " + cause + " 击杀",
    Optional.of(maid.blockPosition()),
    Optional.ofNullable(maid.level().dimension().location().toString()),
    maid.level().getGameTime(), 0, 5
));
```

**改为**：
```java
// 获取手持物品名称
String heldItem = "空手";
var mainHand = maid.getMainHandItem();
if (!mainHand.isEmpty()) heldItem = mainHand.getDescriptionId();

// 获取当前任务名称
String task = "无";
var currentTask = maid.getTask();
if (currentTask != null) task = currentTask.getUid().toString();

// 获取杀死者名称（部分 DamageSource 有 getEntity 或 getDirectEntity）
String killer = "未知";
try {
    var attacker = maid.getLastDamageSource();
    if (attacker != null) {
        var entity = attacker.getEntity();
        if (entity != null) killer = entity.getDisplayName().getString();
    }
} catch (Exception ignored) {}

String deathContent = String.format(
    "被 %s 击杀 手持%s 任务:%s",
    killer, heldItem, task);

palace.addMemory(new MemoryItem(
    UUID.randomUUID(), MemoryCategory.EVENT,
    deathContent,
    Optional.of(maid.blockPosition()),
    Optional.ofNullable(maid.level().dimension().location().toString()),
    maid.level().getGameTime(), 0, 5
));
```

---

### 任务 4.4：`/mindpalace` 调试命令

**目标**：管理员可查看/管理女仆记忆列表。

**新建文件**：`command/MindPalaceCommand.java`

```java
package com.github.lonelygeo.enhancedlittlemaidai.command;

import com.github.lonelygeo.enhancedlittlemaidai.memory.MemoryItem;
import com.github.lonelygeo.enhancedlittlemaidai.memory.MindPalace;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import org.apache.commons.lang3.StringUtils;

import java.util.Comparator;
import java.util.List;

public final class MindPalaceCommand {
    private MindPalaceCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("mindpalace")
            .requires(ctx -> ctx.hasPermission(2))
            .then(Commands.literal("list")
                .then(Commands.argument("maid", EntityArgument.entity())
                    .executes(ctx -> listMemories(ctx.getSource(), EntityArgument.getEntity(ctx, "maid")))))
            .then(Commands.literal("clear")
                .then(Commands.argument("maid", EntityArgument.entity())
                    .executes(ctx -> clearMemories(ctx.getSource(), EntityArgument.getEntity(ctx, "maid")))))
            .then(Commands.literal("stats")
                .then(Commands.argument("maid", EntityArgument.entity())
                    .executes(ctx -> showStats(ctx.getSource(), EntityArgument.getEntity(ctx, "maid")))));
    }

    private static int listMemories(CommandSourceStack src, net.minecraft.world.entity.Entity entity) {
        if (!(entity instanceof EntityMaid maid)) {
            src.sendFailure(Component.literal("Target is not a maid"));
            return 0;
        }
        MindPalace palace = MindPalace.get(maid.getUUID());
        if (palace == null || palace.size() == 0) {
            src.sendSuccess(() -> Component.literal("No memories for " + maid.getDisplayName().getString()), false);
            return 1;
        }
        List<MemoryItem> items = new java.util.ArrayList<>(palace.getStore().toList());
        items.sort(Comparator.comparingInt(MemoryItem::importance).reversed());
        for (MemoryItem m : items) {
            src.sendSuccess(() -> Component.literal(
                String.format("[%s|imp=%d] %s", m.category().name(), m.importance(), m.content())), false);
        }
        src.sendSuccess(() -> Component.literal("--- Total: " + items.size() + " memories ---"), false);
        return 1;
    }

    private static int clearMemories(CommandSourceStack src, net.minecraft.world.entity.Entity entity) {
        if (!(entity instanceof EntityMaid maid)) {
            src.sendFailure(Component.literal("Target is not a maid"));
            return 0;
        }
        MindPalace.remove(maid.getUUID());
        src.sendSuccess(() -> Component.literal("Cleared memories for " + maid.getDisplayName().getString()), false);
        return 1;
    }

    private static int showStats(CommandSourceStack src, net.minecraft.world.entity.Entity entity) {
        if (!(entity instanceof EntityMaid maid)) {
            src.sendFailure(Component.literal("Target is not a maid"));
            return 0;
        }
        MindPalace palace = MindPalace.get(maid.getUUID());
        if (palace == null || palace.size() == 0) {
            src.sendSuccess(() -> Component.literal("No memories for " + maid.getDisplayName().getString()), false);
            return 1;
        }
        var store = palace.getStore();
        java.util.Map<String, Integer> categoryCounts = new java.util.LinkedHashMap<>();
        for (MemoryItem m : store.toList()) {
            categoryCounts.merge(m.category().name(), 1, Integer::sum);
        }
        src.sendSuccess(() -> Component.literal(
            String.format("Total: %d memories | %s", store.size(), categoryCounts)), false);
        return 1;
    }
}
```

**修改文件**：`EnhancedLittleMaidAI.java` — 构造函数中注册命令：

```java
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;

public EnhancedLittleMaidAI(IEventBus modEventBus) {
    LOGGER.info("Enhanced Little Maid AI addon loaded.");
    modEventBus.addListener(this::registerCommands);
}

@SubscribeEvent
private void registerCommands(RegisterCommandsEvent event) {
    event.getDispatcher().register(MindPalaceCommand.register());
}
```

---

### 任务 4.5：立即写入记忆（关键词触发）

**目标**：用户在对话中说"记住这个"/"别忘了"/"记下来"时，跳过 5 轮等待，立即将当前 LLM 回复创建为一条记忆。

**改动文件**：`mixin/LLMCallbackMixin.java` — 在 `enhanced$extractMemoriesOnSuccess` 中，`incrementRoundCounter` 之后新增：

```java
// 关键词触发立即记忆
String userMessage = "";
// messages 列表的最后一条 USER 消息就是当前用户输入
for (int i = messages.size() - 1; i >= 0; i--) {
    if (messages.get(i).role() == Role.USER) {
        userMessage = messages.get(i).message();
        break;
    }
}
if (StringUtils.containsAny(userMessage, "记住", "别忘了", "记下来", "remember", "don't forget")) {
    String replyText = responseChat.getChatText();
    if (StringUtils.isNotBlank(replyText) && replyText.length() <= 80) {
        MemoryItem quickMemory = new MemoryItem(
                UUID.randomUUID(),
                MemoryCategory.KNOWLEDGE,
                replyText.substring(0, Math.min(80, replyText.length())),
                Optional.of(maid.blockPosition()),
                Optional.ofNullable(maid.level().dimension().location().toString()),
                gameTime,
                0,
                4
        );
        palace.addMemory(quickMemory);
        if (TouhouLittleMaid.DEBUG) {
            TouhouLittleMaid.LOGGER.info(
                    "EnhancedLittleMaidAI: Quick memory saved for maid {} (keyword trigger)",
                    maid.getUUID());
        }
    }
}
```

**新增 import**：`MemoryCategory`, `Optional`, `UUID`

---

### 任务 4.6：编译 + 验证

1. `gradlew build` — 确保全部编译通过
2. `runClient` — 需要你手动确认
3. 验证项：
   - `/mindpalace list` 查看记忆列表
   - 对话中说"记住 XXX" → 检查 palace size 增加
   - 对话 5+ 轮 → 检查日志出现 `memory extraction` 和 `memory compression`
   - 杀女仆 → 复活 → 检查日志出现增强后的死亡记忆

---


