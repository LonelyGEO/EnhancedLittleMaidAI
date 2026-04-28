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
    public static ModConfigSpec.IntValue COOLDOWN_TICKS;
    public static ModConfigSpec.DoubleValue TRIGGER_CHANCE_PER_TICK;
    public static ModConfigSpec.IntValue MAX_CHATS_PER_DAY;
    public static ModConfigSpec.DoubleValue MIN_PLAYER_DISTANCE;
    public static ModConfigSpec.IntValue EVENT_COOLDOWN_TICKS;
    public static ModConfigSpec.IntValue EVENT_MAX_PER_DAY;

    // === [context] 上下文感知 ===
    public static ModConfigSpec.IntValue BFS_MAX_DEPTH;
    public static ModConfigSpec.IntValue ENTITY_RADIUS;
    public static ModConfigSpec.IntValue MAX_ENTITIES;

    // === [debug] 调试 ===
    public static ModConfigSpec.BooleanValue DEBUG_LOG;
    public static ModConfigSpec.BooleanValue ENABLE_MINING_CHAT;

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

        // ========== debug ==========
        builder.push("debug");

        DEBUG_LOG = builder
                .comment("启用调试日志（输出详细运行信息到日志文件）")
                .define("debugLog", false);

        ENABLE_MINING_CHAT = builder
                .comment("启用 LLM 接管采矿消息（替代 MLM 硬编码文本，需 MiningLittleMaid 模组）")
                .define("enableMiningChat", true);

        builder.pop();

        SPEC = builder.build();
    }

    /** 便捷方法：调试日志开关 */
    public static boolean debugLog() {
        return DEBUG_LOG.get();
    }
}
