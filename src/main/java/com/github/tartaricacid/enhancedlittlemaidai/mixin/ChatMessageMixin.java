package com.github.tartaricacid.enhancedlittlemaidai.mixin;

import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.openai.request.ChatMessage;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 占位 Mixin（不再添加字段，避免与已有字段冲突）。
 * JSON 注入通过 {@link ReasoningContentStore} 直接操作 JSON 字符串完成。
 */
@Mixin(value = ChatMessage.class, remap = false)
public abstract class ChatMessageMixin {
}
