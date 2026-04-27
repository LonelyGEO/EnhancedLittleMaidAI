package com.github.tartaricacid.enhancedlittlemaidai.mixin;

import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.openai.request.ChatMessage;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.openai.response.ToolCall;
import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;

/**
 * 为 ChatMessage (LLM 请求) 添加 reasoningContent 字段支持。
 * Gson 序列化时自动将 @SerializedName("reasoning_content") 字段写入 JSON。
 */
@Mixin(value = ChatMessage.class, remap = false)
public abstract class ChatMessageMixin {
    @SerializedName("reasoning_content")
    @Unique
    private String reasoningContent;

    @Unique
    @Nullable
    public String enhancedGetReasoningContent() {
        return reasoningContent;
    }

    @Unique
    public void enhancedSetReasoningContent(@Nullable String reasoningContent) {
        this.reasoningContent = reasoningContent;
    }
}
