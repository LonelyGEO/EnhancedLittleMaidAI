package com.github.lonelygeo.enhancedlittlemaidai.mixin;

import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.openai.response.Message;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 占位 Mixin（不再添加字段，避免与已有字段冲突）。
 * reasoning_content 提取通过 {@link ReasoningContentStore#getFromMessage(Object)} 反射完成。
 */
@Mixin(value = Message.class, remap = false)
public abstract class MessageMixin {
}
