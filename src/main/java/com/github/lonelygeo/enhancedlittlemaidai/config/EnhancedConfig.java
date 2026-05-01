package com.github.lonelygeo.enhancedlittlemaidai.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class EnhancedConfig {
    // === Spec 入口 ===
    public static final ModConfigSpec SPEC;

    // === [memory] 记忆系统 ===
    public static ModConfigSpec.IntValue MAX_MEMORIES;
    public static ModConfigSpec.IntValue COMPRESS_TRIGGER;
    public static ModConfigSpec.DoubleValue DEDUP_SCORE_THRESHOLD;
    public static ModConfigSpec.IntValue MAX_AGE_TICKS;
    public static ModConfigSpec.IntValue COMPRESS_BATCH_SIZE;
    public static ModConfigSpec.IntValue TARGET_SUMMARIES;
    public static ModConfigSpec.IntValue SPATIAL_RECALL_RADIUS;
    public static ModConfigSpec.IntValue SEMANTIC_RETRIEVE_TOP_K;

    // === [proactive_chat] 主动聊天 ===
    public static ModConfigSpec.BooleanValue PROACTIVE_CHAT_ENABLED;
    public static ModConfigSpec.IntValue COOLDOWN_TICKS;
    public static ModConfigSpec.DoubleValue TRIGGER_CHANCE_PER_TICK;
    public static ModConfigSpec.IntValue MAX_CHATS_PER_DAY;
    public static ModConfigSpec.DoubleValue MIN_PLAYER_DISTANCE;
    public static ModConfigSpec.IntValue EVENT_COOLDOWN_TICKS;
    public static ModConfigSpec.IntValue EVENT_MAX_PER_DAY;
    public static ModConfigSpec.IntValue EVENT_RANGE_BLOCKS;
    public static ModConfigSpec.ConfigValue<String> PROACTIVE_PROMPT_MODE;

    // === [context] 上下文感知 ===
    public static ModConfigSpec.IntValue BFS_MAX_DEPTH;
    public static ModConfigSpec.IntValue ENTITY_RADIUS;
    public static ModConfigSpec.IntValue MAX_ENTITIES;

    // === [inter_maid] 女仆社交 ===
    public static ModConfigSpec.BooleanValue INTER_MAID_ENABLED;
    public static ModConfigSpec.ConfigValue<String> INTER_MAID_PROMPT_MODE;
    public static ModConfigSpec.IntValue INTER_MAID_MIN_ROUNDS;
    public static ModConfigSpec.IntValue INTER_MAID_MAX_ROUNDS;
    public static ModConfigSpec.DoubleValue INTER_MAID_WORKING_DISTANCE;
    public static ModConfigSpec.DoubleValue INTER_MAID_IDLE_DISTANCE;
    public static ModConfigSpec.DoubleValue INTER_MAID_WORKING_CHANCE;
    public static ModConfigSpec.DoubleValue INTER_MAID_IDLE_CHANCE;
    public static ModConfigSpec.IntValue INTER_MAID_SCAN_INTERVAL;
    public static ModConfigSpec.DoubleValue INTER_MAID_PLAYER_DISTANCE;
    public static ModConfigSpec.IntValue INTER_MAID_MAX_PER_DAY;
    public static ModConfigSpec.IntValue INTER_MAID_MAX_GLOBAL_PER_DAY;
    public static ModConfigSpec.IntValue INTER_MAID_COOLDOWN_TICKS;
    public static ModConfigSpec.ConfigValue<String> INTER_MAID_DECISION_MODE;
    public static ModConfigSpec.BooleanValue INTER_MAID_CROSS_OWNER;
    public static ModConfigSpec.IntValue INTER_MAID_MAX_GROUP_SIZE;
    public static ModConfigSpec.IntValue INTER_MAID_ROUND_DELAY_MIN;
    public static ModConfigSpec.IntValue INTER_MAID_ROUND_DELAY_MAX;
    public static ModConfigSpec.DoubleValue SOCIAL_MEMORY_INJECT_CHANCE;
    public static ModConfigSpec.IntValue SOCIAL_MEMORY_TOP_K;
    public static ModConfigSpec.IntValue SOCIAL_STORE_MAX_SIZE;
    public static ModConfigSpec.IntValue SOCIAL_MEMORY_COMPRESS_TRIGGER;

    // === [storage_context] 存储感知 ===
    public static ModConfigSpec.BooleanValue ENABLE_STORAGE_MEMORY;
    public static ModConfigSpec.IntValue STORAGE_MAX_POSITIONS;
    public static ModConfigSpec.IntValue STORAGE_MAX_ITEMS_PER_POS;
    public static ModConfigSpec.IntValue STORAGE_MAX_ITEMS_SUMMARY;

    // === [debug] 调试 ===
    public static ModConfigSpec.BooleanValue DEBUG_LOG;
    public static ModConfigSpec.BooleanValue ENABLE_MINING_CHAT;
    public static ModConfigSpec.BooleanValue ENABLE_MAID_GREETING;
    public static ModConfigSpec.BooleanValue OVERRIDE_TTS_DISABLED;
    public static ModConfigSpec.BooleanValue SUPPRESS_PARENT_JSON_DUMP;
    public static ModConfigSpec.BooleanValue STRIP_REASONING_CONTENT;
    public static ModConfigSpec.DoubleValue CHAT_MAID_DISTANCE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Enhanced Little Maid AI 配置");

        // ========== memory ==========
        builder.push("memory");

        MAX_MEMORIES = builder
                .comment("每只女仆可存储的最大记忆数量")
                .defineInRange("maxMemories", 100, 20, 500);

        COMPRESS_TRIGGER = builder
                .comment("触发 LLM 记忆压缩的记忆数量阈值")
                .defineInRange("compressTrigger", 80, 20, 500);

        DEDUP_SCORE_THRESHOLD = builder
                .comment("记忆去重相似度阈值（0.0~1.0，越高越严格）")
                .defineInRange("dedupScoreThreshold", 0.85, 0.5, 1.0);

        MAX_AGE_TICKS = builder
                .comment("记忆最大年龄（tick），影响淘汰评分中的新鲜度因子")
                .defineInRange("maxAgeTicks", 720000, 100000, Integer.MAX_VALUE);

        COMPRESS_BATCH_SIZE = builder
                .comment("每次压缩取最旧记忆的条数")
                .defineInRange("compressBatchSize", 20, 5, 100);

        TARGET_SUMMARIES = builder
                .comment("压缩后生成的摘要条数上限")
                .defineInRange("targetSummaries", 5, 1, 30);

        SPATIAL_RECALL_RADIUS = builder
                .comment("空间记忆召回半径（格）")
                .defineInRange("spatialRecallRadius", 16, 1, 64);

        SEMANTIC_RETRIEVE_TOP_K = builder
                .comment("BM25 语义检索返回的记忆数量")
                .defineInRange("semanticRetrieveTopK", 3, 1, 10);

        builder.pop();

        // ========== proactive_chat ==========
        builder.push("proactive_chat");

        PROACTIVE_CHAT_ENABLED = builder
                .comment("是否启用女仆主动聊天")
                .define("enabled", true);

        COOLDOWN_TICKS = builder
                .comment("两次主动聊天之间的冷却时间（tick）")
                .defineInRange("cooldownTicks", 12000, 600, 72000);

        TRIGGER_CHANCE_PER_TICK = builder
                .comment("冷却结束后每次 tick 触发主动聊天的概率")
                .defineInRange("triggerChancePerTick", 0.002, 0.0001, 1.0);

        MAX_CHATS_PER_DAY = builder
                .comment("每天凌晨时主动聊天的最大触发次数（日出清零）")
                .defineInRange("maxChatsPerDay", 8, 1, 100);

        MIN_PLAYER_DISTANCE = builder
                .comment("主人距离女仆多少格以内才可能触发主动聊天")
                .defineInRange("minPlayerDistance", 10.0, 1.0, 64.0);

        EVENT_COOLDOWN_TICKS = builder
                .comment("环境事件主动聊天的冷却时间（tick）")
                .defineInRange("eventCooldownTicks", 6000, 1200, 72000);

        EVENT_MAX_PER_DAY = builder
                .comment("每天凌晨时环境事件主动聊天的最大触发次数（日出清零）")
                .defineInRange("eventMaxPerDay", 5, 1, 30);

        EVENT_RANGE_BLOCKS = builder
                .comment("环境事件范围冷却半径（格）。范围内有女仆刚触发过事件则其他女仆不重复触发，防止同一场雨/日出多女仆同时说话")
                .defineInRange("eventRangeBlocks", 32, 16, 128);

        PROACTIVE_PROMPT_MODE = builder
                .comment("主动聊天角色设定长度: FULL(完整) SUMMARY(前200字) MINIMAL(仅名字)")
                .define("proactivePromptMode", "FULL");

        builder.pop();

        // ========== context ==========
        builder.push("context");

        BFS_MAX_DEPTH = builder
                .comment("BFS 方块采样深度")
                .defineInRange("bfsMaxDepth", 5, 1, 10);

        ENTITY_RADIUS = builder
                .comment("附近实体扫描半径（格）")
                .defineInRange("entityRadius", 16, 4, 64);

        MAX_ENTITIES = builder
                .comment("附近实体最大返回数量")
                .defineInRange("maxEntities", 30, 5, 100);

        builder.pop();

        // ========== inter_maid ==========
        builder.push("inter_maid");
        builder.comment("女仆社交配置（多女仆协调对话 + 社交记忆）");

        INTER_MAID_ENABLED = builder
                .comment("是否启用多女仆协调对话")
                .define("enabled", true);

        INTER_MAID_PROMPT_MODE = builder
                .comment("角色设定长度模式: FULL(完整) SUMMARY(摘要) MINIMAL(最小)")
                .define("promptMode", "FULL");

        INTER_MAID_MIN_ROUNDS = builder
                .comment("最少对话轮数（参与女仆每多1人+1轮）")
                .defineInRange("minRounds", 2, 2, 4);

        INTER_MAID_MAX_ROUNDS = builder
                .comment("最多对话轮数")
                .defineInRange("maxRounds", 4, 2, 8);

        INTER_MAID_WORKING_DISTANCE = builder
                .comment("工作中的女仆触发对话的距离（格）")
                .defineInRange("workingDistance", 5.0, 1.0, 32.0);

        INTER_MAID_IDLE_DISTANCE = builder
                .comment("空闲中的女仆触发对话的距离（格）")
                .defineInRange("idleDistance", 16.0, 1.0, 64.0);

        INTER_MAID_WORKING_CHANCE = builder
                .comment("工作中女仆每次扫描触发的概率（配合 scanInterval 使用）")
                .defineInRange("workingChance", 0.10, 0.0001, 1.0);

        INTER_MAID_IDLE_CHANCE = builder
                .comment("空闲中女仆每次扫描触发的概率（配合 scanInterval 使用）")
                .defineInRange("idleChance", 0.20, 0.0001, 1.0);

        INTER_MAID_SCAN_INTERVAL = builder
                .comment("女仆扫描附近同伴的间隔（tick），120=6秒")
                .defineInRange("scanInterval", 120, 10, 200);

        INTER_MAID_PLAYER_DISTANCE = builder
                .comment("感知玩家范围，玩家在此范围内才能触发对话")
                .defineInRange("playerDistance", 18.0, 1.0, 64.0);

        INTER_MAID_MAX_PER_DAY = builder
                .comment("每女仆每日对话上限（日出清零）")
                .defineInRange("maxPerDay", 3, 1, 30);

        INTER_MAID_MAX_GLOBAL_PER_DAY = builder
                .comment("全局每日对话上限（日出清零）")
                .defineInRange("maxGlobalPerDay", 15, 1, 50);

        INTER_MAID_COOLDOWN_TICKS = builder
                .comment("同对女仆对话冷却时间（tick）")
                .defineInRange("cooldownTicks", 1200, 600, 72000);

        INTER_MAID_DECISION_MODE = builder
                .comment("B接受提案的决策方式: LLM(基于社交记忆AI判断) WEIGHT(纯概率权重)")
                .define("decisionMode", "WEIGHT");

        INTER_MAID_CROSS_OWNER = builder
                .comment("是否允许不同主人的女仆之间也触发对话")
                .define("crossOwner", true);

        INTER_MAID_MAX_GROUP_SIZE = builder
                .comment("一次对话最多几个女仆参与")
                .defineInRange("maxGroupSize", 3, 2, 8);

        INTER_MAID_ROUND_DELAY_MIN = builder
                .comment("女仆社交每轮对话间隔最小秒数")
                .defineInRange("roundDelayMin", 3, 1, 10);

        INTER_MAID_ROUND_DELAY_MAX = builder
                .comment("女仆社交每轮对话间隔最大秒数")
                .defineInRange("roundDelayMax", 5, 1, 10);

        SOCIAL_MEMORY_INJECT_CHANCE = builder
                .comment("主动聊天时注入社交记忆的概率")
                .defineInRange("socialMemoryInjectChance", 0.3, 0.0, 1.0);

        SOCIAL_MEMORY_TOP_K = builder
                .comment("每次注入社交记忆的最大条数")
                .defineInRange("socialMemoryTopK", 2, 1, 5);

        SOCIAL_STORE_MAX_SIZE = builder
                .comment("社交记忆存储上限")
                .defineInRange("socialStoreMaxSize", 50, 10, 200);

        SOCIAL_MEMORY_COMPRESS_TRIGGER = builder
                .comment("触发社交记忆压缩的阈值")
                .defineInRange("socialMemoryCompressTrigger", 40, 10, 200);

        builder.pop();

        // ========== storage_context ==========
        builder.push("storage_context");
        builder.comment("仓储感知配置（需 MaidStorageManager 模组，无 MSM 时仅影响回退 BFS 扫描）");

        ENABLE_STORAGE_MEMORY = builder
                .comment("是否将储物操作自动写入 MindPalace 记忆")
                .define("enableStorageMemory", true);

        STORAGE_MAX_POSITIONS = builder
                .comment("附近存储上下文最多显示的位置数")
                .defineInRange("maxStoragePositions", 10, 3, 30);

        STORAGE_MAX_ITEMS_PER_POS = builder
                .comment("每个存储位置最多显示的物品种类数")
                .defineInRange("maxItemsPerStorage", 8, 3, 20);

        STORAGE_MAX_ITEMS_SUMMARY = builder
                .comment("库存摘要最多显示的物品种类数")
                .defineInRange("maxItemsSummary", 15, 5, 50);

        builder.pop();

        // ========== debug ==========
        builder.push("debug");

        DEBUG_LOG = builder
                .comment("启用调试日志（输出详细运行信息到日志文件）")
                .define("debugLog", false);

        ENABLE_MINING_CHAT = builder
                .comment("启用 LLM 接管采矿消息（替代 MLM 硬编码文本，需 MiningLittleMaid 模组）")
                .define("enableMiningChat", true);

        ENABLE_MAID_GREETING = builder
                .comment("女仆放置时如果LLM启用则自动生成角色设定并见面问候")
                .define("enableMaidGreeting", true);

        OVERRIDE_TTS_DISABLED = builder
                .comment("全局禁用女仆 TTS 语音输出（即使父模组配置了站点也不播放）")
                .define("overrideTTSDisabled", false);

        SUPPRESS_PARENT_JSON_DUMP = builder
                .comment("抑制父模组 TouhouLittleMaid 的 LLM 请求/响应 JSON dump 日志")
                .define("suppressParentJsonDump", true);

        STRIP_REASONING_CONTENT = builder
                .comment("NBT 保存时是否剥离 assistant 消息的 reasoningContent（默认 false=保留）。设为 true 可在 NBT 过大时瘦身，但 DeepSeek thinking 模式会因缺失 reasoning_content 报 400 错误")
                .define("stripReasoningContent", false);

        CHAT_MAID_DISTANCE = builder
                .comment("/maid 命令匹配女仆的距离范围（格）")
                .defineInRange("chatMaidDistance", 16.0, 1.0, 64.0);

        builder.pop();

        SPEC = builder.build();
    }

    /** 便捷方法：调试日志开关 */
    public static boolean debugLog() {
        return DEBUG_LOG.get();
    }
}
