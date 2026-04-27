package com.github.tartaricacid.enhancedlittlemaidai.mixin;

import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatData;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.openai.response.ToolCall;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.util.CappedQueue;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;

/**
 * 为 MaidAIChatData 添加支持 reasoningContent 的 addAssistantHistory 重载方法。
 */
@Mixin(value = MaidAIChatData.class, remap = false)
public abstract class MaidAIChatDataMixin {
    @Shadow
    public abstract EntityMaid getMaid();

    @Shadow
    public abstract CappedQueue<LLMMessage> getHistory();

    @Shadow
    protected abstract void onHistoryUpdated();

    /**
     * 添加 assistant 历史记录，附带 reasoningContent。
     */
    @Unique
    public void addAssistantHistory(String message, @Nullable String reasoningContent) {
        LLMMessage llmMsg = LLMMessage.assistantChat(getMaid(), message);
        ((LLMMessageMixin) (Object) llmMsg).enhancedSetReasoningContent(reasoningContent);
        getHistory().add(llmMsg);
        onHistoryUpdated();
    }

    /**
     * 添加 assistant 历史记录（含 toolCalls 和 reasoningContent）。
     */
    @Unique
    public void addAssistantHistory(String message, List<ToolCall> toolCalls, @Nullable String reasoningContent) {
        LLMMessage llmMsg = LLMMessage.assistantChat(getMaid(), message, toolCalls);
        ((LLMMessageMixin) (Object) llmMsg).enhancedSetReasoningContent(reasoningContent);
        getHistory().add(llmMsg);
        onHistoryUpdated();
    }
}
