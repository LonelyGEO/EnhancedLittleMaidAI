package com.github.lonelygeo.enhancedlittlemaidai.util;

import com.github.lonelygeo.enhancedlittlemaidai.EnhancedLittleMaidAI;
import com.github.lonelygeo.enhancedlittlemaidai.config.EnhancedConfig;
import com.github.lonelygeo.enhancedlittlemaidai.memory.MindPalace;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatManager;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.response.ResponseChat;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.setting.CharacterSetting;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.LivingEntity;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.net.http.HttpRequest;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 主动聊天 LLM 回调。
 * 继承 LLMCallback，覆写 onSuccess() 以显示聊天气泡，不调用 super 以避免触发
 * LLMCallbackMixin 的记忆提取链，不存储到聊天历史（避免污染正常对话上下文）。
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

    /** 设置"思考中"气泡 ID，由 caller 在 chat() 前调用 */
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
                    long fallbackId = maid.getChatBubbleManager().addThinkingText("...");
                    maid.getChatBubbleManager().addLLMChatText(chatText, fallbackId);
                }
            } catch (Exception e) {
                EnhancedLittleMaidAI.LOGGER.warn(
                        "EnhancedLittleMaidAI: Failed to display proactive chat bubble", e);
            }
        }

        if (EnhancedConfig.debugLog()) {
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
     * 包含环境信息（生物群系、时间、天气）+ MindPalace 记忆上下文。
     */
    public static String buildProactivePrompt(EntityMaid maid) {
        return buildProactivePrompt(maid, null);
    }

    /**
     * 构建主动聊天的系统 prompt，可附带环境事件描述。
     *
     * @param maid             女仆
     * @param eventDescription 环境事件描述，如"现在是清晨日出时分。"，为 null 时行为同无参版本
     */
    public static String buildProactivePrompt(EntityMaid maid, @Nullable String eventDescription) {
        String biome = "未知";
        try {
            var biomeKey = maid.level().getBiome(maid.blockPosition()).unwrapKey();
            if (biomeKey.isPresent()) {
                String path = biomeKey.get().location().getPath();
                biome = path.replace('_', ' ');
            }
        } catch (Exception ignored) {
        }

        String timeOfDay = "";
        try {
            long time = maid.level().getDayTime() % 24000;
            if (time < 6000) timeOfDay = "清晨";
            else if (time < 12000) timeOfDay = "白天";
            else if (time < 13000) timeOfDay = "日落";
            else timeOfDay = "夜晚";
        } catch (Exception ignored) {
        }

        String weather = "未知";
        try {
            if (maid.level().isRaining()) {
                weather = maid.level().isThundering() ? "雷雨" : "下雨";
            } else {
                weather = "晴朗";
            }
        } catch (Exception ignored) {
        }

        String ownerName = "主人";
        try {
            LivingEntity owner = maid.getOwner();
            if (owner != null) ownerName = owner.getDisplayName().getString();
        } catch (Exception ignored) {
        }

        String characterSetting = getCharacterSetting(maid);
        if (StringUtils.isBlank(characterSetting)) {
            characterSetting = maid.getDisplayName().getString() + "，" + ownerName + "的忠诚女仆。";
        }

        String memoryContext = "";
        try {
            MindPalace palace = MindPalace.get(maid.getUUID());
            if (palace != null && palace.size() > 0) {
                String mem = palace.buildMemoryContext("当前环境", maid.blockPosition());
                if (!mem.isEmpty()) {
                    memoryContext = "\n你记得以下关于当前环境的信息：\n" + mem;
                }
            }
        } catch (Exception ignored) {
        }

        // 社交记忆注入
        String socialMemoryContext = "";
        try {
            if (Math.random() < EnhancedConfig.SOCIAL_MEMORY_INJECT_CHANCE.get()) {
                MindPalace palace = MindPalace.get(maid.getUUID());
                if (palace != null && palace.getSocialStore().size() > 0) {
                    String social = palace.buildSocialMemoryContext();
                    if (!social.isEmpty()) {
                        socialMemoryContext = social;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        String eventLine = "";
        if (eventDescription != null) {
            eventLine = eventDescription + "\n";
        }

        return String.format("""
                [系统指令] 你现在要主动发起对话（不需要等待主人说话）。

                %s
                你目前在%s，%s天气，%s。
                %s%s%s
                请用1-2句简短自然的话主动和主人聊天。直接说话即可，不要加动作描写、括号注释或任何格式标记。

                可选话题：关心主人状态、评论环境或天气、分享你注意到的事情、询问是否需要帮助。
                注意：你是在主动发起对话，不要回应任何人的话。
                注意：游戏数据中的英文地名、物品名，请转换为中文Minecraft玩家熟知的名词。例如：wooded badlands → 繁茂的恶地，iron_ore → 铁矿石。""",
                characterSetting, biome, weather, timeOfDay, eventLine, memoryContext, socialMemoryContext);
    }

    /** 获取角色设定（复用于主动聊天 prompt） */
    private static String getCharacterSetting(EntityMaid maid) {
        String mode = EnhancedConfig.PROACTIVE_PROMPT_MODE.get();
        if ("MINIMAL".equals(mode)) {
            return maid.getDisplayName().getString() + "，一位女仆。";
        }

        String custom = maid.getAiChatManager().customSetting;
        if (StringUtils.isNotBlank(custom)) {
            if ("SUMMARY".equals(mode)) {
                return custom.substring(0, Math.min(200, custom.length()));
            }
            return custom;
        }

        try {
            var optSetting = maid.getAiChatManager().getSetting();
            if (optSetting.isPresent()) {
                String raw = optSetting.get().getSetting(maid, "zh_cn");
                if ("SUMMARY".equals(mode)) {
                    return raw.substring(0, Math.min(200, raw.length()));
                }
                return raw;
            }
        } catch (Exception ignored) {
        }

        return maid.getDisplayName().getString() + "，一位女仆。";
    }
}
