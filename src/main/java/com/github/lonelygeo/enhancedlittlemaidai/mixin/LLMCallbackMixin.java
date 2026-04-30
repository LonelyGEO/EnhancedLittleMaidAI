package com.github.lonelygeo.enhancedlittlemaidai.mixin;

import com.github.lonelygeo.enhancedlittlemaidai.memory.MemoryCategory;
import com.github.lonelygeo.enhancedlittlemaidai.memory.MemoryExtractionCallback;
import com.github.lonelygeo.enhancedlittlemaidai.memory.MemoryItem;
import com.github.lonelygeo.enhancedlittlemaidai.memory.MindPalace;
import com.github.lonelygeo.enhancedlittlemaidai.util.InterMaidChatCallback;
import com.github.lonelygeo.enhancedlittlemaidai.util.InterMaidDecisionCallback;
import com.github.lonelygeo.enhancedlittlemaidai.util.ProactiveChatCallback;
import com.github.lonelygeo.enhancedlittlemaidai.EnhancedLittleMaidAI;
import com.github.lonelygeo.enhancedlittlemaidai.config.EnhancedConfig;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatData;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatManager;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.response.ResponseChat;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMClient;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMSite;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.Role;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.openai.response.Message;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.openai.response.ToolCall;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import org.apache.commons.lang3.StringUtils;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * LLMCallback Mixin：reasoningContent 支持 + 记忆提取。
 */
@Mixin(value = LLMCallback.class, remap = false)
public abstract class LLMCallbackMixin {

    @Shadow
    @Final
    private MaidAIChatManager chatManager;

    @Shadow
    private List<LLMMessage> messages;

    @Shadow
    public abstract EntityMaid getMaid();

