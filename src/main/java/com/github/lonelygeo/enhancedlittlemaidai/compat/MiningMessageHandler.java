package com.github.lonelygeo.enhancedlittlemaidai.compat;

import com.github.lonelygeo.enhancedlittlemaidai.EnhancedLittleMaidAI;
import com.github.lonelygeo.enhancedlittlemaidai.config.EnhancedConfig;
import com.github.lonelygeo.mininglittlemaid.api.event.MiningMessageEvent;
import com.github.lonelygeo.mininglittlemaid.api.event.MiningMessageType;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatManager;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMClient;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;

/**
 * MiningMessageEvent 监听器。
 * 在 MLM 发出采矿消息事件时，取消原硬编码消息，改为 LLM 生成自然对话气泡。
 * 仅在 MiningLittleMaid 已加载时由 EnhancedLittleMaidAI 通过反射注册。
 */
public final class MiningMessageHandler {

    private MiningMessageHandler() {
    }

    /**
     * 注册到 NeoForge 事件总线。由 EnhancedLittleMaidAI 反射调用。
     */
    public static void register() {
        NeoForge.EVENT_BUS.register(MiningMessageHandler.class);
        EnhancedLittleMaidAI.LOGGER.info("EnhancedLittleMaidAI: MiningMessageHandler registered");
    }

    @SubscribeEvent
    public static void onMiningMessage(MiningMessageEvent event) {
        if (!EnhancedConfig.ENABLE_MINING_CHAT.get()) return;

        EntityMaid maid = event.getMaid();
        if (maid == null || maid.isRemoved()) return;

        MaidAIChatManager chatManager = maid.getAiChatManager();
        if (chatManager == null) return;

        var site = chatManager.getLLMSite();
        if (site == null || !site.enabled()) return;

        LLMClient client = site.client();
        if (client == null) return;

        event.setCanceled(true);

        String situation = buildSituation(event);
        String prompt = EnhancedConfig.MINING_CHAT_PROMPT.get()
                .replace("{maid}", maid.getDisplayName().getString())
                .replace("{situation}", situation);

        LLMMessage sysMsg = LLMMessage.systemChat(maid, prompt);
        LLMMessage userMsg = LLMMessage.userChat(maid, "（采矿事件触发对话）");
        List<LLMMessage> messages = List.of(sysMsg, userMsg);

        long bubbleId = maid.getChatBubbleManager().addThinkingText("...");

        MiningChatCallback callback = new MiningChatCallback(chatManager, messages, bubbleId);
        client.chat(callback);
    }

    private static String buildSituation(MiningMessageEvent event) {
        MiningMessageType type = event.getType();
        String ore = event.getOreName();

        return switch (type) {
            case ORE_ABOVE -> "在头顶上方发现了" + ore + "。";
            case ORE_BELOW -> "在脚下发现了" + ore + "。";
            case ORE_UNREACHABLE -> "发现了" + ore + "，但是无法到达。";
            case INVENTORY_FULL -> "背包已满，无法继续采矿。";
            case NO_TORCH -> "需要火把，但是已经没有火把了。";
        };
    }
}
