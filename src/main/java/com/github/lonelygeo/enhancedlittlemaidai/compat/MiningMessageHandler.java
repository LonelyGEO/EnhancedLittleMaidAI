package com.github.lonelygeo.enhancedlittlemaidai.compat;

import com.github.lonelygeo.enhancedlittlemaidai.EnhancedLittleMaidAI;
import com.github.lonelygeo.enhancedlittlemaidai.config.EnhancedConfig;
import com.github.lonelygeo.mininglittlemaid.api.event.MiningMessageEvent;
import com.github.lonelygeo.mininglittlemaid.api.event.MiningMessageType;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatManager;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.setting.CharacterSetting;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMClient;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MiningMessageEvent 监听器。
 * 在 MLM 发出采矿消息事件时，取消原硬编码消息，改为基于女仆角色设定由 LLM 生成自然对话。
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

        // 获取女仆角色设定
        String characterSetting = getCharacterSetting(chatManager, maid);
        if (StringUtils.isBlank(characterSetting)) return;

        event.setCanceled(true);

        String situation = buildSituation(event);
        String prompt = characterSetting
                + "\n\n[系统指令] 刚才发生了以下情况：" + situation
                + "\n请用1-2句简短自然的话告诉主人。直接说话即可，不要加动作描写或格式标记。" +
                " 注意：英文地名、物品名请转换为中文MC玩家熟知的名词。" +
                " 偶尔可以使用颜文字增加趣味，但不要每句都用。";

        LLMMessage sysMsg = LLMMessage.systemChat(maid, prompt);
        LLMMessage userMsg = LLMMessage.userChat(maid, "（采矿事件触发对话）");
        List<LLMMessage> messages = List.of(sysMsg, userMsg);

        long bubbleId = maid.getChatBubbleManager().addThinkingText("...");

        MiningChatCallback callback = new MiningChatCallback(chatManager, messages, bubbleId);
        client.chat(callback);
    }

    /**
     * 获取女仆的角色设定文本。
     * 优先使用玩家自定义设定，回退到模型预设设定。
     */
    private static String getCharacterSetting(MaidAIChatManager chatManager, EntityMaid maid) {
        String custom = chatManager.customSetting;
        if (StringUtils.isNotBlank(custom)) {
            return custom;
        }

        Optional<CharacterSetting> optSetting = chatManager.getSetting();
        if (optSetting.isPresent()) {
            try {
                return optSetting.get().getSetting(maid, "zh_cn");
            } catch (Exception e) {
                EnhancedLittleMaidAI.LOGGER.debug(
                        "EnhancedLittleMaidAI: Failed to get maid setting for mining chat", e);
            }
        }

        return null;
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
            case COMBAT_DETECTED -> buildCombatSituation(event);
        };
    }

    private static String buildCombatSituation(MiningMessageEvent event) {
        String ore = event.getOreName();
        Map<String, Object> ctx = event.getContext();

        // 尝试从 context 中提取敌对生物信息
        String mobInfo = "";
        if (ctx != null) {
            Object mobName = ctx.get("entity_name");
            if (mobName != null) {
                mobInfo = "，遇到了" + mobName;
            } else {
                Object mobType = ctx.get("entity_type");
                if (mobType != null) mobInfo = "，遇到了" + mobType;
            }
        }

        if (ore != null && !ore.isEmpty()) {
            return "正在挖掘" + ore + "时遭遇了敌对生物" + mobInfo + "。";
        }
        return "采矿时遭遇了敌对生物" + mobInfo + "。";
    }
}
