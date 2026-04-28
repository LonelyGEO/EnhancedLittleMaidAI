package com.github.lonelygeo.enhancedlittlemaidai.config;

import com.github.lonelygeo.enhancedlittlemaidai.compat.MiningCompat;
import com.github.tartaricacid.touhoulittlemaid.api.event.client.AddClothConfigEvent;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Cloth Config 实际事件处理器。
 * 仅在 cloth_config 已加载时由 ClothConfigIntegration 通过反射实例化。
 * 直接引用 Cloth Config API 类，避免主类 ClothConfigIntegration 因缺少 Cloth Config 类而加载失败。
 */
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

        // ========== 主动聊天 ==========
        SubCategoryBuilder proactiveSub = entryBuilder.startSubCategory(
                Component.translatable("config.enhancedlittlemaidai.sub.proactive"));
        proactiveSub.setExpanded(true);

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
                        EnhancedConfig.MAX_CHATS_PER_SESSION.get(), 1, 100)
                .setDefaultValue(8)
                .setTooltip(Component.translatable(
                        "config.enhancedlittlemaidai.proactive.maxChats.tooltip"))
                .setSaveConsumer(EnhancedConfig.MAX_CHATS_PER_SESSION::set)
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
                        EnhancedConfig.EVENT_MAX_PER_SESSION.get(), 1, 30)
                .setDefaultValue(5)
                .setTooltip(Component.translatable(
                        "config.enhancedlittlemaidai.proactive.eventMaxPerSession.tooltip"))
                .setSaveConsumer(EnhancedConfig.EVENT_MAX_PER_SESSION::set)
                .build());

        category.addEntry(proactiveSub.build());

        // ========== 调试 ==========
        SubCategoryBuilder debugSub = entryBuilder.startSubCategory(
                Component.translatable("config.enhancedlittlemaidai.sub.debug"));
        debugSub.setExpanded(false);

        debugSub.add(entryBuilder
                .startBooleanToggle(
                        Component.translatable("config.enhancedlittlemaidai.debug.debugLog"),
                        EnhancedConfig.DEBUG_LOG.get())
                .setDefaultValue(false)
                .setTooltip(Component.translatable(
                        "config.enhancedlittlemaidai.debug.debugLog.tooltip"))
                .setSaveConsumer(EnhancedConfig.DEBUG_LOG::set)
                .build());

        category.addEntry(debugSub.build());
    }
}
