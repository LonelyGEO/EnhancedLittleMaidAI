package com.github.lonelygeo.enhancedlittlemaidai.util;

import com.github.lonelygeo.enhancedlittlemaidai.EnhancedLittleMaidAI;
import com.github.lonelygeo.enhancedlittlemaidai.config.EnhancedConfig;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatManager;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.response.ResponseChat;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMClient;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.apache.commons.lang3.StringUtils;

import java.net.http.HttpRequest;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 女仆放置时自动生成角色设定 + 见面问候。
 */
public final class MaidSpawnHandler {

    private static final Set<UUID> GREETED = Collections.synchronizedSet(new HashSet<>());

    private MaidSpawnHandler() {
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!EnhancedConfig.ENABLE_MAID_GREETING.get()) return;
        Entity entity = event.getEntity();
        if (!(entity instanceof EntityMaid maid)) return;
        if (maid.level().isClientSide()) return;
        if (maid.isRemoved()) return;

        if (!LLMUtil.isAvailable(maid)) return;

        if (EnhancedConfig.debugLog()) {
            EnhancedLittleMaidAI.LOGGER.debug(
                    "MaidSpawn: Maid {} joined, LLM available", maid.getUUID());
        }

        MaidAIChatManager chatManager = maid.getAiChatManager();
        LLMClient client = chatManager.getLLMSite().client();

        if (GREETED.contains(maid.getUUID())) return;
        GREETED.add(maid.getUUID());

        boolean hasSetting = StringUtils.isNotBlank(chatManager.customSetting)
                || chatManager.getSetting().isPresent();

        if (hasSetting) {
            if (EnhancedConfig.debugLog()) {
                EnhancedLittleMaidAI.LOGGER.debug(
                        "MaidSpawn: Maid {} has existing setting, generating greeting", maid.getUUID());
            }
            sendGreeting(chatManager, client, maid);
        } else {
            if (EnhancedConfig.debugLog()) {
                EnhancedLittleMaidAI.LOGGER.debug(
                        "MaidSpawn: Maid {} has no setting, auto-generating + greeting", maid.getUUID());
            }
            genSetting(chatManager, client, maid, 0);
        }
    }

    // ==================== 设定生成 ====================

    /** 生成角色设定（最多重试一次） */
    private static void genSetting(MaidAIChatManager chatManager, LLMClient client,
                                    EntityMaid maid, int retry) {
        String prompt = buildGenSettingPrompt(maid);
        LLMMessage msg = LLMMessage.userChat(maid, prompt);
        List<LLMMessage> msgs = List.of(msg);

        client.chat(new LLMCallback(chatManager, msgs, true) {{
            needAddTools = false;
        } 
            @Override
            public void onSuccess(ResponseChat responseChat) {
                String result = responseChat.getChatText();
                if (StringUtils.isBlank(result)) {
                    if (retry < 1) genSetting(chatManager, client, maid, retry + 1);
                    return;
                }
                result = result.replaceAll("\n+", "\n\n");
                if (result.length() > 4000) result = result.substring(0, 4000);
                chatManager.customSetting = result;

                sendGreeting(chatManager, client, maid);
                if (EnhancedConfig.debugLog()) {
                    EnhancedLittleMaidAI.LOGGER.info(
                            "MaidSpawn: Auto-gen setting for maid {} ({} chars)",
                            maid.getUUID(), result.length());
                }
            }

            @Override
            public void onFailure(HttpRequest request, Throwable throwable, int errorCode) {
                EnhancedLittleMaidAI.LOGGER.warn(
                        "MaidSpawn: Failed to gen setting for maid {}", maid.getUUID());
            }
        });
    }

    /** 构建设定生成 prompt（与 TLM autoGenSetting 等价） */
    private static String buildGenSettingPrompt(EntityMaid maid) {
        String name = maid.getDisplayName().getString();
        return String.format("""
                Generate a character profile for a Minecraft maid companion based on the given name. Include:
                - Character setting and role
                - Personality traits
                - Language style and speech patterns
                - Background story
                - Appearance features
                
                ## Notes
                - The profile must fit the Minecraft game world.
                - If the name comes from a game, anime, or manga character, follow the original source material as closely as possible.
                
                ## Output Format
                - About 300 words
                - Divide into paragraphs separated by blank lines
                - Write in 中文 (中国)
                
                Character: %s""", name);
    }

    // ==================== 见面问候 ====================

    private static void sendGreeting(MaidAIChatManager chatManager, LLMClient client,
                                      EntityMaid maid) {
        String prompt = buildGreetingPrompt(maid);
        LLMMessage sysMsg = LLMMessage.systemChat(maid, prompt);
        LLMMessage userMsg = LLMMessage.userChat(maid, "（女仆苏醒问候）");
        List<LLMMessage> msgs = List.of(sysMsg, userMsg);

        client.chat(new LLMCallback(chatManager, msgs, true) {{
            needAddTools = false;
        }
            @Override
            public void onSuccess(ResponseChat responseChat) {
                String text = responseChat.getChatText();
                if (StringUtils.isBlank(text)) return;
                maid.getChatBubbleManager().addTextChatBubble(text);
                if (EnhancedConfig.debugLog()) {
                    EnhancedLittleMaidAI.LOGGER.info(
                            "MaidSpawn: Greeting delivered for maid {}: {}",
                            maid.getUUID(), text.substring(0, Math.min(60, text.length())));
                }
            }

            @Override
            public void onFailure(HttpRequest request, Throwable throwable, int errorCode) {
                EnhancedLittleMaidAI.LOGGER.warn(
                        "MaidSpawn: Failed to greet maid {}", maid.getUUID());
            }
        });
    }

    private static String getCharacterSetting(EntityMaid maid) {
        String mode = EnhancedConfig.PROACTIVE_PROMPT_MODE.get();
        if ("MINIMAL".equals(mode)) {
            return maid.getDisplayName().getString() + "，一位女仆。";
        }

        MaidAIChatManager mgr = maid.getAiChatManager();
        String custom = mgr.customSetting;
        if (StringUtils.isNotBlank(custom)) {
            if ("SUMMARY".equals(mode)) return custom.substring(0, Math.min(200, custom.length()));
            return custom;
        }

        try {
            var opt = mgr.getSetting();
            if (opt.isPresent()) {
                String raw = opt.get().getSetting(maid, "zh_cn");
                if ("SUMMARY".equals(mode)) return raw.substring(0, Math.min(200, raw.length()));
                return raw;
            }
        } catch (Exception ignored) {
        }

        return maid.getDisplayName().getString() + "，一位女仆。";
    }

    private static String buildGreetingPrompt(EntityMaid maid) {
        String setting = getCharacterSetting(maid);
        return setting
                + "\n\n[系统指令] 你刚刚苏醒，来到了一个新的世界。请用1句话向主人问候。"
                + "只输出对话文本，严禁输出任何括号内的动作描述、旁白、心理活动。";
    }
}
