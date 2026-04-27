package com.github.tartaricacid.enhancedlittlemaidai.mixin;

import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.openai.request.ChatCompletion;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.openai.request.ChatMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * 为 ChatCompletion 提供 messages 列表的访问接口。
 */
@Mixin(value = ChatCompletion.class, remap = false)
public interface ChatCompletionAccessor {
    @Accessor("messages")
    List<ChatMessage> getChatMessages();
}
