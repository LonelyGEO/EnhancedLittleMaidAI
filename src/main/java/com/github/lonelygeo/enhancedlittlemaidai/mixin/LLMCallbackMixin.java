package com.github.lonelygeo.enhancedlittlemaidai.mixin;

import com.github.lonelygeo.enhancedlittlemaidai.memory.MindPalace;
import com.github.lonelygeo.enhancedlittlemaidai.util.ReasoningContentStore;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatData;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.response.ResponseChat;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMClient;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.openai.response.Message;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.openai.response.ToolCall;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import org.apache.commons.lang3.StringUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * LLMCallback Mixin：reasoningContent 支持 + 记忆提取计数。
 */
@Mixin(value = LLMCallback.class, remap = false)
public abstract class LLMCallbackMixin {

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
        String reasoningContent = ReasoningContentStore.getFromMessage(choice);
        target.addAssistantHistory(rawContent, toolCalls);
        setReasoningOnLastEntry(target, reasoningContent);
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
        String reasoningContent = ReasoningContentStore.getFromMessage(choice);
        LLMMessage result = LLMMessage.assistantChat(maid, rawContent, toolCalls);
        ReasoningContentStore.put(result, reasoningContent);
        return result;
    }

    @Unique
    private static void setReasoningOnLastEntry(MaidAIChatData chatData, String reasoningContent) {
        if (StringUtils.isNotBlank(reasoningContent)) {
            try {
                LLMMessage lastMsg = chatData.getHistory().getDeque().peekLast();
                if (lastMsg != null) {
                    ReasoningContentStore.put(lastMsg, reasoningContent);
                }
            } catch (Exception ignored) {
            }
        }
    }

    // ==================== 记忆提取 ====================

    @Inject(
            method = "onSuccess",
            at = @At("TAIL"),
            remap = false
    )
    private void enhanced$extractMemoriesOnSuccess(ResponseChat responseChat, CallbackInfo ci) {
        try {
            EntityMaid maid = getMaid();
            if (maid == null || maid.isRemoved()) return;

            MindPalace palace = MindPalace.getOrCreate(maid.getUUID());
            palace.incrementRoundCounter();

            long gameTime = maid.level().getGameTime();
            if (!palace.shouldExtractMemories(gameTime)) return;
            palace.markExtractionDone(gameTime);

            // TODO: 异步 LLM 记忆提取 — Phase 2 后续迭代
        } catch (Exception e) {
            // 静默失败，不影响正常对话
        }
    }
}
