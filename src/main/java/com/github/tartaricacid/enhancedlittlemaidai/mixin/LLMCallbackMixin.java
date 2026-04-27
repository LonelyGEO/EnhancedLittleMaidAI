package com.github.tartaricacid.enhancedlittlemaidai.mixin;

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
 * 修改 onFunctionCall：用 Redirect 替换原版的 addAssistantHistory 和 LLMMessage.assistantChat 调用，
 * 使其携带 reasoningContent。
 */
@Mixin(value = LLMCallback.class, remap = false)
public abstract class LLMCallbackMixin {

    /**
     * 重定向 addAssistantHistory(StringUtils.EMPTY, choice.getToolCalls()) 调用。
     * 替换为带 rawContent 的版本，并附加 reasoningContent。
     */
    @Redirect(
            method = "onFunctionCall",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/github/tartaricacid/touhoulittlemaid/ai/manager/entity/MaidAIChatData;"
                             + "addAssistantHistory(Ljava/lang/String;Ljava/util/List;)V"
            ),
            remap = false
    )
    private void enhanced$redirectAddHistory(
            MaidAIChatData target,
            String originalMessage,
            List<ToolCall> toolCalls,
            Message choice,
            LLMClient client
    ) {
        String rawContent = StringUtils.defaultString(getRawContentSafe(choice));
        String reasoningContent = getReasoningContentSafe(choice);
        target.addAssistantHistory(rawContent, toolCalls);
        // 为刚添加的历史记录设置 reasoningContent
        setReasoningOnLastEntry(target, reasoningContent);
    }

    /**
     * 重定向 LLMMessage.assistantChat(maid, choice.getContent(), choice.getToolCalls()) 调用。
     * 替换为带 reasoningContent 的版本，使用 rawContent 而非过滤后的 content。
     */
    @Redirect(
            method = "onFunctionCall",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/github/tartaricacid/touhoulittlemaid/ai/service/llm/LLMMessage;"
                             + "assistantChat(Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;"
                             + "Ljava/lang/String;Ljava/util/List;)"
                             + "Lcom/github/tartaricacid/touhoulittlemaid/ai/service/llm/LLMMessage;"
            ),
            remap = false
    )
    private LLMMessage enhanced$redirectAssistantChat(
            EntityMaid maid,
            String originalMessage,
            List<ToolCall> toolCalls,
            Message choice,
            LLMClient client
    ) {
        String rawContent = StringUtils.defaultString(getRawContentSafe(choice));
        String reasoningContent = getReasoningContentSafe(choice);
        LLMMessage msg = LLMMessage.assistantChat(maid, rawContent, toolCalls);
        if (StringUtils.isNotBlank(reasoningContent)) {
            ((LLMMessageMixin) (Object) msg).enhancedSetReasoningContent(reasoningContent);
        }
        return msg;
    }

    @Unique
    private static void setReasoningOnLastEntry(MaidAIChatData chatData, String reasoningContent) {
        if (StringUtils.isNotBlank(reasoningContent)) {
            try {
                LLMMessage lastMsg = chatData.getHistory().getDeque().peekLast();
                if (lastMsg != null) {
                    ((LLMMessageMixin) (Object) lastMsg).enhancedSetReasoningContent(reasoningContent);
                }
            } catch (Exception ignored) {
            }
        }
    }

    @Unique
    private static String getRawContentSafe(Message message) {
        try {
            String raw = ((MessageMixin) (Object) message).getRawContent();
            return raw != null ? raw : message.getContent();
        } catch (Exception ignored) {
            return message.getContent();
        }
    }

    @Unique
    private static String getReasoningContentSafe(Message message) {
        try {
            return ((MessageMixin) (Object) message).getReasoningContent();
        } catch (Exception ignored) {
            return null;
        }
    }
}
