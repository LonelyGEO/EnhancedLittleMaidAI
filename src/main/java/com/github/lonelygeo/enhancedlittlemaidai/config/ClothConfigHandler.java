package com.github.lonelygeo.enhancedlittlemaidai.config;

import com.github.lonelygeo.enhancedlittlemaidai.compat.MiningCompat;
import com.github.lonelygeo.enhancedlittlemaidai.compat.StorageCompat;
import com.github.tartaricacid.touhoulittlemaid.api.event.client.AddClothConfigEvent;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Cloth Config 实际事件处理器。
 * 仅在 cloth_config 已加载时由 ClothConfigIntegration 通过反射实例化。
 * 直接引用 Cloth Config API 类，避免主类 ClothConfigIntegration 因缺少 Cloth Config 类而加载失败。
 * 仅客户端加载，纯服务端跳过。
 */
@OnlyIn(Dist.CLIENT)
public final class ClothConfigHandler {

    private ClothConfigHandler() {
    }

    /**
     * 注册到 NeoForge 事件总线。由 ClothConfigIntegration 反射调用。
     */
    public static void register() {
        NeoForge.EVENT_BUS.register(ClothConfigHandler.class);
    }

    @SubscribeEvent
    public static void onAddClothConfig(AddClothConfigEvent event) {
        ConfigBuilder builder = event.getRoot();
        ConfigEntryBuilder entryBuilder = event.getEntryBuilder();

        Component title = Component.translatable(
                "config.enhancedlittlemaidai.title", "ELMAI");
        ConfigCategory category = builder.getOrCreateCategory(title);

        // ========== 采矿对话（父分类根层级独立条目，置顶，仅 MLM 加载时显示） ==========
        if (MiningCompat.isLoaded()) {
            category.addEntry(entryBuilder
                    .startBooleanToggle(
                            Component.translatable("config.enhancedlittlemaidai.mining.enableMiningChat"),
                            EnhancedConfig.ENABLE_MINING_CHAT.get())
                    .setDefaultValue(true)
                    .setTooltip(Component.translatable(
                            "config.enhancedlittlemaidai.mining.enableMiningChat.tooltip"))
                    .setSaveConsumer(EnhancedConfig.ENABLE_MINING_CHAT::set)
                    .build());
        }

        // ========== 上下文感知 ==========
        SubCategoryBuilder contextSub = entryBuilder.startSubCategory(
                Component.translatable("config.enhancedlittlemaidai.sub.context"));
        contextSub.setExpanded(true);

        contextSub.add(entryBuilder
                .startIntSlider(
                        Component.translatable("config.enhancedlittlemaidai.context.bfsDepth"),
                        EnhancedConfig.BFS_MAX_DEPTH.get(), 1, 10)
                .setDefaultValue(5)
                .setTooltip(Component.translatable(
                        "config.enhancedlittlemaidai.context.bfsDepth.tooltip"))
                .setSaveConsumer(EnhancedConfig.BFS_MAX_DEPTH::set)
                .build());

        contextSub.add(entryBuilder
                .startIntSlider(
                        Component.translatable("config.enhancedlittlemaidai.context.entityRadius"),
                        EnhancedConfig.ENTITY_RADIUS.get(), 4, 64)
                .setDefaultValue(16)
                .setTooltip(Component.translatable(
                        "config.enhancedlittlemaidai.context.entityRadius.tooltip"))
                .setSaveConsumer(EnhancedConfig.ENTITY_RADIUS::set)
                .build());

        contextSub.add(entryBuilder
                .startIntSlider(
                        Component.translatable("config.enhancedlittlemaidai.context.maxEntities"),
                        EnhancedConfig.MAX_ENTITIES.get(), 5, 100)
                .setDefaultValue(30)
                .setTooltip(Component.translatable(
                        "config.enhancedlittlemaidai.context.maxEntities.tooltip"))
                .setSaveConsumer(EnhancedConfig.MAX_ENTITIES::set)
                .build());

        category.addEntry(contextSub.build());

        // ========== 记忆系统 ==========
        SubCategoryBuilder memorySub = entryBuilder.startSubCategory(
                Component.translatable("config.enhancedlittlemaidai.sub.memory"));
        memorySub.setExpanded(true);

        memorySub.add(entryBuilder
                .startIntSlider(
                        Component.translatable("config.enhancedlittlemaidai.memory.maxMemories"),
                        EnhancedConfig.MAX_MEMORIES.get(), 20, 500)
                .setDefaultValue(100)
                .setTooltip(Component.translatable(
                        "config.enhancedlittlemaidai.memory.maxMemories.tooltip"))
                .setSaveConsumer(EnhancedConfig.MAX_MEMORIES::set)
                .build());

        memorySub.add(entryBuilder
                .startIntSlider(
                        Component.translatable("config.enhancedlittlemaidai.memory.compressTrigger"),
                        EnhancedConfig.COMPRESS_TRIGGER.get(), 20, 500)
                .setDefaultValue(80)
                .setTooltip(Component.translatable(
                        "config.enhancedlittlemaidai.memory.compressTrigger.tooltip"))
                .setSaveConsumer(EnhancedConfig.COMPRESS_TRIGGER::set)
                .build());

        memorySub.add(entryBuilder
                .startDoubleField(
                        Component.translatable("config.enhancedlittlemaidai.memory.dedupThreshold"),
                        EnhancedConfig.DEDUP_SCORE_THRESHOLD.get())
                .setDefaultValue(0.85)
                .setMin(0.5).setMax(1.0)
                .setTooltip(Component.translatable(
                        "config.enhancedlittlemaidai.memory.dedupThreshold.tooltip"))
                .setSaveConsumer(EnhancedConfig.DEDUP_SCORE_THRESHOLD::set)
                .build());

        memorySub.add(entryBuilder
                .startIntSlider(
                        Component.translatable("config.enhancedlittlemaidai.memory.compressBatchSize"),
                        EnhancedConfig.COMPRESS_BATCH_SIZE.get(), 5, 100)
                .setDefaultValue(20)
                .setTooltip(Component.translatable(
                        "config.enhancedlittlemaidai.memory.compressBatchSize.tooltip"))
                .setSaveConsumer(EnhancedConfig.COMPRESS_BATCH_SIZE::set)
                .build());

        memorySub.add(entryBuilder
                .startIntSlider(
                        Component.translatable("config.enhancedlittlemaidai.memory.targetSummaries"),
                        EnhancedConfig.TARGET_SUMMARIES.get(), 1, 30)
                .setDefaultValue(5)
                .setTooltip(Component.translatable(
                        "config.enhancedlittlemaidai.memory.targetSummaries.tooltip"))
                .setSaveConsumer(EnhancedConfig.TARGET_SUMMARIES::set)
                .build());

        memorySub.add(entryBuilder
                .startIntSlider(
                        Component.translatable("config.enhancedlittlemaidai.memory.spatialRecallRadius"),
                        EnhancedConfig.SPATIAL_RECALL_RADIUS.get(), 1, 64)
                .setDefaultValue(16)
                .setTooltip(Component.translatable("config.enhancedlittlemaidai.memory.spatialRecallRadius.tooltip"))
                .setSaveConsumer(EnhancedConfig.SPATIAL_RECALL_RADIUS::set)
                .build());

        memorySub.add(entryBuilder
                .startIntSlider(
                        Component.translatable("config.enhancedlittlemaidai.memory.semanticRetrieveTopK"),
                        EnhancedConfig.SEMANTIC_RETRIEVE_TOP_K.get(), 1, 10)
                .setDefaultValue(3)
                .setTooltip(Component.translatable("config.enhancedlittlemaidai.memory.semanticRetrieveTopK.tooltip"))
                .setSaveConsumer(EnhancedConfig.SEMANTIC_RETRIEVE_TOP_K::set)
                .build());

        memorySub.add(entryBuilder
                .startIntSlider(
                        Component.translatable("config.enhancedlittlemaidai.memory.maxFreshAgeHours"),
                        EnhancedConfig.MAX_AGE_TICKS.get() / 72000, 1, 100)
                .setDefaultValue(10)
                .setTooltip(Component.translatable("config.enhancedlittlemaidai.memory.maxFreshAgeHours.tooltip"))
                .setSaveConsumer(val -> EnhancedConfig.MAX_AGE_TICKS.set(val * 72000))
                .build());

        category.addEntry(memorySub.build());

        // ========== 主动聊天 ==========
        SubCategoryBuilder proactiveSub = entryBuilder.startSubCategory(
                Component.translatable("config.enhancedlittlemaidai.sub.proactive"));
        proactiveSub.setExpanded(true);

        proactiveSub.add(entryBuilder
                .startBooleanToggle(
                        Component.translatable("config.enhancedlittlemaidai.proactive.enabled"),
                        EnhancedConfig.PROACTIVE_CHAT_ENABLED.get())
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.enhancedlittlemaidai.proactive.enabled.tooltip"))
                .setSaveConsumer(EnhancedConfig.PROACTIVE_CHAT_ENABLED::set)
                .build());

        proactiveSub.add(entryBuilder
                .startIntSlider(
                        Component.translatable("config.enhancedlittlemaidai.proactive.cooldownTicks"),
                        EnhancedConfig.COOLDOWN_TICKS.get() / 20, 30, 3600)
                .setDefaultValue(600)
                .setTooltip(Component.translatable(
                        "config.enhancedlittlemaidai.proactive.cooldownTicks.tooltip"))
                .setSaveConsumer(val -> EnhancedConfig.COOLDOWN_TICKS.set(val * 20))
                .build());

        proactiveSub.add(entryBuilder
                .startDoubleField(
                        Component.translatable("config.enhancedlittlemaidai.proactive.triggerChance"),
                        EnhancedConfig.TRIGGER_CHANCE_PER_TICK.get())
                .setDefaultValue(0.002)
                .setMin(0.0001).setMax(1.0)
                .setTooltip(Component.translatable(
                        "config.enhancedlittlemaidai.proactive.triggerChance.tooltip"))
                .setSaveConsumer(EnhancedConfig.TRIGGER_CHANCE_PER_TICK::set)
                .build());

        proactiveSub.add(entryBuilder
                .startIntSlider(
                        Component.translatable("config.enhancedlittlemaidai.proactive.maxChats"),
                        EnhancedConfig.MAX_CHATS_PER_DAY.get(), 1, 100)
                .setDefaultValue(8)
                .setTooltip(Component.translatable(
                        "config.enhancedlittlemaidai.proactive.maxChats.tooltip"))
                .setSaveConsumer(EnhancedConfig.MAX_CHATS_PER_DAY::set)
                .build());

        proactiveSub.add(entryBuilder
                .startDoubleField(
                        Component.translatable("config.enhancedlittlemaidai.proactive.minDistance"),
                        EnhancedConfig.MIN_PLAYER_DISTANCE.get())
                .setDefaultValue(10.0)
                .setMin(1.0).setMax(64.0)
                .setTooltip(Component.translatable(
                        "config.enhancedlittlemaidai.proactive.minDistance.tooltip"))
                .setSaveConsumer(EnhancedConfig.MIN_PLAYER_DISTANCE::set)
                .build());

        proactiveSub.add(entryBuilder
                .startIntSlider(
                        Component.translatable("config.enhancedlittlemaidai.proactive.eventCooldown"),
                        EnhancedConfig.EVENT_COOLDOWN_TICKS.get() / 20, 60, 3600)
                .setDefaultValue(300)
                .setTooltip(Component.translatable(
                        "config.enhancedlittlemaidai.proactive.eventCooldown.tooltip"))
                .setSaveConsumer(val -> EnhancedConfig.EVENT_COOLDOWN_TICKS.set(val * 20))
                .build());

        proactiveSub.add(entryBuilder
                .startIntSlider(
                        Component.translatable("config.enhancedlittlemaidai.proactive.eventMaxPerSession"),
                        EnhancedConfig.EVENT_MAX_PER_DAY.get(), 1, 30)
                .setDefaultValue(5)
                .setTooltip(Component.translatable(
                        "config.enhancedlittlemaidai.proactive.eventMaxPerSession.tooltip"))
                .setSaveConsumer(EnhancedConfig.EVENT_MAX_PER_DAY::set)
                .build());

        proactiveSub.add(entryBuilder
                .startIntSlider(
                        Component.translatable("config.enhancedlittlemaidai.proactive.eventRangeBlocks"),
                        EnhancedConfig.EVENT_RANGE_BLOCKS.get(), 16, 128)
                .setDefaultValue(32)
                .setTooltip(Component.translatable("config.enhancedlittlemaidai.proactive.eventRangeBlocks.tooltip"))
                .setSaveConsumer(EnhancedConfig.EVENT_RANGE_BLOCKS::set)
                .build());

        proactiveSub.add(entryBuilder
                .startStrField(
                        Component.translatable("config.enhancedlittlemaidai.proactive.promptMode"),
                        EnhancedConfig.PROACTIVE_PROMPT_MODE.get())
                .setDefaultValue("FULL")
                .setTooltip(Component.translatable("config.enhancedlittlemaidai.proactive.promptMode.tooltip"))
                .setSaveConsumer(EnhancedConfig.PROACTIVE_PROMPT_MODE::set)
                .build());

        category.addEntry(proactiveSub.build());

        // ========== 女仆社交 ==========
        SubCategoryBuilder interMaidSub = entryBuilder.startSubCategory(
                Component.translatable("config.enhancedlittlemaidai.sub.interMaid"));
        interMaidSub.setExpanded(true);

        interMaidSub.add(entryBuilder
                .startBooleanToggle(
                        Component.translatable("config.enhancedlittlemaidai.interMaid.enabled"),
                        EnhancedConfig.INTER_MAID_ENABLED.get())
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.enhancedlittlemaidai.interMaid.enabled.tooltip"))
                .setSaveConsumer(EnhancedConfig.INTER_MAID_ENABLED::set)
                .build());

        interMaidSub.add(entryBuilder
                .startStrField(
                        Component.translatable("config.enhancedlittlemaidai.interMaid.promptMode"),
                        EnhancedConfig.INTER_MAID_PROMPT_MODE.get())
                .setDefaultValue("FULL")
                .setTooltip(Component.translatable("config.enhancedlittlemaidai.interMaid.promptMode.tooltip"))
                .setSaveConsumer(EnhancedConfig.INTER_MAID_PROMPT_MODE::set)
                .build());

        interMaidSub.add(entryBuilder
                .startIntSlider(
                        Component.translatable("config.enhancedlittlemaidai.interMaid.maxRounds"),
                        EnhancedConfig.INTER_MAID_MAX_ROUNDS.get(), 2, 4)
                .setDefaultValue(2)
                .setTooltip(Component.translatable("config.enhancedlittlemaidai.interMaid.maxRounds.tooltip"))
                .setSaveConsumer(EnhancedConfig.INTER_MAID_MAX_ROUNDS::set)
                .build());

        interMaidSub.add(entryBuilder
                .startDoubleField(
                        Component.translatable("config.enhancedlittlemaidai.interMaid.workingDistance"),
                        EnhancedConfig.INTER_MAID_WORKING_DISTANCE.get())
                .setDefaultValue(5.0)
                .setMin(1.0).setMax(32.0)
                .setTooltip(Component.translatable("config.enhancedlittlemaidai.interMaid.workingDistance.tooltip"))
                .setSaveConsumer(EnhancedConfig.INTER_MAID_WORKING_DISTANCE::set)
                .build());

        interMaidSub.add(entryBuilder
                .startDoubleField(
                        Component.translatable("config.enhancedlittlemaidai.interMaid.idleDistance"),
                        EnhancedConfig.INTER_MAID_IDLE_DISTANCE.get())
                .setDefaultValue(16.0)
                .setMin(1.0).setMax(64.0)
                .setTooltip(Component.translatable("config.enhancedlittlemaidai.interMaid.idleDistance.tooltip"))
                .setSaveConsumer(EnhancedConfig.INTER_MAID_IDLE_DISTANCE::set)
                .build());

        interMaidSub.add(entryBuilder
                .startDoubleField(
                        Component.translatable("config.enhancedlittlemaidai.interMaid.workingChance"),
                        EnhancedConfig.INTER_MAID_WORKING_CHANCE.get())
                .setDefaultValue(0.0002)
                .setMin(0.0001).setMax(1.0)
                .setTooltip(Component.translatable("config.enhancedlittlemaidai.interMaid.workingChance.tooltip"))
                .setSaveConsumer(EnhancedConfig.INTER_MAID_WORKING_CHANCE::set)
                .build());

        interMaidSub.add(entryBuilder
                .startDoubleField(
                        Component.translatable("config.enhancedlittlemaidai.interMaid.idleChance"),
                        EnhancedConfig.INTER_MAID_IDLE_CHANCE.get())
                .setDefaultValue(0.0005)
                .setMin(0.0001).setMax(1.0)
                .setTooltip(Component.translatable("config.enhancedlittlemaidai.interMaid.idleChance.tooltip"))
                .setSaveConsumer(EnhancedConfig.INTER_MAID_IDLE_CHANCE::set)
                .build());

        interMaidSub.add(entryBuilder
                .startIntSlider(
                        Component.translatable("config.enhancedlittlemaidai.interMaid.scanInterval"),
                        EnhancedConfig.INTER_MAID_SCAN_INTERVAL.get(), 10, 200)
                .setDefaultValue(120)
                .setTooltip(Component.translatable("config.enhancedlittlemaidai.interMaid.scanInterval.tooltip"))
                .setSaveConsumer(EnhancedConfig.INTER_MAID_SCAN_INTERVAL::set)
                .build());

        interMaidSub.add(entryBuilder
                .startDoubleField(
                        Component.translatable("config.enhancedlittlemaidai.interMaid.playerDistance"),
                        EnhancedConfig.INTER_MAID_PLAYER_DISTANCE.get())
                .setDefaultValue(18.0)
                .setMin(1.0).setMax(64.0)
                .setTooltip(Component.translatable("config.enhancedlittlemaidai.interMaid.playerDistance.tooltip"))
                .setSaveConsumer(EnhancedConfig.INTER_MAID_PLAYER_DISTANCE::set)
                .build());

        interMaidSub.add(entryBuilder
                .startIntSlider(
                        Component.translatable("config.enhancedlittlemaidai.interMaid.maxPerDay"),
                        EnhancedConfig.INTER_MAID_MAX_PER_DAY.get(), 1, 30)
                .setDefaultValue(3)
                .setTooltip(Component.translatable("config.enhancedlittlemaidai.interMaid.maxPerDay.tooltip"))
                .setSaveConsumer(EnhancedConfig.INTER_MAID_MAX_PER_DAY::set)
                .build());

        interMaidSub.add(entryBuilder
                .startIntSlider(
                        Component.translatable("config.enhancedlittlemaidai.interMaid.maxGlobalPerDay"),
                        EnhancedConfig.INTER_MAID_MAX_GLOBAL_PER_DAY.get(), 1, 50)
                .setDefaultValue(10)
                .setTooltip(Component.translatable("config.enhancedlittlemaidai.interMaid.maxGlobalPerDay.tooltip"))
                .setSaveConsumer(EnhancedConfig.INTER_MAID_MAX_GLOBAL_PER_DAY::set)
                .build());

        interMaidSub.add(entryBuilder
                .startIntSlider(
                        Component.translatable("config.enhancedlittlemaidai.interMaid.cooldownTicks"),
                        EnhancedConfig.INTER_MAID_COOLDOWN_TICKS.get() / 20, 30, 3600)
                .setDefaultValue(300)
                .setTooltip(Component.translatable("config.enhancedlittlemaidai.interMaid.cooldownTicks.tooltip"))
                .setSaveConsumer(val -> EnhancedConfig.INTER_MAID_COOLDOWN_TICKS.set(val * 20))
                .build());

        interMaidSub.add(entryBuilder
                .startStrField(
                        Component.translatable("config.enhancedlittlemaidai.interMaid.decisionMode"),
                        EnhancedConfig.INTER_MAID_DECISION_MODE.get())
                .setDefaultValue("LLM")
                .setTooltip(Component.translatable("config.enhancedlittlemaidai.interMaid.decisionMode.tooltip"))
                .setSaveConsumer(EnhancedConfig.INTER_MAID_DECISION_MODE::set)
                .build());

        interMaidSub.add(entryBuilder
                .startBooleanToggle(
                        Component.translatable("config.enhancedlittlemaidai.interMaid.crossOwner"),
                        EnhancedConfig.INTER_MAID_CROSS_OWNER.get())
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.enhancedlittlemaidai.interMaid.crossOwner.tooltip"))
                .setSaveConsumer(EnhancedConfig.INTER_MAID_CROSS_OWNER::set)
                .build());

        interMaidSub.add(entryBuilder
                .startIntSlider(
                        Component.translatable("config.enhancedlittlemaidai.interMaid.maxGroupSize"),
                        EnhancedConfig.INTER_MAID_MAX_GROUP_SIZE.get(), 2, 5)
                .setDefaultValue(3)
                .setTooltip(Component.translatable("config.enhancedlittlemaidai.interMaid.maxGroupSize.tooltip"))
                .setSaveConsumer(EnhancedConfig.INTER_MAID_MAX_GROUP_SIZE::set)
                .build());

        interMaidSub.add(entryBuilder
                .startIntSlider(
                        Component.translatable("config.enhancedlittlemaidai.interMaid.roundDelayMin"),
                        EnhancedConfig.INTER_MAID_ROUND_DELAY_MIN.get(), 1, 10)
                .setDefaultValue(3)
                .setTooltip(Component.translatable("config.enhancedlittlemaidai.interMaid.roundDelayMin.tooltip"))
                .setSaveConsumer(EnhancedConfig.INTER_MAID_ROUND_DELAY_MIN::set)
                .build());

        interMaidSub.add(entryBuilder
                .startIntSlider(
                        Component.translatable("config.enhancedlittlemaidai.interMaid.roundDelayMax"),
                        EnhancedConfig.INTER_MAID_ROUND_DELAY_MAX.get(), 1, 10)
                .setDefaultValue(5)
                .setTooltip(Component.translatable("config.enhancedlittlemaidai.interMaid.roundDelayMax.tooltip"))
                .setSaveConsumer(EnhancedConfig.INTER_MAID_ROUND_DELAY_MAX::set)
                .build());

        interMaidSub.add(entryBuilder
                .startDoubleField(
                        Component.translatable("config.enhancedlittlemaidai.interMaid.socialInjectChance"),
                        EnhancedConfig.SOCIAL_MEMORY_INJECT_CHANCE.get())
                .setDefaultValue(0.3)
                .setMin(0.0).setMax(1.0)
                .setTooltip(Component.translatable("config.enhancedlittlemaidai.interMaid.socialInjectChance.tooltip"))
                .setSaveConsumer(EnhancedConfig.SOCIAL_MEMORY_INJECT_CHANCE::set)
                .build());

        interMaidSub.add(entryBuilder
                .startIntSlider(
                        Component.translatable("config.enhancedlittlemaidai.interMaid.socialTopK"),
                        EnhancedConfig.SOCIAL_MEMORY_TOP_K.get(), 1, 5)
                .setDefaultValue(2)
                .setTooltip(Component.translatable("config.enhancedlittlemaidai.interMaid.socialTopK.tooltip"))
                .setSaveConsumer(EnhancedConfig.SOCIAL_MEMORY_TOP_K::set)
                .build());

        interMaidSub.add(entryBuilder
                .startIntSlider(
                        Component.translatable("config.enhancedlittlemaidai.interMaid.socialMaxSize"),
                        EnhancedConfig.SOCIAL_STORE_MAX_SIZE.get(), 10, 200)
                .setDefaultValue(50)
                .setTooltip(Component.translatable("config.enhancedlittlemaidai.interMaid.socialMaxSize.tooltip"))
                .setSaveConsumer(EnhancedConfig.SOCIAL_STORE_MAX_SIZE::set)
                .build());

        interMaidSub.add(entryBuilder
                .startIntSlider(
                        Component.translatable("config.enhancedlittlemaidai.interMaid.socialCompressTrigger"),
                        EnhancedConfig.SOCIAL_MEMORY_COMPRESS_TRIGGER.get(), 10, 200)
                .setDefaultValue(40)
                .setTooltip(Component.translatable("config.enhancedlittlemaidai.interMaid.socialCompressTrigger.tooltip"))
                .setSaveConsumer(EnhancedConfig.SOCIAL_MEMORY_COMPRESS_TRIGGER::set)
                .build());

        category.addEntry(interMaidSub.build());

        // ========== 仓储感知（仅 MSM 加载时显示） ==========
        if (StorageCompat.isLoaded()) {
            SubCategoryBuilder storageSub = entryBuilder.startSubCategory(
                    Component.translatable("config.enhancedlittlemaidai.sub.storage"));
            storageSub.setExpanded(false);

            storageSub.add(entryBuilder
                    .startBooleanToggle(
                            Component.translatable("config.enhancedlittlemaidai.storage.enableStorageMemory"),
                            EnhancedConfig.ENABLE_STORAGE_MEMORY.get())
                    .setDefaultValue(true)
                    .setTooltip(Component.translatable(
                            "config.enhancedlittlemaidai.storage.enableStorageMemory.tooltip"))
                    .setSaveConsumer(EnhancedConfig.ENABLE_STORAGE_MEMORY::set)
                    .build());

            storageSub.add(entryBuilder
                    .startIntSlider(
                            Component.translatable("config.enhancedlittlemaidai.storage.maxStoragePositions"),
                            EnhancedConfig.STORAGE_MAX_POSITIONS.get(), 3, 30)
                    .setDefaultValue(10)
                    .setTooltip(Component.translatable(
                            "config.enhancedlittlemaidai.storage.maxStoragePositions.tooltip"))
                    .setSaveConsumer(EnhancedConfig.STORAGE_MAX_POSITIONS::set)
                    .build());

            storageSub.add(entryBuilder
                    .startIntSlider(
                            Component.translatable("config.enhancedlittlemaidai.storage.maxItemsPerStorage"),
                            EnhancedConfig.STORAGE_MAX_ITEMS_PER_POS.get(), 3, 20)
                    .setDefaultValue(8)
                    .setTooltip(Component.translatable(
                            "config.enhancedlittlemaidai.storage.maxItemsPerStorage.tooltip"))
                    .setSaveConsumer(EnhancedConfig.STORAGE_MAX_ITEMS_PER_POS::set)
                    .build());

            storageSub.add(entryBuilder
                    .startIntSlider(
                            Component.translatable("config.enhancedlittlemaidai.storage.maxItemsSummary"),
                            EnhancedConfig.STORAGE_MAX_ITEMS_SUMMARY.get(), 5, 50)
                    .setDefaultValue(15)
                    .setTooltip(Component.translatable(
                            "config.enhancedlittlemaidai.storage.maxItemsSummary.tooltip"))
                    .setSaveConsumer(EnhancedConfig.STORAGE_MAX_ITEMS_SUMMARY::set)
                    .build());

            category.addEntry(storageSub.build());
        }

        // ========== 调试 ==========
        SubCategoryBuilder debugSub = entryBuilder.startSubCategory(
                Component.translatable("config.enhancedlittlemaidai.sub.debug"));
        debugSub.setExpanded(false);

        debugSub.add(entryBuilder
                .startBooleanToggle(
                        Component.translatable("config.enhancedlittlemaidai.debug.enableMaidGreeting"),
                        EnhancedConfig.ENABLE_MAID_GREETING.get())
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.enhancedlittlemaidai.debug.enableMaidGreeting.tooltip"))
                .setSaveConsumer(EnhancedConfig.ENABLE_MAID_GREETING::set)
                .build());

        debugSub.add(entryBuilder
                .startBooleanToggle(
                        Component.translatable("config.enhancedlittlemaidai.debug.debugLog"),
                        EnhancedConfig.DEBUG_LOG.get())
                .setDefaultValue(false)
                .setTooltip(Component.translatable(
                        "config.enhancedlittlemaidai.debug.debugLog.tooltip"))
                .setSaveConsumer(EnhancedConfig.DEBUG_LOG::set)
                .build());

        debugSub.add(entryBuilder
                .startBooleanToggle(
                        Component.translatable("config.enhancedlittlemaidai.debug.suppressParentJsonDump"),
                        EnhancedConfig.SUPPRESS_PARENT_JSON_DUMP.get())
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.enhancedlittlemaidai.debug.suppressParentJsonDump.tooltip"))
                .setSaveConsumer(EnhancedConfig.SUPPRESS_PARENT_JSON_DUMP::set)
                .build());

        category.addEntry(debugSub.build());
    }
}