    @Redirect(
            method = "onFunctionCall",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/github/tartaricacid/touhoulittlemaid/ai/manager/entity/MaidAIChatData;"
                             + "addAssistantHistory(Ljava/lang/String;Ljava/util/List;)V"
            ),
            remap = false,
            require = 0
    )
    private void enhanced$redirectAddHistory(
            MaidAIChatData target, String msg, List<ToolCall> toolCalls,
            Message choice, LLMClient client
    ) {
        String rawContent = StringUtils.defaultString(choice.getContent());
        String reasoningContent = StringUtils.defaultString(choice.getReasoningContent());
        target.addAssistantHistory(rawContent, toolCalls,
                StringUtils.isNotBlank(reasoningContent) ? reasoningContent : null);
    }

    @Redirect(
            method = "onFunctionCall",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/github/tartaricacid/touhoulittlemaid/ai/service/llm/LLMMessage;"
                             + "assistantChat(Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;"
                             + "Ljava/lang/String;Ljava/util/List;)"
                             + "Lcom/github/tartaricacid/touhoulittlemaid/ai/service/llm/LLMMessage;"
            ),
            remap = false,
            require = 0
    )
    private LLMMessage enhanced$redirectAssistantChat(
            EntityMaid maid, String msg, List<ToolCall> toolCalls,
            Message choice, LLMClient client
    ) {
        String rawContent = StringUtils.defaultString(choice.getContent());
        String reasoningContent = StringUtils.defaultString(choice.getReasoningContent());
        return LLMMessage.assistantChat(maid, rawContent, toolCalls,
                StringUtils.isNotBlank(reasoningContent) ? reasoningContent : null);
    }

    // ==================== 记忆提取 ====================

    @Inject(
            method = "onSuccess",
            at = @At("TAIL"),
            remap = false
    )
    private void enhanced$extractMemoriesOnSuccess(ResponseChat responseChat, CallbackInfo ci) {
        try {
            if (EnhancedConfig.debugLog()) {
                EnhancedLittleMaidAI.LOGGER.debug(
                        "EnhancedLittleMaidAI: LLM onSuccess ENTER, cb={}",
                        this.getClass().getSimpleName());
            }
            // 防递归：MemoryExtractionCallback/ProactiveChatCallback 自身触发不做提取
            if ((Object) this instanceof MemoryExtractionCallback) return;
            if ((Object) this instanceof ProactiveChatCallback) return;
            if ((Object) this instanceof InterMaidChatCallback) return;
            if ((Object) this instanceof InterMaidDecisionCallback) return;

            EntityMaid maid = getMaid();
            if (maid == null || maid.isRemoved()) return;

            MindPalace palace = MindPalace.getOrCreate(maid.getUUID());
            palace.incrementRoundCounter();

            long gameTime = maid.level().getGameTime();

            // 关键词触发立即记忆
            String userMessage = "";
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
                    if (EnhancedConfig.debugLog()) {
                        EnhancedLittleMaidAI.LOGGER.info(
                                "EnhancedLittleMaidAI: Quick memory saved for maid {} (keyword trigger)",
                                maid.getUUID());
                    }
                }
            }

            if (!palace.shouldExtractMemories(gameTime)) return;
            palace.markExtractionDone(gameTime);

            if (EnhancedConfig.debugLog()) {
                EnhancedLittleMaidAI.LOGGER.info(
                        "EnhancedLittleMaidAI: Triggering memory extraction for maid {}",
                        maid.getUUID());
            }

            enhanced$triggerAsyncMemoryExtraction(maid, palace, gameTime);

            // 记忆压缩检测
            if (palace.needsCompression()) {
                LLMSite site = chatManager.getLLMSite();
                if (site != null && site.enabled()) {
                    LLMClient client = site.client();
                    if (client != null) {
                        palace.triggerCompression(maid, client);
                        if (EnhancedConfig.debugLog()) {
                            EnhancedLittleMaidAI.LOGGER.info(
                                    "EnhancedLittleMaidAI: Triggering memory compression for maid {}",
                                    maid.getUUID());
                        }
                    }
                }
            }
        } catch (Exception e) {
            EnhancedLittleMaidAI.LOGGER.warn(
                    "EnhancedLittleMaidAI: Memory extraction trigger failed for maid {}",
                    getMaid().getUUID(), e);
        }
    }

    @Unique
    private void enhanced$triggerAsyncMemoryExtraction(EntityMaid maid, MindPalace palace, long gameTime) {
        LLMSite site = chatManager.getLLMSite();
        if (site == null || !site.enabled()) {
            if (EnhancedConfig.debugLog()) {
                EnhancedLittleMaidAI.LOGGER.info(
                        "EnhancedLittleMaidAI: LLM site not available for maid {}, skipping extraction",
                        maid.getUUID());
            }
            return;
        }

        LLMClient client = site.client();
        if (client == null) return;

        List<String> recentTexts = enhanced$extractRecentMessages();
        if (recentTexts.isEmpty()) return;

        String prompt = MemoryExtractionCallback.buildPrompt(recentTexts);
        LLMMessage systemMsg = LLMMessage.systemChat(maid, prompt);
        List<LLMMessage> extractionMessages = List.of(systemMsg);

        CompletableFuture<List<MemoryItem>> future = new CompletableFuture<>();
        MemoryExtractionCallback exCallback = new MemoryExtractionCallback(
                chatManager, extractionMessages, future);

        client.chat(exCallback);

        future.whenComplete((items, ex) -> {
            if (ex != null) {
                EnhancedLittleMaidAI.LOGGER.warn(
                        "EnhancedLittleMaidAI: Memory extraction failed for maid {}",
                        maid.getUUID(), ex);
                return;
            }
            if (items != null && !items.isEmpty()) {
                palace.addMemories(items, gameTime);
                if (EnhancedConfig.debugLog()) {
                    EnhancedLittleMaidAI.LOGGER.info(
                            "EnhancedLittleMaidAI: Memory extraction completed for maid {}: {} memories",
                            maid.getUUID(), items.size());
                }
            }
        });
    }

    @Unique
    private List<String> enhanced$extractRecentMessages() {
        List<String> texts = new ArrayList<>();
        int count = Math.min(messages.size(), 20);
        int start = messages.size() - count;
        for (int i = start; i < messages.size(); i++) {
            LLMMessage msg = messages.get(i);
            if (msg.role() == Role.USER || msg.role() == Role.ASSISTANT) {
                texts.add(msg.role().name() + ": " + msg.message());
            }
        }
        return texts;
    }
}
