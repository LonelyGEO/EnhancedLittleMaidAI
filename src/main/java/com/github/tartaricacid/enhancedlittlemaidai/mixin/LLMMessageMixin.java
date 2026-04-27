package com.github.tartaricacid.enhancedlittlemaidai.mixin;

import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * 为 LLMMessage record 添加 reasoningContent 字段支持。
 * 该字段通过 {@code #enhancedSetReasoningContent} / {@code #reasoningContent} 存取。
 */
@Mixin(value = LLMMessage.class, remap = false)
public abstract class LLMMessageMixin {
    @Unique
    private String enhancedReasoningContent;

    @Unique
    @Nullable
    public String reasoningContent() {
        return enhancedReasoningContent;
    }

    @Unique
    public void enhancedSetReasoningContent(@Nullable String content) {
        this.enhancedReasoningContent = content;
    }
}
