package com.github.tartaricacid.enhancedlittlemaidai.mixin;

import com.github.tartaricacid.enhancedlittlemaidai.util.ReasoningContentStore;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatData;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMClient;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.openai.response.Message;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.openai.response.ToolCall;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import org.apache.commons.lang3.StringUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * 修改 onFunctionCall：Redirect 替换 addAssistantHistory 和 LLMMessage.assistantChat 调用，
 * 使其携带 reasoningContent。
 */
@Mixin(value = LLMCallback.class, remap = false)
public abstract class LLMCallbackMixin {

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
}
