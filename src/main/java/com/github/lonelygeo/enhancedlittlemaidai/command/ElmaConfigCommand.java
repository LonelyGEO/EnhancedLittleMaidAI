package com.github.lonelygeo.enhancedlittlemaidai.command;

import com.github.lonelygeo.enhancedlittlemaidai.config.EnhancedConfig;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class ElmaConfigCommand {
    private ElmaConfigCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("config")
                .executes(ctx -> showConfig(ctx.getSource()));
    }

    private static int showConfig(CommandSourceStack src) {
        String cfg = "ELMAI 配置:\n" +
                "  主动聊天: " + yn(EnhancedConfig.PROACTIVE_CHAT_ENABLED.get()) +
                " | 冷却 " + ts(EnhancedConfig.COOLDOWN_TICKS.get()) +
                " | 概率 " + EnhancedConfig.TRIGGER_CHANCE_PER_TICK.get() + "/tick" +
                " | 每日 " + EnhancedConfig.MAX_CHATS_PER_DAY.get() + "次" +
                " | 距离 " + EnhancedConfig.MIN_PLAYER_DISTANCE.get() + "格\n" +
                "  主动聊天提示词: " + EnhancedConfig.PROACTIVE_PROMPT_MODE.get() + "\n" +
                "  环境事件: 每日 " + EnhancedConfig.EVENT_MAX_PER_DAY.get() + "次" +
                " | 冷却 " + ts(EnhancedConfig.EVENT_COOLDOWN_TICKS.get()) +
                " | 范围冷却 " + EnhancedConfig.EVENT_RANGE_BLOCKS.get() + "格\n" +
                "  女仆社交: " + yn(EnhancedConfig.INTER_MAID_ENABLED.get()) +
                " | " + EnhancedConfig.INTER_MAID_DECISION_MODE.get() + "决策" +
                " | 扫描间隔 " + ts(EnhancedConfig.INTER_MAID_SCAN_INTERVAL.get()) +
                " | 每日 " + EnhancedConfig.INTER_MAID_MAX_PER_DAY.get() +
                "/全局 " + EnhancedConfig.INTER_MAID_MAX_GLOBAL_PER_DAY.get() + "次" +
                " | 冷却 " + ts(EnhancedConfig.INTER_MAID_COOLDOWN_TICKS.get()) + "\n" +
                "  女仆社交: 空闲距离 " + EnhancedConfig.INTER_MAID_IDLE_DISTANCE.get() +
                "格/工作距离 " + EnhancedConfig.INTER_MAID_WORKING_DISTANCE.get() +
                "格 | 玩家范围 " + EnhancedConfig.INTER_MAID_PLAYER_DISTANCE.get() +
                "格 | 最大 " + EnhancedConfig.INTER_MAID_MAX_GROUP_SIZE.get() + "人群聊" +
                " | 轮间 " + EnhancedConfig.INTER_MAID_ROUND_DELAY_MIN.get() +
                "-" + EnhancedConfig.INTER_MAID_ROUND_DELAY_MAX.get() + "s\n" +
                "  女仆社交: 跨主人 " + yn(EnhancedConfig.INTER_MAID_CROSS_OWNER.get()) + "\n" +
                "  记忆: 上限 " + EnhancedConfig.MAX_MEMORIES.get() + "条" +
                " | 压缩阈值 " + EnhancedConfig.COMPRESS_TRIGGER.get() +
                " | 去重 " + EnhancedConfig.DEDUP_SCORE_THRESHOLD.get() +
                " | 压缩批次 " + EnhancedConfig.COMPRESS_BATCH_SIZE.get() +
                " | 摘要上限 " + EnhancedConfig.TARGET_SUMMARIES.get() + "\n" +
                "  记忆: 新鲜窗口 " + (EnhancedConfig.MAX_AGE_TICKS.get() / 72000) + "h" +
                " | 空间召回 " + EnhancedConfig.SPATIAL_RECALL_RADIUS.get() + "格" +
                " | 语义检索 " + EnhancedConfig.SEMANTIC_RETRIEVE_TOP_K.get() + "条\n" +
                "  上下文: BFS深度 " + EnhancedConfig.BFS_MAX_DEPTH.get() +
                " | 实体半径 " + EnhancedConfig.ENTITY_RADIUS.get() +
                " | 最大实体 " + EnhancedConfig.MAX_ENTITIES.get() + "\n" +
                "  仓储: 储物记忆 " + yn(EnhancedConfig.ENABLE_STORAGE_MEMORY.get()) +
                " | 位置数 " + EnhancedConfig.STORAGE_MAX_POSITIONS.get() +
                " | 物品/位 " + EnhancedConfig.STORAGE_MAX_ITEMS_PER_POS.get() +
                " | 摘要 " + EnhancedConfig.STORAGE_MAX_ITEMS_SUMMARY.get() + "\n" +
                "  调试: 日志 " + yn(EnhancedConfig.DEBUG_LOG.get()) +
                " | 采矿对话 " + yn(EnhancedConfig.ENABLE_MINING_CHAT.get()) +
                " | 问候 " + yn(EnhancedConfig.ENABLE_MAID_GREETING.get()) +
                " | TTS禁用 " + yn(EnhancedConfig.OVERRIDE_TTS_DISABLED.get()) +
                " | /maid距离 " + EnhancedConfig.CHAT_MAID_DISTANCE.get() + "格";

        src.sendSuccess(() -> Component.literal(cfg), false);
        return 1;
    }

    private static String yn(boolean b) {
        return b ? "开启" : "关闭";
    }

    private static String ts(int ticks) {
        return ticks / 20 + "s";
    }
}
