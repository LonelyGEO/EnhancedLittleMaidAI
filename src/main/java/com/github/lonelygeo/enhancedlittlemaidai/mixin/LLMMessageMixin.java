package com.github.lonelygeo.enhancedlittlemaidai.mixin;

import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 为 LLMMessage 保留 reasoningContent 字段（供未来扩展使用）。
 * 实际存取通过 {@link ReasoningContentStore} 完成。
 */
@Mixin(value = LLMMessage.class, remap = false)
public abstract class LLMMessageMixin {
}
